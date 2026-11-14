package brightspark.asynclocator.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public record LocatedMap(BlockPos pos, @Nullable MapPreview preview) {
    // Called on Async Locator's result thread
    public static @Nullable LocatedMap prepare(ServerLevel level, @Nullable BlockPos pos, int scale) {
        return pos == null ? null : new LocatedMap(pos, MapPreview.tryRender(level, pos, scale));
    }
}
