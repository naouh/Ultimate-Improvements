package com.nao.questcycle.network;

import com.nao.questcycle.QuestCycleMod;
import com.nao.questcycle.core.LeaderboardBuilder;
import com.nao.questcycle.data.PlayerPersistence;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.TitleDef;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Central packet dispatch. Server-side handling for C->S packets; client-side
 * decoding lives in ClientPacketHandler.
 */
public final class QuestPacketHandler implements IPacketHandler {
	public static final byte PKT_FULL_STATE          = 1; // S->C
	public static final byte PKT_PROGRESS_DELTA      = 2; // S->C
	public static final byte PKT_QUEST_CONFIG        = 3; // S->C (digest)
	public static final byte PKT_TOAST               = 4; // S->C
	public static final byte PKT_SET_TITLE           = 5; // C->S
	public static final byte PKT_PRESTIGE_AWARDED    = 6; // S->C
	public static final byte PKT_LEADERBOARD_REQUEST = 7; // C->S
	public static final byte PKT_LEADERBOARD_RESPONSE= 8; // S->C
	public static final byte PKT_QUEST_DEFINITIONS   = 9; // S->C (full JSON of both files)

	@Override
	public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
		if (packet == null || packet.data == null || packet.data.length == 0) return;
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
		try {
			byte id = in.readByte();
			boolean onServer = player instanceof EntityPlayerMP;
			if (onServer) handleServerSide(id, in, (EntityPlayerMP) player);
			else com.nao.questcycle.network.ClientPacketHandler.handle(id, in);
		} catch (Throwable t) {
			System.err.println("[QuestCycle] packet dispatch failed: " + t.getMessage());
			t.printStackTrace();
		}
	}

	private void handleServerSide(byte id, DataInputStream in, EntityPlayerMP player) throws Exception {
		switch (id) {
			case PKT_SET_TITLE: {
				String titleId = in.readUTF();
				PlayerStateCache.Entry e = PlayerStateCache.getOrLoad(player.username);
				PlayerPersistence p = e.persist;
				TitleDef applied = null;
				if (titleId == null || titleId.length() == 0) {
					p.activeTitleId = null;
				} else if (p.earnedTitleIds.contains(titleId)) {
					TitleDef t = QuestRegistry.CURRENT.title(titleId);
					if (t != null) { p.activeTitleId = titleId; applied = t; }
				}
				// Persist to disk immediately — otherwise a server restart auto-applies the
				// previous title on login and the change appears to revert.
				com.nao.questcycle.data.PersistStore.save(p);
				// Push fresh state so client UI updates immediately.
				PacketDispatcher.sendPacketToPlayer(wrap(PacketBuilder.fullState(e.progress, p)), (Player) player);
				com.nao.questcycle.core.TabListUpdater.refreshFor(player);
				// Drive the Bukkit-side EssentialsChat prefix (mirrors what /title set does).
				if (applied != null) com.nao.questcycle.event.BukkitChatBridge.setPrefix(player.username, applied.displayName);
				else com.nao.questcycle.event.BukkitChatBridge.clearPrefix(player.username);
				return;
			}
			case PKT_LEADERBOARD_REQUEST: {
				int topN = in.readInt();
				List<LeaderboardBuilder.Row> rows = LeaderboardBuilder.build(topN);
				PacketDispatcher.sendPacketToPlayer(wrap(PacketBuilder.leaderboardResponse(rows)), (Player) player);
				return;
			}
			default:
				System.err.println("[QuestCycle] unexpected server-side packet id " + id);
		}
	}

	public static Packet250CustomPayload wrap(byte[] payload) {
		Packet250CustomPayload p = new Packet250CustomPayload();
		p.channel = QuestCycleMod.CHANNEL;
		p.data = payload;
		p.length = payload.length;
		return p;
	}
}
