package brightspark.asynclocator;

import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

public final class LocateCancellation {
    private static final ThreadLocal<Boolean> LOCATING = new ThreadLocal<>();

    private LocateCancellation() {}

    static <T> T call(Supplier<T> search) {
        Boolean previous = LOCATING.get();
        LOCATING.set(true);
        try {
            return search.get();
        } finally {
            if (previous == null) LOCATING.remove();
            else LOCATING.set(previous);
        }
    }

    static boolean isActive() {
        return Boolean.TRUE.equals(LOCATING.get());
    }

    public static void check() {
        // The common, non-interrupted path requires no ThreadLocal lookup or allocation
        if (Thread.currentThread().isInterrupted() && Boolean.TRUE.equals(LOCATING.get())) {
            throw new CancellationException("Locate search interrupted");
        }
    }
}
