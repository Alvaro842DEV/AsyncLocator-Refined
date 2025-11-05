package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.logic.ExplorationMapFunctionLogic;
import brightspark.asynclocator.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

	@Inject(method = "<init>(Ljava/util/List;Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/Holder;BIZ)V",
			at = @At("RETURN"))
	private void captureDecorationKey(List<LootItemCondition> conditions, TagKey<Structure> dest, Holder<MapDecorationType> typeHolder, byte zm, int radius, boolean skip, CallbackInfo ci) {
		typeHolder.unwrapKey().ifPresentOrElse(
			key -> this.asyncLocator$decorationTypeKey = key,
			() -> {
				ALConstants.logWarn("Failed to find registered key for MapDecorationType Holder {} in ExplorationMapFunction constructor", typeHolder);
				this.asyncLocator$decorationTypeKey = null;
			}
		);
	}

	@Unique
	private Optional<Holder<MapDecorationType>> getDecorationHolderFromKey(LootContext context) {
		if (this.asyncLocator$decorationTypeKey == null) return Optional.empty();
		return context.getLevel().registryAccess().lookup(Registries.MAP_DECORATION_TYPE)
			.flatMap(registry -> registry.get(this.asyncLocator$decorationTypeKey));
	}

	@Unique
	private static void asyncLocator$refreshMerchantUIIfApplicable(LootContext context) {
		var entity = context.hasParameter(LootContextParams.THIS_ENTITY)
			? context.getParameter(LootContextParams.THIS_ENTITY)
			: null;
		if (entity instanceof AbstractVillager merchant) {
			if (merchant.getTradingPlayer() instanceof ServerPlayer tradingPlayer) {
				int villagerLevel = merchant instanceof Villager villager ? villager.getVillagerData().level() : 1;
				tradingPlayer.sendMerchantOffers(
					tradingPlayer.containerMenu.containerId,
					merchant.getOffers(),
					villagerLevel,
					merchant.getVillagerXp(),
					merchant.showProgressBar(),
					merchant.canRestock()
				);
				ALConstants.logDebug("Refreshed merchant offers for trade UI");
			}
		}
	}

	@Redirect(
		method = "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/MapItem;create(Lnet/minecraft/server/level/ServerLevel;IIBZZ)Lnet/minecraft/world/item/ItemStack;"
		)
	)
	private ItemStack redirectMapItemCreate(
		ServerLevel serverLevel, int x, int z, byte scale, boolean trackingPosition, boolean unlimitedTracking,
		ItemStack originalStack_usedByRun, LootContext context_usedByRun
	) {
		LootContext context = context_usedByRun;

		if (!Services.CONFIG.explorationMapEnabled()) {
			return MapItem.create(serverLevel, x, z, scale, trackingPosition, unlimitedTracking);
		}

		Optional<Holder<MapDecorationType>> mapDecorationHolderOpt = getDecorationHolderFromKey(context);
		if (mapDecorationHolderOpt.isEmpty()) {
			ALConstants.logError("ExplorationMap Redirect: Couldn't get MapDecorationType Holder for key {}, falling back to vanilla map creation.", this.asyncLocator$decorationTypeKey);
			return MapItem.create(serverLevel, x, z, scale, trackingPosition, unlimitedTracking);
		}

		ALConstants.logDebug("Redirecting MapItem.create for async locator exploration map {}.", destination.location());

		BlockPos originPos = context.hasParameter(LootContextParams.ORIGIN)
			? BlockPos.containing(context.getParameter(LootContextParams.ORIGIN))
			: BlockPos.containing(x, serverLevel.getHeight() / 2, z);

		MapItemSavedData mapData = MapItemSavedData.createFresh(
			x,
			z,
			this.zoom,
			false,
			false,
			serverLevel.dimension()
		);

		MapId newMapId = serverLevel.getFreeMapId();
		serverLevel.setMapData(newMapId, mapData);
		ALConstants.logDebug("Saved initial MapItemSavedData for new MapId {} for exploration map.", newMapId);

        ItemStack pendingMapStack = CommonLogic.createManagedMap();
		pendingMapStack.set(DataComponents.MAP_ID, newMapId);
		ALConstants.logDebug("Assigned MapId {} to exploration map ItemStack.", newMapId);

		AsyncLocator.locate(serverLevel, destination, originPos, searchRadius, skipKnownStructures)
			.thenOnServerThread(foundPos -> {
				Component mapName = ExplorationMapFunctionLogic.getCachedName(pendingMapStack);
				BlockPos inventoryPos = context.hasParameter(LootContextParams.ORIGIN)
					? BlockPos.containing(context.getParameter(LootContextParams.ORIGIN))
					: null;

		// First, try to update merchant offer result directly
		boolean merchantUpdated = false;
			var thisEntity = context.hasParameter(LootContextParams.THIS_ENTITY)
				? context.getParameter(LootContextParams.THIS_ENTITY)
				: null;
			if (thisEntity instanceof AbstractVillager merchant) {
				UUID targetId = CommonLogic.getTrackingUUID(pendingMapStack);
				if (targetId != null) {
					for (var offer : merchant.getOffers()) {
						var result = offer.getResult();
						UUID offerId = CommonLogic.getTrackingUUID(result);
						if (targetId.equals(offerId)) {
							if (foundPos != null) {
								ALConstants.logDebug("Finalizing map in merchant offer (UUID: {})", offerId);
								CommonLogic.finalizeMap(result, serverLevel, foundPos, this.zoom, mapDecorationHolderOpt.get(), mapName);
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
					ALConstants.logInfo("Async location found for exploration map {}: {}", destination.location(), foundPos);
					if (inventoryPos != null) {
						// Update the map in the inventory
						Services.EXPLORATION_MAP_FUNCTION_LOGIC.updateMap(
							pendingMapStack, serverLevel, foundPos, this.zoom,
							mapDecorationHolderOpt.get(), inventoryPos, mapName
						);
					} else {
						// if it can't find the container, finalize the map
						CommonLogic.finalizeMap(pendingMapStack, serverLevel, foundPos, this.zoom, mapDecorationHolderOpt.get(), mapName);
					}
				} else {
					ALConstants.logInfo("Async location not found for exploration map {} -> Invalidating map in inventory (if possible)", destination.location());
                    if (inventoryPos != null) {
                        Services.EXPLORATION_MAP_FUNCTION_LOGIC.invalidateMap(pendingMapStack, serverLevel, inventoryPos);
                    } else {
                        ALConstants.logWarn("Cannot invalidate exploration map - LootContext lacks ORIGIN parameter.");
                        CommonLogic.clearPendingState(pendingMapStack);
                    }
				}
			}
				asyncLocator$refreshMerchantUIIfApplicable(context);
			});

		return pendingMapStack;
	}
}
