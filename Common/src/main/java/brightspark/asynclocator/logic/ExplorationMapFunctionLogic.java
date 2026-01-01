package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALConstants;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ExplorationMapFunctionLogic {
    // I'd like to think that structure locating shouldn't take *this* long
    private static final Cache<UUID, Component> MAP_NAME_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(3_000)
            .build();

    private ExplorationMapFunctionLogic() {}

    public static void cacheName(ItemStack stack, Component name) {
        if (name == null) return;
        UUID id = CommonLogic.getTrackingUUID(stack);
        if (id != null) {
            MAP_NAME_CACHE.put(id, name);
            ALConstants.logDebug("Cached name for map UUID {}", id);
        } else {
            ALConstants.logWarn("Attempted to cache name for map without tracking UUID");
        }
    }

    public static Component getCachedName(ItemStack stack) {
        UUID id = CommonLogic.getTrackingUUID(stack);
        if (id == null) return null;
        Component name = MAP_NAME_CACHE.getIfPresent(id);
        if (name != null) {
            MAP_NAME_CACHE.invalidate(id);
            ALConstants.logDebug("Retrieved and invalidated cached name for map UUID {}", id);
        }
        return name;
    }
}
