package com.nao.questcycle.task;

import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * One progress requirement inside a quest. Tasks are loaded from JSON via
 * TaskFactory and matched against player events (craft / pickup / future mine,
 * kill, place) by the central event handlers.
 *
 * Implementations are immutable - the per-player counter is held by
 * PlayerProgress, not by the task itself.
 */
public interface QuestTask {
	/** Discriminator written to JSON; must be unique across QuestTask implementations. */
	String type();

	/** Target value the counter needs to reach for this task to be complete. */
	int targetCount();

	/** Item id of the target (for GUI rendering). Negative if the task isn't item-based. */
	int targetItemId();

	/** Item meta of the target. -1 = any meta (used in matches() too). */
	int targetItemMeta();

	/**
	 * Returns how many counts this event should add (0 if it doesn't match).
	 * Called by the relevant event handler with whatever ctx that handler chose
	 * to pass. Implementations check their own type and the ctx shape.
	 */
	int matches(EntityPlayerMP player, Object ctx);

	/** Inverse of TaskFactory.read. */
	Map<String, Object> toJson();

	/** Human-readable label shown in the GUI detail panel beside the progress bar. */
	String displayName();
}
