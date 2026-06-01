package com.cagecontrol;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
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

    /**
     * Fixes a SoulShards bug: when a cage is broken, {@code BlockCage.breakBlock} recreates the
     * dropped shard writing only the {@code mobtype}/{@code mobname} NBT keys — never
     * {@code specialmob}. So a wither-skeleton cage drops a plain skeleton shard.
     *
     * The shard {@link EntityItem} is spawned from within {@code breakBlock}, which runs before
     * the cage TileEntity is removed (see {@code Chunk.setBlockIDWithMetadata}: breakBlock then
     * removeBlockTileEntity). So the cage is still readable here: if it was the {@code special}
     * (wither) variant, we restore {@code specialmob} on the dropped shard.
     */
    @ForgeSubscribe
    public void onEntityJoin(EntityJoinWorldEvent event) {
        World world = event.world;
        if (world.isRemote) return;
        if (!(event.entity instanceof EntityItem)) return;

        ItemStack is = ((EntityItem) event.entity).getEntityItem();
        if (is == null) return;
        Object shardItem = ReflectSS.soulShardsItem();
        if (shardItem == null || is.getItem() != shardItem) return;

        // Only a bound, non-special shard can have lost its wither flag in transit.
        String type = ReflectSS.getShardType(is);
        if (type == null || type.isEmpty()) return;
        if (ReflectSS.getShardSpecial(is)) return;

        // The drop spawns at cage (x,y,z) + a [0.1,0.9] offset, so floor() recovers the cage pos.
        int x = MathHelper.floor_double(event.entity.posX);
        int y = MathHelper.floor_double(event.entity.posY);
        int z = MathHelper.floor_double(event.entity.posZ);
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!ReflectSS.isSoulCage(te)) return;
        if (!ReflectSS.getCageSpecial(te)) return;

        NBTTagCompound tag = is.getTagCompound();
        if (tag == null) { tag = new NBTTagCompound(); is.setTagCompound(tag); }
        tag.setBoolean("specialmob", true);
    }
}
