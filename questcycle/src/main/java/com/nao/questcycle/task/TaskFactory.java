package com.nao.questcycle.task;

import com.nao.questcycle.config.Json;
import java.util.Map;

/**
 * Central dispatch from the JSON "type" string to a concrete QuestTask.
 * Extension point: add `mine`/`kill`/`place` here as more QuestTask types ship.
 */
public final class TaskFactory {
	private TaskFactory() {}

	public static QuestTask read(Map<String, Object> json) {
		String type = Json.asString(json.get("type"), "");
		if (CraftTask.TYPE.equals(type)) {
			int item = Json.asInt(json.get("item"), -1);
			int meta = Json.asInt(json.get("meta"), -1);
			int count = Json.asInt(json.get("count"), 1);
			if (item < 0) throw new Json.JsonException("craft task missing 'item' id");
			if (count <= 0) throw new Json.JsonException("craft task 'count' must be > 0");
			return new CraftTask(item, meta, count);
		}
		if (ObtainTask.TYPE.equals(type)) {
			int item = Json.asInt(json.get("item"), -1);
			int meta = Json.asInt(json.get("meta"), -1);
			int count = Json.asInt(json.get("count"), 1);
			if (item < 0) throw new Json.JsonException("obtain task missing 'item' id");
			if (count <= 0) throw new Json.JsonException("obtain task 'count' must be > 0");
			return new ObtainTask(item, meta, count);
		}
		if (NamedMatchTask.TYPE_CRAFT.equals(type) || NamedMatchTask.TYPE_OBTAIN.equals(type)) {
			String name = Json.asString(json.get("name"), null);
			if (name == null || name.length() == 0) throw new Json.JsonException(type + " task missing 'name'");
			boolean substring = Json.asBool(json.get("substring"), false);
			int count = Json.asInt(json.get("count"), 1);
			if (count <= 0) throw new Json.JsonException(type + " task 'count' must be > 0");
			int iconId = 0;
			int iconMeta = 0;
			Map<String, Object> icon = Json.asMap(json.get("icon"));
			if (icon != null) {
				iconId = Json.asInt(icon.get("id"), 0);
				iconMeta = Json.asInt(icon.get("meta"), 0);
			}
			return new NamedMatchTask(type, name, substring, count, iconId, iconMeta);
		}
		if (HaveNamedTask.TYPE.equals(type)) {
			String name = Json.asString(json.get("name"), null);
			if (name == null || name.length() == 0) throw new Json.JsonException(type + " task missing 'name'");
			boolean substring = Json.asBool(json.get("substring"), false);
			int count = Json.asInt(json.get("count"), 1);
			if (count <= 0) throw new Json.JsonException(type + " task 'count' must be > 0");
			int iconId = 0;
			int iconMeta = 0;
			Map<String, Object> icon = Json.asMap(json.get("icon"));
			if (icon != null) {
				iconId = Json.asInt(icon.get("id"), 0);
				iconMeta = Json.asInt(icon.get("meta"), 0);
			}
			return new HaveNamedTask(name, substring, count, iconId, iconMeta);
		}
		throw new Json.JsonException("Unknown task type: '" + type + "'");
	}
}
