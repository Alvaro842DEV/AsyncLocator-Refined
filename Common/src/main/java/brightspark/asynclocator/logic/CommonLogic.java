package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.platform.Services;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
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

public class CommonLogic {
    private static final String MAP_HOVER_NAME_KEY = "menu.working";
    private static final String PENDING_MARKER = "asynclocator.pending";
    private static final String UUID_TRACKER = PENDING_MARKER + ".uuid";
    private static final String LOOTR_INVENTORY_CLASS = "noobanidus.mods.lootr.common.data.LootrInventory";
    private static final String LOOTR_DEFAULT_LOOT_FILLER_CLASS =
            "noobanidus.mods.lootr.common.api.data.DefaultLootFiller";
    private static final ConcurrentMap<LootrMethodKey, Method> LOOTR_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean LOOTR_FAILURE_WARNED = new AtomicBoolean();

    private CommonLogic() {}

    public record LootrTarget(
            UUID ownerId, @Nullable UUID sourceId, @Nullable String sourceKey, Container inventory) {}

    private static void upsertAsyncLocatorTag(ItemStack stack, java.util.function.UnaryOperator<CompoundTag> edit) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = (customData != null) ? customData.copyTag() : new CompoundTag();

        tag = edit.apply(tag);

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static void markPending(ItemStack stack, @Nullable UUID uuid) {
        upsertAsyncLocatorTag(stack, tag -> {
            tag.putByte(PENDING_MARKER, (byte) 1);
            if (uuid != null) tag.putString(UUID_TRACKER, uuid.toString());
            return tag;
        });
    }

    private static void clearPendingMarkers(ItemStack stack) {
        upsertAsyncLocatorTag(stack, tag -> {
            tag.remove(PENDING_MARKER);
            tag.remove(UUID_TRACKER);
            return tag;
        });
    }

