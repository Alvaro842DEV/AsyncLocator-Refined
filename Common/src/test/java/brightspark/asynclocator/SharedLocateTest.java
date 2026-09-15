package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brightspark.asynclocator.AsyncLocator.LocateTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SharedLocateTest {
    @Test
    void concurrentSubscribersCannotExceedLimit() throws Exception {
        SharedLocate<String> shared = new SharedLocate<>(ignored -> {});
        List<Future<Boolean>> attempts = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 32; i++) {
                attempts.add(executor.submit(() -> {
                    try {
                        shared.subscribe(4);
                        return true;
                    } catch (RejectedExecutionException expected) {
                        return false;
                    }
                }));
            }
            int accepted = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(5, TimeUnit.SECONDS)) accepted++;
            }
            assertEquals(4, accepted);
        } finally {
            shared.shutdown();
        }
    }

    @Test
    void subscriptionLimitSurvivesCancellationWithoutAffectingRemainingCaller() {
        SharedLocate<String> shared = new SharedLocate<>(ignored -> {});
        CompletableFuture<String> first = shared.subscribe(2);
        CompletableFuture<String> second = shared.subscribe(2);
        CompletableFuture<String> result = new CompletableFuture<>();
        FutureTask<Void> task = new FutureTask<>(() -> null);
        shared.connect(new LocateTask<>(null, result, task));

        second.cancel(false);
        assertThrows(RejectedExecutionException.class, () -> shared.subscribe(2));
        assertFalse(task.isCancelled());
        result.complete("stronghold");
        assertEquals("stronghold", first.join());
    }

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
    void underlyingStartIsPropagatedToSubscribers() {
        SharedLocate<String> shared = new SharedLocate<>(ignored -> {});
        CompletableFuture<Void> underlyingStarted = new CompletableFuture<>();
        shared.connect(
                new LocateTask<>(null, new CompletableFuture<>(), new FutureTask<>(() -> null), underlyingStarted));

        assertFalse(shared.startedFuture().isDone());
        underlyingStarted.complete(null);
        assertTrue(shared.startedFuture().isDone());
        assertFalse(shared.startedFuture().isCompletedExceptionally());
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
