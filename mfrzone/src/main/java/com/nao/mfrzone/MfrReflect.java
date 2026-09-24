package com.nao.mfrzone;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.tileentity.TileEntity;

/**
 * Server-side reflective access to a MineFactory Reloaded machine's harvest area (Planter,
 * Harvester and Fertilizer all keep a private {@code HarvestAreaManager _areaManager}). Ordinary
 * mod code (deobf-remapped) so it can take a {@link TileEntity} directly; it reaches into MFR by
 * reflection because MFR is not on our compile classpath. Used by the packet handler to read the
 * radius (upgrade included) and the area centre that the client's laser box is built from.
 */
public final class MfrReflect {

    private MfrReflect() {}

    private static Method mGetHarvestArea;
    private static Field  fAreaXMin, fAreaXMax, fAreaYMin, fAreaZMin, fAreaZMax;
    private static boolean areaResolved;

    /** True for the three machines whose area we manage: Planter, Harvester, Fertilizer. */
    public static boolean isMfrMachine(TileEntity te) {
        if (te == null) return false;
        String n = te.getClass().getName();
        return n.endsWith(".TileEntityPlanter")
            || n.endsWith(".TileEntityHarvester")
            || n.endsWith(".TileEntityFertilizer");
    }

    /**
     * @return {radius, centreX, centreY, centreZ} for the machine's current harvest area, or null
     *         if it can't be read. radius is the half-extent (so the grid is {@code 2*radius+1}).
     */
    public static int[] getRadiusCenter(TileEntity te) {
        if (!isMfrMachine(te)) return null;
        try {
            Field fMgr = declared(te.getClass(), "_areaManager");
            if (fMgr == null) return null;
            Object mgr = fMgr.get(te);
            if (mgr == null) return null;

            if (mGetHarvestArea == null) mGetHarvestArea = mgr.getClass().getMethod("getHarvestArea");
            Object area = mGetHarvestArea.invoke(mgr);
            if (area == null) return null;

            resolveArea(area.getClass());
            if (!areaResolved) return null;

            int xMin = fAreaXMin.getInt(area);
            int xMax = fAreaXMax.getInt(area);
            int yMin = fAreaYMin.getInt(area);
            int zMin = fAreaZMin.getInt(area);
            int zMax = fAreaZMax.getInt(area);

            int radius = (xMax - xMin) / 2;
            int cx = (xMin + xMax) / 2;
            int cz = (zMin + zMax) / 2;
            return new int[] { radius, cx, yMin, cz };
        } catch (Throwable t) {
            return null;
        }
    }

    private static void resolveArea(Class<?> areaCls) {
        if (areaResolved) return;
        try {
            fAreaXMin = areaCls.getField("xMin");
            fAreaXMax = areaCls.getField("xMax");
            fAreaYMin = areaCls.getField("yMin");
            fAreaZMin = areaCls.getField("zMin");
            fAreaZMax = areaCls.getField("zMax");
            areaResolved = true;
        } catch (Throwable t) {
            areaResolved = false;
        }
    }

    private static Field declared(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
