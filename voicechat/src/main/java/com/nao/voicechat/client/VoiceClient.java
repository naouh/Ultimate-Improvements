package com.nao.voicechat.client;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.nao.voicechat.proto.VoiceProto;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Client side of the voice link. Audio now rides the Minecraft connection (Forge custom-payload
 * channel "VC") rather than a separate UDP socket — so there is no extra port to open at any
 * firewall. This class owns the outbound path (mic frames → server) plus the negotiated audio
 * params; inbound frames are decoded straight into {@link AudioPlayback} by the packet handler.
 *
 * "Connected" simply means the server sent us a handshake this session and we still have a player.
 */
public final class VoiceClient {

    private VoiceClient() {}

    private static volatile boolean running;
    private static volatile int     sampleRate, frameSamples, maxRangeBlocks;
    private static volatile int     outSeq;

    /** Called when the server's handshake arrives. No socket — just arms mic capture. */
    public static synchronized void connect(int sr, int frame, int range) {
        disconnect();
        sampleRate     = sr;
        frameSamples   = frame;
        maxRangeBlocks = range;
        outSeq         = 0;
        running        = true;
        MicCapture.start(sr, frame);
    }

    public static synchronized void disconnect() {
        running = false;
        MicCapture.stop();
        AudioPlayback.shutdown();
    }

    public static boolean isConnected() {
        return running && Minecraft.getMinecraft().thePlayer != null;
    }

    public static int maxRange()     { return maxRangeBlocks; }
    public static int sampleRate()   { return sampleRate; }
    public static int frameSamples() { return frameSamples; }

    /**
     * Called by {@link MicCapture} once per encoded frame, from the mic thread. Wraps the frame in
     * a custom-payload packet and hands it to the client's send queue (the queue add is
     * synchronized, so off-thread calls are safe).
     */
    public static void sendAudio(byte[] mulawFrame, int length) {
        if (!running) return;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 + 4 + 2 + length);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(VoiceProto.CTRL_AUDIO_C2S);
            dos.writeInt(outSeq++);
            dos.writeShort(length);
            dos.write(mulawFrame, 0, length);
        } catch (IOException e) {
            return;
        }
        byte[] data = bos.toByteArray();

        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = VoiceProto.CTRL_CHANNEL;
        pkt.data    = data;
        pkt.length  = data.length;
        try {
            PacketDispatcher.sendPacketToServer(pkt);
        } catch (Throwable ignored) {
            // Mid-disconnect / no net handler — drop this frame silently.
        }
    }
}
