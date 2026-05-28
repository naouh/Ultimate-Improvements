package com.nao.questcycle.network;

import com.nao.questcycle.data.PlayerPersistence;
import com.nao.questcycle.data.PlayerProgress;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/** Hand-rolled byte[] builders for outbound packets. Mirrors claimteam's PacketHandler style. */
public final class PacketBuilder {
	private PacketBuilder() {}

	public static byte[] fullState(PlayerProgress progress, PlayerPersistence persist) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_FULL_STATE);
			out.writeUTF(progress.username);
			// progress
			Map<String, int[]> snap = progress.snapshotCounters();
			out.writeInt(snap.size());
			for (Map.Entry<String, int[]> e : snap.entrySet()) {
				out.writeUTF(e.getKey());
				int[] arr = e.getValue();
				out.writeByte(arr.length);
				for (int i = 0; i < arr.length; i++) out.writeInt(arr[i]);
			}
			// persist
			out.writeInt(persist.prestige);
			out.writeInt(persist.earnedTitleIds.size());
			for (String t : persist.earnedTitleIds) out.writeUTF(t);
			out.writeUTF(persist.activeTitleId == null ? "" : persist.activeTitleId);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] progressDelta(String questId, int taskIdx, int newCount, boolean questComplete) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_PROGRESS_DELTA);
			out.writeUTF(questId);
			out.writeByte(taskIdx);
			out.writeInt(newCount);
			out.writeBoolean(questComplete);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] toast(String text, int accentArgb) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_TOAST);
			out.writeUTF(text);
			out.writeInt(accentArgb);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] prestigeAwarded(int newPrestige) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_PRESTIGE_AWARDED);
			out.writeInt(newPrestige);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] setTitleRequest(String titleIdOrEmpty) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_SET_TITLE);
			out.writeUTF(titleIdOrEmpty == null ? "" : titleIdOrEmpty);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] leaderboardRequest(int topN) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_LEADERBOARD_REQUEST);
			out.writeInt(topN);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] leaderboardResponse(java.util.List<com.nao.questcycle.core.LeaderboardBuilder.Row> rows) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_LEADERBOARD_RESPONSE);
			out.writeInt(rows.size());
			for (int i = 0; i < rows.size(); i++) {
				com.nao.questcycle.core.LeaderboardBuilder.Row r = rows.get(i);
				out.writeUTF(r.username);
				out.writeInt(r.prestige);
				out.writeInt(r.titlesCount);
				out.writeInt(r.cyclePct);
				out.writeBoolean(r.online);
				out.writeUTF(r.activeTitleDisplay == null ? "" : r.activeTitleDisplay);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] questDefinitions(String questsJson, String achievementsJson) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_QUEST_DEFINITIONS);
			byte[] q = questsJson == null ? new byte[0] : questsJson.getBytes("UTF-8");
			byte[] a = achievementsJson == null ? new byte[0] : achievementsJson.getBytes("UTF-8");
			out.writeInt(q.length);
			out.write(q);
			out.writeInt(a.length);
			out.write(a);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	public static byte[] questConfigDigest(int totalPrestige, int totalAchievements, int totalTitles) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		try {
			out.writeByte(QuestPacketHandler.PKT_QUEST_CONFIG);
			out.writeInt(totalPrestige);
			out.writeInt(totalAchievements);
			out.writeInt(totalTitles);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}
}
