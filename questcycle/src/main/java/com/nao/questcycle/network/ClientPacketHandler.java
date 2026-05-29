package com.nao.questcycle.network;

import com.nao.questcycle.client.ClientState;
import com.nao.questcycle.client.hud.ToastQueue;
import com.nao.questcycle.core.LeaderboardBuilder;
import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

/** Decodes server-pushed packets into the static ClientState + ToastQueue. */
public final class ClientPacketHandler {
	private ClientPacketHandler() {}

	public static void handle(byte id, DataInputStream in) throws Exception {
		switch (id) {
			case QuestPacketHandler.PKT_FULL_STATE:        readFullState(in); return;
			case QuestPacketHandler.PKT_PROGRESS_DELTA:    readProgressDelta(in); return;
			case QuestPacketHandler.PKT_TOAST:             readToast(in); return;
			case QuestPacketHandler.PKT_PRESTIGE_AWARDED:  readPrestige(in); return;
			case QuestPacketHandler.PKT_LEADERBOARD_RESPONSE: readLeaderboard(in); return;
			case QuestPacketHandler.PKT_QUEST_CONFIG:      readConfigDigest(in); return;
			case QuestPacketHandler.PKT_QUEST_DEFINITIONS: readQuestDefinitions(in); return;
			default:
				System.err.println("[QuestCycle] unexpected client-side packet id " + id);
		}
	}

	private static void readFullState(DataInputStream in) throws Exception {
		ClientState.username = in.readUTF();
		int counterCount = in.readInt();
		ClientState.counters.clear();
		for (int i = 0; i < counterCount; i++) {
			String questId = in.readUTF();
			int len = in.readByte() & 0xFF;
			int[] arr = new int[len];
			for (int j = 0; j < len; j++) arr[j] = in.readInt();
			ClientState.counters.put(questId, arr);
		}
		ClientState.prestige = in.readInt();
		int titleCount = in.readInt();
		ClientState.earnedTitleIds.clear();
		for (int i = 0; i < titleCount; i++) ClientState.earnedTitleIds.add(in.readUTF());
		String at = in.readUTF();
		ClientState.activeTitleId = at == null || at.length() == 0 ? null : at;
	}

	private static void readProgressDelta(DataInputStream in) throws Exception {
		String questId = in.readUTF();
		int taskIdx = in.readByte() & 0xFF;
		int newCount = in.readInt();
		boolean complete = in.readBoolean();
		QuestDef q = QuestRegistry.CURRENT.get(questId);
		int sz = q == null ? Math.max(1, taskIdx + 1) : q.tasks.size();
		ClientState.setCounter(questId, taskIdx, newCount, sz);
		if (complete) {
			// Best-effort visual hint - real toast is sent separately via PKT_TOAST.
		}
	}

	private static void readToast(DataInputStream in) throws Exception {
		String text = in.readUTF();
		int accent = in.readInt();
		ToastQueue.push(text, accent);
	}

	private static void readPrestige(DataInputStream in) throws Exception {
		ClientState.prestige = in.readInt();
	}

	private static void readLeaderboard(DataInputStream in) throws Exception {
		int n = in.readInt();
		List<LeaderboardBuilder.Row> rows = new ArrayList<LeaderboardBuilder.Row>(n);
		for (int i = 0; i < n; i++) {
			LeaderboardBuilder.Row r = new LeaderboardBuilder.Row();
			r.username = in.readUTF();
			r.prestige = in.readInt();
			r.titlesCount = in.readInt();
			r.cyclePct = in.readInt();
			r.online = in.readBoolean();
			r.activeTitleDisplay = in.readUTF();
			rows.add(r);
		}
		ClientState.leaderboard = rows;
	}

	private static void readConfigDigest(DataInputStream in) throws Exception {
		ClientState.totalPrestige = in.readInt();
		ClientState.totalAchievements = in.readInt();
		ClientState.totalTitles = in.readInt();
	}

	private static void readQuestDefinitions(DataInputStream in) throws Exception {
		int qLen = in.readInt();
		byte[] qBuf = new byte[qLen];
		in.readFully(qBuf);
		int aLen = in.readInt();
		byte[] aBuf = new byte[aLen];
		in.readFully(aBuf);
		String qJson = gunzip(qBuf);
		String aJson = gunzip(aBuf);
		String err = com.nao.questcycle.config.QuestConfigLoader.loadFromStrings(qJson, aJson);
		if (err != null) {
			System.err.println("[QuestCycle] server-pushed quest config rejected: " + err);
		} else {
			System.out.println("[QuestCycle] applied server-pushed quest config (" + qLen + "+" + aLen + " gzip bytes)");
		}
	}

	private static String gunzip(byte[] data) throws Exception {
		if (data == null || data.length == 0) return "";
		java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data));
		java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
		byte[] tmp = new byte[4096];
		int n;
		while ((n = gz.read(tmp)) != -1) bos.write(tmp, 0, n);
		gz.close();
		return new String(bos.toByteArray(), "UTF-8");
	}
}
