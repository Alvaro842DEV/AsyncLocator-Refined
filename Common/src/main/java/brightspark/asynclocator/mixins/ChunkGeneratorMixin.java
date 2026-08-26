package brightspark.asynclocator.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @WrapMethod(method = "tryAddReference")
    private static boolean asynclocator$claimStructureReferenceAtomically(
            StructureManager structureManager, StructureStart structureStart, Operation<Boolean> original) {
        synchronized (structureStart) {
            return original.call(structureManager, structureStart);
        }
    }
}
