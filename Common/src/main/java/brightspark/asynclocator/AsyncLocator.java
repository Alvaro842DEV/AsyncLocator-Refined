package brightspark.asynclocator;

import com.mojang.datafixers.util.Pair;
import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;

public class AsyncLocator {
    /*
     * All executor state is guarded by the AsyncLocator.class monitor so
     * that a task submission can never race a concurrent shutdown
     */
    private static ExecutorService LOCATING_EXECUTOR_SERVICE = null;
    private static boolean EXECUTOR_STOPPED = false;
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private static final ConcurrentHashMap<LocateKey, CompletableFuture<?>> PENDING_LOCATES = new ConcurrentHashMap<>();

    private record LocateKey(ResourceKey<Level> dimension, Object target, BlockPos pos, int searchRadius) {}

    private AsyncLocator() {}

    /**
     * Initializes the singleton executor for locating tasks using Java 21 Virtual Threads.
     *
     * After v1.5.1, we use virtual threads, which are lightweight and managed by the JVM, not the OS.
     * There is no need to configure thread pool size anymore (automatically scaled)
     */
    public static void setupExecutorService() {
        synchronized (AsyncLocator.class) {
            shutdownExecutorService();
            EXECUTOR_STOPPED = false;

            ALConstants.logInfo("Starting locating executor service with virtual threads (Java 21+)");

            LOCATING_EXECUTOR_SERVICE = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                    .name(ALConstants.MOD_ID + "-", THREAD_COUNTER.getAndIncrement())
                    .uncaughtExceptionHandler(
                            (t, e) -> ALConstants.logError(e, "Uncaught exception in virtual thread {}", t.getName()))
                    .factory());
        }
    }

    public static void shutdownExecutorService() {
        ExecutorService executor;
        synchronized (AsyncLocator.class) {
            executor = LOCATING_EXECUTOR_SERVICE;
            LOCATING_EXECUTOR_SERVICE = null;
            EXECUTOR_STOPPED = true;
        }

        if (executor == null) {
            return;
        }

        ALConstants.logInfo("Shutting down locating executor service");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                List<Runnable> pending = executor.shutdownNow();
                ALConstants.logWarn("Executor did not terminate cleanly, pending tasks: {}", pending.size());
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isExecutorActive() {
        synchronized (AsyncLocator.class) {
            return LOCATING_EXECUTOR_SERVICE != null && !LOCATING_EXECUTOR_SERVICE.isShutdown();
        }
    }

    private static Future<?> submitTask(CompletableFuture<?> completableFuture, Runnable task) {
        synchronized (AsyncLocator.class) {
            if (LOCATING_EXECUTOR_SERVICE == null || LOCATING_EXECUTOR_SERVICE.isShutdown()) {
                if (EXECUTOR_STOPPED) {
                    ALConstants.logWarn("Locating executor service has been shut down - rejecting locate task");
                    completableFuture.completeExceptionally(
                            new RejectedExecutionException("Async locator executor service has been shut down"));
                    return CompletableFuture.completedFuture(null);
                }
                ALConstants.logWarn("Locating executor service not initialized yet: creating lazily");
                setupExecutorService();
            }
            try {
                return LOCATING_EXECUTOR_SERVICE.submit(task);
            } catch (RejectedExecutionException e) {
                completableFuture.completeExceptionally(e);
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    /**
     * Queues a task to locate a feature using {@link ServerLevel#findNearestMapStructure(TagKey, BlockPos, int, boolean)}
     * and returns a {@link LocateTask} with the futures for it.
     */
    public static LocateTask<BlockPos> locate(
            ServerLevel level,
            TagKey<Structure> structureTag,
            BlockPos pos,
            int searchRadius,
            boolean skipKnownStructures) {
        ALConstants.logDebug(
                "Creating locate task for {} in {} around {} within {} chunks", structureTag, level, pos, searchRadius);

        if (!skipKnownStructures) {
            return coalesced(
                    level,
                    structureTag,
                    pos,
                    searchRadius,
                    () -> startLocateLevel(level, structureTag, pos, searchRadius, false));
        }
        return startLocateLevel(level, structureTag, pos, searchRadius, true);
    }

    private static LocateTask<BlockPos> startLocateLevel(
            ServerLevel level, TagKey<Structure> structureTag, BlockPos pos, int searchRadius, boolean skipKnown) {
        CompletableFuture<BlockPos> completableFuture = new CompletableFuture<>();
        Future<?> future = submitTask(
                completableFuture,
                () -> doLocateLevel(completableFuture, level, structureTag, pos, searchRadius, skipKnown));
        return new LocateTask<>(level.getServer(), completableFuture, future);
    }

    /**
     * Queues a task to locate a feature using
     * {@link ChunkGenerator#findNearestMapStructure(ServerLevel, HolderSet, BlockPos, int, boolean)} and returns a
     * {@link LocateTask} with the futures for it.
     */
    public static LocateTask<Pair<BlockPos, Holder<Structure>>> locate(
            ServerLevel level,
            HolderSet<Structure> structureSet,
            BlockPos pos,
            int searchRadius,
            boolean skipKnownStructures) {
        ALConstants.logDebug(
                "Creating locate task for {} in {} around {} within {} chunks", structureSet, level, pos, searchRadius);

        if (!skipKnownStructures) {
            return coalesced(
                    level,
                    structureSet,
                    pos,
                    searchRadius,
                    () -> startLocateChunkGenerator(level, structureSet, pos, searchRadius, false));
        }
        return startLocateChunkGenerator(level, structureSet, pos, searchRadius, true);
    }

    private static LocateTask<Pair<BlockPos, Holder<Structure>>> startLocateChunkGenerator(
            ServerLevel level, HolderSet<Structure> structureSet, BlockPos pos, int searchRadius, boolean skipKnown) {
        CompletableFuture<Pair<BlockPos, Holder<Structure>>> completableFuture = new CompletableFuture<>();
        Future<?> future = submitTask(
                completableFuture,
                () -> doLocateChunkGenerator(completableFuture, level, structureSet, pos, searchRadius, skipKnown));
        return new LocateTask<>(level.getServer(), completableFuture, future);
    }

    @SuppressWarnings("unchecked")
    private static <T> LocateTask<T> coalesced(
            ServerLevel level, Object target, BlockPos pos, int searchRadius, Supplier<LocateTask<T>> starter) {
        LocateKey key = new LocateKey(level.dimension(), target, pos.immutable(), searchRadius);
        CompletableFuture<T> shared = (CompletableFuture<T>) PENDING_LOCATES.computeIfAbsent(key, k -> {
            CompletableFuture<T> base = starter.get().completableFuture();
            base.whenComplete((result, throwable) -> PENDING_LOCATES.remove(k, base));
            return base;
        });
        CompletableFuture<T> child = shared.copy();
        return new LocateTask<>(level.getServer(), child, child);
    }

    private static void doLocateLevel(
            CompletableFuture<BlockPos> completableFuture,
            ServerLevel level,
            TagKey<Structure> structureTag,
            BlockPos pos,
            int searchRadius,
            boolean skipExistingChunks) {
        try {
            ALConstants.logDebug(
                    "Trying to locate {} in {} around {} within {} chunks", structureTag, level, pos, searchRadius);
            long start = System.nanoTime();
            BlockPos foundPos = level.findNearestMapStructure(structureTag, pos, searchRadius, skipExistingChunks);
            String time =
                    NumberFormat.getNumberInstance().format(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            if (foundPos == null) ALConstants.logInfo("No {} found (took {}ms)", structureTag, time);
            else ALConstants.logInfo("Found {} at {} (took {}ms)", structureTag, foundPos, time);
            completableFuture.complete(foundPos);
        } catch (Throwable t) {
            ALConstants.logError(t, "Exception while locating {} around {}", structureTag, pos);
            try {
                completableFuture.complete(null);
            } catch (Throwable ignore) {
            }
        }
    }

    private static void doLocateChunkGenerator(
            CompletableFuture<Pair<BlockPos, Holder<Structure>>> completableFuture,
            ServerLevel level,
            HolderSet<Structure> structureSet,
            BlockPos pos,
            int searchRadius,
            boolean skipExistingChunks) {
        try {
            ALConstants.logDebug(
                    "Trying to locate {} in {} around {} within {} chunks", structureSet, level, pos, searchRadius);
            long start = System.nanoTime();
            Pair<BlockPos, Holder<Structure>> foundPair = level.getChunkSource()
                    .getGenerator()
                    .findNearestMapStructure(level, structureSet, pos, searchRadius, skipExistingChunks);
            String time =
                    NumberFormat.getNumberInstance().format(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            if (foundPair == null) ALConstants.logInfo("No {} found (took {}ms)", structureSet, time);
            else
                ALConstants.logInfo(
                        "Found {} at {} (took {}ms)",
                        foundPair.getSecond().value().getClass().getSimpleName(),
                        foundPair.getFirst(),
                        time);
            completableFuture.complete(foundPair);
        } catch (Throwable t) {
            ALConstants.logError(t, "Exception while locating {} around {}", structureSet, pos);
            try {
                completableFuture.complete(null);
            } catch (Throwable ignore) {
            }
        }
    }

    public static LocateTask<Pair<BlockPos, Holder<Biome>>> locateBiome(
            ServerLevel level,
            ResourceOrTagArgument.Result<Biome> biomeResult,
            BlockPos pos,
            int searchRadius,
            int horizontalStep,
            int verticalStep) {
        return locateBiome(
                level, biomeResult, biomeResult.asPrintable(), pos, searchRadius, horizontalStep, verticalStep);
    }

    public static LocateTask<Pair<BlockPos, Holder<Biome>>> locateBiome(
            ServerLevel level,
            Predicate<Holder<Biome>> biomePredicate,
            String printableName,
            BlockPos pos,
            int searchRadius,
            int horizontalStep,
            int verticalStep) {
        ALConstants.logDebug(
                "Creating locate task for biomes {} in {} around {} within {} blocks",
                printableName,
                level,
                pos,
                searchRadius);

        CompletableFuture<Pair<BlockPos, Holder<Biome>>> completableFuture = new CompletableFuture<>();
        Future<?> future = submitTask(
                completableFuture,
                () -> doLocateBiome(
                        completableFuture,
                        level,
                        biomePredicate,
                        printableName,
                        pos,
                        searchRadius,
                        horizontalStep,
                        verticalStep));
        return new LocateTask<>(level.getServer(), completableFuture, future);
    }

    private static void doLocateBiome(
            CompletableFuture<Pair<BlockPos, Holder<Biome>>> completableFuture,
            ServerLevel level,
            Predicate<Holder<Biome>> biomePredicate,
            String printableName,
            BlockPos pos,
            int searchRadius,
            int horizontalStep,
            int verticalStep) {
        try {
            ALConstants.logDebug(
                    "Trying to locate biomes {} in {} around {} within {} blocks",
                    printableName,
                    level,
                    pos,
                    searchRadius);
            long start = System.nanoTime();

            Pair<BlockPos, Holder<Biome>> foundPair =
                    level.findClosestBiome3d(biomePredicate, pos, searchRadius, horizontalStep, verticalStep);

            String time =
                    NumberFormat.getNumberInstance().format(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            if (foundPair == null) {
                ALConstants.logInfo("No biome from {} found (took {}ms)", printableName, time);
            } else {
                ALConstants.logInfo(
                        "Found biome {} at {} (took {}ms)",
                        foundPair.getSecond().value().getClass().getSimpleName(),
                        foundPair.getFirst(),
                        time);
            }
            completableFuture.complete(foundPair);
        } catch (Throwable t) {
            ALConstants.logError(t, "Exception while locating biomes {} around {}", printableName, pos);
            completableFuture.completeExceptionally(t);
        }
    }

    /**
     * Holder of the futures for an async locate task as well as providing some helper functions.
     * The completableFuture will be completed once the call to
     * {@link ServerLevel#findNearestMapStructure(TagKey, BlockPos, int, boolean)} has completed, and will hold the
     * result of it.
     * The taskFuture is the future for the {@link Runnable} itself in the executor service.
     */
    public record LocateTask<T>(MinecraftServer server, CompletableFuture<T> completableFuture, Future<?> taskFuture) {
        /**
         * Helper function that calls {@link CompletableFuture#thenAccept(Consumer)} with the given action.
         * Bear in mind that the action will be executed from the task's thread. If you intend to change any game data,
         * it's strongly advised you use {@link #thenOnServerThread(Consumer)} instead so that it's queued and executed
         * on the main server thread instead.
         */
        public LocateTask<T> then(Consumer<T> action) {
            completableFuture.thenAccept(action);
            return this;
        }

        /**
         * Helper function that calls {@link CompletableFuture#thenAccept(Consumer)} with the given action on the server
         * thread.
         */
        public LocateTask<T> thenOnServerThread(Consumer<T> action) {
            completableFuture.thenAccept(result -> deferToServerThread(() -> action.accept(result)));
            return this;
        }

        /**
         * Executes errorHandler when task fails with an exception (on task's thread).
         */
        public LocateTask<T> onError(Consumer<Throwable> errorHandler) {
            completableFuture.exceptionally(t -> {
                errorHandler.accept(t);
                return null;
            });
            return this;
        }

        /**
         * Executes errorHandler when task fails with an exception (on server thread)
         */
        public LocateTask<T> onErrorOnServerThread(Consumer<Throwable> errorHandler) {
            completableFuture.exceptionally(t -> {
                deferToServerThread(() -> errorHandler.accept(t));
                return null;
            });
            return this;
        }

        /**
         * Handles both success and error cases (on task's thread).
         * @param handler receives (result, throwable) one will always be null
         */
        public LocateTask<T> handle(BiConsumer<T, Throwable> handler) {
            completableFuture.handle((result, throwable) -> {
                handler.accept(result, throwable);
                return null;
            });
            return this;
        }

        /**
         * Handles both success and error cases (on server thread)
         * @param handler receives (result, throwable) one will always be null
         */
        public LocateTask<T> handleOnServerThread(BiConsumer<T, Throwable> handler) {
            completableFuture.handle((result, throwable) -> {
                deferToServerThread(() -> handler.accept(result, throwable));
                return null;
            });
            return this;
        }

        /*
         * Always queues the runnable onto the server thread instead of running it immediately.
         * This avoids callbacks firing before the caller has finished its setup
         */
        private void deferToServerThread(Runnable runnable) {
            server.schedule(new TickTask(server.getTickCount(), runnable));
        }

        /*
         * Fails this task with a {@link TimeoutException} if it
         * does not finish within the given time
         */
        public LocateTask<T> withTimeout(long timeout, TimeUnit unit) {
            completableFuture.orTimeout(timeout, unit);
            return this;
        }

        /*
         * Cancels this caller's futures so pending callbacks are not run.
         * Cancellation is cooperative and does not stop an ongoing search.
         */
        public void cancel() {
            taskFuture.cancel(true);
            completableFuture.cancel(false);
        }
    }
}
