package brightspark.asynclocator;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Loads chunks for locate workers without blocking the server thread.
 * The wait happens on the worker, so the server tick keeps running while the chunk loads
 */
public final class LocateChunkLoader {
    /*
     * getChunkFuture holds the chunk with a TicketType.UNKNOWN ticket, which is purged after two ticks. Vanilla's
     * blocking path never sees that because no tick runs while it waits. Here ticks keep running, so the request is
     * repeated about once per tick, which resets the ticket and returns the same in progress load
     */
    static final long TICKET_REFRESH_MILLIS = 40L;
    static final int MAX_ATTEMPTS = 8;

    private LocateChunkLoader() {}

    // Returns null when the caller should use the vanilla path
    public static @Nullable ChunkAccess tryLoad(LevelReader level, int x, int z, ChunkStatus status) {
        if (!(level instanceof ServerLevel serverLevel) || !LocateCancellation.isActive()) return null;

        ChunkAccess chunk = await(() -> serverLevel.getChunkSource().getChunkFuture(x, z, status, true), MAX_ATTEMPTS);
        if (chunk == null) {
            ALConstants.logDebug(
                    "Chunk [{}, {}] was not available after {} attempts: loading it on the server thread",
                    x,
                    z,
                    MAX_ATTEMPTS);
        }
        return chunk;
    }

    static <T> @Nullable T await(Supplier<CompletableFuture<ChunkResult<T>>> request, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            LocateCancellation.check();
            T value = awaitRefreshingTicket(request.get(), request).orElse(null);
            if (value != null) return value;
        }
        return null;
    }

    private static <T> ChunkResult<T> awaitRefreshingTicket(
            CompletableFuture<ChunkResult<T>> future, Supplier<CompletableFuture<ChunkResult<T>>> request) {
        while (true) {
            try {
                return future.get(TICKET_REFRESH_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                LocateCancellation.check();
                request.get();
            } catch (InterruptedException exception) {
                // get is interruptible, so cancelling the search also stops the wait
                Thread.currentThread().interrupt();
                throw new CancellationException("Locate search interrupted while waiting for a chunk");
            } catch (ExecutionException exception) {
                throw new CompletionException(exception.getCause());
            }
        }
    }
}
