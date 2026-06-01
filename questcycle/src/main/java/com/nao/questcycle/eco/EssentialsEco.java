package com.nao.questcycle.eco;

import java.lang.reflect.Method;

/**
 * Reflective bridge to the Essentials economy on an MCPC+ hybrid server, used to pay out quest
 * money rewards. Server-side only in practice; fails safe (no-op) when Essentials is absent.
 *
 * The Forge mod classloader cannot see {@code com.earth2me.essentials.api.Economy} directly (it lives
 * in Essentials' own PluginClassLoader), so we reach it through Bukkit, whose API is on the parent
 * classloader and IS visible from Forge mods. Mirrors the proven bridge from the {@code hdv} and
 * {@code claimteam} mods.
 */
public final class EssentialsEco {

	private EssentialsEco() {}

	private static volatile boolean probed = false;
	private static volatile boolean enabled = false;

	private static Method mAdd;    // void add(String, double)
	private static Method mFormat; // String format(double)

	private static void probe() {
		if (probed) return;
		synchronized (EssentialsEco.class) {
			if (probed) return;
			probed = true;
			try {
				Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
				Object server = bukkit.getMethod("getServer").invoke(null);
				if (server == null) {
					System.out.println("[QuestCycle] Economy bridge: Bukkit.getServer() is null - not a hybrid server.");
					return;
				}
				Object pm = server.getClass().getMethod("getPluginManager").invoke(server);
				Object ess = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "Essentials");
				if (ess == null) {
					System.out.println("[QuestCycle] Economy bridge: Essentials plugin not loaded.");
					return;
				}
				Class<?> eco = ess.getClass().getClassLoader().loadClass("com.earth2me.essentials.api.Economy");
				mAdd    = eco.getMethod("add", String.class, double.class);
				mFormat = eco.getMethod("format", double.class);
				enabled = true;
				System.out.println("[QuestCycle] Economy bridge enabled (Essentials).");
			} catch (Throwable t) {
				enabled = false;
				System.out.println("[QuestCycle] Economy bridge unavailable (" + t.getClass().getSimpleName() + "): " + t.getMessage());
			}
		}
	}

	public static boolean isEnabled() {
		probe();
		return enabled;
	}

	/** Pays {@code amount} to {@code name}. Returns false if economy is unavailable or the call failed. */
	public static boolean deposit(String name, double amount) {
		if (amount <= 0) return true;
		probe();
		if (!enabled) return false;
		try {
			mAdd.invoke(null, name, Double.valueOf(amount));
			return true;
		} catch (Throwable t) {
			System.out.println("[QuestCycle] deposit failed for " + name + ": " + t);
			return false;
		}
	}

	public static String format(double amount) {
		probe();
		if (enabled) {
			try {
				Object r = mFormat.invoke(null, Double.valueOf(amount));
				if (r != null) return r.toString();
			} catch (Throwable ignored) {}
		}
		return String.valueOf(amount);
	}
}
