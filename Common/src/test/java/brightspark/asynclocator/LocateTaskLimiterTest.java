package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocateTaskLimiterTest {
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
                FutureTask<Void> task = limiter.createTask(new CompletableFuture<>(), () -> {
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
            releaseTasks.countDown();

            for (FutureTask<Void> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        }

        assertTrue(limiter.tryAdmit());
        FutureTask<Void> cleanup = limiter.createTask(new CompletableFuture<>(), () -> {});
        assertTrue(cleanup.cancel(false));
    }

    @Test
    void cancellingBeforeExecutionReleasesAdmission() {
        LocateTaskLimiter limiter = new LocateTaskLimiter(1, 0);
        assertTrue(limiter.tryAdmit());

        FutureTask<Void> task = limiter.createTask(new CompletableFuture<>(), () -> {});
        assertFalse(limiter.tryAdmit());
        assertTrue(task.cancel(false));
        assertTrue(limiter.tryAdmit());

        FutureTask<Void> cleanup = limiter.createTask(new CompletableFuture<>(), () -> {});
        assertTrue(cleanup.cancel(false));
    }

    @Test
    void reconfigurationAppliesWithoutCancellingAdmittedTasks() {
        LocateTaskLimiter limiter = new LocateTaskLimiter(1, 0);
        assertTrue(limiter.tryAdmit());
        FutureTask<Void> first = limiter.createTask(new CompletableFuture<>(), () -> {});
        assertFalse(limiter.tryAdmit());

        limiter.configure(2, 1);
        assertTrue(limiter.tryAdmit());
        FutureTask<Void> second = limiter.createTask(new CompletableFuture<>(), () -> {});
        assertTrue(limiter.tryAdmit());
        FutureTask<Void> third = limiter.createTask(new CompletableFuture<>(), () -> {});
        assertFalse(limiter.tryAdmit());

        assertTrue(first.cancel(false));
        assertTrue(second.cancel(false));
        assertTrue(third.cancel(false));
    }
}
