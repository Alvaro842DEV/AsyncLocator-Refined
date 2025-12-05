package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.platform.Services;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class SlotMixin {
    @Inject(method = "mayPickup", at = @At(value = "HEAD"), cancellable = true)
    public void preventPickupOfPendingExplorationMap(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (Services.CONFIG.explorationMapEnabled()) {
            // Cast this to Slot to access getItem() method directly
            Slot slot = (Slot) (Object) this;
            if (CommonLogic.isEmptyPendingMap(slot.getItem())) {
                ALConstants.logDebug("Intercepted Slot#mayPickup call");
                cir.setReturnValue(false);
            }
        }
    }
}
