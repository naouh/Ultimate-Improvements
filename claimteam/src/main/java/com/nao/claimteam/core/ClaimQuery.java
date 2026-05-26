package com.nao.claimteam.core;

/**
 * Static query interface for ASM-patched bytecode.
 *
 * <p>This class must:
 * <ul>
 *   <li>Live in a package that is safe to load very early (no MC class references at class-init).</li>
 *   <li>Stay null-safe before the mod has fully initialized — bytecode will call these methods
 *       during the first explosion / fluid tick / piston push and we cannot guarantee that
 *       has happened after {@code @Init}.</li>
 * </ul>
 *
 * The actual lookup is delegated to a {@link Provider} set by {@code ClaimTeamMod} at @Init.
 * Before that, every query returns "do not block" so the world behaves like vanilla.
 */
public final class ClaimQuery {

    private ClaimQuery() {}

    public interface Provider {
        boolean shouldBlockExplosion(int dim, int x, int y, int z);
        boolean shouldBlockTransition(int dim, int srcCx, int srcCz, int dstCx, int dstCz);
        boolean shouldBlockPvp(String attacker, int dim, int x, int y, int z);
    }

    private static volatile Provider provider;

    public static void setProvider(Provider p) {
        provider = p;
    }

    public static boolean shouldBlockExplosion(int dim, int x, int y, int z) {
        Provider p = provider;
        if (p == null) return false;
        try { return p.shouldBlockExplosion(dim, x, y, z); }
        catch (Throwable t) { return false; }
    }

    public static boolean shouldBlockTransition(int dim, int srcCx, int srcCz, int dstCx, int dstCz) {
        Provider p = provider;
        if (p == null) return false;
        try { return p.shouldBlockTransition(dim, srcCx, srcCz, dstCx, dstCz); }
        catch (Throwable t) { return false; }
    }

    public static boolean shouldBlockPvp(String attacker, int dim, int x, int y, int z) {
        Provider p = provider;
        if (p == null) return false;
        try { return p.shouldBlockPvp(attacker, dim, x, y, z); }
        catch (Throwable t) { return false; }
    }
}
