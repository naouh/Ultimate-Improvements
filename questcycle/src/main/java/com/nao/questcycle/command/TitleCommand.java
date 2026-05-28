package com.nao.questcycle.command;

import com.nao.questcycle.core.TabListUpdater;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.TitleDef;
import java.util.Iterator;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/** /title set <id> | /title clear | /title list */
public final class TitleCommand extends CommandBase {
	@Override
	public String getCommandName() {
		return "title";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/title <set <id>|clear|list>";
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
		if (!(sender instanceof EntityPlayerMP)) { reply(sender, "§cPlayers only."); return; }
		EntityPlayerMP player = (EntityPlayerMP) sender;
		PlayerStateCache.Entry e = PlayerStateCache.getOrLoad(player.username);
		if (args.length == 0) { reply(sender, getCommandUsage(sender)); return; }
		String sub = args[0];
		if ("list".equalsIgnoreCase(sub)) {
			if (e.persist.earnedTitleIds.isEmpty()) {
				reply(sender, "§7You haven't earned any titles yet.");
				return;
			}
			reply(sender, "§eYour titles (active: §a" + (e.persist.activeTitleId == null ? "none" : e.persist.activeTitleId) + "§e):");
			Iterator<String> it = e.persist.earnedTitleIds.iterator();
			while (it.hasNext()) {
				String tid = it.next();
				TitleDef def = QuestRegistry.CURRENT.title(tid);
				reply(sender, "  §f" + tid + "§r  " + (def == null ? "(unknown)" : def.displayName));
			}
			return;
		}
		if ("clear".equalsIgnoreCase(sub)) {
			e.persist.activeTitleId = null;
			com.nao.questcycle.data.PersistStore.save(e.persist);
			TabListUpdater.refreshFor(player);
			com.nao.questcycle.event.BukkitChatBridge.clearPrefix(player.username);
			reply(sender, "§aTitle cleared.");
			return;
		}
		if ("set".equalsIgnoreCase(sub)) {
			if (args.length < 2) { reply(sender, "Usage: /title set <id>"); return; }
			String tid = args[1];
			if (!e.persist.earnedTitleIds.contains(tid)) { reply(sender, "§cYou haven't earned that title."); return; }
			TitleDef def = QuestRegistry.CURRENT.title(tid);
			if (def == null) { reply(sender, "§cThat title isn't loaded in the current config."); return; }
			e.persist.activeTitleId = tid;
			com.nao.questcycle.data.PersistStore.save(e.persist);
			TabListUpdater.refreshFor(player);
			com.nao.questcycle.event.BukkitChatBridge.setPrefix(player.username, def.displayName);
			reply(sender, "§aActive title set: " + def.displayName);
			return;
		}
		reply(sender, getCommandUsage(sender));
	}

	@Override
	public int compareTo(Object other) {
		if (other instanceof ICommand) return this.getCommandName().compareTo(((ICommand) other).getCommandName());
		return 0;
	}

	private static void reply(ICommandSender sender, String msg) {
		sender.sendChatToPlayer(msg);
	}
}
