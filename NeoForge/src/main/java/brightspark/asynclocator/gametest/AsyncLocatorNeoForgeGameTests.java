package brightspark.asynclocator.gametest;

import brightspark.asynclocator.ALConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class AsyncLocatorNeoForgeGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = buildTests();
    private static final Set<String> CHUNK_LOADING_TESTS = Set.of("locate_commands_complete_async");
    private static final ResourceLocation EMPTY_STRUCTURE =
            ResourceLocation.parse(AsyncLocatorGameTestLogic.EMPTY_STRUCTURE);

    private AsyncLocatorNeoForgeGameTests() {}

    private static Map<String, Consumer<GameTestHelper>> buildTests() {
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put(
                "status_and_reload_commands_execute",
                helper -> AsyncLocatorGameTestLogic.statusAndReloadCommandsExecute(
                        helper, FMLPaths.CONFIGDIR.get().resolve("asynclocator.toml")));
        tests.put("structure_locate_completes", AsyncLocatorGameTestLogic::structureLocateCompletes);
        tests.put(
                "coalesced_locate_survives_sibling_cancel",
                AsyncLocatorGameTestLogic::coalescedLocateSurvivesSiblingCancel);
        tests.put("biome_locate_finds_plains", AsyncLocatorGameTestLogic::biomeLocateFindsPlains);
        tests.put(
                "interrupted_structure_check_stops",
                AsyncLocatorGameTestLogic::interruptedStructureCheckStopsBeforeStorage);
        tests.put(
                "interrupted_biome_locate_stops", AsyncLocatorGameTestLogic::interruptedBiomeLocateStopsBeforeSampling);
        tests.put(
                "locate_worker_loads_structure_chunk_itself",
                AsyncLocatorGameTestLogic::locateWorkerLoadsStructureChunkItself);
        tests.put("map_preview_colors_match_vanilla", AsyncLocatorGameTestLogic::mapPreviewColorsMatchVanilla);
        tests.put(
                "map_preview_samples_vanilla_biomes_off_thread",
                AsyncLocatorGameTestLogic::mapPreviewSamplesVanillaBiomesOffThread);
        tests.put("finished_map_uses_prepared_preview", AsyncLocatorGameTestLogic::finishedMapUsesPreparedPreview);
        tests.put(
                "locate_result_is_prepared_off_server_thread",
                AsyncLocatorGameTestLogic::locateResultIsPreparedOffServerThread);
        tests.put("exploration_map_invalidates", AsyncLocatorGameTestLogic::explorationMapInvalidatesWhenNothingFound);
        tests.put("merchant_map_invalidates", AsyncLocatorGameTestLogic::merchantMapInvalidatesWhenNothingFound);
        tests.put("eye_of_ender_refunds", AsyncLocatorGameTestLogic::eyeOfEnderRefundsWhenNothingFound);
        tests.put(
                "eye_of_ender_success_signals_to_block_position",
                AsyncLocatorGameTestLogic::eyeOfEnderSuccessfulResultSignalsToBlockPosition);
        tests.put("pending_map_name_write_is_deferred", AsyncLocatorGameTestLogic::pendingMapNameWriteIsDeferred);
        tests.put("pending_map_cannot_be_picked_up", AsyncLocatorGameTestLogic::pendingMapCannotBePickedUp);
        tests.put("locate_commands_complete_async", AsyncLocatorGameTestLogic::locateCommandsCompleteAsynchronously);
        tests.put("finalize_map_produces_usable_map", AsyncLocatorGameTestLogic::finalizeMapProducesUsableMap);
        tests.put("dolphin_survives_ticking", AsyncLocatorGameTestLogic::dolphinSurvivesTicking);
        return tests;
    }

    public static void registerTestFunctions(RegisterEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;

        event.register(
                Registries.TEST_FUNCTION,
                helper -> TESTS.forEach((name, function) -> helper.register(id(name), function)));
    }

    public static void registerTestInstances(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(id("default"));

        TESTS.keySet()
                .forEach(name -> event.registerTest(
                        id(name),
                        new FunctionGameTestInstance(
                                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                                new TestData<>(environment, EMPTY_STRUCTURE, maxTicks(name), 0, true))));
    }

    private static int maxTicks(String name) {
        return CHUNK_LOADING_TESTS.contains(name)
                ? AsyncLocatorGameTestLogic.CHUNK_LOADING_MAX_TICKS
                : AsyncLocatorGameTestLogic.MAX_TICKS;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(ALConstants.MOD_ID, name);
    }
}
