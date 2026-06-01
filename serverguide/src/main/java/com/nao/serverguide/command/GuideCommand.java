package com.nao.serverguide.command;

import com.nao.serverguide.config.GuideContent;
import com.nao.serverguide.network.GuidePacketHandler;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /guide} opens the guide GUI for the sender. {@code /guide reload} (op-only) re-reads the
 * content files from disk so edits show up without a server restart.
 */
public final class GuideCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "guide";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/guide [reload]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!isOp(sender)) { sender.sendChatToPlayer("§cYou need OP to reload the guide."); return; }
            GuideContent.reload();
            sender.sendChatToPlayer("§aServer Guide content reloaded.");
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendChatToPlayer("Only a player can open the guide.");
            return;
        }
        PacketDispatcher.sendPacketToPlayer(GuidePacketHandler.openPacket(), (Player) sender);
    }

    private static boolean isOp(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null) return false;
        return srv.getConfigurationManager().getOps().contains(((EntityPlayerMP) sender).username.toLowerCase());
    }

    @Override
    public int compareTo(Object other) {
        if (other instanceof ICommand) return getCommandName().compareTo(((ICommand) other).getCommandName());
        return 0;
    }
}
