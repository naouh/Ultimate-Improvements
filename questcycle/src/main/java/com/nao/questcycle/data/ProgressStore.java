package com.nao.questcycle.data;

import com.nao.questcycle.config.Json;
import com.nao.questcycle.config.QuestConfigPaths;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/** Reads/writes PlayerProgress to <world>/questcycle/players/<user>.json (wipes with world). */
public final class ProgressStore {
	private ProgressStore() {}

	public static PlayerProgress load(String username) {
		File f = QuestConfigPaths.progressFor(username);
		if (!f.exists()) return new PlayerProgress(username);
		try {
			Map<String, Object> root = Json.parseObject(readAll(f));
			return PlayerProgress.fromJson(username, root);
		} catch (Throwable t) {
			System.err.println("[QuestCycle] failed to read progress for " + username + ": " + t.getMessage());
			return new PlayerProgress(username);
		}
	}

	public static void save(PlayerProgress p) {
		File f = QuestConfigPaths.progressFor(p.username);
		try {
			String body = Json.emit(p.toJson());
			writeAll(f, body);
		} catch (Throwable t) {
			System.err.println("[QuestCycle] failed to save progress for " + p.username + ": " + t.getMessage());
		}
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
