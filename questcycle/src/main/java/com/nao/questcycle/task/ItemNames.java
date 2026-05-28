package com.nao.questcycle.task;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Best-effort item display name resolver used by QuestTask.displayName(). */
public final class ItemNames {
	private ItemNames() {}

	public static String lookup(int itemId, int meta) {
		if (itemId <= 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) {
			return "Item#" + itemId;
		}
		ItemStack stack = new ItemStack(Item.itemsList[itemId], 1, meta < 0 ? 0 : meta);
		try {
			String n = stack.getItem().getItemDisplayName(stack);
			if (n != null && n.length() > 0) return n;
		} catch (Throwable ignored) {
		}
		return "Item#" + itemId;
	}
}
