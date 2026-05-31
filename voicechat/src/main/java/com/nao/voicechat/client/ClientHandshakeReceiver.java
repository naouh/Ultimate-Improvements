package com.nao.voicechat.client;

import com.nao.voicechat.network.HandshakePacketHandler;
import com.nao.voicechat.network.HandshakePacketHandler.Handshake;

import net.minecraft.client.Minecraft;

/**
 * Called by {@link HandshakePacketHandler} on the client side when a handshake arrives over the
 * "VC" custom-payload channel. Reflective entry point — keep the signature exactly
 * {@code public static void handle(byte[])} or the server-side dispatcher breaks.
 */
public final class ClientHandshakeReceiver {

    private ClientHandshakeReceiver() {}

    public static void handle(byte[] data) {
        try {
            Handshake h = HandshakePacketHandler.parseHandshake(data);

            String host = (h.udpHost == null || h.udpHost.isEmpty())
                          ? Minecraft.getMinecraft().getServerData() != null
                                ? hostOnly(Minecraft.getMinecraft().getServerData().serverIP)
                                : "127.0.0.1"
                          : h.udpHost;

            VoiceClient.connect(host, h.udpPort, h.tokenHi, h.tokenLo,
                                h.sampleRate, h.frameSamples, h.maxRange);
            System.out.println("[VoiceChat] handshake → udp " + host + ":" + h.udpPort +
                               " sr=" + h.sampleRate + " frame=" + h.frameSamples +
                               " range=" + h.maxRange);
        } catch (Throwable t) {
            System.err.println("[VoiceChat] handshake parse failed: " + t);
            t.printStackTrace();
        }
    }

    private static String hostOnly(String serverIP) {
        if (serverIP == null) return "127.0.0.1";
        int colon = serverIP.indexOf(':');
        return colon < 0 ? serverIP : serverIP.substring(0, colon);
    }
}
