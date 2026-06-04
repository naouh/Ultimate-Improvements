package com.nao.worldbackup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Streams a set of world folders into one zip archive. Pure JDK, no dependencies.
 *
 * <p>Runs entirely on a background thread (see {@link WorldBackupPlugin}), so the heavy disk I/O
 * never touches the server tick. Files are read as they are; the caller guarantees consistency by
 * flushing + disabling world saving before this starts, and re-enabling it after.
 */
final class Zipper {

	private Zipper() {}

	/** Result of a backup run: total uncompressed bytes read and how many files were archived. */
	static final class Result {
		final long bytesRead;
		final int fileCount;
		Result(long bytesRead, int fileCount) {
			this.bytesRead = bytesRead;
			this.fileCount = fileCount;
		}
	}

	/**
	 * Zips every file under each root folder into {@code out}. Each root is stored under its own
	 * folder name, so extracting recreates {@code world/}, {@code world_nether/}, etc. side by side.
	 *
	 * @param roots       top-level world folders to archive
	 * @param out         destination .zip (parent dirs must exist)
	 * @param level       Deflater level 0-9 (0 = stored, fastest/biggest)
	 * @param throttleMs  sleep this long after each file to ease disk I/O (0 = full speed)
	 * @param skip        a folder to never descend into (e.g. the backup output dir), or null
	 * @param log         logger for per-file warnings
	 */
	static Result zip(File[] roots, File out, int level, long throttleMs, File skip, Logger log)
			throws IOException, InterruptedException {
		File skipCanon = skip == null ? null : canon(skip);
		long bytes = 0L;
		int files = 0;
		byte[] buf = new byte[64 * 1024];

		ZipOutputStream zos = new ZipOutputStream(
				new java.io.BufferedOutputStream(new java.io.FileOutputStream(out), 256 * 1024));
		try {
			// level 0 -> NO_COMPRESSION (still DEFLATED method, but near-zero CPU). 1-9 -> deflate.
			zos.setLevel(level <= 0 ? Deflater.NO_COMPRESSION : Math.min(level, 9));

			for (File root : roots) {
				if (!root.isDirectory()) continue;
				String base = root.getName();
				// Iterative DFS so a deep Mystcraft age tree can't blow the call stack.
				Deque<File> stack = new ArrayDeque<File>();
				stack.push(root);
				while (!stack.isEmpty()) {
					File f = stack.pop();
					if (skipCanon != null && canon(f).equals(skipCanon)) continue;
					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedException("backup interrupted");
					}
					if (f.isDirectory()) {
						File[] kids = f.listFiles();
						if (kids != null) {
							// Stable order keeps archives diff-friendly and predictable.
							Arrays.sort(kids);
							for (File k : kids) stack.push(k);
						}
						continue;
					}
					// session.lock is held open by the live server; skip it (and it's worthless in a backup).
					if (f.getName().equals("session.lock")) continue;

					String entryName = base + "/" + relativize(root, f);
					long added = addFile(zos, entryName, f, buf, log);
					if (added >= 0) {
						bytes += added;
						files++;
						if (throttleMs > 0) Thread.sleep(throttleMs);
					}
				}
			}
		} finally {
			zos.close();
		}
		return new Result(bytes, files);
	}

	/** Adds one file; returns bytes read, or -1 if it had to be skipped (vanished / unreadable). */
	private static long addFile(ZipOutputStream zos, String entryName, File f, byte[] buf, Logger log) {
		InputStream in = null;
		try {
			in = new FileInputStream(f);
			ZipEntry e = new ZipEntry(entryName);
			e.setTime(f.lastModified());
			zos.putNextEntry(e);
			long total = 0L;
			int n;
			while ((n = in.read(buf)) > 0) {
				zos.write(buf, 0, n);
				total += n;
			}
			zos.closeEntry();
			return total;
		} catch (IOException ex) {
			// A chunk file can be deleted mid-walk (chunk unload); just skip it.
			log.warning("WorldBackup: skipped unreadable file " + f + " (" + ex.getMessage() + ")");
			try { zos.closeEntry(); } catch (IOException ignored) {}
			return -1L;
		} finally {
			if (in != null) try { in.close(); } catch (IOException ignored) {}
		}
	}

	/** Path of {@code f} relative to {@code root}, using forward slashes for zip entries. */
	private static String relativize(File root, File f) {
		String rootPath = root.getPath();
		String filePath = f.getPath();
		String rel = filePath.substring(rootPath.length());
		if (rel.startsWith(File.separator)) rel = rel.substring(1);
		return rel.replace(File.separatorChar, '/');
	}

	private static File canon(File f) {
		try {
			return f.getCanonicalFile();
		} catch (IOException e) {
			return f.getAbsoluteFile();
		}
	}
}
