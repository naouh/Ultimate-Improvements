package com.quarryrange;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Client-only live preview of the chosen quarry area, drawn with BuildCraft's own laser entities
 * (reflectively) in the client world. Fully owned by the editor screen: it is rebuilt on every
 * change and cleared when the screen closes, so it never fights the quarry's native rendering and
 * needs no server round-trip.
 */
public final class ClientPreview {

    private ClientPreview() {}

    private static boolean inited, ok;
    private static Method mCreateLaserBox;   // Utils.createLaserBox(World, d,d,d,d,d,d, LaserKind)
    private static Object laserKind;         // LaserKind.Blue
    private static Object[] lasers;          // currently-spawned EntityBlock[]

    private static boolean init() {
        if (inited) return ok;
        inited = true;
        try {
            Class<?> utils = Class.forName("buildcraft.core.utils.Utils");
            Class<?> kindCls = Class.forName("buildcraft.api.core.LaserKind");
            mCreateLaserBox = utils.getMethod("createLaserBox", World.class,
                    double.class, double.class, double.class,
                    double.class, double.class, double.class, kindCls);
            for (Object c : kindCls.getEnumConstants()) {
                if (c.toString().equals("Blue")) { laserKind = c; break; }
            }
            if (laserKind == null) laserKind = kindCls.getEnumConstants()[0];
            ok = true;
        } catch (Throwable t) {
            System.out.println("[QuarryRange] preview lasers unavailable: " + t);
            ok = false;
        }
        return ok;
    }

    public static void show(int[] box) {
        clear();
        if (!init() || box == null || box.length < 6) return;
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return;
        try {
            Object arr = mCreateLaserBox.invoke(null, w,
                    (double) box[0], (double) box[1], (double) box[2],
                    (double) box[3], (double) box[4], (double) box[5], laserKind);
            if (arr instanceof Object[]) lasers = (Object[]) arr;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void clear() {
        if (lasers == null) return;
        for (Object o : lasers) {
            if (o instanceof Entity) ((Entity) o).setDead();
        }
        lasers = null;
    }

    /**
     * Kills any BuildCraft laser ({@code EntityBlock}) sitting in the given box that isn't part of
     * our own preview — i.e. the quarry's native box and any orphan lasers left by the client
     * re-creating it before the server's hold flags synced.
     */
    public static void sweepNative(int cx, int cy, int cz, int radius) {
        if (!init()) return;
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return;
        try {
            Class<?> ebCls = Class.forName("buildcraft.core.EntityBlock");
            double minX = cx - radius - 1, minY = cy - 2,         minZ = cz - radius - 1;
            double maxX = cx + radius + 1, maxY = cy + radius + 2, maxZ = cz + radius + 1;
            for (Object o : w.loadedEntityList.toArray()) {
                if (!ebCls.isInstance(o) || isMine(o)) continue;
                Entity e = (Entity) o;
                if (e.posX >= minX && e.posX <= maxX && e.posY >= minY && e.posY <= maxY
                        && e.posZ >= minZ && e.posZ <= maxZ) {
                    e.setDead();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isMine(Object o) {
        if (lasers == null) return false;
        for (Object l : lasers) if (l == o) return true;
        return false;
    }
}
