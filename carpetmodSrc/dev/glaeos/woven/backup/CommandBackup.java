package dev.glaeos.woven.backup;

import carpet.commands.CommandCarpetBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nonnull;

public class CommandBackup extends CommandCarpetBase {

    @Override
    public @Nonnull String getName() {
        return "backup";
    }

    @Override
    public @Nonnull String getUsage(@Nonnull ICommandSender sender) {
        return "Usage: backup <save|status> [name]";
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                        @Nonnull String[] args) throws CommandException {

    }

}
