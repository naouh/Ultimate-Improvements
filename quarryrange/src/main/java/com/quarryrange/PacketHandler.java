package com.quarryrange;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class PacketHandler implements IPacketHandler {

    public static final byte PKT_OPEN    = 1; // S->C: open editor
    public static final byte PKT_PREVIEW = 2; // C->S: live preview (stay held)
    public static final byte PKT_APPLY   = 3; // C->S: confirm (release + start when powered)
    public static final byte PKT_CANCEL  = 4; // C->S: cancel (apply default, release)

    private static final double MAX_DIST = 12.0;

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
        if (packet == null || packet.data == null) return;
        if (!QuarryRange.CHANNEL.equals(packet.channel)) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.data));
            byte type = in.readByte();
            if (p instanceof EntityPlayerMP) {
                EntityPlayerMP epm = (EntityPlayerMP) p;
                if (type == PKT_PREVIEW) {
                    int x = in.readInt(), y = in.readInt(), z = in.readInt();
                    int size = in.readInt(), anchor = in.readByte();
                    apply(epm, x, y, z, size, anchor, false, false);
                } else if (type == PKT_APPLY) {
                    int x = in.readInt(), y = in.readInt(), z = in.readInt();
                    int size = in.readInt(), anchor = in.readByte();
                    apply(epm, x, y, z, size, anchor, true, false);
                } else if (type == PKT_CANCEL) {
                    int x = in.readInt(), y = in.readInt(), z = in.readInt();
                    apply(epm, x, y, z, Config.defaultSize, QuarryArea.ANCHOR_FRONT, true, true);
                }
            } else if (FMLCommonHandler.instance().getSide().isClient()) {
                ClientPacketHandler.handle(type, in);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * @param commit  true = confirm/cancel (release the hold + reload chunks); false = live preview.
     * @param ignoreDist used for cancel-on-close, where the player may have stepped away.
     */
    private void apply(EntityPlayerMP epm, int x, int y, int z, int size, int anchor,
                       boolean commit, boolean ignoreDist) {
        World world = epm.worldObj;
        if (world == null) return;
        int dim = world.provider.dimensionId;
        if (!QuarryManager.isPending(dim, x, y, z)) return; // only editable while in edit mode

        int quarryId = ReflectQuarry.getQuarryBlockId();
        if (quarryId <= 0 || world.getBlockId(x, y, z) != quarryId) {
            QuarryManager.clearPending(dim, x, y, z);
            return;
        }
        if (!ignoreDist && epm.getDistance(x + 0.5, y + 0.5, z + 0.5) > MAX_DIST) return;

        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (!ReflectQuarry.isQuarry(te)) { QuarryManager.clearPending(dim, x, y, z); return; }

        size = Config.clamp(size);
        if (anchor < QuarryArea.ANCHOR_FRONT || anchor > QuarryArea.ANCHOR_CORNER_RIGHT) {
            anchor = QuarryArea.ANCHOR_FRONT;
        }
        int meta = world.getBlockMetadata(x, y, z);
        int[] box = QuarryArea.compute(x, y, z, meta, size, anchor);

        if (commit) {
            ReflectQuarry.setInProcess(te, false);  // let the client draw the box again
            ReflectQuarry.setAlive(te, true);       // release
            ReflectQuarry.setArea(te, box, true);   // forceChunkLoading re-shows the box + syncs
            QuarryManager.clearPending(dim, x, y, z);
        } else {
            ReflectQuarry.setAlive(te, false);      // keep held during live preview
            ReflectQuarry.setInProcess(te, true);
        }
    }

    public static void sendOpen(EntityPlayerMP epm, int x, int y, int z, int meta,
                                int size, int anchor, int min, int max) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(PKT_OPEN);
            dos.writeInt(x); dos.writeInt(y); dos.writeInt(z);
            dos.writeInt(meta);
            dos.writeInt(size);
            dos.writeByte(anchor);
            dos.writeInt(min);
            dos.writeInt(max);
        } catch (IOException e) {
            return;
        }
        send(bos, (Player) epm, true);
    }

    static void sendToServer(byte type, int x, int y, int z, int size, int anchor) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(type);
            dos.writeInt(x); dos.writeInt(y); dos.writeInt(z);
            if (type != PKT_CANCEL) {
                dos.writeInt(size);
                dos.writeByte(anchor);
            }
        } catch (IOException e) {
            return;
        }
        send(bos, null, false);
    }

    private static void send(ByteArrayOutputStream bos, Player to, boolean toClient) {
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = QuarryRange.CHANNEL;
        pkt.data = bos.toByteArray();
        pkt.length = pkt.data.length;
        if (toClient) PacketDispatcher.sendPacketToPlayer(pkt, to);
        else PacketDispatcher.sendPacketToServer(pkt);
    }
}
