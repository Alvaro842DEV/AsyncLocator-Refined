package brightspark.asynclocator.mixins;

import brightspark.asynclocator.LocateChunkLoader;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @WrapMethod(method = "tryAddReference")
    private static boolean asynclocator$claimStructureReferenceAtomically(
            StructureManager structureManager, StructureStart structureStart, Operation<Boolean> original) {
        synchronized (structureStart) {
            return original.call(structureManager, structureStart);
        }
    }

    // Each candidate chunk a locate search inspects would otherwise block the server thread until it has loaded
    @WrapOperation(
            method = "getStructureGeneratingAt",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/LevelReader;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private static ChunkAccess asynclocator$loadCandidateChunkOffServerThread(
            LevelReader level, int x, int z, ChunkStatus status, Operation<ChunkAccess> original) {
        ChunkAccess chunk = LocateChunkLoader.tryLoad(level, x, z, status);
        return chunk != null ? chunk : original.call(level, x, z, status);
    }
}
