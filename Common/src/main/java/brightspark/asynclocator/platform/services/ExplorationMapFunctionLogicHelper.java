package brightspark.asynclocator.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.jetbrains.annotations.Nullable;

public interface ExplorationMapFunctionLogicHelper {
    void invalidateMap(ItemStack mapStack, ServerLevel level, BlockPos invPos);

    /**
     * Updates a pending exploration map located in an inventory at the given position with the found feature details
     * Implementations should find the mapStack instance in the inventory at invPos,
     * apply the feature details to it and ensure the updated stack is saved back into the inventory slot
     */
    void updateMap(
            ItemStack mapStack,
            ServerLevel level,
            BlockPos pos,
            int scale,
            Holder<MapDecorationType> destinationTypeHolder,
            BlockPos invPos,
            @Nullable Component displayName);
}
