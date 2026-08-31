package brightspark.asynclocator.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Vanilla confines {@code StructureCheck} to the main server thread because its caches are plain
 * unsynchronized maps. {@code ServerLevel#onStructureStartsAvailable} intentionally marshals the only
 * chunk-load-time mutation onto the main thread via { @code server.execute}. Async Locator calls checkStart (and,
 * for skip-known-structures searches,  {@code incrementReference}) from background threads, so every
 * entry point touching those caches must be serialized on the instance monitor.
 */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
    @WrapMethod(method = "checkStart")
    private StructureCheckResult asynclocator$synchronizeCheckStart(
            ChunkPos chunkPos,
            Structure structure,
            StructurePlacement placement,
            boolean skipKnownStructures,
            Operation<StructureCheckResult> original) {
        synchronized (this) {
            return original.call(chunkPos, structure, placement, skipKnownStructures);
        }
    }

    @WrapMethod(method = "onStructureLoad")
    private void asynclocator$synchronizeOnStructureLoad(
            ChunkPos chunkPos, Map<Structure, StructureStart> structureStarts, Operation<Void> original) {
        synchronized (this) {
            original.call(chunkPos, structureStarts);
        }
    }

    @WrapMethod(method = "incrementReference")
    private void asynclocator$synchronizeIncrementReference(
            ChunkPos chunkPos, Structure structure, Operation<Void> original) {
        synchronized (this) {
            original.call(chunkPos, structure);
        }
    }
}
