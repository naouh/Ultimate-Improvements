package com.nao.crackauth;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CrackAuth - minimal offline-mode (cracked) login for MCPC+ 1.4.7.
 *
 * <p>The server runs in offline mode, so anyone can connect under any name and there is no Mojang
 * check to prove they own it. CrackAuth closes that hole: the first time a name connects it must
 * {@code /register} a password, and every connection after that it must {@code /login} with it.
 *
 * <p>Until a player is authenticated they are <b>frozen</b> rather than having their inventory wiped
 * (the approach AuthMe takes, which is risky with modded item NBT): an unauthenticated player can't
 * move, interact, break/place blocks, touch their inventory, drop/pick up items, chat, take or deal
 * damage, or run any command other than {@code /login} / {@code /register}. This keeps an
 * impersonator completely inert without ever touching the real owner's items.
 *
 * <p>Passwords are stored in {@code accounts.yml} as salted PBKDF2-WithHmacSHA256 hashes (JDK only,
 * no external dependency); the plain password is never written anywhere. Accounts are keyed by
 * lowercase name, since in 1.4.7 (pre-UUID) players are identified by name.
 *
 * <p>All player-facing text is English per project convention.
 */
public final class CrackAuthPlugin extends JavaPlugin implements Listener {

	/** Commands an unauthenticated player is still allowed to run. */
	private static final Set<String> ALLOWED_UNAUTHED_COMMANDS =
			new HashSet<String>(Arrays.asList("login", "l", "register", "reg"));

	// --- Crypto tuning -----------------------------------------------------
	private static final int PBKDF2_ITERATIONS = 64000;
	private static final int PBKDF2_KEY_BITS = 256;
	private static final int SALT_BYTES = 16;
	private static final String HASH_SCHEME = "pbkdf2_sha256";

	// --- Runtime state -----------------------------------------------------
	/** Lowercase names of players who have logged in this session. */
	private final Set<String> authenticated = new HashSet<String>();
	/** Lowercase name -> consecutive failed /login attempts. */
	private final Map<String, Integer> failedAttempts = new HashMap<String, Integer>();
	/** Lowercase name -> scheduler id of the pending login-timeout kick. */
	private final Map<String, Integer> timeoutTasks = new HashMap<String, Integer>();
	private final SecureRandom secureRandom = new SecureRandom();

	private File accountsFile;
	private YamlConfiguration accounts;
	private int reminderTaskId = -1;
	private int enforceTaskId = -1;

	// --- Optional CrackAuthCore coremod bridge (mod-packet gate) ------------
	private boolean gateInit;
	private boolean gateClassFound;
	private java.lang.reflect.Method gateLockMethod;
	private java.lang.reflect.Method gateUnlockMethod;
	private java.lang.reflect.Method gateClearMethod;
	private java.lang.reflect.Method gateInstalledMethod;
	private java.lang.reflect.Method gateDropCountMethod;
	private java.lang.reflect.Method gateLastDecisionMethod;
	private java.lang.reflect.Method gateDiagMethod;

	// --- Config (cached) ---------------------------------------------------
	private int minPasswordLength;
	private int maxAttempts;
	private int timeoutSeconds;
	private boolean blindness;
	private long reminderIntervalTicks;
	private String prefix;

	// =======================================================================
	// Lifecycle
	// =======================================================================

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadConfigValues();
		accountsFile = new File(getDataFolder(), "accounts.yml");
		accounts = YamlConfiguration.loadConfiguration(accountsFile);

		getServer().getPluginManager().registerEvents(this, this);
		scheduleReminder();
		scheduleEnforcement();
		initGate();
		installLogRedaction();

		// If the plugin is enabled while players are already online (e.g. a /reload), trust nobody:
		// every online player must re-authenticate before they can act again.
		for (Player p : getServer().getOnlinePlayers()) {
			beginAuth(p);
		}

