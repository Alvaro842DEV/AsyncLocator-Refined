package brightspark.asynclocator;

import brightspark.asynclocator.AsyncLocator.LocateTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

final class SharedLocate<T> {
    private final Consumer<SharedLocate<T>> removePending;
    private final CompletableFuture<T> result = new CompletableFuture<>();
    private LocateTask<T> underlyingTask;
    private int subscribers;
    private boolean closed;

    SharedLocate(Consumer<SharedLocate<T>> removePending) {
        this.removePending = removePending;
    }

    synchronized CompletableFuture<T> subscribe() {
        if (closed) return null;

        subscribers++;
        CompletableFuture<T> child = result.copy();
        child.whenComplete((ignoredResult, ignoredThrowable) -> releaseSubscriber());
        return child;
    }

    synchronized void connect(LocateTask<T> task) {
        underlyingTask = task;
        if (closed) {
            task.cancel();
            return;
        }

        task.completableFuture().whenComplete(this::complete);
    }

    void fail(Throwable throwable) {
        complete(null, throwable);
    }

    void shutdown() {
        LocateTask<T> taskToCancel;
        synchronized (this) {
            if (closed) return;
            closed = true;
            taskToCancel = underlyingTask;
        }

        if (taskToCancel != null) taskToCancel.cancel();
        result.completeExceptionally(
                new RejectedExecutionException("Async locator executor service has been shut down"));
    }

    private void complete(T value, Throwable throwable) {
        synchronized (this) {
            if (closed) return;
            closed = true;
            removePending.accept(this);
        }

        if (throwable == null) result.complete(value);
        else result.completeExceptionally(throwable);
    }

    private void releaseSubscriber() {
        LocateTask<T> taskToCancel = null;
        boolean abandoned = false;
        synchronized (this) {
            subscribers--;
            if (subscribers == 0 && !closed) {
                closed = true;
                removePending.accept(this);
                taskToCancel = underlyingTask;
                abandoned = true;
            }
        }

        if (taskToCancel != null) taskToCancel.cancel();
        if (abandoned) result.cancel(false);
    }
}
