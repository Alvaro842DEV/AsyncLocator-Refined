package brightspark.asynclocator;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public class AsyncLocatorConfigNeoForge {

    private static final int DEFAULT_MAX_CONCURRENT_LOCATES = 2;
    private static final int MIN_MAX_CONCURRENT_LOCATES = 1;
    private static final int MAX_MAX_CONCURRENT_LOCATES = 256;
    private static final int DEFAULT_MAX_QUEUED_LOCATES = 128;
    private static final int MIN_MAX_QUEUED_LOCATES = 0;
    private static final int MAX_MAX_QUEUED_LOCATES = 10_000;
    private static final int DEFAULT_BIOME_RADIUS = 6400;
    private static final int MIN_BIOME_RADIUS = 1600;
    private static final int MAX_BIOME_RADIUS = 12800;

    public static ModConfigSpec SPEC;
    public static ConfigValue<Integer> MAX_CONCURRENT_LOCATES;
    public static ConfigValue<Integer> MAX_QUEUED_LOCATES;
    public static ConfigValue<Integer> BIOME_SEARCH_RADIUS;
    public static ConfigValue<Boolean> REMOVE_OFFER;

    // Feature toggles
    public static ConfigValue<Boolean> DOLPHIN_TREASURE_ENABLED;
    public static ConfigValue<Boolean> EYE_OF_ENDER_ENABLED;
    public static ConfigValue<Boolean> EXPLORATION_MAP_ENABLED;
    public static ConfigValue<Boolean> LOCATE_COMMAND_ENABLED;
    public static ConfigValue<Boolean> LOCATE_BIOME_COMMAND_ENABLED;
    public static ConfigValue<Boolean> VILLAGER_TRADE_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_CONCURRENT_LOCATES = builder.comment(
                        "Maximum locate searches that may execute concurrently.",
                        "The default value is recommended for most servers. Higher values can improve throughput,",
                        "but may increase CPU, disk, chunk-generation, and main-thread completion load.")
                .defineInRange(
                        "maxConcurrentLocates",
                        DEFAULT_MAX_CONCURRENT_LOCATES,
                        MIN_MAX_CONCURRENT_LOCATES,
                        MAX_MAX_CONCURRENT_LOCATES);
        MAX_QUEUED_LOCATES = builder.comment(
                        "Maximum locate searches that may wait for execution capacity.",
                        "Requests above the active plus queued limit fail immediately instead of consuming unbounded resources.")
                .defineInRange(
                        "maxQueuedLocates", DEFAULT_MAX_QUEUED_LOCATES, MIN_MAX_QUEUED_LOCATES, MAX_MAX_QUEUED_LOCATES);
        BIOME_SEARCH_RADIUS = builder.comment(
                        "Maximum search radius in blocks for /locate biome command.",
                        "The vanilla value is 6400.",
                        "It is not recommended to change the value unless you want to test something or have some issue.")
                .defineInRange("biomeSearchRadius", DEFAULT_BIOME_RADIUS, MIN_BIOME_RADIUS, MAX_BIOME_RADIUS);

        REMOVE_OFFER = builder.comment(
                        "When a merchant's treasure map offer ends up not finding a feature location,",
                        "remove the offer instead of marking it out of stock.")
                .define("removeMerchantInvalidMapOffer", false);

        builder.push("Feature Toggles");
        DOLPHIN_TREASURE_ENABLED = builder.comment(
                        "If true, enables asynchronous locating of structures for dolphin treasures.")
                .define("dolphinTreasureEnabled", true);
        EYE_OF_ENDER_ENABLED = builder.comment(
                        "If true, enables asynchronous locating of structures when Eyes Of Ender are thrown.")
                .define("eyeOfEnderEnabled", true);
        EXPLORATION_MAP_ENABLED = builder.comment(
                        "If true, enables asynchronous locating of structures for exploration maps found in chests.")
                .define("explorationMapEnabled", true);
        LOCATE_COMMAND_ENABLED = builder.comment(
                        "If true, enables asynchronous locating of structures for the locate command.")
                .define("locateCommandEnabled", true);
        LOCATE_BIOME_COMMAND_ENABLED = builder.comment(
                        "If true, enables asynchronous locating of biomes for the locate command.")
                .define("locateBiomeCommandEnabled", true);
        VILLAGER_TRADE_ENABLED = builder.comment(
                        "If true, enables asynchronous locating of structures for villager trades.")
                .define("villagerTradeEnabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    // Add validation method
    public static void validateConfig() {
        boolean needsSave = false;

        int biomeRadius = BIOME_SEARCH_RADIUS.get();
        if (biomeRadius < MIN_BIOME_RADIUS || biomeRadius > MAX_BIOME_RADIUS) {
            ALConstants.logError(
                    "Invalid biomeSearchRadius value ({}). Must be between {}-{}. Resetting to default ({}).",
                    biomeRadius,
                    MIN_BIOME_RADIUS,
                    MAX_BIOME_RADIUS,
                    DEFAULT_BIOME_RADIUS);
            BIOME_SEARCH_RADIUS.set(DEFAULT_BIOME_RADIUS);
            needsSave = true;
        }

        if (needsSave) {
            SPEC.save();
            ALConstants.logInfo("Config values corrected and saved");
        }
    }
}
