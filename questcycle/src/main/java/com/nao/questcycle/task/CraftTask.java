package com.nao.questcycle.task;

import com.nao.questcycle.event.CraftCtx;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Counts items crafted from a workbench / inventory crafting grid. */
public final class CraftTask implements QuestTask {
	public static final String TYPE = "craft";

	public final int itemId;
	public final int meta;
	public final int target;

	public CraftTask(int itemId, int meta, int target) {
		this.itemId = itemId;
		this.meta = meta;
		this.target = target;
	}

	@Override public String type() { return TYPE; }
	@Override public int targetCount() { return target; }
	@Override public int targetItemId() { return itemId; }
	@Override public int targetItemMeta() { return meta; }

	@Override
	public int matches(EntityPlayerMP player, Object ctx) {
		if (!(ctx instanceof CraftCtx)) return 0;
		CraftCtx c = (CraftCtx) ctx;
		ItemStack s = c.crafted;
		if (s == null || s.itemID != itemId) return 0;
		if (meta != -1 && s.getItemDamage() != meta) return 0;
		return s.stackSize;
	}

	@Override
	public String displayName() {
		return ItemNames.lookup(itemId, meta);
	}

	@Override
	public Map<String, Object> toJson() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("type", TYPE);
		m.put("item", Long.valueOf(itemId));
		if (meta != -1) m.put("meta", Long.valueOf(meta));
		m.put("count", Long.valueOf(target));
		return m;
	}
}
