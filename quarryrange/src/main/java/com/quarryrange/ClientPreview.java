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
     * Kills any BuildCraft laser ({@code EntityBlock}) belonging to the quarry's own frame box that
     * isn't part of our preview — the native box and any orphan lasers left by the client
     * re-creating it before the server's hold flags synced.
     *
     * <p>The bounds come from the quarry's current box (BC centres every beam inside its block, so
     * one block of slack on each side covers them all), never from the maximum size: sweeping a
     * 64-block radius used to wipe the frames of every other quarry, filler or landmark within ~130
     * blocks, and BC only re-creates those on a chunk reload.
     */
    public static void sweepNative(World w, int qx, int qy, int qz) {
        if (!init() || w == null) return;
        try {
            Class<?> ebCls = Class.forName("buildcraft.core.EntityBlock");
            int[] b = ReflectQuarry.getBox(w.getBlockTileEntity(qx, qy, qz));
            double minX, minY, minZ, maxX, maxY, maxZ;
            if (b != null) {
                minX = b[0] - 1; minY = b[1] - 1; minZ = b[2] - 1;
                maxX = b[3] + 2; maxY = b[4] + 2; maxZ = b[5] + 2;
            } else {
                // Box not synced yet: just the quarry's immediate surroundings (BC's default 11x11
                // frame sits within 12 blocks of it).
                minX = qx - 12; minY = qy - 2; minZ = qz - 12;
                maxX = qx + 13; maxY = qy + 7; maxZ = qz + 13;
            }
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
