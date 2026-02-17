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
import net.minecraft.world.phys.Vec3;

public class EnderEyeItemLogic {
	private EnderEyeItemLogic() {}

	public static void locateAsync(ServerLevel level, Player player, EyeOfEnder eyeOfEnder, EnderEyeItem enderEyeItem) {
		final long timeoutSeconds = 60L;

		/*
		 * Locate with ChunkGenerator using HolderSet(StructureTags.EYE_OF_ENDER_LOCATED) for better compatibility
		 * with structure overhaul mods (e.g., YUNG's, Dungeons and Taverns...)
		 */
		try {
			Registry<net.minecraft.world.level.levelgen.structure.Structure> registry =
				level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			java.util.Optional<HolderSet.Named<net.minecraft.world.level.levelgen.structure.Structure>> optionalSet =
				registry.get(StructureTags.EYE_OF_ENDER_LOCATED);
			if (optionalSet.isPresent()) {
				HolderSet<net.minecraft.world.level.levelgen.structure.Structure> holderSet = optionalSet.get();

				((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(true);

				var locateTask = AsyncLocator.locate(
					level,
					holderSet,
					player.blockPosition(),
					100,
					false
				);
				var timed = locateTask.completableFuture().orTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
				timed.whenComplete((pair, throwable) -> locateTask.server().submit(() -> {
					((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(false);

					// Entity may have been removed/unloaded while we were locating
					if (!eyeOfEnder.isAlive() || eyeOfEnder.isRemoved()) {
						ALConstants.logDebug("EyeOfEnder no longer alive when locate result arrived");
						return;
					}

					if (throwable instanceof java.util.concurrent.TimeoutException) {
						ALConstants.logWarn("EyeOfEnder locate timed out after {}s, dropping item and removing entity", timeoutSeconds);
						try { locateTask.cancel(); } catch (Throwable ignore) {}
						net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
							level,
							eyeOfEnder.getX(), eyeOfEnder.getY(), eyeOfEnder.getZ(),
							new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE)
						);
						level.addFreshEntity(drop);
						eyeOfEnder.discard();
						return;
					} else if (throwable != null) {
						ALConstants.logError(throwable, "Exception while locating using HolderSet for EyeOfEnder");
						ALConstants.logInfo("No location found - removing eye of ender entity");
						eyeOfEnder.discard();
						return;
					}

					if (pair != null) {
						ALConstants.logInfo(
							"Location found - updating eye of ender entity (structure: {})",
							pair.getSecond().value().getClass().getSimpleName()
						);
						try {
							eyeOfEnder.signalTo(new Vec3(pair.getFirst()));
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
				return;
			} else {
				ALConstants.logWarn("EYE_OF_ENDER_LOCATED tag not found in structure registry");
			}
		} catch (Throwable t) {
			ALConstants.logError(t, "Failed to resolve HolderSet for EYE_OF_ENDER_LOCATED");
		}

		// fallback
		((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(true);
		var locateTask = AsyncLocator.locate(
			level,
			StructureTags.EYE_OF_ENDER_LOCATED,
			player.blockPosition(),
			100,
			false
		);
		var timed = locateTask.completableFuture().orTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
		timed.whenComplete((pos, throwable) -> locateTask.server().submit(() -> {
			((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(false);

			// Entity may have been removed/unloaded while we were locating
			if (!eyeOfEnder.isAlive() || eyeOfEnder.isRemoved()) {
				ALConstants.logDebug("EyeOfEnder no longer alive when locate result arrived");
				return;
			}

			if (throwable instanceof java.util.concurrent.TimeoutException) {
				ALConstants.logWarn("EyeOfEnder locate timed out after {}s, dropping item and removing entity", timeoutSeconds);
				try { locateTask.cancel(); } catch (Throwable ignore) {}
				net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
					level,
					eyeOfEnder.getX(), eyeOfEnder.getY(), eyeOfEnder.getZ(),
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE)
				);
				level.addFreshEntity(drop);
				eyeOfEnder.discard();
				return;
			} else if (throwable != null) {
				ALConstants.logError(throwable, "Exception while locating using TagKey for EyeOfEnder");
				ALConstants.logInfo("No location found - removing eye of ender entity");
				eyeOfEnder.discard();
				return;
			}

			if (pos != null) {
				ALConstants.logInfo("Location found - updating eye of ender entity");
				try {
					eyeOfEnder.signalTo(new Vec3(pos));
				} catch (Throwable t2) {
					ALConstants.logError(t2, "Failed to signal EyeOfEnder to position {}", pos);
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
}
