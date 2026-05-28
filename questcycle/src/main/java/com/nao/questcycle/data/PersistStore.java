package com.nao.questcycle.data;

import com.nao.questcycle.config.Json;
import com.nao.questcycle.config.QuestConfigPaths;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/** Reads/writes PlayerPersistence to <server>/config/questcycle/persist/<user>.json (survives world reset). */
public final class PersistStore {
	private PersistStore() {}

	public static PlayerPersistence load(String username) {
		File f = QuestConfigPaths.persistFor(username);
		if (!f.exists()) return new PlayerPersistence(username);
		try {
			Map<String, Object> root = Json.parseObject(readAll(f));
			return PlayerPersistence.fromJson(username, root);
		} catch (Throwable t) {
			System.err.println("[QuestCycle] failed to read persist for " + username + ": " + t.getMessage());
			return new PlayerPersistence(username);
		}
	}

	public static void save(PlayerPersistence p) {
		File f = QuestConfigPaths.persistFor(p.username);
		try {
			String body = Json.emit(p.toJson());
			writeAll(f, body);
		} catch (Throwable t) {
			System.err.println("[QuestCycle] failed to save persist for " + p.username + ": " + t.getMessage());
		}
	}

	/** Returns list of usernames that have a persist file on disk. Used by LeaderboardBuilder. */
	public static String[] listKnownUsernames() {
		File d = QuestConfigPaths.persistDir();
		if (d == null || !d.exists()) return new String[0];
		File[] files = d.listFiles();
		if (files == null) return new String[0];
		int n = 0;
		for (int i = 0; i < files.length; i++) {
			if (files[i].isFile() && files[i].getName().endsWith(".json")) n++;
		}
		String[] out = new String[n];
		int j = 0;
		for (int i = 0; i < files.length; i++) {
			if (files[i].isFile() && files[i].getName().endsWith(".json")) {
				String nm = files[i].getName();
				out[j++] = nm.substring(0, nm.length() - ".json".length());
			}
		}
		return out;
	}

	private static String readAll(File f) throws IOException {
		FileInputStream fis = new FileInputStream(f);
		try {
			byte[] buf = new byte[(int) f.length()];
			int read = 0;
			while (read < buf.length) {
				int n = fis.read(buf, read, buf.length - read);
				if (n < 0) break;
				read += n;
			}
			return new String(buf, 0, read, "UTF-8");
		} finally {
			try { fis.close(); } catch (IOException ignored) {}
		}
	}

	private static void writeAll(File f, String body) throws IOException {
		FileOutputStream fos = new FileOutputStream(f);
		try { fos.write(body.getBytes("UTF-8")); } finally {
			try { fos.close(); } catch (IOException ignored) {}
		}
	}
}
