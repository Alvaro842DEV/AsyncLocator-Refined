package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brightspark.asynclocator.AsyncLocator.LocateTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocateTaskLimiterTest {
    @Test
    void locateTimeoutStartsOnlyAfterExecutionCapacityIsAcquired() throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        CompletableFuture<Void> started = new CompletableFuture<>();
        CountDownLatch cancelled = new CountDownLatch(1);
        FutureTask<Void> task = new FutureTask<>(() -> null) {
            @Override
            protected void done() {
                if (isCancelled()) cancelled.countDown();
            }
        };
        new LocateTask<>(null, result, task, started).withTimeout(50, TimeUnit.MILLISECONDS);

        assertThrows(TimeoutException.class, () -> result.get(150, TimeUnit.MILLISECONDS));
        assertFalse(result.isDone());
        assertFalse(task.isCancelled());

        started.complete(null);
        ExecutionException exception = assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, exception.getCause());
        assertTrue(cancelled.await(2, TimeUnit.SECONDS), "Time out task should be cancelled");
        assertTrue(task.isCancelled());
    }

    @Test
    void boundsConcurrentAndQueuedTasks() throws Exception {
        LocateTaskLimiter limiter = new LocateTaskLimiter(2, 2);
        CountDownLatch activeTasks = new CountDownLatch(2);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maximumRunning = new AtomicInteger();
        List<FutureTask<Void>> tasks = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 4; i++) {
                assertTrue(limiter.tryAdmit());
                FutureTask<Void> task = limiter.createTask(new CompletableFuture<>(), new CompletableFuture<>(), () -> {
                    int current = running.incrementAndGet();
                    maximumRunning.accumulateAndGet(current, Math::max);
                    activeTasks.countDown();
                    try {
                        assertTrue(releaseTasks.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        running.decrementAndGet();
                    }
                });
                tasks.add(task);
                executor.execute(task);
            }

            assertFalse(limiter.tryAdmit());
            assertTrue(activeTasks.await(5, TimeUnit.SECONDS));
            assertEquals(2, maximumRunning.get());
            assertEquals(new LocateTaskLimiter.Snapshot(2, 2, 2, 2), limiter.snapshot());
            releaseTasks.countDown();

            for (FutureTask<Void> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        }

        assertEquals(new LocateTaskLimiter.Snapshot(0, 0, 2, 2), limiter.snapshot());

        assertTrue(limiter.tryAdmit());
        FutureTask<Void> cleanup = limiter.createTask(new CompletableFuture<>(), new CompletableFuture<>(), () -> {});
        assertTrue(cleanup.cancel(false));
    }

    @Test
    void cancellingBeforeExecutionReleasesAdmission() {
        LocateTaskLimiter limiter = new LocateTaskLimiter(1, 0);
        assertTrue(limiter.tryAdmit());

        FutureTask<Void> task = limiter.createTask(new CompletableFuture<>(), new CompletableFuture<>(), () -> {});
        assertFalse(limiter.tryAdmit());
        assertTrue(task.cancel(false));
        assertTrue(limiter.tryAdmit());

        FutureTask<Void> cleanup = limiter.createTask(new CompletableFuture<>(), new CompletableFuture<>(), () -> {});
        assertTrue(cleanup.cancel(false));
    }

    @Test
    void reconfigurationAppliesWithoutCancellingAdmittedTasks() {
        LocateTaskLimiter limiter = new LocateTaskLimiter(1, 0);
        assertTrue(limiter.tryAdmit());
        FutureTask<Void> first = limiter.createTask(new CompletableFuture<>(), new CompletableFuture<>(), () -> {});
        assertFalse(limiter.tryAdmit());

        limiter.configure(2, 1);
        assertTrue(limiter.tryAdmit());
        FutureTask<Void> second = limiter.createTask(new CompletableFuture<>(), new CompletableFuture<>(), () -> {});
        assertTrue(limiter.tryAdmit());
        FutureTask<Void> third = limiter.createTask(new CompletableFuture<>(), new CompletableFuture<>(), () -> {});
        assertFalse(limiter.tryAdmit());

        assertTrue(first.cancel(false));
        assertTrue(second.cancel(false));
        assertTrue(third.cancel(false));
    }
}
