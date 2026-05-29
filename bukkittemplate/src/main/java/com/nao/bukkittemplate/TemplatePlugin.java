package com.nao.bukkittemplate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal Bukkit plugin skeleton for MCPC+ 1.4.7.
 *
 * Lifecycle: onEnable / onDisable. Registers itself as an event {@link Listener} (add
 * {@code @EventHandler} methods here), and handles the /bukkittemplate command declared in
 * plugin.yml. Copy this class as the starting point for a real plugin.
 */
public class TemplatePlugin extends JavaPlugin implements Listener {

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		getLogger().info("BukkitTemplate enabled.");
	}

	@Override
	public void onDisable() {
		getLogger().info("BukkitTemplate disabled.");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equalsIgnoreCase("bukkittemplate")) {
			sender.sendMessage("Hello from BukkitTemplate!");
			return true;
		}
		return false;
	}
}
