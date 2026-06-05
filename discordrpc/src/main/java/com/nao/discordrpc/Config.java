package com.nao.discordrpc;

import java.io.File;

import net.minecraftforge.common.Configuration;

/**
 * Client tunables, stored in config/DiscordRPC.cfg.
 *
 * The big "Playing &lt;X&gt;" line is the name of your Discord application (set on
 * discord.com/developers); to get "Playing on T1F" literally, name the application "on T1F".
 * {@link #details} / {@link #state} render as the two smaller lines below it.
 */
public final class Config {
	private Config() {}

	/** Discord application id (Client ID) from discord.com/developers. Required; RPC is disabled if blank. */
	public static String applicationId = "";
	/** Server label, used for the large-image tooltip. */
	public static String serverName = "T1F";
	/** Second line under the app name (optional). */
	public static String details = "";
	/** Third line (optional). Ignored when {@link #showPlayerCount} is on - the count takes this line. */
	public static String state = "";
	/** Rich Presence asset key uploaded in the dev portal (optional; blank = no image). */
	public static String largeImageKey = "";
	/** Show the "elapsed" timer since the player connected. */
	public static boolean showElapsed = true;
	/** Show the live online-player count on the "state" (third) line. */
	public static boolean showPlayerCount = true;
	/** Optional server max-player slots; if &gt; 0 the count renders as "X/Y players". 0 = just "X players". */
	public static int serverMaxPlayers = 0;

	public static void load(File file) {
		Configuration cfg = new Configuration(file);
		cfg.load();
		applicationId = cfg.get("general", "applicationId", "",
				"Discord application id (Client ID) from discord.com/developers. REQUIRED.").value;
		serverName = cfg.get("general", "serverName", "T1F",
				"Server label shown in the large-image tooltip.").value;
		details = cfg.get("general", "details", "",
				"Optional second line under the app name (e.g. 'Surviving').").value;
		state = cfg.get("general", "state", "",
				"Optional third line.").value;
		largeImageKey = cfg.get("general", "largeImageKey", "",
				"Rich Presence asset key from the dev portal (blank = no image).").value;
		showElapsed = cfg.get("general", "showElapsed", true,
				"Show the elapsed timer since connecting.").getBoolean(true);
		showPlayerCount = cfg.get("general", "showPlayerCount", true,
				"Show the live online-player count on the state line (overrides 'state' while on a server).").getBoolean(true);
		serverMaxPlayers = cfg.get("general", "serverMaxPlayers", 0,
				"Server max-player slots; >0 renders the count as 'X/Y players', 0 shows just 'X players'.").getInt(0);
		cfg.save();
	}

	public static boolean enabled() {
		return applicationId != null && applicationId.trim().length() > 0;
	}
}
