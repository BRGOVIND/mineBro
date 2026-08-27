package com.minebro.agent;

import com.minebro.core.CancellationToken;
import com.minebro.provider.model.ChatMessage;
import com.minebro.provider.model.ToolSchema;
import com.minebro.tool.PermissionLevel;
import com.minebro.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Public entry point: {@link #submit} and {@link #cancel}. Owns the short-term conversation
 * memory - a ring buffer, not a database (architecture doc §9.6): kept deliberately small
 * because stale tool results in history are a cause of hallucination, not a mitigation.
 */
public final class ConversationController {

    private static final int MAX_HISTORY_MESSAGES = 20;

    private final AgentLoop loop;
    private final ToolRegistry toolRegistry;
    private final PermissionLevel permissionLevel;
    private final Supplier<String> snapshotJsonSupplier;

    private final List<ChatMessage> history = new ArrayList<>();
    private volatile CancellationToken currentCancel;

    public ConversationController(AgentLoop loop, ToolRegistry toolRegistry, PermissionLevel permissionLevel,
                                   Supplier<String> snapshotJsonSupplier) {
        this.loop = loop;
        this.toolRegistry = toolRegistry;
        this.permissionLevel = permissionLevel;
        this.snapshotJsonSupplier = snapshotJsonSupplier;
    }

    /**
     * Starts a turn, superseding any request still in flight. The result is empty when this
     * request was cancelled ({@code /minebro stop}) or superseded by a newer one - the caller
     * must display nothing in that case, otherwise a stale answer lands in chat unrelated to
     * whatever the player last asked.
     *
     * <p>Installing a new request as current and merging a finished request's history back both
     * happen inside {@code synchronized (this)} on this instance - the same monitor - so a
     * finishing request can never observe a half-installed successor. Either the finisher's
     * check-and-merge runs whole before the newcomer's cancel-and-install (the finisher was
     * genuinely first, its answer is legitimately shown), or the newcomer's runs whole first, in
     * which case it has already cancelled the finisher's token and the finisher discards itself.
     */
    public CompletableFuture<Optional<String>> submit(String question, AgentEventSink sink) {
        CancellationToken myToken;
        List<ChatMessage> snapshot;
        synchronized (this) {
            if (currentCancel != null) {
                currentCancel.cancel();
            }
            if (history.isEmpty()) {
                List<ToolSchema> schemas = toolRegistry.schemasAtOrBelow(permissionLevel);
                history.add(new ChatMessage.SystemMessage(PromptAssembler.systemPrompt(schemas)));
            }
            String snapshotJson = snapshotJsonSupplier.get();
            history.add(new ChatMessage.UserMessage(PromptAssembler.userTurn(question, snapshotJson)));
            trimHistory();

            myToken = new CancellationToken();
            currentCancel = myToken;
            snapshot = new ArrayList<>(history);
        }
        return loop.run(snapshot, sink, myToken).thenApply(outcome -> {
            synchronized (this) {
                if (currentCancel == myToken && !myToken.isCancelled()) {
                    history.clear();
                    history.addAll(outcome.updatedHistory());
                    return Optional.of(outcome.finalText());
                }
            }
            return Optional.<String>empty();
        });
    }

    public void cancel() {
        CancellationToken token = currentCancel;
        if (token != null) {
            token.cancel();
        }
    }

    public synchronized void clear() {
        history.clear();
    }

    private void trimHistory() {
        if (history.size() <= MAX_HISTORY_MESSAGES + 1) {
            return;
        }
        ChatMessage system = history.get(0);
        List<ChatMessage> tail = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size()));
        history.clear();
        history.add(system);
        history.addAll(tail);
    }
}
