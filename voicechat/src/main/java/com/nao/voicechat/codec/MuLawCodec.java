package com.nao.voicechat.codec;

/**
 * G.711 μ-law codec. 16-bit linear PCM ↔ 8-bit μ-law.
 * 2:1 compression, narrow-band telephony quality. No deps, pure Java, Java 6+.
 *
 * Both directions are table-driven: decode is a 256-entry lookup, encode a 64 KiB lookup over the
 * full 16-bit sample range. The tables are built once at class load, so the per-frame path
 * (8000 samples/s out, plus 8000/s per inbound talker) is a single array read per sample.
 *
 * Reference: ITU-T G.711, μ=255.
 */
public final class MuLawCodec {

    private MuLawCodec() {}

    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;

    /** μ-law byte (0..255) → linear PCM. */
    private static final short[] DECODE_TABLE = new short[256];
    /** linear PCM sample (indexed by {@code s & 0xFFFF}) → μ-law byte. */
    private static final byte[]  ENCODE_TABLE = new byte[65536];

    static {
        for (int i = 0; i < 256; i++)   DECODE_TABLE[i] = decodeCompute((byte) i);
        for (int i = 0; i < 65536; i++) ENCODE_TABLE[i] = encodeCompute((short) i);
    }

    public static byte  encode(short pcm)     { return ENCODE_TABLE[pcm & 0xFFFF]; }
    public static short decode(byte ulawByte) { return DECODE_TABLE[ulawByte & 0xFF]; }

    /** Encode {@code count} PCM samples from {@code pcm} into {@code out} starting at index 0. */
    public static void encode(short[] pcm, int count, byte[] out) {
        for (int i = 0; i < count; i++) out[i] = ENCODE_TABLE[pcm[i] & 0xFFFF];
    }

    /** Decode {@code count} μ-law bytes from {@code in} into {@code pcm}. */
    public static void decode(byte[] in, int count, short[] pcm) {
        for (int i = 0; i < count; i++) pcm[i] = DECODE_TABLE[in[i] & 0xFF];
    }

    /* ----- one-time table construction ----- */

    private static byte encodeCompute(short pcm) {
        int sign = (pcm >> 8) & 0x80;
        int sample = sign != 0 ? -pcm : pcm;
        if (sample > CLIP) sample = CLIP;
        sample += BIAS;

        int exponent = 7;
        for (int mask = 0x4000; (sample & mask) == 0 && exponent > 0; mask >>= 1) {
            exponent--;
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        int ulaw = ~(sign | (exponent << 4) | mantissa);
        return (byte) (ulaw & 0xFF);
    }

    private static short decodeCompute(byte ulawByte) {
        int ulaw = ~ulawByte & 0xFF;
        int sign = ulaw & 0x80;
        int exponent = (ulaw >> 4) & 0x07;
        int mantissa = ulaw & 0x0F;
        int sample = ((mantissa << 3) + BIAS) << exponent;
        sample -= BIAS;
        return (short) (sign != 0 ? -sample : sample);
    }
}
