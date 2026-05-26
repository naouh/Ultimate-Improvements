package com.nao.claimteam.perm;

import java.lang.reflect.Method;

/**
 * Reflective bridge to Essentials GroupManager (org.anjocaido.groupmanager.GroupManager).
 * If the class isn't on the classpath the bridge stays in "disabled" mode and all queries
 * return null without throwing — caller must use the {@link com.nao.claimteam.Config}
 * fallback in that case.
 *
 * Resolution chain:
 *   1. GroupManager plugin instance (static getter on GroupManager — varies by version,
 *      try a couple of known shapes).
 *   2. plugin.getWorldsHolder().getWorldData(worldName) -> OverloadedWorldHolder
 *   3. worldData.getUser(username) -> User
 *   4. user.getGroupName() -> String
 *
 * All steps cached after first successful resolution. If any step changes shape across
 * GM versions, set ENABLED=false and log once.
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
                Class<?> gmClass = Class.forName("org.anjocaido.groupmanager.GroupManager");
                // Try common static accessors. GroupManager keeps a static reference to the
                // current plugin instance; the field name varies by build.
                pluginInstance = findStaticInstance(gmClass);
                if (pluginInstance == null) return;

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
            }
        }
    }

    private static Object findStaticInstance(Class<?> gmClass) {
        // GroupManager.plugin (newer) or GroupManager.instance (older) — both static fields.
        try {
            java.lang.reflect.Field f = gmClass.getDeclaredField("plugin");
            f.setAccessible(true);
            Object v = f.get(null);
            if (v != null) return v;
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Field f = gmClass.getDeclaredField("instance");
            f.setAccessible(true);
            Object v = f.get(null);
            if (v != null) return v;
        } catch (Throwable ignored) {}
        return null;
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
            enabled = false;
            return null;
        }
    }
}
