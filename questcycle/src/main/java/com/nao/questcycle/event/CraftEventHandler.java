package com.nao.questcycle.event;

import com.nao.questcycle.core.CompletionEngine;
import com.nao.questcycle.data.PlayerProgress;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.task.QuestTask;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Static dispatch helper. The actual event source is InventoryDeltaTracker which
 * watches every online player's inventory and fires CraftCtx + PickupCtx for any
 * positive item delta. This covers vanilla crafting, Applied Energistics ME
 * crafting, /give, mob drops, chest pulls - everything.
 */
public final class CraftEventHandler {
	public static void dispatch(EntityPlayerMP player, Object ctx) {
		PlayerStateCache.Entry e = PlayerStateCache.getOrLoad(player.username);
		PlayerProgress prog = e.progress;
		QuestRegistry reg = QuestRegistry.CURRENT;
		boolean anyChange = false;
		for (QuestDef q : reg.all().values()) {
			if (prog.isQuestComplete(q)) continue;
			if (!prog.requiresMet(q, reg)) continue;
			for (int i = 0; i < q.tasks.size(); i++) {
				QuestTask t = q.tasks.get(i);
				int add = t.matches(player, ctx);
				if (add <= 0) continue;
				int before = prog.getTaskCount(q.id, i);
				int after = prog.incTask(q.id, i, add, q.tasks.size(), t.targetCount());
				if (after != before) {
					anyChange = true;
					CompletionEngine.onTaskProgress(player, q, i, before, after);
				}
			}
		}
		if (anyChange) {
			// Lazy save on every change is overkill; flush on logout + server stop. Just mark dirty in cache.
		}
	}
}
