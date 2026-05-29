package com.nao.hdv;

import com.nao.hdv.command.HdvCommand;
import com.nao.hdv.network.PacketHandler;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.Mod.ServerStarting;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;

/**
 * Hotel de Vente - a player auction house for Minecraft 1.4.7 (Forge, MCPC+ hybrid).
 *
 * A fully rendered GUI (see {@code client.HdvGui}) lets players browse listings (Buy tab) and put
 * their own items up for sale (Sell tab). The server is authoritative: the client only sends intent
 * (slot/quantity/price/listing-id), the server validates against the real inventory and the Essentials
 * economy, so there is no duplication vector. Listings persist in world save data across restarts.
 */
@Mod(modid = HdvMod.MODID, name = HdvMod.NAME, version = HdvMod.VERSION, acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            channels = { HdvMod.CHANNEL }, packetHandler = PacketHandler.class)
public class HdvMod {

	public static final String MODID   = "HDV";
	public static final String NAME    = "Hotel de Vente";
	public static final String VERSION = "0.1.0";
	public static final String CHANNEL = "HDV";

	@Instance(MODID)
	public static HdvMod instance;

	@SidedProxy(clientSide = "com.nao.hdv.client.ClientProxy", serverSide = "com.nao.hdv.CommonProxy")
	public static CommonProxy proxy;

	@PreInit
	public void preInit(FMLPreInitializationEvent e) {
		Config.load(e.getSuggestedConfigurationFile());
		proxy.preInit();
	}

	@Init
	public void init(FMLInitializationEvent e) {
		proxy.init();
	}

	@ServerStarting
	public void onServerStarting(FMLServerStartingEvent e) {
		e.registerServerCommand(new HdvCommand());
	}
}
