package brightspark.asynclocator.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("asynclocator")
@PrefixGameTestTemplate(false)
public final class AsyncLocatorNeoForgeGameTests {
    private AsyncLocatorNeoForgeGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        event.register(AsyncLocatorNeoForgeGameTests.class);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void structureLocateCompletes(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.structureLocateCompletes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void coalescedLocateSurvivesSiblingCancel(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.coalescedLocateSurvivesSiblingCancel(helper);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void biomeLocateFindsPlains(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.biomeLocateFindsPlains(helper);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void explorationMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.explorationMapInvalidatesWhenNothingFound(helper);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void merchantMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.merchantMapInvalidatesWhenNothingFound(helper);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void eyeOfEnderRefundsWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.eyeOfEnderRefundsWhenNothingFound(helper);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void finalizeMapProducesUsableMap(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.finalizeMapProducesUsableMap(helper);
    }

    @GameTest(template = "empty", timeoutTicks = AsyncLocatorGameTestLogic.MAX_TICKS)
    public static void dolphinSurvivesTicking(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.dolphinSurvivesTicking(helper);
    }
}
