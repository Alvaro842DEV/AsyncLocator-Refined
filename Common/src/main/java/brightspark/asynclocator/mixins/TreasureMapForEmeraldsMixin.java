package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.MerchantLogic;
import brightspark.asynclocator.platform.Services;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$TreasureMapForEmeralds")
public abstract class TreasureMapForEmeraldsMixin {
    @Shadow
    @Final
    private int emeraldCost;

    @Shadow
    @Final
    private String displayName;

    @Shadow
    @Final
    private int maxUses;

    @Shadow
    @Final
    private int villagerXp;

    @Shadow
    @Final
    private TagKey<Structure> destination;

    @Shadow
    @Final
    private Holder<MapDecorationType> destinationType;

    @Inject(
            method = "getOffer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"),
            cancellable = true)
    public void updateMapAsyncInject(Entity pTrader, RandomSource pRandom, CallbackInfoReturnable<MerchantOffer> cir) {
        if (!Services.CONFIG.villagerTradeEnabled()) return;

        Holder<MapDecorationType> destinationTypeHolder = this.destinationType;

        if (destinationTypeHolder == null) {
            ALConstants.logError("Shadowed destinationType Holder is null, cannot create async offer.");
            return;
        }

        ALConstants.logDebug("Intercepted TreasureMapForEmeralds#getOffer call via @Inject");
        MerchantOffer offer = MerchantLogic.updateMapAsync(
                pTrader, emeraldCost, displayName, destinationTypeHolder, maxUses, villagerXp, destination);

        if (offer != null) {
            cir.setReturnValue(offer);
        }
    }
}
