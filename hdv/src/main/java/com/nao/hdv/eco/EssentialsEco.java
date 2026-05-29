package com.nao.hdv.eco;

import java.lang.reflect.Method;

/**
 * Reflective bridge to the Essentials economy on an MCPC+ hybrid server.
 *
 * The Forge mod classloader cannot see {@code com.earth2me.essentials.api.Economy} directly (it lives
 * in Essentials' own PluginClassLoader). So we reach it through Bukkit, whose API is on the parent
 * classloader and IS visible from Forge mods:
 *   1. Bukkit.getServer().getPluginManager().getPlugin("Essentials")  -> the plugin instance
 *   2. pluginInstance.getClass().getClassLoader().loadClass("com.earth2me.essentials.api.Economy")
 *   3. call the static methods getMoney/hasEnough/subtract/add/format/playerExists by reflection
 *
 * All methods key on player NAME (Minecraft 1.4.7 has no UUIDs). Failures fail safe so a transaction
 * can always be aborted without creating or destroying money.
 */
public final class EssentialsEco {

	private EssentialsEco() {}

	private static volatile boolean probed = false;
	private static volatile boolean enabled = false;

	private static Method mGetMoney;     // double getMoney(String)
	private static Method mHasEnough;    // boolean hasEnough(String, double)
	private static Method mSubtract;     // void subtract(String, double)
	private static Method mAdd;          // void add(String, double)
	private static Method mFormat;       // String format(double)
	private static Method mPlayerExists; // boolean playerExists(String)

	private static void probe() {
		if (probed) return;
		synchronized (EssentialsEco.class) {
			if (probed) return;
			probed = true;
			try {
				Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
				Object server = bukkit.getMethod("getServer").invoke(null);
				if (server == null) {
					System.out.println("[HDV] Economy bridge: Bukkit.getServer() is null - not a hybrid server.");
					return;
				}
				Object pm = server.getClass().getMethod("getPluginManager").invoke(server);
				Object ess = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "Essentials");
				if (ess == null) {
					System.out.println("[HDV] Economy bridge: Essentials plugin not loaded.");
					return;
				}
				Class<?> eco = ess.getClass().getClassLoader().loadClass("com.earth2me.essentials.api.Economy");
				mGetMoney     = eco.getMethod("getMoney", String.class);
				mHasEnough    = eco.getMethod("hasEnough", String.class, double.class);
				mSubtract     = eco.getMethod("subtract", String.class, double.class);
				mAdd          = eco.getMethod("add", String.class, double.class);
				mFormat       = eco.getMethod("format", double.class);
				mPlayerExists = eco.getMethod("playerExists", String.class);
				enabled = true;
				System.out.println("[HDV] Economy bridge enabled (Essentials).");
			} catch (Throwable t) {
				enabled = false;
				System.out.println("[HDV] Economy bridge unavailable (" + t.getClass().getSimpleName() + "): " + t.getMessage());
			}
		}
	}

	public static boolean isEnabled() {
		probe();
		return enabled;
	}

	public static double balance(String name) {
		probe();
		if (!enabled) return 0.0;
		try {
			Object r = mGetMoney.invoke(null, name);
			return r == null ? 0.0 : ((Number) r).doubleValue();
		} catch (Throwable t) {
			return 0.0;
		}
	}

	public static boolean has(String name, double amount) {
		if (amount <= 0) return true;
		probe();
		if (!enabled) return false;
		try {
			Object r = mHasEnough.invoke(null, name, Double.valueOf(amount));
			return r instanceof Boolean && (Boolean) r;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Removes money. Returns false (and changes nothing) if not affordable or the call failed. */
	public static boolean withdraw(String name, double amount) {
		if (amount <= 0) return true;
		probe();
		if (!enabled) return false;
		try {
			Object ok = mHasEnough.invoke(null, name, Double.valueOf(amount));
			if (!(ok instanceof Boolean) || !((Boolean) ok)) return false;
			mSubtract.invoke(null, name, Double.valueOf(amount));
			return true;
		} catch (Throwable t) {
			System.out.println("[HDV] withdraw failed for " + name + ": " + t);
			return false;
		}
	}

	public static boolean deposit(String name, double amount) {
		if (amount <= 0) return true;
		probe();
		if (!enabled) return false;
		try {
			mAdd.invoke(null, name, Double.valueOf(amount));
			return true;
		} catch (Throwable t) {
			System.out.println("[HDV] deposit failed for " + name + ": " + t);
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
