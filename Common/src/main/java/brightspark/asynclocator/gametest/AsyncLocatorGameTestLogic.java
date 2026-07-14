package brightspark.asynclocator.gametest;

import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.logic.EnderEyeItemLogic;
import brightspark.asynclocator.logic.MerchantLogic;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * Game test worlds are superflat with structure generation disabled, so
 * structure locates deterministically complete with "not found" almost instantly,
 * which is exactly what the invalidation/refund code paths need, while biome locates succeed
 * immediately (the whole world is plains).
 */
public final class AsyncLocatorGameTestLogic {
    // Structure template shared by both loaders: data/asynclocator/structure/empty.nbt
    public static final String EMPTY_STRUCTURE = "asynclocator:empty";

    public static final int MAX_TICKS = 200;

    private AsyncLocatorGameTestLogic() {}

    public static void structureLocateCompletes(GameTestHelper helper) {
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean correct = new AtomicBoolean();

        AsyncLocator.locate(
                        helper.getLevel(),
                        StructureTags.EYE_OF_ENDER_LOCATED,
                        helper.absolutePos(new BlockPos(1, 1, 1)),
                        5,
                        false)
                .handleOnServerThread((pos, throwable) -> {
                    correct.set(throwable == null
                            && pos == null
                            && helper.getLevel().getServer().isSameThread());
                    completed.set(true);
                });

        helper.succeedWhen(() -> {
            helper.assertTrue(completed.get(), Component.literal("Locate task did not complete"));
            helper.assertTrue(
                    correct.get(),
                    Component.literal("Expected a null result delivered on the server thread"
                            + " (structures are disabled in game test worlds)"));
        });
    }

    public static void coalescedLocateSurvivesSiblingCancel(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        AtomicBoolean firstCompleted = new AtomicBoolean();
        AtomicBoolean secondSettled = new AtomicBoolean();

        var first = AsyncLocator.locate(helper.getLevel(), StructureTags.DOLPHIN_LOCATED, origin, 5, false);
        var second = AsyncLocator.locate(helper.getLevel(), StructureTags.DOLPHIN_LOCATED, origin, 5, false);

        first.handleOnServerThread((pos, throwable) -> firstCompleted.set(throwable == null));
        second.handle(
                (pos, throwable) -> secondSettled.set(throwable == null || throwable instanceof CancellationException));
        second.cancel();

        helper.succeedWhen(() -> helper.assertTrue(
                firstCompleted.get() && secondSettled.get(),
                Component.literal("Expected the first caller to complete normally"
                        + " despite its coalesced sibling being cancelled")));
    }

    public static void biomeLocateFindsPlains(GameTestHelper helper) {
        AtomicBoolean found = new AtomicBoolean();

        AsyncLocator.locateBiome(
                        helper.getLevel(),
                        holder -> holder.is(Biomes.PLAINS),
                        "plains (game test)",
                        helper.absolutePos(new BlockPos(1, 1, 1)),
                        64,
                        32,
                        64)
                .handleOnServerThread((pair, throwable) -> found.set(throwable == null && pair != null));

        helper.succeedWhen(() ->
                helper.assertTrue(found.get(), Component.literal("Expected to find a plains biome in a flat world")));
    }

    public static void explorationMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(1, 2, 1);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(chestPos, Blocks.CHEST);

        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        chest.setLootTable(BuiltInLootTables.SHIPWRECK_MAP, 42L);
        chest.unpackLootTable(null);

        helper.assertTrue(
                containsPendingMap(chest), Component.literal("Expected a pending map right after loot generation"));

        helper.succeedWhen(() -> helper.assertTrue(
                containsItem(chest, Items.MAP) && !containsPendingMap(chest),
                Component.literal("Expected the pending map to be invalidated into a plain map")));
    }

    public static void merchantMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));

        MerchantOffer offer = MerchantLogic.updateMapAsync(
                villager,
                13,
                "filled_map.buried_treasure",
                MapDecorationTypes.RED_X,
                12,
                5,
                StructureTags.ON_TREASURE_MAPS);
        helper.assertTrue(offer != null, Component.literal("Expected updateMapAsync to create an offer"));
        helper.assertTrue(
                CommonLogic.isEmptyPendingMap(offer.getResult()),
                Component.literal("Expected the offer result to start as a pending map"));
        villager.getOffers().add(offer);

        helper.succeedWhen(() -> helper.assertTrue(
                !CommonLogic.isEmptyPendingMap(offer.getResult()) && offer.isOutOfStock(),
                Component.literal("Expected the pending map offer to be invalidated and out of stock")));
    }

    public static void eyeOfEnderRefundsWhenNothingFound(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EyeOfEnder eye = helper.spawn(EntityType.EYE_OF_ENDER, new BlockPos(1, 3, 1));
        EnderEyeItemLogic.locateAsync(helper.getLevel(), player, eye, (EnderEyeItem) Items.ENDER_EYE);

        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(EntityType.EYE_OF_ENDER);
            helper.assertItemEntityPresent(Items.ENDER_EYE, new BlockPos(1, 2, 1), 3.0);
        });
    }

    public static void finalizeMapProducesUsableMap(GameTestHelper helper) {
        ItemStack stack = CommonLogic.createManagedMap();
        helper.assertTrue(
                CommonLogic.isEmptyPendingMap(stack), Component.literal("Expected a fresh managed map to be pending"));

        CommonLogic.finalizeMap(
                stack,
                helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)),
                2,
                MapDecorationTypes.RED_X,
                Component.literal("Async Locator Test Map"));

        helper.assertTrue(
                !CommonLogic.isEmptyPendingMap(stack),
                Component.literal("Expected the pending state to be cleared after finalizing"));
        helper.assertTrue(
                stack.get(DataComponents.MAP_ID) != null,
                Component.literal("Expected the finalized map to have a map id"));
        helper.succeed();
    }

    public static void dolphinSurvivesTicking(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Dolphin dolphin = helper.spawn(EntityType.DOLPHIN, new BlockPos(1, 2, 1));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(dolphin.isAlive(), Component.literal("Expected the dolphin to still be alive"));
            helper.succeed();
        });
    }

    private static boolean containsPendingMap(ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (CommonLogic.isEmptyPendingMap(chest.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsItem(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
}
