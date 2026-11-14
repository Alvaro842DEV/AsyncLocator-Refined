package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALConstants;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The biome preview vanilla draws on exploration maps, computed away from the server thread.
 * Vanilla samples 16384 biomes on the server thread when a map is finished, costing hundreds of
 * milliseconds on large maps.
 * This reads biomes straight from the biome source with the level's zoom seed matching vanilla for unloaded chunks.
 * Loaded chunks can differ only where biomes were changed after generation.
 */
public record MapPreview(ResourceKey<Level> dimension, int centerX, int centerZ, int scale, byte[] colors) {
    private static final int SIZE = 128;

    public static @Nullable MapPreview tryRender(ServerLevel level, BlockPos target, int scale) {
        try {
            return render(level, target, scale);
        } catch (RuntimeException exception) {
            if (!(exception instanceof CancellationException)) {
                ALConstants.logWarn(
                        "Failed to compute the map preview off the server thread: {}", exception.toString());
            }
            return null;
        }
    }

    static MapPreview render(ServerLevel level, BlockPos target, int scale) {
        int[] center = mapCenter(target, scale);
        int centerX = center[0];
        int centerZ = center[1];

        boolean[] watery = sample(
                offThreadBiomes(level), centerX, centerZ, scale, biome -> biome.is(BiomeTags.WATER_ON_MAP_OUTLINES));
        return new MapPreview(level.dimension(), centerX, centerZ, scale, colorize(watery));
    }

    public static BiomeManager offThreadBiomes(ServerLevel level) {
        return level.getBiomeManager().withDifferentSource(level::getUncachedNoiseBiome);
    }

    // The center MapItemSavedData#createFresh gives the map containing target
    public static int[] mapCenter(BlockPos target, int scale) {
        int blocksPerMap = SIZE * (1 << scale);
        return new int[] {
            Mth.floor((target.getX() + 64.0) / blocksPerMap) * blocksPerMap + blocksPerMap / 2 - 64,
            Mth.floor((target.getZ() + 64.0) / blocksPerMap) * blocksPerMap + blocksPerMap / 2 - 64
        };
    }

    /**
     * The biome sampling loop of MapItem#renderBiomePreviewMap, indexed by z * 128 + x
     * Vanilla tests each biome against BiomeTags#WATER_ON_MAP_OUTLINES
     */
    public static boolean[] sample(
            BiomeManager biomes, int centerX, int centerZ, int scale, Predicate<Holder<Biome>> predicate) {
        int blocksPerPixel = 1 << scale;
        int minX = centerX / blocksPerPixel - 64;
        int minZ = centerZ / blocksPerPixel - 64;
        boolean[] matches = new boolean[SIZE * SIZE];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < SIZE; z++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Map preview interrupted");
            for (int x = 0; x < SIZE; x++) {
                Holder<Biome> biome =
                        biomes.getBiome(pos.set((minX + x) * blocksPerPixel, 0, (minZ + z) * blocksPerPixel));
                matches[z * SIZE + x] = predicate.test(biome);
            }
        }
        return matches;
    }

    // The coloring loop of MapItem#renderBiomePreviewMap, kept identical to vanilla
    public static byte[] colorize(boolean[] watery) {
        byte[] colors = new byte[SIZE * SIZE];
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                int wateryNeighbours = 0;
                for (int dx = -1; dx < 2; dx++) {
                    for (int dz = -1; dz < 2; dz++) {
                        if ((dx != 0 || dz != 0) && watery[(z + dz) * SIZE + x + dx]) {
                            wateryNeighbours++;
                        }
                    }
                }

                MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
                MapColor color = MapColor.NONE;
                if (watery[z * SIZE + x]) {
                    color = MapColor.COLOR_ORANGE;
                    if (wateryNeighbours > 7 && z % 2 == 0) {
                        switch ((x + (int) (Mth.sin(z + 0.0F) * 7.0F)) / 8 % 5) {
                            case 0, 4 -> brightness = MapColor.Brightness.LOW;
                            case 1, 3 -> brightness = MapColor.Brightness.NORMAL;
                            case 2 -> brightness = MapColor.Brightness.HIGH;
                            default -> {}
                        }
                    } else if (wateryNeighbours > 7) {
                        color = MapColor.NONE;
                    } else if (wateryNeighbours > 5) {
                        brightness = MapColor.Brightness.NORMAL;
                    } else if (wateryNeighbours > 1) {
                        brightness = MapColor.Brightness.LOW;
                    }
                } else if (wateryNeighbours > 0) {
                    color = MapColor.COLOR_BROWN;
                    brightness = wateryNeighbours > 3 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOWEST;
                }

                if (color != MapColor.NONE) {
                    colors[z * SIZE + x] = color.getPackedId(brightness);
                }
            }
        }
        return colors;
    }

    /**
     * Copies the preview into newly created map data on the server thread.
     */
    public boolean applyTo(MapItemSavedData data) {
        if (data.dimension != dimension || data.centerX != centerX || data.centerZ != centerZ || data.scale != scale) {
            return false;
        }
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                byte color = colors[z * SIZE + x];
                if (color != 0) data.setColor(x, z, color);
            }
        }
        return true;
    }
}
