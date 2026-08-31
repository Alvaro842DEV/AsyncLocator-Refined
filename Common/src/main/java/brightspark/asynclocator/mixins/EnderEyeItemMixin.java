package brightspark.asynclocator.mixins;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.EnderEyeItemLogic;
import brightspark.asynclocator.platform.Services;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.advancements.critereon.UsedEnderEyeTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderEyeItem.class)
public class EnderEyeItemMixin {
    /*
    Intercept EnderEyeItem#use call and return BlockPos.ZERO instead. It won't be used in the EyeOfEnder entity
    created later either, as we need to set the actual location ourselves.
    */
    @WrapOperation(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"))
    public BlockPos levelFindNearestMapFeature(
            ServerLevel serverlevel,
            TagKey<Structure> pStructureTag,
            BlockPos pPos,
            int pRadius,
            boolean pSkipExistingChunks,
            Operation<BlockPos> original) {
        if (Services.CONFIG.eyeOfEnderEnabled()) {
            ALConstants.logDebug("Intercepted EnderEyeItem#use call");
            return BlockPos.ZERO;
        } else {
            // Normal behaviour
            return original.call(serverlevel, pStructureTag, pPos, pRadius, pSkipExistingChunks);
        }
    }

    // Start the async locate task here so we have the eye of ender entity for context
    @Inject(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/projectile/EyeOfEnder;setItem(Lnet/minecraft/world/item/ItemStack;)V"))
    public void startAsyncLocateTask(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            CallbackInfoReturnable<InteractionResult> cir,
            @Local EyeOfEnder eyeofender) {
        if (!Services.CONFIG.eyeOfEnderEnabled()) return;
        if (!(pLevel instanceof ServerLevel serverlevel)) return;
        EnderEyeItemLogic.locateAsync(serverlevel, pPlayer, eyeofender, (EnderEyeItem) (Object) this);
    }

    @WrapWithCondition(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/projectile/EyeOfEnder;signalTo(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean eyeOfEnderSignalTo(EyeOfEnder eyeOfEnder, Vec3 target) {
        return !Services.CONFIG.eyeOfEnderEnabled();
    }

    @WrapWithCondition(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/advancements/critereon/UsedEnderEyeTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)V"))
    private boolean triggerUsedEnderEyeCriteria(UsedEnderEyeTrigger trigger, ServerPlayer player, BlockPos pos) {
        return !Services.CONFIG.eyeOfEnderEnabled();
    }

    @WrapWithCondition(
            method = "use",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;)V"))
    private boolean playerAwardStat(Player player, Stat<?> pStat) {
        return !Services.CONFIG.eyeOfEnderEnabled();
    }
}
