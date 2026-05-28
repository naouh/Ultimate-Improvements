package com.nao.questcycle.core;

import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.TitleDef;
import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet201PlayerInfo;
import net.minecraft.server.MinecraftServer;

/**
 * Decorates a player's tab-list name with their active title via Packet201PlayerInfo.
 * Broadcasts to every connected player so everyone sees the same decorated name.
 *
 * 1.4.7 limits tab-list entries to 16 chars - if [Title] + " " + name exceeds that,
 * the title is shown alone (with a star to indicate truncation).
 */
public final class TabListUpdater {
	private TabListUpdater() {}

	private static final int MAX_LEN = 16;

	/** Tracks the last name we set per username so we can remove the OLD entry, not the current vanilla one. */
	private static final java.util.Map<String, String> lastSetName = new java.util.HashMap<String, String>();

	public static synchronized void refreshFor(EntityPlayerMP player) {
		MinecraftServer srv = MinecraftServer.getServer();
		if (srv == null) return;
		String decorated = decoratedName(player);
		String previous = lastSetName.get(player.username);
		if (previous == null) previous = player.username;
		if (previous.equals(decorated)) return; // no change
		Packet201PlayerInfo remove = new Packet201PlayerInfo(previous, false, 9999);
		Packet201PlayerInfo add = new Packet201PlayerInfo(decorated, true, getPing(player));
		PacketDispatcher.sendPacketToAllPlayers(remove);
		PacketDispatcher.sendPacketToAllPlayers(add);
		lastSetName.put(player.username, decorated);
	}

	public static synchronized void broadcastRemove(EntityPlayerMP player) {
		MinecraftServer srv = MinecraftServer.getServer();
		if (srv == null) return;
		String previous = lastSetName.remove(player.username);
		if (previous == null) previous = player.username;
		Packet201PlayerInfo remove = new Packet201PlayerInfo(previous, false, 9999);
		PacketDispatcher.sendPacketToAllPlayers(remove);
	}

	/**
	 * Builds the tab-list line, prioritizing the username (always visible).
	 * Layout: "<shortTag> <username>" if it fits in 16 chars, else username alone.
	 * shortTag is a short bracketed marker like "[C]" derived from the title.
	 */
	private static String decoratedName(EntityPlayerMP player) {
		PlayerStateCache.Entry e = PlayerStateCache.getIfLoaded(player.username);
		if (e == null || e.persist.activeTitleId == null) return player.username;
		TitleDef t = QuestRegistry.CURRENT.title(e.persist.activeTitleId);
		if (t == null) return player.username;
		String user = player.username;
		// First choice: shortTag + " " + user
		String prefixed = t.shortTag + " " + user;
		if (visibleLength(prefixed) <= MAX_LEN) return prefixed;
		// Second choice: shortTag glued to user (drop separator)
		String glued = t.shortTag + user;
		if (visibleLength(glued) <= MAX_LEN) return glued;
		// Last resort: username only (we always keep the name)
		return user;
	}

	/** Color codes (§X) don't count toward the visible 16-char limit (Packet201 measures raw length). */
	private static int visibleLength(String s) {
		// Packet201 measures the raw string length - color codes DO count toward the 16-char cap.
		return s.length();
	}

	private static int getPing(EntityPlayerMP p) {
		try {
			return p.ping;
		} catch (Throwable t) {
			return 0;
		}
	}
}
