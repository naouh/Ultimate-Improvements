package com.nao.automessages;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * AutoMessages - periodic chat announcements for MCPC+ 1.4.7.
 *
 * <p>A repeating task cycles through a configured list of messages and shows the next one to every
 * online player, in sequential or random order, every N minutes. Each player can silence the
 * announcements for themselves with {@code /automsg off} (or just {@code /automsg} to toggle); that
 * choice is persisted to {@code data.yml} (keyed by lowercase player name, since the server runs in
 * offline/cracked mode) so it survives reconnects and restarts.
 *
 * <p>Everything is English-facing per project convention; the announcement texts are configurable.
 */
public final class AutoMessagesPlugin extends JavaPlugin {

	// --- Config (cached) ---------------------------------------------------
	private final List<String> messages = new ArrayList<String>();
	private String prefix;
	private long intervalTicks;
	private boolean randomOrder;

	// --- Runtime state -----------------------------------------------------
	/** Lowercase names of players who have opted OUT of auto-messages. */
	private final Set<String> optedOut = new HashSet<String>();
	private final Random rng = new Random();
	private int taskId = -1;
	private int index;          // next message for sequential order
	private int lastSent = -1;  // last index shown, to avoid back-to-back repeats in random order
	private File dataFile;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		dataFile = new File(getDataFolder(), "data.yml");
		loadConfigValues();
		loadData();
		schedule();
		getLogger().info("AutoMessages enabled (" + messages.size() + " messages, every "
				+ String.format(java.util.Locale.US, "%.1f", intervalTicks / 1200.0) + " min, "
				+ (randomOrder ? "random" : "sequential") + ", " + optedOut.size() + " opted out).");
	}

	@Override
	public void onDisable() {
		saveData();
		getLogger().info("AutoMessages disabled.");
	}

	// --- Config ------------------------------------------------------------

	private void loadConfigValues() {
		reloadConfig();
		FileConfiguration c = getConfig();
		messages.clear();
		messages.addAll(c.getStringList("messages"));
		prefix = c.getString("prefix", "&8[&bInfo&8]&r ");
		randomOrder = c.getString("order", "sequential").equalsIgnoreCase("random");

		double minutes = c.getDouble("interval-minutes", 5.0);
		long ticks = Math.round(minutes * 1200.0); // 1 min = 1200 ticks
		intervalTicks = Math.max(100L, ticks);     // floor at 5s so it can't spam the chat
		index = 0;
		lastSent = -1;
	}

	private void schedule() {
		if (taskId != -1) {
			getServer().getScheduler().cancelTask(taskId);
			taskId = -1;
		}
		if (messages.isEmpty() || intervalTicks <= 0) return;
		taskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override public void run() { broadcastNext(); }
		}, intervalTicks, intervalTicks);
	}

	// --- Broadcasting ------------------------------------------------------

	/** Shows the next message to every online player who hasn't opted out. */
	private void broadcastNext() {
		if (messages.isEmpty()) return;
		Player[] online = getServer().getOnlinePlayers();
		if (online.length == 0) return; // nobody to tell; don't advance the rotation either

		String raw = nextMessage();
		String full = ChatColor.translateAlternateColorCodes('&', prefix + raw);
		// Support multi-line entries written with "\n".
		String[] lines = full.split("\n", -1);

		for (Player p : online) {
			if (optedOut.contains(p.getName().toLowerCase())) continue;
			for (String line : lines) p.sendMessage(line);
		}
	}

	/** Picks the next message text, advancing sequential order or rolling a non-repeating random one. */
	private String nextMessage() {
		int size = messages.size();
		if (randomOrder) {
			int pick = rng.nextInt(size);
			if (size > 1 && pick == lastSent) pick = (pick + 1) % size; // no immediate repeat
			lastSent = pick;
			return messages.get(pick);
		}
		if (index >= size) index = 0;
		String msg = messages.get(index);
		lastSent = index;
		index = (index + 1) % size;
		return msg;
	}

	// --- Per-player opt-out ------------------------------------------------

	/** Applies a new state for the player ({@code wantOn}=null toggles) and persists it. */
	private void applyState(Player p, Boolean wantOn) {
		String key = p.getName().toLowerCase();
		boolean currentlyOn = !optedOut.contains(key);
		boolean on = (wantOn == null) ? !currentlyOn : wantOn;
		if (on) optedOut.remove(key);
		else optedOut.add(key);
		saveData();
		if (on) {
			p.sendMessage(ChatColor.GREEN + "Auto-messages are now ON - you'll see periodic announcements.");
		} else {
			p.sendMessage(ChatColor.GRAY + "Auto-messages are now OFF. Use "
					+ ChatColor.WHITE + "/automsg on" + ChatColor.GRAY + " to bring them back.");
		}
	}

	// --- Persistence -------------------------------------------------------

	private void loadData() {
		optedOut.clear();
		if (!dataFile.exists()) return;
		YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
		for (String name : y.getStringList("opted-out")) {
			if (name != null && !name.trim().isEmpty()) optedOut.add(name.trim().toLowerCase());
		}
	}

	private void saveData() {
		YamlConfiguration y = new YamlConfiguration();
		y.set("opted-out", new ArrayList<String>(optedOut));
		try {
			if (!getDataFolder().exists()) getDataFolder().mkdirs();
			y.save(dataFile);
		} catch (IOException ex) {
			getLogger().warning("AutoMessages could not save data.yml: " + ex.getMessage());
		}
	}

	// --- Command -----------------------------------------------------------

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length > 0 ? args[0].toLowerCase() : "toggle";

		// Player-facing controls.
		if (sub.equals("toggle") || sub.equals("on") || sub.equals("off")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "Only a player can toggle their own auto-messages.");
				return true;
			}
			if (!sender.hasPermission("automessages.toggle")) {
				sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
				return true;
			}
			Boolean want = sub.equals("toggle") ? null : Boolean.valueOf(sub.equals("on"));
			applyState((Player) sender, want);
			return true;
		}

		// Admin controls.
		if (sub.equals("reload")) {
			if (notAdmin(sender)) return true;
			loadConfigValues();
			schedule();
			sender.sendMessage(ChatColor.GREEN + "AutoMessages reloaded: " + messages.size() + " messages, every "
					+ String.format(java.util.Locale.US, "%.1f", intervalTicks / 1200.0) + " min, "
					+ (randomOrder ? "random" : "sequential") + ".");
			return true;
		}
		if (sub.equals("status")) {
			if (notAdmin(sender)) return true;
			sender.sendMessage(ChatColor.YELLOW + "AutoMessages status:");
			sender.sendMessage(ChatColor.GRAY + " - messages: " + ChatColor.WHITE + messages.size());
			sender.sendMessage(ChatColor.GRAY + " - interval: " + ChatColor.WHITE
					+ String.format(java.util.Locale.US, "%.1f", intervalTicks / 1200.0) + " min");
			sender.sendMessage(ChatColor.GRAY + " - order: " + ChatColor.WHITE + (randomOrder ? "random" : "sequential"));
			sender.sendMessage(ChatColor.GRAY + " - running: " + ChatColor.WHITE + (taskId != -1));
			sender.sendMessage(ChatColor.GRAY + " - opted out: " + ChatColor.WHITE + optedOut.size() + " players");
			return true;
		}
		if (sub.equals("list")) {
			if (notAdmin(sender)) return true;
			if (messages.isEmpty()) {
				sender.sendMessage(ChatColor.GRAY + "No messages configured.");
				return true;
			}
			sender.sendMessage(ChatColor.YELLOW + "AutoMessages (" + messages.size() + "):");
			for (int i = 0; i < messages.size(); i++) {
				sender.sendMessage(ChatColor.GRAY + " " + i + ": "
						+ ChatColor.translateAlternateColorCodes('&', prefix + messages.get(i)));
			}
			return true;
		}
		if (sub.equals("next")) { // force-send now, handy for testing
			if (notAdmin(sender)) return true;
			broadcastNext();
			sender.sendMessage(ChatColor.GREEN + "Sent the next auto-message.");
			return true;
		}

		sender.sendMessage(ChatColor.YELLOW + "AutoMessages: " + ChatColor.WHITE + "/" + label
				+ " <on|off|toggle>" + ChatColor.GRAY + " (admin: reload, status, list, next)");
		return true;
	}

	/** True (and warns) if the sender lacks the admin permission. */
	private boolean notAdmin(CommandSender sender) {
		if (sender.hasPermission("automessages.admin")) return false;
		sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
		return true;
	}
}
