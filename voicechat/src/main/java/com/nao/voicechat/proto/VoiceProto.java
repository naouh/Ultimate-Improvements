package com.nao.voicechat.proto;

/**
 * Wire protocol between the voicechat client and server.
 *
 * Two channels:
 *
 * <h2>1. Control — over Forge's standard custom-payload (MC channel "VC")</h2>
 * Reliable, TCP, multiplexed with the rest of the game's traffic. Used only at session start:
 * the server tells the client where to UDP, and what token to present.
 *
 * Server → client handshake payload:
 * <pre>
 *   byte    type        = {@link #CTRL_HANDSHAKE}
 *   UTF     udpHost     ("" means: use the same host the MC connection is on)
 *   int     udpPort
 *   long    tokenHi
 *   long    tokenLo
 *   int     sampleRate  (Hz)
 *   short   frameSamples
 *   short   maxRangeBlocks
 * </pre>
 *
 * <h2>2. Audio — over a dedicated UDP socket (default port 25566)</h2>
 * Unreliable, low-latency, no head-of-line blocking. Every packet starts with a single type byte.
 *
 * <h3>Client → server</h3>
 * <pre>
 *   byte    type = {@link #UDP_KEEPALIVE}
 *   long    tokenHi
 *   long    tokenLo
 * </pre>
 * <pre>
 *   byte    type = {@link #UDP_AUDIO_OUT}
 *   long    tokenHi
 *   long    tokenLo
 *   int     seq
 *   short   payloadLen
 *   byte[]  payload (μ-law mono, frameSamples bytes)
 * </pre>
 *
 * <h3>Server → client</h3>
 * <pre>
 *   byte    type = {@link #UDP_AUDIO_IN}
 *   int     senderEntityId
 *   int     seq
 *   short   payloadLen
 *   byte[]  payload
 * </pre>
 * <pre>
 *   byte    type = {@link #UDP_KICK}
 *   byte    reason   (1 = bad token, 2 = no session)
 * </pre>
 */
public final class VoiceProto {

    private VoiceProto() {}

    /** Custom-payload channel name (≤16 chars per the MC 1.4.7 packet limit). */
    public static final String CTRL_CHANNEL = "VC";

    /* ----- control packet types ----- */
    public static final byte CTRL_HANDSHAKE = 1;

    /* ----- udp packet types ----- */
    public static final byte UDP_KEEPALIVE     = 1;
    public static final byte UDP_AUDIO_OUT     = 2;
    public static final byte UDP_AUDIO_IN      = 3;
    public static final byte UDP_KICK          = 4;
    public static final byte UDP_KEEPALIVE_ACK = 5;

    /* ----- audio params ----- */
    public static final int   SAMPLE_RATE   = 8000;       // 8 kHz, narrow-band telephony
    public static final int   FRAME_MS      = 20;
    public static final int   FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;  // 160
    public static final int   FRAME_BYTES   = FRAME_SAMPLES;                  // μ-law: 1B / sample

    /** UDP packets larger than this are dropped (sanity bound). */
    public static final int   MAX_UDP_LEN   = 1 + 8 + 8 + 4 + 2 + 512;
}
