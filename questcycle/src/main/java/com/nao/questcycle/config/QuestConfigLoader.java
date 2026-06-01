package com.nao.questcycle.config;

import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.QuestSection;
import com.nao.questcycle.data.TitleDef;
import com.nao.questcycle.task.QuestTask;
import com.nao.questcycle.task.TaskFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads/writes quests.json + achievements.json, builds the QuestRegistry. */
public final class QuestConfigLoader {
	private QuestConfigLoader() {}

	/** Returns the raw text of quests.json (creating sample if missing). */
	public static String readQuestsText() throws IOException {
		File f = QuestConfigPaths.quests();
		if (!f.exists()) writeSample(f, sampleQuests());
		return readAll(f);
	}

	/** Returns the raw text of achievements.json (creating sample if missing). */
	public static String readAchievementsText() throws IOException {
		File f = QuestConfigPaths.achievements();
		if (!f.exists()) writeSample(f, sampleAchievements());
		return readAll(f);
	}

	/** Parses the given JSON strings and swaps QuestRegistry.CURRENT. Used client-side when the server pushes its config. */
	public static String loadFromStrings(String questsJson, String achievementsJson) {
		try {
			Map<String, QuestDef> all = new LinkedHashMap<String, QuestDef>();
			Map<String, TitleDef> titles = new LinkedHashMap<String, TitleDef>();
			loadFromText("quests.json", questsJson, QuestSection.PRESTIGE, all, titles);
			loadFromText("achievements.json", achievementsJson, QuestSection.ACHIEVEMENT, all, titles);
			QuestRegistry.CURRENT = new QuestRegistry(all, titles);
			return null;
		} catch (Throwable t) {
			System.err.println("[QuestCycle] failed to load pushed quest config: " + t.getMessage());
			t.printStackTrace();
			return t.getMessage();
		}
	}

	/**
	 * Loads both files (creating defaults if missing) and atomically swaps
	 * QuestRegistry.CURRENT. Returns null on success, or an error message.
	 */
	public static String loadAll() {
		try {
			File qf = QuestConfigPaths.quests();
			File af = QuestConfigPaths.achievements();
			if (!qf.exists()) writeSample(qf, sampleQuests());
			if (!af.exists()) writeSample(af, sampleAchievements());

			Map<String, QuestDef> all = new LinkedHashMap<String, QuestDef>();
			Map<String, TitleDef> titles = new LinkedHashMap<String, TitleDef>();
			loadFile(qf, QuestSection.PRESTIGE, all, titles);
			loadFile(af, QuestSection.ACHIEVEMENT, all, titles);

			QuestRegistry.CURRENT = new QuestRegistry(all, titles);
			System.out.println("[QuestCycle] loaded " + all.size() + " quest(s), " + titles.size() + " title(s)");
			return null;
		} catch (Throwable t) {
			System.err.println("[QuestCycle] failed to load quest config: " + t.getMessage());
			t.printStackTrace();
			return t.getMessage();
		}
	}

	private static void loadFile(File f, QuestSection section,
	                             Map<String, QuestDef> all, Map<String, TitleDef> titles) throws IOException {
		String src = readAll(f);
		loadFromText(f.getName(), src, section, all, titles);
	}

	private static void loadFromText(String sourceLabel, String src, QuestSection section,
	                                 Map<String, QuestDef> all, Map<String, TitleDef> titles) {
		Map<String, Object> root = Json.parseObject(src);
		List<Object> categories = Json.asList(root.get("categories"));
		if (categories == null) return;
		for (int i = 0; i < categories.size(); i++) {
			Map<String, Object> cat = Json.asMap(categories.get(i));
			String catId = Json.asString(cat.get("id"), "default");
			String catName = Json.asString(cat.get("name"), catId);
			List<Object> quests = Json.asList(cat.get("quests"));
			if (quests == null) continue;
			for (int j = 0; j < quests.size(); j++) {
				Map<String, Object> q = Json.asMap(quests.get(j));
				String id = Json.asString(q.get("id"), null);
				if (id == null || id.length() == 0) {
					System.err.println("[QuestCycle] skipping quest with missing id in " + sourceLabel);
					continue;
				}
				if (all.containsKey(id)) {
					System.err.println("[QuestCycle] duplicate quest id '" + id + "', skipping the second");
					continue;
				}
				String name = Json.asString(q.get("name"), id);
				String desc = Json.asString(q.get("desc"), "");
				int iconId = 0;
				int iconMeta = 0;
				Map<String, Object> icon = Json.asMap(q.get("icon"));
				if (icon != null) {
					iconId = Json.asInt(icon.get("id"), 0);
					iconMeta = Json.asInt(icon.get("meta"), 0);
				}
				List<QuestTask> tasks = new ArrayList<QuestTask>();
				List<Object> taskJson = Json.asList(q.get("tasks"));
				if (taskJson != null) {
					for (int k = 0; k < taskJson.size(); k++) {
						tasks.add(TaskFactory.read(Json.asMap(taskJson.get(k))));
					}
				}
				List<String> requires = new ArrayList<String>();
				List<Object> reqJson = Json.asList(q.get("requires"));
				if (reqJson != null) {
					for (int k = 0; k < reqJson.size(); k++) {
						String r = Json.asString(reqJson.get(k), null);
						if (r != null) requires.add(r);
					}
				}
				TitleDef title = null;
				if (section == QuestSection.ACHIEVEMENT) {
					Map<String, Object> titleJson = Json.asMap(q.get("title"));
					if (titleJson != null) {
						String tid = Json.asString(titleJson.get("id"), id);
						String tdisp = Json.asString(titleJson.get("display"), "[" + name + "]");
						String tshort = Json.asString(titleJson.get("short"), null);
						title = new TitleDef(tid, tdisp, tshort, id);
						titles.put(tid, title);
					}
				}
				int money = Json.asInt(q.get("money"), 0);
				if (money < 0) money = 0;
				QuestDef def = new QuestDef(id, section, catId, catName, name, desc, iconId, iconMeta,
						Collections.unmodifiableList(tasks), Collections.unmodifiableList(requires), title, money);
				all.put(id, def);
			}
		}
	}

