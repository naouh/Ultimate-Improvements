package com.nao.mpsnaoaddons;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

/**
 * Single right-click-block listener that dispatches to whichever of our
 * power-tool modules is the active mode: OmniWrench (rotate/wrench),
 * EU Reader (IC2), or TE Multimeter.
 *
 * <p>We do the dispatch here rather than via MPS' onRightClick because
 * MPS' RightClickPowerModule only fires for AIR clicks. The ItemPowerTool's
 * onItemUseFirst is hard-coded to handle the built-in "Multimeter" module
 * only — there is no general "active right-click module sees the block"
 * hook to plug into.
 *
 * <p>OmniWrench's BC/Railcraft/TE-conduit support also goes through interface
 * methods bytecode-injected into ItemPowerTool by
 * {@link com.nao.mpsnaoaddons.transform.PowerToolInterfaceTransformer}; those run
 * regardless of this listener.
 */
public class OmniWrenchEventHandler {

    @ForgeSubscribe
    public void onRightClick(PlayerInteractEvent event) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        if (player == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null) return;
        World world = player.worldObj;
        if (world == null || world.isRemote) return;

        // Order: check the cheaper / more specific modules first so a player
        // who somehow has multiple modes active gets predictable behaviour.
        // In practice only one can be active at a time (MPS' getActiveMode
        // returns a single string), so order only matters for short-circuit
        // performance.
        if (OmniWrenchHelper.isActiveOnPowerTool(held)) {
            if (OmniWrenchHelper.tryWrench(player, world,
                    event.x, event.y, event.z, event.face, held)) {
                cancelClick(event, player);
            }
            return;
        }
        if (EUReaderHelper.isActiveOnPowerTool(held)) {
            if (EUReaderHelper.handleClick(player, world,
                    event.x, event.y, event.z, held)) {
                cancelClick(event, player);
            }
            return;
        }
        if (TEMultimeterHelper.isActiveOnPowerTool(held)) {
            if (TEMultimeterHelper.handleClick(player, world,
                    event.x, event.y, event.z, event.face,
                    /* hit coords aren't provided by PlayerInteractEvent in
                     * 1.4.7; TE's multimeter doesn't actually use them, so
                     * zero is fine. */
                    0.0f, 0.0f, 0.0f, held)) {
                cancelClick(event, player);
            }
        }
    }

    private static void cancelClick(PlayerInteractEvent event, EntityPlayer player) {
        event.useBlock = Event.Result.DENY;
        event.useItem  = Event.Result.DENY;
        event.setCanceled(true);
        player.swingItem();
    }
}
