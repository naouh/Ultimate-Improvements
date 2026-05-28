package com.nao.questcycle.core;

import com.nao.questcycle.data.PersistStore;
import com.nao.questcycle.data.PlayerPersistence;
import com.nao.questcycle.data.PlayerProgress;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.ProgressStore;
import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.QuestSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a ranked snapshot of every known player. Combines in-memory cache for
 * online players with per-file scan of the persist dir for offline-but-known.
 */
public final class LeaderboardBuilder {
	private LeaderboardBuilder() {}

	public static List<Row> build(int topN) {
		Set<String> seen = new HashSet<String>();
		List<Row> out = new ArrayList<Row>();

		// Online players first - already in memory.
		String[] online = PlayerStateCache.onlineUsernames();
		Set<String> onlineSet = new HashSet<String>();
		for (int i = 0; i < online.length; i++) onlineSet.add(online[i]);
		for (int i = 0; i < online.length; i++) {
			String u = online[i];
			seen.add(u);
			PlayerStateCache.Entry e = PlayerStateCache.getIfLoaded(u);
			if (e == null) continue;
			Row r = toRow(u, e.progress, e.persist);
			r.online = true;
			out.add(r);
		}

		// Offline players: read persist files + load progress on demand.
		String[] known = PersistStore.listKnownUsernames();
		for (int i = 0; i < known.length; i++) {
			String u = known[i];
			if (seen.contains(u)) continue;
			PlayerPersistence pp = PersistStore.load(u);
			PlayerProgress pr = ProgressStore.load(u);
			Row r = toRow(u, pr, pp);
			r.online = onlineSet.contains(u);
			out.add(r);
		}

		Collections.sort(out, new Comparator<Row>() {
			@Override public int compare(Row a, Row b) {
				if (a.prestige != b.prestige) return b.prestige - a.prestige;
				if (a.cyclePct != b.cyclePct) return b.cyclePct - a.cyclePct;
				if (a.titlesCount != b.titlesCount) return b.titlesCount - a.titlesCount;
				return a.username.compareToIgnoreCase(b.username);
			}
		});

		if (topN > 0 && out.size() > topN) return new ArrayList<Row>(out.subList(0, topN));
		return out;
	}

	private static Row toRow(String username, PlayerProgress progress, PlayerPersistence persist) {
		Row r = new Row();
		r.username = username;
		r.prestige = persist.prestige;
		r.titlesCount = persist.earnedTitleIds.size();
		r.cyclePct = cyclePct(progress);
		if (persist.activeTitleId != null) {
			com.nao.questcycle.data.TitleDef t = com.nao.questcycle.data.QuestRegistry.CURRENT.title(persist.activeTitleId);
			if (t != null) r.activeTitleDisplay = t.displayName;
		}
		return r;
	}

	private static int cyclePct(PlayerProgress progress) {
		QuestRegistry reg = QuestRegistry.CURRENT;
		List<QuestDef> pq = reg.section(QuestSection.PRESTIGE);
		if (pq.isEmpty()) return 0;
		int complete = 0;
		for (int i = 0; i < pq.size(); i++) {
			if (progress.isQuestComplete(pq.get(i))) complete++;
		}
		return (int) ((complete * 100L) / pq.size());
	}

	public static final class Row {
		public String username;
		public int prestige;
		public int titlesCount;
		public int cyclePct;
		public boolean online;
		public String activeTitleDisplay = ""; // populated for tab/leaderboard from persist
	}
}
