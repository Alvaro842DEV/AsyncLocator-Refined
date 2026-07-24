package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.logic.ExplorationMapFunctionLogic;
import brightspark.asynclocator.logic.MerchantLogic;
import brightspark.asynclocator.platform.Services;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplorationMapFunction.class)
public abstract class ExplorationMapFunctionMixin {

    @Shadow
    @Final
    TagKey<Structure> destination;

    @Shadow
    @Final
    byte zoom;

    @Shadow
    @Final
    int searchRadius;

    @Shadow
    @Final
    boolean skipKnownStructures;

    @Unique
    private ResourceKey<MapDecorationType> asyncLocator$decorationTypeKey;

    @Inject(
            method = "<init>(Ljava/util/List;Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/Holder;BIZ)V",
            at = @At("RETURN"))
    private void asyncLocator$captureDecorationKey(
            List<LootItemCondition> conditions,
            TagKey<Structure> destination,
            Holder<MapDecorationType> decoration,
            byte zoom,
            int searchRadius,
            boolean skipKnownStructures,
            CallbackInfo ci) {
        decoration.unwrapKey().ifPresentOrElse(key -> this.asyncLocator$decorationTypeKey = key, () -> {
            ALConstants.logWarn(
                    "Failed to find registered key for MapDecorationType Holder {} in ExplorationMapFunction constructor",
                    decoration);
            this.asyncLocator$decorationTypeKey = null;
        });
    }

    @Unique
    private Optional<Holder<MapDecorationType>> asyncLocator$getDecorationHolder(LootContext context) {
        if (this.asyncLocator$decorationTypeKey == null) return Optional.empty();
        return context.getLevel()
                .registryAccess()
                .registry(Registries.MAP_DECORATION_TYPE)
                .flatMap(registry -> registry.getHolder(this.asyncLocator$decorationTypeKey));
    }

    @Inject(
            method =
                    "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"),
            cancellable = true)
    private void asyncLocator$locateAsync(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        if (!Services.CONFIG.explorationMapEnabled()) return;
        if (context.getParamOrNull(LootContextParams.ORIGIN) == null) return;

        Optional<Holder<MapDecorationType>> decoration = asyncLocator$getDecorationHolder(context);
        if (decoration.isEmpty()) {
            ALConstants.logError(
                    "Couldn't resolve exploration map decoration type {}, falling back to vanilla map creation",
                    this.asyncLocator$decorationTypeKey);
            return;
        }

        ServerLevel serverLevel = context.getLevel();
        BlockPos originPos = BlockPos.containing(context.getParam(LootContextParams.ORIGIN));

        ALConstants.logDebug("Intercepting exploration map creation for {}.", destination.location());

        MapItemSavedData mapData = MapItemSavedData.createFresh(
                originPos.getX(), originPos.getZ(), this.zoom, false, false, serverLevel.dimension());
        MapId newMapId = serverLevel.getFreeMapId();
        serverLevel.setMapData(newMapId, mapData);

        ItemStack pendingMapStack = CommonLogic.createManagedMap();
        pendingMapStack.set(DataComponents.MAP_ID, newMapId);
        CommonLogic.LootrTarget lootrTarget = CommonLogic.getActiveLootrTarget();
        ALConstants.logDebug("Assigned MapId {} to exploration map ItemStack.", newMapId);

        AsyncLocator.locate(serverLevel, destination, originPos, searchRadius, skipKnownStructures)
                .handleOnServerThread((foundPos, throwable) -> {
                    if (throwable != null) {
                        ALConstants.logError(
                                throwable,
                                "Exploration map locate for {} failed - invalidating map",
                                destination.location());
                        foundPos = null;
                    }
                    asyncLocator$handleLocateResult(
                            serverLevel, context, pendingMapStack, lootrTarget, decoration.get(), foundPos);
                });

        cir.setReturnValue(pendingMapStack);
    }

    @Unique
    private void asyncLocator$handleLocateResult(
            ServerLevel serverLevel,
            LootContext context,
            ItemStack pendingMapStack,
            @Nullable CommonLogic.LootrTarget lootrTarget,
            Holder<MapDecorationType> decoration,
            @Nullable BlockPos foundPos) {
        if (!AsyncLocator.isLevelActive(serverLevel)) {
            ALConstants.logDebug("Exploration map locate result arrived after its server level stopped");
            return;
        }

        Component mapName = ExplorationMapFunctionLogic.getCachedName(pendingMapStack);
        BlockPos inventoryPos = context.getParamOrNull(LootContextParams.ORIGIN) != null
                ? BlockPos.containing(context.getParam(LootContextParams.ORIGIN))
                : null;

        boolean merchantUpdated = false;
        var thisEntity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (thisEntity instanceof AbstractVillager merchant) {
            if (merchant.isRemoved() || !merchant.isAlive() || merchant.level() != serverLevel) {
                ALConstants.logDebug("Merchant is no longer active when its exploration map result arrived");
                return;
            }
            UUID targetId = CommonLogic.getTrackingUUID(pendingMapStack);
            if (targetId != null) {
                for (var offer : merchant.getOffers()) {
                    var result = offer.getResult();
                    UUID offerId = CommonLogic.getTrackingUUID(result);
                    if (targetId.equals(offerId)) {
                        if (foundPos != null) {
                            ALConstants.logDebug("Finalizing map in merchant offer (UUID: {})", offerId);
                            CommonLogic.finalizeMap(result, serverLevel, foundPos, this.zoom, decoration, mapName);
                        } else {
                            ALConstants.logDebug("Clearing pending map in merchant offer (UUID: {})", offerId);
                            CommonLogic.clearPendingState(result);
                        }
                        merchantUpdated = true;
                        break;
                    }
                }
            } else {
                ALConstants.logWarn("Managed map lacks tracking UUID in trade context: cannot match offer result");
            }
        }

        if (!merchantUpdated) {
            if (foundPos != null) {
                ALConstants.logInfo(
                        "Async location found for exploration map {}: {}", destination.location(), foundPos);

                boolean updated = CommonLogic.tryUpdateMapInLootrTarget(
                        serverLevel, lootrTarget, pendingMapStack, foundPos, this.zoom, decoration, mapName);

                if (!updated && inventoryPos != null) {
                    Services.EXPLORATION_MAP_FUNCTION_LOGIC.updateMap(
                            pendingMapStack, serverLevel, foundPos, this.zoom, decoration, inventoryPos, mapName);
                } else if (!updated) {
                    CommonLogic.finalizeMap(pendingMapStack, serverLevel, foundPos, this.zoom, decoration, mapName);
                }
            } else {
                ALConstants.logInfo(
                        "Async location not found for exploration map {} -> Invalidating", destination.location());

                boolean invalidated =
                        CommonLogic.tryInvalidateMapInLootrTarget(serverLevel, lootrTarget, pendingMapStack);

                if (!invalidated && inventoryPos != null) {
                    Services.EXPLORATION_MAP_FUNCTION_LOGIC.invalidateMap(pendingMapStack, serverLevel, inventoryPos);
                } else if (!invalidated) {
                    ALConstants.logWarn("Cannot invalidate exploration map - no player container or ORIGIN parameter.");
                    CommonLogic.clearPendingState(pendingMapStack);
                }
            }
        }

        if (thisEntity instanceof AbstractVillager merchant) {
            MerchantLogic.sendOffersToTradingPlayer(merchant);
        }
    }
}
