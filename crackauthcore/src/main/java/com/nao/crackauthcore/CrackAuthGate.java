package com.nao.crackauthcore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared lock state between the CrackAuth Bukkit plugin and the ASM-injected packet guard.
 *
 * <p>The plugin (a different classloader) calls {@link #lock}/{@link #unlock} by reflection as players
 * join and log in; the transformed {@code NetServerHandler.handleCustomPayload} calls
 * {@link #shouldDrop} on every inbound mod packet. Both reach the same static set because this class
 * is loaded once by FML's launch classloader and the plugin's classloader resolves it through its
 * parent (it must NOT be bundled in the plugin jar, or the two would see different copies).
 *
 * <p>Everything here is fail-open: any reflection problem makes {@link #shouldDrop} return false, so a
 * mistake can only ever fail to gate a packet - it can never break the server's networking.
 */
public final class CrackAuthGate {

	private CrackAuthGate() {}

	/** Lowercase names of players who have joined but not yet logged in. */
	private static final Set<String> LOCKED =
			Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	// Cached reflective handles (resolved on first use). MC types are reached as Object, and we never
	// hard-code MC field names (Cauldron/MCPC+ uses CraftBukkit's NMS names, not MCP/SRG/obf): the
	// player object is found by TYPE (its class name contains "EntityPlayer", which holds across
	// CraftBukkit and MCP), and the name via the Bukkit entity, so this works regardless of mappings.
	private static volatile Field fPlayerField;     // NetServerHandler -> the EntityPlayer field
	private static volatile Method mGetBukkitEntity; // EntityPlayer -> CraftPlayer
	private static volatile Method mBukkitGetName;   // CraftPlayer -> String
	private static volatile Field fChannel;
	private static volatile String reflInfo = "ok";

	// --- called by the plugin (reflection) --------------------------------

	public static void lock(String name) {
		if (name != null) {
			LOCKED.add(name.toLowerCase());
			lockCalls++;
		}
	}

	public static void unlock(String name) {
		if (name != null) LOCKED.remove(name.toLowerCase());
	}

	public static void clear() {
		LOCKED.clear();
	}

	public static boolean isLocked(String name) {
		return name != null && !LOCKED.isEmpty() && LOCKED.contains(name.toLowerCase());
	}

	// Set true once the transformer has actually patched NetServerHandler. Read back by the plugin
	// (reflection) so /crackauth info can confirm the coremod loaded AND shares this same class copy.
	private static volatile boolean installed;

	public static void markInstalled() { installed = true; }

	public static boolean isInstalled() { return installed; }

	// --- called by the ASM guard ------------------------------------------

	/**
	 * @param netHandler the {@code NetServerHandler} (passed as Object so no MC type is referenced here)
	 * @param packet     the inbound {@code Packet250CustomPayload} (also as Object)
	 * @return true to drop the packet, false to let it through
	 *
	 * <p>Only <b>mod</b> channels are dropped. Minecraft's own ({@code MC|...}) and FML's system
	 * channels ({@code FML...}, {@code REGISTER}/{@code UNREGISTER}) are always let through so vanilla
	 * UIs and the pause/quit menu keep working - we only want to kill mod keybind / mod GUI traffic.
	 */
	public static boolean shouldDrop(Object netHandler, Object packet) {
		gateCalls++; // counted before any early-exit so we can tell "never called" from "no one locked"
		try {
			if (netHandler == null) return false;
			if (LOCKED.isEmpty()) return false;
			callsLockedNonEmpty++;

			String name = playerName(netHandler);
			if (name == null) {
				nameNullCount++;
				lastDecision = "name=NULL [" + reflInfo + "]; lockedNow=" + LOCKED.size();
				return false;
			}
			if (!LOCKED.contains(name.toLowerCase())) {
				lastDecision = "name='" + name + "' NOT in locked set " + LOCKED;
				return false;
			}

			String channel = channelOf(packet);
			boolean drop = channel != null && !isSystemChannel(channel);
			lastDecision = "channel=" + channel + " -> " + (drop ? "DROP" : "ALLOW") + " (" + name + ")";
			if (drop) gateDrops++;
			return drop;
		} catch (Throwable t) {
			return false; // never break networking
		}
	}

	// Diagnostics readable in-game via /crackauth info (no server-log access needed).
	private static volatile int gateDrops;
	private static volatile int gateCalls;
	private static volatile int callsLockedNonEmpty;
	private static volatile int nameNullCount;
	private static volatile int lockCalls;
	private static volatile String lastDecision = "(none yet)";

	public static int getDropCount() { return gateDrops; }

	public static String getLastDecision() { return lastDecision; }

	/** One-line diagnostic for /crackauth info (no server-log access needed). */
	public static String getDiag() {
		return "calls=" + gateCalls            // handleCustomPayload hook fired this many times (0 = wrong hook)
				+ " lockCalls=" + lockCalls    // plugin -> lock() reached us (0 = bridge/beginAuth issue)
				+ " lockedNow=" + LOCKED.size()
				+ " whileLocked=" + callsLockedNonEmpty // calls seen while someone was locked
				+ " nameNull=" + nameNullCount // reflection couldn't read player name (field mismatch)
				+ " drops=" + gateDrops
				+ " refl=" + reflInfo
				+ " last=[" + lastDecision + "]";
	}

	/** True for Minecraft's and FML's own custom-payload channels (never gated). */
	private static boolean isSystemChannel(String ch) {
		return ch.startsWith("MC|")
				|| ch.startsWith("FML")
				|| ch.equals("REGISTER")
				|| ch.equals("UNREGISTER");
	}

	private static String channelOf(Object packet) {
		if (packet == null) return null;
		if (fChannel == null) {
			fChannel = find(packet.getClass(), "channel", "field_73630_a", "a");
			if (fChannel == null) return null;
		}
		try {
			Object v = fChannel.get(packet);
			return v == null ? null : v.toString();
		} catch (Throwable t) {
			return null;
		}
	}

	/** Resolves the connecting player's name from the NetServerHandler. Runtime here is obfuscated. */
	private static String playerName(Object handler) {
		Object player = playerOf(handler);
		if (player == null) { reflInfo = "no player field on " + handler.getClass().getName(); return null; }

		// 1) Username String field directly. Obf 'bR' is EntityPlayer.username in this exact Forge
		//    build (runtime is obfuscated), plus clean/SRG names for other environments.
		Field uf = find(player.getClass(), "bR", "username", "name", "field_71092_bJ");
		if (uf != null) {
			try {
				Object n = uf.get(player);
				if (n instanceof String) { reflInfo = "ok(field " + uf.getName() + ")"; return (String) n; }
			} catch (Throwable t) {
				// fall through
			}
		}

		// 2) CraftBukkit entity name (getBukkitEntity().getName()) - mapping-agnostic where present.
		try {
			if (mGetBukkitEntity == null) mGetBukkitEntity = findZeroArg(player.getClass(), "getBukkitEntity");
			if (mGetBukkitEntity != null) {
				Object be = mGetBukkitEntity.invoke(player);
				if (be != null) {
					if (mBukkitGetName == null) mBukkitGetName = findZeroArg(be.getClass(), "getName");
					if (mBukkitGetName != null) {
						Object n = mBukkitGetName.invoke(be);
						if (n != null) { reflInfo = "ok(bukkit)"; return n.toString(); }
					}
				}
			}
		} catch (Throwable t) {
			// fall through
		}

		// 3) Name accessor methods.
		String[] methods = { "getCommandSenderName", "getEntityName", "getName" };
		for (int i = 0; i < methods.length; i++) {
			try {
				Method m = findZeroArg(player.getClass(), methods[i]);
				if (m != null) {
					Object n = m.invoke(player);
					if (n instanceof String) { reflInfo = "ok(method " + methods[i] + ")"; return (String) n; }
				}
			} catch (Throwable t) {
				// try next
			}
		}
		reflInfo = "found player " + player.getClass().getName() + " but no name accessor";
		return null;
	}

	/**
	 * Finds the EntityPlayer the handler holds. Runtime is obfuscated, so we try the obf field name
	 * first ('d' = NetServerHandler.playerEntity in this build), then any field whose value is typed
	 * EntityPlayerMP ('iq') / contains "EntityPlayer", or carries CraftBukkit's getBukkitEntity().
	 */
	private static Object playerOf(Object handler) {
		try {
			if (fPlayerField != null) {
				Object v = fPlayerField.get(handler);
				if (v != null) return v;
			}
			Field byName = find(handler.getClass(), "d", "playerEntity", "field_72574_e");
			if (byName != null) {
				Object v = byName.get(handler);
				if (v != null && findZeroArg(v.getClass(), "getBukkitEntity") != null) { fPlayerField = byName; return v; }
				if (v != null && find(v.getClass(), "bR", "username", "name", "field_71092_bJ") != null) { fPlayerField = byName; return v; }
			}
			for (Class<?> c = handler.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				Field[] fields = c.getDeclaredFields();
				for (int i = 0; i < fields.length; i++) {
					Field f = fields[i];
					if (f.getType().isPrimitive() || Modifier.isStatic(f.getModifiers())) continue;
					f.setAccessible(true);
					Object v;
					try { v = f.get(handler); } catch (Throwable t) { continue; }
					if (v == null) continue;
					String tn = f.getType().getName();
					if (tn.equals("iq") || tn.endsWith(".iq") || tn.contains("EntityPlayer")
							|| findZeroArg(v.getClass(), "getBukkitEntity") != null) {
						fPlayerField = f;
						return v;
					}
				}
			}
		} catch (Throwable t) {
			// fail-open
		}
		return null;
	}

	private static Method findZeroArg(Class<?> c, String name) {
		for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
			try {
				Method m = cur.getDeclaredMethod(name);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {
				// try superclass
			}
		}
		try {
			return c.getMethod(name); // public (incl. interface) methods
		} catch (NoSuchMethodException ignored) {
			return null;
		}
	}

	private static Field find(Class<?> c, String... names) {
		for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
			for (int i = 0; i < names.length; i++) {
				try {
					Field f = cur.getDeclaredField(names[i]);
					f.setAccessible(true);
					return f;
				} catch (NoSuchFieldException ignored) {
					// try next candidate / superclass
				}
			}
		}
		return null;
	}
}