	private static String readAll(File f) throws IOException {
		FileInputStream fis = new FileInputStream(f);
		try {
			byte[] buf = new byte[(int) f.length()];
			int read = 0;
			while (read < buf.length) {
				int n = fis.read(buf, read, buf.length - read);
				if (n < 0) break;
				read += n;
			}
			return new String(buf, 0, read, "UTF-8");
		} finally {
			try { fis.close(); } catch (IOException ignored) {}
		}
	}

	private static void writeSample(File f, String body) throws IOException {
		FileOutputStream fos = new FileOutputStream(f);
		try {
			fos.write(body.getBytes("UTF-8"));
		} finally {
			try { fos.close(); } catch (IOException ignored) {}
		}
		System.out.println("[QuestCycle] wrote sample " + f.getName());
	}

	private static String sampleQuests() {
		return "{\n" +
				"  \"version\": 1,\n" +
				"  \"categories\": [\n" +
				"    {\n" +
				"      \"id\": \"early_game\", \"name\": \"Early Game\",\n" +
				"      \"quests\": [\n" +
				"        { \"id\": \"first_pick\", \"name\": \"First Pickaxe\", \"desc\": \"Craft a wooden pickaxe.\",\n" +
				"          \"icon\": { \"id\": 270 },\n" +
				"          \"tasks\": [ { \"type\": \"craft\", \"item\": 270, \"count\": 1 } ] },\n" +
				"        { \"id\": \"stack_cobble\", \"name\": \"Cobble Hoarder\", \"desc\": \"Pick up 64 cobblestone.\",\n" +
				"          \"icon\": { \"id\": 4 }, \"requires\": [\"first_pick\"],\n" +
				"          \"tasks\": [ { \"type\": \"obtain\", \"item\": 4, \"count\": 64 } ] }\n" +
				"      ]\n" +
				"    },\n" +
				"    {\n" +
				"      \"id\": \"tools\", \"name\": \"Tools\",\n" +
				"      \"quests\": [\n" +
				"        { \"id\": \"iron_pick\", \"name\": \"Iron Pickaxe\", \"desc\": \"Craft an iron pickaxe.\",\n" +
				"          \"icon\": { \"id\": 257 },\n" +
				"          \"tasks\": [ { \"type\": \"craft\", \"item\": 257, \"count\": 1 } ] }\n" +
				"      ]\n" +
				"    }\n" +
				"  ]\n" +
				"}\n";
	}

	private static String sampleAchievements() {
		return "{\n" +
				"  \"version\": 1,\n" +
				"  \"categories\": [\n" +
				"    {\n" +
				"      \"id\": \"milestones\", \"name\": \"Milestones\",\n" +
				"      \"quests\": [\n" +
				"        { \"id\": \"diamond_age\", \"name\": \"Diamond Age\", \"desc\": \"Craft a diamond pickaxe.\",\n" +
				"          \"icon\": { \"id\": 278 },\n" +
				"          \"tasks\": [ { \"type\": \"craft\", \"item\": 278, \"count\": 1 } ],\n" +
				"          \"title\": { \"id\": \"carbonized\", \"display\": \"\\u00a7b[Carbonized]\\u00a7r\" } },\n" +
				"        { \"id\": \"wool_collector\", \"name\": \"Wool Collector\", \"desc\": \"Obtain 16 wool.\",\n" +
				"          \"icon\": { \"id\": 35 },\n" +
				"          \"tasks\": [ { \"type\": \"obtain\", \"item\": 35, \"meta\": -1, \"count\": 16 } ],\n" +
				"          \"title\": { \"id\": \"shepherd\", \"display\": \"\\u00a7e[Shepherd]\\u00a7r\" } }\n" +
				"      ]\n" +
				"    }\n" +
				"  ]\n" +
				"}\n";
	}
}
