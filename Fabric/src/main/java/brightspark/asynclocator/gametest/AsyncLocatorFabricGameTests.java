package brightspark.asynclocator.gametest;

import static brightspark.asynclocator.gametest.AsyncLocatorGameTestLogic.EMPTY_STRUCTURE;
import static brightspark.asynclocator.gametest.AsyncLocatorGameTestLogic.MAX_TICKS;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AsyncLocatorFabricGameTests {
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void statusAndReloadCommandsExecute(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.statusAndReloadCommandsExecute(
                helper, FabricLoader.getInstance().getConfigDir().resolve("asynclocator.properties"));
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void structureLocateCompletes(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.structureLocateCompletes(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void coalescedLocateSurvivesSiblingCancel(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.coalescedLocateSurvivesSiblingCancel(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void biomeLocateFindsPlains(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.biomeLocateFindsPlains(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void explorationMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.explorationMapInvalidatesWhenNothingFound(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void merchantMapInvalidatesWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.merchantMapInvalidatesWhenNothingFound(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void eyeOfEnderRefundsWhenNothingFound(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.eyeOfEnderRefundsWhenNothingFound(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void eyeOfEnderSuccessfulResultSignalsToBlockPosition(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.eyeOfEnderSuccessfulResultSignalsToBlockPosition(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void locateCommandsCompleteAsynchronously(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.locateCommandsCompleteAsynchronously(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void finalizeMapProducesUsableMap(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.finalizeMapProducesUsableMap(helper);
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = MAX_TICKS)
    public void dolphinSurvivesTicking(GameTestHelper helper) {
        AsyncLocatorGameTestLogic.dolphinSurvivesTicking(helper);
    }
}
