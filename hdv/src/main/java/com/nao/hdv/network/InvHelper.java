package com.nao.hdv.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/** Server-side inventory math for all-or-nothing transfers (so a purchase never partially completes). */
public final class InvHelper {
	private InvHelper() {}

	private static boolean canStack(ItemStack a, ItemStack b) {
		return a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b) && a.isStackable();
	}

	/** How many more items of {@code unit} fit into the player's 36 main slots. */
	public static int freeSpaceFor(InventoryPlayer inv, ItemStack unit) {
		int max = Math.min(unit.getMaxStackSize(), inv.getInventoryStackLimit());
		if (max < 1) max = 1;
		int free = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.mainInventory[i];
			if (s == null) {
				free += max;
			} else if (canStack(s, unit)) {
				int room = max - s.stackSize;
				if (room > 0) free += room;
			}
		}
		return free;
	}

	public static boolean canFit(EntityPlayerMP p, ItemStack unit, int count) {
		return freeSpaceFor(p.inventory, unit) >= count;
	}

	/** Adds exactly {@code count} of {@code unit} to the player. Caller must have checked {@link #canFit}. */
	public static void giveAll(EntityPlayerMP p, ItemStack unit, int count) {
		int max = unit.getMaxStackSize();
		if (max < 1) max = 1;
		int remaining = count;
		while (remaining > 0) {
			int give = Math.min(max, remaining);
			ItemStack s = unit.copy();
			s.stackSize = give;
			p.inventory.addItemStackToInventory(s);
			remaining -= give;
		}
		p.inventoryContainer.detectAndSendChanges();
	}
}
