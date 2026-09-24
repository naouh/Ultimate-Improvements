package com.nao.clearlag;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ClearLag (nao) - a clean entity cleaner for MCPC+ 1.4.7, built to replace bob7l's ClearLag with
 * three deliberate differences the original lacks:
 *
 * <ol>
 *   <li><b>Ground items near players are kept.</b> A dropped item (or XP orb) within a configurable
 *       radius of any player is never removed, so nobody loses a fresh drop mid-pickup.</li>
 *   <li><b>Animals are never touched.</b> Passive mobs, tamed pets, villagers, golems and bosses are
 *       protected, so player farms/breeders survive.</li>
 *   <li><b>Modded mobs are handled.</b> Hostility is detected via the Minecraft {@code IMob}
 *       interface at runtime (see {@link EntityClassifier}), not a hard-coded vanilla list.</li>
 * </ol>
 *
 * <p>Policy: only entities we can <em>positively</em> identify as hostile or as removable junk are
 * deleted - anything unclassified is left alone. Runs on the main thread (sync scheduler task), which
 * is the safe place to mutate entities under TickThreading.
 *
 * <p>Also provides a lag report (<code>/clearlag check</code>) and polite chunk unloading
 * (<code>/clearlag unloadchunks</code>). English-facing per project convention.
 */
public final class ClearLagPlugin extends JavaPlugin {

	private EntityClassifier classifier;
	private final TpsMeter tps = new TpsMeter();

	// --- Config ------------------------------------------------------------
	private int intervalMinutes;
	private int warnSeconds;
	private boolean broadcast;
	private Set<String> worldFilter;       // lowercase; empty = all worlds

	private double itemRadius;
	private boolean protectAnimals, protectTamed, protectVillagers, protectGolems, protectBosses, protectRidden;
	private Set<String> protectTypes;      // lowercase entity type / class names

	private boolean rmMonsters, rmItems, rmXp, rmArrows, rmFalling, rmBoats, rmMinecarts, rmProjectiles;
	private int keepRadiusChunks;
	private String msgWarn, msgCleared;

