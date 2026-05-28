package com.nao.questcycle.event;

import net.minecraft.item.ItemStack;

/** Context passed to QuestTask.matches() for pickup events. */
public final class PickupCtx {
	public final ItemStack picked;

	public PickupCtx(ItemStack picked) {
		this.picked = picked;
	}
}
