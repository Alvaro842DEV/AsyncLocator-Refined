package brightspark.asynclocator.platform;

import brightspark.asynclocator.AsyncLocatorConfigFabric;
import brightspark.asynclocator.platform.services.ConfigHelper;

public class FabricConfigHelper implements ConfigHelper {
    private record Snapshot(
            int maxConcurrentLocates,
            int maxQueuedLocates,
            int biomeSearchRadius,
            boolean removeOffer,
            boolean dolphinTreasureEnabled,
            boolean eyeOfEnderEnabled,
            boolean explorationMapEnabled,
            boolean locateCommandEnabled,
            boolean locateBiomeCommandEnabled,
            boolean villagerTradeEnabled) {}

    private static volatile Snapshot snapshot = snapshotFromConfig();

    public static void refresh() {
        snapshot = snapshotFromConfig();
    }

    private static Snapshot snapshotFromConfig() {
        return new Snapshot(
                AsyncLocatorConfigFabric.MAX_CONCURRENT_LOCATES,
                AsyncLocatorConfigFabric.MAX_QUEUED_LOCATES,
                AsyncLocatorConfigFabric.BIOME_SEARCH_RADIUS,
                AsyncLocatorConfigFabric.REMOVE_OFFER,
                AsyncLocatorConfigFabric.FeatureToggles.DOLPHIN_TREASURE_ENABLED,
                AsyncLocatorConfigFabric.FeatureToggles.EYE_OF_ENDER_ENABLED,
                AsyncLocatorConfigFabric.FeatureToggles.EXPLORATION_MAP_ENABLED,
                AsyncLocatorConfigFabric.FeatureToggles.LOCATE_COMMAND_ENABLED,
                AsyncLocatorConfigFabric.FeatureToggles.LOCATE_BIOME_COMMAND_ENABLED,
                AsyncLocatorConfigFabric.FeatureToggles.VILLAGER_TRADE_ENABLED);
    }

    @Override
    public int maxConcurrentLocates() {
        return snapshot.maxConcurrentLocates();
    }

    @Override
    public int maxQueuedLocates() {
        return snapshot.maxQueuedLocates();
    }

    @Override
    public int biomeSearchRadius() {
        return snapshot.biomeSearchRadius();
    }

    @Override
    public boolean removeOffer() {
        return snapshot.removeOffer();
    }

    @Override
    public boolean dolphinTreasureEnabled() {
        return snapshot.dolphinTreasureEnabled();
    }

    @Override
    public boolean eyeOfEnderEnabled() {
        return snapshot.eyeOfEnderEnabled();
    }

    @Override
    public boolean explorationMapEnabled() {
        return snapshot.explorationMapEnabled();
    }

    @Override
    public boolean locateCommandEnabled() {
        return snapshot.locateCommandEnabled();
    }

    @Override
    public boolean locateBiomeCommandEnabled() {
        return snapshot.locateBiomeCommandEnabled();
    }

    @Override
    public boolean villagerTradeEnabled() {
        return snapshot.villagerTradeEnabled();
    }
}
