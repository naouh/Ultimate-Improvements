package com.nao.voicechat.proto;

/**
 * Wire protocol between the voicechat client and server.
 *
 * <h2>Single channel — Forge custom-payload (MC channel "VC")</h2>
 * Audio rides the Minecraft connection itself rather than a side UDP socket, so it needs no extra
 * firewall port: anything that can reach the MC port can do voice. The trade-off is TCP semantics
 * (in-order, head-of-line blocking) instead of UDP, which is fine for small proximity chat.
 *
 * Every payload starts with a single type byte.
 *
 * <h3>Server → client</h3>
 * <pre>
 *   byte    type = {@link #CTRL_HANDSHAKE}   // enable voice + announce params on login
 *   int     sampleRate    (Hz)
 *   short   frameSamples
 *   short   maxRangeBlocks
 * </pre>
 * <pre>
 *   byte    type = {@link #CTRL_AUDIO_S2C}   // one forwarded frame from a nearby talker
 *   int     senderEntityId
 *   int     seq
 *   short   payloadLen
 *   byte[]  payload (μ-law mono)
 * </pre>
 *
 * <h3>Client → server</h3>
 * <pre>
 *   byte    type = {@link #CTRL_AUDIO_C2S}   // one mic frame; sender identified by the connection
 *   int     seq
 *   short   payloadLen
 *   byte[]  payload (μ-law mono, frameSamples bytes)
 * </pre>
 */
public final class VoiceProto {

    private VoiceProto() {}

    /** Custom-payload channel name (≤16 chars per the MC 1.4.7 packet limit). */
    public static final String CTRL_CHANNEL = "VC";

    /* ----- packet types (all on the "VC" channel) ----- */
    public static final byte CTRL_HANDSHAKE = 1;   // server -> client: enable + audio params
    public static final byte CTRL_AUDIO_C2S = 2;   // client -> server: one mic frame
    public static final byte CTRL_AUDIO_S2C = 3;   // server -> client: one forwarded frame

    /* ----- audio params ----- */
    public static final int   SAMPLE_RATE   = 8000;       // 8 kHz, narrow-band telephony
    public static final int   FRAME_MS      = 20;
    public static final int   FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;  // 160
    public static final int   FRAME_BYTES   = FRAME_SAMPLES;                  // μ-law: 1B / sample
}
