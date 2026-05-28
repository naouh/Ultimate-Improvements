package com.nao.questcycle.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Lazy client-side index of every item registered in the game, keyed by
 * lower-case display name. Lets craft_named / obtain_named tasks resolve a
 * concrete (itemId, meta) for icon rendering without the JSON needing to
 * spell it out.
 *
 * Built once on first lookup (after all mods have registered their items).
 * Call reset() if mods get reloaded (none of our flows trigger that today).
 */
public final class ItemNameIndex {
	private static volatile Map<String, int[]> index;

	private ItemNameIndex() {
	}

	/** Returns {itemId, meta} for the given display name, or null if no match. */
	public static int[] lookup(String displayName) {
		if (displayName == null || displayName.length() == 0) return null;
		if (index == null) buildIndex();
		Map<String, int[]> snap = index;
		if (snap == null) return null;
		String key = displayName.toLowerCase();
		int[] hit = snap.get(key);
		if (hit != null) return hit;
		// substring fallback so "iridium" matches "Iridium Ore", "Iridium Plate", etc.
		for (Map.Entry<String, int[]> e : snap.entrySet()) {
			if (e.getKey().contains(key)) return e.getValue();
		}
		return null;
	}

	public static void reset() {
		index = null;
	}

	private static synchronized void buildIndex() {
		if (index != null) return;
		Map<String, int[]> m = new HashMap<String, int[]>();
		for (int id = 0; id < Item.itemsList.length; id++) {
			Item it = Item.itemsList[id];
			if (it == null) continue;
			try {
				List<ItemStack> subs = new ArrayList<ItemStack>();
				it.getSubItems(id, null, subs);
				if (subs.isEmpty()) {
					addEntry(m, new ItemStack(it, 1, 0));
				} else {
					for (int i = 0; i < subs.size(); i++) addEntry(m, subs.get(i));
				}
			} catch (Throwable t) {
				try { addEntry(m, new ItemStack(it, 1, 0)); } catch (Throwable ignored) {}
			}
		}
		index = m;
		System.out.println("[QuestCycle] item-name index built: " + m.size() + " entries");
	}

	private static void addEntry(Map<String, int[]> m, ItemStack s) {
		if (s == null || s.itemID <= 0) return;
		try {
			String name = s.getItem().getItemDisplayName(s);
			if (name == null || name.length() == 0) return;
			String key = name.toLowerCase();
			// First-wins so vanilla doesn't get overridden by a mod re-registering "Iron Ingot".
			if (!m.containsKey(key)) m.put(key, new int[]{s.itemID, s.getItemDamage()});
		} catch (Throwable ignored) {
		}
	}
}
