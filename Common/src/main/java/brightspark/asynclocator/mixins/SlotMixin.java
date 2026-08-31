package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.platform.Services;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Slot.class)
public class SlotMixin {
    @ModifyReturnValue(method = "mayPickup", at = @At("RETURN"))
    private boolean preventPickupOfPendingExplorationMap(boolean original) {
        if (Services.CONFIG.explorationMapEnabled()) {
            Slot slot = (Slot) (Object) this;
            if (CommonLogic.isEmptyPendingMap(slot.getItem())) {
                ALConstants.logDebug("Intercepted Slot#mayPickup call");
                return false;
            }
        }
        return original;
    }
}
