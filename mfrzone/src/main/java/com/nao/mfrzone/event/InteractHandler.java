package com.nao.mfrzone.event;

import com.nao.mfrzone.MfrReflect;
import com.nao.mfrzone.network.PacketHandler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

/**
 * Shows a machine's working area on <b>sneak + left-click with an empty hand</b> on a Planter /
 * Harvester / Fertilizer.
 *
 * <p>In 1.4.7 the {@code LEFT_CLICK_BLOCK} interaction event is only fired server-side (from
 * {@code ItemInWorldManager.onBlockClicked}, at the very start of a dig). Cancelling it there
 * aborts the dig and re-syncs the block to the client — so the machine isn't broken, including the
 * instant break a creative left-click would otherwise cause. The server already knows the radius
 * (the upgrade is server-side), so it pushes the box straight to the player.
 *
 * <p>A normal left-click (no sneak, or with a tool) still digs; a normal right-click still opens
 * MFR's own inventory GUI.
 */
public class InteractHandler {

    @ForgeSubscribe
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != Action.LEFT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        if (!(player instanceof EntityPlayerMP)) return;
        World world = player.worldObj;
        if (world == null || world.isRemote) return; // server-authoritative
        if (!player.isSneaking()) return;
        if (player.getCurrentEquippedItem() != null) return; // empty hand only

        TileEntity te = world.getBlockTileEntity(event.x, event.y, event.z);
        if (!MfrReflect.isMfrMachine(te)) return;

        event.setCanceled(true); // abort the dig + re-sync the block to the client
        PacketHandler.showFor((EntityPlayerMP) player, event.x, event.y, event.z);
    }
}
