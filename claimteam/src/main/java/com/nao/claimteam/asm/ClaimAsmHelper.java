package com.nao.claimteam.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import com.nao.claimteam.Config;
import com.nao.claimteam.core.ClaimQuery;

/**
 * Static helpers invoked by the ASM transformers.
 *
 * <p>All signatures use {@code java.lang.Object} for MC types so the helper's bytecode is
 * unaffected by Voldeloom's deobf-to-obf remap pass. The transformers can therefore emit a
 * single stable INVOKESTATIC descriptor (e.g. {@code (Ljava/lang/Object;)V}) regardless of
 * whether the production runtime presents classes by their obf or deobf names. Reflection
 * inside resolves the fields/methods needed.
 *
 * <p>Lookups try multiple field/method name candidates (clean, SRG, obf) so the helper works
 * in dev (deobf) and prod (obf) alike. First successful lookup is cached.
 *
 * <p>Every entry point is fail-safe: any Throwable returns "do not block".
 */
public final class ClaimAsmHelper {

    private ClaimAsmHelper() {}

    // ---- field/method handles (lazy, cached) ----
    private static volatile boolean fieldsBad;
    private static volatile Field   fExpWorld;
    private static volatile Field   fExpAffected;
    private static volatile Field   fCpX;
    private static volatile Field   fCpY;
    private static volatile Field   fCpZ;
    private static volatile Field   fWorldIsRemote;
    private static volatile Field   fWorldProvider;
    private static volatile Field   fProviderDim;
    private static volatile Field   fEntityPosX;
    private static volatile Field   fEntityPosY;
    private static volatile Field   fEntityPosZ;
    private static volatile Field   fEntityWorld;
    private static volatile Field   fPlayerUsername;

