package com.favouredcraft.serverlist;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;

/**
 * Client-side mod for Minecraft 1.4.7 that replaces the multiplayer server list screen
 * with a custom one supporting a server icon and a multi-line, colored MOTD.
 *
 * The vanilla 1.4.7 protocol cannot carry a favicon, so the icon is bundled in the mod
 * (see {@link FavouredConfig}). The vanilla client also strips newlines out of the MOTD
 * during the server-list ping; this mod re-implements the ping so newlines survive.
 */
@Mod(modid = FavouredServerListMod.MODID, name = "Favoured Server List", version = "1.0")
public class FavouredServerListMod {
	public static final String MODID = "favouredserverlist";

	@SidedProxy(
			clientSide = "com.favouredcraft.serverlist.ClientProxy",
			serverSide = "com.favouredcraft.serverlist.CommonProxy")
	public static CommonProxy proxy;

	@Mod.Init
	public void init(FMLInitializationEvent event) {
		proxy.init();
	}
}
