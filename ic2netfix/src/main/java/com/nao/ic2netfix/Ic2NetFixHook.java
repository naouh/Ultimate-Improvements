package com.nao.ic2netfix;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.MathHelper;

/**
 * Runtime helpers invoked by the injected ASM guards.
 *
 * Kept free of IC2/obfuscation concerns. Minecraft types referenced here are remapped by
 * voldeloom at build time and are only touched at runtime (block placement / network tick),
 * long after Minecraft has finished loading, so referencing them from a coremod class is safe.
 */
public final class Ic2NetFixHook {

    private Ic2NetFixHook() {}

    /** True when the current call runs on the logical client, where a server->client network
     *  send is meaningless and crashes in IC2's NetworkManager. */
    public static boolean skipClientSend() {
        try {
            return FMLCommonHandler.instance().getEffectiveSide().isClient();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Sets the facing of a non-IC2 {@code TileEntityBlock} (e.g. AdvancedMachines' own copy)
     * placed through IC2's {@code BlockMultiID.onBlockPlacedBy}, which would otherwise
     * {@code ClassCastException} trying to cast it to {@code ic2.core.block.TileEntityBlock}.
     *
     * Replicates IC2's own facing math and calls {@code setFacing(short)} reflectively, so the
     * machine still orients toward the player. Server-side only (mirrors IC2's isSimulating
     * guard); best-effort - never throws back into placement.
     */
    public static void setForeignFacing(Object te, Object placer) {
        if (skipClientSend()) return; // server-only, like IC2.platform.isSimulating()
        if (te == null) return;
        try {
            short facing;
            if (placer == null) {
                facing = 2;
            } else {
                float yaw = ((EntityLiving) placer).rotationYaw;
                int l = MathHelper.floor_double((double) (yaw * 4.0F / 360.0F) + 0.5D) & 3;
                switch (l) {
                    case 0: facing = 2; break;
                    case 1: facing = 5; break;
                    case 2: facing = 3; break;
                    case 3: facing = 4; break;
                    default: return; // leave default facing
                }
            }
            te.getClass().getMethod("setFacing", Short.TYPE).invoke(te, Short.valueOf(facing));
        } catch (Throwable t) {
            // best effort - a wrong-facing machine beats a crashed placement
        }
    }
}
