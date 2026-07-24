package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.AsyncLocator.LocateTask;
import brightspark.asynclocator.platform.Services;
import java.util.concurrent.CancellationException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.animal.Dolphin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.animal.Dolphin$DolphinSwimToTreasureGoal", priority = 800)
public class DolphinSwimToTreasureGoalMixin {
    @Unique
    private LocateTask<BlockPos> locateTask = null;

    @Shadow
    @Final
    private Dolphin dolphin;

    @Redirect(
            method = "start",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"))
    public BlockPos redirectFindNearestMapStructure(
            ServerLevel level,
            net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> structureTag,
            BlockPos pos,
            int searchRadius,
            boolean skipKnownStructures) {
        if (!Services.CONFIG.dolphinTreasureEnabled()) {
            // If disabled, use vanilla behavior
            return level.findNearestMapStructure(structureTag, pos, searchRadius, skipKnownStructures);
        }

        ALConstants.logDebug("Intercepted DolphinSwimToTreasureGoal findNearestMapStructure call");

        // Start async task
        handleFindTreasureAsync(level, pos);
        return null;
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void asynclocator$undoVanillaStuckWhenAsync(CallbackInfo ci) {
        if (this.locateTask != null) {
            ((DolphinSwimToTreasureGoalStuckAccessor) (Object) this).asynclocator$setStuck(false);
        }
    }

    // Keep goal alive while an async locating task is ongoing
    @Inject(method = "canContinueToUse", at = @At(value = "HEAD"), cancellable = true)
    public void continueToUseIfLocatingTreasure(CallbackInfoReturnable<Boolean> cir) {
        if (locateTask != null && this.dolphin.gotFish() && this.dolphin.getAirSupply() >= 100) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "stop", at = @At(value = "HEAD"))
    public void stopLocatingTreasure(CallbackInfo ci) {
        if (locateTask != null) {
            ALConstants.logDebug("Locating task ongoing - cancelling during stop()");
            locateTask.cancel();
            locateTask = null;
        }
    }

    /*
     * Skip ticking while a locate task is active so dolphin
     * doesn't try to go towards an old treasure position
     */
    @Inject(method = "tick", at = @At(value = "HEAD"), cancellable = true)
    public void skipTickingIfLocatingTreasure(CallbackInfo ci) {
        if (locateTask != null) {
            ci.cancel();
        }
    }

    /*
     * Uses the vanilla TagKey overload, which resolves DOLPHIN_LOCATED through the same
     * ChunkGenerator search as a manual lookup would.
     */
    @Unique
    private void handleFindTreasureAsync(ServerLevel level, BlockPos origin) {
        locateTask = AsyncLocator.locate(level, StructureTags.DOLPHIN_LOCATED, origin, 50, false)
                .handleOnServerThread((pos, throwable) -> {
                    if (throwable instanceof CancellationException) {
                        ALConstants.logDebug("Dolphin treasure locate task cancelled");
                        return;
                    }
                    if (throwable != null) {
                        ALConstants.logError(throwable, "Dolphin treasure locate failed");
                        pos = null;
                    }
                    handleLocationFound(level, pos);
                });
    }

    @Unique
    private void handleLocationFound(ServerLevel level, @Nullable BlockPos pos) {
        locateTask = null;
        if (!AsyncLocator.isLevelActive(level)
                || this.dolphin.isRemoved()
                || !this.dolphin.isAlive()
                || this.dolphin.level() != level) {
            ALConstants.logDebug("Dolphin is no longer active when its treasure locate result arrived");
            return;
        }

        if (pos != null) {
            this.dolphin.setTreasurePos(pos);
            ((DolphinSwimToTreasureGoalStuckAccessor) (Object) this).asynclocator$setStuck(false);
            level.broadcastEntityEvent(this.dolphin, (byte) 38);
            ALConstants.logInfo("Location found at {} - dolphin will now swim to treasure", pos);
        } else {
            // Mirror vanilla start()'s not-found branch so canContinueToUse() or stop() behave as vanilla
            ((DolphinSwimToTreasureGoalStuckAccessor) (Object) this).asynclocator$setStuck(true);
            ALConstants.logInfo("No location found - dolphin will continue normal behavior");
        }
    }
}
