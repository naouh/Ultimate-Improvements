package com.nao.questcycle.data;

import com.nao.questcycle.task.QuestTask;
import java.util.Collections;
import java.util.List;

/** Immutable definition of a single quest, built once at config load. */
public final class QuestDef {
	public final String id;
	public final QuestSection section;
	public final String categoryId;
	public final String categoryName;
	public final String name;
	public final String desc;
	/** Item id used for the quest icon in the list view. */
	public final int iconItemId;
	public final int iconItemMeta;
	public final List<QuestTask> tasks;
	/** Quest ids that must be complete before this one becomes available; never null. */
	public final List<String> requires;
	/** Non-null only for ACHIEVEMENT quests; the title granted on completion. */
	public final TitleDef titleReward;

	public QuestDef(String id, QuestSection section, String categoryId, String categoryName,
	                String name, String desc, int iconItemId, int iconItemMeta,
	                List<QuestTask> tasks, List<String> requires, TitleDef titleReward) {
		this.id = id;
		this.section = section;
		this.categoryId = categoryId;
		this.categoryName = categoryName;
		this.name = name;
		this.desc = desc;
		this.iconItemId = iconItemId;
		this.iconItemMeta = iconItemMeta;
		this.tasks = Collections.unmodifiableList(tasks);
		this.requires = Collections.unmodifiableList(requires);
		this.titleReward = titleReward;
	}
}
