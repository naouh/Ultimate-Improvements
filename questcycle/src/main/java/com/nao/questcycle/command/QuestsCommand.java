package com.nao.questcycle.command;

import com.nao.questcycle.config.QuestConfigLoader;
import com.nao.questcycle.core.CompletionEngine;
import com.nao.questcycle.core.LeaderboardBuilder;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.network.PacketBuilder;
import com.nao.questcycle.network.QuestPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** /quests reload | /quests info <id> | /quests give-prestige <user> <n> | /quests top [n] */
public final class QuestsCommand extends CommandBase {
	@Override
	public String getCommandName() {
		return "quests";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/quests <reload|info|give-prestige|top> [args]";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0; // /quests top is public; sub-commands self-check for op
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if (args.length == 0) {
			sender.sendChatToPlayer(getCommandUsage(sender));
			return;
		}
		String sub = args[0];
		if ("reload".equalsIgnoreCase(sub)) {
			if (!isOp(sender)) { reply(sender, "§cYou need OP."); return; }
			String err = QuestConfigLoader.loadAll();
			if (err == null) {
				reply(sender, "§aReloaded " + QuestRegistry.CURRENT.all().size() + " quests, "
						+ QuestRegistry.CURRENT.allTitles().size() + " titles.");
				CompletionEngine.recomputeAll();
				broadcastConfigDigest();
				broadcastQuestDefinitions();
			} else {
				reply(sender, "§cReload failed: " + err);
			}
			return;
		}
		if ("info".equalsIgnoreCase(sub)) {
			if (args.length < 2) { reply(sender, "Usage: /quests info <questId>"); return; }
			String id = args[1];
			com.nao.questcycle.data.QuestDef q = QuestRegistry.CURRENT.get(id);
			if (q == null) { reply(sender, "§cNo such quest: " + id); return; }
			reply(sender, "§e" + q.name + "§r [" + q.section + " / " + q.categoryName + "]");
			reply(sender, q.desc);
			reply(sender, "Tasks: " + q.tasks.size() + ", requires: " + q.requires.size());
			return;
		}
		if ("give-prestige".equalsIgnoreCase(sub)) {
			if (!isOp(sender)) { reply(sender, "§cYou need OP."); return; }
			if (args.length < 3) { reply(sender, "Usage: /quests give-prestige <user> <n>"); return; }
			String user = args[1];
			int n;
			try { n = Integer.parseInt(args[2]); } catch (NumberFormatException nfe) { reply(sender, "§cBad number"); return; }
			PlayerStateCache.Entry e = PlayerStateCache.getOrLoad(user);
			e.persist.prestige += n;
			com.nao.questcycle.data.PersistStore.save(e.persist);
			reply(sender, "§a" + user + " prestige is now " + e.persist.prestige);
			MinecraftServer srv = MinecraftServer.getServer();
			if (srv != null) {
				EntityPlayerMP mp = srv.getConfigurationManager().getPlayerForUsername(user);
				if (mp != null) {
					PacketDispatcher.sendPacketToPlayer(
							QuestPacketHandler.wrap(PacketBuilder.prestigeAwarded(e.persist.prestige)), (Player) mp);
				}
			}
			return;
		}
		if ("top".equalsIgnoreCase(sub)) {
			int topN = 10;
			if (args.length >= 2) {
				try { topN = Integer.parseInt(args[1]); } catch (NumberFormatException nfe) {}
			}
			List<LeaderboardBuilder.Row> rows = LeaderboardBuilder.build(topN);
			if (rows.isEmpty()) { reply(sender, "§7No players tracked yet."); return; }
			reply(sender, "§e=== Top " + rows.size() + " ===");
			for (int i = 0; i < rows.size(); i++) {
				LeaderboardBuilder.Row r = rows.get(i);
				reply(sender, "§f#" + (i + 1) + " §b" + r.username + "§r  §6P:" + r.prestige
						+ "§r  §7T:" + r.titlesCount + "  cycle:" + r.cyclePct + "%");
			}
			return;
		}
		reply(sender, getCommandUsage(sender));
	}

	private static boolean isOp(ICommandSender sender) {
		if (!(sender instanceof EntityPlayerMP)) return true; // console
		MinecraftServer srv = MinecraftServer.getServer();
		if (srv == null) return false;
		return srv.getConfigurationManager().getOps().contains(((EntityPlayerMP) sender).username.toLowerCase());
	}

	private static void reply(ICommandSender sender, String msg) {
		sender.sendChatToPlayer(msg);
	}

	@Override
	public int compareTo(Object other) {
		if (other instanceof ICommand) return this.getCommandName().compareTo(((ICommand) other).getCommandName());
		return 0;
	}

	private static void broadcastConfigDigest() {
		byte[] payload = PacketBuilder.questConfigDigest(
				QuestRegistry.CURRENT.section(com.nao.questcycle.data.QuestSection.PRESTIGE).size(),
				QuestRegistry.CURRENT.section(com.nao.questcycle.data.QuestSection.ACHIEVEMENT).size(),
				QuestRegistry.CURRENT.allTitles().size());
		PacketDispatcher.sendPacketToAllPlayers(QuestPacketHandler.wrap(payload));
	}

	/** Push the full quest JSON to every connected client so their UI reflects the server's config. */
	private static void broadcastQuestDefinitions() {
		try {
			String qJson = QuestConfigLoader.readQuestsText();
			String aJson = QuestConfigLoader.readAchievementsText();
			byte[] payload = PacketBuilder.questDefinitions(qJson, aJson);
			PacketDispatcher.sendPacketToAllPlayers(QuestPacketHandler.wrap(payload));
		} catch (Throwable t) {
			System.err.println("[QuestCycle] broadcastQuestDefinitions failed: " + t.getMessage());
		}
	}
}
