package com.nao.worldtps;

import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;

/**
 * Best-effort reflection bridge into the MCPC+/Forge {@code MinecraftServer} to read true
 * server-side timing the Bukkit API does not expose:
 *
 *   - global mean MSPT from {@code MinecraftServer.tickTimeArray} (nanoseconds per full tick)
 *   - per-dimension mean tick time from Forge's {@code worldTickTimes} map (dim id -> long[] ns)
 *
 * Field lookups are type/heuristic based (no hard-coded obfuscated names: NMS is obfuscated on
 * MCPC+ 1.4.7), and every access is wrapped so an unexpected server build just yields
 * "unavailable" rather than throwing. See [[reference_mcpc_modding]].
 */
final class ForgeTickTimes {

	private boolean serverResolved;
	private Object mcServer;
	private Field tickTimeArrayField;   // long[] length 100, nanoseconds per tick
	private Field worldTickTimesField;  // Map<Integer, long[]>

	private void resolveServer() {
		if (serverResolved) return;
		serverResolved = true;
		try {
			Object craft = Bukkit.getServer();
			mcServer = craft.getClass().getMethod("getServer").invoke(craft);
		} catch (Throwable t) {
			mcServer = null;
		}
	}

	/** Re-scans for the timing fields until both are found (the tick-times map may be empty at startup). */
	private void resolveFields() {
		resolveServer();
		if (mcServer == null || (tickTimeArrayField != null && worldTickTimesField != null)) return;
		for (Class<?> c = mcServer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (tickTimeArrayField != null && worldTickTimesField != null) return;
				Object val;
				try {
					f.setAccessible(true);
					val = f.get(mcServer);
				} catch (Throwable t) {
					continue;
				}
				if (val == null) continue;
				if (worldTickTimesField == null && val instanceof Map && firstValueIsLongArray((Map<?, ?>) val)) {
					worldTickTimesField = f;
				} else if (tickTimeArrayField == null && val instanceof long[] && looksLikeTickTimeArray((long[]) val)) {
					// Several MinecraftServer long[100] fields exist (packet counters too); the tick-time
					// array is the one whose mean is in the millions of ns (tens of ms), not a small count.
					tickTimeArrayField = f;
				}
			}
		}
	}

	boolean isAvailable() {
		resolveFields();
		return tickTimeArrayField != null || worldTickTimesField != null;
	}

	/** Global mean ms per tick, or -1 if unavailable. */
	double globalMsptMs() {
		resolveFields();
		if (tickTimeArrayField == null) return -1;
		try {
			return meanMs((long[]) tickTimeArrayField.get(mcServer));
		} catch (Throwable t) {
			return -1;
		}
	}

	/** Map of dimension id -> mean tick time in ms (sorted by dim id). Empty if unavailable. */
	Map<Integer, Double> perDimensionMeanMs() {
		resolveFields();
		Map<Integer, Double> out = new TreeMap<Integer, Double>();
		if (worldTickTimesField == null) return out;
		try {
			Object raw = worldTickTimesField.get(mcServer);
			if (!(raw instanceof Map)) return out;
			for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
				if (!(e.getKey() instanceof Integer) || !(e.getValue() instanceof long[])) continue;
				double ms = meanMs((long[]) e.getValue());
				if (ms >= 0) out.put((Integer) e.getKey(), ms);
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	private static boolean firstValueIsLongArray(Map<?, ?> m) {
		for (Object v : m.values()) return v instanceof long[];
		return false; // empty -> not a confident match yet; resolveFields() will retry later
	}

	private static boolean looksLikeTickTimeArray(long[] a) {
		if (a.length != 100) return false;
		long sum = 0;
		int n = 0;
		for (long v : a) {
			if (v > 0) { sum += v; n++; }
		}
		return n > 0 && (sum / n) > 1_000_000L; // > 1ms in ns
	}

	private static double meanMs(long[] a) {
		long sum = 0;
		int n = 0;
		for (long v : a) {
			if (v > 0) { sum += v; n++; }
		}
		if (n == 0) return -1;
		return (sum / (double) n) / 1_000_000.0;
	}
}
