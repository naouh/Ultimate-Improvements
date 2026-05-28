package com.nao.questcycle.task;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * "Have N items in inventory at once" - the counter mirrors the player's
 * current inventory count rather than accumulating from pickups, so the
 * drop-and-loop trick (toss one, pick it up, repeat) doesn't grind the count.
 *
 * Matches by display name like {@link NamedMatchTask}. Once the count reaches
 * the target, completion fires through the normal CompletionEngine path and
 * any title reward is granted permanently - the player can drop the items
 * afterwards without losing the title.
 *
 * Driven by InventoryDeltaTracker, which calls {@link #countInInventory} each
 * scan tick and absolute-sets the counter via PlayerProgress.setTaskCount.
 */
public final class HaveNamedTask implements QuestTask {
	public static final String TYPE = "have_named";

	public final String displayLabel;
	public final String needle;
	public final boolean substring;
	public final int target;
	public final int iconItemId;
	public final int iconItemMeta;

	public HaveNamedTask(String displayLabel, boolean substring, int target, int iconItemId, int iconItemMeta) {
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

	/** Not invoked - this task is absolute-scanned by InventoryDeltaTracker. */
	@Override public int matches(EntityPlayerMP player, Object ctx) { return 0; }

	@Override public String displayName() { return displayLabel; }

	/** Sum of stackSize across the player's main + armor inventory whose display name matches. */
	public int countInInventory(EntityPlayerMP player) {
		if (player == null || player.inventory == null) return 0;
		int n = 0;
		n += scanArray(player.inventory.mainInventory);
		n += scanArray(player.inventory.armorInventory);
		return n;
	}

	private int scanArray(ItemStack[] arr) {
		if (arr == null) return 0;
		int total = 0;
		for (int i = 0; i < arr.length; i++) {
			ItemStack s = arr[i];
			if (s == null) continue;
			if (s.itemID <= 0 || s.itemID >= Item.itemsList.length || Item.itemsList[s.itemID] == null) continue;
			String name;
			try { name = s.getItem().getItemDisplayName(s); } catch (Throwable t) { continue; }
			if (name == null) continue;
			String haystack = name.toLowerCase();
			boolean match = substring ? haystack.contains(needle) : haystack.equals(needle);
			if (match) total += s.stackSize;
		}
		return total;
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
