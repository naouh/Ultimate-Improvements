package com.nao.tfcfixes;

import java.lang.reflect.Method;

/**
 * Reflection-based check for GregTech's "TE not yet synced" state.
 *
 * GregTech's machine blocks back onto {@code gregtechmod.api.BaseMetaTileEntity}
 * which carries a {@code mMetaTileEntity} field populated on Packet132 arrival.
 * Before that arrives, {@code hasValidMetaTileEntity()} returns false and GT
 * shows the "You ran into a serious Bug" placeholder. A right-click on the
 * block during this window is what we want to swallow client-side.
 *
 * Done via reflection so the coremod has no compile-time dependency on GT and
 * still works in any pack that doesn't ship it.
 */
public final class GTCompat {

    private static volatile boolean resolved = false;
    private static volatile Class<?> baseMetaTECls;
    private static volatile Method hasValidMethod;

    private GTCompat() {}

    public static boolean isBrokenGTTile(Object te) {
        if (te == null) return false;
        if (!resolved) resolve();
        if (baseMetaTECls == null || hasValidMethod == null) return false;
        if (!baseMetaTECls.isInstance(te)) return false;
        try {
            Object r = hasValidMethod.invoke(te);
            return (r instanceof Boolean) && !((Boolean) r).booleanValue();
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved) return;
        try {
            baseMetaTECls = Class.forName("gregtechmod.api.BaseMetaTileEntity");
            hasValidMethod = baseMetaTECls.getMethod("hasValidMetaTileEntity");
        } catch (Throwable t) {
            baseMetaTECls = null;
            hasValidMethod = null;
        }
        resolved = true;
    }
}
