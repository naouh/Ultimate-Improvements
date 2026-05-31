package com.nao.voicechat.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.nao.voicechat.VoiceConfig;
import com.nao.voicechat.proto.VoiceProto;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * The dedicated UDP voice server. One {@link DatagramSocket} bound to {@link VoiceConfig#udpPort},
 * one receiver thread, sessions keyed by entity id and by token.
 *
 * Hot path on receive:
 *   1. Parse the type byte.
 *   2. Look up the session by token (long×2 lookup table).
 *   3. Update {@link VoiceSession#udpAddress} if needed.
 *   4. For audio: enumerate the sender's nearby players and {@code sendto} each.
 */
public final class VoiceServer {

    private VoiceServer() {}

    private static final SecureRandom RNG = new SecureRandom();

    private static final Map<Integer, VoiceSession> byEntityId = new ConcurrentHashMap<Integer, VoiceSession>();
    private static final Map<Long, VoiceSession>    byToken    = new ConcurrentHashMap<Long, VoiceSession>();

    private static volatile DatagramSocket socket;
    private static volatile Thread         receiverThread;
    private static volatile boolean        running;

    /** Mix the two 64-bit halves of the token into a single key for the lookup map. */
    private static long tokenKey(long hi, long lo) {
        return hi ^ (lo * 0x9E3779B97F4A7C15L);
    }

    public static synchronized void start(int port) {
        if (running) return;
        try {
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(64 * 1024);
            socket.setSendBufferSize(64 * 1024);
        } catch (SocketException e) {
            System.err.println("[VoiceChat] FAILED to bind UDP " + port + ": " + e);
            return;
        }
        running = true;
        receiverThread = new Thread(new Receiver(), "VoiceChat-UDP");
        receiverThread.setDaemon(true);
        receiverThread.start();
        System.out.println("[VoiceChat] UDP server listening on port " + port);
    }

    public static synchronized void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (receiverThread != null) {
            receiverThread.interrupt();
            receiverThread = null;
        }
        byEntityId.clear();
        byToken.clear();
        System.out.println("[VoiceChat] UDP server stopped");
    }

    public static VoiceSession allocateSession(EntityPlayerMP epm) {
        // Free any prior session for this entity (re-login on the same world).
        removeSession(epm.entityId);

        long hi = RNG.nextLong();
        long lo = RNG.nextLong();
        VoiceSession s = new VoiceSession(hi, lo, epm);
        byEntityId.put(epm.entityId, s);
        byToken.put(tokenKey(hi, lo), s);
        return s;
    }

    public static void removeSession(int entityId) {
        VoiceSession s = byEntityId.remove(entityId);
        if (s != null) byToken.remove(tokenKey(s.tokenHi, s.tokenLo));
    }

    /* --------------------------------------------------------------------- */

    private static final class Receiver implements Runnable {

        private final byte[] buf = new byte[VoiceProto.MAX_UDP_LEN];
        private final DatagramPacket pkt = new DatagramPacket(buf, buf.length);

        @Override
        public void run() {
            while (running) {
                pkt.setData(buf, 0, buf.length);
                try {
                    socket.receive(pkt);
                    handle(pkt);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[VoiceChat] udp recv: " + e);
                    }
                }
            }
        }

        private void handle(DatagramPacket p) {
            ByteBuffer bb = ByteBuffer.wrap(p.getData(), p.getOffset(), p.getLength());
            if (bb.remaining() < 1) return;
            byte type = bb.get();

            if (type == VoiceProto.UDP_KEEPALIVE) {
                if (bb.remaining() < 16) return;
                long hi = bb.getLong();
                long lo = bb.getLong();
                VoiceSession s = byToken.get(tokenKey(hi, lo));
                if (s == null) {
                    sendKick(p, (byte) 1);
                    return;
                }
                boolean firstSeen = (s.udpAddress == null);
                s.udpAddress = (InetSocketAddress) p.getSocketAddress();
                s.lastUdpRecvMs = System.currentTimeMillis();
                if (firstSeen) {
                    System.out.println("[VoiceChat] UDP registered for " + s.username +
                                       " <- " + s.udpAddress);
                }
                if (VoiceConfig.verboseLogging) {
                    System.out.println("[VoiceChat] keepalive from " + s.username +
                                       " (" + s.udpAddress + ")");
                }
                // ACK so the client knows the round trip works.
                byte[] ack = new byte[1];
                ack[0] = VoiceProto.UDP_KEEPALIVE_ACK;
                try {
                    socket.send(new DatagramPacket(ack, ack.length, p.getSocketAddress()));
                } catch (IOException ignored) {}
                return;
            }

            if (type == VoiceProto.UDP_AUDIO_OUT) {
                if (bb.remaining() < 16 + 4 + 2) return;
                long hi = bb.getLong();
                long lo = bb.getLong();
                VoiceSession s = byToken.get(tokenKey(hi, lo));
                if (s == null) {
                    sendKick(p, (byte) 1);
                    return;
                }
                if (s.udpAddress == null) s.udpAddress = (InetSocketAddress) p.getSocketAddress();
                s.lastUdpRecvMs = System.currentTimeMillis();

                int   seq        = bb.getInt();
                int   payloadLen = bb.getShort() & 0xFFFF;
                if (payloadLen > bb.remaining()) return;

                broadcast(s, seq, bb, payloadLen);
            }
        }

        private void broadcast(VoiceSession sender, int seq, ByteBuffer src, int payloadLen) {
            MinecraftServer mc = MinecraftServer.getServer();
            if (mc == null) return;

            EntityPlayerMP senderEntity = findPlayer(mc, sender.entityId);
            if (senderEntity == null) return;

            double sx = senderEntity.posX, sy = senderEntity.posY, sz = senderEntity.posZ;
            int    senderDim = senderEntity.worldObj.provider.dimensionId;
            double rangeSq = (double) VoiceConfig.maxRangeBlocks * VoiceConfig.maxRangeBlocks;

            // Build the forwarded packet once: header is identical for every recipient.
            ByteBuffer out = ByteBuffer.allocate(1 + 4 + 4 + 2 + payloadLen);
            out.put(VoiceProto.UDP_AUDIO_IN);
            out.putInt(sender.entityId);
            out.putInt(seq);
            out.putShort((short) payloadLen);
            int srcPos = src.position();
            for (int i = 0; i < payloadLen; i++) out.put(src.get(srcPos + i));
            byte[] forward = out.array();

            for (Object o : mc.getConfigurationManager().playerEntityList) {
                EntityPlayerMP rcv = (EntityPlayerMP) o;
                if (rcv.entityId == sender.entityId && !VoiceConfig.selfEcho) continue;
                if (rcv.worldObj == null || rcv.worldObj.provider.dimensionId != senderDim) continue;

                double dx = rcv.posX - sx, dy = rcv.posY - sy, dz = rcv.posZ - sz;
                if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

                VoiceSession rs = byEntityId.get(rcv.entityId);
                if (rs == null || rs.udpAddress == null) continue;

                try {
                    DatagramPacket fp = new DatagramPacket(forward, forward.length, rs.udpAddress);
                    socket.send(fp);
                } catch (IOException e) {
                    if (VoiceConfig.verboseLogging) {
                        System.err.println("[VoiceChat] forward to " + rs.username + " failed: " + e);
                    }
                }
            }
        }

        private void sendKick(DatagramPacket p, byte reason) {
            byte[] msg = new byte[2];
            msg[0] = VoiceProto.UDP_KICK;
            msg[1] = reason;
            try {
                socket.send(new DatagramPacket(msg, msg.length, p.getSocketAddress()));
            } catch (IOException ignored) {}
        }

        @SuppressWarnings("unchecked")
        private static EntityPlayerMP findPlayer(MinecraftServer mc, int entityId) {
            List<EntityPlayerMP> all = mc.getConfigurationManager().playerEntityList;
            for (int i = 0, n = all.size(); i < n; i++) {
                EntityPlayerMP epm = all.get(i);
                if (epm.entityId == entityId) return epm;
            }
            return null;
        }
    }

    /* Used by tests / commands to drop dead sessions. */
    public static int reapStale(long olderThanMs) {
        long cutoff = System.currentTimeMillis() - olderThanMs;
        int n = 0;
        Iterator<Map.Entry<Integer, VoiceSession>> it = byEntityId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, VoiceSession> e = it.next();
            VoiceSession s = e.getValue();
            if (s.lastUdpRecvMs != 0 && s.lastUdpRecvMs < cutoff) {
                it.remove();
                byToken.remove(tokenKey(s.tokenHi, s.tokenLo));
                n++;
            }
        }
        return n;
    }

    /** Allow other server code (e.g. an admin command) to query who's connected. */
    public static Iterable<VoiceSession> sessions() {
        return byEntityId.values();
    }

    // Touched in init() of VoiceChatMod just to keep the unused-import warning quiet on
    // side-only deployments.
    @SuppressWarnings("unused")
    private static final Class<?> HOLD = FMLCommonHandler.class;
    @SuppressWarnings("unused")
    private static final Class<?> HOLD2 = WorldServer.class;
}
