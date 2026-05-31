package com.nao.voicechat.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.nao.voicechat.VoiceConfig;
import com.nao.voicechat.codec.MuLawCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Per-talker audio playback. One {@link SourceDataLine} per remote player, kept alive until
 * we go ~3 s without hearing from them.
 *
 * On every incoming frame we look up the speaker's entity client-side, compute distance-based
 * attenuation (linear falloff to {@link VoiceClient#maxRange()}), apply it to the decoded PCM,
 * and write to the line. There's no spatial panning yet — both ears hear the same mono signal.
 */
public final class AudioPlayback {

    private AudioPlayback() {}

    private static final Map<Integer, Stream> streams = new HashMap<Integer, Stream>();
    private static final Object LOCK = new Object();
    private static final long IDLE_TIMEOUT_MS = 3_000L;

    static void enqueue(int senderEntityId, int seq, byte[] mulaw) {
        if (!com.nao.voicechat.VoiceConfig.enabled) return;
        if (isMutedById(senderEntityId)) return;
        Stream s;
        synchronized (LOCK) {
            s = streams.get(senderEntityId);
            if (s == null) {
                s = new Stream(senderEntityId);
                if (!s.open(VoiceClient.sampleRate())) return;
                streams.put(senderEntityId, s);
            }
        }
        s.write(seq, mulaw);
    }

    private static boolean isMutedById(int entityId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return false;
        net.minecraft.entity.Entity e = mc.theWorld.getEntityByID(entityId);
        if (!(e instanceof EntityPlayer)) return false;
        return MuteList.isMuted(((EntityPlayer) e).username);
    }

    /** Called every client tick. Reaps streams whose source we haven't heard from in a while. */
    public static void tick() {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            for (Iterator<Map.Entry<Integer, Stream>> it = streams.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Integer, Stream> e = it.next();
                if (now - e.getValue().lastWriteMs > IDLE_TIMEOUT_MS) {
                    e.getValue().close();
                    it.remove();
                }
            }
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            for (Stream s : streams.values()) s.close();
            streams.clear();
        }
    }

    public static boolean isTalking(int entityId) {
        synchronized (LOCK) {
            Stream s = streams.get(entityId);
            return s != null && (System.currentTimeMillis() - s.lastWriteMs) < 250;
        }
    }

    private static final class Stream {

        final int entityId;
        SourceDataLine line;
        long lastWriteMs;
        int  lastSeq = Integer.MIN_VALUE;

        Stream(int entityId) { this.entityId = entityId; }

        boolean open(int sampleRate) {
            AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            try {
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt, sampleRate * 2 / 5); // ~200 ms buffer
                line.start();
                return true;
            } catch (LineUnavailableException e) {
                System.err.println("[VoiceChat] playback line for eid=" + entityId + " unavailable: " + e);
                return false;
            }
        }

        void write(int seq, byte[] mulaw) {
            // Drop late arrivals — wrapping-safe.
            if (seq - lastSeq < 0 && lastSeq != Integer.MIN_VALUE) return;
            lastSeq = seq;
            lastWriteMs = System.currentTimeMillis();

            short[] pcm = new short[mulaw.length];
            MuLawCodec.decode(mulaw, mulaw.length, pcm);

            float att = computeAttenuation();
            int spkGain = VoiceConfig.spkGainPercent;

            byte[] out = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                int s = (int) (pcm[i] * att);
                if (spkGain != 100) s = (s * spkGain) / 100;
                if (s > 32767)  s = 32767;
                if (s < -32768) s = -32768;
                out[i * 2]     = (byte) (s & 0xFF);
                out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }

            SourceDataLine l = line;
            if (l != null) {
                // Non-blocking: write as much as the buffer accepts and drop the rest.
                int writable = l.available();
                int n = Math.min(writable, out.length);
                if (n > 0) l.write(out, 0, n);
            }
        }

        float computeAttenuation() {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer me = mc.thePlayer;
            if (me == null || mc.theWorld == null) return 1f;
            Entity src = mc.theWorld.getEntityByID(entityId);
            if (src == null) return 1f;  // out of render range — play at full volume rather than mute

            double dx = src.posX - me.posX;
            double dy = src.posY - me.posY;
            double dz = src.posZ - me.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int range = VoiceClient.maxRange();
            if (range <= 0) return 1f;
            if (d >= range) return 0f;
            return (float) (1.0 - d / range);
        }

        void close() {
            SourceDataLine l = line;
            if (l != null) {
                try { l.drain(); l.stop(); l.close(); } catch (Throwable ignored) {}
            }
            line = null;
        }
    }
}
