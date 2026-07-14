package brightspark.asynclocator.gametest;

import brightspark.asynclocator.ALConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class AsyncLocatorNeoForgeGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = buildTests();
    private static final ResourceLocation EMPTY_STRUCTURE =
            ResourceLocation.parse(AsyncLocatorGameTestLogic.EMPTY_STRUCTURE);

    private AsyncLocatorNeoForgeGameTests() {}

    private static Map<String, Consumer<GameTestHelper>> buildTests() {
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put("structure_locate_completes", AsyncLocatorGameTestLogic::structureLocateCompletes);
        tests.put(
                "coalesced_locate_survives_sibling_cancel",
                AsyncLocatorGameTestLogic::coalescedLocateSurvivesSiblingCancel);
        tests.put("biome_locate_finds_plains", AsyncLocatorGameTestLogic::biomeLocateFindsPlains);
        tests.put("exploration_map_invalidates", AsyncLocatorGameTestLogic::explorationMapInvalidatesWhenNothingFound);
        tests.put("merchant_map_invalidates", AsyncLocatorGameTestLogic::merchantMapInvalidatesWhenNothingFound);
        tests.put("eye_of_ender_refunds", AsyncLocatorGameTestLogic::eyeOfEnderRefundsWhenNothingFound);
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
                                new TestData<>(
                                        environment, EMPTY_STRUCTURE, AsyncLocatorGameTestLogic.MAX_TICKS, 0, true))));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(ALConstants.MOD_ID, name);
    }
}
