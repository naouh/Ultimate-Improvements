package com.nao.questcycle.event;

import net.minecraft.item.ItemStack;

/** Context passed to QuestTask.matches() for craft events. */
public final class CraftCtx {
	public final ItemStack crafted;

	public CraftCtx(ItemStack crafted) {
		this.crafted = crafted;
	}
}
