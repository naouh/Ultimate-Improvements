package com.nao.voicechat.codec;

/**
 * G.711 μ-law codec. 16-bit linear PCM ↔ 8-bit μ-law.
 * 2:1 compression, narrow-band telephony quality. No deps, pure Java, Java 6+.
 *
 * Reference: ITU-T G.711, μ=255.
 */
public final class MuLawCodec {

    private MuLawCodec() {}

    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;

    public static byte encode(short pcm) {
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

    public static short decode(byte ulawByte) {
        int ulaw = ~ulawByte & 0xFF;
        int sign = ulaw & 0x80;
        int exponent = (ulaw >> 4) & 0x07;
        int mantissa = ulaw & 0x0F;
        int sample = ((mantissa << 3) + BIAS) << exponent;
        sample -= BIAS;
        return (short) (sign != 0 ? -sample : sample);
    }

    /** Encode {@code count} PCM samples from {@code pcm} into {@code out} starting at index 0. */
    public static void encode(short[] pcm, int count, byte[] out) {
        for (int i = 0; i < count; i++) {
            out[i] = encode(pcm[i]);
        }
    }

    /** Decode {@code count} μ-law bytes from {@code in} into {@code pcm}. */
    public static void decode(byte[] in, int count, short[] pcm) {
        for (int i = 0; i < count; i++) {
            pcm[i] = decode(in[i]);
        }
    }
}
