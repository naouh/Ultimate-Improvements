package com.nao.voicechat.client;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import com.nao.voicechat.network.HandshakePacketHandler;
import com.nao.voicechat.network.HandshakePacketHandler.Handshake;
import com.nao.voicechat.proto.VoiceProto;

/**
 * Client-side entry point for the "VC" channel — called reflectively by
 * {@link HandshakePacketHandler} so the dedicated server never classloads it. Keep the signature
 * exactly {@code public static void handle(byte[])}.
 *
 * Two message types arrive here: the login handshake (arms mic capture) and forwarded audio
 * frames (decoded straight into {@link AudioPlayback}).
 */
public final class ClientHandshakeReceiver {

    private ClientHandshakeReceiver() {}

    public static void handle(byte[] data) {
        if (data == null || data.length < 1) return;
        byte type = data[0];
        try {
            if (type == VoiceProto.CTRL_HANDSHAKE) {
                Handshake h = HandshakePacketHandler.parseHandshake(data);
                VoiceClient.connect(h.sampleRate, h.frameSamples, h.maxRange);
                System.out.println("[VoiceChat] voice enabled by server (sr=" + h.sampleRate +
                                   " frame=" + h.frameSamples + " range=" + h.maxRange +
                                   ") — audio over MC channel \"" + VoiceProto.CTRL_CHANNEL + "\"");
            } else if (type == VoiceProto.CTRL_AUDIO_S2C) {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                in.readByte();                       // type
                int senderEntityId = in.readInt();
                int seq            = in.readInt();
                int len            = in.readShort() & 0xFFFF;
                if (len <= 0 || len > data.length) return;
                byte[] payload = new byte[len];
                in.readFully(payload, 0, len);
                AudioPlayback.enqueue(senderEntityId, seq, payload);
            }
        } catch (Throwable t) {
            System.err.println("[VoiceChat] client packet handling failed: " + t);
        }
    }
}
