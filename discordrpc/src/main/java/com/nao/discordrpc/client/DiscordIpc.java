package com.nao.discordrpc.client;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Minimal Discord IPC client over a Windows named pipe, in pure Java (works on Java 6+).
 *
 * Protocol: each message is a frame of [int32 opcode][int32 length][UTF-8 JSON], both ints
 * little-endian. We only need two opcodes: 0 = handshake (announce the application id) and
 * 1 = frame (carry a SET_ACTIVITY command). Discord exposes pipes named discord-ipc-0 .. -9.
 *
 * This is deliberately tiny: it does not parse responses beyond draining the handshake reply.
 */
final class DiscordIpc {

	private static final int OP_HANDSHAKE = 0;
	private static final int OP_FRAME     = 1;
	private static final int OP_CLOSE     = 2;

	private final RandomAccessFile pipe;

	private DiscordIpc(RandomAccessFile pipe) {
		this.pipe = pipe;
	}

	/** Tries discord-ipc-0 .. -9, performs the handshake, and returns the first that connects. */
	static DiscordIpc open(String applicationId) throws IOException {
		IOException last = null;
		for (int i = 0; i < 10; i++) {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
				DiscordIpc ipc = new DiscordIpc(raf);
				ipc.writeFrame(OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + applicationId + "\"}");
				ipc.readFrame(); // READY (ignored)
				return ipc;
			} catch (IOException e) {
				last = e;
				if (raf != null) {
					try { raf.close(); } catch (IOException ignored) {}
				}
			}
		}
		throw (last != null) ? last : new IOException("No Discord IPC pipe found");
	}

	void writeActivity(String envelopeJson) throws IOException {
		writeFrame(OP_FRAME, envelopeJson);
	}

	void close() {
		try {
			writeFrame(OP_CLOSE, "{}");
		} catch (IOException ignored) {
		}
		try {
			pipe.close();
		} catch (IOException ignored) {
		}
	}

	// --- framing -----------------------------------------------------------

	private void writeFrame(int opcode, String json) throws IOException {
		byte[] data = json.getBytes("UTF-8");
		byte[] header = new byte[8];
		putLE(header, 0, opcode);
		putLE(header, 4, data.length);
		synchronized (pipe) {
			pipe.write(header);
			pipe.write(data);
		}
	}

	/** Reads and discards one frame. Throws if the pipe closed (lets the caller reconnect). */
	private void readFrame() throws IOException {
		byte[] header = new byte[8];
		pipe.readFully(header);
		int length = getLE(header, 4);
		if (length < 0 || length > (1 << 20)) {
			throw new IOException("Bogus IPC frame length: " + length);
		}
		byte[] body = new byte[length];
		pipe.readFully(body);
	}

	private static void putLE(byte[] buf, int off, int value) {
		buf[off]     = (byte) (value & 0xFF);
		buf[off + 1] = (byte) ((value >> 8) & 0xFF);
		buf[off + 2] = (byte) ((value >> 16) & 0xFF);
		buf[off + 3] = (byte) ((value >> 24) & 0xFF);
	}

	private static int getLE(byte[] buf, int off) {
		return (buf[off] & 0xFF)
				| ((buf[off + 1] & 0xFF) << 8)
				| ((buf[off + 2] & 0xFF) << 16)
				| ((buf[off + 3] & 0xFF) << 24);
	}
}
