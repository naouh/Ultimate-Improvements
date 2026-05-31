package com.nao.voicechat.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.nao.voicechat.VoiceConfig;
import com.nao.voicechat.proto.VoiceProto;

/**
 * Client-side bridge to the dedicated UDP voice port. Single socket. One receiver thread (audio
 * in) and one keepalive thread (every 2s). Mic capture is owned by {@link MicCapture}.
 *
 * Connection state is reset every time we receive a fresh handshake from the server — so a
 * reconnect or world change just transparently rebinds.
 */
public final class VoiceClient {

    private VoiceClient() {}

    private static volatile DatagramSocket    socket;
    private static volatile InetSocketAddress serverAddr;
    private static volatile long              tokenHi, tokenLo;
    private static volatile int               sampleRate, frameSamples, maxRangeBlocks;
    private static volatile Thread            receiverThread;
    private static volatile Thread            keepaliveThread;
    private static volatile int               outSeq;
    private static volatile boolean           running;
    /** Set by the Receiver thread when we get a UDP packet back from the server. */
    static volatile boolean                   ackReceived;

    public static synchronized void connect(String host, int port,
                                            long hi, long lo,
                                            int sr, int frame, int range) {
        disconnect();
        try {
            socket          = new DatagramSocket();
            socket.setSendBufferSize(64 * 1024);
            socket.setReceiveBufferSize(64 * 1024);
            serverAddr      = new InetSocketAddress(InetAddress.getByName(host), port);
            tokenHi         = hi;
            tokenLo         = lo;
            sampleRate      = sr;
            frameSamples    = frame;
            maxRangeBlocks  = range;
            outSeq          = 0;
            ackReceived     = false;
            running         = true;
        } catch (IOException e) {
            System.err.println("[VoiceChat] client UDP bind failed: " + e);
            return;
        }

        receiverThread = new Thread(new Receiver(), "VoiceChat-ClientRecv");
        receiverThread.setDaemon(true);
        receiverThread.start();

        keepaliveThread = new Thread(new Keepalive(), "VoiceChat-Keepalive");
        keepaliveThread.setDaemon(true);
        keepaliveThread.start();

        // Send one keepalive immediately so the server learns our address before we try to talk.
        sendKeepalive();

        MicCapture.start(sr, frame);
    }

    public static synchronized void disconnect() {
        running = false;
        MicCapture.stop();
        AudioPlayback.shutdown();
        if (socket != null) { socket.close(); socket = null; }
        if (receiverThread != null) { receiverThread.interrupt(); receiverThread = null; }
        if (keepaliveThread != null) { keepaliveThread.interrupt(); keepaliveThread = null; }
        serverAddr = null;
    }

    public static boolean isConnected() {
        return running && socket != null && serverAddr != null;
    }

    public static int maxRange() { return maxRangeBlocks; }
    public static int sampleRate() { return sampleRate; }
    public static int frameSamples() { return frameSamples; }

    /** Called by {@link MicCapture} once per encoded frame. */
    public static void sendAudio(byte[] mulawFrame, int length) {
        if (!isConnected()) return;
        ByteBuffer bb = ByteBuffer.allocate(1 + 16 + 4 + 2 + length);
        bb.put(VoiceProto.UDP_AUDIO_OUT);
        bb.putLong(tokenHi);
        bb.putLong(tokenLo);
        bb.putInt(outSeq++);
        bb.putShort((short) length);
        bb.put(mulawFrame, 0, length);
        send(bb.array());
    }

    private static void sendKeepalive() {
        if (!isConnected()) return;
        ByteBuffer bb = ByteBuffer.allocate(1 + 16);
        bb.put(VoiceProto.UDP_KEEPALIVE);
        bb.putLong(tokenHi);
        bb.putLong(tokenLo);
        send(bb.array());
    }

    private static void send(byte[] data) {
        DatagramSocket s = socket;
        InetSocketAddress a = serverAddr;
        if (s == null || a == null) return;
        try {
            s.send(new DatagramPacket(data, data.length, a));
        } catch (IOException ignored) {}
    }

    private static final class Receiver implements Runnable {
        private final byte[] buf = new byte[VoiceProto.MAX_UDP_LEN];
        private final DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        @Override
        public void run() {
            int audioFrames = 0;
            long lastStatsMs = System.currentTimeMillis();
            while (running) {
                DatagramSocket s = socket;
                if (s == null) return;
                pkt.setData(buf, 0, buf.length);
                try {
                    s.receive(pkt);
                } catch (IOException e) {
                    if (running) System.err.println("[VoiceChat] client recv: " + e);
                    return;
                }
                ByteBuffer bb = ByteBuffer.wrap(pkt.getData(), pkt.getOffset(), pkt.getLength());
                if (bb.remaining() < 1) continue;
                byte type = bb.get();
                if (type == VoiceProto.UDP_KEEPALIVE_ACK) {
                    if (!ackReceived) {
                        System.out.println("[VoiceChat] UDP round-trip confirmed (keepalive ACK)");
                        ackReceived = true;
                    }
                    continue;
                }
                if (type == VoiceProto.UDP_AUDIO_IN) {
                    if (bb.remaining() < 4 + 4 + 2) continue;
                    int senderEntityId = bb.getInt();
                    int seq            = bb.getInt();
                    int len            = bb.getShort() & 0xFFFF;
                    if (len > bb.remaining()) continue;
                    byte[] payload = new byte[len];
                    bb.get(payload, 0, len);
                    AudioPlayback.enqueue(senderEntityId, seq, payload);
                    audioFrames++;
                    if (VoiceConfig.verboseLogging) {
                        long now = System.currentTimeMillis();
                        if (now - lastStatsMs >= 1000) {
                            System.out.println("[VoiceChat] rx " + audioFrames + " frames/s");
                            audioFrames = 0;
                            lastStatsMs = now;
                        }
                    }
                } else if (type == VoiceProto.UDP_KICK) {
                    System.err.println("[VoiceChat] kicked by server (reason=" +
                                       (bb.remaining() >= 1 ? bb.get() : 0) + ")");
                    running = false;
                }
            }
        }
    }

    private static final class Keepalive implements Runnable {
        @Override
        public void run() {
            int tick = 0;
            while (running) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                sendKeepalive();
                tick++;
                if (tick == 3 && !ackReceived) {
                    InetSocketAddress a = serverAddr;
                    System.err.println("[VoiceChat] WARNING: no UDP reply from " + a +
                                       " after 6s — the server-side UDP port is probably " +
                                       "blocked by a firewall/NAT.");
                }
            }
        }
    }
}
