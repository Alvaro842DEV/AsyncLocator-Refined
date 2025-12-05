package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.lang.ref.WeakReference;
import java.util.Optional;

public class EnderEyeItemLogic {
	private EnderEyeItemLogic() {}

	// Cache for EYE_OF_ENDER_LOCATED, avoids expensive registry lookups on every throw
	private static WeakReference<ServerLevel> cachedLevel = new WeakReference<>(null);
	private static HolderSet<Structure> cachedHolderSet = null;

	private static Optional<HolderSet<Structure>> getEyeOfEnderHolderSet(ServerLevel level) {
		if (cachedLevel.get() == level && cachedHolderSet != null) {
			ALConstants.logDebug("Using cached EYE_OF_ENDER_LOCATED HolderSet");
			return Optional.of(cachedHolderSet);
		}

		try {
			Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
			Optional<HolderSet.Named<Structure>> optionalSet = registry.getTag(StructureTags.EYE_OF_ENDER_LOCATED);
			
			if (optionalSet.isPresent()) {
				cachedLevel = new WeakReference<>(level);
				cachedHolderSet = optionalSet.get();
				ALConstants.logDebug("Cached EYE_OF_ENDER_LOCATED HolderSet for level {}", level.dimension().location());
				return Optional.of(cachedHolderSet);
			} else {
				ALConstants.logWarn("EYE_OF_ENDER_LOCATED tag not found in structure registry");
				return Optional.empty();
			}
		} catch (Throwable t) {
			ALConstants.logError(t, "Failed to resolve HolderSet for EYE_OF_ENDER_LOCATED");
			return Optional.empty();
		}
	}

	public static void locateAsync(ServerLevel level, Player player, EyeOfEnder eyeOfEnder, EnderEyeItem enderEyeItem) {
		final long timeoutSeconds = 20L;

		// Use cached HolderSet lookup for better performance
		Optional<HolderSet<Structure>> holderSetOpt = getEyeOfEnderHolderSet(level);
		
		if (holderSetOpt.isEmpty()) {
			// Fallback to TagKey-based locate if HolderSet unavailable
			ALConstants.logWarn("HolderSet unavailable, using TagKey fallback for EyeOfEnder locate");
			locateAsyncWithTagKey(level, player, eyeOfEnder, enderEyeItem, timeoutSeconds);
			return;
		}

		HolderSet<Structure> holderSet = holderSetOpt.get();
		((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(true);

		var locateTask = AsyncLocator.locate(
			level,
			holderSet,
			player.blockPosition(),
			100,
			false
		);
		
		locateTask.completableFuture()
			.orTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
			.whenComplete((pair, throwable) -> locateTask.server().submit(() -> {
				((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(false);

				// Entity may have been removed/unloaded while we were locating
				if (!eyeOfEnder.isAlive() || eyeOfEnder.isRemoved()) {
					ALConstants.logDebug("EyeOfEnder no longer alive when locate result arrived");
					return;
				}

				if (throwable instanceof java.util.concurrent.TimeoutException) {
					handleTimeout(level, eyeOfEnder, locateTask, timeoutSeconds);
					return;
				} else if (throwable != null) {
					ALConstants.logError(throwable, "Exception while locating for EyeOfEnder");
					eyeOfEnder.discard();
					return;
				}

				if (pair != null) {
					ALConstants.logInfo(
						"Location found - updating eye of ender entity (structure: {})",
						pair.getSecond().value().getClass().getSimpleName()
					);
					try {
						eyeOfEnder.signalTo(pair.getFirst());
					} catch (Throwable t) {
						ALConstants.logError(t, "Failed to signal EyeOfEnder to position {}", pair.getFirst());
					}
					if (player instanceof ServerPlayer sp) {
						CriteriaTriggers.USED_ENDER_EYE.trigger(sp, pair.getFirst());
					}
					player.awardStat(Stats.ITEM_USED.get(enderEyeItem));
				} else {
					ALConstants.logInfo("No location found - removing eye of ender entity");
					eyeOfEnder.discard();
				}
			}));
	}

	private static void locateAsyncWithTagKey(ServerLevel level, Player player, EyeOfEnder eyeOfEnder, EnderEyeItem enderEyeItem, long timeoutSeconds) {
		((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(true);
		
		var locateTask = AsyncLocator.locate(
			level,
			StructureTags.EYE_OF_ENDER_LOCATED,
			player.blockPosition(),
			100,
			false
		);
		
		locateTask.completableFuture()
			.orTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
			.whenComplete((pos, throwable) -> locateTask.server().submit(() -> {
				((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(false);

				if (!eyeOfEnder.isAlive() || eyeOfEnder.isRemoved()) {
					ALConstants.logDebug("EyeOfEnder no longer alive when locate result arrived");
					return;
				}

				if (throwable instanceof java.util.concurrent.TimeoutException) {
					handleTimeout(level, eyeOfEnder, locateTask, timeoutSeconds);
					return;
				} else if (throwable != null) {
					ALConstants.logError(throwable, "Exception while locating using TagKey for EyeOfEnder");
					eyeOfEnder.discard();
					return;
				}

				if (pos != null) {
					ALConstants.logInfo("Location found - updating eye of ender entity");
					try {
						eyeOfEnder.signalTo(pos);
					} catch (Throwable t) {
						ALConstants.logError(t, "Failed to signal EyeOfEnder to position {}", pos);
					}
					if (player instanceof ServerPlayer sp) {
						CriteriaTriggers.USED_ENDER_EYE.trigger(sp, pos);
					}
					player.awardStat(Stats.ITEM_USED.get(enderEyeItem));
				} else {
					ALConstants.logInfo("No location found - removing eye of ender entity");
					eyeOfEnder.discard();
				}
			}));
	}

	private static void handleTimeout(ServerLevel level, EyeOfEnder eyeOfEnder, AsyncLocator.LocateTask<?> locateTask, long timeoutSeconds) {
		ALConstants.logWarn("EyeOfEnder locate timed out after {}s, dropping item and removing entity", timeoutSeconds);
		try { locateTask.cancel(); } catch (Throwable ignore) {}
		net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
			level,
			eyeOfEnder.getX(), eyeOfEnder.getY(), eyeOfEnder.getZ(),
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE)
		);
		level.addFreshEntity(drop);
		eyeOfEnder.discard();
	}
}
