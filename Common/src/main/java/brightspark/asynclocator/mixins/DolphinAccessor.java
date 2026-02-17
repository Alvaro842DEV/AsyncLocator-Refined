package brightspark.asynclocator.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Dolphin.class)
public interface DolphinAccessor {
	@Accessor("treasurePos")
	void asynclocator$setTreasurePos(BlockPos pos);
}
