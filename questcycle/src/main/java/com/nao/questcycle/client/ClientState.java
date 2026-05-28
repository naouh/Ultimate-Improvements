package com.nao.questcycle.client;

import com.nao.questcycle.core.LeaderboardBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mirror of the server's per-player state, populated by
 * PKT_FULL_STATE / PKT_PROGRESS_DELTA and queried by the GUI.
 */
public final class ClientState {
	public static volatile String username = "";
	public static volatile int prestige = 0;
	public static final Set<String> earnedTitleIds = new HashSet<String>();
	public static volatile String activeTitleId = null;

	/** questId -> per-task counter array (parallel to QuestDef.tasks[]). */
	public static final Map<String, int[]> counters = new HashMap<String, int[]>();

	/** Last received leaderboard rows, sorted by server. */
	public static volatile List<LeaderboardBuilder.Row> leaderboard = new ArrayList<LeaderboardBuilder.Row>();

	/** Optional config digest from server. */
	public static volatile int totalPrestige = 0;
	public static volatile int totalAchievements = 0;
	public static volatile int totalTitles = 0;

	private ClientState() {}

	public static int taskCount(String questId, int taskIdx) {
		int[] arr = counters.get(questId);
		if (arr == null || taskIdx < 0 || taskIdx >= arr.length) return 0;
		return arr[taskIdx];
	}

	public static void setCounter(String questId, int taskIdx, int value, int taskTotalSize) {
		int[] arr = counters.get(questId);
		if (arr == null || arr.length != taskTotalSize) {
			int[] resized = new int[taskTotalSize];
			if (arr != null) System.arraycopy(arr, 0, resized, 0, Math.min(arr.length, taskTotalSize));
			arr = resized;
			counters.put(questId, arr);
		}
		if (taskIdx >= 0 && taskIdx < arr.length) arr[taskIdx] = value;
	}
}
