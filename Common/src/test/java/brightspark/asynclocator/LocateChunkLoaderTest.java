package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ChunkResult;
import org.junit.jupiter.api.Test;

class LocateChunkLoaderTest {
    @Test
    void chunkThatLostItsTicketIsRequestedAgain() {
        AtomicInteger requests = new AtomicInteger();

        String chunk = LocateChunkLoader.await(
                () -> CompletableFuture.completedFuture(
                        requests.incrementAndGet() == 1
                                ? ChunkResult.error("Unloaded chunk")
                                : ChunkResult.of("chunk")),
                3);

        assertEquals("chunk", chunk);
        assertEquals(2, requests.get());
    }

    @Test
    void slowChunkLoadKeepsRefreshingItsTicketWithoutUsingAttempts() {
        AtomicInteger requests = new AtomicInteger();
        CompletableFuture<ChunkResult<String>> load = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(LocateChunkLoader.TICKET_REFRESH_MILLIS * 4, TimeUnit.MILLISECONDS)
                .execute(() -> load.complete(ChunkResult.of("chunk")));

        String chunk = LocateChunkLoader.await(
                () -> {
                    requests.incrementAndGet();
                    return load;
                },
                1);

        assertEquals("chunk", chunk);
        assertTrue(requests.get() >= 2, "Expected the ticket to be refreshed while the chunk loads");
    }

    @Test
    void chunkThatNeverLoadsFallsBackAfterMaxAttempts() {
        AtomicInteger requests = new AtomicInteger();

        String chunk = LocateChunkLoader.await(
                () -> {
                    requests.incrementAndGet();
                    return CompletableFuture.completedFuture(ChunkResult.<String>error("Unloaded chunk"));
                },
                3);

        assertNull(chunk);
        assertEquals(3, requests.get());
    }

    @Test
    void interruptionStopsWaitingForChunkAndKeepsFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    CancellationException.class,
                    () -> LocateChunkLoader.await(CompletableFuture<ChunkResult<String>>::new, 3));
            assertTrue(Thread.currentThread().isInterrupted(), "Cancellation must keep the interrupt flag");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedSearchStopsBeforeRequestingChunk() {
        AtomicInteger requests = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    CancellationException.class,
                    () -> LocateCancellation.call(() -> LocateChunkLoader.await(
                            () -> {
                                requests.incrementAndGet();
                                return CompletableFuture.completedFuture(ChunkResult.of("chunk"));
                            },
                            3)));
            assertEquals(0, requests.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void failedChunkLoadPropagatesCause() {
        IllegalStateException failure = new IllegalStateException("generation failed");

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> LocateChunkLoader.<String>await(() -> CompletableFuture.failedFuture(failure), 3));

        assertSame(failure, thrown.getCause());
    }
}
