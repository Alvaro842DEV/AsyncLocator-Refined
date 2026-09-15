package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class LocateCancellationTest {
    @Test
    void interruptionOnlyCancelsOwnedSearchAndScopeIsClearedOnFailure() {
        Thread.currentThread().interrupt();
        try {
            assertDoesNotThrow(LocateCancellation::check);
            assertThrows(
                    CancellationException.class,
                    () -> LocateCancellation.call(() -> {
                        LocateCancellation.check();
                        return null;
                    }));
            assertDoesNotThrow(LocateCancellation::check);
            assertTrue(Thread.currentThread().isInterrupted(), "Checkpoint must not clear interruption");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void nestedScopeRestoresOuterSearch() {
        LocateCancellation.call(() -> {
            LocateCancellation.call(() -> null);
            Thread.currentThread().interrupt();
            try {
                assertThrows(CancellationException.class, LocateCancellation::check);
            } finally {
                Thread.interrupted();
            }
            return null;
        });
    }

    @Test
    void completedSearchDoesNotLeaveCallbacksInsideCancellationScope() {
        LocateCancellation.call(() -> null);
        Thread.currentThread().interrupt();
        try {
            assertDoesNotThrow(LocateCancellation::check);
        } finally {
            Thread.interrupted();
        }
    }
}
