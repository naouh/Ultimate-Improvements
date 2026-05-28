package com.nao.questcycle.event;

import com.nao.questcycle.core.TabListUpdater;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.network.PacketBuilder;
import com.nao.questcycle.network.QuestPacketHandler;
import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Login: load progress + persist into cache, push full state to client.
 * Logout: save + drop from cache.
 */
public final class PlayerLifecycleHandler implements IPlayerTracker {
	@Override
	public void onPlayerLogin(EntityPlayer player) {
		if (!(player instanceof EntityPlayerMP)) return;
		EntityPlayerMP mp = (EntityPlayerMP) player;
		// Push server's authoritative quest definitions BEFORE state so client GUI has the right registry.
		try {
			String qJson = com.nao.questcycle.config.QuestConfigLoader.readQuestsText();
			String aJson = com.nao.questcycle.config.QuestConfigLoader.readAchievementsText();
			PacketDispatcher.sendPacketToPlayer(
					QuestPacketHandler.wrap(PacketBuilder.questDefinitions(qJson, aJson)),
					(cpw.mods.fml.common.network.Player) mp);
		} catch (Throwable t) {
			System.err.println("[QuestCycle] failed to push quest definitions on login: " + t.getMessage());
		}
		PlayerStateCache.Entry e = PlayerStateCache.getOrLoad(mp.username);
		byte[] packet = PacketBuilder.fullState(e.progress, e.persist);
		PacketDispatcher.sendPacketToPlayer(QuestPacketHandler.wrap(packet), (cpw.mods.fml.common.network.Player) mp);
		// Apply tab-list decoration if the player has a title equipped.
		TabListUpdater.refreshFor(mp);
		// Re-push the GroupManager prefix so EssentialsChat displays the title.
		// (GM persists prefixes itself, but this guards against config changes between sessions.)
		if (e.persist.activeTitleId != null) {
			com.nao.questcycle.data.TitleDef t = com.nao.questcycle.data.QuestRegistry.CURRENT.title(e.persist.activeTitleId);
			if (t != null) BukkitChatBridge.setPrefix(mp.username, t.displayName);
		} else {
			BukkitChatBridge.clearPrefix(mp.username);
		}
	}

	@Override
	public void onPlayerLogout(EntityPlayer player) {
		if (!(player instanceof EntityPlayerMP)) return;
		EntityPlayerMP mp = (EntityPlayerMP) player;
		// Send a tab-list removal (vanilla refresh handles it, but make sure decorated entry is gone)
		TabListUpdater.broadcastRemove(mp);
		InventoryDeltaTracker.onPlayerLogout(player);
		PlayerStateCache.saveAndRemove(mp.username);
	}

	@Override
	public void onPlayerChangedDimension(EntityPlayer player) {
	}

	@Override
	public void onPlayerRespawn(EntityPlayer player) {
	}
}
