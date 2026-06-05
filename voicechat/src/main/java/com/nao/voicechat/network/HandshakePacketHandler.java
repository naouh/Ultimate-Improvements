package com.nao.voicechat.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import com.nao.voicechat.VoiceConfig;
import com.nao.voicechat.proto.VoiceProto;
import com.nao.voicechat.server.VoiceServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Handles the "VC" custom-payload channel — both the login handshake and the audio frames, since
 * audio now travels over the Minecraft connection instead of a side UDP socket.
 *
 *   server side: the handshake out, and incoming {@link VoiceProto#CTRL_AUDIO_C2S} mic frames in.
 *   client side: dispatched to {@code ClientHandshakeReceiver} (loaded reflectively so a dedicated
 *                server never classloads anything under com.nao.voicechat.client).
 */
public class HandshakePacketHandler implements IPacketHandler {

    private static volatile Method clientHandle;

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player p) {
        if (packet == null || packet.data == null || packet.data.length < 1) return;
        if (!VoiceProto.CTRL_CHANNEL.equals(packet.channel)) return;

        byte type = packet.data[0];

        // Server side: the only thing we accept from a client is an audio frame.
        if (p instanceof EntityPlayerMP) {
            if (type == VoiceProto.CTRL_AUDIO_C2S) {
                handleClientAudio((EntityPlayerMP) p, packet.data);
            }
            return;
        }

        // Client side: handshake + inbound audio, behind a reflective boundary.
        if (FMLCommonHandler.instance().getSide().isClient()) {
            dispatchClient(packet.data);
        }
    }

    /** Parse a client→server mic frame and hand it to the relay. */
    private static void handleClientAudio(EntityPlayerMP sender, byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            in.readByte();                       // type
            int seq = in.readInt();
            int len = in.readShort() & 0xFFFF;
            if (len <= 0 || len > data.length) return;
            byte[] payload = new byte[len];
            in.readFully(payload, 0, len);
            VoiceServer.relayAudio(sender, seq, payload);
        } catch (IOException ignored) {
            // Truncated/garbage packet — drop it.
        }
    }

    private static void dispatchClient(byte[] data) {
        try {
            Method m = clientHandle;
            if (m == null) {
                Class<?> recv = Class.forName("com.nao.voicechat.client.ClientHandshakeReceiver");
                m = recv.getMethod("handle", byte[].class);
                clientHandle = m;
            }
            m.invoke(null, (Object) data);
        } catch (Throwable t) {
            System.err.println("[VoiceChat] client dispatch failed: " + t);
        }
    }

    /** Build + ship the handshake (enable + audio params) for {@code epm} on login. */
    public static void sendHandshake(EntityPlayerMP epm) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(VoiceProto.CTRL_HANDSHAKE);
            dos.writeInt(VoiceProto.SAMPLE_RATE);
            dos.writeShort(VoiceProto.FRAME_SAMPLES);
            dos.writeShort(VoiceConfig.maxRangeBlocks);
        } catch (IOException e) {
            return;
        }
        byte[] data = bos.toByteArray();

        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = VoiceProto.CTRL_CHANNEL;
        pkt.data    = data;
        pkt.length  = data.length;
        PacketDispatcher.sendPacketToPlayer(pkt, (Player) epm);

        if (VoiceConfig.verboseLogging) {
            System.out.println("[VoiceChat] sent handshake to " + epm.username);
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
        h.sampleRate   = in.readInt();
        h.frameSamples = in.readShort();
        h.maxRange     = in.readShort();
        return h;
    }

    public static final class Handshake {
        public int sampleRate;
        public int frameSamples;
        public int maxRange;
    }
}
