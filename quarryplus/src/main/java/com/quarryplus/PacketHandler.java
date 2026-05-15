package com.quarryplus;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import com.quarryplus.tile.APacketTile;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Custom-payload packet dispatcher.
 *
 * <p>1.7.10 used a Netty channel pipeline plus per-class codecs. 1.4.7 has neither — every mod
 * funnels its traffic through a single {@code IPacketHandler} on the FML side and dispatches
 * by reading a leading type byte. We keep the original byte values so packet payloads can be
 * copy-adapted from the decompile.
 *
 * <p>Wire format produced by {@link APacketTile#buildPacket}:
 * {@code [type:byte] [x:int] [y:int] [z:int] [payload...]}. We use the coordinates to look up
 * the target tile and hand the {@link DataInputStream} (positioned right after the coords) to
 * {@link APacketTile#S_receivePacket} on the server or {@link APacketTile#C_receivePacket} on
 * the client.
 */
public class PacketHandler implements IPacketHandler {

    // ----- Server → Client -----
    public static final byte StC_OPEN_GUI_MAPPING = 3;
    public static final byte StC_NOW              = 4;
    public static final byte StC_HEAD_POS         = 5;
    public static final byte StC_UPDATE_MARKER    = 6;
    public static final byte StC_LINK_RES         = 7;

    // ----- Client → Server -----
    public static final byte CtS_REMOVE_FORTUNE   = 10;
    public static final byte CtS_REMOVE_SILKTOUCH = 11;
    public static final byte CtS_TOGGLE_FORTUNE   = 12;
    public static final byte CtS_TOGGLE_SILKTOUCH = 13;
    public static final byte CtS_LINK_REQ         = 14;
    public static final byte CtS_ADD_MAPPING      = 16;
    public static final byte CtS_REMOVE_MAPPING   = 17;
    public static final byte CtS_UPDATE_MAPPING   = 18;

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        if (!QuarryPlus.CHANNEL.equals(packet.channel)) return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
        try {
            byte type = in.readByte();
            int x = in.readInt();
            int y = in.readInt();
            int z = in.readInt();

            World world = resolveWorld(player);
            if (world == null) return;
            TileEntity tile = world.getBlockTileEntity(x, y, z);
            if (!(tile instanceof APacketTile)) return;
            APacketTile target = (APacketTile) tile;

            if (player instanceof EntityPlayer) {
                // Server-side dispatch: the EntityPlayer here is the sender.
                target.S_receivePacket(type, in, (EntityPlayer) player);
            } else {
                target.C_receivePacket(type, in);
            }
        } catch (IOException e) {
            // malformed payload — drop silently.
        }
    }

    /**
     * Resolve the world for the incoming packet. On the server, the {@code Player} is an
     * {@link EntityPlayer} whose {@code worldObj} is the source. On the client there is no
     * player attached to the inbound packet — fall back to the current Minecraft world.
     */
    private static World resolveWorld(Player p) {
        if (p instanceof EntityPlayer) return ((EntityPlayer) p).worldObj;
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.theWorld : null;
    }
}
