package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.AsyncLocator.LocateTask;
import brightspark.asynclocator.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.animal.Dolphin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.animal.Dolphin$DolphinSwimToTreasureGoal")
public class DolphinSwimToTreasureGoalMixin {
	private LocateTask<BlockPos> locateTask = null;
	private BlockPos asyncFoundPos = null;

	@Redirect(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"))
	public BlockPos redirectFindNearestMapStructure(ServerLevel level,
			net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> structureTag,
			BlockPos pos, int searchRadius, boolean skipKnownStructures) {
		if (!Services.CONFIG.dolphinTreasureEnabled()) {
			// If disabled, use vanilla behavior
			return level.findNearestMapStructure(structureTag, pos, searchRadius, skipKnownStructures);
		}

		ALConstants.logDebug("Intercepted DolphinSwimToTreasureGoal findNearestMapStructure call");

		// Start async task and reset any stale position
		asyncFoundPos = null;
		handleFindTreasureAsync(level, pos);
		return null;
	}

    // Keep goal alive while an async locating task is ongoing
	@Inject(method = "canContinueToUse", at = @At(value = "HEAD"), cancellable = true)
	public void continueToUseIfLocatingTreasure(CallbackInfoReturnable<Boolean> cir) {
		if (locateTask != null || asyncFoundPos != null) {
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
        asyncFoundPos = null;
	}

    /*
     * Skip ticking while a locate task is active so dolphin 
     * doesn't try to go towards an old treasure position
     */
	@Inject(method = "tick", at = @At(value = "HEAD"), cancellable = true)
	public void skipTickingIfLocatingTreasure(CallbackInfo ci) {
		if (locateTask != null && asyncFoundPos == null) {
			ci.cancel();
		}
	}

	// Redirect calls to getTreasurePos() to return our async found position
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Dolphin;getTreasurePos()Lnet/minecraft/core/BlockPos;"))
	public BlockPos redirectGetTreasurePos(Dolphin dolphin) {
		if (asyncFoundPos != null) {
			return asyncFoundPos;
		}
		return dolphin.getTreasurePos();
	}

	private static final long DOLPHIN_LOCATE_TIMEOUT_SECONDS = 30L;

	private void handleFindTreasureAsync(ServerLevel level, BlockPos blockPos) {
		locateTask = AsyncLocator.locate(level, StructureTags.DOLPHIN_LOCATED, blockPos, 50, false);
		locateTask.completableFuture()
			.orTimeout(DOLPHIN_LOCATE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
			.whenComplete((pos, throwable) -> locateTask.server().submit(() -> {
				if (throwable instanceof java.util.concurrent.TimeoutException) {
					ALConstants.logWarn("Dolphin treasure locate timed out after {}s - resetting goal", DOLPHIN_LOCATE_TIMEOUT_SECONDS);
					try { locateTask.cancel(); } catch (Throwable ignore) {}
					locateTask = null;
					asyncFoundPos = null;
					return;
				} else if (throwable != null) {
					ALConstants.logError(throwable, "Exception while locating treasure for dolphin");
					locateTask = null;
					asyncFoundPos = null;
					return;
				}
				handleLocationFound(level, pos);
			}));
	}

	private void handleLocationFound(ServerLevel level, BlockPos pos) {
		locateTask = null;
		asyncFoundPos = pos;
		if (pos != null) {
			ALConstants.logInfo("Location found at {} - dolphin will now swim to treasure", pos);
		} else {
			ALConstants.logInfo("No location found - dolphin will continue normal behavior");
		}
	}
}
