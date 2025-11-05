package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.mixins.MerchantOfferAccess;
import brightspark.asynclocator.platform.Services;
import brightspark.asynclocator.ALDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class MerchantLogic {
	private MerchantLogic() {}


	public static void invalidateMap(AbstractVillager merchant, ItemStack mapStack) {
		mapStack.set(DataComponents.ITEM_NAME, Component.translatable("item.minecraft.map"));
		mapStack.remove(brightspark.asynclocator.ALDataComponents.LOCATING);

		merchant.getOffers()
			.stream()
			.filter(offer -> offer.getResult() == mapStack)
			.findFirst()
			.ifPresentOrElse(
				offer -> removeOffer(merchant, offer),
				() -> ALConstants.logWarn("Failed to find merchant offer for map stack instance used in invalidateMap")
			);
	}

	public static void removeOffer(AbstractVillager merchant, MerchantOffer offer) {
		if (Services.CONFIG.removeOffer()) {
			if (merchant.getOffers().remove(offer)) ALConstants.logInfo("Removed merchant map offer");
			else ALConstants.logWarn("Failed to remove merchant map offer");
		} else {
			((MerchantOfferAccess) offer).setMaxUses(0);
			offer.setToOutOfStock();
			ALConstants.logInfo("Marked merchant map offer as out of stock");
		}
	}

	public static void handleLocationFound(
		ServerLevel level,
		AbstractVillager merchant,
		ItemStack mapStack,
		@Nullable String displayNameKey,
		Holder<MapDecorationType> destinationTypeHolder,
		@Nullable BlockPos pos
	) {
		if (pos == null) {
			ALConstants.logInfo("No location found - invalidating merchant offer");
			invalidateMap(merchant, mapStack);
		} else {
			ALConstants.logInfo("Location found at {} - updating treasure map in merchant offer", pos);
			Component nameComponent = (displayNameKey == null || displayNameKey.isEmpty()) ? null : Component.translatable(displayNameKey);
			CommonLogic.finalizeMap(mapStack, level, pos, 2, destinationTypeHolder, nameComponent);
		}

		if (merchant.getTradingPlayer() instanceof ServerPlayer tradingPlayer) {
			ALConstants.logInfo("Player {} currently trading - updating merchant offers", tradingPlayer);

			int villagerLevel = merchant instanceof Villager villager ? villager.getVillagerData().level() : 1;
			tradingPlayer.sendMerchantOffers(
				tradingPlayer.containerMenu.containerId,
				merchant.getOffers(),
				villagerLevel,
				merchant.getVillagerXp(),
				merchant.showProgressBar(),
				merchant.canRestock()
			);
		}
	}

	public static MerchantOffer updateMapAsync(
		Entity pTrader,
		int emeraldCost,
		String displayNameKey,
		Holder<MapDecorationType> destinationTypeHolder,
		int maxUses,
		int villagerXp,
		TagKey<Structure> destination
	) {
		return updateMapAsyncInternal(
			pTrader,
			emeraldCost,
			maxUses,
			villagerXp,
			(level, merchant, mapStack) -> AsyncLocator.locate(level, destination, merchant.blockPosition(), 100, true)
				.thenOnServerThread(pos -> handleLocationFound(
					level,
					merchant,
					mapStack,
					displayNameKey,
					destinationTypeHolder,
					pos
				))
		);
	}

	public static MerchantOffer updateMapAsync(
		Entity pTrader,
		int emeraldCost,
		String displayNameKey,
		Holder<MapDecorationType> destinationTypeHolder,
		int maxUses,
		int villagerXp,
		HolderSet<Structure> structureSet
	) {
		return updateMapAsyncInternal(
			pTrader,
			emeraldCost,
			maxUses,
			villagerXp,
			(level, merchant, mapStack) -> AsyncLocator.locate(level, structureSet, merchant.blockPosition(), 100, true)
				.thenOnServerThread(pair -> {
					BlockPos pos = pair != null ? pair.getFirst() : null;
					handleLocationFound(
					level,
					merchant,
					mapStack,
						displayNameKey,
						destinationTypeHolder,
						pos
					);
				})
		);
	}

	private static MerchantOffer updateMapAsyncInternal(
		Entity trader, int emeraldCost, int maxUses, int villagerXp, MapUpdateTask task
	) {
        if (trader instanceof AbstractVillager merchant && trader.level() instanceof ServerLevel serverLevel) {
            // Use specialized method for merchant maps that creates proper MapId immediately
            ItemStack mapStack = CommonLogic.createMerchantMap(serverLevel);
            ALConstants.logDebug("Created merchant map with MapId {} for offer", mapStack.get(DataComponents.MAP_ID));

            // Start async task with the properly initialized map
            task.apply(serverLevel, merchant, mapStack);

            // Create the offdr with the map that has a valid ID
            ItemCost emeraldItemCost = new ItemCost(Items.EMERALD, emeraldCost);
            Optional<ItemCost> compassCost = Optional.of(new ItemCost(Items.COMPASS));
			return new MerchantOffer(
                emeraldItemCost,
                compassCost,
				mapStack,
				maxUses,
				villagerXp,
				0.2F
			);
		} else {
			ALConstants.logInfo(
                "Merchant is not of type {} or level is not ServerLevel - not running async logic",
				AbstractVillager.class.getSimpleName()
			);
			return null;
		}
	}

	public interface MapUpdateTask {
		void apply(ServerLevel level, AbstractVillager merchant, ItemStack mapStack);
	}
}
