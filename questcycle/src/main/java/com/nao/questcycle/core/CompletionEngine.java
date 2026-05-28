package com.nao.questcycle.core;

import com.nao.questcycle.data.PlayerPersistence;
import com.nao.questcycle.data.PlayerProgress;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.QuestSection;
import com.nao.questcycle.data.TitleDef;
import com.nao.questcycle.network.PacketBuilder;
import com.nao.questcycle.network.QuestPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Single chokepoint called after any task counter increment.
 * Sends a PROGRESS_DELTA to the player, fires toast on quest completion,
 * grants titles for ACHIEVEMENTs, recomputes the prestige cycle for PRESTIGEs.
 */
public final class CompletionEngine {
	private CompletionEngine() {}

	public static void onTaskProgress(EntityPlayerMP player, QuestDef q, int taskIdx, int before, int after) {
		PlayerStateCache.Entry e = PlayerStateCache.getOrLoad(player.username);
		boolean questComplete = e.progress.isQuestComplete(q);

		// Push incremental delta so the client GUI updates without a full state push.
		PacketDispatcher.sendPacketToPlayer(
				QuestPacketHandler.wrap(PacketBuilder.progressDelta(q.id, taskIdx, after, questComplete)),
				(Player) player);

		if (!questComplete) return;

		// Quest just completed.
		String headline = "Quest complete: " + q.name;
		PacketDispatcher.sendPacketToPlayer(
				QuestPacketHandler.wrap(PacketBuilder.toast(headline, 0xFF22DD55)),
				(Player) player);

		if (q.section == QuestSection.ACHIEVEMENT) {
			grantTitleIfAchievement(player, q, e.persist);
		} else {
			checkPrestigeCycle(player, e.progress, e.persist);
		}
	}

	private static void grantTitleIfAchievement(EntityPlayerMP player, QuestDef q, PlayerPersistence persist) {
		TitleDef t = q.titleReward;
		if (t == null) return;
		if (persist.earnedTitleIds.add(t.id)) {
			PacketDispatcher.sendPacketToPlayer(
					QuestPacketHandler.wrap(PacketBuilder.toast("Title earned: " + t.displayName, 0xFFFFD060)),
					(Player) player);
			PacketDispatcher.sendPacketToPlayer(
					QuestPacketHandler.wrap(PacketBuilder.fullState(
							PlayerStateCache.getOrLoad(player.username).progress, persist)),
					(Player) player);
		}
	}

	private static void checkPrestigeCycle(EntityPlayerMP player, PlayerProgress progress, PlayerPersistence persist) {
		// Already awarded this cycle - progress stays completed visually until the
		// world is wiped (which deletes the progress file and the flag with it).
		if (progress.cycleAwarded) return;
		QuestRegistry reg = QuestRegistry.CURRENT;
		// All PRESTIGE quests must be complete.
		for (QuestDef pq : reg.section(QuestSection.PRESTIGE)) {
			if (!progress.isQuestComplete(pq)) return;
		}
		// Award - but keep the quest progress so the player still sees everything green.
		persist.prestige++;
		progress.cycleAwarded = true;

		PacketDispatcher.sendPacketToPlayer(
				QuestPacketHandler.wrap(PacketBuilder.toast(
						"§6+1 Prestige!§r  (now " + persist.prestige + ")", 0xFFFFAA00)),
				(Player) player);
		PacketDispatcher.sendPacketToPlayer(
				QuestPacketHandler.wrap(PacketBuilder.prestigeAwarded(persist.prestige)),
				(Player) player);
		PacketDispatcher.sendPacketToPlayer(
				QuestPacketHandler.wrap(PacketBuilder.fullState(progress, persist)),
				(Player) player);

		com.nao.questcycle.data.ProgressStore.save(progress);
		com.nao.questcycle.data.PersistStore.save(persist);
	}

	/** Server-only: when the quest config changes, re-evaluate everyone for cycle completion + title grants. */
	public static void recomputeAll() {
		String[] online = PlayerStateCache.onlineUsernames();
		for (int i = 0; i < online.length; i++) {
			PlayerStateCache.Entry e = PlayerStateCache.getIfLoaded(online[i]);
			if (e == null) continue;
			net.minecraft.server.MinecraftServer srv = net.minecraft.server.MinecraftServer.getServer();
			if (srv == null) continue;
			EntityPlayerMP mp = srv.getConfigurationManager().getPlayerForUsername(online[i]);
			if (mp == null) continue;
			// Re-push full state so the GUI sees any new quests.
			PacketDispatcher.sendPacketToPlayer(
					QuestPacketHandler.wrap(PacketBuilder.fullState(e.progress, e.persist)),
					(Player) mp);
			// Check title grants for already-met achievements.
			for (QuestDef q : QuestRegistry.CURRENT.section(QuestSection.ACHIEVEMENT)) {
				if (q.titleReward != null && e.progress.isQuestComplete(q) && !e.persist.earnedTitleIds.contains(q.titleReward.id)) {
					grantTitleIfAchievement(mp, q, e.persist);
				}
			}
			checkPrestigeCycle(mp, e.progress, e.persist);
		}
	}
}
