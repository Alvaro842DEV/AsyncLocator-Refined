package brightspark.asynclocator.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.entity.animal.Dolphin$DolphinSwimToTreasureGoal")
public interface DolphinSwimToTreasureGoalStuckAccessor {
    @Accessor("stuck")
    void asynclocator$setStuck(boolean stuck);
}
