package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.logic.ExplorationMapFunctionLogic;
import brightspark.asynclocator.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SetNameFunction.class)
public abstract class SetNameFunctionMixin {
    @Inject(
            method =
                    "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true)
    private void asyncLocator$cacheNameForPendingMap(
            ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        if (Services.CONFIG.explorationMapEnabled() && CommonLogic.isEmptyPendingMap(stack)) {
            Component nameToSet = getNameFromSetNameFunction();

            // If a name was determined, cache it and cancel the original method
            if (nameToSet != null) {
                ALConstants.logDebug(
                        "SetNameFunctionMixin: Caching name '{}' for pending map and cancelling original set.",
                        nameToSet.getString());
                ExplorationMapFunctionLogic.cacheName(stack, nameToSet);
                cir.setReturnValue(stack);
            } else {
                ALConstants.logDebug(
                        "SetNameFunctionMixin: No direct name component found for pending map, letting original method run.");
            }
        } else {
            ALConstants.logDebug(
                    "SetNameFunctionMixin: Not a pending map or feature disabled, letting original method run.");
        }
    }

    private Component getNameFromSetNameFunction() {
        try {
            SetNameFunctionAccessor accessor = (SetNameFunctionAccessor) (Object) this;
            return accessor.getName().orElse(null);
        } catch (Throwable t) {
            ALConstants.logWarn("Failed to access name via SetNameFunctionAccessor: {}", t.getMessage());
            return null;
        }
    }
}
