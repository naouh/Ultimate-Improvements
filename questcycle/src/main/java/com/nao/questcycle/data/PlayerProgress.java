package com.nao.questcycle.data;

import com.nao.questcycle.config.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player, per-quest counters. Lives in world/questcycle/players/<user>.json -
 * wiped together with the world dir on a server reset, which is the whole point
 * (so the prestige cycle starts fresh).
 *
 * Counters are parallel to QuestDef.tasks[]. A counter >= task.targetCount() means
 * that task is complete; a quest is complete iff all its tasks are complete.
 */
public final class PlayerProgress {
	public final String username;
	private final Map<String, int[]> taskCounters;
	/** Quest ids that have already fired their completion celebration this cycle. Prevents the
	 * "Quest complete" toast / title grant from re-firing when a quest re-crosses its target
	 * (e.g. a have_named counter that mirrors inventory after a drop + re-pickup). */
	private final Set<String> celebratedQuestIds;
	/** True once the player has been awarded +1 prestige for the CURRENT cycle. Cleared on world reset (since the whole file gets deleted with the world dir). */
	public boolean cycleAwarded;

	public PlayerProgress(String username) {
		this.username = username;
		this.taskCounters = new HashMap<String, int[]>();
		this.celebratedQuestIds = new HashSet<String>();
		this.cycleAwarded = false;
	}

	public int getTaskCount(String questId, int taskIdx) {
		int[] arr = taskCounters.get(questId);
		if (arr == null || taskIdx < 0 || taskIdx >= arr.length) return 0;
		return arr[taskIdx];
	}

	/**
	 * Sets the counter to an absolute value (clamped to [0, targetCount]).
	 * Used by inventory-have tasks where the counter mirrors current inventory
	 * rather than accumulating. Returns the new value.
	 */
	public int setTaskCount(String questId, int taskIdx, int value, int taskCountSize, int targetCount) {
		int[] arr = taskCounters.get(questId);
		if (arr == null || arr.length != taskCountSize) {
			int[] resized = new int[taskCountSize];
			if (arr != null) {
				System.arraycopy(arr, 0, resized, 0, Math.min(arr.length, taskCountSize));
			}
			arr = resized;
			taskCounters.put(questId, arr);
		}
		int v = value;
		if (v > targetCount) v = targetCount;
		if (v < 0) v = 0;
		arr[taskIdx] = v;
		return v;
	}

	/** Bumps the counter by `n`, capped at `targetCount`. Returns the new value. */
	public int incTask(String questId, int taskIdx, int n, int taskCountSize, int targetCount) {
		int[] arr = taskCounters.get(questId);
		if (arr == null || arr.length != taskCountSize) {
			int[] resized = new int[taskCountSize];
			if (arr != null) {
				System.arraycopy(arr, 0, resized, 0, Math.min(arr.length, taskCountSize));
			}
			arr = resized;
			taskCounters.put(questId, arr);
		}
		int v = arr[taskIdx] + n;
		if (v > targetCount) v = targetCount;
		if (v < 0) v = 0;
		arr[taskIdx] = v;
		return v;
	}

	public boolean isQuestComplete(QuestDef q) {
		if (q.tasks.isEmpty()) return false;
		int[] arr = taskCounters.get(q.id);
		if (arr == null) return false;
		for (int i = 0; i < q.tasks.size(); i++) {
			int target = q.tasks.get(i).targetCount();
			if (i >= arr.length || arr[i] < target) return false;
		}
		return true;
	}

	/** Whether all "requires" quest ids are complete. Empty requires = always true. */
	public boolean requiresMet(QuestDef q, QuestRegistry reg) {
		for (int i = 0; i < q.requires.size(); i++) {
			QuestDef r = reg.get(q.requires.get(i));
			if (r == null) continue;
			if (!isQuestComplete(r)) return false;
		}
		return true;
	}

	/** Marks a quest as having fired its completion celebration. Returns true the first time
	 * (per cycle), false if it was already celebrated. */
	public boolean markCelebrated(String questId) {
		return celebratedQuestIds.add(questId);
	}

	/** Resets all counters to zero. Used on prestige cycle award. */
	public void resetAll() {
		taskCounters.clear();
		celebratedQuestIds.clear();
	}

	public Map<String, int[]> snapshotCounters() {
		Map<String, int[]> copy = new HashMap<String, int[]>();
		for (Map.Entry<String, int[]> e : taskCounters.entrySet()) {
			copy.put(e.getKey(), e.getValue().clone());
		}
		return copy;
	}

	// ---- JSON serialization ----

	public Map<String, Object> toJson() {
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("version", Long.valueOf(1L));
		out.put("cycleAwarded", Boolean.valueOf(cycleAwarded));
		Map<String, Object> counters = new LinkedHashMap<String, Object>();
		for (Map.Entry<String, int[]> e : taskCounters.entrySet()) {
			List<Object> arr = new ArrayList<Object>(e.getValue().length);
			for (int i = 0; i < e.getValue().length; i++) arr.add(Long.valueOf(e.getValue()[i]));
			counters.put(e.getKey(), arr);
		}
		out.put("tasks", counters);
		out.put("celebrated", new ArrayList<Object>(celebratedQuestIds));
		return out;
	}

	public static PlayerProgress fromJson(String username, Map<String, Object> root) {
		PlayerProgress p = new PlayerProgress(username);
		p.cycleAwarded = Json.asBool(root.get("cycleAwarded"), false);
		Map<String, Object> counters = Json.asMap(root.get("tasks"));
		if (counters != null) {
			for (Map.Entry<String, Object> e : counters.entrySet()) {
				List<Object> arr = Json.asList(e.getValue());
				if (arr == null) continue;
				int[] vals = new int[arr.size()];
				for (int i = 0; i < arr.size(); i++) vals[i] = Json.asInt(arr.get(i), 0);
				p.taskCounters.put(e.getKey(), vals);
			}
		}
		List<Object> celebrated = Json.asList(root.get("celebrated"));
		if (celebrated != null) {
			for (int i = 0; i < celebrated.size(); i++) {
				Object id = celebrated.get(i);
				if (id != null) p.celebratedQuestIds.add(String.valueOf(id));
			}
		}
		return p;
	}
}
