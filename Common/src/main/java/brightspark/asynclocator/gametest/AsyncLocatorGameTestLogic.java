package brightspark.asynclocator.gametest;

import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.logic.EnderEyeItemLogic;
import brightspark.asynclocator.logic.ExplorationMapFunctionLogic;
import brightspark.asynclocator.logic.EyeOfEnderData;
import brightspark.asynclocator.logic.MerchantLogic;
import brightspark.asynclocator.platform.Services;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.inventory.Slot;
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
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

/**
 * Game test worlds are superflat with structure generation disabled, so
 * structure locates deterministically complete with "not found" almost instantly,
 * which is exactly what the invalidation/refund code paths need, while biome locates succeed
 * immediately (the whole world is plains).
 */
public final class AsyncLocatorGameTestLogic {
    // Structure template shared by both loaders: data/asynclocator/structure/empty.nbt
    public static final String EMPTY_STRUCTURE = "asynclocator:empty";

    public static final int MAX_TICKS = 600;

    private AsyncLocatorGameTestLogic() {}

    public static void statusAndReloadCommandsExecute(GameTestHelper helper, Path configFile) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();

        try {
            helper.assertTrue(
                    dispatcher.execute("asynclocator status", source) == 1,
                    Component.literal("Expected /asynclocator status to succeed"));
            int originalMaxQueued = Services.CONFIG.maxQueuedLocates();
            int reloadedMaxQueued = originalMaxQueued == 127 ? 128 : 127;
            String originalConfig = Files.readString(configFile);
            String editedConfig =
                    originalConfig.replaceFirst("(?m)^(\\s*maxQueuedLocates\\s*=\\s*)\\d+", "$1" + reloadedMaxQueued);
            helper.assertFalse(
                    originalConfig.equals(editedConfig), Component.literal("Could not edit maxQueuedLocates"));

            try {
                Files.writeString(configFile, editedConfig);
                helper.assertTrue(
                        dispatcher.execute("asynclocator reload", source) == 1,
                        Component.literal("Expected /asynclocator reload to succeed"));
                helper.assertTrue(
                        Services.CONFIG.maxQueuedLocates() == reloadedMaxQueued,
                        Component.literal("Reload did not apply maxQueuedLocates"));
            } finally {
                Files.writeString(configFile, originalConfig);
                dispatcher.execute("asynclocator reload", source);
            }
            helper.assertTrue(
                    Services.CONFIG.maxQueuedLocates() == originalMaxQueued,
                    Component.literal("Reload did not restore maxQueuedLocates"));

            String invalidConfig =
                    originalConfig.replaceFirst("(?m)^(\\s*maxQueuedLocates\\s*=\\s*)\\d+", "$1" + Integer.MAX_VALUE);
            helper.assertFalse(
                    originalConfig.equals(invalidConfig), Component.literal("Could not make maxQueuedLocates invalid"));
            try {
                Files.writeString(configFile, invalidConfig);
                helper.assertTrue(
                        dispatcher.execute("asynclocator reload", source) == 0,
                        Component.literal("Expected reload to reject an out-of-range value"));
                helper.assertTrue(
                        Services.CONFIG.maxQueuedLocates() == originalMaxQueued,
                        Component.literal("Invalid reload changed the active maxQueuedLocates"));
            } finally {
                Files.writeString(configFile, originalConfig);
                dispatcher.execute("asynclocator reload", source);
            }
            helper.assertTrue(
                    Services.CONFIG.maxQueuedLocates() == originalMaxQueued,
                    Component.literal("Reload did not restore config after invalid-value test"));

            boolean originalDolphinToggle = Services.CONFIG.dolphinTreasureEnabled();
            String invalidBooleanConfig = originalConfig.replaceFirst(
                    "(?m)^(\\s*dolphinTreasureEnabled\\s*=\\s*)(true|false)", "$1\"not-a-boolean\"");
            helper.assertFalse(
                    originalConfig.equals(invalidBooleanConfig),
                    Component.literal("Could not make dolphinTreasureEnabled invalid"));
            try {
                Files.writeString(configFile, invalidBooleanConfig);
                helper.assertTrue(
                        dispatcher.execute("asynclocator reload", source) == 0,
                        Component.literal("Expected reload to reject a malformed boolean"));
                helper.assertTrue(
                        Services.CONFIG.dolphinTreasureEnabled() == originalDolphinToggle,
                        Component.literal("Invalid reload changed the active dolphinTreasureEnabled value"));
            } finally {
                Files.writeString(configFile, originalConfig);
                dispatcher.execute("asynclocator reload", source);
            }
            helper.assertTrue(
                    dispatcher.execute("al status", source) == 1,
                    Component.literal("Expected the /al shortcut to execute Async Locator status"));
            helper.succeed();
        } catch (Exception exception) {
            helper.fail(Component.literal("Async Locator command failed: " + exception.getMessage()));
        }
    }

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
        helper.assertFalse(
                containsPendingMapWithId(chest),
                Component.literal("Pending exploration map unexpectedly allocated persistent map data"));

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
        helper.assertTrue(
                offer.getResult().get(DataComponents.MAP_ID) == null,
                Component.literal("Pending merchant map unexpectedly allocated persistent map data"));
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
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 3, 1));
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
        Items.ENDER_EYE.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        helper.succeedWhen(() -> {
            helper.assertEntityNotPresent(EntityType.EYE_OF_ENDER);
            helper.assertItemEntityPresent(Items.ENDER_EYE);
        });
    }

    public static void pendingMapNameWriteIsDeferred(GameTestHelper helper) {
        ItemStack pendingMap = CommonLogic.createManagedMap();
        Component resolvedName = Component.literal("Resolved Async Locator Map");
        LootContext context = new LootContext.Builder(
                        new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY))
                .create(Optional.empty());
        LootItemFunction setName = SetNameFunction.setName(resolvedName, SetNameFunction.Target.ITEM_NAME)
                .build();

        setName.apply(pendingMap, context);

        helper.assertFalse(
                resolvedName.equals(pendingMap.get(DataComponents.ITEM_NAME)),
                Component.literal("Pending map name was written before locate completion"));
        helper.assertTrue(
                resolvedName.equals(ExplorationMapFunctionLogic.getCachedName(pendingMap)),
                Component.literal("Resolved pending map name was not cached"));
        helper.succeed();
    }

    public static void pendingMapCannotBePickedUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer container = new SimpleContainer(CommonLogic.createManagedMap());
        Slot slot = new Slot(container, 0, 0, 0);

        helper.assertFalse(slot.mayPickup(player), Component.literal("Pending map slot allowed pickup"));

        container.setItem(0, new ItemStack(Items.FILLED_MAP));
        helper.assertTrue(slot.mayPickup(player), Component.literal("Ordinary filled map slot blocked pickup"));
        helper.succeed();
    }

    public static void eyeOfEnderSuccessfulResultSignalsToBlockPosition(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EyeOfEnder eye = helper.spawn(EntityType.EYE_OF_ENDER, new BlockPos(1, 3, 1));
        BlockPos target = eye.blockPosition().offset(4, 2, 3);

        EnderEyeItemLogic.completeSuccessfulLocate(player, eye, (EnderEyeItem) Items.ENDER_EYE, target);

        helper.assertTrue(
                Vec3.atLowerCornerOf(target).equals(((EyeOfEnderData) eye).getSignalTarget()),
                Component.literal("Expected the eye target to be the lower-corner Vec3 of the located BlockPos"));
        helper.succeed();
    }

    public static void locateCommandsCompleteAsynchronously(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        AtomicReference<Component> structureResponse = new AtomicReference<>();
        AtomicReference<Component> biomeResponse = new AtomicReference<>();

        try {
            int structureResult = dispatcher.execute(
                    "locate structure #minecraft:eye_of_ender_located",
                    server.createCommandSourceStack()
                            .withLevel(helper.getLevel())
                            .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                            .withSource(messageCollector(structureResponse)));
            int biomeResult = dispatcher.execute(
                    "locate biome minecraft:plains",
                    server.createCommandSourceStack()
                            .withLevel(helper.getLevel())
                            .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                            .withSource(messageCollector(biomeResponse)));
            helper.assertTrue(structureResult == 1, Component.literal("Structure command was not accepted"));
            helper.assertTrue(biomeResult == 1, Component.literal("Biome command was not accepted"));
        } catch (Exception exception) {
            helper.fail(Component.literal("Locate command failed to start: " + exception.getMessage()));
            return;
        }

        helper.succeedWhen(() -> {
            Component structureMessage = structureResponse.get();
            Component biomeMessage = biomeResponse.get();
            helper.assertTrue(structureMessage != null, Component.literal("Structure locate sent no completion"));
            helper.assertTrue(biomeMessage != null, Component.literal("Biome locate sent no completion"));
            helper.assertTrue(
                    TextColor.fromLegacyFormat(ChatFormatting.RED)
                            .equals(structureMessage.getStyle().getColor()),
                    Component.literal("Expected the unavailable structure to report failure"));
            helper.assertFalse(
                    TextColor.fromLegacyFormat(ChatFormatting.RED)
                            .equals(biomeMessage.getStyle().getColor()),
                    Component.literal("Expected plains biome locate to report success"));
        });
    }

    private static CommandSource messageCollector(AtomicReference<Component> response) {
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                response.set(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
    }

    public static void finalizeMapProducesUsableMap(GameTestHelper helper) {
        ItemStack stack = CommonLogic.createManagedMap();
        helper.assertTrue(
                CommonLogic.isEmptyPendingMap(stack), Component.literal("Expected a fresh managed map to be pending"));
        helper.assertTrue(
                stack.get(DataComponents.MAP_ID) == null,
                Component.literal("Pending managed map unexpectedly allocated persistent map data"));

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
        helper.assertTrue(
                helper.getLevel().getMapData(stack.get(DataComponents.MAP_ID)) != null,
                Component.literal("Expected the finalized map id to resolve to saved map data"));
        helper.succeed();
    }

    public static void dolphinSurvivesTicking(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Dolphin dolphin = helper.spawn(EntityType.DOLPHIN, new BlockPos(1, 2, 1));
        dolphin.setGotFish(true);
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

    private static boolean containsPendingMapWithId(ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (CommonLogic.isEmptyPendingMap(stack) && stack.get(DataComponents.MAP_ID) != null) {
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
