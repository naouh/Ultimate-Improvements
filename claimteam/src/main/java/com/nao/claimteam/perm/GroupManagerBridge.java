package com.nao.claimteam.perm;

import java.lang.reflect.Method;

/**
 * Reflective bridge to Essentials GroupManager on an MCPC+ hybrid server.
 *
 * Critical: on MCPC+ the Forge mod classloader cannot see Bukkit plugin
 * classes directly (they live in a per-plugin PluginClassLoader). So we
 * cannot {@code Class.forName("org.anjocaido.groupmanager.GroupManager")}
 * from here - that always throws ClassNotFoundException.
 *
 * Instead we go through Bukkit (whose API classes ARE on the parent
 * classloader and ARE visible from Forge mods):
 *   1. Bukkit.getServer().getPluginManager().getPlugin("GroupManager")
 *   2. plugin.getWorldsHolder().getWorldData(worldName) -> OverloadedWorldHolder
 *   3. worldData.getUser(username) -> User
 *   4. user.getGroupName() -> String
 *
 * If Bukkit isn't on the classpath (vanilla Forge server) or GroupManager
 * isn't enabled, everything no-ops and the caller falls back to
 * {@link com.nao.claimteam.Config} player groups.
 */
public final class GroupManagerBridge {

    private GroupManagerBridge() {}

    private static volatile boolean probed = false;
    private static volatile boolean enabled = false;

    private static Object pluginInstance;
    private static Method mGetWorldsHolder;
    private static Method mGetWorldData;
    private static Method mGetUser;
    private static Method mGetGroupName;

    private static void probe() {
        if (probed) return;
        synchronized (GroupManagerBridge.class) {
            if (probed) return;
            probed = true;
            try {
                // Reach Bukkit via the parent classloader (visible from Forge).
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                Object server = bukkit.getMethod("getServer").invoke(null);
                if (server == null) {
                    System.out.println("[ClaimTeam] GroupManager bridge: Bukkit.getServer() returned null - not on a Bukkit-hybrid server.");
                    return;
                }
                Object pm = server.getClass().getMethod("getPluginManager").invoke(server);
                if (pm == null) {
                    System.out.println("[ClaimTeam] GroupManager bridge: PluginManager unavailable.");
                    return;
                }
                pluginInstance = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "GroupManager");
                if (pluginInstance == null) {
                    System.out.println("[ClaimTeam] GroupManager bridge: GroupManager plugin not loaded.");
                    return;
                }
                // Now we have the GM plugin instance whose class IS visible to its own classloader.
                Class<?> gmClass = pluginInstance.getClass();
                mGetWorldsHolder = gmClass.getMethod("getWorldsHolder");
                Class<?> wHolder = mGetWorldsHolder.getReturnType();
                mGetWorldData = wHolder.getMethod("getWorldData", String.class);
                Class<?> wData = mGetWorldData.getReturnType();
                mGetUser = wData.getMethod("getUser", String.class);
                Class<?> userCls = mGetUser.getReturnType();
                mGetGroupName = userCls.getMethod("getGroupName");

                enabled = true;
                System.out.println("[ClaimTeam] GroupManager bridge enabled.");
            } catch (Throwable t) {
                enabled = false;
                System.out.println("[ClaimTeam] GroupManager bridge unavailable (" + t.getClass().getSimpleName() + "): " + t.getMessage());
            }
        }
    }

    public static boolean isEnabled() {
        probe();
        return enabled;
    }

    /** Returns the GroupManager group name of {@code username} in {@code worldName}, or null if unavailable. */
    public static String getGroupName(String username, String worldName) {
        if (username == null || worldName == null) return null;
        probe();
        if (!enabled) return null;
        try {
            Object holder = mGetWorldsHolder.invoke(pluginInstance);
            if (holder == null) return null;
            Object world = mGetWorldData.invoke(holder, worldName);
            if (world == null) return null;
            Object user = mGetUser.invoke(world, username);
            if (user == null) return null;
            Object name = mGetGroupName.invoke(user);
            return name == null ? null : name.toString();
        } catch (Throwable t) {
            System.out.println("[ClaimTeam] GroupManager lookup failed for " + username + "@" + worldName + ": " + t);
            return null;
        }
    }
}
