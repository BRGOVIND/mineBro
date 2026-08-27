package com.minebro.agent;

import com.minebro.agent.codec.JsonPromptToolCallCodec;
import com.minebro.agent.codec.ParseOutcome;
import com.minebro.config.MineBroConfig;
import com.minebro.core.CancellationToken;
import com.minebro.provider.AIProvider;
import com.minebro.provider.model.ChatMessage;
import com.minebro.provider.model.ChatRequest;
import com.minebro.provider.model.ChatResponse;
import com.minebro.provider.model.ToolCall;
import com.minebro.tool.ToolContext;
import com.minebro.tool.ToolExecutor;
import com.minebro.tool.ToolResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The tool loop and its caps (architecture doc §7.6). Runs entirely off the client thread - only
 * {@link ToolExecutor} hops back onto it, per tool call, via its own main-thread marshalling.
 */
public final class AgentLoop {

    /**
     * Non-final so the settings screen can swap providers without a restart. {@code volatile}
     * because it is written on the client thread and read from whatever thread the loop's next
     * step runs on. A turn already in flight keeps the provider it started with - the swap takes
     * effect from the next {@code step(...)} onwards, which is the only sane boundary.
     */
    private volatile AIProvider provider;
    private final ToolExecutor executor;
    private final JsonPromptToolCallCodec codec;
    private final MineBroConfig config;
    private final ToolExecutor.ToolContextSupplier ctxSupplier;

    public AgentLoop(AIProvider provider, ToolExecutor executor, JsonPromptToolCallCodec codec,
                      MineBroConfig config, ToolExecutor.ToolContextSupplier ctxSupplier) {
        this.provider = provider;
        this.executor = executor;
        this.codec = codec;
        this.config = config;
        this.ctxSupplier = ctxSupplier;
    }

    /** Replaces the provider used by subsequent turns (MineBroConfigScreen's Save). */
    public void setProvider(AIProvider provider) {
        this.provider = provider;
    }

    /**
     * The result of a turn: the player-facing text plus the history as it stands after the turn.
     * The loop mutates a copy of the history it was handed, so returning it is the only way the
     * caller ({@link ConversationController}) can persist assistant replies and tool exchanges -
     * without this, every turn's accumulated context was discarded when the future resolved.
     */
    public record Outcome(String finalText, List<ChatMessage> updatedHistory) {}

    public CompletableFuture<Outcome> run(List<ChatMessage> history, AgentEventSink sink, CancellationToken cancel) {
        List<ChatMessage> mutableHistory = new ArrayList<>(history);
        sink.onThinking();
        return step(mutableHistory, 0, sink, cancel);
    }

    private CompletableFuture<Outcome> step(List<ChatMessage> history, int iteration, AgentEventSink sink, CancellationToken cancel) {
        if (cancel.isCancelled()) {
            // Nobody will ever see this Outcome - ConversationController discards a cancelled
            // request's result unconditionally - so this exists only to stop spending real work
            // (an HTTP round trip, a tool execution) on a request the player already walked away
            // from, once the loop next gets a chance to check.
            return CompletableFuture.completedFuture(new Outcome("cancelled", history));
        }
        if (iteration >= config.maxToolIterations) {
            String message = "I got stuck going in circles - here's what I found so far.";
            sink.onFinalAnswer(message);
            return CompletableFuture.completedFuture(new Outcome(message, history));
        }

        ChatRequest request = new ChatRequest(
                history, List.of(), config.temperature, config.maxTokens,
                Duration.ofSeconds(config.requestTimeoutSeconds));

        return provider.chat(request, cancel).thenCompose(response ->
                handleResponse(history, response, iteration, sink, cancel)
        ).exceptionallyCompose(t -> {
            String message = "MineBro hit a problem: " + rootMessage(t);
            sink.onError(message);
            return CompletableFuture.completedFuture(new Outcome(message, history));
        });
    }

    private CompletableFuture<Outcome> handleResponse(
            List<ChatMessage> history, ChatResponse response, int iteration, AgentEventSink sink, CancellationToken cancel
    ) {
        ParseOutcome outcome = codec.decode(response);

        if (outcome instanceof ParseOutcome.TextAnswer text) {
            String answerText = text.text().isBlank()
                    ? "I didn't have anything to say to that - try rephrasing the question."
                    : text.text();
            history.add(new ChatMessage.AssistantMessage(answerText, List.of()));
            sink.onFinalAnswer(answerText);
            return CompletableFuture.completedFuture(new Outcome(answerText, history));
        }

        if (cancel.isCancelled()) {
            return CompletableFuture.completedFuture(new Outcome("cancelled", history));
        }

        ParseOutcome.ToolCallFound found = (ParseOutcome.ToolCallFound) outcome;
        ToolCall call = found.call();
        history.add(new ChatMessage.AssistantMessage(null, List.of(call)));
        sink.onToolCall(call.tool());

        return executor.run(call, ctxSupplier).thenCompose(result -> {
            sink.onToolResult(result);
            history.add(new ChatMessage.ToolResultMessage(call.id(), call.tool(), result.toJson().toString()));
            sink.onThinking();
            return step(history, iteration + 1, sink, cancel);
        });
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }
}
