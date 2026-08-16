package brightspark.asynclocator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.junit.jupiter.api.Test;

class AsyncLocatorCommandsTest {
    @Test
    void keepsExistingAlCommandUntouched() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandNode<CommandSourceStack> existingAlias = dispatcher.register(Commands.literal("al"));

        AsyncLocatorCommands.register(dispatcher, () -> {});

        assertSame(existingAlias, dispatcher.getRoot().getChild("al"));
        assertNotNull(dispatcher.getRoot().getChild("asynclocator"));
        assertNotNull(dispatcher.getRoot().getChild("asynclocator").getChild("status"));
        assertNotNull(dispatcher.getRoot().getChild("asynclocator").getChild("reload"));
    }
}