		getLogger().info("CrackAuth enabled (" + countAccounts() + " registered accounts).");
	}

	@Override
	public void onDisable() {
		saveAccounts();
		if (reminderTaskId != -1) {
			getServer().getScheduler().cancelTask(reminderTaskId);
			reminderTaskId = -1;
		}
		if (enforceTaskId != -1) {
			getServer().getScheduler().cancelTask(enforceTaskId);
			enforceTaskId = -1;
		}
		invokeGate(gateClearMethod); // release the packet gate for everyone
		getLogger().info("CrackAuth disabled.");
	}

	private void loadConfigValues() {
		reloadConfig();
		FileConfiguration c = getConfig();
		minPasswordLength = Math.max(1, c.getInt("min-password-length", 4));
		maxAttempts = Math.max(0, c.getInt("max-login-attempts", 5));
		timeoutSeconds = Math.max(0, c.getInt("login-timeout-seconds", 60));
		blindness = c.getBoolean("blindness", true);
		reminderIntervalTicks = Math.max(0, c.getInt("reminder-interval-seconds", 8)) * 20L;
		prefix = c.getString("message-prefix", "&8[&bAuth&8]&r ");
	}

	// =======================================================================
	// Auth flow
	// =======================================================================

	/** Starts the login/register prompt for a player: freezes them and arms the timeout kick. */
	private void beginAuth(final Player p) {
		final String key = key(p);
		authenticated.remove(key);
		failedAttempts.remove(key);
		invokeGate(gateLockMethod, p.getName()); // drop this player's mod packets until they log in

		if (blindness) {
			// Big duration; it's cleared the moment they log in (see finishAuth).
			p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1000000, 0), true);
		}

		if (isRegistered(key)) {
			send(p, "&eThis name is protected. Log in with &f/login <password>");
		} else {
			send(p, "&eWelcome! Protect your name now: &f/register <password> <password>");
		}

		if (timeoutSeconds > 0) {
			int id = getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				@Override public void run() {
					if (p.isOnline() && !isAuthed(p)) {
						p.kickPlayer(color(prefix + "&cLogin timed out. Please reconnect and log in."));
					}
				}
			}, timeoutSeconds * 20L);
			timeoutTasks.put(key, id);
		}
	}

	/** Marks the player authenticated, unfreezes them and clears their timeout. */
	private void finishAuth(Player p) {
		String key = key(p);
		authenticated.add(key);
		failedAttempts.remove(key);
		cancelTimeout(key);
		invokeGate(gateUnlockMethod, p.getName()); // mod packets flow again
		if (blindness) {
			p.removePotionEffect(PotionEffectType.BLINDNESS);
		}

		accounts.set("accounts." + key + ".lastlogin", System.currentTimeMillis());
		if (p.getAddress() != null && p.getAddress().getAddress() != null) {
			accounts.set("accounts." + key + ".ip", p.getAddress().getAddress().getHostAddress());
		}
		saveAccounts();

		send(p, "&aLogged in. Welcome, " + p.getName() + "!");
	}

	/** Removes all per-player state when someone disconnects. */
	private void cleanup(Player p) {
		String key = key(p);
		authenticated.remove(key);
		failedAttempts.remove(key);
		cancelTimeout(key);
		invokeGate(gateUnlockMethod, p.getName()); // keep the gate's locked set clean
	}

	// =======================================================================
	// CrackAuthCore coremod bridge (optional packet gate)
	// =======================================================================

	/**
	 * Links to the optional {@code CrackAuthCore} coremod, which ASM-drops inbound mod packets from
	 * locked players (closing the mod-keybind hole that a plugin can't reach). Resolved by reflection
	 * because the plugin is compiled only against the server API, not the coremod. If the coremod
	 * isn't installed we just run without the gate - all the plugin-level protections stay in effect.
	 */
	private void initGate() {
		gateInit = true;
		Class<?> gate = null;
		ClassLoader[] loaders = {
				getClass().getClassLoader(),
				Thread.currentThread().getContextClassLoader(),
				ClassLoader.getSystemClassLoader()
		};
		for (ClassLoader cl : loaders) {
			if (cl == null) continue;
			try {
				gate = Class.forName("com.nao.crackauthcore.CrackAuthGate", true, cl);
				break;
			} catch (Throwable ignored) {
				// try the next classloader
			}
		}
		if (gate == null) {
			getLogger().warning("CrackAuthCore coremod not found - the mod-keybind packet gate is OFF "
					+ "(drop crackauthcore into coremods/ to enable it). Plugin protections remain active.");
			return;
		}
		gateClassFound = true;
		try {
			gateLockMethod = gate.getMethod("lock", String.class);
			gateUnlockMethod = gate.getMethod("unlock", String.class);
			gateClearMethod = gate.getMethod("clear");
			gateInstalledMethod = gate.getMethod("isInstalled");
			gateDropCountMethod = gate.getMethod("getDropCount");
			gateLastDecisionMethod = gate.getMethod("getLastDecision");
			gateDiagMethod = gate.getMethod("getDiag");
			getLogger().info("CrackAuthCore packet gate linked - inbound mod packets are blocked until login.");
		} catch (Throwable t) {
			getLogger().warning("CrackAuthCore found but its API didn't match: " + t);
		}
	}

	/**
	 * Installs {@link PasswordLogFilter} so {@code /login}/{@code /register}/{@code /changepassword}
	 * arguments don't appear in plain text in the server console. The filter is chained onto every
	 * handler (and the loggers themselves) along the root / "Minecraft" / "ForgeModLoader" / server-logger
	 * chains, so it catches the "issued server command" echo wherever it's emitted.
	 */
	private void installLogRedaction() {
		if (!getConfig().getBoolean("redact-passwords-in-log", true)) return;
		try {
			java.util.Set<java.util.logging.Logger> seenLoggers = new java.util.HashSet<java.util.logging.Logger>();
			java.util.Set<java.util.logging.Handler> seenHandlers = new java.util.HashSet<java.util.logging.Handler>();
			java.util.logging.Logger[] roots = {
					java.util.logging.Logger.getLogger(""),
					java.util.logging.Logger.getLogger("Minecraft"),
					java.util.logging.Logger.getLogger("ForgeModLoader"),
					getServer().getLogger()
			};
			int handlers = 0;
			for (java.util.logging.Logger root : roots) {
				for (java.util.logging.Logger cur = root; cur != null; cur = cur.getParent()) {
					if (seenLoggers.add(cur)) {
						cur.setFilter(new PasswordLogFilter(cur.getFilter()));
					}
					for (java.util.logging.Handler h : cur.getHandlers()) {
						if (seenHandlers.add(h)) {
							h.setFilter(new PasswordLogFilter(h.getFilter()));
							handlers++;
						}
					}
				}
			}
			getLogger().info("CrackAuth: password log redaction installed (" + handlers + " handler(s)).");
		} catch (Throwable t) {
			getLogger().warning("CrackAuth: could not install password log redaction: " + t);
		}
	}

	/** True if the coremod's transformer actually patched NetServerHandler (and shares our class copy). */
	private boolean gateInstalled() {
		if (gateInstalledMethod == null) return false;
		try {
			Object r = gateInstalledMethod.invoke(null);
			return r instanceof Boolean && (Boolean) r;
		} catch (Throwable t) {
			return false;
		}
	}

	private int gateDropCount() {
		if (gateDropCountMethod == null) return 0;
		try {
			Object r = gateDropCountMethod.invoke(null);
			return r instanceof Integer ? (Integer) r : 0;
		} catch (Throwable t) {
			return 0;
		}
	}

	private String gateLastDecision() {
		if (gateLastDecisionMethod == null) return "n/a";
		try {
			Object r = gateLastDecisionMethod.invoke(null);
			return r == null ? "n/a" : r.toString();
		} catch (Throwable t) {
			return "n/a";
		}
	}

	private String gateDiag() {
		if (gateDiagMethod == null) return "n/a";
		try {
			Object r = gateDiagMethod.invoke(null);
			return r == null ? "n/a" : r.toString();
		} catch (Throwable t) {
			return "n/a";
		}
	}

	private void invokeGate(java.lang.reflect.Method method, Object... args) {
		if (!gateInit) initGate();
		if (method == null) return;
		try {
			method.invoke(null, args);
		} catch (Throwable ignored) {
			// gate is best-effort; never let a bridge error affect auth
		}
	}

	private void cancelTimeout(String key) {
		Integer id = timeoutTasks.remove(key);
		if (id != null) {
			getServer().getScheduler().cancelTask(id);
		}
	}

	private boolean isAuthed(Player p) {
		return authenticated.contains(key(p));
	}

	// =======================================================================
	// Reminders
	// =======================================================================

	private void scheduleReminder() {
		if (reminderTaskId != -1) {
			getServer().getScheduler().cancelTask(reminderTaskId);
			reminderTaskId = -1;
		}
		if (reminderIntervalTicks <= 0) return;
		reminderTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override public void run() {
				for (Player p : getServer().getOnlinePlayers()) {
					if (!isAuthed(p)) remind(p);
				}
			}
		}, reminderIntervalTicks, reminderIntervalTicks);
	}

	private void remind(Player p) {
		if (isRegistered(key(p))) {
			send(p, "&ePlease log in: &f/login <password>");
		} else {
			send(p, "&ePlease register: &f/register <password> <password>");
		}
	}

	/**
	 * Force-closes any open <b>container</b> for unauthenticated players, twice a second.
	 *
	 * <p>Backstop for modded GUIs that slip past {@link InventoryOpenEvent}. We only close real
	 * containers (chests, machines, backpacks, ...) - never the default inventory view. Closing
	 * sends a CloseWindow packet, and in 1.4.7 that closes the client's current screen; if we closed
	 * unconditionally it would also slam shut the pause/quit menu (which isn't a server-side container),
	 * making it impossible to use. Checking the open inventory type keeps Minecraft's own menus working.
	 */
	private void scheduleEnforcement() {
		if (enforceTaskId != -1) {
			getServer().getScheduler().cancelTask(enforceTaskId);
			enforceTaskId = -1;
		}
		enforceTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override public void run() {
				for (Player p : getServer().getOnlinePlayers()) {
					if (isAuthed(p)) continue;
					InventoryView view = p.getOpenInventory();
					if (view == null) continue;
					InventoryType type = view.getType();
					// CRAFTING == the default view shown when no container is open (and when the
					// client-only pause menu is up). Only a real container warrants a force-close.
					if (type != null && type != InventoryType.CRAFTING && type != InventoryType.PLAYER) {
						p.closeInventory();
					}
				}
			}
		}, 10L, 10L);
	}

	// =======================================================================
	// Listeners - the "freeze" for unauthenticated players
	// =======================================================================

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		beginAuth(e.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		cleanup(e.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onMove(PlayerMoveEvent e) {
		if (isAuthed(e.getPlayer())) return;
		Location from = e.getFrom();
		Location to = e.getTo();
		if (to == null) return;
		// Allow looking around / sub-block jitter; only block actually walking to a new block.
		if (from.getBlockX() == to.getBlockX()
				&& from.getBlockY() == to.getBlockY()
				&& from.getBlockZ() == to.getBlockZ()) {
			return;
		}
		Location frozen = from.clone();
		frozen.setYaw(to.getYaw());
		frozen.setPitch(to.getPitch());
		e.setTo(frozen);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
		if (isAuthed(e.getPlayer())) return;
		String msg = e.getMessage();
		String cmd = (msg.length() > 1 ? msg.substring(1) : "").split(" ", 2)[0].toLowerCase();
		if (ALLOWED_UNAUTHED_COMMANDS.contains(cmd)) return;
		e.setCancelled(true);
		remind(e.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent e) {
		if (isAuthed(e.getPlayer())) return;
		e.setCancelled(true);
		final Player p = e.getPlayer();
		// Chat is async; bounce the reminder back onto the main thread.
		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override public void run() { remind(p); }
		});
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent e) {
		if (!isAuthed(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInteractEntity(PlayerInteractEntityEvent e) {
		if (!isAuthed(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		if (!isAuthed(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent e) {
		if (!isAuthed(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDrop(PlayerDropItemEvent e) {
		if (!isAuthed(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPickup(PlayerPickupItemEvent e) {
		if (!isAuthed(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent e) {
		HumanEntity who = e.getWhoClicked();
		if (who instanceof Player && !isAuthed((Player) who)) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryOpen(InventoryOpenEvent e) {
		// Stop containers / modded GUIs (backpacks, "house" storage, etc.) from opening at all.
		if (e.getPlayer() instanceof Player && !isAuthed((Player) e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onToggleFlight(PlayerToggleFlightEvent e) {
		if (!isAuthed(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent e) {
		// Make unauthenticated players invulnerable so they can't die at the login prompt.
		if (e.getEntity() instanceof Player && !isAuthed((Player) e.getEntity())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDamageByEntity(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof Player && !isAuthed((Player) e.getEntity())) {
			e.setCancelled(true);
			return;
		}
		if (e.getDamager() instanceof Player && !isAuthed((Player) e.getDamager())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onFood(FoodLevelChangeEvent e) {
		if (e.getEntity() instanceof Player && !isAuthed((Player) e.getEntity())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onTarget(EntityTargetEvent e) {
		// Don't let mobs path to / attack someone stuck at the login screen.
		if (e.getTarget() instanceof Player && !isAuthed((Player) e.getTarget())) e.setCancelled(true);
	}

	// =======================================================================
	// Commands
	// =======================================================================

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String name = command.getName().toLowerCase();
		if (name.equals("register")) return cmdRegister(sender, args);
		if (name.equals("login")) return cmdLogin(sender, args);
		if (name.equals("changepassword")) return cmdChangePassword(sender, args);
		if (name.equals("crackauth")) return cmdAdmin(sender, args);
		return false;
	}

	private boolean cmdRegister(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) { sender.sendMessage("Only a player can register."); return true; }
		Player p = (Player) sender;
		String key = key(p);
		if (isAuthed(p)) { send(p, "&aYou are already logged in."); return true; }
		if (isRegistered(key)) { send(p, "&cThis name is already registered. Use &f/login <password>&c."); return true; }
		if (args.length < 2) { send(p, "&eUsage: &f/register <password> <password>"); return true; }
		if (!args[0].equals(args[1])) { send(p, "&cThe two passwords do not match."); return true; }
		if (args[0].length() < minPasswordLength) {
			send(p, "&cPassword too short (minimum " + minPasswordLength + " characters).");
			return true;
		}
		accounts.set("accounts." + key + ".password", hash(args[0]));
		accounts.set("accounts." + key + ".registered", System.currentTimeMillis());
		saveAccounts();
		finishAuth(p);
		send(p, "&aRegistered! Remember this password - you'll need it every time you connect.");
		return true;
	}

	private boolean cmdLogin(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) { sender.sendMessage("Only a player can log in."); return true; }
		Player p = (Player) sender;
		String key = key(p);
		if (isAuthed(p)) { send(p, "&aYou are already logged in."); return true; }
		if (!isRegistered(key)) { send(p, "&cYou are not registered. Use &f/register <password> <password>&c."); return true; }
		if (args.length < 1) { send(p, "&eUsage: &f/login <password>"); return true; }

		if (verify(args[0], getHash(key))) {
			finishAuth(p);
			return true;
		}

		int n = increment(failedAttempts, key);
		if (maxAttempts > 0 && n >= maxAttempts) {
			p.kickPlayer(color(prefix + "&cToo many failed login attempts."));
			return true;
		}
		send(p, maxAttempts > 0
				? "&cWrong password (" + n + "/" + maxAttempts + ")."
				: "&cWrong password.");
		return true;
	}

	private boolean cmdChangePassword(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) { sender.sendMessage("Only a player can change their password."); return true; }
		Player p = (Player) sender;
		String key = key(p);
		if (!isAuthed(p)) { send(p, "&cLog in first with &f/login <password>&c."); return true; }
		if (args.length < 2) { send(p, "&eUsage: &f/changepassword <oldPassword> <newPassword>"); return true; }
		if (!verify(args[0], getHash(key))) { send(p, "&cYour current password is incorrect."); return true; }
		if (args[1].length() < minPasswordLength) {
			send(p, "&cNew password too short (minimum " + minPasswordLength + " characters).");
			return true;
		}
		accounts.set("accounts." + key + ".password", hash(args[1]));
		saveAccounts();
		send(p, "&aPassword changed.");
		return true;
	}

	private boolean cmdAdmin(CommandSender sender, String[] args) {
		if (!sender.hasPermission("crackauth.admin")) {
			sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
			return true;
		}
		String sub = args.length > 0 ? args[0].toLowerCase() : "help";

		if (sub.equals("reload")) {
			loadConfigValues();
			scheduleReminder();
			sender.sendMessage(ChatColor.GREEN + "CrackAuth config reloaded.");
			return true;
		}

		if (sub.equals("info")) {
			int unauthed = 0;
			Player[] online = getServer().getOnlinePlayers();
			for (Player p : online) if (!isAuthed(p)) unauthed++;
			sender.sendMessage(ChatColor.YELLOW + "CrackAuth: " + ChatColor.WHITE + countAccounts()
					+ ChatColor.GRAY + " accounts, " + ChatColor.WHITE + online.length
					+ ChatColor.GRAY + " online (" + ChatColor.WHITE + unauthed
					+ ChatColor.GRAY + " not logged in).");
			String gateStatus;
			if (!gateClassFound) {
				gateStatus = ChatColor.RED + "NOT FOUND (coremod not loaded / not on classpath)";
			} else if (gateInstalled()) {
				gateStatus = ChatColor.GREEN + "ACTIVE (transformer patched NetServerHandler)";
			} else {
				gateStatus = ChatColor.YELLOW + "class found but transformer NOT installed "
						+ "(server not restarted with the coremod, or NetServerHandler not matched)";
			}
			sender.sendMessage(ChatColor.GRAY + " - mod-packet gate: " + gateStatus);
			if (gateClassFound && gateInstalled()) {
				sender.sendMessage(ChatColor.GRAY + " - gate diag: " + ChatColor.WHITE + gateDiag());
			}
			return true;
		}

		if (sub.equals("register")) {
			if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /crackauth register <player> <password>"); return true; }
			String key = args[1].toLowerCase();
			if (isRegistered(key)) { sender.sendMessage(ChatColor.RED + "'" + args[1] + "' is already registered."); return true; }
			accounts.set("accounts." + key + ".password", hash(args[2]));
			accounts.set("accounts." + key + ".registered", System.currentTimeMillis());
			saveAccounts();
			sender.sendMessage(ChatColor.GREEN + "Registered '" + args[1] + "'.");
			return true;
		}

		if (sub.equals("unregister")) {
			if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /crackauth unregister <player>"); return true; }
			String key = args[1].toLowerCase();
			if (!isRegistered(key)) { sender.sendMessage(ChatColor.RED + "No account named '" + args[1] + "'."); return true; }
			accounts.set("accounts." + key, null);
			saveAccounts();
			Player online = findOnline(args[1]);
			if (online != null) beginAuth(online); // force them back to the register prompt
			sender.sendMessage(ChatColor.GREEN + "Unregistered '" + args[1] + "'.");
			return true;
		}

		if (sub.equals("changepassword") || sub.equals("cp")) {
			if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /crackauth changepassword <player> <newPassword>"); return true; }
			String key = args[1].toLowerCase();
			if (!isRegistered(key)) { sender.sendMessage(ChatColor.RED + "No account named '" + args[1] + "'."); return true; }
			accounts.set("accounts." + key + ".password", hash(args[2]));
			saveAccounts();
			sender.sendMessage(ChatColor.GREEN + "Password updated for '" + args[1] + "'.");
			return true;
		}

		sender.sendMessage(ChatColor.YELLOW + "CrackAuth admin: " + ChatColor.WHITE
				+ "reload, info, register <player> <pass>, unregister <player>, changepassword <player> <pass>");
		return true;
	}

	// =======================================================================
	// Accounts storage
	// =======================================================================

	private boolean isRegistered(String key) {
		return accounts.getString("accounts." + key + ".password") != null;
	}

	private String getHash(String key) {
		return accounts.getString("accounts." + key + ".password");
	}

	private int countAccounts() {
		ConfigurationSection sec = accounts.getConfigurationSection("accounts");
		return sec == null ? 0 : sec.getKeys(false).size();
	}

	private void saveAccounts() {
		try {
			if (!getDataFolder().exists()) getDataFolder().mkdirs();
			accounts.save(accountsFile);
		} catch (IOException ex) {
			getLogger().warning("CrackAuth could not save accounts.yml: " + ex.getMessage());
		}
	}

	// =======================================================================
	// Password hashing (PBKDF2-WithHmacSHA256, JDK only)
	// =======================================================================

	/** Produces a self-describing hash string: {@code pbkdf2_sha256$iterations$saltB64$hashB64}. */
	private String hash(String password) {
		byte[] salt = new byte[SALT_BYTES];
		secureRandom.nextBytes(salt);
		byte[] dk = pbkdf2(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
		return HASH_SCHEME + "$" + PBKDF2_ITERATIONS + "$"
				+ Base64.getEncoder().encodeToString(salt) + "$"
				+ Base64.getEncoder().encodeToString(dk);
	}

	/** Constant-time check of a password against a stored {@link #hash} string. */
	private boolean verify(String password, String stored) {
		if (stored == null) return false;
		String[] parts = stored.split("\\$");
		if (parts.length != 4 || !parts[0].equals(HASH_SCHEME)) return false;
		int iterations;
		try {
			iterations = Integer.parseInt(parts[1]);
		} catch (NumberFormatException ex) {
			return false;
		}
		byte[] salt;
		byte[] expected;
		try {
			salt = Base64.getDecoder().decode(parts[2]);
			expected = Base64.getDecoder().decode(parts[3]);
		} catch (IllegalArgumentException ex) {
			return false;
		}
		byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length * 8);
		return MessageDigest.isEqual(expected, actual);
	}

	private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
			SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return skf.generateSecret(spec).getEncoded();
		} catch (Exception ex) {
			throw new RuntimeException("PBKDF2 hashing failed", ex);
		}
	}

	// =======================================================================
	// Small helpers
	// =======================================================================

	private static String key(Player p) {
		return p.getName().toLowerCase();
	}

	private static int increment(Map<String, Integer> map, String key) {
		Integer v = map.get(key);
		int n = (v == null ? 0 : v) + 1;
		map.put(key, n);
		return n;
	}

	private Player findOnline(String name) {
		for (Player p : getServer().getOnlinePlayers()) {
			if (p.getName().equalsIgnoreCase(name)) return p;
		}
		return null;
	}

	private void send(Player p, String msg) {
		p.sendMessage(color(prefix + msg));
	}

	private static String color(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}
}
