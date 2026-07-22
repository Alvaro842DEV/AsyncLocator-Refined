package brightspark.asynclocator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

final class LocateTaskLimiter {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition capacityAvailable = lock.newCondition();

    private int maxConcurrent;
    private int maxQueued;
    private int active;
    private int admitted;

    LocateTaskLimiter(int maxConcurrent, int maxQueued) {
        configure(maxConcurrent, maxQueued);
    }

    void configure(int maxConcurrent, int maxQueued) {
        if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent must be at least 1");
        if (maxQueued < 0) throw new IllegalArgumentException("maxQueued cannot be negative");

        lock.lock();
        try {
            this.maxConcurrent = maxConcurrent;
            this.maxQueued = maxQueued;
            capacityAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    boolean tryAdmit() {
        lock.lock();
        try {
            if (admitted >= maxConcurrent + maxQueued) return false;
            admitted++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    FutureTask<Void> createTask(CompletableFuture<?> result, Runnable task) {
        return new AdmittedTask(result, task);
    }

    private void acquireCapacity() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (active >= maxConcurrent) {
                capacityAvailable.await();
            }
            active++;
        } finally {
            lock.unlock();
        }
    }

    private void releaseCapacity() {
        lock.lock();
        try {
            active--;
            capacityAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void releaseAdmission() {
        lock.lock();
        try {
            admitted--;
        } finally {
            lock.unlock();
        }
    }

    private final class AdmittedTask extends FutureTask<Void> {
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean admissionReleased = new AtomicBoolean();

        private AdmittedTask(CompletableFuture<?> result, Runnable task) {
            super(() -> {
                boolean acquired = false;
                try {
                    acquireCapacity();
                    acquired = true;
                    task.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    result.completeExceptionally(exception);
                } finally {
                    if (acquired) releaseCapacity();
                }
                return null;
            });
        }

        @Override
        public void run() {
            started.set(true);
            try {
                super.run();
            } finally {
                releaseAdmissionOnce();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && !started.get()) releaseAdmissionOnce();
            return cancelled;
        }

        private void releaseAdmissionOnce() {
            if (admissionReleased.compareAndSet(false, true)) releaseAdmission();
        }
    }
}
