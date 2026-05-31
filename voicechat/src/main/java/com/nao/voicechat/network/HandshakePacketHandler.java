package com.nao.voicechat.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.nao.voicechat.VoiceConfig;
import com.nao.voicechat.proto.VoiceProto;
import com.nao.voicechat.server.VoiceServer;
import com.nao.voicechat.server.VoiceSession;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Handles the reliable "VC" custom-payload channel. The only message exchanged here is
 * the handshake the server pushes on player login.
 *
 * The client-side branch is implemented in {@code ClientHandshakeReceiver} (loaded reflectively
 * to keep server-only deployments from pulling in client classes).
 */
public class HandshakePacketHandler implements IPacketHandler {

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
        if (packet == null || packet.data == null) return;
        if (!VoiceProto.CTRL_CHANNEL.equals(packet.channel)) return;

        // Server side: we never expect a control packet *from* the client (yet).
        if (p instanceof EntityPlayerMP) return;

        // Client side: dispatch to the client receiver. Keep the dependency reflective so
        // a dedicated server doesn't try to classload anything under com.nao.voicechat.client.
        if (FMLCommonHandler.instance().getSide().isClient()) {
            dispatchClient(packet.data);
        }
    }

    private static void dispatchClient(byte[] data) {
        try {
            Class<?> recv = Class.forName("com.nao.voicechat.client.ClientHandshakeReceiver");
            recv.getMethod("handle", byte[].class).invoke(null, (Object) data);
        } catch (Throwable t) {
            System.err.println("[VoiceChat] client handshake dispatch failed: " + t);
            t.printStackTrace();
        }
    }

    /**
     * Build + ship the handshake packet for {@code epm}. Allocates a session in
     * {@link VoiceServer} as a side effect.
     */
    public static void sendHandshake(EntityPlayerMP epm) {
        VoiceSession s = VoiceServer.allocateSession(epm);
        if (s == null) return;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(VoiceProto.CTRL_HANDSHAKE);
            dos.writeUTF(VoiceConfig.publicHost == null ? "" : VoiceConfig.publicHost);
            dos.writeInt(VoiceConfig.udpPort);
            dos.writeLong(s.tokenHi);
            dos.writeLong(s.tokenLo);
            dos.writeInt(VoiceProto.SAMPLE_RATE);
            dos.writeShort(VoiceProto.FRAME_SAMPLES);
            dos.writeShort(VoiceConfig.maxRangeBlocks);
        } catch (IOException e) {
            return;
        }
        byte[] data = bos.toByteArray();

        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = VoiceProto.CTRL_CHANNEL;
        pkt.data = data;
        pkt.length = data.length;
        PacketDispatcher.sendPacketToPlayer(pkt, (Player) epm);

        if (VoiceConfig.verboseLogging) {
            System.out.println("[VoiceChat] sent handshake to " + epm.username +
                               " (port=" + VoiceConfig.udpPort + ")");
        }
    }

    /** Parse a server→client handshake payload. Used by the client receiver. */
    public static Handshake parseHandshake(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte type = in.readByte();
        if (type != VoiceProto.CTRL_HANDSHAKE) {
            throw new IOException("unexpected ctrl type " + type);
        }
        Handshake h = new Handshake();
        h.udpHost      = in.readUTF();
        h.udpPort      = in.readInt();
        h.tokenHi      = in.readLong();
        h.tokenLo      = in.readLong();
        h.sampleRate   = in.readInt();
        h.frameSamples = in.readShort();
        h.maxRange     = in.readShort();
        return h;
    }

    public static final class Handshake {
        public String udpHost;
        public int    udpPort;
        public long   tokenHi;
        public long   tokenLo;
        public int    sampleRate;
        public int    frameSamples;
        public int    maxRange;
    }
}
