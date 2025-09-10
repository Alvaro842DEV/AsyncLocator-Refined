package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CommonLogic {
	private static final String MAP_HOVER_NAME_KEY = "menu.working";
	private static final String PENDING_MARKER = "asynclocator.pending";
	private static final String UUID_TRACKER = PENDING_MARKER + ".uuid";

	private CommonLogic() {}

	// Creates an empty "Filled Map", marks it as locating, and gives it a temporary name
	public static ItemStack createEmptyMap() {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.ITEM_NAME, Component.translatable(MAP_HOVER_NAME_KEY));
		CompoundTag customData = new CompoundTag();
		customData.putByte(PENDING_MARKER, (byte) 1);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
		return stack;
	}

	public static ItemStack createManagedMap() {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.ITEM_NAME, Component.translatable(MAP_HOVER_NAME_KEY));
		CompoundTag customData = new CompoundTag();
		customData.putUUID(UUID_TRACKER, UUID.randomUUID());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
		return stack;
	}

	// This way it will render correctly in the GUI
	public static ItemStack createMerchantMap(ServerLevel level) {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		
		MapItemSavedData mapData = MapItemSavedData.createFresh(
			0, 0, (byte) 2, true, true, level.dimension()
		);
		
		MapId newMapId = level.getFreeMapId();
		stack.set(DataComponents.MAP_ID, newMapId);
		level.setMapData(newMapId, mapData);
		
		stack.set(DataComponents.ITEM_NAME, Component.translatable(MAP_HOVER_NAME_KEY));
		stack.set(ALDataComponents.LOCATING, Unit.INSTANCE);
		
		return stack;
	}

	// Check if FILLED_MAP is pending
	public static boolean isEmptyPendingMap(ItemStack stack) {
		if (!stack.is(Items.FILLED_MAP)) {
			return false;
		}
		if (stack.has(ALDataComponents.LOCATING)) return true;
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) return false;
		return customData.contains(PENDING_MARKER) || customData.contains(UUID_TRACKER);
	}

	// Retrieves the tracking UUID stoerd on a managed pending map
	public static @Nullable java.util.UUID getTrackingUUID(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) return null;
		CompoundTag tag = customData.copyTag();
		return tag.hasUUID(UUID_TRACKER) ? tag.getUUID(UUID_TRACKER) : null;
	}

	public static void clearPendingState(ItemStack mapStack) {
		mapStack.remove(ALDataComponents.LOCATING);
		
	CustomData currentData = mapStack.get(DataComponents.CUSTOM_DATA);
		if (currentData != null) {
			CompoundTag newTag = currentData.copyTag();
			newTag.remove(PENDING_MARKER);
			newTag.remove(UUID_TRACKER);
			if (newTag.isEmpty()) {
				mapStack.remove(DataComponents.CUSTOM_DATA);
			} else {
				mapStack.set(DataComponents.CUSTOM_DATA, CustomData.of(newTag));
			}
		}
	}
	
	// Updates the data of the map
	public static void finalizeMap(
		ItemStack mapStack,
		ServerLevel level,
		BlockPos pos,
		int scale,
		Holder<MapDecorationType> destinationType,
		@Nullable Component displayName
	) {
		MapId existingId = mapStack.get(DataComponents.MAP_ID);
		MapId mapId = existingId != null ? existingId : level.getFreeMapId();

		// Create or replace map data with proper settings
		MapItemSavedData mapData = MapItemSavedData.createFresh(
			pos.getX(),
			pos.getZ(),
			(byte) scale,
			true,
			true,
			level.dimension()
		);
		level.setMapData(mapId, mapData);
		if (existingId == null) {
			mapStack.set(DataComponents.MAP_ID, mapId);
		}
		
		MapItem.renderBiomePreviewMap(level, mapStack);
		MapItemSavedData.addTargetDecoration(mapStack, pos, "+", destinationType);
		
		if (displayName != null) {
			mapStack.set(DataComponents.ITEM_NAME, displayName);
		}
		/** 
		 * If no displayName was provided, we keep whatever name the map already has
		 * so we don't wipe titles set by other mods or loot
		 */
		
	clearPendingState(mapStack);
	}

	// Legacy method for compatibility, delegates to finalizeMap
	public static void completeMapUpdate(
		ItemStack mapStack,
		ServerLevel level,
		BlockPos pos,
		Holder<MapDecorationType> destinationTypeHolder,
		@Nullable Component displayName
	) {
		finalizeMap(mapStack, level, pos, 2, destinationTypeHolder, displayName);
	}

	/**
	 * Broadcasts slot changes to all players that have the chest container open.
	 * Won't do anything if the BlockEntity isn't an instance of {@link ChestBlockEntity}.
	 * Keep for backward compatibility (new method: broadcastContainerChanges)
	 */
	public static void broadcastChestChanges(ServerLevel level, BlockEntity be) {
		if (!(be instanceof ChestBlockEntity chestBE))
			return;

		level.players().forEach(player -> {
			AbstractContainerMenu container = player.containerMenu;
			if (container instanceof ChestMenu chestMenu && chestMenu.getContainer() == chestBE) {
				chestMenu.broadcastChanges();
			}
		});
	}

	// This works with any container
	public static void broadcastContainerChanges(ServerLevel level, BlockEntity be, net.minecraft.world.Container container) {
		level.players().forEach(player -> {
			AbstractContainerMenu menu = player.containerMenu;
			// ChestMenu is used by both chests and barrels
			if (menu instanceof ChestMenu chestMenu && chestMenu.getContainer() == container) {
				chestMenu.broadcastChanges();
			}
		});
	}
}
