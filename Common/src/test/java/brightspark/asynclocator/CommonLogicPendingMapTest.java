package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brightspark.asynclocator.logic.CommonLogic;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CommonLogicPendingMapTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Test for the Mouse Tweaks crash report against 1.21.11-1.5.x (https://github.com/Alvaro842DEV/AsyncLocator-Refined/issues/9):
     * Slot#mayPickup runs for every hovered slot, so any filled map without custom data must
     * be reported as non-pending instead of dereferencing a null CustomData component.
     */
    @Test
    void plainFilledMapWithoutCustomDataIsNotPendingAndDoesNotThrow() {
        ItemStack plainMap = new ItemStack(Items.FILLED_MAP);
        assertFalse(CommonLogic.isEmptyPendingMap(plainMap));
        assertNull(CommonLogic.getTrackingUUID(plainMap));
    }

    @Test
    void emptyStackIsNotPendingAndDoesNotThrow() {
        assertFalse(CommonLogic.isEmptyPendingMap(ItemStack.EMPTY));
        assertNull(CommonLogic.getTrackingUUID(ItemStack.EMPTY));
    }

    @Test
    void nonMapItemsAreNeverPending() {
        assertFalse(CommonLogic.isEmptyPendingMap(new ItemStack(Items.MAP)));
        assertFalse(CommonLogic.isEmptyPendingMap(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void managedMapIsPendingUntilFinalized() {
        ItemStack managed = CommonLogic.createManagedMap();
        assertTrue(CommonLogic.isEmptyPendingMap(managed));

        CommonLogic.clearPendingState(managed);
        assertFalse(CommonLogic.isEmptyPendingMap(managed));
        assertNull(CommonLogic.getTrackingUUID(managed));
    }

    @Test
    void merchantMapStartsPendingWithoutPersistentIdentity() {
        ItemStack offer = CommonLogic.createMerchantMap(null);
        assertTrue(CommonLogic.isEmptyPendingMap(offer));
        assertEquals(Items.FILLED_MAP, offer.getItem());
        assertNull(offer.get(DataComponents.MAP_ID));
    }
}