    // Creates an empty "Filled Map", marks it as locating, and gives it a temporary name
    public static ItemStack createEmptyMap() {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.ITEM_NAME, Component.translatable(MAP_HOVER_NAME_KEY));
        markPending(stack, null);
        return stack;
    }

    public static ItemStack createManagedMap() {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.ITEM_NAME, Component.translatable(MAP_HOVER_NAME_KEY));
        markPending(stack, UUID.randomUUID());
        return stack;
    }

    // This way it will render correctly in the GUI
    public static ItemStack createMerchantMap(ServerLevel level) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);

        MapItemSavedData mapData = MapItemSavedData.createFresh(0, 0, (byte) 2, true, true, level.dimension());

        MapId newMapId = level.getFreeMapId();
        stack.set(DataComponents.MAP_ID, newMapId);
        level.setMapData(newMapId, mapData);

        stack.set(DataComponents.ITEM_NAME, Component.translatable(MAP_HOVER_NAME_KEY));
        markPending(stack, null);

        return stack;
    }

    // Check if FILLED_MAP is pending
    public static boolean isEmptyPendingMap(ItemStack stack) {
        if (!stack.is(Items.FILLED_MAP)) {
            return false;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.contains(PENDING_MARKER) || customData.contains(UUID_TRACKER);
    }

    // Retrieves the tracking UUID stoerd on a managed pending map
    public static @Nullable java.util.UUID getTrackingUUID(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        String raw = tag.getString(UUID_TRACKER).orElse("");
        if (raw.isEmpty()) return null;
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void clearPendingState(ItemStack mapStack) {
        clearPendingMarkers(mapStack);
    }

    // Updates the data of the map
    public static void finalizeMap(
            ItemStack mapStack,
            ServerLevel level,
            BlockPos pos,
            int scale,
            Holder<MapDecorationType> destinationType,
            @Nullable Component displayName) {
        MapId existingId = mapStack.get(DataComponents.MAP_ID);
        MapId mapId = existingId != null ? existingId : level.getFreeMapId();

        // Create or replace map data with proper settings
        MapItemSavedData mapData =
                MapItemSavedData.createFresh(pos.getX(), pos.getZ(), (byte) scale, true, true, level.dimension());
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
            @Nullable Component displayName) {
        finalizeMap(mapStack, level, pos, 2, destinationTypeHolder, displayName);
    }

    public static boolean tryUpdateMapInLootrTarget(
            ServerLevel level,
            @Nullable LootrTarget target,
            ItemStack pendingMapStack,
            BlockPos pos,
            int scale,
            Holder<MapDecorationType> destinationType,
            @Nullable Component displayName) {
        if (target == null) return false;

        return tryUpdateMapInLootrMenu(level, target, pendingMapStack, pos, scale, destinationType, displayName)
                || tryUpdateMapInLootrInventory(
                        target.inventory(), pendingMapStack, level, pos, scale, destinationType, displayName);
    }

    public static boolean tryInvalidateMapInLootrTarget(
            ServerLevel level, @Nullable LootrTarget target, ItemStack pendingMapStack) {
        if (target == null) return false;

        return tryInvalidateMapInLootrMenu(level, target, pendingMapStack)
                || tryInvalidateMapInLootrInventory(target.inventory(), pendingMapStack);
    }

    private static boolean tryUpdateMapInLootrMenu(
            ServerLevel level,
            LootrTarget target,
            ItemStack pendingMapStack,
            BlockPos pos,
            int scale,
            Holder<MapDecorationType> destinationType,
            @Nullable Component displayName) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target.ownerId());
        if (player == null) return false;

        AbstractContainerMenu menu = player.containerMenu;
        if (!isTargetLootrChestMenu(menu, target)) return false;

        UUID targetId = getTrackingUUID(pendingMapStack);
        if (targetId == null) return false;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            ItemStack slotStack = slot.getItem();
            UUID slotId = getTrackingUUID(slotStack);
            if (targetId.equals(slotId)) {
                finalizeMap(slotStack, level, pos, scale, destinationType, displayName);
                slot.set(slotStack);
                slot.setChanged();
                menu.broadcastChanges();
                ALConstants.logDebug(
                        "Updated pending map via player {}'s Lootr menu slot {}",
                        player.getName().getString(),
                        i);
                return true;
            }
        }
        return false;
    }

    private static boolean tryInvalidateMapInLootrMenu(
            ServerLevel level, LootrTarget target, ItemStack pendingMapStack) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target.ownerId());
        if (player == null) return false;

        AbstractContainerMenu menu = player.containerMenu;
        if (!isTargetLootrChestMenu(menu, target)) return false;

        UUID targetId = getTrackingUUID(pendingMapStack);
        if (targetId == null) return false;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            ItemStack slotStack = slot.getItem();
            UUID slotId = getTrackingUUID(slotStack);
            if (targetId.equals(slotId)) {
                slot.set(new ItemStack(Items.MAP));
                slot.setChanged();
                menu.broadcastChanges();
                ALConstants.logDebug(
                        "Invalidated pending map via player's {} Lootr menu slot {}",
                        player.getName().getString(),
                        i);
                return true;
            }
        }
        return false;
    }

    private static boolean tryUpdateMapInLootrInventory(
            @Nullable Container container,
            ItemStack pendingMapStack,
            ServerLevel level,
            BlockPos pos,
            int scale,
            Holder<MapDecorationType> destinationType,
            @Nullable Component displayName) {
        if (container == null || !isLootrInventory(container)) return false;

        UUID targetId = getTrackingUUID(pendingMapStack);
        if (targetId == null) return false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slotStack = container.getItem(i);
            UUID slotId = getTrackingUUID(slotStack);
            if (targetId.equals(slotId)) {
                finalizeMap(slotStack, level, pos, scale, destinationType, displayName);
                container.setItem(i, slotStack);
                container.setChanged();
                ALConstants.logDebug("Updated pending map via Lootr inventory slot {}", i);
                return true;
            }
        }

        return false;
    }

    private static boolean tryInvalidateMapInLootrInventory(@Nullable Container container, ItemStack pendingMapStack) {
        if (container == null || !isLootrInventory(container)) return false;

        UUID targetId = getTrackingUUID(pendingMapStack);
        if (targetId == null) return false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slotStack = container.getItem(i);
            UUID slotId = getTrackingUUID(slotStack);
            if (targetId.equals(slotId)) {
                container.setItem(i, new ItemStack(Items.MAP));
                container.setChanged();
                ALConstants.logDebug("Invalidated pending map via Lootr inventory slot {}", i);
                return true;
            }
        }

        return false;
    }

    public static @Nullable LootrTarget getActiveLootrTarget() {
        if (!Services.PLATFORM.isModLoaded("lootr")) return null;

        try {
            Object state = invokeLootrStatic(LOOTR_DEFAULT_LOOT_FILLER_CLASS, "getFillerState");
            if (state == null) return null;

            Object container = invokeLootr(state, "container");
            Object player = invokeLootr(state, "player");
            if (container instanceof Container lootrContainer
                    && isLootrInventory(lootrContainer)
                    && player instanceof Player lootrOwner) {
                Object provider = invokeLootr(state, "provider");
                UUID sourceId = readInfoUUID(provider);
                String sourceKey = readInfoKey(provider);
                if (sourceId == null) sourceId = readLootrInventorySourceId(lootrContainer);
                if (sourceKey == null) sourceKey = readLootrInventorySourceKey(lootrContainer);
                return new LootrTarget(lootrOwner.getUUID(), sourceId, sourceKey, lootrContainer);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (LOOTR_FAILURE_WARNED.compareAndSet(false, true)) {
                ALConstants.logWarn(
                        "Lootr is installed but its loot filler state could not be inspected - Lootr chest maps"
                                + " will resolve without Lootr integration. This usually means an incompatible"
                                + " Lootr version.",
                        exception);
            } else {
                ALConstants.logDebug("Unable to inspect active Lootr loot filler state", exception);
            }
        }

        return null;
    }

    private static @Nullable Object invokeLootr(@Nullable Object target, String methodName)
            throws ReflectiveOperationException {
        if (target == null) return null;
        return lootrMethod(target.getClass(), methodName).invoke(target);
    }

    private static @Nullable Object invokeLootrStatic(String className, String methodName)
            throws ReflectiveOperationException {
        return lootrMethod(Class.forName(className), methodName).invoke(null);
    }

    private static Method lootrMethod(Class<?> owner, String methodName) throws NoSuchMethodException {
        Method cached = LOOTR_METHOD_CACHE.get(new LootrMethodKey(owner, methodName));
        if (cached != null) return cached;
        Method resolved = owner.getMethod(methodName);
        LOOTR_METHOD_CACHE.putIfAbsent(new LootrMethodKey(owner, methodName), resolved);
        return resolved;
    }

    private record LootrMethodKey(Class<?> owner, String name) {}

    private static boolean isTargetLootrChestMenu(AbstractContainerMenu menu, LootrTarget target) {
        if (menu instanceof ChestMenu chestMenu) {
            Container container = chestMenu.getContainer();
            return isLootrInventory(container) && isSameLootrTarget(container, target);
        }

        return false;
    }

    private static boolean isLootrInventory(Container container) {
        return container.getClass().getName().equals(LOOTR_INVENTORY_CLASS);
    }

    private static boolean isSameLootrTarget(Container container, LootrTarget target) {
        if (container == target.inventory()) return true;

        UUID sourceId = readLootrInventorySourceId(container);
        if (sourceId != null && sourceId.equals(target.sourceId())) return true;

        String sourceKey = readLootrInventorySourceKey(container);
        return sourceKey != null && sourceKey.equals(target.sourceKey());
    }

    private static @Nullable UUID readLootrInventorySourceId(Container container) {
        try {
            return readInfoUUID(invokeLootr(container, "getInfo"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            ALConstants.logDebug("Unable to read Lootr inventory source UUID", exception);
            return null;
        }
    }

    private static @Nullable String readLootrInventorySourceKey(Container container) {
        try {
            return readInfoKey(invokeLootr(container, "getInfo"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            ALConstants.logDebug("Unable to read Lootr inventory source key", exception);
            return null;
        }
    }

    private static @Nullable UUID readInfoUUID(@Nullable Object info) {
        if (info == null) return null;

        try {
            Object value = invokeLootr(info, "getInfoUUID");
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            ALConstants.logDebug("Unable to read Lootr info UUID", exception);
            return null;
        }
    }

    private static @Nullable String readInfoKey(@Nullable Object info) {
        if (info == null) return null;

        try {
            Object value = invokeLootr(info, "getInfoKey");
            return value instanceof String key ? key : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            ALConstants.logDebug("Unable to read Lootr info key", exception);
            return null;
        }
    }

    /**
     * Broadcasts slot changes to all players that have the chest container open.
     * Won't do anything if the BlockEntity isn't an instance of {@link ChestBlockEntity}.
     * Keep for backward compatibility (new method: broadcastContainerChanges)
     */
    public static void broadcastChestChanges(ServerLevel level, BlockEntity be) {
        if (!(be instanceof ChestBlockEntity chestBE)) return;

        level.players().forEach(player -> {
            AbstractContainerMenu container = player.containerMenu;
            if (container instanceof ChestMenu chestMenu && chestMenu.getContainer() == chestBE) {
                chestMenu.broadcastChanges();
            }
        });
    }

    // This works with any container
    public static void broadcastContainerChanges(
            ServerLevel level, BlockEntity be, net.minecraft.world.Container container) {
        level.players().forEach(player -> {
            AbstractContainerMenu menu = player.containerMenu;
            // ChestMenu is used by both chests and barrels
            if (menu instanceof ChestMenu chestMenu && chestMenu.getContainer() == container) {
                chestMenu.broadcastChanges();
            }
        });
    }
}