    private static Field find(Class<?> c, String... names) {
        for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            for (String n : names) {
                try {
                    Field f = cur.getDeclaredField(n);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
        }
        return null;
    }

    private static int safeInt(Field f, Object o) {
        if (f == null || o == null) return 0;
        try { return f.getInt(o); } catch (Throwable t) { return 0; }
    }

    private static Object safeGet(Field f, Object o) {
        if (f == null || o == null) return null;
        try { return f.get(o); } catch (Throwable t) { return null; }
    }

    private static int dimensionOf(Object world) {
        if (world == null) return 0;
        if (fWorldProvider == null) fWorldProvider = find(world.getClass(), "provider", "field_73011_w");
        Object prov = safeGet(fWorldProvider, world);
        if (prov == null) return 0;
        if (fProviderDim == null) fProviderDim = find(prov.getClass(), "dimensionId", "field_76574_g");
        return safeInt(fProviderDim, prov);
    }

    private static boolean isRemote(Object world) {
        if (world == null) return false;
        if (fWorldIsRemote == null) fWorldIsRemote = find(world.getClass(), "isRemote", "field_72995_K");
        try { return fWorldIsRemote != null && fWorldIsRemote.getBoolean(world); }
        catch (Throwable t) { return false; }
    }

    // ---- public ASM entry points ----

    /**
     * {@code Explosion.doExplosionB} entry hook. The explosion is passed as Object so the
     * helper's bytecode contains no MC type references.
     */
    @SuppressWarnings("unchecked")
    public static void filterExplosion(Object explosion) {
        if (explosion == null) return;
        if (!Config.enableExplosionTransformer) return;
        try {
            Class<?> c = explosion.getClass();
            if (fExpWorld == null)
                fExpWorld = find(c, "worldObj", "field_77287_j", "k");
            if (fExpAffected == null)
                fExpAffected = find(c, "affectedBlockPositions", "field_77281_g", "h");
            if (fExpWorld == null || fExpAffected == null) return;
            Object world = fExpWorld.get(explosion);
            if (world == null || isRemote(world)) return;
            int dim = dimensionOf(world);
            List list = (List) fExpAffected.get(explosion);
            if (list == null || list.isEmpty()) return;
            Iterator it = list.iterator();
            while (it.hasNext()) {
                Object cp = it.next();
                if (cp == null) continue;
                if (fCpX == null) {
                    Class<?> cc = cp.getClass();
                    fCpX = find(cc, "x", "chunkPosX", "field_76934_a", "a");
                    fCpY = find(cc, "y", "chunkPosY", "field_76935_b", "b");
                    fCpZ = find(cc, "z", "chunkPosZ", "field_76936_c", "c");
                }
                int x = safeInt(fCpX, cp);
                int y = safeInt(fCpY, cp);
                int z = safeInt(fCpZ, cp);
                if (ClaimQuery.shouldBlockExplosion(dim, x, y, z)) it.remove();
            }
        } catch (Throwable t) {
            // swallow
        }
    }

    /** {@code BlockPistonBase.tryExtend(World, int, int, int, int)} entry hook. */
    public static boolean pistonShouldBlock(Object world, int x, int y, int z, int direction) {
        if (world == null) return false;
        if (!Config.enablePistonTransformer) return false;
        if (isRemote(world)) return false;
        try {
            int dim = dimensionOf(world);
            int srcCx = x >> 4;
            int srcCz = z >> 4;
            int dxs, dzs;
            switch (direction) {
                case 2: dxs = 0;  dzs = -1; break;
                case 3: dxs = 0;  dzs = 1;  break;
                case 4: dxs = -1; dzs = 0;  break;
                case 5: dxs = 1;  dzs = 0;  break;
                default: return false;
            }
            for (int n = 1; n <= 12; n++) {
                int dstCx = (x + dxs * n) >> 4;
                int dstCz = (z + dzs * n) >> 4;
                if (dstCx == srcCx && dstCz == srcCz) continue;
                if (ClaimQuery.shouldBlockTransition(dim, srcCx, srcCz, dstCx, dstCz)) return true;
                break;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@code BlockFlowing.updateTick(World, int, int, int, Random)} entry hook. */
    public static boolean fluidShouldBlock(Object world, int x, int y, int z) {
        if (world == null) return false;
        if (!Config.enableFluidTransformer) return false;
        if (isRemote(world)) return false;
        try {
            int dim = dimensionOf(world);
            int srcCx = x >> 4;
            int srcCz = z >> 4;
            int[] dx = new int[] { -1, 1, 0, 0 };
            int[] dz = new int[] { 0, 0, -1, 1 };
            for (int i = 0; i < 4; i++) {
                int nx = (x + dx[i]) >> 4;
                int nz = (z + dz[i]) >> 4;
                if (nx == srcCx && nz == srcCz) continue;
                if (ClaimQuery.shouldBlockTransition(dim, srcCx, srcCz, nx, nz)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@code EntityPlayer.attackTargetEntityWithCurrentItem(Entity)} entry hook. */
    public static boolean pvpShouldBlock(Object attacker, Object victim) {
        if (attacker == null || victim == null) return false;
        if (!Config.enablePvpTransformer) return false;
        // Only PVP — check the victim is a player. We detect by checking for a username field.
        try {
            if (fPlayerUsername == null) {
                fPlayerUsername = find(attacker.getClass(), "username", "field_71092_bJ");
            }
            if (fPlayerUsername == null) return false;
            // If the victim doesn't have the same field, it's not a player.
            Field uF = find(victim.getClass(), "username", "field_71092_bJ");
            if (uF == null) return false;

            // attacker's world for isRemote/dim
            if (fEntityWorld == null) fEntityWorld = find(attacker.getClass(), "worldObj", "field_70170_p");
            Object aw = safeGet(fEntityWorld, attacker);
            if (aw == null || isRemote(aw)) return false;

            if (fEntityPosX == null) fEntityPosX = find(victim.getClass(), "posX", "field_70165_t");
            if (fEntityPosY == null) fEntityPosY = find(victim.getClass(), "posY", "field_70163_u");
            if (fEntityPosZ == null) fEntityPosZ = find(victim.getClass(), "posZ", "field_70161_v");

            double dx = safeDouble(fEntityPosX, victim);
            double dy = safeDouble(fEntityPosY, victim);
            double dz = safeDouble(fEntityPosZ, victim);
            int x = (int) Math.floor(dx);
            int y = (int) Math.floor(dy);
            int z = (int) Math.floor(dz);
            String name = (String) fPlayerUsername.get(attacker);
            return ClaimQuery.shouldBlockPvp(name, dimensionOf(aw), x, y, z);
        } catch (Throwable t) {
            return false;
        }
    }

    private static double safeDouble(Field f, Object o) {
        if (f == null || o == null) return 0.0;
        try { return f.getDouble(o); } catch (Throwable t) { return 0.0; }
    }
}
