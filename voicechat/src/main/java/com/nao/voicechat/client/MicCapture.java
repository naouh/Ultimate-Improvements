package com.nao.voicechat.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import com.nao.voicechat.VoiceConfig;
import com.nao.voicechat.codec.MuLawCodec;

/**
 * Microphone capture thread. Opens a 16-bit signed PCM TargetDataLine at the negotiated sample
 * rate, reads one frame at a time, applies the user gain, μ-law encodes, and pushes the result
 * to {@link VoiceClient#sendAudio}.
 *
 * Capture only runs while {@link #setTransmitting(boolean)} is true — that's our push-to-talk
 * gate, driven by {@link VoiceKeyHandler}. The line stays open the whole session to avoid the
 * 100–300 ms cost of every {@code line.open()} on Windows.
 */
public final class MicCapture {

    private MicCapture() {}

    private static volatile TargetDataLine line;
    private static volatile Thread thread;
    private static volatile boolean running;
    private static volatile boolean transmitting;
    private static int sampleRate;
    private static int frameSamples;
    private static int captureRate;          // Hz the line actually opens at
    private static int captureChannels;      // 1 or 2 (we downmix to mono if 2)
    private static int captureFrameSamples;  // mono samples-per-frame at captureRate

    public static synchronized void start(int sr, int frame) {
        stop();
        sampleRate   = sr;
        frameSamples = frame;
        if (!VoiceConfig.enabled) return; // master switch off — don't grab the mic device

        // Try a list of candidate formats. The first one we can actually open wins; we
        // resample/downmix to the negotiated (sr, mono) in the capture loop.
        int[]  rates    = { sr, 48000, 44100, 22050, 16000 };
        int[]  channels = { 1, 2 };

        TargetDataLine opened = null;
        AudioFormat    openedFmt = null;
        StringBuilder  tried = new StringBuilder();

        outer:
        for (int r : rates) {
            for (int ch : channels) {
                AudioFormat fmt = new AudioFormat(r, 16, ch, true, false);
                if (tried.length() > 0) tried.append(", ");
                tried.append(r).append("/").append(ch);
                opened = tryOpen(fmt, frame, r, sr);
                if (opened != null) {
                    openedFmt = fmt;
                    captureRate     = r;
                    captureChannels = ch;
                    break outer;
                }
            }
        }

        if (opened == null) {
            System.err.println("[VoiceChat] no mic line could be opened. Tried (Hz/ch): " + tried);
            System.err.println("[VoiceChat] available input mixers:");
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                Mixer m = AudioSystem.getMixer(mi);
                if (m.getTargetLineInfo().length == 0) continue; // not an input mixer
                System.err.println("  - " + mi.getName() + " :: " + mi.getDescription());
            }
            return;
        }

