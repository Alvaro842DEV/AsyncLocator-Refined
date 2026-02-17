package brightspark.asynclocator;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class ItemResourceUtils {
    public static ItemStack extractItem(ResourceHandler<ItemResource> handler, int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        var resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // We have to limit to the max stack size, per the contract of extractItem
        amount = Math.min(amount, resource.getMaxStackSize());
        try (var tx = Transaction.openRoot()) {
            int extracted = handler.extract(slot, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return resource.toStack(extracted);
        }
    }

    public static void setStackInSlot(CombinedResourceHandler<ItemResource> combinedResourceHandler, int slot, ItemStack stack) {
        /*
        slot = combinedResourceHandler.getAmountAsInt(slot, index);*/
        int index = combinedResourceHandler.getAmountAsInt(slot);
        ItemResource handler = combinedResourceHandler.getResource(index);
        combinedResourceHandler.insert(handler, slot, Transaction.openRoot());
    }
}
