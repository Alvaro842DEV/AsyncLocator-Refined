package brightspark.asynclocator.logic;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface EyeOfEnderData {
    void setLocateTaskOngoing(boolean locateTaskOngoing);

    @Nullable
    Vec3 getSignalTarget();
}
