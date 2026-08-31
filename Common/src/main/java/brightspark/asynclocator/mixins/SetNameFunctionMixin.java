package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.logic.ExplorationMapFunctionLogic;
import brightspark.asynclocator.platform.Services;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SetNameFunction.class)
public abstract class SetNameFunctionMixin {
    @Shadow
    @Final
    private Optional<LootContext.EntityTarget> resolutionContext;

    @WrapOperation(
            method =
                    "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private void asyncLocator$cacheResolvedNameForPendingMap(
            Optional<Component> name,
            Consumer<Component> setter,
            Operation<Void> original,
            ItemStack stack,
            LootContext context) {
        if (Services.CONFIG.explorationMapEnabled() && CommonLogic.isEmptyPendingMap(stack)) {
            name.ifPresent(component -> {
                Component resolvedName = SetNameFunction.createResolver(context, resolutionContext.orElse(null))
                        .apply(component);
                ALConstants.logDebug(
                        "SetNameFunctionMixin: Caching resolved name '{}' for pending map.", resolvedName.getString());
                ExplorationMapFunctionLogic.cacheName(stack, resolvedName);
            });
            return;
        }

        original.call(name, setter);
    }
}
