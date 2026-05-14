package com.cagecontrol;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class PacketHandler implements IPacketHandler {

    public static final byte PKT_SET_NAME = 1;

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
        if (packet == null || packet.data == null) return;
        if (!CageControl.CHANNEL.equals(packet.channel)) return;
        if (!(p instanceof EntityPlayerMP)) return;
        EntityPlayerMP epm = (EntityPlayerMP) p;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
            byte type = in.readByte();
            if (type == PKT_SET_NAME) {
                int x = in.readInt(), y = in.readInt(), z = in.readInt();
                String name = in.readUTF();
                handleSetName(epm, x, y, z, name);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void msg(EntityPlayer p, String s) {
        p.sendChatToPlayer("[CageControl] " + s);
    }

    private void handleSetName(EntityPlayerMP epm, int x, int y, int z, String name) {
        World world = epm.worldObj;
        if (world == null) return;

        if (name == null) { msg(epm, "Empty name."); return; }
        name = name.trim();
        if (name.isEmpty() || !name.matches("[A-Za-z0-9_\\-]{1,24}")) {
            msg(epm, "Invalid name (A-Z 0-9 _ -, max 24 chars).");
            return;
        }

        int cageId = ReflectSS.getCageBlockId();
        if (cageId <= 0 || world.getBlockId(x, y, z) != cageId) {
            msg(epm, "No Soul Cage at those coordinates.");
            return;
        }
        if (epm.getDistance(x + 0.5, y + 0.5, z + 0.5) > 8.0) {
            msg(epm, "Too far from the cage.");
            return;
        }

        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!ReflectSS.isSoulCage(te)) { msg(epm, "Soul Cage tile entity not found."); return; }

        ItemStack held = epm.getCurrentEquippedItem();
        Object shardItem = ReflectSS.soulShardsItem();
        if (held == null || shardItem == null || held.getItem() != shardItem) {
            msg(epm, "You are no longer holding a Soul Shard."); return;
        }

        String mobType = ReflectSS.getShardType(held);
        boolean special = ReflectSS.getShardSpecial(held);
        if (mobType == null || mobType.isEmpty()) { msg(epm, "Empty shard."); return; }

        int charges = held.getMaxDamage() - held.getItemDamage();
        int tier = (int) (Math.log(charges) / Math.log(2.0)) - 5;
        if (tier <= 0) { msg(epm, "Shard not charged enough (tier 1+ required)."); return; }

        CageRegistry reg = CageRegistry.get(world);

        if (reg.findByPos(x, y, z) != null) { msg(epm, "Cage is already registered."); return; }
        if (reg.nameTaken(epm.username, name)) {
            msg(epm, "You already have a cage named '" + name + "'."); return;
        }

        CageData d = new CageData(x, y, z, world.provider.dimensionId,
                                  epm.username, name, mobType, special, tier);
        reg.put(d);

        // Configure the TE: keep mobType set so the spawner visual stays, but block spawning
        // by setting the spawn delay to Integer.MAX_VALUE (disableSpawn).
        ReflectSS.setSignal(te, world.isBlockGettingPowered(x, y, z) || world.isBlockIndirectlyGettingPowered(x, y, z));
        // setTier must come BEFORE disableSpawn (setTier overwrites the delay field).
        ReflectSS.setTier(te, tier);
        ReflectSS.setMobType(te, mobType, special);
        ReflectSS.disableSpawn(te);
        ReflectSS.resetCount(te);

        // Sync TE to client so the spawner renderer picks up the new entity name.
        world.markBlockForUpdate(x, y, z);

        // Consume the shard the way ItemShard.onItemUse did: stackSize = 0.
        held.stackSize = 0;

        msg(epm, "Cage '" + name + "' registered. Use /shard " + name + " start to activate.");
    }
}
