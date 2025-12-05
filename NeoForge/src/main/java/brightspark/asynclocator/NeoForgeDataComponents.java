package brightspark.asynclocator;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NeoForgeDataComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ALConstants.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> LOCATING =
            DATA_COMPONENTS.register("locating", () -> DataComponentType.<Unit>builder()
                    .persistent(Codec.unit(Unit.INSTANCE))
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
                    .build());

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
        // After registration completes, set the static field in ALDataComponents
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ALDataComponents.setLocating(LOCATING.get());
            ALConstants.logDebug("Linked NeoForge data component to Common");
        }));
    }
}
