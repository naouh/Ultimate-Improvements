package com.nao.questcycle.config;

import com.nao.questcycle.QuestCycleMod;
import java.io.File;

/** Resolves the on-disk paths the mod reads + writes. */
public final class QuestConfigPaths {
	private static File configDir;        // <server>/config/questcycle/
	private static File persistDir;       // <server>/config/questcycle/persist/
	private static File worldDir;         // <world>/questcycle/
	private static File worldPlayersDir;  // <world>/questcycle/players/

	private QuestConfigPaths() {}

	/** Called once at preInit with FMLPreInitializationEvent.getModConfigurationDirectory(). */
	public static void initConfigDir(File baseConfigDir) {
		configDir = new File(baseConfigDir, QuestCycleMod.MODID);
		persistDir = new File(configDir, "persist");
		if (!configDir.exists() && !configDir.mkdirs()) {
			System.err.println("[QuestCycle] failed to create " + configDir);
		}
		if (!persistDir.exists() && !persistDir.mkdirs()) {
			System.err.println("[QuestCycle] failed to create " + persistDir);
		}
	}

	/** Called on server start; world dir is only known then. */
	public static void initWorldDir(File worldDirIn) {
		worldDir = new File(worldDirIn, QuestCycleMod.MODID);
		worldPlayersDir = new File(worldDir, "players");
		if (!worldPlayersDir.exists() && !worldPlayersDir.mkdirs()) {
			System.err.println("[QuestCycle] failed to create " + worldPlayersDir);
		}
	}

	public static File configDir() { return configDir; }
	public static File persistDir() { return persistDir; }
	public static File worldDir() { return worldDir; }
	public static File worldPlayersDir() { return worldPlayersDir; }

	public static File quests()       { return new File(configDir, "quests.json"); }
	public static File achievements() { return new File(configDir, "achievements.json"); }

	public static File persistFor(String username) { return new File(persistDir, sanitize(username) + ".json"); }
	public static File progressFor(String username) { return new File(worldPlayersDir, sanitize(username) + ".json"); }

	/** Reject anything outside [A-Za-z0-9_] to keep file IO inside the intended dir. */
	public static String sanitize(String username) {
		if (username == null) return "_";
		StringBuilder sb = new StringBuilder(username.length());
		for (int i = 0; i < username.length(); i++) {
			char c = username.charAt(i);
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
				sb.append(c);
			} else {
				sb.append('_');
			}
		}
		String out = sb.toString();
		return out.length() == 0 ? "_" : out;
	}
}
