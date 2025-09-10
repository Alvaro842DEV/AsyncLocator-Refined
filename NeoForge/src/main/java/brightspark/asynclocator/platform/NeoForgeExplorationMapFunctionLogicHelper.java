package brightspark.asynclocator.platform;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.platform.services.ExplorationMapFunctionLogicHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.BiConsumer;

public class NeoForgeExplorationMapFunctionLogicHelper
	implements ExplorationMapFunctionLogicHelper {

	@Override
	public void invalidateMap(
		ItemStack mapStack,
		ServerLevel level,
		BlockPos invPos
	) {
		boolean updated = handleUpdateMapInChest(mapStack, level, invPos, (handler, slot) -> {
			ALConstants.logDebug(
				"Invalidating map in Forge inventory slot {}",
				slot
			);
			if (handler instanceof IItemHandlerModifiable modifiableHandler) {
				modifiableHandler.setStackInSlot(
					slot,
					new ItemStack(Items.MAP)
				);
			} else {
				ItemStack extracted = handler.extractItem(
					slot,
					mapStack.getCount(),
					false
				);
				if (!extracted.isEmpty()) {
					handler.insertItem(slot, new ItemStack(Items.MAP), false);
				}
			}
		});
		if (!updated) {
			ALConstants.logDebug("NeoForge invalidateMap fallback: no container/slot match. Clearing pending state in-place.");
			CommonLogic.clearPendingState(mapStack);
		}
	}

	@Override
	public void updateMap(
		ItemStack mapStack,
		ServerLevel level,
		BlockPos pos,
		int scale,
		Holder<MapDecorationType> destinationTypeHolder,
		BlockPos invPos,
		@Nullable Component displayName
	) {
		CommonLogic.finalizeMap(
			mapStack,
			level,
			pos,
			scale,
			destinationTypeHolder,
			displayName
		);

		boolean updated = handleUpdateMapInChest(mapStack, level, invPos, (handler, slot) -> {
			ItemStack actualStack = handler.getStackInSlot(slot);

			CommonLogic.finalizeMap(actualStack, level, pos, scale, destinationTypeHolder, displayName);
			// finalize the actual stack
			ALConstants.logDebug("Updated map in NeoForge inventory slot {}, broadcasting changes.", slot);

			if (handler instanceof IItemHandlerModifiable modifiableHandler) {
				modifiableHandler.setStackInSlot(slot, actualStack);
			} else {
				handler.extractItem(slot, actualStack.getCount(), false);
				handler.insertItem(slot, actualStack, false);
			}
		});
		if (!updated) {
			ALConstants.logDebug("NeoForge updateMap fallback: no container/slot match. Pending map already finalized in-place.");
		}
	}

	private static boolean handleUpdateMapInChest(
		ItemStack mapStackToFind,
		ServerLevel level,
		BlockPos inventoryPos,
		BiConsumer<IItemHandler, Integer> handleSlotFound
	) {
		BlockEntity be = level.getBlockEntity(inventoryPos);
		if (be != null) {
			IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, inventoryPos, null);
			if (itemHandler != null) {
				boolean found = false;
				UUID targetId = CommonLogic.getTrackingUUID(mapStackToFind);

				if (targetId != null) {
					for (int i = 0; i < itemHandler.getSlots(); i++) {
						ItemStack slotStack = itemHandler.getStackInSlot(i);
						UUID slotId = CommonLogic.getTrackingUUID(slotStack);
						if (targetId.equals(slotId)) {
							handleSlotFound.accept(itemHandler, i);
							CommonLogic.broadcastChestChanges(level, be);
							found = true;
							break;
						}
					}
				}
				if (!found) {
					ALConstants.logWarn(
						"Could not find map with UUID {} in {} at {}",
						targetId,
						be.getClass().getSimpleName(),
						inventoryPos
					);
				}
				return found;
			} else {
				ALConstants.logWarn(
					"Couldn't find item handler capability on block entity {} at {}",
					be.getClass().getSimpleName(),
					inventoryPos
				);
				return false;
			}
		} else {
			ALConstants.logWarn(
				"Couldn't find block entity at inventory position {} in level {}",
				inventoryPos,
				level.dimension().location()
			);
			return false;
		}
	}
}