package com.nao.questcycle.data;

import com.nao.questcycle.config.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player data that SURVIVES a world reset. Stored under
 * <serverRoot>/config/questcycle/persist/<user>.json - outside the world dir so
 * `rm -rf world && restart` keeps prestige + titles.
 */
public final class PlayerPersistence {
	public final String username;
	public int prestige;
	public final Set<String> earnedTitleIds;
	public String activeTitleId;

	public PlayerPersistence(String username) {
		this.username = username;
		this.prestige = 0;
		this.earnedTitleIds = new LinkedHashSet<String>();
		this.activeTitleId = null;
	}

	public Map<String, Object> toJson() {
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("version", Long.valueOf(1L));
		out.put("prestige", Long.valueOf(prestige));
		List<Object> titles = new ArrayList<Object>(earnedTitleIds);
		out.put("earnedTitles", titles);
		out.put("activeTitle", activeTitleId == null ? "" : activeTitleId);
		return out;
	}

	public static PlayerPersistence fromJson(String username, Map<String, Object> root) {
		PlayerPersistence p = new PlayerPersistence(username);
		p.prestige = Json.asInt(root.get("prestige"), 0);
		List<Object> titles = Json.asList(root.get("earnedTitles"));
		if (titles != null) {
			for (int i = 0; i < titles.size(); i++) {
				String s = Json.asString(titles.get(i), null);
				if (s != null && !s.isEmpty()) p.earnedTitleIds.add(s);
			}
		}
		String at = Json.asString(root.get("activeTitle"), "");
		p.activeTitleId = at.isEmpty() ? null : at;
		return p;
	}
}
