package com.nao.mfrzone.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.nao.mfrzone.Config;
import com.nao.mfrzone.MFRZoneMod;
import com.nao.mfrzone.MfrReflect;
import com.nao.mfrzone.client.ClientPacketHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * One-way handler: the server computes a machine's working-area box (the radius comes from the
 * upgrade slot, which only exists server-side) and pushes it to the client, which lights it up
 * with BuildCraft lasers for a few seconds.
 */
public class PacketHandler implements IPacketHandler {

    public static final byte PKT_SHOW = 1; // S->C: {x1,y1,z1,x2,y2,z2}

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
        if (packet == null || packet.data == null) return;
        if (!MFRZoneMod.CHANNEL.equals(packet.channel)) return;
        // Only the client consumes packets here (EntityPlayerMP = server side, nothing to read).
        if (p instanceof EntityPlayerMP) return;
        if (!FMLCommonHandler.instance().getSide().isClient()) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
            ClientPacketHandler.handle(in.readByte(), in);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Server-side: work out the box for the machine at x,y,z and show it to this player. */
    public static void showFor(EntityPlayerMP epm, int x, int y, int z) {
        World w = epm.worldObj;
        if (w == null) return;
        if (epm.getDistance(x + 0.5, y + 0.5, z + 0.5) > Config.maxDistance) return;
        TileEntity te = w.getBlockTileEntity(x, y, z);
        int[] rc = MfrReflect.getRadiusCenter(te); // {radius, cx, cy, cz}
        if (rc == null) return;
        int radius = rc[0];
        if (radius < 0) radius = 0;
        if (radius > Config.maxRadius) radius = Config.maxRadius;
        int cx = rc[1], cy = rc[2], cz = rc[3];
        // Inclusive block AABB of the (2r+1)^2 footprint. Vertically it spans from the machine block
        // to the working plane so the box hugs the machine whichever way it faces. The client turns
        // these block coords into the laser wireframe (expanding by half a block per side).
        int yLo = Math.min(y, cy);
        int yHi = Math.max(y, cy);
        sendShow(epm, cx - radius, yLo, cz - radius, cx + radius, yHi, cz + radius);
    }

    private static void sendShow(EntityPlayerMP epm, int x1, int y1, int z1, int x2, int y2, int z2) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_SHOW);
            dos.writeInt(x1); dos.writeInt(y1); dos.writeInt(z1);
            dos.writeInt(x2); dos.writeInt(y2); dos.writeInt(z2);
        } catch (IOException e) {
            return;
        }
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = MFRZoneMod.CHANNEL;
        pkt.data = bos.toByteArray();
        pkt.length = pkt.data.length;
        PacketDispatcher.sendPacketToPlayer(pkt, (Player) epm);
    }
}
