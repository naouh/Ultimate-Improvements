package com.nao.itemguard;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ItemGuard - two independent rules, both keyed by item/block ID so they work for vanilla AND
 * modded items on MCPC+ 1.4.7. Every rule key may be a bare ID ("243", matches any data value)
 * or an ID:data pair ("243:1", matches only that subtype), so mods that pack many items onto one
 * ID (Railcraft anchors, etc.) can be targeted precisely.
 *
 *   1) Blacklist: matching items can't be crafted (result is wiped from the grid and the craft is
 *      cancelled) and can't be placed.
 *   2) Placement limits: a player may only have N matching blocks placed at once. The cap is
 *      resolved from permissions (highest matching wins), so groups like builder/donator get
 *      different limits. Breaking one of your own placed blocks frees a slot.
 *
 * Placed-block ownership is tracked by location and persisted to data.yml so limits survive a
 * restart. Everything is English-facing per project convention.
 */
public class ItemGuardPlugin extends JavaPlugin implements Listener {

	// --- Config: blacklist -------------------------------------------------
	// Each blacklisted key maps to the set of (lowercase) world names it is blocked in.
	// An EMPTY set is the marker for "all worlds" (blocked everywhere).
	/** id (any data) -> worlds it's blocked in (empty = everywhere). */
	private final Map<Integer, Set<String>> blAnyData = new HashMap<Integer, Set<String>>();
	/** encoded id:data -> worlds it's blocked in (empty = everywhere). */
	private final Map<Long, Set<String>> blExact = new HashMap<Long, Set<String>>();

	// --- Config: limits ----------------------------------------------------
	/** id (any data) -> limit definition. */
	private final Map<Integer, LimitDef> limAnyData = new HashMap<Integer, LimitDef>();
	/** encoded id:data -> limit definition. */
	private final Map<Long, LimitDef> limExact = new HashMap<Long, LimitDef>();

	// --- Runtime state -----------------------------------------------------
	/** locKey -> who placed a limited block there, under which limit key. */
	private final Map<String, Placement> placements = new HashMap<String, Placement>();
	/** Derived from placements: lowercase player name -> (limit key -> count). */
	private final Map<String, Map<String, Integer>> counts = new HashMap<String, Map<String, Integer>>();

	private File dataFile;
	private boolean dirty;

	/** A limited block's placement record. {@code typeKey} is the canonical limit key it counts toward. */
	private static final class Placement {
		final String owner;   // lowercase player name
		final String typeKey; // e.g. "243" or "243:1"
		Placement(String owner, String typeKey) {
			this.owner = owner;
			this.typeKey = typeKey;
		}
	}

