package brightspark.asynclocator.gametest;

import static brightspark.asynclocator.gametest.AsyncLocatorGameTestLogic.EMPTY_STRUCTURE;
import static brightspark.asynclocator.gametest.AsyncLocatorGameTestLogic.MAX_TICKS;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AsyncLocatorFabricGameTests {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void structureLocateCompletes(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.structureLocateCompletes(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void coalescedLocateSurvivesSiblingCancel(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.coalescedLocateSurvivesSiblingCancel(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void biomeLocateFindsPlains(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.biomeLocateFindsPlains(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void explorationMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.explorationMapInvalidatesWhenNothingFound(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void merchantMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.merchantMapInvalidatesWhenNothingFound(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void eyeOfEnderRefundsWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.eyeOfEnderRefundsWhenNothingFound(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void finalizeMapProducesUsableMap(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.finalizeMapProducesUsableMap(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = MAX_TICKS)
    public void dolphinSurvivesTicking(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.dolphinSurvivesTicking(helper);
    }
}
