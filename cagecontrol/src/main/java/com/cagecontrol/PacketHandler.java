package com.cagecontrol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class PacketHandler implements IPacketHandler {

    public static final byte PKT_SET_NAME       = 1; // C->S (cage naming popup)
    public static final byte PKT_OPEN_GUI       = 2; // S->C (open management GUI)
    public static final byte PKT_LIST_REQUEST   = 3; // C->S (admin flag in payload)
    public static final byte PKT_LIST_RESPONSE  = 4; // S->C (cage list)

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
        if (packet == null || packet.data == null) return;
        if (!CageControl.CHANNEL.equals(packet.channel)) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
            byte type = in.readByte();
            // Server-bound packets need an EntityPlayerMP; client-bound need to go through ClientPacketHandler.
            if (p instanceof EntityPlayerMP) {
                EntityPlayerMP epm = (EntityPlayerMP) p;
                if (type == PKT_SET_NAME) {
                    int x = in.readInt(), y = in.readInt(), z = in.readInt();
                    String name = in.readUTF();
                    handleSetName(epm, x, y, z, name);
                } else if (type == PKT_LIST_REQUEST) {
                    sendCageList(epm);
                }
            } else if (FMLCommonHandler.instance().getSide().isClient()) {
                ClientPacketHandler.handle(type, in);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Build and dispatch a snapshot of every cage the player can see:
     *   - their own cages (owned or co-owned)
     *   - PLUS every cage in the world if the caller is OP
     *
     * One packet, one allocation. The client just replaces its cached list.
     */
    public static void sendCageList(EntityPlayerMP epm) {
        World w = epm.worldObj;
        if (w == null) return;
        boolean isAdmin = MinecraftServer.getServer().getConfigurationManager()
                .areCommandsAllowed(epm.username);
        CageRegistry reg = CageRegistry.get(w);
        List<CageData> all = reg.snapshot();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_LIST_RESPONSE);
            dos.writeBoolean(isAdmin);
            // First pass: count visible entries.
            int count = 0;
            for (int i = 0; i < all.size(); i++) {
                CageData d = all.get(i);
                if (isAdmin || d.canControl(epm.username)) count++;
            }
            dos.writeInt(count);
            for (int i = 0; i < all.size(); i++) {
                CageData d = all.get(i);
                if (!(isAdmin || d.canControl(epm.username))) continue;
                dos.writeUTF(d.owner == null ? "" : d.owner);
                dos.writeUTF(d.name == null ? "" : d.name);
                dos.writeUTF(d.mobType == null ? "" : d.mobType);
                dos.writeBoolean(d.special);
                dos.writeInt(d.tier);
                dos.writeBoolean(d.active);
                dos.writeInt(d.dim);
                dos.writeInt(d.x);
                dos.writeInt(d.y);
                dos.writeInt(d.z);
                dos.writeBoolean(d.isOwner(epm.username));
                // Co-owner list.
                dos.writeInt(d.coOwners.size());
                for (String co : d.coOwners) dos.writeUTF(co);
            }
        } catch (IOException e) {
            return;
        }
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = CageControl.CHANNEL;
        pkt.data = bos.toByteArray();
        pkt.length = pkt.data.length;
        PacketDispatcher.sendPacketToPlayer(pkt, (Player) epm);
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
