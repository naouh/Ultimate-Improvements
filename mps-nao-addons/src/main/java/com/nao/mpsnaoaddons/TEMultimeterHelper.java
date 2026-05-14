package com.nao.mpsnaoaddons;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * TE Multimeter power module. Delegates the click straight to TE's
 * {@code ItemMultimeter.onItemUseFirst}, which prints the conduit
 * saturation / liquid flow / powered-tile request to the player's chat.
 *
 * <p>If TE isn't installed, the module registers but stays inert.
 */
public final class TEMultimeterHelper {

    public static final String MODULE_NAME = "TE Multimeter";
    public static final double ENERGY_PER_READING = 50.0;

    private static ItemStack multimeterStack;
    private static boolean inited = false;

    private TEMultimeterHelper() {}

    private static synchronized void init() {
        if (inited) return;
        inited = true;
        // GameRegistry.findItemStack(modId, name, qty) is a 1.5+ API; in 1.4.7
        // we scan Item.itemsList for an instance of TE's ItemMultimeter class.
        try {
            Class<?> cTEMulti = Class.forName("thermalexpansion.transport.item.ItemMultimeter");
            for (Item item : Item.itemsList) {
                if (item != null && cTEMulti.isInstance(item)) {
                    multimeterStack = new ItemStack(item, 1);
                    break;
                }
            }
            if (multimeterStack == null) {
                System.err.println("[TEMultimeter] ThermalExpansion multimeter instance not found in Item.itemsList");
            }
        } catch (Throwable t) {
            System.err.println("[TEMultimeter] ThermalExpansion multimeter class not on classpath; module inert");
        }
    }

    /** Exposed for the registration code so we charge the multimeter as an install cost. */
    public static ItemStack getMultimeterStack() {
        init();
        return multimeterStack;
    }

    public static boolean isActiveOnPowerTool(ItemStack stack) {
        return OmniWrenchHelper.hasActiveModule(stack, MODULE_NAME);
    }

    /**
     * Forward the right-click to TE's multimeter item. Energy is drained iff
     * the multimeter returns true (a TE conduit / powered tile was hit).
     */
    public static boolean handleClick(EntityPlayer player, World world,
                                      int x, int y, int z, int side,
                                      float hitX, float hitY, float hitZ,
                                      ItemStack stack) {
        init();
        if (multimeterStack == null) return false;
        Item item = multimeterStack.getItem();
        if (item == null) return false;
        try {
            boolean handled = item.onItemUseFirst(multimeterStack, player, world,
                    x, y, z, side, hitX, hitY, hitZ);
            if (handled) OmniWrenchHelper.drain(stack, ENERGY_PER_READING);
            return handled;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }
}