	private int autoTaskId = -1;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		classifier = new EntityClassifier(getLogger());
		loadConfigValues();
		tps.start(this);
		scheduleAuto();
		getLogger().info("ClearLag enabled (auto every "
				+ (intervalMinutes > 0 ? intervalMinutes + " min" : "OFF")
				+ ", item-protect radius " + (int) itemRadius + ").");
	}

	@Override
	public void onDisable() {
		tps.stop(this);
		getLogger().info("ClearLag disabled.");
	}

	// --- Config ------------------------------------------------------------

	private void loadConfigValues() {
		reloadConfig();
		FileConfiguration c = getConfig();

		intervalMinutes = c.getInt("auto.interval-minutes", 20);
		warnSeconds = Math.max(0, c.getInt("auto.warn-seconds", 30));
		broadcast = c.getBoolean("auto.broadcast", true);
		worldFilter = lowerSet(c.getStringList("auto.worlds"));

		itemRadius = Math.max(0, c.getDouble("protect.item-radius", 8.0));
		protectAnimals = c.getBoolean("protect.animals", true);
		protectTamed = c.getBoolean("protect.tamed", true);
		protectVillagers = c.getBoolean("protect.villagers", true);
		protectGolems = c.getBoolean("protect.golems", true);
		protectBosses = c.getBoolean("protect.bosses", true);
		protectRidden = c.getBoolean("protect.ridden", true);
		protectTypes = lowerSet(c.getStringList("protect.entity-types"));

		rmMonsters = c.getBoolean("remove.monsters", true);
		rmItems = c.getBoolean("remove.items", true);
		rmXp = c.getBoolean("remove.xp-orbs", false);
		rmArrows = c.getBoolean("remove.arrows", true);
		rmFalling = c.getBoolean("remove.falling-blocks", true);
		rmBoats = c.getBoolean("remove.boats", false);
		rmMinecarts = c.getBoolean("remove.minecarts", false);
		rmProjectiles = c.getBoolean("remove.projectiles", true);

		keepRadiusChunks = Math.max(0, c.getInt("unloadchunks.keep-radius-chunks", 4));

		msgWarn = c.getString("messages.warn", "&e[ClearLag] &7Clearing ground items & hostile mobs in &f{seconds}s&7...");
		msgCleared = c.getString("messages.cleared", "&e[ClearLag] &aRemoved &f{count}&a entities &7(&f{mobs}&7 mobs, &f{items}&7 items).");
	}

	private static Set<String> lowerSet(List<String> in) {
		Set<String> s = new HashSet<String>();
		if (in != null) for (String v : in) if (v != null && !v.trim().isEmpty()) s.add(v.trim().toLowerCase(Locale.US));
		return s;
	}

	private boolean worldAllowed(World w) {
		return worldFilter.isEmpty() || worldFilter.contains(w.getName().toLowerCase(Locale.US));
	}

	// --- Scheduling --------------------------------------------------------

	private void scheduleAuto() {
		if (autoTaskId != -1) {
			getServer().getScheduler().cancelTask(autoTaskId);
			autoTaskId = -1;
		}
		if (intervalMinutes <= 0) return;
		long period = (long) intervalMinutes * 60L * 20L; // minutes -> ticks
		autoTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override public void run() { startCycle(); }
		}, period, period);
	}

	/** One scheduled cycle: optionally warn, then clear after the warning delay. */
	private void startCycle() {
		if (warnSeconds > 0) {
			announce(msgWarn.replace("{seconds}", Integer.toString(warnSeconds)));
			getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				@Override public void run() { doClear(true); }
			}, (long) warnSeconds * 20L);
		} else {
			doClear(true);
		}
	}

	// --- Clearing ----------------------------------------------------------

	/** Sweeps all allowed worlds, removing hostile mobs + junk while keeping protected entities. */
	private int[] doClear(boolean announce) {
		int mobs = 0, items = 0;
		for (World w : getServer().getWorlds()) {
			if (!worldAllowed(w)) continue;

			// Snapshot player positions once per world so item-radius checks are cheap.
			List<Location> playerLocs = new ArrayList<Location>();
			for (Player p : w.getPlayers()) playerLocs.add(p.getLocation());

			for (Entity e : w.getEntities()) {
				int cat = removalCategory(e, playerLocs);
				if (cat == 0) continue;
				e.remove();
				if (cat == 1) mobs++; else items++;
			}
		}
		int total = mobs + items;
		if (announce && total > 0) {
			announce(msgCleared
					.replace("{count}", Integer.toString(total))
					.replace("{mobs}", Integer.toString(mobs))
					.replace("{items}", Integer.toString(items)));
		} else {
			getLogger().info("ClearLag removed " + total + " entities (" + mobs + " mobs, " + items + " items).");
		}
		return new int[] { mobs, items };
	}

	/** 0 = keep, 1 = remove as a mob, 2 = remove as an item/junk entity. */
	private int removalCategory(Entity e, List<Location> playerLocs) {
		if (e instanceof Player) return 0;

		// --- protections (checked first; a protected entity is never removed) ---
		if (protectAnimals && classifier.isAnimal(e)) return 0;
		if (protectTamed && e instanceof Tameable && ((Tameable) e).isTamed()) return 0;
		if (protectVillagers && e instanceof Villager) return 0;
		if (protectGolems && e instanceof Golem) return 0;
		if (protectBosses && classifier.isBoss(e)) return 0;
		if (protectRidden && e.getPassenger() != null) return 0; // occupied mount / vehicle
		if (!protectTypes.isEmpty() && isProtectedType(e)) return 0;

		// --- removable junk (vanilla types that also cover their modded subclasses) ---
		if (e instanceof Item) return (rmItems && !nearPlayer(e, playerLocs)) ? 2 : 0;
		if (e instanceof ExperienceOrb) return (rmXp && !nearPlayer(e, playerLocs)) ? 2 : 0;
		if (e instanceof Arrow) return rmArrows ? 2 : 0;            // Arrow before Projectile (own toggle)
		if (e instanceof FallingBlock) return rmFalling ? 2 : 0;
		if (e instanceof Boat) return rmBoats ? 2 : 0;
		if (e instanceof Minecart) return rmMinecarts ? 2 : 0;
		if (e instanceof Projectile) return rmProjectiles ? 2 : 0; // snowball, egg, thrown potion, fireball...

		// --- hostile mobs (vanilla + modded via IMob) ---
		if (rmMonsters && classifier.isHostile(e)) return 1;
		return 0;
	}

	private boolean isProtectedType(Entity e) {
		String type;
		try {
			type = e.getType() != null ? e.getType().name().toLowerCase(Locale.US) : null;
		} catch (Throwable t) {
			type = null;
		}
		if (type != null && protectTypes.contains(type)) return true;
		// Modded entities often report UNKNOWN/odd types - also match the class name.
		return protectTypes.contains(e.getClass().getSimpleName().toLowerCase(Locale.US));
	}

	/** True if any player is within {@link #itemRadius} blocks of the entity (same world). */
	private boolean nearPlayer(Entity e, List<Location> playerLocs) {
		if (itemRadius <= 0 || playerLocs.isEmpty()) return false;
		double r2 = itemRadius * itemRadius;
		Location el = e.getLocation();
		for (Location pl : playerLocs) {
			double dx = pl.getX() - el.getX();
			double dy = pl.getY() - el.getY();
			double dz = pl.getZ() - el.getZ();
			if (dx * dx + dy * dy + dz * dz <= r2) return true;
		}
		return false;
	}

	// --- Chunk unloading ---------------------------------------------------

	/** Politely requests unload of chunks with no players (and not near spawn) in the given worlds. */
	private int unloadChunks(List<World> worlds) {
		int requested = 0;
		for (World w : worlds) {
			Chunk spawn = w.getSpawnLocation().getChunk();
			// Collect player chunk coords so we can keep a radius around each player loaded.
			List<int[]> keep = new ArrayList<int[]>();
			for (Player p : w.getPlayers()) {
				Chunk pc = p.getLocation().getChunk();
				keep.add(new int[] { pc.getX(), pc.getZ() });
			}
			for (Chunk ch : w.getLoadedChunks()) {
				if (isNearSpawn(ch, spawn) || isNearAny(ch, keep)) continue;
				if (w.unloadChunkRequest(ch.getX(), ch.getZ())) requested++;
			}
		}
		return requested;
	}

	private boolean isNearSpawn(Chunk ch, Chunk spawn) {
		return Math.abs(ch.getX() - spawn.getX()) <= keepRadiusChunks
				&& Math.abs(ch.getZ() - spawn.getZ()) <= keepRadiusChunks;
	}

	private boolean isNearAny(Chunk ch, List<int[]> keep) {
		for (int[] k : keep) {
			if (Math.abs(ch.getX() - k[0]) <= keepRadiusChunks && Math.abs(ch.getZ() - k[1]) <= keepRadiusChunks) {
				return true;
			}
		}
		return false;
	}

	// --- Lag report --------------------------------------------------------

	private void sendLagReport(CommandSender to) {
		double cur = tps.current();
		double avg = tps.averageOneMinute();
		to.sendMessage(ChatColor.YELLOW + "=== ClearLag report ===");
		to.sendMessage(ChatColor.GRAY + "TPS: " + tpsColour(cur) + fmt1(cur)
				+ ChatColor.GRAY + " (1-min avg " + tpsColour(avg) + fmt1(avg) + ChatColor.GRAY + ", 20 = perfect)");

		int totalEntities = 0, totalChunks = 0, totalPlayers = 0;
		// Tally entity types across all worlds to surface the biggest lag sources.
		Map<String, Integer> typeCounts = new HashMap<String, Integer>();
		for (World w : getServer().getWorlds()) {
			List<Entity> ents = w.getEntities();
			int chunks = w.getLoadedChunks().length;
			int players = w.getPlayers().size();
			totalEntities += ents.size();
			totalChunks += chunks;
			totalPlayers += players;
			for (Entity e : ents) {
				String key = typeName(e);
				Integer prev = typeCounts.get(key);
				typeCounts.put(key, prev == null ? 1 : prev + 1);
			}
			to.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + w.getName() + ChatColor.GRAY + ": "
					+ ents.size() + " entities, " + chunks + " chunks, " + players + " players");
		}
		to.sendMessage(ChatColor.GRAY + "Totals: " + ChatColor.WHITE + totalEntities + ChatColor.GRAY
				+ " entities, " + ChatColor.WHITE + totalChunks + ChatColor.GRAY + " chunks, "
				+ ChatColor.WHITE + totalPlayers + ChatColor.GRAY + " players.");

		// Top 5 entity types.
		List<Map.Entry<String, Integer>> top = new ArrayList<Map.Entry<String, Integer>>(typeCounts.entrySet());
		top.sort(new java.util.Comparator<Map.Entry<String, Integer>>() {
			@Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
				return b.getValue() - a.getValue();
			}
		});
		StringBuilder sb = new StringBuilder(ChatColor.GRAY + "Top types: ");
		for (int i = 0; i < Math.min(5, top.size()); i++) {
			if (i > 0) sb.append(ChatColor.GRAY).append(", ");
			sb.append(ChatColor.WHITE).append(top.get(i).getKey())
					.append(ChatColor.GRAY).append(" x").append(top.get(i).getValue());
		}
		if (!top.isEmpty()) to.sendMessage(sb.toString());
	}

	private static String typeName(Entity e) {
		try {
			if (e.getType() != null) return e.getType().name();
		} catch (Throwable ignored) {}
		return e.getClass().getSimpleName();
	}

	private static ChatColor tpsColour(double t) {
		if (t >= 18.0) return ChatColor.GREEN;
		if (t >= 14.0) return ChatColor.YELLOW;
		return ChatColor.RED;
	}

	private static String fmt1(double d) {
		return String.format(Locale.US, "%.1f", d);
	}

	// --- Messaging ---------------------------------------------------------

	private void announce(String raw) {
		String colored = ChatColor.translateAlternateColorCodes('&', raw);
		getLogger().info(ChatColor.stripColor(colored));
		if (broadcast) getServer().broadcastMessage(colored);
	}

	// --- Command -----------------------------------------------------------

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length > 0 ? args[0].toLowerCase(Locale.US) : "help";

		if (sub.equals("clear") || sub.equals("now")) {
			if (noPerm(sender, "clearlag.clear")) return true;
			int[] r = doClear(false);
			sender.sendMessage(ChatColor.GREEN + "ClearLag removed " + (r[0] + r[1]) + " entities ("
					+ r[0] + " mobs, " + r[1] + " items).");
			return true;
		}
		if (sub.equals("check") || sub.equals("tps")) {
			if (noPerm(sender, "clearlag.check")) return true;
			sendLagReport(sender);
			return true;
		}
		if (sub.equals("unloadchunks") || sub.equals("unload")) {
			if (noPerm(sender, "clearlag.unload")) return true;
			List<World> worlds = new ArrayList<World>();
			if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
				World w = getServer().getWorld(args[1]);
				if (w == null) {
					sender.sendMessage(ChatColor.RED + "No such world: " + args[1]);
					return true;
				}
				worlds.add(w);
			} else {
				worlds.addAll(getServer().getWorlds());
			}
			int n = unloadChunks(worlds);
			sender.sendMessage(ChatColor.GREEN + "ClearLag requested unload of " + n
					+ " chunk(s) (the server keeps any still in use).");
			return true;
		}
		if (sub.equals("reload")) {
			if (noPerm(sender, "clearlag.admin")) return true;
			loadConfigValues();
			scheduleAuto();
			sender.sendMessage(ChatColor.GREEN + "ClearLag config reloaded (auto every "
					+ (intervalMinutes > 0 ? intervalMinutes + " min" : "OFF") + ").");
			return true;
		}

		sender.sendMessage(ChatColor.YELLOW + "ClearLag commands:");
		sender.sendMessage(ChatColor.GRAY + " /" + label + " clear " + ChatColor.DARK_GRAY + "- remove entities now");
		sender.sendMessage(ChatColor.GRAY + " /" + label + " check " + ChatColor.DARK_GRAY + "- TPS + entity/chunk report");
		sender.sendMessage(ChatColor.GRAY + " /" + label + " unloadchunks [world|all] " + ChatColor.DARK_GRAY + "- free unused chunks");
		sender.sendMessage(ChatColor.GRAY + " /" + label + " reload " + ChatColor.DARK_GRAY + "- reload config");
		return true;
	}

	private boolean noPerm(CommandSender sender, String perm) {
		if (sender.hasPermission(perm)) return false;
		sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
		return true;
	}
}
