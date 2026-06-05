package com.nao.mpsnaoaddons;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Backward-compat cleanup for the ModularPowersuits {@code mmmpsmod} stacking bug (see
 * {@link MuseTagGuard}). The {@code getMuseItemTag} guard stops NEW stamps and heals items as MPS
 * touches them, but items that were already stamped and now just sit in a player's inventory are
 * never re-checked by MPS - so they'd stay un-stackable. This server-side PLAYER-tick sweep walks
 * every online player's inventory (and the container they currently have open, e.g. a chest) and
 * strips the leftover stamp, so old items self-heal within a couple seconds of being near a player.
 *
 * <p>Cheap: per slot it's a {@code hasTagCompound()} short-circuit, so most slots cost nothing. The
 * gate keeps it to ~once a second per player.
 */
public final class MmmpsmodCleanupHandler implements ITickHandler {

    /** ~1s between sweeps of a given player (PLAYER tick fires ~20x/s). */
    private static final int INTERVAL = 20;

    private int tickCount;

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        if (!type.contains(TickType.PLAYER)) return;
        if (++tickCount < INTERVAL) return;
        tickCount = 0;
        if (tickData == null || tickData.length == 0 || !(tickData[0] instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) tickData[0];
        try {
            sweep(player.inventory.mainInventory);
            sweep(player.inventory.armorInventory);
            Container open = player.openContainer;
            if (open != null && open.inventorySlots != null) {
                for (Object o : open.inventorySlots) {
                    if (o instanceof Slot) {
                        MuseTagGuard.cleanIfStamped(((Slot) o).getStack());
                    }
                }
            }
        } catch (Throwable ignored) {
            // never let cleanup break the player tick
        }
    }

    private static void sweep(ItemStack[] arr) {
        if (arr == null) return;
        for (int i = 0; i < arr.length; i++) {
            MuseTagGuard.cleanIfStamped(arr[i]);
        }
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.PLAYER);
    }

    @Override
    public String getLabel() {
        return "MpsNaoAddons.MmmpsmodCleanup";
    }
}
