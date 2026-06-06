package com.nao.questcycle.event;

import com.nao.questcycle.core.CompletionEngine;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.network.PacketBuilder;
import com.nao.questcycle.network.QuestPacketHandler;
import com.nao.questcycle.task.HaveNamedTask;
import com.nao.questcycle.task.QuestTask;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Single source of truth for "did the player gain item X?".
 *
 * Vanilla 1.4.7 fires ICraftingHandler on workbench crafts and EntityItemPickupEvent
 * on ground pickup, but ME/AE crafts (and several modded gimmicks) bypass both.
 *
 * Strategy: every N ticks, snapshot each online player's inventory as a
 * (itemId<<16 | meta) -> totalCount map, diff against the previous snapshot,
 * and dispatch synthetic CraftCtx + PickupCtx for every positive delta. Both
 * CraftTask and ObtainTask thus fire from a single, reliable source.
 *
 * Initial snapshot on login = baseline (no false positive on existing inventory).
 */
public final class InventoryDeltaTracker implements ITickHandler {
	/** Run roughly twice a second; cheap and well within human reaction time. */
	private static final int TICK_INTERVAL = 10;

	/** username -> (itemId<<16 | (meta & 0xFFFF)) -> stack count summed across inventory + hotbar */
	private static final Map<String, Map<Long, Integer>> snapshots = new HashMap<String, Map<Long, Integer>>();
	private int tickCount;

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		if (!type.contains(TickType.PLAYER)) return;
		if (++tickCount < TICK_INTERVAL) return;
		tickCount = 0;
		if (tickData == null || tickData.length == 0) return;
		Object o = tickData[0];
		if (!(o instanceof EntityPlayerMP)) return;
		EntityPlayerMP player = (EntityPlayerMP) o;
		scanPlayer(player);
	}

	private void scanPlayer(EntityPlayerMP player) {
		// Ignore until cache is loaded (PlayerLifecycleHandler does that on login).
		PlayerStateCache.Entry cacheEntry = PlayerStateCache.getIfLoaded(player.username);
		if (cacheEntry == null) return;

		Map<Long, Integer> current = snapshot(player);
		Map<Long, Integer> previous = snapshots.get(player.username);
		if (previous == null) {
			// First scan after login = baseline only, no events fired.
			snapshots.put(player.username, current);
			return;
		}

		// Diff: items where the count went UP.
		for (Map.Entry<Long, Integer> e : current.entrySet()) {
			Long key = e.getKey();
			int now = e.getValue().intValue();
			Integer prev = previous.get(key);
			int delta = now - (prev == null ? 0 : prev.intValue());
			if (delta <= 0) continue;
			int itemId = (int) (key.longValue() >>> 16);
			int meta = (int) (key.longValue() & 0xFFFFL);
			if (meta == 0xFFFF) meta = -1; // sentinel for "no meta", though we always store 0+
			if (itemId <= 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) continue;
			ItemStack synthetic = new ItemStack(Item.itemsList[itemId], delta, meta < 0 ? 0 : meta);
			// Dispatch as CraftCtx so CraftTask fires; ObtainTask uses PickupCtx so dispatch that too.
			CraftEventHandler.dispatch(player, new CraftCtx(synthetic));
			CraftEventHandler.dispatch(player, new PickupCtx(synthetic));
		}

		snapshots.put(player.username, current);

		// Absolute pass for "have_named" tasks - counter mirrors current inventory,
		// independent of pickup history (kills the drop-and-loop trick).
		scanHaveTasks(player, cacheEntry);
	}

	private void scanHaveTasks(EntityPlayerMP player, PlayerStateCache.Entry cacheEntry) {
		QuestRegistry reg = QuestRegistry.CURRENT;
		if (reg == null) return;
		for (QuestDef q : reg.all().values()) {
			for (int i = 0; i < q.tasks.size(); i++) {
				QuestTask t = q.tasks.get(i);
				if (!(t instanceof HaveNamedTask)) continue;
				HaveNamedTask have = (HaveNamedTask) t;
				int prev = cacheEntry.progress.getTaskCount(q.id, i);
				// "Have N at once" is a milestone: once the player has demonstrated it, the task
				// stays satisfied. Don't let dropping/spending the items un-complete it afterwards.
				if (prev >= have.target) continue;
				int now = have.countInInventory(player);
				if (now > have.target) now = have.target;
				if (now == prev) continue;
				cacheEntry.progress.setTaskCount(q.id, i, now, q.tasks.size(), have.target);
				if (now > prev) {
					// Crossed upward - run the full completion pipeline (toast, title grant, prestige check).
					CompletionEngine.onTaskProgress(player, q, i, prev, now);
				} else {
					// Counter went down (player dropped/used items). Push the delta so the
					// client UI reflects it, but no toast / title revoke.
					boolean done = cacheEntry.progress.isQuestComplete(q);
					PacketDispatcher.sendPacketToPlayer(
							QuestPacketHandler.wrap(PacketBuilder.progressDelta(q.id, i, now, done)),
							(Player) player);
				}
			}
		}
	}

	private static Map<Long, Integer> snapshot(EntityPlayerMP player) {
		Map<Long, Integer> out = new HashMap<Long, Integer>();
		InventoryPlayer inv = player.inventory;
		if (inv == null) return out;
		addAll(out, inv.mainInventory);
		addAll(out, inv.armorInventory);
		// Items the player has parked in a crafting grid (their own 2x2 or an open
		// workbench 3x3) leave mainInventory but are still in their possession. Count
		// them here so a put-then-take round-trip conserves the total and doesn't
		// register as a phantom obtain/craft (the put-in-grid-and-pull-back dupe).
		// The crafting *result* slot is an InventoryCraftResult, not InventoryCrafting,
		// so the preview output is correctly excluded - a real craft still produces a
		// genuine +delta on the output item in mainInventory.
		addOpenCraftingMatrices(out, player);
		return out;
	}

	@SuppressWarnings("unchecked")
	private static void addOpenCraftingMatrices(Map<Long, Integer> map, EntityPlayerMP player) {
		Container open = player.openContainer;
		if (open == null || open.inventorySlots == null) return;
		List<Slot> slots = open.inventorySlots;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (slot == null || !(slot.inventory instanceof InventoryCrafting)) continue;
			ItemStack s = slot.getStack();
			if (s == null) continue;
			long key = ((long) s.itemID << 16) | (s.getItemDamage() & 0xFFFFL);
			Integer prev = map.get(Long.valueOf(key));
			map.put(Long.valueOf(key), Integer.valueOf((prev == null ? 0 : prev.intValue()) + s.stackSize));
		}
	}

	private static void addAll(Map<Long, Integer> map, ItemStack[] arr) {
		if (arr == null) return;
		for (int i = 0; i < arr.length; i++) {
			ItemStack s = arr[i];
			if (s == null) continue;
			long key = ((long) s.itemID << 16) | (s.getItemDamage() & 0xFFFFL);
			Integer prev = map.get(Long.valueOf(key));
			map.put(Long.valueOf(key), Integer.valueOf((prev == null ? 0 : prev.intValue()) + s.stackSize));
		}
	}

	public static void onPlayerLogout(EntityPlayer player) {
		if (player == null) return;
		snapshots.remove(player.username);
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.PLAYER);
	}

	@Override
	public String getLabel() {
		return "QuestCycle.InventoryDelta";
	}
}
