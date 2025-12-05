package brightspark.asynclocator;

import brightspark.asynclocator.SparkConfig.Category;
import brightspark.asynclocator.SparkConfig.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class AsyncLocatorConfigFabric {

    private static final int DEFAULT_BIOME_RADIUS = 6400;
    private static final int MIN_BIOME_RADIUS = 1600;
    private static final int MAX_BIOME_RADIUS = 12800;

    @Config(value = "biomeSearchRadius", comment = """
			Maximum search radius in blocks for /locate biome command.
			The vanilla value is 6400.
			It is not recommended to change the value unless you want to test something or have some issue.
			""", min = MIN_BIOME_RADIUS, max = MAX_BIOME_RADIUS)
    public static int BIOME_SEARCH_RADIUS = DEFAULT_BIOME_RADIUS;

    @Config(value = "removeMerchantInvalidMapOffer", comment = """
			When a merchant's treasure map offer ends up not finding a feature location,
			whether the offer should be removed or marked as out of stock.
			""")
    public static boolean REMOVE_OFFER = false;

    @Category("Feature Toggles")
    public static class FeatureToggles {
        @Config(
                value = "dolphinTreasureEnabled",
                comment = "If true, enables asynchronous locating of structures for dolphin treasures.")
        public static boolean DOLPHIN_TREASURE_ENABLED = true;

        @Config(
                value = "eyeOfEnderEnabled",
                comment = "If true, enables asynchronous locating of structures when Eyes Of Ender are thrown.")
        public static boolean EYE_OF_ENDER_ENABLED = true;

        @Config(
                value = "explorationMapEnabled",
                comment = "If true, enables asynchronous locating of structures for exploration maps found in chests.")
        public static boolean EXPLORATION_MAP_ENABLED = true;

        @Config(
                value = "locateCommandEnabled",
                comment = "If true, enables asynchronous locating of structures for the locate command.")
        public static boolean LOCATE_COMMAND_ENABLED = true;

        @Config(
                value = "locateBiomeCommandEnabled",
                comment = "If true, enables asynchronous locating of biomes for the locate command.")
        public static boolean LOCATE_BIOME_COMMAND_ENABLED = true;

        @Config(
                value = "villagerTradeEnabled",
                comment = "If true, enables asynchronous locating of structures for villager trades.")
        public static boolean VILLAGER_TRADE_ENABLED = true;
    }

    private AsyncLocatorConfigFabric() {}

    // Helper method
    private static void resetToDefaults() {
        BIOME_SEARCH_RADIUS = DEFAULT_BIOME_RADIUS;
        REMOVE_OFFER = false;
        FeatureToggles.DOLPHIN_TREASURE_ENABLED = true;
        FeatureToggles.EYE_OF_ENDER_ENABLED = true;
        FeatureToggles.EXPLORATION_MAP_ENABLED = true;
        FeatureToggles.LOCATE_COMMAND_ENABLED = true;
        FeatureToggles.LOCATE_BIOME_COMMAND_ENABLED = true;
        FeatureToggles.VILLAGER_TRADE_ENABLED = true;
    }

    public static void init() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve(ALConstants.MOD_ID + ".properties");

        if (Files.exists(configFile)) {
            ALConstants.logInfo("Config file found");
            try {
                SparkConfig.read(configFile, AsyncLocatorConfigFabric.class);

                // Validate values in case of manual edits
                boolean needsRewrite = false;

                if (BIOME_SEARCH_RADIUS > MAX_BIOME_RADIUS || BIOME_SEARCH_RADIUS < MIN_BIOME_RADIUS) {
                    ALConstants.logError(
                            "Invalid biomeSearchRadius value ({}). Must be between {}-{}. Resetting to default ({}).",
                            BIOME_SEARCH_RADIUS,
                            MIN_BIOME_RADIUS,
                            MAX_BIOME_RADIUS,
                            DEFAULT_BIOME_RADIUS);
                    BIOME_SEARCH_RADIUS = DEFAULT_BIOME_RADIUS;
                    needsRewrite = true;
                }

                if (needsRewrite) {
                    try {
                        SparkConfig.write(configFile, AsyncLocatorConfigFabric.class);
                        ALConstants.logInfo("Config file rewritten with default values");
                    } catch (IOException | IllegalAccessException writeError) {
                        ALConstants.logError(writeError, "Failed to rewrite config file");
                    }
                }

            } catch (IllegalStateException e) {
                if (e.getMessage().contains("greater than the maximum")
                        || e.getMessage().contains("less than the minimum")) {
                    ALConstants.logError(
                            "Invalid config value detected: {}. Resetting to defaults and recreating config.",
                            e.getMessage());
                    resetToDefaults();

                    // Rewrite config with defaults
                    try {
                        SparkConfig.write(configFile, AsyncLocatorConfigFabric.class);
                        ALConstants.logInfo("Config file rewrite with default values");
                    } catch (IOException | IllegalAccessException writeError) {
                        ALConstants.logError(writeError, "Failed to rewrite cpnfig file");
                    }
                } else {
                    ALConstants.logError(e, "Failed to read config file, using defaults");
                    resetToDefaults();
                }
            } catch (IOException | IllegalAccessException e) {
                ALConstants.logError(e, "Failed to read config file {}, using defaults", configFile);
                resetToDefaults();
            }
        } else {
            ALConstants.logInfo("No config file found - creating it");
            try {
                SparkConfig.write(configFile, AsyncLocatorConfigFabric.class);
            } catch (IOException | IllegalAccessException e) {
                ALConstants.logError(e, "Failed to write config file {}", configFile);
            }
        }
    }
}
