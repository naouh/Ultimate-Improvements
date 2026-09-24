package com.nao.mpsnaoaddons;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

/**
 * Single right-click listener that dispatches to whichever of our power-tool
 * modules is the active mode: OmniWrench (rotate/wrench), EU Reader (IC2) and
 * TE Multimeter on a block click, ME Wireless Terminal on an air click.
 *
 * <p>We do the dispatch here rather than via MPS' onRightClick because
 * MPS' RightClickPowerModule only fires for AIR clicks, and we can't subclass
 * it from a Voldeloom build anyway (its {@code onRightClick} signature uses
 * obfuscated MC types). The ItemPowerTool's onItemUseFirst is hard-coded to
 * handle the built-in "Multimeter" module only — there is no general "active
 * right-click module sees the block" hook to plug into. Both
 * {@code RIGHT_CLICK_BLOCK} and {@code RIGHT_CLICK_AIR} are fired server-side
 * in Forge 1.4.7 ({@code ItemInWorldManager} / {@code NetServerHandler}).
 *
 * <p>OmniWrench's BC/Railcraft/TE-conduit support also goes through interface
 * methods bytecode-injected into ItemPowerTool by
 * {@link com.nao.mpsnaoaddons.transform.PowerToolInterfaceTransformer}; those run
 * regardless of this listener.
 */
public class OmniWrenchEventHandler {

    @ForgeSubscribe
    public void onRightClick(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null) return;
        World world = player.worldObj;
        if (world == null || world.isRemote) return;

        Action action = event.action;
        if (action == Action.RIGHT_CLICK_BLOCK) {
            handleBlockClick(event, player, world, held);
        } else if (action == Action.RIGHT_CLICK_AIR) {
            handleAirClick(event, player, world, held);
        }
    }

    /**
     * Block-context modules: OmniWrench (rotate), EU Reader (IC2 tile),
     * TE Multimeter (TE conduits). ME Wireless is intentionally NOT in here
     * — the wireless terminal is an air-click feature, and dispatching it on
     * block right-click would steal click events from chests/levers/whatever
     * the player is actually trying to interact with.
     */
    private void handleBlockClick(PlayerInteractEvent event, EntityPlayer player,
                                  World world, ItemStack held) {
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

    /** Air-context module dispatch. Only ME Wireless cares about air clicks
     *  — AE's terminal opens the GUI from any open-air right-click. */
    private void handleAirClick(PlayerInteractEvent event, EntityPlayer player,
                                World world, ItemStack held) {
        if (MEWirelessHelper.isActiveOnPowerTool(held)) {
            if (MEWirelessHelper.handleClick(player, world, held)) {
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
