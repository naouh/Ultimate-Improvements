package com.favouredcraft.serverlist;

import net.minecraft.client.multiplayer.ServerData;

/** Tweakable constants for the custom server list. */
public class FavouredConfig {

	/**
	 * Classpath path of the bundled server icon (PNG). Replace
	 * {@code src/main/resources/favouredcraft/icon.png} with your own 64x64 image and rebuild.
	 */
	public static final String ICON_PATH = "/favouredcraft/Logo.png";

	/** On-screen size of the icon, in GUI pixels. */
	public static final int ICON_SIZE = 32;

	/**
	 * If non-empty, only servers whose saved address contains this (case-insensitive) substring
	 * get the icon. Leave empty to show the icon for every server in the list.
	 * Example: {@code "favouredcraft.net"}.
	 */
	public static final String ICON_ADDRESS_FILTER = "";

	/** Max number of MOTD lines to render in a single entry. */
	public static final int MAX_MOTD_LINES = 3;

	public static boolean showIconFor(ServerData data) {
		if (ICON_ADDRESS_FILTER.length() == 0) {
			return true;
		}
		return data.serverIP != null
				&& data.serverIP.toLowerCase().indexOf(ICON_ADDRESS_FILTER.toLowerCase()) >= 0;
	}

	private FavouredConfig() {
	}
}
