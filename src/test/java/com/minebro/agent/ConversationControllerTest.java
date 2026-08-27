package com.minebro.agent;

import com.google.gson.JsonObject;
import com.minebro.agent.codec.JsonPromptToolCallCodec;
import com.minebro.config.MineBroConfig;
import com.minebro.core.CancellationToken;
import com.minebro.core.MainThreadExecutor;
import com.minebro.provider.AIProvider;
import com.minebro.provider.ProviderCapabilities;
import com.minebro.provider.model.ChatMessage;
import com.minebro.provider.model.ChatRequest;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.FinishReason;
import com.minebro.provider.model.HealthReport;
import com.minebro.provider.model.TokenUsage;
import com.minebro.provider.model.ToolCall;
import com.minebro.provider.model.ToolSchema;
import com.minebro.tool.MineBroTool;
import com.minebro.tool.PermissionLevel;
import com.minebro.tool.ToolContext;
import com.minebro.tool.ToolExecutor;
import com.minebro.tool.ToolKind;
import com.minebro.tool.ToolRegistry;
import com.minebro.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two regressions guarded here, both rooted in {@link ConversationController} not owning the
 * result of a turn:
 *
 * <ol>
 *   <li>the agent loop mutated a private copy of the history, so assistant replies and tool
 *       exchanges were thrown away when the turn's future resolved - every turn started blind;</li>
 *   <li>a single {@code currentCancel} field was overwritten by each submit, so an older in-flight
 *       request could still publish its (now unrelated) answer into chat.</li>
 * </ol>
 *
 * <p>History is verified indirectly - through the messages the fake provider actually receives on
 * a later turn - rather than through an accessor added only for tests.
 *
 * <p>The concurrency tests drive both orderings of the interleaving explicitly (a newer request
 * installing itself before vs. after an older one's continuation runs) instead of racing threads,
 * which single-JVM test code cannot do deterministically. True concurrent interleaving is ruled
 * out structurally, not by these tests: both the install and the check-and-merge happen inside
 * {@code synchronized (this)} on the same monitor, so only these two orderings exist.
 */
class ConversationControllerTest {

    private static final MainThreadExecutor SYNCHRONOUS = new MainThreadExecutor() {
        @Override
        public <T> CompletableFuture<T> submit(Callable<T> task) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    };

    // ---------------------------------------------------------------- history persistence

    @Test
    void aSuccessfulTurnRecordsTheSystemUserAndAssistantMessagesInOrder() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ConversationController controller = controllerFor(provider, new ToolRegistry());
        provider.enqueueText("Craft planks, then sticks.");
        provider.enqueueText("(probe)");

        assertEquals(Optional.of("Craft planks, then sticks."),
                controller.submit("How do I make a pickaxe?", AgentEventSink.NOOP).get());
        controller.submit("What about diamonds?", AgentEventSink.NOOP).get();

        List<ChatMessage> secondRequest = provider.requests.get(1);
        assertEquals(4, secondRequest.size());
        assertInstanceOf(ChatMessage.SystemMessage.class, secondRequest.get(0));
        assertTrue(text(secondRequest.get(1)).contains("How do I make a pickaxe?"));
        ChatMessage.AssistantMessage assistant = assertInstanceOf(ChatMessage.AssistantMessage.class, secondRequest.get(2));
        assertEquals("Craft planks, then sticks.", assistant.content());
        assertTrue(text(secondRequest.get(3)).contains("What about diamonds?"));
    }

    @Test
    void everyCompletedTurnIsMergedBackNotJustTheFirst() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ConversationController controller = controllerFor(provider, new ToolRegistry());
        provider.enqueueText("Answer one.");
        provider.enqueueText("Answer two.");
        provider.enqueueText("(probe)");

        controller.submit("First question?", AgentEventSink.NOOP).get();
        controller.submit("Second question?", AgentEventSink.NOOP).get();
        controller.submit("Third question?", AgentEventSink.NOOP).get();

        List<String> thirdRequest = provider.requests.get(2).stream().map(ConversationControllerTest::text).toList();
        assertTrue(thirdRequest.contains("Answer one."), "first turn's reply must survive into turn three");
        assertTrue(thirdRequest.contains("Answer two."), "second turn's reply must survive into turn three");
    }

    @Test
    void aTurnThatCallsAToolRecordsBothTheToolCallAndItsResult() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool());
        ConversationController controller = controllerFor(provider, registry);
        provider.enqueueText("{\"tool\": \"fake_tool\", \"arguments\": {}}");
        provider.enqueueText("You have three diamonds.");
        provider.enqueueText("(probe)");

        assertEquals(Optional.of("You have three diamonds."),
                controller.submit("How many diamonds do I have?", AgentEventSink.NOOP).get());
        controller.submit("And iron?", AgentEventSink.NOOP).get();

        List<ChatMessage> probeRequest = provider.requests.get(2);
        assertTrue(probeRequest.stream().anyMatch(m -> m instanceof ChatMessage.AssistantMessage a && !a.toolCalls().isEmpty()),
                "the assistant's tool call must be in history");
        assertTrue(probeRequest.stream().anyMatch(m -> m instanceof ChatMessage.ToolResultMessage r && r.toolName().equals("fake_tool")),
                "the tool result must be in history");
    }

    // ------------------------------------------------------- supersession and cancellation

    @Test
    void aRequestThatIsNeitherCancelledNorSupersededDeliversItsAnswer() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ConversationController controller = controllerFor(provider, new ToolRegistry());
        CompletableFuture<ChatResponse> pending = provider.enqueueDeferred();

        CompletableFuture<Optional<String>> answer = controller.submit("Where am I?", AgentEventSink.NOOP);
        pending.complete(response("You are at spawn."));

        assertEquals(Optional.of("You are at spawn."), answer.get());
    }

    @Test
    void anOlderRequestResolvingAfterANewerOneStartedPublishesNothing() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ConversationController controller = controllerFor(provider, new ToolRegistry());
        CompletableFuture<ChatResponse> pendingA = provider.enqueueDeferred();
        CompletableFuture<ChatResponse> pendingB = provider.enqueueDeferred();

        CompletableFuture<Optional<String>> answerA = controller.submit("Question A", AgentEventSink.NOOP);
        CompletableFuture<Optional<String>> answerB = controller.submit("Question B", AgentEventSink.NOOP);

        pendingA.complete(response("Stale answer A"));
        assertEquals(Optional.empty(), answerA.get());

        pendingB.complete(response("Fresh answer B"));
        assertEquals(Optional.of("Fresh answer B"), answerB.get());
    }

    @Test
    void cancelAppliesToTheNewestRequestNotAStaleOne() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ConversationController controller = controllerFor(provider, new ToolRegistry());
        CompletableFuture<ChatResponse> pendingA = provider.enqueueDeferred();
        CompletableFuture<ChatResponse> pendingB = provider.enqueueDeferred();

        CompletableFuture<Optional<String>> answerA = controller.submit("Question A", AgentEventSink.NOOP);
        CompletableFuture<Optional<String>> answerB = controller.submit("Question B", AgentEventSink.NOOP);
        controller.cancel();

        pendingA.complete(response("Stale answer A"));
        pendingB.complete(response("Cancelled answer B"));

        assertEquals(Optional.empty(), answerA.get());
        assertEquals(Optional.empty(), answerB.get(), "/minebro stop must silence the request that was actually active");
    }

    @Test
    void aCancelledRequestStaysSilentEvenThoughALaterRequestStillWorks() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ConversationController controller = controllerFor(provider, new ToolRegistry());
        CompletableFuture<ChatResponse> pendingA = provider.enqueueDeferred();

        CompletableFuture<Optional<String>> answerA = controller.submit("Question A", AgentEventSink.NOOP);
        controller.cancel();
        pendingA.complete(response("Answer A after stop"));
        assertEquals(Optional.empty(), answerA.get());

        provider.enqueueText("Answer B");
        assertEquals(Optional.of("Answer B"), controller.submit("Question B", AgentEventSink.NOOP).get());
    }

    // ------------------------------------------------------------- provider-layer hardening

    @Test
    void aBlankProviderAnswerBecomesAFriendlyFallbackNotAnEmptyMessage() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ConversationController controller = controllerFor(provider, new ToolRegistry());
        provider.enqueueText("");

        Optional<String> answer = controller.submit("...", AgentEventSink.NOOP).get();

        assertTrue(answer.isPresent());
        assertTrue(!answer.get().isBlank(), "a blank model reply must never surface as a blank chat message");
    }

    @Test
    void aCancelledTurnNeverExecutesAToolOrMakesAFurtherProviderCall() throws Exception {
        ScriptedProvider provider = new ScriptedProvider();
        ToolRegistry registry = new ToolRegistry();
        FakeTool tool = new FakeTool();
        registry.register(tool);
        ConversationController controller = controllerFor(provider, registry);
        CompletableFuture<ChatResponse> pending = provider.enqueueDeferred();

        CompletableFuture<Optional<String>> answer = controller.submit("How many diamonds?", AgentEventSink.NOOP);
        controller.cancel();
        pending.complete(response("{\"tool\": \"fake_tool\", \"arguments\": {}}"));

        assertEquals(Optional.empty(), answer.get());
        assertEquals(1, provider.requests.size(), "a cancelled turn must not spend a second provider call once cancellation is observed");
        assertFalse(tool.executed, "a cancelled turn must not execute a tool call it has not yet started");
    }

    // ------------------------------------------------------------------ provider hot-swap

    /**
     * The settings screen's Save builds a new provider and pushes it into the running loop instead
     * of asking the player to restart Minecraft. If the loop kept using the provider it was
     * constructed with, Save would look like it worked while every turn still went to the old
     * endpoint - so the swap is asserted by which fake actually receives the second turn.
     */
    @Test
    void setProviderSendsTheNextTurnToTheNewProviderNotTheOldOne() throws Exception {
        ScriptedProvider first = new ScriptedProvider();
        ScriptedProvider second = new ScriptedProvider();
        MineBroConfig config = new MineBroConfig();
        config.permissionLevel = PermissionLevel.READ_ONLY;
        ToolRegistry registry = new ToolRegistry();
        AgentLoop loop = new AgentLoop(first, new ToolExecutor(registry, SYNCHRONOUS),
                new JsonPromptToolCallCodec(), config,
                () -> new ToolContext(null, null, null, config, Instant.now(), null));
        ConversationController controller = new ConversationController(loop, registry, PermissionLevel.READ_ONLY, () -> "{}");

        first.enqueueText("From the first provider.");
        assertEquals(Optional.of("From the first provider."),
                controller.submit("Before the swap?", AgentEventSink.NOOP).get());

        loop.setProvider(second);
        second.enqueueText("From the second provider.");
        assertEquals(Optional.of("From the second provider."),
                controller.submit("After the swap?", AgentEventSink.NOOP).get());

        assertEquals(1, first.requests.size(), "the replaced provider must not see any turn after setProvider");
        assertEquals(1, second.requests.size(), "the turn after setProvider must go to the new provider");
    }

    // ------------------------------------------------------------------------- fixtures

    private static ConversationController controllerFor(AIProvider provider, ToolRegistry registry) {
        MineBroConfig config = new MineBroConfig();
        config.permissionLevel = PermissionLevel.READ_ONLY;
        ToolExecutor executor = new ToolExecutor(registry, SYNCHRONOUS);
        AgentLoop loop = new AgentLoop(provider, executor, new JsonPromptToolCallCodec(), config,
                () -> new ToolContext(null, null, null, config, Instant.now(), null));
        return new ConversationController(loop, registry, PermissionLevel.READ_ONLY, () -> "{}");
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(new ChatMessage.AssistantMessage(content, List.of()),
                FinishReason.STOP, TokenUsage.UNKNOWN, Duration.ZERO);
    }

    private static String text(ChatMessage message) {
        return switch (message) {
            case ChatMessage.SystemMessage m -> m.content();
            case ChatMessage.UserMessage m -> m.content();
            case ChatMessage.AssistantMessage m -> m.content() == null ? "" : m.content();
            case ChatMessage.ToolResultMessage m -> m.jsonResult();
        };
    }

    /**
     * Returns pre-scripted responses and remembers exactly what it was asked - including futures
     * the test completes by hand, so an "in-flight" request can be held open across another
     * submit.
     */
    private static final class ScriptedProvider implements AIProvider {

        final List<List<ChatMessage>> requests = new ArrayList<>();
        private final Deque<CompletableFuture<ChatResponse>> scripted = new ArrayDeque<>();

        void enqueueText(String content) {
            scripted.add(CompletableFuture.completedFuture(response(content)));
        }

        CompletableFuture<ChatResponse> enqueueDeferred() {
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();
            scripted.add(future);
            return future;
        }

        @Override public String id() { return "ollama"; }
        @Override public String displayName() { return "scripted"; }
        @Override public ProviderCapabilities capabilities() { return new ProviderCapabilities(false, false, false, true, 4096, false); }
        @Override public CompletableFuture<HealthReport> health() { return CompletableFuture.completedFuture(HealthReport.ok("scripted")); }

        @Override
        public CompletableFuture<ChatResponse> chat(ChatRequest request, CancellationToken cancel) {
            requests.add(request.messages());
            CompletableFuture<ChatResponse> next = scripted.poll();
            assertNotNull(next, "the fake provider ran out of scripted responses");
            return next;
        }
    }

    private static final class FakeTool implements MineBroTool {
        boolean executed = false;

        @Override public String id() { return "fake_tool"; }
        @Override public ToolSchema schema() { return new ToolSchema("fake_tool", "counts things", new JsonObject()); }
        @Override public PermissionLevel requiredPermission() { return PermissionLevel.READ_ONLY; }
        @Override public ToolKind kind() { return ToolKind.READ; }

        @Override
        public ToolResult execute(ToolCall call, ToolContext ctx) {
            executed = true;
            return ToolResult.ok(call.id(), id(), "counted", new JsonObject());
        }
    }
}
