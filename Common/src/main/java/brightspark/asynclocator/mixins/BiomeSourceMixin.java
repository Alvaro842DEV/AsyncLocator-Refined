package brightspark.asynclocator.mixins;

import brightspark.asynclocator.LocateCancellation;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin {
    @ModifyArg(
            method = "findClosestBiome3d",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/QuartPos;fromBlock(I)I", ordinal = 0),
            index = 0)
    private int asynclocator$cancelBeforeSearchColumn(int blockX) {
        LocateCancellation.check();
        return blockX;
    }
}
