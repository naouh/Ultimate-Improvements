package com.nao.questcycle.event;

import java.lang.reflect.Method;

/**
 * Optional integration with EssentialsChat + GroupManager (Bukkit-side mods on
 * MCPC+ hybrid servers). EssentialsChat formats player chat using the
 * `{PREFIX}` placeholder which it reads from GroupManager's per-user prefix.
 *
 * Forge's ServerChatEvent doesn't reach EssentialsChat (separate event chain),
 * so we drive the title into Bukkit's view by dispatching GroupManager commands
 * through Bukkit's command map (which is SEPARATE from MC's CommandHandler).
 *
 * If Bukkit isn't on the classpath (vanilla Forge server), everything no-ops.
 */
public final class BukkitChatBridge {
	private static boolean checked;
	private static Class<?> bukkitClass;
	private static Method dispatchCommand;
	private static Object consoleSender;

	private BukkitChatBridge() {}

	public static void setPrefix(String username, String prefix) {
		if (username == null || prefix == null) return;
		// Quotes get stored literally by GroupManager's parser, so don't add them.
		// Trailing space also gets trimmed - if the user wants a visual gap between
		// title and player name they should bake it into the title's `display` string
		// (e.g. "[Carbonized]·" or "[Carbonized]§r ").
		String safe = prefix.replace("\"", "");
		// `manuaddv` doesn't overwrite a pre-existing variable - delete first to be safe.
		// `mansave` flushes GM in-memory state to disk and forces EssentialsChat's
		// cached prefix lookup to refresh.
		dispatch("manudelv " + username + " prefix");
		dispatch("manuaddv " + username + " prefix " + safe);
		dispatch("mansave");
	}

	public static void clearPrefix(String username) {
		if (username == null) return;
		dispatch("manudelv " + username + " prefix");
		dispatch("mansave");
	}

	private static synchronized boolean resolve() {
		if (checked) return bukkitClass != null;
		checked = true;
		try {
			bukkitClass = Class.forName("org.bukkit.Bukkit");
			Class<?> senderClass = Class.forName("org.bukkit.command.CommandSender");
			dispatchCommand = bukkitClass.getMethod("dispatchCommand", senderClass, String.class);
			consoleSender = bukkitClass.getMethod("getConsoleSender").invoke(null);
			if (consoleSender == null) throw new IllegalStateException("getConsoleSender returned null");
			return true;
		} catch (Throwable t) {
			System.out.println("[QuestCycle] Bukkit chat bridge unavailable (" + t.getClass().getSimpleName() + "): " + t.getMessage());
			bukkitClass = null;
			return false;
		}
	}

	private static void dispatch(String command) {
		if (!resolve()) return;
		try {
			Object result = dispatchCommand.invoke(null, consoleSender, command);
			System.out.println("[QuestCycle] Bukkit dispatch `" + command + "` -> " + result);
		} catch (Throwable t) {
			System.out.println("[QuestCycle] Bukkit dispatch failed for `" + command + "`: " + t);
		}
	}
}
