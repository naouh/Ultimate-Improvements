package com.nao.worldtps;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced replacement for Bukkit's /tps. Reports rolling global TPS (1m/5m/15m) and MSPT,
 * then breaks down load per world (players, loaded chunks, entities, living, tile entities) and,
 * when running on MCPC+/Forge, the real per-dimension and global tick times read via reflection.
 *
 * Commands: /tps (overview), /tps <world> (detailed single-world view). Aliases: /lag, /wtps.
 */
public class WorldTpsPlugin extends JavaPlugin {

	private static final double TICK_BUDGET_MS = 50.0; // 20 TPS target = 50 ms/tick

	private final TpsSampler sampler = new TpsSampler();
	private final ForgeTickTimes forge = new ForgeTickTimes();

	@Override
	public void onEnable() {
		sampler.runTaskTimer(this, 0L, 1L);
		getLogger().info("WorldTPS enabled (Forge timing: " + (forge.isAvailable() ? "available" : "unavailable") + ").");
	}

	@Override
	public void onDisable() {
		try {
			sampler.cancel();
		} catch (Throwable ignored) {
		}
		getLogger().info("WorldTPS disabled.");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("tps")) return false;

		sender.sendMessage(ChatColor.GOLD + "===== " + ChatColor.YELLOW + "WorldTPS" + ChatColor.GOLD + " =====");
		sendGlobal(sender);

		if (args.length >= 1) {
			World w = Bukkit.getWorld(args[0]);
			if (w == null) {
				sender.sendMessage(ChatColor.RED + "Unknown world: " + args[0]);
				return true;
			}
			sendWorldDetail(sender, w);
		} else {
			sendPerDimension(sender);
			sendWorldOverview(sender);
		}
		return true;
	}

	// --- Global TPS / MSPT -------------------------------------------------

	private void sendGlobal(CommandSender sender) {
		sender.sendMessage(ChatColor.GRAY + "TPS  " + ChatColor.WHITE + "1m " + fmtTps(sampler.tps(60))
				+ ChatColor.WHITE + "  5m " + fmtTps(sampler.tps(300))
				+ ChatColor.WHITE + "  15m " + fmtTps(sampler.tps(900)));

		double mspt = forge.globalMsptMs();
		String source = "forge";
		if (mspt < 0) {
			mspt = sampler.meanInterTickMs(100);
			source = "interval";
		}
		String msptStr = (mspt < 0) ? (ChatColor.GRAY + "n/a")
				: budgetColor(mspt) + String.format("%.1f", mspt) + " ms";
		sender.sendMessage(ChatColor.GRAY + "MSPT " + msptStr + ChatColor.DARK_GRAY + " (" + source + ")");
	}

	// --- Forge per-dimension tick times ------------------------------------

	private void sendPerDimension(CommandSender sender) {
		Map<Integer, Double> dims = forge.perDimensionMeanMs();
		if (dims.isEmpty()) return;
		sender.sendMessage(ChatColor.GRAY + "Per-dimension tick time:");
		for (Map.Entry<Integer, Double> e : dims.entrySet()) {
			double ms = e.getValue();
			int pct = (int) Math.round(ms / TICK_BUDGET_MS * 100.0);
			sender.sendMessage("  " + ChatColor.AQUA + "dim " + e.getKey() + ChatColor.GRAY + ": "
					+ budgetColor(ms) + String.format("%.1f", ms) + " ms"
					+ ChatColor.DARK_GRAY + " (" + pct + "% of budget)");
		}
	}

	// --- Per-world load ----------------------------------------------------

	private void sendWorldOverview(CommandSender sender) {
		List<World> worlds = getServer().getWorlds();
		sender.sendMessage(ChatColor.GRAY + "Worlds: " + ChatColor.WHITE + worlds.size() + ChatColor.GRAY + " loaded");
		for (World w : worlds) {
			sender.sendMessage("  " + ChatColor.YELLOW + w.getName()
					+ ChatColor.GRAY + " p" + ChatColor.WHITE + w.getPlayers().size()
					+ ChatColor.GRAY + " ch" + ChatColor.WHITE + w.getLoadedChunks().length
					+ ChatColor.GRAY + " ent" + ChatColor.WHITE + w.getEntities().size()
					+ ChatColor.GRAY + " liv" + ChatColor.WHITE + w.getLivingEntities().size()
					+ ChatColor.GRAY + " tile" + ChatColor.WHITE + countTileEntities(w));
		}
		sender.sendMessage(ChatColor.DARK_GRAY + "Tip: /tps <world> for a detailed breakdown.");
	}

	private void sendWorldDetail(CommandSender sender, World w) {
		sender.sendMessage(ChatColor.GRAY + "World " + ChatColor.YELLOW + w.getName()
				+ ChatColor.DARK_GRAY + " (" + w.getEnvironment() + ")");
		sender.sendMessage(ChatColor.GRAY + "  Players ......... " + ChatColor.WHITE + w.getPlayers().size());
		sender.sendMessage(ChatColor.GRAY + "  Loaded chunks ... " + ChatColor.WHITE + w.getLoadedChunks().length);
		sender.sendMessage(ChatColor.GRAY + "  Entities ........ " + ChatColor.WHITE + w.getEntities().size()
				+ ChatColor.DARK_GRAY + " (" + w.getLivingEntities().size() + " living)");
		sender.sendMessage(ChatColor.GRAY + "  Tile entities ... " + ChatColor.WHITE + countTileEntities(w));

		// Top entity types by count - reveals what is actually loading the world.
		Map<String, Integer> byType = new HashMap<String, Integer>();
		for (Entity e : w.getEntities()) {
			String type = e.getType() != null ? e.getType().name() : e.getClass().getSimpleName();
			Integer prev = byType.get(type);
			byType.put(type, prev == null ? 1 : prev + 1);
		}
		if (!byType.isEmpty()) {
			List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(byType.entrySet());
			Collections.sort(entries, new java.util.Comparator<Map.Entry<String, Integer>>() {
				public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
					return b.getValue() - a.getValue();
				}
			});
			sender.sendMessage(ChatColor.GRAY + "  Top entities:");
			int shown = 0;
			for (Map.Entry<String, Integer> e : entries) {
				if (shown++ >= 6) break;
				sender.sendMessage("    " + ChatColor.WHITE + e.getValue() + ChatColor.GRAY + "x " + e.getKey());
			}
		}
	}

	private int countTileEntities(World w) {
		int total = 0;
		for (Chunk c : w.getLoadedChunks()) {
			try {
				total += c.getTileEntities().length;
			} catch (Throwable ignored) {
			}
		}
		return total;
	}

	// --- Formatting helpers ------------------------------------------------

	private static String fmtTps(double tps) {
		if (tps < 0) return ChatColor.GRAY + "n/a";
		ChatColor c = tps >= 18.0 ? ChatColor.GREEN : tps >= 15.0 ? ChatColor.YELLOW : ChatColor.RED;
		return c + String.format("%.2f", tps);
	}

	private static ChatColor budgetColor(double ms) {
		double pct = ms / TICK_BUDGET_MS;
		return pct < 0.6 ? ChatColor.GREEN : pct < 0.9 ? ChatColor.YELLOW : ChatColor.RED;
	}
}
