package com.nao.claimteam.command;

import java.util.ArrayList;
import java.util.List;

import com.nao.claimteam.action.ClaimActions;
import com.nao.claimteam.network.PacketHandler;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public class ClaimCommand extends CommandBase {

    private final String name;
    private final byte action;

    public ClaimCommand(String name, byte action) {
        this.name = name;
        this.action = action;
    }

    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return this.getCommandName().compareTo(((ICommand) other).getCommandName());
        }
        return 0;
    }

    @Override
    public String getCommandName() { return name; }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    @Override
    public String getCommandUsage(ICommandSender sender) { return "/" + name; }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override
    public List getCommandAliases() { return new ArrayList<String>(); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendChatToPlayer("In-game only.");
            return;
        }
        EntityPlayerMP epm = (EntityPlayerMP) sender;
        World w = epm.worldObj;
        int cx = ((int) Math.floor(epm.posX)) >> 4;
        int cz = ((int) Math.floor(epm.posZ)) >> 4;
        String msg;
        if (action == PacketHandler.ACTION_CLAIM)        msg = ClaimActions.claim(epm, w, cx, cz);
        else if (action == PacketHandler.ACTION_UNCLAIM) msg = ClaimActions.unclaim(epm, w, cx, cz);
        else                                              msg = ClaimActions.toggleChunkload(epm, w, cx, cz);
        if (msg != null) epm.sendChatToPlayer("§e[ClaimTeam] " + msg);
    }
}
