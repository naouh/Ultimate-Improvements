package com.nao.mpsnaoaddons;

import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Invoked from bytecode patched into
 * {@code EntityPlayer.getCurrentPlayerStrVsBlock}. Returns {@code true} when
 * the player wears an MPS helmet with the Air Stride module installed and
 * active.
 *
 * <p>We call {@code MuseItemUtils.itemHasActiveModule(ItemStack, String)} via
 * reflection because the MPS jar's compiled methods reference obfuscated MC
 * class names (e.g. {@code Lur;} for ItemStack) which Voldeloom can't reconcile
 * with its de-obfuscated compile classpath. At runtime the same method exists
 * with the correct obf signature, so reflection resolves it cleanly.
 */
public final class AirStrideHelper {

    public static final String MODULE_NAME = "Air Stride";

    private static Method mItemHasActiveModule;
    private static boolean inited = false;

    private AirStrideHelper() {}

    private static synchronized Method resolveCheck() {
        if (inited) return mItemHasActiveModule;
        inited = true;
        try {
            Class<?> util = Class.forName("net.machinemuse.api.MuseItemUtils");
            mItemHasActiveModule = util.getMethod("itemHasActiveModule",
                    ItemStack.class, String.class);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return mItemHasActiveModule;
    }

    public static boolean hasActiveModule(EntityPlayer player) {
        if (player == null) return false;
        try {
            ItemStack helmet = player.inventory.armorInventory[3];
            if (helmet == null) return false;
            Method m = resolveCheck();
            if (m == null) return false;
            Object result = m.invoke(null, helmet, MODULE_NAME);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }
}
