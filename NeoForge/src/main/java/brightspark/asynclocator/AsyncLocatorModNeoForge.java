package brightspark.asynclocator;

import brightspark.asynclocator.gametest.AsyncLocatorNeoForgeGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(ALConstants.MOD_ID)
public class AsyncLocatorModNeoForge {

    public AsyncLocatorModNeoForge(IEventBus modEventBus, ModContainer modContainer) {

        modContainer.registerConfig(
                ModConfig.Type.COMMON, AsyncLocatorConfigNeoForge.SPEC, ALConstants.MOD_ID + ".toml");

        modEventBus.addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == AsyncLocatorConfigNeoForge.SPEC) {
                AsyncLocatorConfigNeoForge.validateConfig();
                AsyncLocatorModCommon.printConfigs();
            }
        });

        // Note: no executor rebuild here - no config value affects the executor, and restarting it
        // would disrupt in-flight locate tasks
        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == AsyncLocatorConfigNeoForge.SPEC) {
                ALConstants.logInfo("Config reloaded");
                AsyncLocatorConfigNeoForge.validateConfig();
                AsyncLocatorModCommon.printConfigs();
            }
        });

        modEventBus.addListener(AsyncLocatorNeoForgeGameTests::registerTestFunctions);
        modEventBus.addListener(AsyncLocatorNeoForgeGameTests::registerTestInstances);

        IEventBus neoforgeEventBus = NeoForge.EVENT_BUS;
        neoforgeEventBus.addListener((ServerAboutToStartEvent event) -> AsyncLocator.setupExecutorService());
        neoforgeEventBus.addListener((ServerStoppingEvent event) -> AsyncLocator.shutdownExecutorService());
    }
}
