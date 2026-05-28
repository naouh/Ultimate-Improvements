package com.cagecontrol;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Opens the management GUI on the client. Server-side this just dispatches an
 * OPEN_GUI packet to the calling player; the client decodes it and shows the
 * cage list (filtered to controllable cages, or all cages for ops).
 */
public class CommandCageControl extends CommandBase {

    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return this.getCommandName().compareTo(((ICommand) other).getCommandName());
        }
        return 0;
    }

    @Override public String getCommandName() { return "cagecontrol"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public String getCommandUsage(ICommandSender s) { return "/cagecontrol"; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender s) { return true; }

    @Override
    public List getCommandAliases() {
        List<String> a = new ArrayList<String>();
        a.add("cages");
        a.add("cagegui");
        return a;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendChatToPlayer("[CageControl] Players only."); return;
        }
        EntityPlayerMP epm = (EntityPlayerMP) sender;
        PacketDispatcher.sendPacketToPlayer(buildOpenGuiPacket(), (cpw.mods.fml.common.network.Player) epm);
        // The server immediately follows up with a fresh cage list so the GUI
        // has data the moment it opens.
        PacketHandler.sendCageList(epm);
    }

    private static Packet250CustomPayload buildOpenGuiPacket() {
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = CageControl.CHANNEL;
        pkt.data = new byte[] { PacketHandler.PKT_OPEN_GUI };
        pkt.length = 1;
        return pkt;
    }
}
