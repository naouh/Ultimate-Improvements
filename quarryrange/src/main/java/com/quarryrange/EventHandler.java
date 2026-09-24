package com.quarryrange;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

/**
 * Re-opens the editor when a player right-clicks an existing quarry with an empty hand. (Fresh
 * placements are caught by {@link ServerTick}'s scan, which is far more reliable than trying to
 * guess where the block landed.)
 */
public class EventHandler {

    @ForgeSubscribe
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        World world = player.worldObj;
        if (world == null || world.isRemote) return; // server drives everything

        int quarryId = ReflectQuarry.getQuarryBlockId();
        if (quarryId <= 0) return;

        ItemStack held = player.getCurrentEquippedItem();
        if (held != null) return; // empty hand only, so we never fight wrenches / placement
        if (world.getBlockId(event.x, event.y, event.z) != quarryId) return;
        if (!ReflectQuarry.isQuarry(world.getBlockTileEntity(event.x, event.y, event.z))) return;

        event.useBlock = Event.Result.DENY;
        event.setCanceled(true);
        ServerTick.openEditorFor(player, world, event.x, event.y, event.z, false);
    }
}
