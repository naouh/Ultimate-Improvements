package com.nao.randomtp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * /rtp - teleports a player to a random safe surface location in the world they are currently in.
 *
 * A random point is picked in a ring (min..max radius) around the world spawn, then the column is
 * probed for a safe standing spot (solid floor, two air blocks above, no lava/fire, optionally no
 * water). The Nether has no real "surface", so there the column is scanned upward for an air pocket.
 *
 * Configurable per-player cooldown and an optional warmup (cancelled if the player moves). See
 * config.yml. Everything is English-facing per project convention.
 */
public class RandomTpPlugin extends JavaPlugin implements Listener {

	private final Random random = new Random();
	private final Map<String, Long> cooldownUntil = new HashMap<String, Long>();
	private final Map<String, Pending> pending = new HashMap<String, Pending>();

	// Cached config values.
	private int cooldownSeconds;
	private int warmupSeconds;
	private int minRadius;
	private int maxRadius;
	private int maxAttempts;
	private boolean avoidWater;
	private List<String> worldsEnabled;
	private List<String> worldsDisabled;

	/** A teleport waiting out its warmup: the scheduler task and the block the player must stay on. */
	private static final class Pending {
		final int taskId;
		final int blockX;
		final int blockZ;
		Pending(int taskId, int blockX, int blockZ) {
			this.taskId = taskId;
			this.blockX = blockX;
			this.blockZ = blockZ;
		}
	}

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadConfigValues();
		getServer().getPluginManager().registerEvents(this, this);
		getLogger().info("RandomTP enabled.");
	}

	@Override
	public void onDisable() {
		getLogger().info("RandomTP disabled.");
	}

	private void loadConfigValues() {
		reloadConfig();
		cooldownSeconds = getConfig().getInt("cooldown-seconds", 120);
		warmupSeconds = getConfig().getInt("warmup-seconds", 3);
		minRadius = Math.max(0, getConfig().getInt("min-radius", 250));
		maxRadius = Math.max(minRadius + 1, getConfig().getInt("max-radius", 5000));
		maxAttempts = Math.max(1, getConfig().getInt("max-attempts", 50));
		avoidWater = getConfig().getBoolean("avoid-water", true);
		worldsEnabled = getConfig().getStringList("worlds.enabled");
		worldsDisabled = getConfig().getStringList("worlds.disabled");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equalsIgnoreCase("rtpreload")) {
			loadConfigValues();
			sender.sendMessage("§aRandomTP config reloaded.");
			return true;
		}
		if (!command.getName().equalsIgnoreCase("rtp")) return false;

		if (!(sender instanceof Player)) {
			sender.sendMessage("Only players can use /rtp.");
			return true;
		}
		Player player = (Player) sender;
		World world = player.getWorld();

		if (!isWorldAllowed(world)) {
			player.sendMessage("§c/rtp is disabled in this world.");
			return true;
		}
		if (pending.containsKey(player.getName())) {
			player.sendMessage("§cYou are already teleporting.");
			return true;
		}

		boolean bypass = player.hasPermission("rtp.bypass");
		if (!bypass) {
			long remaining = remainingCooldownMs(player.getName());
			if (remaining > 0) {
				player.sendMessage("§cYou must wait §e" + ((remaining + 999) / 1000) + "s§c before using /rtp again.");
				return true;
			}
		}

		Location dest = findSafeLocation(world);
		if (dest == null) {
			player.sendMessage("§cCouldn't find a safe spot. Try again.");
			return true;
		}
		// Keep the player's facing direction.
		dest.setYaw(player.getLocation().getYaw());
		dest.setPitch(player.getLocation().getPitch());

		int warmup = bypass ? 0 : warmupSeconds;
		if (warmup <= 0) {
			teleport(player, dest);
			return true;
		}

		player.sendMessage("§eTeleporting in §6" + warmup + "s§e... don't move.");
		Location from = player.getLocation();
		final String name = player.getName();
		final Player fPlayer = player;
		final Location fDest = dest;
		int taskId = getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			public void run() {
				pending.remove(name);
				if (fPlayer.isOnline()) {
					teleport(fPlayer, fDest);
				}
			}
		}, warmup * 20L);

		if (taskId == -1) {
			// Scheduling failed: just teleport now rather than leave the player hanging.
			teleport(player, dest);
		} else {
			pending.put(name, new Pending(taskId, from.getBlockX(), from.getBlockZ()));
		}
		return true;
	}

	private void teleport(Player player, Location dest) {
		player.teleport(dest);
		setCooldown(player.getName());
		player.sendMessage("§aTeleported to §f" + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ()
				+ " §7(" + dest.getWorld().getName() + ")");
	}

	// --- Cooldown bookkeeping ----------------------------------------------

	private long remainingCooldownMs(String name) {
		Long until = cooldownUntil.get(name);
		if (until == null) return 0;
		return Math.max(0, until - System.currentTimeMillis());
	}

	private void setCooldown(String name) {
		if (cooldownSeconds > 0) {
			cooldownUntil.put(name, System.currentTimeMillis() + cooldownSeconds * 1000L);
		}
	}

	// --- World filtering ---------------------------------------------------

	private boolean isWorldAllowed(World world) {
		String name = world.getName();
		if (worldsEnabled != null && !worldsEnabled.isEmpty()) {
			return worldsEnabled.contains(name);
		}
		return worldsDisabled == null || !worldsDisabled.contains(name);
	}

	// --- Safe-location search ----------------------------------------------

	/** Picks a random ring point around spawn and probes its column; null if none safe in maxAttempts. */
	private Location findSafeLocation(World world) {
		Location spawn = world.getSpawnLocation();
		int cx = spawn.getBlockX();
		int cz = spawn.getBlockZ();
		boolean nether = world.getEnvironment() == World.Environment.NETHER;

		for (int i = 0; i < maxAttempts; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			// Uniform over the ring area, not biased toward the center.
			double r = Math.sqrt(random.nextDouble() * (maxRadius * (double) maxRadius - minRadius * (double) minRadius)
					+ minRadius * (double) minRadius);
			int x = cx + (int) Math.round(Math.cos(angle) * r);
			int z = cz + (int) Math.round(Math.sin(angle) * r);

			Location loc = nether ? scanNetherColumn(world, x, z) : probeSurface(world, x, z);
			if (loc != null) return loc;
		}
		return null;
	}

	/** Overworld/End: stand on the highest solid block of the column, if that spot is safe. */
	private Location probeSurface(World world, int x, int z) {
		int y = world.getHighestBlockYAt(x, z);
		Block floor = world.getBlockAt(x, y, z);
		// getHighestBlockYAt may report the first air above terrain; walk down to the real floor.
		while (y > 1 && floor.isEmpty()) {
			y--;
			floor = world.getBlockAt(x, y, z);
		}
		return safeStandLocation(floor);
	}

	/** Nether: no sky, so scan upward for the first air pocket with a safe floor. */
	private Location scanNetherColumn(World world, int x, int z) {
		int top = Math.min(world.getMaxHeight() - 2, 120);
		for (int y = 6; y <= top; y++) {
			Location loc = safeStandLocation(world.getBlockAt(x, y, z));
			if (loc != null) return loc;
		}
		return null;
	}

	/** Returns a standing location on top of {@code floor} if floor is solid and the two blocks above are clear. */
	private Location safeStandLocation(Block floor) {
		if (floor.isEmpty()) return null;
		Material m = floor.getType();
		if (m == Material.FIRE || m == Material.CACTUS) return null;
		if (m == Material.LAVA || m == Material.STATIONARY_LAVA) return null;
		if (floor.isLiquid() && avoidWater) return null; // remaining liquids are water

		Block feet = floor.getRelative(0, 1, 0);
		Block head = floor.getRelative(0, 2, 0);
		if (!feet.isEmpty() || !head.isEmpty()) return null;

		World w = floor.getWorld();
		return new Location(w, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
	}

	// --- Warmup cancellation ------------------------------------------------

	@EventHandler
	public void onMove(PlayerMoveEvent event) {
		Pending p = pending.get(event.getPlayer().getName());
		if (p == null) return;
		Location to = event.getTo();
		if (to.getBlockX() != p.blockX || to.getBlockZ() != p.blockZ) {
			getServer().getScheduler().cancelTask(p.taskId);
			pending.remove(event.getPlayer().getName());
			event.getPlayer().sendMessage("§cTeleport cancelled - you moved.");
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Pending p = pending.remove(event.getPlayer().getName());
		if (p != null) {
			getServer().getScheduler().cancelTask(p.taskId);
		}
	}
}
