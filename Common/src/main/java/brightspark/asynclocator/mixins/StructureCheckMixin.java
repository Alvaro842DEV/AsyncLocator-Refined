package brightspark.asynclocator.mixins;

import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla confines {@code StructureCheck} to the main server thread because its caches are plain
 * unsynchronized maps. {@code ServerLevel#onStructureStartsAvailable} intentionally marshals the only
 * chunk-load-time mutation onto the main thread via { @code server.execute}. Async Locator calls checkStart (and,
 * for skip-known-structures searches,  {@code incrementReference}) from background threads, so every
 * entry point touching those caches must be serialized on the instance monitor.
 */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
    @Inject(method = "checkStart", at = @At("HEAD"), cancellable = true)
    private void asynclocator$synchronizeCheckStart(
            ChunkPos chunkPos,
            Structure structure,
            StructurePlacement placement,
            boolean skipKnownStructures,
            CallbackInfoReturnable<StructureCheckResult> cir) {
        if (Thread.holdsLock(this)) return;
        synchronized (this) {
            cir.setReturnValue(
                    ((StructureCheck) (Object) this).checkStart(chunkPos, structure, placement, skipKnownStructures));
        }
    }

    @Inject(method = "onStructureLoad", at = @At("HEAD"), cancellable = true)
    private void asynclocator$synchronizeOnStructureLoad(
            ChunkPos chunkPos, Map<Structure, StructureStart> structureStarts, CallbackInfo ci) {
        if (Thread.holdsLock(this)) return;
        synchronized (this) {
            ((StructureCheck) (Object) this).onStructureLoad(chunkPos, structureStarts);
        }
        ci.cancel();
    }

    @Inject(method = "incrementReference", at = @At("HEAD"), cancellable = true)
    private void asynclocator$synchronizeIncrementReference(ChunkPos chunkPos, Structure structure, CallbackInfo ci) {
        if (Thread.holdsLock(this)) return;
        synchronized (this) {
            ((StructureCheck) (Object) this).incrementReference(chunkPos, structure);
        }
        ci.cancel();
    }
}
