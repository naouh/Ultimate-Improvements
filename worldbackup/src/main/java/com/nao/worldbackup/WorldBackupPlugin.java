package com.nao.worldbackup;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WorldBackup - low-lag, TickThreading-safe world backups for MCPC+ 1.4.7.
 *
 * <p><b>Why it doesn't lag:</b> the only work done on the server (main) thread is a one-shot save
 * flush + "autosave off" - exactly the cost of a normal autosave tick, paid once. The whole archive
 * is then written on a dedicated background thread, so the heavy disk I/O never blocks ticking. When
 * the zip finishes, autosave is turned back on from the main thread.
 *
 * <p><b>Why it's TickThreading-safe:</b> with TT, worlds tick on worker threads, but it still honours
 * the per-world "saving disabled" flag (the same one CraftBukkit's {@code setAutoSave(false)} sets).
 * We flush + disable saving for every loaded world before copying, so region files stay frozen and
 * internally consistent while we read them, then re-enable. No file is read while the server writes it.
 *
 * <p><b>Forge/MCPC+ layout:</b> dimensions are separate top-level folders (world, world_nether,
 * world_the_end, world_twilightforest, world_myst). Mystcraft ages live as sub-folders inside
 * world_myst, so we archive whole top-level folders recursively - every age comes along. Auto-detect
 * also scans for world folders that aren't currently loaded (a Twilight Forest nobody is in), so a
 * sleeping dimension still gets backed up.
 *
 * <p>Everything is English-facing per project convention; wording is configurable in config.yml.
 */
public final class WorldBackupPlugin extends JavaPlugin implements Listener {

	private static final String PREFIX = "world-backup-";
	private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

	// --- Config (cached) ---------------------------------------------------
	private int intervalMinutes;
	private boolean onStartup;
	private File outputDir;
	private int keep;
	private int compressionLevel;
	private List<String> configuredWorlds;
	private boolean disableSave;
	private long minFreeMb;
	private long throttleMs;
	private boolean broadcast;
	private String msgStart;
	private String msgDone;
	private String msgFailed;

	// --- Runtime state -----------------------------------------------------
	/** Guards against two backups running at once (manual + scheduled colliding). */
	private final AtomicBoolean running = new AtomicBoolean(false);
	/** True while a backup is in flight; used by the WorldLoadEvent guard. */
	private volatile boolean backupActive;
	/** Worlds whose autosave WE turned off, to be restored when the backup ends. */
	private final List<String> savedDisabled = new ArrayList<String>();
	/** Scheduler id of the repeating auto-backup task (-1 = none). */
	private int autoTaskId = -1;
	private volatile String lastStatus = "no backup run yet this session";

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadConfigValues();
		getServer().getPluginManager().registerEvents(this, this);
		scheduleAuto();
		if (onStartup) {
			// Delay so the server finishes loading worlds first (20 ticks/s -> ~30s).
			getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				@Override public void run() { runBackup(getServer().getConsoleSender()); }
			}, 600L);
		}
		getLogger().info("WorldBackup enabled (auto every "
				+ (intervalMinutes > 0 ? intervalMinutes + " min" : "OFF") + ", keep " + keep
				+ ", level " + compressionLevel + ", output " + outputDir + ").");
	}

	@Override
	public void onDisable() {
		// If a backup is still zipping we can't wait on the main thread; just make sure saving is
		// back on so the server doesn't shut down with autosave disabled.
		reenableSaves();
		getLogger().info("WorldBackup disabled.");
	}

	// --- Config ------------------------------------------------------------

	private void loadConfigValues() {
		reloadConfig();
		FileConfiguration c = getConfig();
		intervalMinutes = c.getInt("backup.interval-minutes", 120);
		onStartup = c.getBoolean("backup.on-startup", false);
		keep = c.getInt("backup.keep", 10);
		compressionLevel = clamp(c.getInt("backup.compression-level", 1), 0, 9);
		configuredWorlds = c.getStringList("backup.worlds");
		disableSave = c.getBoolean("backup.disable-save-during-backup", true);
		minFreeMb = Math.max(0, c.getLong("backup.min-free-space-mb", 1024));
		throttleMs = Math.max(0, c.getLong("backup.throttle-ms", 0));
		broadcast = c.getBoolean("messages.broadcast", true);
		msgStart = c.getString("messages.start", "&7[Backup] &fStarting world backup...");
		msgDone = c.getString("messages.done", "&7[Backup] &aBackup complete (&f{size}&a, {files} files) in &f{seconds}s&a.");
		msgFailed = c.getString("messages.failed", "&7[Backup] &cBackup failed - see console.");

		String outRaw = c.getString("backup.output-dir", "backups");
		File out = new File(outRaw);
		outputDir = out.isAbsolute() ? out : new File(getServer().getWorldContainer(), outRaw);
	}

	private void scheduleAuto() {
		if (autoTaskId != -1) {
			getServer().getScheduler().cancelTask(autoTaskId);
			autoTaskId = -1;
		}
		if (intervalMinutes <= 0) return;
		long period = (long) intervalMinutes * 60L * 20L; // minutes -> ticks
		autoTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override public void run() { runBackup(getServer().getConsoleSender()); }
		}, period, period);
	}

	// --- Backup orchestration ----------------------------------------------

	/**
	 * Entry point, always called on the MAIN thread. Resolves world folders, flushes + disables
	 * saving, then hands the zip work to a background thread. Returns immediately.
	 */
	private void runBackup(final CommandSender initiator) {
		if (!running.compareAndSet(false, true)) {
			send(initiator, ChatColor.YELLOW + "A backup is already running.");
			return;
		}
		boolean spawned = false;
		try {
			final File[] roots = resolveRoots();
			if (roots.length == 0) {
				send(initiator, ChatColor.RED + "WorldBackup: no world folders found to back up.");
				return;
			}

			if (minFreeMb > 0) {
				File probe = outputDir.exists() ? outputDir : outputDir.getParentFile();
				long freeMb = (probe != null ? probe.getUsableSpace() : Long.MAX_VALUE) / (1024L * 1024L);
				if (freeMb < minFreeMb) {
					send(initiator, ChatColor.RED + "WorldBackup aborted: only " + freeMb
							+ " MB free (need " + minFreeMb + " MB). Free up disk space or lower min-free-space-mb.");
					lastStatus = "aborted (low disk: " + freeMb + " MB free)";
					return;
				}
			}

			if (!outputDir.exists() && !outputDir.mkdirs()) {
				send(initiator, ChatColor.RED + "WorldBackup: could not create output dir " + outputDir);
				return;
			}

			// --- main-thread cost: flush once, then freeze saving. Same as one autosave tick. ---
			if (disableSave) {
				savedDisabled.clear();
				for (World w : getServer().getWorlds()) {
					if (w.isAutoSave()) {
						w.setAutoSave(false);
						savedDisabled.add(w.getName());
					}
					try {
						w.save();
					} catch (Throwable t) {
						getLogger().warning("WorldBackup: world '" + w.getName() + "' failed to flush: " + t);
					}
				}
			}
			backupActive = true;

			final String stamp = STAMP.format(new Date());
			final File tmp = new File(outputDir, PREFIX + stamp + ".zip.tmp");
			final File finalFile = new File(outputDir, PREFIX + stamp + ".zip");
			final long startNs = System.nanoTime();
			final String initiatorName = (initiator instanceof Player) ? initiator.getName() : null;

			announce(msgStart, initiatorName);
			lastStatus = "running (started " + stamp + ")";

			Thread worker = new Thread(new Runnable() {
				@Override public void run() {
					backgroundZip(roots, tmp, finalFile, startNs, initiatorName);
				}
			}, "WorldBackup-zip");
			worker.setDaemon(true);
			worker.start();
			spawned = true;
		} catch (Throwable t) {
			getLogger().severe("WorldBackup failed to start: " + t);
			send(initiator, ChatColor.RED + "WorldBackup failed to start - see console.");
		} finally {
			if (!spawned) {
				// Nothing handed off to the background thread: undo any save-disable and release the lock.
				reenableSaves();
				backupActive = false;
				running.set(false);
			}
		}
	}

	/** Runs on the background thread: writes the archive, prunes, then hops back to finish. */
	private void backgroundZip(File[] roots, File tmp, File finalFile, long startNs, String initiatorName) {
		String detail;
		try {
			Zipper.Result r = Zipper.zip(roots, tmp, compressionLevel, throttleMs, outputDir, getLogger());
			if (!tmp.renameTo(finalFile)) {
				throw new java.io.IOException("could not rename " + tmp.getName() + " -> " + finalFile.getName());
			}
			double seconds = (System.nanoTime() - startNs) / 1.0e9;
			String size = humanSize(finalFile.length());
			prune();
			detail = msgDone
					.replace("{size}", size)
					.replace("{files}", Integer.toString(r.fileCount))
					.replace("{seconds}", String.format(java.util.Locale.US, "%.1f", seconds));
			lastStatus = "ok: " + finalFile.getName() + " (" + size + ", " + r.fileCount
					+ " files, " + String.format(java.util.Locale.US, "%.1fs", seconds) + ")";
			getLogger().info("WorldBackup wrote " + finalFile.getName() + " (" + size + ", "
					+ r.fileCount + " files) in " + String.format(java.util.Locale.US, "%.1fs", seconds) + ".");
		} catch (Throwable t) {
			getLogger().severe("WorldBackup zip failed: " + t);
			tmp.delete();
			detail = msgFailed;
			lastStatus = "FAILED: " + t;
		}
		final String message = detail;
		// Hop back to the main thread to re-enable saving and announce.
		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override public void run() {
				reenableSaves();
				backupActive = false;
				running.set(false);
				announce(message, initiatorName);
			}
		});
	}

	/** Re-enables autosave on every world we turned off. Safe to call when nothing was disabled. */
	private void reenableSaves() {
		if (savedDisabled.isEmpty()) return;
		for (String name : savedDisabled) {
			World w = getServer().getWorld(name);
			if (w != null) w.setAutoSave(true);
		}
		savedDisabled.clear();
	}

	/**
	 * While a backup is in flight, freeze saving on any world that loads mid-copy (e.g. a player
	 * walks into the Twilight Forest), so its region files don't get written while we read them.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldLoad(WorldLoadEvent event) {
		if (!backupActive || !disableSave) return;
		World w = event.getWorld();
		if (w.isAutoSave()) {
			w.setAutoSave(false);
			savedDisabled.add(w.getName());
		}
	}

	// --- World-folder resolution -------------------------------------------

	/** Top-level world folders to archive: configured list, or auto-detected. De-duped, no nesting. */
	private File[] resolveRoots() {
		File container = canon(getServer().getWorldContainer());
		File outCanon = canon(outputDir);
		// canonical path -> folder, insertion-ordered for stable archives.
		Map<String, File> roots = new LinkedHashMap<String, File>();

		if (configuredWorlds != null && !configuredWorlds.isEmpty()) {
			for (String name : configuredWorlds) {
				if (name == null || name.trim().isEmpty()) continue;
				File f = new File(name.trim());
				if (!f.isAbsolute()) f = new File(getServer().getWorldContainer(), name.trim());
				if (f.isDirectory()) addRoot(roots, f);
				else getLogger().warning("WorldBackup: configured world folder not found: " + f);
			}
		} else {
			// 1) Every loaded world, mapped up to its top-level folder under the container.
			for (World w : getServer().getWorlds()) {
				File top = topLevelUnder(container, w.getWorldFolder());
				addRoot(roots, top);
			}
			// 2) Scan the container for world folders that aren't currently loaded.
			File[] subs = getServer().getWorldContainer().listFiles();
			if (subs != null) {
				Arrays.sort(subs);
				for (File d : subs) {
					if (!d.isDirectory()) continue;
					if (canon(d).equals(outCanon)) continue; // never back up the backups
					if (looksLikeWorld(d)) addRoot(roots, d);
				}
			}
		}

		// Drop any folder nested inside another selected folder (parent already covers it).
		List<File> kept = new ArrayList<File>();
		outer:
		for (Map.Entry<String, File> e : roots.entrySet()) {
			String p = e.getKey();
			for (String other : roots.keySet()) {
				if (!other.equals(p) && p.startsWith(other + File.separator)) continue outer;
			}
			kept.add(e.getValue());
		}
		return kept.toArray(new File[0]);
	}

	private void addRoot(Map<String, File> roots, File f) {
		File c = canon(f);
		roots.put(c.getPath(), c);
	}

	/** Climbs from {@code folder} to the child that sits directly under {@code container}. */
	private static File topLevelUnder(File container, File folder) {
		File cur = canon(folder);
		File parent = cur.getParentFile();
		while (parent != null && !canon(parent).equals(container)) {
			cur = parent;
			parent = cur.getParentFile();
		}
		return parent != null ? cur : canon(folder); // not under container -> use the folder as-is
	}

	/** A folder is a world root if it (or any immediate child) holds level.dat or a region/ dir. */
	private static boolean looksLikeWorld(File d) {
		if (hasWorldData(d)) return true;
		File[] kids = d.listFiles();
		if (kids != null) {
			for (File k : kids) {
				if (k.isDirectory() && hasWorldData(k)) return true; // e.g. world_myst/age2
			}
		}
		return false;
	}

	private static boolean hasWorldData(File d) {
		return new File(d, "level.dat").exists() || new File(d, "region").isDirectory();
	}

	// --- Retention ---------------------------------------------------------

	/** Deletes the oldest archives beyond {@code keep} (0 = keep everything). */
	private void prune() {
		if (keep <= 0) return;
		File[] files = outputDir.listFiles(new java.io.FilenameFilter() {
			@Override public boolean accept(File dir, String name) {
				return name.startsWith(PREFIX) && name.endsWith(".zip");
			}
		});
		if (files == null || files.length <= keep) return;
		Arrays.sort(files, new java.util.Comparator<File>() {
			@Override public int compare(File a, File b) {
				return Long.compare(a.lastModified(), b.lastModified()); // oldest first
			}
		});
		int toDelete = files.length - keep;
		for (int i = 0; i < toDelete; i++) {
			if (files[i].delete()) getLogger().info("WorldBackup pruned old archive " + files[i].getName());
			else getLogger().warning("WorldBackup could not delete old archive " + files[i].getName());
		}
	}

	// --- Messaging ---------------------------------------------------------

	/** Console always; broadcast to everyone if enabled, else just to the initiating player. */
	private void announce(String raw, String initiatorName) {
		String colored = ChatColor.translateAlternateColorCodes('&', raw);
		getLogger().info(ChatColor.stripColor(colored));
		if (broadcast) {
			getServer().broadcastMessage(colored);
		} else if (initiatorName != null) {
			Player p = getServer().getPlayerExact(initiatorName);
			if (p != null) p.sendMessage(colored);
		}
	}

	private void send(CommandSender to, String msg) {
		if (to != null) to.sendMessage(msg);
	}

	// --- Command -----------------------------------------------------------

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length > 0 ? args[0].toLowerCase() : "now";

		if (sub.equals("now") || sub.equals("start")) {
			runBackup(sender);
			return true;
		}
		if (sub.equals("reload")) {
			loadConfigValues();
			scheduleAuto();
			sender.sendMessage(ChatColor.GREEN + "WorldBackup config reloaded (auto every "
					+ (intervalMinutes > 0 ? intervalMinutes + " min" : "OFF") + ", keep " + keep + ").");
			return true;
		}
		if (sub.equals("status")) {
			sender.sendMessage(ChatColor.YELLOW + "WorldBackup status:");
			sender.sendMessage(ChatColor.GRAY + " - running: " + ChatColor.WHITE + running.get());
			sender.sendMessage(ChatColor.GRAY + " - last: " + ChatColor.WHITE + lastStatus);
			sender.sendMessage(ChatColor.GRAY + " - auto: " + ChatColor.WHITE
					+ (intervalMinutes > 0 ? "every " + intervalMinutes + " min" : "disabled"));
			sender.sendMessage(ChatColor.GRAY + " - output: " + ChatColor.WHITE + outputDir);
			sender.sendMessage(ChatColor.GRAY + " - save-off during backup: " + ChatColor.WHITE + disableSave);
			return true;
		}
		if (sub.equals("list")) {
			File[] files = outputDir.listFiles(new java.io.FilenameFilter() {
				@Override public boolean accept(File dir, String name) {
					return name.startsWith(PREFIX) && name.endsWith(".zip");
				}
			});
			if (files == null || files.length == 0) {
				sender.sendMessage(ChatColor.GRAY + "No backups in " + outputDir);
				return true;
			}
			Arrays.sort(files, new java.util.Comparator<File>() {
				@Override public int compare(File a, File b) {
					return Long.compare(b.lastModified(), a.lastModified()); // newest first
				}
			});
			sender.sendMessage(ChatColor.YELLOW + "Backups (" + files.length + ", newest first):");
			int shown = Math.min(files.length, 15);
			for (int i = 0; i < shown; i++) {
				sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + files[i].getName()
						+ ChatColor.GRAY + "  (" + humanSize(files[i].length()) + ")");
			}
			if (files.length > shown) {
				sender.sendMessage(ChatColor.GRAY + "   ...and " + (files.length - shown) + " more.");
			}
			return true;
		}

		sender.sendMessage(ChatColor.YELLOW + "WorldBackup: /" + label + " <now|status|list|reload>");
		return true;
	}

	// --- Helpers -----------------------------------------------------------

	private static int clamp(int v, int lo, int hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static File canon(File f) {
		try {
			return f.getCanonicalFile();
		} catch (Exception e) {
			return f.getAbsoluteFile();
		}
	}

	private static String humanSize(long bytes) {
		if (bytes < 1024) return bytes + " B";
		double kb = bytes / 1024.0;
		if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb);
		double mb = kb / 1024.0;
		if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
		return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
	}
}
