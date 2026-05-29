package com.nao.hdv.command;

import java.util.ArrayList;
import java.util.List;

import com.nao.hdv.network.PacketHandler;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/** /hdv (aliases /ah, /shop) - tells the player's client to open the auction house GUI. */
public class HdvCommand extends CommandBase {

	public int compareTo(Object other) {
		if (other instanceof ICommand) {
			return this.getCommandName().compareTo(((ICommand) other).getCommandName());
		}
		return 0;
	}

	@Override
	public String getCommandName() { return "hdv"; }

	@Override
	public int getRequiredPermissionLevel() { return 0; }

	@Override
	public String getCommandUsage(ICommandSender sender) { return "/hdv"; }

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

	@Override
	public List getCommandAliases() {
		List<String> a = new ArrayList<String>();
		a.add("ah");
		a.add("shop");
		return a;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if (!(sender instanceof EntityPlayerMP)) {
			sender.sendChatToPlayer("This command can only be used in-game.");
			return;
		}
		PacketHandler.sendOpen((EntityPlayerMP) sender);
	}
}
