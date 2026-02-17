package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.AsyncLocator.LocateTask;
import brightspark.asynclocator.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.Optional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.animal.Dolphin$DolphinSwimToTreasureGoal", priority = 800)
public class DolphinSwimToTreasureGoalMixin {
	private LocateTask<?> locateTask = null;

	@Shadow @Final
	private Dolphin dolphin;

	@Redirect(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"))
	public BlockPos redirectFindNearestMapStructure(ServerLevel level,
			net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> structureTag,
			BlockPos pos, int searchRadius, boolean skipKnownStructures) {
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
		((DolphinAccessor) (Object) this.dolphin).asynclocator$setTreasurePos(null);
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

private void handleFindTreasureAsync(ServerLevel level, BlockPos origin) {
		try {
			Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			Optional<HolderSet.Named<Structure>> holderSetOpt = registry.get(StructureTags.DOLPHIN_LOCATED);
			if (holderSetOpt.isPresent()) {
				HolderSet<Structure> set = holderSetOpt.get();
				locateTask = AsyncLocator
						.locate(level, set, origin, 50, false)
						.thenOnServerThread(pair -> handleLocationFound(level, pair == null ? null : pair.getFirst()));
				return;
			} else {
				ALConstants.logWarn("DOLPHIN_LOCATED tag not found; falling back to ServerLevel.findNearestMapStructure path");
			}
		} catch (Throwable t) {
			ALConstants.logError(t, "Failed to resolve HolderSet for DOLPHIN_LOCATED");
		}

		locateTask = AsyncLocator
				.locate(level, StructureTags.DOLPHIN_LOCATED, origin, 50, false)
				.thenOnServerThread(pos -> handleLocationFound(level, pos));
	}

	private void handleLocationFound(ServerLevel level, BlockPos pos) {
		locateTask = null;
		if (pos != null) {
			((DolphinAccessor) (Object) this.dolphin).asynclocator$setTreasurePos(pos);
			((DolphinSwimToTreasureGoalStuckAccessor) (Object) this).asynclocator$setStuck(false);
			level.broadcastEntityEvent(this.dolphin, (byte) 38);
			ALConstants.logInfo("Location found at {} - dolphin will now swim to treasure", pos);
		} else {
			ALConstants.logInfo("No location found - dolphin will continue normal behavior");
		}
	}
}
