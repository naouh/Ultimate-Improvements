package com.quarryplus.tile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.quarryplus.QuarryPlus;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;

/**
 * Abstract parent for tiles that exchange custom-payload packets with the other side.
 *
 * <p>1.7.10's APacketTile uses Netty channels and FMLProxyPacket. 1.4.7 doesn't have either —
 * we route through the vanilla {@link Packet250CustomPayload} pipe and dispatch by a leading
 * type byte. The wire format is {@code [type:byte] [x:int] [y:int] [z:int] [payload...]} so
 * {@link com.quarryplus.PacketHandler} can locate the target tile and call
 * {@link #S_receivePacket} or {@link #C_receivePacket} on it.
 *
 * <p>Initial chunk-load synchronisation goes through the standard {@link #getDescriptionPacket}
 * mechanism, which serialises the tile's NBT — subclasses get this for free as long as their
 * {@link #writeToNBT} / {@link #readFromNBT} pair is correct.
 */
public abstract class APacketTile extends TileEntity {

    /** Server-side: handle a packet that arrived from a player. */
    public abstract void S_receivePacket(byte type, DataInputStream in, EntityPlayer sender) throws IOException;

    /** Client-side: handle a packet that the server pushed to us. */
    public abstract void C_receivePacket(byte type, DataInputStream in) throws IOException;

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        return new Packet132TileEntityData(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(INetworkManager net, Packet132TileEntityData pkt) {
        this.readFromNBT(pkt.customParam1);
    }

    /**
     * Build a {@link Packet250CustomPayload} carrying {@code [type, x, y, z, payload]}.
     * Use the static helpers below ({@link #sendToServer} / {@link #sendToAround} /
     * {@link #sendToPlayer}) rather than calling this directly unless you need the packet
     * object itself.
     */
    public Packet250CustomPayload buildPacket(byte type, byte[] payload) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(type);
            out.writeInt(xCoord);
            out.writeInt(yCoord);
            out.writeInt(zCoord);
            if (payload != null) out.write(payload);
            Packet250CustomPayload pkt = new Packet250CustomPayload();
            pkt.channel = QuarryPlus.CHANNEL;
            pkt.data = bos.toByteArray();
            pkt.length = pkt.data.length;
            return pkt;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendToServer(byte type, byte[] payload) {
        PacketDispatcher.sendPacketToServer(buildPacket(type, payload));
    }

    public void sendToAround(byte type, byte[] payload) {
        // 64-block radius matches the vanilla "TileEntity is loaded for this player" range.
        PacketDispatcher.sendPacketToAllAround(xCoord, yCoord, zCoord, 64,
                worldObj.provider.dimensionId, buildPacket(type, payload));
    }

    public void sendToPlayer(byte type, byte[] payload, EntityPlayer p) {
        if (p instanceof EntityPlayerMP) {
            PacketDispatcher.sendPacketToPlayer(buildPacket(type, payload), (Player) p);
        }
    }

    /** Convenience: open a writer over a fresh byte array. Caller closes via toByteArray. */
    protected static DataOutputStream newWriter(ByteArrayOutputStream bos) {
        return new DataOutputStream(bos);
    }

    protected static DataInputStream reader(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
}
