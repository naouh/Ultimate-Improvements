package com.nao.discordbridge;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Two-way Minecraft &lt;-&gt; Discord chat bridge for MCPC+ 1.4.7.
 *
 * <p>Outbound: chat / join / quit / death / server start-stop are pushed to Discord (see {@link DiscordClient}).
 * Inbound: a background poll fetches new Discord messages and this class broadcasts them in-game on the
 * main server thread.
 *
 * <p>Everything is English-facing per project convention; all wording is configurable in config.yml.
 */
public final class DiscordBridgePlugin extends JavaPlugin implements Listener {

	private DiscordClient client;

	// Cached config so the hot event path never touches the YAML tree.
	private boolean eventJoinQuit;
	private boolean eventServerStartStop;
	private boolean eventDeaths;
	private String fmtChat;
	private String fmtJoin;
	private String fmtQuit;
	private String fmtDeath;
	private String fmtServerStart;
	private String fmtServerStop;
	private String fmtToMinecraft;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		if (!startBridge()) {
			getLogger().severe("DiscordBridge is idle: set 'discord.token' and 'discord.channel-id' in "
					+ "plugins/DiscordBridge/config.yml, then run /discordbridge reload.");
			// Still register the command so /discordbridge reload works after editing the config.
			getServer().getPluginManager().registerEvents(this, this);
			return;
		}
		getServer().getPluginManager().registerEvents(this, this);
		getLogger().info("DiscordBridge enabled (channel poll active).");
	}

	@Override
	public void onDisable() {
		if (client != null) {
			if (eventServerStartStop) client.sendAsBotBlocking(fmtServerStop);
			client.stop();
			client = null;
		}
	}

	/** (Re)read config and (re)create the Discord client. Returns false if it isn't configured yet. */
	private boolean startBridge() {
		reloadConfig();
		FileConfiguration cfg = getConfig();

		String token = cfg.getString("discord.token", "").trim();
		String channelId = cfg.getString("discord.channel-id", "").trim();
		String webhook = cfg.getString("discord.webhook-url", "").trim();
		// Default to per-player MC heads even on older config.yml files that predate this key.
		String avatar = cfg.getString("discord.avatar-url", "https://mc-heads.net/avatar/{player}/64.png").trim();
		int poll = cfg.getInt("discord.poll-interval", 3);
		boolean ignoreBots = cfg.getBoolean("discord.ignore-bots", true);

		eventJoinQuit = cfg.getBoolean("events.join-quit", true);
		eventServerStartStop = cfg.getBoolean("events.server-start-stop", true);
		eventDeaths = cfg.getBoolean("events.deaths", false);

		fmtChat = cfg.getString("messages.chat-to-discord", "**{player}**: {message}");
		fmtJoin = cfg.getString("messages.join-to-discord", "**{player}** joined the server");
		fmtQuit = cfg.getString("messages.quit-to-discord", "**{player}** left the server");
		fmtDeath = cfg.getString("messages.death-to-discord", "{message}");
		fmtServerStart = cfg.getString("messages.server-start", "Server is now online");
		fmtServerStop = cfg.getString("messages.server-stop", "Server is shutting down");
		fmtToMinecraft = cfg.getString("messages.discord-to-mc", "&9[Discord] &b{user}&r: {message}");

		if (client != null) {
			client.stop();
			client = null;
		}

		if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")
				|| channelId.isEmpty() || channelId.replace("0", "").isEmpty()) {
			return false;
		}

		client = new DiscordClient(getLogger(), token, channelId, webhook, avatar, ignoreBots);
		client.start(poll, new DiscordClient.MessageSink() {
			@Override public void accept(final List<DiscordClient.ChatLine> lines) {
				// Hop back to the main thread: broadcasting / colour translation must not run off-thread.
				Bukkit.getScheduler().runTask(DiscordBridgePlugin.this, new Runnable() {
					@Override public void run() {
						broadcastFromDiscord(lines);
					}
				});
			}
		});
		if (eventServerStartStop) client.sendAsBot(fmtServerStart);
		return true;
	}

	private void broadcastFromDiscord(List<DiscordClient.ChatLine> lines) {
		for (DiscordClient.ChatLine line : lines) {
			// Discord messages can be multi-line; broadcast each line so the prefix stays readable.
			for (String part : line.message.split("\n", -1)) {
				String text = fmtToMinecraft
						.replace("{user}", line.user)
						.replace("{message}", part);
				getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', text));
			}
		}
	}

	// --- Minecraft -> Discord ----------------------------------------------------------------------

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent event) {
		if (client == null) return;
		Player p = event.getPlayer();
		String message = ChatColor.stripColor(event.getMessage());
		if (client.hasWebhook()) {
			// With a webhook the player name is the Discord author, so only the raw message is sent.
			client.sendAsWebhook(p.getName(), message);
		} else {
			client.sendAsBot(fmtChat
					.replace("{player}", p.getName())
					.replace("{world}", p.getWorld() != null ? p.getWorld().getName() : "")
					.replace("{message}", message));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		if (client == null || !eventJoinQuit) return;
		client.sendAsBot(fmtJoin.replace("{player}", event.getPlayer().getName()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		if (client == null || !eventJoinQuit) return;
		client.sendAsBot(fmtQuit.replace("{player}", event.getPlayer().getName()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		if (client == null || !eventDeaths) return;
		String msg = event.getDeathMessage();
		if (msg == null || msg.isEmpty()) return;
		client.sendAsBot(fmtDeath.replace("{message}", ChatColor.stripColor(msg)));
	}

	// --- Command -----------------------------------------------------------------------------------

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
			boolean ok = startBridge();
			sender.sendMessage(ok
					? ChatColor.GREEN + "DiscordBridge reloaded - the bridge is active."
					: ChatColor.RED + "DiscordBridge reloaded but is idle: check 'token' and 'channel-id' in config.yml.");
			return true;
		}
		if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
			if (client == null) {
				sender.sendMessage(ChatColor.RED + "DiscordBridge is NOT connected (not configured).");
			} else {
				sender.sendMessage(ChatColor.GREEN + "DiscordBridge is active"
						+ (client.hasWebhook() ? " (webhook mode)." : " (bot mode)."));
			}
			return true;
		}
		sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <reload|status>");
		return true;
	}
}
