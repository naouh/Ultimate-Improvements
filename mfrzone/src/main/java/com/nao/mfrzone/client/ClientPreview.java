package com.nao.mfrzone.client;

import java.lang.reflect.Method;

import com.nao.mfrzone.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Client-only laser-box preview of a machine's working area, drawn with BuildCraft's own laser
 * entities (reflectively, so we need no BuildCraft compile dependency). The box is spawned on
 * request and cleared automatically after a few seconds by {@link #tick}.
 */
public final class ClientPreview {

    private ClientPreview() {}

    private static boolean inited, ok;
    private static Method mCreateLaserBox; // Utils.createLaserBox(World, d,d,d,d,d,d, LaserKind)
    private static Object laserKind;       // LaserKind.<configured>
    private static Object[] lasers;        // currently-spawned EntityBlock[]
    private static int ticksLeft;

    private static boolean init() {
        if (inited) return ok;
        inited = true;
        try {
            Class<?> utils = Class.forName("buildcraft.core.utils.Utils");
            Class<?> kindCls = Class.forName("buildcraft.api.core.LaserKind");
            mCreateLaserBox = utils.getMethod("createLaserBox", World.class,
                    double.class, double.class, double.class,
                    double.class, double.class, double.class, kindCls);
            Object[] kinds = kindCls.getEnumConstants();
            for (Object c : kinds) {
                if (c.toString().equalsIgnoreCase(Config.laserColor)) { laserKind = c; break; }
            }
            if (laserKind == null && kinds.length > 0) laserKind = kinds[0];
            ok = laserKind != null;
        } catch (Throwable t) {
            System.out.println("[MFRZone] BuildCraft laser preview unavailable: " + t);
            ok = false;
        }
        return ok;
    }

    /**
     * Spawn the box for {@code durationTicks}, replacing any current preview. {@code box} is the
     * inclusive block AABB {x1,y1,z1,x2,y2,z2}; we expand it by half a block per side so the laser
     * wireframe (whose beams sit at block centres, +0.5) traces the outer faces of those blocks.
     */
    public static void show(int[] box, int durationTicks) {
        clear();
        if (!init() || box == null || box.length < 6) return;
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return;
        try {
            Object arr = mCreateLaserBox.invoke(null, w,
                    box[0] - 0.5D, box[1] - 0.5D, box[2] - 0.5D,
                    box[3] + 0.5D, box[4] + 0.5D, box[5] + 0.5D, laserKind);
            if (arr instanceof Object[]) lasers = (Object[]) arr;
            ticksLeft = durationTicks;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Called every client tick: expire the preview when its timer runs out. */
    public static void tick() {
        if (lasers == null) return;
        if (--ticksLeft <= 0) clear();
    }

    public static void clear() {
        if (lasers == null) return;
        for (Object o : lasers) {
            if (o instanceof Entity) ((Entity) o).setDead();
        }
        lasers = null;
        ticksLeft = 0;
    }
}
