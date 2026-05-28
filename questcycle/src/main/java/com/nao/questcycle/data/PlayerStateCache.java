package com.nao.questcycle.data;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory holder of (PlayerProgress, PlayerPersistence) for all currently
 * online players. Loaded on join, saved on leave + on server stop.
 */
public final class PlayerStateCache {
	private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();

	private PlayerStateCache() {}

	public static synchronized Entry getOrLoad(String username) {
		Entry e = CACHE.get(username);
		if (e == null) {
			e = new Entry(ProgressStore.load(username), PersistStore.load(username));
			CACHE.put(username, e);
		}
		return e;
	}

	public static synchronized Entry getIfLoaded(String username) {
		return CACHE.get(username);
	}

	public static synchronized void saveAndRemove(String username) {
		Entry e = CACHE.remove(username);
		if (e != null) {
			ProgressStore.save(e.progress);
			PersistStore.save(e.persist);
		}
	}

	public static synchronized void saveAll() {
		for (Map.Entry<String, Entry> me : CACHE.entrySet()) {
			ProgressStore.save(me.getValue().progress);
			PersistStore.save(me.getValue().persist);
		}
	}

	public static synchronized String[] onlineUsernames() {
		return CACHE.keySet().toArray(new String[CACHE.size()]);
	}

	public static final class Entry {
		public final PlayerProgress progress;
		public final PlayerPersistence persist;
		public Entry(PlayerProgress progress, PlayerPersistence persist) {
			this.progress = progress;
			this.persist = persist;
		}
	}
}
