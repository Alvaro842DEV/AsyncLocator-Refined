package brightspark.asynclocator.logic;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.mixins.LocateCommandAccess;
import brightspark.asynclocator.platform.Services;
import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

public class LocateCommandLogic {
    private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
    private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;

    private LocateCommandLogic() {}

    // Async structure locating for /locate structure
    public static void locateAsync(
            CommandSourceStack sourceStack,
            ResourceOrTagKeyArgument.Result<Structure> structureResult,
            HolderSet<Structure> holderset) {
        BlockPos originPos = BlockPos.containing(sourceStack.getPosition());
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        AsyncLocator.locate(sourceStack.getLevel(), holderset, originPos, 100, false)
                .thenOnServerThread(pair -> {
                    stopwatch.stop();
                    if (pair != null) {
                        ALConstants.logInfo("Location found - sending success back to command source");
                        LocateCommand.showLocateResult(
                                sourceStack,
                                structureResult,
                                originPos,
                                pair,
                                "commands.locate.structure.success",
                                false,
                                stopwatch.elapsed());
                    } else {
                        ALConstants.logInfo("No location found - sending failure back to command source");
                        sourceStack.sendFailure(Component.literal(LocateCommandAccess.getErrorFailed()
                                .create(structureResult.asPrintable())
                                .getMessage()));
                    }
                });
    }

    // Async biome locating for /locate biome
    public static void locateBiomeAsync(
            CommandSourceStack sourceStack, ResourceOrTagArgument.Result<Biome> biomeResult) {
        BlockPos originPos = BlockPos.containing(sourceStack.getPosition());
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        int radius = Services.CONFIG.biomeSearchRadius();

        AsyncLocator.locateBiome(
                        (ServerLevel) sourceStack.getLevel(),
                        biomeResult,
                        originPos,
                        radius,
                        BIOME_SAMPLE_RESOLUTION_HORIZONTAL,
                        BIOME_SAMPLE_RESOLUTION_VERTICAL)
                .thenOnServerThread((Pair<BlockPos, Holder<Biome>> pair) -> {
                    stopwatch.stop();
                    if (pair != null) {
                        ALConstants.logInfo("Biome found - sending success back to command source");
                        LocateCommand.showLocateResult(
                                sourceStack,
                                biomeResult,
                                originPos,
                                pair,
                                "commands.locate.biome.success",
                                true,
                                stopwatch.elapsed());
                    } else {
                        ALConstants.logInfo("Biome not found - sending failure back to command source");
                        sourceStack.sendFailure(Component.literal(LocateCommandAccess.getErrorBiomeNotFound()
                                .create(biomeResult.asPrintable())
                                .getMessage()));
                    }
                });
    }
}
