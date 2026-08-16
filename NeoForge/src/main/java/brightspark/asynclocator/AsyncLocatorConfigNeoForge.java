package brightspark.asynclocator;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.concurrent.ConcurrentCommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.nio.file.Files;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public class AsyncLocatorConfigNeoForge {
    private static volatile ModConfig loadedConfig;

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

    public static void setLoadedConfig(ModConfig modConfig) {
        loadedConfig = modConfig;
    }

    public static void reload() throws IOException {
        ModConfig modConfig = loadedConfig;
        if (modConfig == null) {
            throw new IllegalStateException("NeoForge config has not finished loading");
        }
        if (modConfig.getFullPath() == null || modConfig.getLoadedConfig() == null) {
            throw new IllegalStateException("NeoForge config is not loaded from a file");
        }

        CommentedConfig parsed;
        try (var reader = Files.newBufferedReader(modConfig.getFullPath())) {
            parsed = new TomlParser().parse(reader);
        }
        validateValues(parsed);
        if (!SPEC.isCorrect(parsed)) {
            ALConstants.logWarn("Config file has missing values or outdated metadata; updating it");
            SPEC.correct(parsed);
        }

        var current = modConfig.getLoadedConfig().config();
        if (current instanceof ConcurrentCommentedConfig concurrent) {
            concurrent.bulkCommentedUpdate(
                    (java.util.function.Consumer<CommentedConfig>) config -> replace(config, parsed));
        } else {
            replace(current, parsed);
        }
        SPEC.acceptConfig(modConfig.getLoadedConfig());
        modConfig.getLoadedConfig().save();
    }

    private static void validateValues(CommentedConfig config) {
        validateInteger(config, "maxConcurrentLocates", MIN_MAX_CONCURRENT_LOCATES, MAX_MAX_CONCURRENT_LOCATES);
        validateInteger(config, "maxQueuedLocates", MIN_MAX_QUEUED_LOCATES, MAX_MAX_QUEUED_LOCATES);
        validateInteger(config, "biomeSearchRadius", MIN_BIOME_RADIUS, MAX_BIOME_RADIUS);
        validateBoolean(config, "removeMerchantInvalidMapOffer");
        validateBoolean(config, "Feature Toggles.dolphinTreasureEnabled");
        validateBoolean(config, "Feature Toggles.eyeOfEnderEnabled");
        validateBoolean(config, "Feature Toggles.explorationMapEnabled");
        validateBoolean(config, "Feature Toggles.locateCommandEnabled");
        validateBoolean(config, "Feature Toggles.locateBiomeCommandEnabled");
        validateBoolean(config, "Feature Toggles.villagerTradeEnabled");
    }

    private static void validateInteger(CommentedConfig config, String path, int minimum, int maximum) {
        Object value = config.get(path);
        if (value == null) return;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            Number number = (Number) value;
            long integer = number.longValue();
            if (integer >= minimum && integer <= maximum) return;
        }
        throw new IllegalArgumentException("Invalid " + path + " value (" + value + "). Must be an integer between "
                + minimum + " and " + maximum);
    }

    private static void validateBoolean(CommentedConfig config, String path) {
        Object value = config.get(path);
        if (value == null || value instanceof Boolean) return;
        throw new IllegalArgumentException("Invalid " + path + " value (" + value + "). Must be true or false");
    }

    private static void replace(CommentedConfig target, CommentedConfig source) {
        target.clear();
        target.clearComments();
        target.putAll(source);
        target.putAllComments(source);
    }
}
