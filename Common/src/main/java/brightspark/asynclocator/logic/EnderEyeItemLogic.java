package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.AsyncLocator.LocateTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public class EnderEyeItemLogic {
    private static final long LOCATE_TIMEOUT_SECONDS = 60L;

    private EnderEyeItemLogic() {}

    /*
     * Uses the vanilla TagKey overload, which resolves EYE_OF_ENDER_LOCATED through the same
     * ChunkGenerator search as a manual HolderSet lookup would, so structure overhaul mods
     * (e.g. YUNG's, Dungeons and Taverns...) are supported identically.
     */
    public static void locateAsync(ServerLevel level, Player player, EyeOfEnder eyeOfEnder, EnderEyeItem enderEyeItem) {
        ((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(true);

        LocateTask<BlockPos> locateTask = AsyncLocator.locate(
                        level, StructureTags.EYE_OF_ENDER_LOCATED, player.blockPosition(), 100, false)
                .withTimeout(LOCATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        locateTask.handleOnServerThread((pos, throwable) ->
                handleLocateResult(level, player, eyeOfEnder, enderEyeItem, locateTask, pos, throwable));
    }

    private static void handleLocateResult(
            ServerLevel level,
            Player player,
            EyeOfEnder eyeOfEnder,
            EnderEyeItem enderEyeItem,
            LocateTask<BlockPos> locateTask,
            @Nullable BlockPos pos,
            @Nullable Throwable throwable) {
        ((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(false);

        // Entity may have been removed/unloaded while we were locating
        if (!eyeOfEnder.isAlive() || eyeOfEnder.isRemoved()) {
            ALConstants.logDebug("EyeOfEnder no longer alive when locate result arrived");
            return;
        }

        if (throwable instanceof TimeoutException) {
            ALConstants.logWarn(
                    "EyeOfEnder locate timed out after {}s, refunding item and removing entity",
                    LOCATE_TIMEOUT_SECONDS);
            locateTask.cancel();
            refundAndDiscard(level, player, eyeOfEnder);
            return;
        }
        if (throwable != null) {
            ALConstants.logError(throwable, "Exception while locating structure for EyeOfEnder");
            refundAndDiscard(level, player, eyeOfEnder);
            return;
        }
        if (pos == null) {
            ALConstants.logInfo("No location found - refunding item and removing eye of ender entity");
            refundAndDiscard(level, player, eyeOfEnder);
            return;
        }

        ALConstants.logInfo("Location found - updating eye of ender entity");
        eyeOfEnder.signalTo(pos);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.USED_ENDER_EYE.trigger(serverPlayer, pos);
        }
        player.awardStat(Stats.ITEM_USED.get(enderEyeItem));
    }

    /*
     * Vanilla only consumes the ender eye once a location was found, but by the time the async
     * result arrives the item has already been consumed, so give it back on any failure
     */
    private static void refundAndDiscard(ServerLevel level, Player player, EyeOfEnder eyeOfEnder) {
        if (!player.hasInfiniteMaterials()) {
            level.addFreshEntity(new ItemEntity(
                    level, eyeOfEnder.getX(), eyeOfEnder.getY(), eyeOfEnder.getZ(), new ItemStack(Items.ENDER_EYE)));
        }
        eyeOfEnder.discard();
    }
}
