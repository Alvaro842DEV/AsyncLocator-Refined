package brightspark.asynclocator;

import com.mojang.serialization.Codec;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;

public class ALDataComponents {
    public static DataComponentType<Unit> LOCATING;

    private static <T> DataComponentType<T> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> operator) {
        DataComponentType.Builder<T> builder = DataComponentType.builder();
        builder = operator.apply(builder);
        return Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE, ALConstants.MOD_ID + ":" + name, builder.build());
    }

    // Direct registration for Fabric
    public static void init() {
        if (LOCATING != null) return;

        LOCATING = register("locating", builder -> builder.persistent(Codec.unit(Unit.INSTANCE))
                .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

        ALConstants.logInfo("Registered data components (Fabric)");
    }

    // Registration for NeoForge
    public static void setLocating(DataComponentType<Unit> locating) {
        LOCATING = locating;
        ALConstants.logInfo("Registered data components (NeoForge)");
    }
}
