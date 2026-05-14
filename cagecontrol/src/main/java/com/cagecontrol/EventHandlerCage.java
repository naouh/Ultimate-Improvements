package com.cagecontrol;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

public class EventHandlerCage {

    @ForgeSubscribe
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        World world = player.worldObj;

        // Pre-validate on both sides: only intercept if held shard + cage
        int cageId = ReflectSS.getCageBlockId();
        if (cageId <= 0) return;
        if (world.getBlockId(event.x, event.y, event.z) != cageId) return;

        ItemStack held = player.getCurrentEquippedItem();
        if (held == null) return;
        Object shardItem = ReflectSS.soulShardsItem();
        if (shardItem == null || held.getItem() != shardItem) return;

        // Only require a Soul Shard with a mob bound to it. Tier/charges validation
        // happens server-side when the user submits the name (so we can chat back errors).
        String mobType = ReflectSS.getShardType(held);
        if (mobType == null || mobType.isEmpty()) return;

        // Server-side: warn if already registered
        if (!world.isRemote) {
            TileEntity te = world.getBlockTileEntity(event.x, event.y, event.z);
            if (ReflectSS.isSoulCage(te)) {
                CageRegistry reg = CageRegistry.get(world);
                if (reg.findByPos(event.x, event.y, event.z) != null) {
                    player.sendChatToPlayer("[CageControl] This cage is already named. Use /shard <name> start|stop.");
                }
            }
        }

        // Cancel on both sides — server prevents ItemShard.onItemUse, client prevents packet send
        event.useItem  = Event.Result.DENY;
        event.useBlock = Event.Result.DENY;
        event.setCanceled(true);

        // Client opens GUI; server does nothing (proxy is a no-op on server)
        if (world.isRemote) {
            CageControl.proxy.openCageGui(event.x, event.y, event.z);
        }
    }

    // Note: Forge 1.4.7 has no BlockEvent.BreakEvent. To recover a shard from a
    // stopped cage, run `/shard <name> start` first then break the cage normally.
}
