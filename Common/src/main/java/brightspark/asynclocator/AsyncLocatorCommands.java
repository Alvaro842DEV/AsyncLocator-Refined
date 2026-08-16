package brightspark.asynclocator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AsyncLocatorCommands {
    private static final Component PREFIX =
            Component.literal("[Async Locator] ").withStyle(ChatFormatting.GOLD);

    @FunctionalInterface
    public interface ConfigReloader {
        void reload() throws Exception;
    }

    private AsyncLocatorCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ConfigReloader configReloader) {
        dispatcher.register(command("asynclocator", configReloader));
        if (dispatcher.getRoot().getChild("al") == null) {
            dispatcher.register(command("al", configReloader));
        } else {
            ALConstants.logWarn("The /al command is already registered; skipping Async Locator's shortcut");
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name, ConfigReloader configReloader) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource(), configReloader)));
    }

    private static int showStatus(CommandSourceStack source) {
        AsyncLocator.Status status = AsyncLocator.status();
        source.sendSuccess(
                () -> PREFIX.copy()
                        .append(Component.literal("Executor "
                                        + (status.executorActive() ? "running" : "stopped")
                                        + ", active "
                                        + status.activeLocates()
                                        + "/"
                                        + status.maxConcurrentLocates()
                                        + ", queued "
                                        + status.queuedLocates()
                                        + "/"
                                        + status.maxQueuedLocates()
                                        + ", shared searches "
                                        + status.sharedLocates())
                                .withStyle(ChatFormatting.WHITE)),
                false);
        return 1;
    }

    private static int reload(CommandSourceStack source, ConfigReloader configReloader) {
        try {
            configReloader.reload();
            AsyncLocator.updateLocateLimitsFromConfig();
            AsyncLocatorModCommon.printConfigs();
            source.sendSuccess(
                    () -> PREFIX.copy()
                            .append(Component.literal("Configuration reloaded").withStyle(ChatFormatting.WHITE)),
                    true);
            return 1;
        } catch (Exception exception) {
            ALConstants.logError(exception, "Failed to reload configuration");
            source.sendFailure(PREFIX.copy()
                    .append(Component.literal("Configuration reload failed; see the server log")
                            .withStyle(ChatFormatting.RED)));
            return 0;
        }
    }
}