	/** How many of a block a player may place: a base default plus permission overrides. */
	private static final class LimitDef {
		final String key; // canonical key this def counts under
		int def;
		final Map<String, Integer> perms = new HashMap<String, Integer>();
		LimitDef(String key) { this.key = key; }
	}

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadConfigValues();
		dataFile = new File(getDataFolder(), "data.yml");
		loadData();
		getServer().getPluginManager().registerEvents(this, this);
		// Periodic flush so a crash loses at most ~60s of placements.
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() { if (dirty) saveData(); }
		}, 1200L, 1200L);
		getLogger().info("ItemGuard enabled (" + (blAnyData.size() + blExact.size())
				+ " blacklisted, " + (limAnyData.size() + limExact.size()) + " limited types).");
	}

	@Override
	public void onDisable() {
		saveData();
		getLogger().info("ItemGuard disabled.");
	}

	// --- Key parsing / encoding -------------------------------------------

	/** Packs id+data into one long (data assumed >= 0). */
	private static long encode(int id, int data) {
		return (((long) id) << 20) | (data & 0xFFFFFL);
	}

	/** Parses "243" or "243:1" (or an Integer) into {id, data} where data == -1 means "any". null if invalid. */
	private static int[] parseKey(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return new int[] { ((Number) o).intValue(), -1 };
		String s = o.toString().trim();
		if (s.isEmpty()) return null;
		int colon = s.indexOf(':');
		try {
			if (colon < 0) {
				return new int[] { Integer.parseInt(s), -1 };
			}
			int id = Integer.parseInt(s.substring(0, colon).trim());
			int data = Integer.parseInt(s.substring(colon + 1).trim());
			return new int[] { id, data };
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Canonical string form of a parsed key, for counts/data.yml. */
	private static String keyString(int id, int data) {
		return data < 0 ? Integer.toString(id) : id + ":" + data;
	}

	// --- Config loading ----------------------------------------------------

	private void loadConfigValues() {
		reloadConfig();
		blAnyData.clear();
		blExact.clear();
		limAnyData.clear();
		limExact.clear();

		for (Object o : getConfig().getList("blacklist", new ArrayList<Object>())) {
			if (o == null) continue;
			// An entry may carry an optional "@world1,world2" suffix scoping it to those worlds.
			// Without a suffix the item is blocked everywhere.
			String raw = o.toString().trim();
			String keyPart = raw;
			Set<String> worlds = null; // null = no suffix = all worlds
			int at = raw.indexOf('@');
			if (at >= 0) {
				keyPart = raw.substring(0, at).trim();
				worlds = parseWorlds(raw.substring(at + 1));
			}
			int[] k = parseKey(keyPart);
			if (k == null) {
				getLogger().warning("ItemGuard: ignoring invalid blacklist entry '" + o + "'.");
				continue;
			}
			if (k[1] < 0) mergeBlacklist(blAnyData, k[0], worlds);
			else mergeBlacklist(blExact, encode(k[0], k[1]), worlds);
		}

		ConfigurationSection limitsSec = getConfig().getConfigurationSection("limits");
		if (limitsSec != null) {
			for (String rawKey : limitsSec.getKeys(false)) {
				int[] k = parseKey(rawKey);
				if (k == null) {
					getLogger().warning("ItemGuard: ignoring invalid limit key '" + rawKey + "'.");
					continue;
				}
				ConfigurationSection s = limitsSec.getConfigurationSection(rawKey);
				if (s == null) continue;
				LimitDef def = new LimitDef(keyString(k[0], k[1]));
				def.def = s.getInt("default", 0);
				ConfigurationSection p = s.getConfigurationSection("permissions");
				if (p != null) {
					for (String node : p.getKeys(false)) {
						def.perms.put(node, p.getInt(node));
					}
				}
				if (k[1] < 0) limAnyData.put(k[0], def);
				else limExact.put(encode(k[0], k[1]), def);
			}
		}
	}

	/** Splits "world1, world2" into a lowercase set; null if it names no world (treated as "all"). */
	private static Set<String> parseWorlds(String s) {
		Set<String> set = new HashSet<String>();
		for (String part : s.split(",")) {
			String w = part.trim().toLowerCase();
			if (!w.isEmpty()) set.add(w);
		}
		return set.isEmpty() ? null : set;
	}

	/**
	 * Records that {@code key} is blacklisted in {@code worlds} (null/empty = everywhere), merging
	 * with any existing entry. "Everywhere" always wins over a world-scoped entry for the same key.
	 */
	private static <K> void mergeBlacklist(Map<K, Set<String>> map, K key, Set<String> worlds) {
		Set<String> cur = map.get(key);
		if (cur != null && cur.isEmpty()) return;            // already blocked everywhere
		if (worlds == null || worlds.isEmpty()) {            // this entry means everywhere
			map.put(key, new HashSet<String>());
			return;
		}
		if (cur == null) map.put(key, new HashSet<String>(worlds));
		else cur.addAll(worlds);
	}

	/** True if {@code worlds} (an empty set = everywhere) covers {@code world}. */
	private static boolean matchesWorld(Set<String> worlds, String world) {
		if (worlds == null) return false;                    // key not blacklisted at all
		if (worlds.isEmpty()) return true;                   // blocked everywhere
		return world != null && worlds.contains(world.toLowerCase());
	}

	private boolean isBlacklisted(int id, int data, String world) {
		if (matchesWorld(blAnyData.get(id), world)) return true;
		return data >= 0 && matchesWorld(blExact.get(encode(id, data)), world);
	}

	/** The limit def that applies to this item (exact id:data wins over bare id), or null. */
	private LimitDef limitDefFor(int id, int data) {
		if (data >= 0) {
			LimitDef exact = limExact.get(encode(id, data));
			if (exact != null) return exact;
		}
		return limAnyData.get(id);
	}

	/** Highest limit the player qualifies for; falls back to the type's default. */
	private int limitFor(Player player, LimitDef def) {
		int limit = def.def;
		for (Map.Entry<String, Integer> e : def.perms.entrySet()) {
			if (player.hasPermission(e.getKey()) && e.getValue() > limit) {
				limit = e.getValue();
			}
		}
		return limit;
	}

	// --- Blacklist: crafting ----------------------------------------------

	/** Hide the result so a blacklisted item can't even be pulled out of the grid. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onPrepareCraft(PrepareItemCraftEvent event) {
		ItemStack result = event.getInventory().getResult();
		if (result == null) return;
		// World-scoped: hide the result only for crafters standing in a world where it's blocked.
		boolean anyBypass = false;
		boolean blocked = false;
		for (org.bukkit.entity.HumanEntity v : event.getViewers()) {
			if (v.hasPermission("itemguard.bypass")) { anyBypass = true; break; }
			if (isBlacklisted(result.getTypeId(), result.getDurability(), v.getWorld().getName())) blocked = true;
		}
		if (!anyBypass && blocked) {
			event.getInventory().setResult(null);
		}
	}

	/** Safety net: cancel the actual craft of a blacklisted result. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCraft(CraftItemEvent event) {
		ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : event.getCurrentItem();
		if (result == null) return;
		if (event.getWhoClicked().hasPermission("itemguard.bypass")) return;
		if (!isBlacklisted(result.getTypeId(), result.getDurability(), event.getWhoClicked().getWorld().getName())) return;
		event.setCancelled(true);
		if (event.getWhoClicked() instanceof Player) {
			((Player) event.getWhoClicked()).sendMessage("§cThat item is blacklisted - you can't craft it.");
		}
	}

	// --- Blacklist + limits: placing --------------------------------------

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();
		if (player.hasPermission("itemguard.bypass")) return;

		// Match on the item in hand: it carries the correct id+data for modded subtypes.
		ItemStack hand = event.getItemInHand();
		int id = hand != null ? hand.getTypeId() : event.getBlockPlaced().getTypeId();
		int data = hand != null ? hand.getDurability() : event.getBlockPlaced().getData();

		if (isBlacklisted(id, data, event.getBlockPlaced().getWorld().getName())) {
			event.setCancelled(true);
			player.sendMessage("§cThat item is blacklisted in this world - you can't place it here.");
			return;
		}

		LimitDef def = limitDefFor(id, data);
		if (def == null) return;

		int limit = limitFor(player, def);
		int current = countFor(player.getName(), def.key);
		if (current >= limit) {
			event.setCancelled(true);
			if (limit <= 0) {
				player.sendMessage("§cYou are not allowed to place that block.");
			} else {
				player.sendMessage("§cLimit reached: you may only place §e" + limit
						+ "§c of that block (you have §e" + current + "§c).");
			}
			return;
		}

		String locKey = locKey(event.getBlockPlaced());
		placements.put(locKey, new Placement(player.getName().toLowerCase(), def.key));
		adjustCount(player.getName(), def.key, +1);
		dirty = true;
		player.sendMessage("§7Placed §f" + (current + 1) + "§7/§f" + limit + "§7 (" + def.key + ").");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Placement p = placements.remove(locKey(event.getBlock()));
		if (p == null) return;
		adjustCount(p.owner, p.typeKey, -1);
		dirty = true;
	}

	// --- Count bookkeeping -------------------------------------------------

	private int countFor(String playerName, String typeKey) {
		Map<String, Integer> m = counts.get(playerName.toLowerCase());
		if (m == null) return 0;
		Integer c = m.get(typeKey);
		return c == null ? 0 : c;
	}

	private void adjustCount(String playerName, String typeKey, int delta) {
		String owner = playerName.toLowerCase();
		Map<String, Integer> m = counts.get(owner);
		if (m == null) {
			m = new HashMap<String, Integer>();
			counts.put(owner, m);
		}
		int next = countFor(owner, typeKey) + delta;
		if (next <= 0) m.remove(typeKey);
		else m.put(typeKey, next);
	}

	private static String locKey(Block b) {
		return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
	}

	// --- Persistence -------------------------------------------------------

	private void loadData() {
		placements.clear();
		counts.clear();
		if (!dataFile.exists()) return;
		YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
		List<Map<?, ?>> list = y.getMapList("blocks");
		for (Map<?, ?> entry : list) {
			Object loc = entry.get("loc");
			Object owner = entry.get("owner");
			Object type = entry.get("type");
			if (loc == null || owner == null || type == null) continue;
			String ownerName = owner.toString().toLowerCase();
			String typeKey = type.toString();
			placements.put(loc.toString(), new Placement(ownerName, typeKey));
			adjustCount(ownerName, typeKey, +1);
		}
		getLogger().info("ItemGuard loaded " + placements.size() + " tracked block placements.");
	}

	private void saveData() {
		YamlConfiguration y = new YamlConfiguration();
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>(placements.size());
		for (Map.Entry<String, Placement> e : placements.entrySet()) {
			Map<String, Object> m = new HashMap<String, Object>();
			m.put("loc", e.getKey());
			m.put("owner", e.getValue().owner);
			m.put("type", e.getValue().typeKey);
			list.add(m);
		}
		y.set("blocks", list);
		try {
			if (!getDataFolder().exists()) getDataFolder().mkdirs();
			y.save(dataFile);
			dirty = false;
		} catch (IOException ex) {
			getLogger().warning("ItemGuard could not save data.yml: " + ex.getMessage());
		}
	}

	// --- Command -----------------------------------------------------------

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("itemguard")) return false;

		if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
			loadConfigValues();
			sender.sendMessage("§aItemGuard config reloaded: " + (blAnyData.size() + blExact.size())
					+ " blacklisted, " + (limAnyData.size() + limExact.size()) + " limited types.");
			return true;
		}

		if (args.length >= 1 && args[0].equalsIgnoreCase("count")) {
			Player target;
			if (args.length >= 2) {
				target = getServer().getPlayer(args[1]);
				if (target == null) {
					sender.sendMessage("§cPlayer not online: " + args[1]);
					return true;
				}
			} else if (sender instanceof Player) {
				target = (Player) sender;
			} else {
				sender.sendMessage("§cConsole must specify a player: /itemguard count <player>");
				return true;
			}

			// Handy: tell a player the ID:data of the item in their hand.
			if (sender instanceof Player) {
				ItemStack inHand = ((Player) sender).getItemInHand();
				if (inHand != null && inHand.getTypeId() != 0) {
					sender.sendMessage("§7Item in your hand: §fID " + inHand.getTypeId()
							+ ":" + inHand.getDurability() + "§7 (" + inHand.getType() + ")");
				}
			}

			Map<String, Integer> m = counts.get(target.getName().toLowerCase());
			if (m == null || m.isEmpty()) {
				sender.sendMessage("§7" + target.getName() + " has no tracked limited blocks placed.");
				return true;
			}
			sender.sendMessage("§eLimited blocks placed by " + target.getName() + ":");
			for (Map.Entry<String, Integer> e : m.entrySet()) {
				int[] k = parseKey(e.getKey());
				LimitDef def = k == null ? null : limitDefFor(k[0], k[1]);
				int cap = def == null ? -1 : limitFor(target, def);
				sender.sendMessage("§7 - §f" + e.getKey() + "§7: §f"
						+ e.getValue() + (cap >= 0 ? "§7/§f" + cap : ""));
			}
			return true;
		}

		sender.sendMessage("§eItemGuard§7: /itemguard reload | /itemguard count [player]");
		return true;
	}
}
