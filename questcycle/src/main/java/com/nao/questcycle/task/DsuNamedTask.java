package com.nao.questcycle.task;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * "Store N of an item (matched by display name) inside a Deep Storage Unit at once."
 *
 * Unlike {@link HaveNamedTask} this is NOT inventory-scanned - a DSU holds far more than an
 * inventory ever could (up to ~2.1 billion). Instead it is evaluated on demand by
 * {@link com.nao.questcycle.event.DsuInteractHandler} when the player opens/right-clicks a
 * Deep Storage Unit: the DSU's stored item type + quantity are read server-side and, if the
 * name matches, the counter is set to the stored quantity (capped at target).
 *
 * Lets achievements like "store 500,000 UU-Matter" work without an inventory cap or the
 * drop-and-loop exploit - you genuinely have to fill the DSU.
 */
public final class DsuNamedTask implements QuestTask {
	public static final String TYPE = "dsu_named";

	public final String displayLabel;
	public final String needle;
	public final boolean substring;
	public final int target;
	public final int iconItemId;
	public final int iconItemMeta;

	public DsuNamedTask(String displayLabel, boolean substring, int target, int iconItemId, int iconItemMeta) {
		this.displayLabel = displayLabel == null ? "" : displayLabel;
		this.needle = this.displayLabel.toLowerCase();
		this.substring = substring;
		this.target = target;
		this.iconItemId = iconItemId;
		this.iconItemMeta = iconItemMeta;
	}

	@Override public String type() { return TYPE; }
	@Override public int targetCount() { return target; }
	@Override public int targetItemId() { return iconItemId; }
	@Override public int targetItemMeta() { return iconItemMeta; }

	/** Not invoked - this task is event-scanned when a DSU is opened. */
	@Override public int matches(EntityPlayerMP player, Object ctx) { return 0; }

	@Override public String displayName() { return displayLabel; }

	/** Whether a DSU's stored-item display name satisfies this task. */
	public boolean matchesName(String name) {
		if (name == null || name.length() == 0) return false;
		String haystack = name.toLowerCase();
		return substring ? haystack.contains(needle) : haystack.equals(needle);
	}

	@Override
	public Map<String, Object> toJson() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("type", TYPE);
		m.put("name", displayLabel);
		if (substring) m.put("substring", Boolean.TRUE);
		m.put("count", Long.valueOf(target));
		if (iconItemId > 0) {
			Map<String, Object> icon = new LinkedHashMap<String, Object>();
			icon.put("id", Long.valueOf(iconItemId));
			if (iconItemMeta != 0) icon.put("meta", Long.valueOf(iconItemMeta));
			m.put("icon", icon);
		}
		return m;
	}
}
