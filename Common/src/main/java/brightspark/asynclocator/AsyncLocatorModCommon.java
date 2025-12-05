package brightspark.asynclocator;

import brightspark.asynclocator.platform.Services;
import brightspark.asynclocator.platform.services.ConfigHelper;

public class AsyncLocatorModCommon {
    public static void printConfigs() {
        ConfigHelper config = Services.CONFIG;
        ALConstants.logInfo("Configs:" + "\nBiome Search Radius: "
                + config.biomeSearchRadius() + "\nRemove Offer: "
                + config.removeOffer() + "\nDolphin Treasure Enabled: "
                + config.dolphinTreasureEnabled() + "\nEye Of Ender Enabled: "
                + config.eyeOfEnderEnabled() + "\nExploration Map Enabled: "
                + config.explorationMapEnabled() + "\nLocate Structure Command Enabled: "
                + config.locateCommandEnabled() + "\nLocate Biome Command Enabled: "
                + config.locateBiomeCommandEnabled() + "\nVillager Trade Enabled: "
                + config.villagerTradeEnabled());
    }
}
