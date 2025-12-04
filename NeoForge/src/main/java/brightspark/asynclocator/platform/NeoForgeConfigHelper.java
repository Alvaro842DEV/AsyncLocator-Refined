package brightspark.asynclocator.platform;

import brightspark.asynclocator.AsyncLocatorConfigNeoForge;
import brightspark.asynclocator.platform.services.ConfigHelper;

public class NeoForgeConfigHelper implements ConfigHelper {

	@Override
	public int biomeSearchRadius() {
		return AsyncLocatorConfigNeoForge.BIOME_SEARCH_RADIUS.get();
	}

	@Override
	public boolean removeOffer() {
		return AsyncLocatorConfigNeoForge.REMOVE_OFFER.get();
	}

	@Override
	public boolean dolphinTreasureEnabled() {
		return AsyncLocatorConfigNeoForge.DOLPHIN_TREASURE_ENABLED.get();
	}

	@Override
	public boolean eyeOfEnderEnabled() {
		return AsyncLocatorConfigNeoForge.EYE_OF_ENDER_ENABLED.get();
	}

	@Override
	public boolean explorationMapEnabled() {
		return AsyncLocatorConfigNeoForge.EXPLORATION_MAP_ENABLED.get();
	}

	@Override
	public boolean locateCommandEnabled() {
		return AsyncLocatorConfigNeoForge.LOCATE_COMMAND_ENABLED.get();
	}

	@Override
	public boolean locateBiomeCommandEnabled() {
		return AsyncLocatorConfigNeoForge.LOCATE_BIOME_COMMAND_ENABLED.get();
	}

	@Override
	public boolean villagerTradeEnabled() {
	return AsyncLocatorConfigNeoForge.VILLAGER_TRADE_ENABLED.get();
	}
}