        line = opened;
        captureFrameSamples = (int) ((long) frame * captureRate / sr);
        System.out.println("[VoiceChat] mic OK: " + openedFmt + "  (line=" + line.getLineInfo() +
                           " buf=" + line.getBufferSize() + "B)");
        running = true;
        thread = new Thread(new Loop(), "VoiceChat-Mic");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Try every input mixer until one opens the requested format. Returns the started line, or
     * {@code null} if nothing worked. Java's default mixer is often a poor choice on Windows —
     * iterating gives the highest chance of finding a usable device.
     */
    private static TargetDataLine tryOpen(AudioFormat fmt, int negFrame, int captureRate, int negRate) {
        int monoFrame = (int) ((long) negFrame * captureRate / negRate);
        int bufBytes  = monoFrame * fmt.getChannels() * 2 * 4;
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            Mixer m = AudioSystem.getMixer(mi);
            if (!m.isLineSupported(info)) continue;
            try {
                TargetDataLine l = (TargetDataLine) m.getLine(info);
                l.open(fmt, bufBytes);
                l.start();
                System.out.println("[VoiceChat] using mixer: " + mi.getName());
                return l;
            } catch (LineUnavailableException ignored) {
                // try next mixer
            } catch (IllegalArgumentException ignored) {
                // some mixers report supported then refuse the open — keep going
            }
        }
        return null;
    }

    public static synchronized void stop() {
        running = false;
        transmitting = false;
        TargetDataLine l = line;
        if (l != null) {
            try { l.stop(); l.flush(); l.close(); } catch (Throwable ignored) {}
            line = null;
        }
        if (thread != null) { thread.interrupt(); thread = null; }
    }

    public static void setTransmitting(boolean on) {
        if (line == null) return;
        if (on && !VoiceConfig.enabled) return;
        if (on == transmitting) return;
        if (on) line.flush(); // drop stale samples from before PTT-down
        transmitting = on;
        if (VoiceConfig.verboseLogging) {
            System.out.println("[VoiceChat] PTT " + (on ? "ON" : "off"));
        }
    }

    /**
     * If the line was previously stopped (e.g. master switch was toggled off, then back on),
     * reopen with the last-negotiated parameters. Safe to call when already running.
     */
    public static synchronized void tryReopen() {
        if (line != null) return;
        if (sampleRate == 0 || frameSamples == 0) return; // never had a handshake
        start(sampleRate, frameSamples);
    }

    public static boolean isTransmitting() {
        return transmitting;
    }

    private static final class Loop implements Runnable {

        @Override
        public void run() {
            // capture side: captureFrameSamples mono samples per frame, but if the line is
            // stereo we read 2× as many bytes (2 channels) and then average L+R into mono.
            final int    nch = captureChannels;
            final int    capBytesPerFrame = captureFrameSamples * 2 * nch;
            final byte[] inBuf   = new byte[capBytesPerFrame];
            final short[] capPcm = new short[captureFrameSamples];
            final short[] pcm    = new short[frameSamples];
            final byte[]  outBuf = new byte[frameSamples];
            final boolean needsResample = (captureRate != sampleRate);

            int  framesSent = 0;
            int  peak = 0;
            long lastLogMs = System.currentTimeMillis();

            while (running) {
                TargetDataLine l = line;
                if (l == null) return;

                int read = 0;
                while (read < capBytesPerFrame) {
                    int n = l.read(inBuf, read, capBytesPerFrame - read);
                    if (n <= 0) break;
                    read += n;
                }
                if (read < capBytesPerFrame) continue;

                if (!transmitting) continue; // drain & discard while PTT is up

                final int gainPct = VoiceConfig.micGainPercent;
                for (int i = 0; i < captureFrameSamples; i++) {
                    int s;
                    if (nch == 1) {
                        int lo = inBuf[i * 2]     & 0xFF;
                        int hi = inBuf[i * 2 + 1];
                        s = (hi << 8) | lo;
                    } else {
                        // average L+R
                        int loL = inBuf[i * 4]     & 0xFF;
                        int hiL = inBuf[i * 4 + 1];
                        int loR = inBuf[i * 4 + 2] & 0xFF;
                        int hiR = inBuf[i * 4 + 3];
                        int sL  = (hiL << 8) | loL;
                        int sR  = (hiR << 8) | loR;
                        s = (sL + sR) >> 1;
                    }
                    if (gainPct != 100) {
                        s = (s * gainPct) / 100;
                        if (s > 32767)  s = 32767;
                        if (s < -32768) s = -32768;
                    }
                    capPcm[i] = (short) s;
                    int a = s < 0 ? -s : s;
                    if (a > peak) peak = a;
                }

                if (needsResample) {
                    // Cheap nearest-neighbour downsample. Voice at 8 kHz isn't worth fancy filtering.
                    for (int i = 0; i < frameSamples; i++) {
                        int srcIdx = (int) ((long) i * captureFrameSamples / frameSamples);
                        pcm[i] = capPcm[srcIdx];
                    }
                } else {
                    System.arraycopy(capPcm, 0, pcm, 0, frameSamples);
                }

                MuLawCodec.encode(pcm, frameSamples, outBuf);
                VoiceClient.sendAudio(outBuf, frameSamples);
                framesSent++;

                if (VoiceConfig.verboseLogging) {
                    long now = System.currentTimeMillis();
                    if (now - lastLogMs >= 1000) {
                        System.out.println("[VoiceChat] tx " + framesSent +
                                           " frames/s peak=" + peak + "/32767");
                        framesSent = 0;
                        peak = 0;
                        lastLogMs = now;
                    }
                }
            }
        }
    }
}
