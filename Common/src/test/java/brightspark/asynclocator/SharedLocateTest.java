package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brightspark.asynclocator.AsyncLocator.LocateTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SharedLocateTest {
    @Test
    void completionIsDeliveredToEverySubscriberWithoutCancellingUnderlyingTask() {
        AtomicBoolean removed = new AtomicBoolean();
        SharedLocate<String> shared = new SharedLocate<>(ignored -> removed.set(true));
        CompletableFuture<String> first = shared.subscribe();
        CompletableFuture<String> second = shared.subscribe();
        CompletableFuture<String> underlyingResult = new CompletableFuture<>();
        FutureTask<Void> underlyingFuture = new FutureTask<>(() -> null);
        shared.connect(new LocateTask<>(null, underlyingResult, underlyingFuture));

        underlyingResult.complete("stronghold");

        assertEquals("stronghold", first.join());
        assertEquals("stronghold", second.join());
        assertFalse(underlyingFuture.isCancelled());
        assertTrue(removed.get());
    }

    @Test
    void lastSubscriberCancellationCancelsUnderlyingTask() {
        AtomicBoolean removed = new AtomicBoolean();
        SharedLocate<String> shared = new SharedLocate<>(ignored -> removed.set(true));
        CompletableFuture<String> first = shared.subscribe();
        CompletableFuture<String> second = shared.subscribe();
        FutureTask<Void> underlyingFuture = new FutureTask<>(() -> null);
        shared.connect(new LocateTask<>(null, new CompletableFuture<>(), underlyingFuture));

        first.cancel(false);
        assertFalse(underlyingFuture.isCancelled());
        assertFalse(removed.get());

        second.cancel(false);
        assertTrue(underlyingFuture.isCancelled());
        assertTrue(removed.get());
    }

    @Test
    void shutdownFailsSubscribersAndCancelsUnderlyingTask() {
        SharedLocate<String> shared = new SharedLocate<>(ignored -> {});
        CompletableFuture<String> subscriber = shared.subscribe();
        FutureTask<Void> underlyingFuture = new FutureTask<>(() -> null);
        shared.connect(new LocateTask<>(null, new CompletableFuture<>(), underlyingFuture));

        shared.shutdown();

        assertTrue(underlyingFuture.isCancelled());
        assertTrue(subscriber.isCompletedExceptionally());
    }
}
