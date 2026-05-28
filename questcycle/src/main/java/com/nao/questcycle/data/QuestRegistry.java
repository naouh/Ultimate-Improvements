package com.nao.questcycle.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory holder of the currently-loaded quest set. Atomically swapped on reload
 * by QuestConfigLoader. Read-only access via the static CURRENT reference.
 */
public final class QuestRegistry {
	public static volatile QuestRegistry CURRENT = new QuestRegistry(
			Collections.<String, QuestDef>emptyMap(), Collections.<String, TitleDef>emptyMap());

	private final Map<String, QuestDef> byId;
	private final Map<String, TitleDef> titlesById;
	private final List<QuestDef> prestige;
	private final List<QuestDef> achievements;
	private final Map<String, List<QuestDef>> byCategoryPrestige;
	private final Map<String, List<QuestDef>> byCategoryAchievement;
	private final List<String> prestigeCategoryOrder;
	private final List<String> achievementCategoryOrder;

	public QuestRegistry(Map<String, QuestDef> byId, Map<String, TitleDef> titlesById) {
		this.byId = Collections.unmodifiableMap(byId);
		this.titlesById = Collections.unmodifiableMap(titlesById);
		List<QuestDef> p = new ArrayList<QuestDef>();
		List<QuestDef> a = new ArrayList<QuestDef>();
		Map<String, List<QuestDef>> bcp = new LinkedHashMap<String, List<QuestDef>>();
		Map<String, List<QuestDef>> bca = new LinkedHashMap<String, List<QuestDef>>();
		List<String> pOrder = new ArrayList<String>();
		List<String> aOrder = new ArrayList<String>();
		for (QuestDef q : byId.values()) {
			if (q.section == QuestSection.PRESTIGE) {
				p.add(q);
				if (!bcp.containsKey(q.categoryId)) {
					bcp.put(q.categoryId, new ArrayList<QuestDef>());
					pOrder.add(q.categoryId);
				}
				bcp.get(q.categoryId).add(q);
			} else {
				a.add(q);
				if (!bca.containsKey(q.categoryId)) {
					bca.put(q.categoryId, new ArrayList<QuestDef>());
					aOrder.add(q.categoryId);
				}
				bca.get(q.categoryId).add(q);
			}
		}
		this.prestige = Collections.unmodifiableList(p);
		this.achievements = Collections.unmodifiableList(a);
		this.byCategoryPrestige = bcp;
		this.byCategoryAchievement = bca;
		this.prestigeCategoryOrder = Collections.unmodifiableList(pOrder);
		this.achievementCategoryOrder = Collections.unmodifiableList(aOrder);
	}

	public QuestDef get(String id) {
		return byId.get(id);
	}

	public Map<String, QuestDef> all() {
		return byId;
	}

	public List<QuestDef> section(QuestSection s) {
		return s == QuestSection.PRESTIGE ? prestige : achievements;
	}

	public List<String> categoriesOf(QuestSection s) {
		return s == QuestSection.PRESTIGE ? prestigeCategoryOrder : achievementCategoryOrder;
	}

	public List<QuestDef> questsInCategory(QuestSection s, String categoryId) {
		Map<String, List<QuestDef>> m = s == QuestSection.PRESTIGE ? byCategoryPrestige : byCategoryAchievement;
		List<QuestDef> l = m.get(categoryId);
		return l == null ? Collections.<QuestDef>emptyList() : l;
	}

	public String categoryName(QuestSection s, String categoryId) {
		List<QuestDef> l = questsInCategory(s, categoryId);
		return l.isEmpty() ? categoryId : l.get(0).categoryName;
	}

	public TitleDef title(String titleId) {
		return titlesById.get(titleId);
	}

	public Map<String, TitleDef> allTitles() {
		return titlesById;
	}

	public int totalPrestige() {
		return prestige.size();
	}
}
