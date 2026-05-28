package com.nao.questcycle.task;

import com.nao.questcycle.event.CraftCtx;
import com.nao.questcycle.event.PickupCtx;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Matches by ItemStack display name instead of itemId+meta. Useful for mod
 * machines (IC2, GregTech) where the item id is shared across many variants
 * keyed by meta, and the meta values aren't easily discoverable.
 *
 * Type discriminators: "craft_named" (fires for CraftCtx) and "obtain_named"
 * (fires for PickupCtx). Match is case-insensitive, optionally substring.
 *
 * Optional `iconItem`/`iconMeta` fields drive the GUI icon since we don't have
 * an item id to derive it from automatically.
 */
public final class NamedMatchTask implements QuestTask {
	public static final String TYPE_CRAFT = "craft_named";
	public static final String TYPE_OBTAIN = "obtain_named";

	public final String type;
	/** Original-cased display label (for GUI). */
	public final String displayLabel;
	/** Lowercased version used for case-insensitive matching. */
	public final String needle;
	public final boolean substring;        // true = contains match, false = exact
	public final int target;
	public final int iconItemId;
	public final int iconItemMeta;

	public NamedMatchTask(String type, String displayLabel, boolean substring, int target, int iconItemId, int iconItemMeta) {
		this.type = type;
		this.displayLabel = displayLabel == null ? "" : displayLabel;
		this.needle = this.displayLabel.toLowerCase();
		this.substring = substring;
		this.target = target;
		this.iconItemId = iconItemId;
		this.iconItemMeta = iconItemMeta;
	}

	@Override public String type() { return type; }
	@Override public int targetCount() { return target; }
	@Override public int targetItemId() { return iconItemId; }
	@Override public int targetItemMeta() { return iconItemMeta; }

	@Override
	public int matches(EntityPlayerMP player, Object ctx) {
		ItemStack s;
		if (TYPE_CRAFT.equals(type) && ctx instanceof CraftCtx) s = ((CraftCtx) ctx).crafted;
		else if (TYPE_OBTAIN.equals(type) && ctx instanceof PickupCtx) s = ((PickupCtx) ctx).picked;
		else return 0;
		if (s == null || s.itemID <= 0 || s.itemID >= Item.itemsList.length || Item.itemsList[s.itemID] == null) return 0;
		String name;
		try {
			name = s.getItem().getItemDisplayName(s);
		} catch (Throwable t) {
			return 0;
		}
		if (name == null) return 0;
		String haystack = name.toLowerCase();
		if (substring ? haystack.contains(needle) : haystack.equals(needle)) {
			return s.stackSize;
		}
		return 0;
	}

	@Override
	public String displayName() {
		return displayLabel;
	}

	@Override
	public Map<String, Object> toJson() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("type", type);
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
