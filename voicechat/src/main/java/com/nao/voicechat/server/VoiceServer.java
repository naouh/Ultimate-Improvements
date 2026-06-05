package com.nao.voicechat.server;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import com.nao.voicechat.VoiceConfig;
import com.nao.voicechat.proto.VoiceProto;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;

/**
 * Server-side voice relay. Audio rides the Minecraft connection itself (Forge custom-payload
 * channel "VC") instead of a side UDP socket, so it needs no extra firewall port — anything that
 * can reach the MC port can do voice.
 *
 * On each audio frame from a player we enumerate the online players, keep those in the same
 * dimension within {@link VoiceConfig#maxRangeBlocks}, and forward the frame to each. There are no
 * sessions or tokens: the sender is whoever the connection says it is.
 */
public final class VoiceServer {

    private VoiceServer() {}

    private static volatile boolean active;

    public static void start() {
        active = true;
        System.out.println("[VoiceChat] voice relay active — audio tunneled over MC channel \"" +
                           VoiceProto.CTRL_CHANNEL + "\" (no UDP port required)");
    }

    public static void stop() {
        active = false;
        System.out.println("[VoiceChat] voice relay stopped");
    }

    /**
     * Relay one audio frame from {@code sender} to every nearby player. Invoked from the
     * custom-payload handler on the server thread.
     */
    @SuppressWarnings("unchecked")
    public static void relayAudio(EntityPlayerMP sender, int seq, byte[] payload) {
        if (!active || sender == null || sender.worldObj == null) return;
        MinecraftServer mc = MinecraftServer.getServer();
        if (mc == null) return;

        double sx = sender.posX, sy = sender.posY, sz = sender.posZ;
        int    senderDim = sender.worldObj.provider.dimensionId;
        double rangeSq = (double) VoiceConfig.maxRangeBlocks * VoiceConfig.maxRangeBlocks;

        // Build the forwarded packet once: it's identical for every recipient.
        byte[] data = buildAudioPacket(sender.entityId, seq, payload);

        List<EntityPlayerMP> all = mc.getConfigurationManager().playerEntityList;
        for (int i = 0, n = all.size(); i < n; i++) {
            EntityPlayerMP rcv = all.get(i);
            if (rcv.entityId == sender.entityId && !VoiceConfig.selfEcho) continue;
            if (rcv.worldObj == null || rcv.worldObj.provider.dimensionId != senderDim) continue;

            double dx = rcv.posX - sx, dy = rcv.posY - sy, dz = rcv.posZ - sz;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

            Packet250CustomPayload pkt = new Packet250CustomPayload();
            pkt.channel = VoiceProto.CTRL_CHANNEL;
            pkt.data    = data;
            pkt.length  = data.length;
            PacketDispatcher.sendPacketToPlayer(pkt, (Player) rcv);
        }
    }

    private static byte[] buildAudioPacket(int senderEntityId, int seq, byte[] payload) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 + 4 + 4 + 2 + payload.length);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(VoiceProto.CTRL_AUDIO_S2C);
            dos.writeInt(senderEntityId);
            dos.writeInt(seq);
            dos.writeShort(payload.length);
            dos.write(payload, 0, payload.length);
        } catch (IOException ignored) {
            // ByteArrayOutputStream never throws; nothing to do.
        }
        return bos.toByteArray();
    }
}
