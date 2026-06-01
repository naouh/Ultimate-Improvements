package com.favouredcraft.serverlist;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.MathHelper;

/**
 * Re-implementation of the 1.4.7 server-list ping.
 *
 * It is functionally identical to vanilla GuiMultiplayer.func_74017_b, with ONE difference:
 * the response sanitiser keeps the newline character ('\n') instead of replacing it with '?'.
 * Vanilla strips newlines because they are not in ChatAllowedCharacters.allowedCharacters,
 * which is exactly why multi-line MOTDs do not show up on a stock 1.4.7 client.
 *
 * The section sign (167) and field separator (0) are built from char codes so there is no
 * non-ASCII byte in this source file, which keeps it compiler-encoding independent.
 */
public class FavouredPinger {

	/** The color-code section sign, U+00A7. */
	private static final char SECTION = (char) 167;
	/** The ping response field separator (NUL, U+0000). */
	private static final String NUL = String.valueOf((char) 0);

	private static final Object LOCK = new Object();
	private static int pending = 0;

	private FavouredPinger() {
	}

	/** Called when the list (re)opens. ServerData objects are recreated on load, so nothing to do. */
	public static void reset() {
		// no-op: per-server "already pinged" state lives on ServerData.field_78841_f
	}

	/** Kicks off a background ping for this server if one is not already running / done. */
	public static void tryPing(final ServerData data) {
		synchronized (LOCK) {
			if (pending >= 5 || data.field_78841_f) {
				return;
			}
			data.field_78841_f = true;
			data.pingToServer = -2L;
			data.serverMOTD = "";
			data.populationInfo = "";
			pending++;
		}

		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					data.serverMOTD = SECTION + "8Polling..";
					long start = System.nanoTime();
					ping(data);
					long end = System.nanoTime();
					data.pingToServer = (end - start) / 1000000L;
				} catch (UnknownHostException e) {
					data.pingToServer = -1L;
					data.serverMOTD = SECTION + "4Can't resolve hostname";
				} catch (SocketTimeoutException e) {
					data.pingToServer = -1L;
					data.serverMOTD = SECTION + "4Can't reach server";
				} catch (ConnectException e) {
					data.pingToServer = -1L;
					data.serverMOTD = SECTION + "4Can't reach server";
				} catch (IOException e) {
					data.pingToServer = -1L;
					data.serverMOTD = SECTION + "4Communication error";
				} catch (Exception e) {
					data.pingToServer = -1L;
					data.serverMOTD = SECTION + "4Error: " + e.getClass().getSimpleName();
				} finally {
					synchronized (LOCK) {
						pending--;
					}
				}
			}
		}, "FavouredServerList-Ping");
		thread.setDaemon(true);
		thread.start();
	}

	private static void ping(ServerData data) throws IOException {
		ServerAddress address = ServerAddress.func_78860_a(data.serverIP);
		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream out = null;

		try {
			socket = new Socket();
			socket.setSoTimeout(3000);
			socket.setTcpNoDelay(true);
			socket.setTrafficClass(18);
			socket.connect(new InetSocketAddress(address.getIP(), address.getPort()), 3000);
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			out.write(254);
			out.write(1);

			if (in.read() != 255) {
				throw new IOException("Bad message");
			}

			String response = Packet.readString(in, 256);
			char[] chars = response.toCharArray();

			for (int i = 0; i < chars.length; i++) {
				char c = chars[i];
				// Unlike vanilla (which replaces everything outside font.txt, killing '\n' AND any
				// special/unicode glyph), we keep section signs, the field separator (NUL), newlines
				// and every printable/unicode character. Only stray control chars are stripped, so
				// multi-line MOTDs and special characters such as stars survive.
				if (c < 32 && c != '\n' && c != 0) {
					chars[i] = 63; // '?'
				}
			}

			response = new String(chars);

			if (response.length() > 1 && response.charAt(0) == SECTION) {
				String[] parts = response.substring(1).split(NUL);

				if (MathHelper.parseIntWithDefault(parts[0], 0) == 1) {
					data.serverMOTD = parts[3];
					data.field_82821_f = MathHelper.parseIntWithDefault(parts[1], data.field_82821_f);
					data.gameVersion = parts[2];
					int online = MathHelper.parseIntWithDefault(parts[4], 0);
					int max = MathHelper.parseIntWithDefault(parts[5], 0);
					data.populationInfo = online >= 0 && max >= 0
							? SECTION + "7" + online + SECTION + "8/" + SECTION + "7" + max
							: SECTION + "8???";
				} else {
					data.gameVersion = "???";
					data.serverMOTD = SECTION + "8???";
					data.field_82821_f = 52;
					data.populationInfo = SECTION + "8???";
				}
			} else {
				String[] parts = response.split(String.valueOf(SECTION));
				int online = -1;
				int max = -1;

				try {
					online = Integer.parseInt(parts[1]);
					max = Integer.parseInt(parts[2]);
				} catch (Exception ignored) {
				}

				data.serverMOTD = SECTION + "7" + parts[0];
				data.populationInfo = online >= 0 && max > 0
						? SECTION + "7" + online + SECTION + "8/" + SECTION + "7" + max
						: SECTION + "8???";
				data.gameVersion = "1.3";
				data.field_82821_f = 50;
			}
		} finally {
			closeQuietly(in);
			closeQuietly(out);
			closeQuietly(socket);
		}
	}

	private static void closeQuietly(java.io.Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Throwable ignored) {
			}
		}
	}

	// java.net.Socket only implements Closeable from Java 7 onwards; this mod targets Java 6.
	private static void closeQuietly(Socket s) {
		if (s != null) {
			try {
				s.close();
			} catch (Throwable ignored) {
			}
		}
	}
}
