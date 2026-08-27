package com.minebro.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Supports the QA-issue-6 fix: an in-flight provider request must actually abort on cancel, not just discard its eventual result. */
class CancellationTokenTest {

    @Test
    void listenerFiresWhenCancelIsCalled() {
        CancellationToken token = new CancellationToken();
        AtomicInteger fired = new AtomicInteger();
        token.onCancel(fired::incrementAndGet);
        assertEquals(0, fired.get());
        token.cancel();
        assertEquals(1, fired.get());
    }

    @Test
    void listenerFiresImmediatelyIfAlreadyCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        AtomicInteger fired = new AtomicInteger();
        token.onCancel(fired::incrementAndGet);
        assertEquals(1, fired.get());
    }

    @Test
    void cancelIsIdempotentListenersFireOnlyOnce() {
        CancellationToken token = new CancellationToken();
        AtomicInteger fired = new AtomicInteger();
        token.onCancel(fired::incrementAndGet);
        token.cancel();
        token.cancel();
        token.cancel();
        assertEquals(1, fired.get());
    }

    @Test
    void aMisbehavingListenerDoesNotStopOthersFromFiring() {
        CancellationToken token = new CancellationToken();
        AtomicInteger fired = new AtomicInteger();
        token.onCancel(() -> { throw new RuntimeException("boom"); });
        token.onCancel(fired::incrementAndGet);
        token.cancel();
        assertEquals(1, fired.get());
    }

    @Test
    void throwIfCancelledOnlyThrowsAfterCancel() {
        CancellationToken token = new CancellationToken();
        assertFalse(token.isCancelled());
        token.cancel();
        assertTrue(token.isCancelled());
        assertThrows(CancellationToken.CancelledException.class, token::throwIfCancelled);
    }
}
