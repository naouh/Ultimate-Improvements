package com.nao.discordrpc;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Discord Rich Presence for Minecraft 1.4.7 (Forge, client-side).
 *
 * While a player is connected to a multiplayer server, their local Discord client shows
 * "Playing on &lt;server&gt;" with an elapsed timer. All the work happens on the client (see
 * {@code client.ClientProxy} / {@code client.RpcManager}); on a dedicated server this mod is inert.
 *
 * There is intentionally no {@code @NetworkMod}: the mod imposes no client/server side requirement,
 * so it can be shipped in the client modpack without the server needing it.
 */
@Mod(modid = DiscordRpcMod.MODID, name = DiscordRpcMod.NAME, version = DiscordRpcMod.VERSION,
     acceptedMinecraftVersions = "[1.4.7]")
public class DiscordRpcMod {

	public static final String MODID   = "DiscordRPC";
	public static final String NAME    = "Discord Rich Presence";
	public static final String VERSION = "0.1.0";

	@SidedProxy(clientSide = "com.nao.discordrpc.client.ClientProxy",
	            serverSide = "com.nao.discordrpc.CommonProxy")
	public static CommonProxy proxy;

	@PreInit
	public void preInit(FMLPreInitializationEvent e) {
		Config.load(e.getSuggestedConfigurationFile());
	}

	@Init
	public void init(FMLInitializationEvent e) {
		proxy.init();
	}
}
