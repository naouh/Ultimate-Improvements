package com.nao.questcycle.event;

import java.lang.reflect.Method;

import com.nao.questcycle.core.CompletionEngine;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.task.DsuNamedTask;
import com.nao.questcycle.task.QuestTask;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Validates {@link DsuNamedTask} quests when a player opens (right-clicks) a Deep Storage Unit.
 *
 * Reads the DSU's stored item type + quantity straight off the server-side tile entity via the
 * MFR {@code IDeepStorageUnit.getStoredItemType()} API (reflection, so there's no hard dependency
 * on MFR). A DSU can hold ~2.1 billion of one item, so this is how achievements like
 * "store 500,000 UU-Matter" are detected - no inventory cap, no drop-loop exploit.
 */
public final class DsuInteractHandler {

	private static volatile boolean resolved = false;
	private static volatile Class<?> dsuInterface;
	private static volatile Method getStoredItemType;

	@ForgeSubscribe
	public void onInteract(PlayerInteractEvent e) {
		if (e == null || e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
		if (!(e.entityPlayer instanceof EntityPlayerMP)) return; // server-side player only
		EntityPlayerMP player = (EntityPlayerMP) e.entityPlayer;
		if (player.worldObj == null || player.worldObj.isRemote) return;

		TileEntity te = player.worldObj.getBlockTileEntity(e.x, e.y, e.z);
		ItemStack stored = readStored(te);
		if (stored == null || stored.stackSize <= 0) return;

		String name;
		try {
			name = stored.getItem().getItemDisplayName(stored);
		} catch (Throwable t) {
			return;
		}
		if (name == null) return;
		int qty = stored.stackSize;

		PlayerStateCache.Entry entry = PlayerStateCache.getIfLoaded(player.username);
		if (entry == null) return;
		QuestRegistry reg = QuestRegistry.CURRENT;
		if (reg == null) return;

		for (QuestDef q : reg.all().values()) {
			for (int i = 0; i < q.tasks.size(); i++) {
				QuestTask t = q.tasks.get(i);
				if (!(t instanceof DsuNamedTask)) continue;
				DsuNamedTask d = (DsuNamedTask) t;
				if (!d.matchesName(name)) continue;

				int prev = entry.progress.getTaskCount(q.id, i);
				if (prev >= d.target) continue; // already met - milestone stays locked
				// All-or-nothing: only validate when the DSU holds the full target at once.
				// A partial amount is meaningless for this kind of goal, so don't move the bar.
				if (qty < d.target) continue;
				entry.progress.setTaskCount(q.id, i, d.target, q.tasks.size(), d.target);
				CompletionEngine.onTaskProgress(player, q, i, prev, d.target);
			}
		}
	}

	/** Returns the DSU's stored ItemStack (stackSize = full quantity) or null if the TE isn't a DSU. */
	private static ItemStack readStored(TileEntity te) {
		if (te == null) return null;
		if (!resolved) resolve();
		if (dsuInterface == null || getStoredItemType == null) return null;
		if (!dsuInterface.isInstance(te)) return null;
		try {
			Object r = getStoredItemType.invoke(te);
			return (r instanceof ItemStack) ? (ItemStack) r : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static synchronized void resolve() {
		if (resolved) return;
		try {
			dsuInterface = Class.forName("powercrystals.minefactoryreloaded.api.IDeepStorageUnit");
			getStoredItemType = dsuInterface.getMethod("getStoredItemType");
		} catch (Throwable t) {
			dsuInterface = null;
			getStoredItemType = null;
		}
		resolved = true;
	}
}
