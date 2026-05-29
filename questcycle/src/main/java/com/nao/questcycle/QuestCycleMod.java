package com.nao.questcycle;

import com.nao.questcycle.command.QuestsCommand;
import com.nao.questcycle.command.TitleCommand;
import com.nao.questcycle.config.QuestConfigLoader;
import com.nao.questcycle.config.QuestConfigPaths;
import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.event.ChatPrefixHandler;
import com.nao.questcycle.event.DsuInteractHandler;
import com.nao.questcycle.event.InventoryDeltaTracker;
import com.nao.questcycle.event.PlayerLifecycleHandler;
import com.nao.questcycle.network.QuestPacketHandler;
import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.Mod.ServerStarting;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = QuestCycleMod.MODID, name = QuestCycleMod.NAME, version = QuestCycleMod.VERSION,
     acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            channels = { QuestCycleMod.CHANNEL }, packetHandler = QuestPacketHandler.class)
public class QuestCycleMod {
	public static final String MODID = "questcycle";
	public static final String NAME = "QuestCycle";
	public static final String VERSION = "0.1.0";
	public static final String CHANNEL = "QC";

	@Instance(MODID)
	public static QuestCycleMod instance;

	@SidedProxy(clientSide = "com.nao.questcycle.client.ClientProxy",
	            serverSide = "com.nao.questcycle.CommonProxy")
	public static CommonProxy proxy;

	@PreInit
	public void preInit(FMLPreInitializationEvent e) {
		QuestConfigPaths.initConfigDir(e.getModConfigurationDirectory());
		String err = QuestConfigLoader.loadAll();
		if (err != null) {
			System.err.println("[QuestCycle] preInit: quest config load returned an error: " + err);
		}
		proxy.preInit();
	}

	@Init
	public void init(FMLInitializationEvent e) {
		GameRegistry.registerPlayerTracker((IPlayerTracker) new PlayerLifecycleHandler());
		MinecraftForge.EVENT_BUS.register(new ChatPrefixHandler());
		// Validate "store N in a Deep Storage Unit" tasks when a player opens a DSU.
		MinecraftForge.EVENT_BUS.register(new DsuInteractHandler());
		// Single source for item-gained events: inventory delta scan per tick.
		// Covers vanilla crafting, AE ME crafting, /give, mob drops, chest pulls.
		TickRegistry.registerTickHandler(new InventoryDeltaTracker(), Side.SERVER);
		proxy.init();
	}

	@PostInit
	public void postInit(FMLPostInitializationEvent e) {
		proxy.postInit();
	}

	@ServerStarting
	public void onServerStarting(FMLServerStartingEvent e) {
		// World-dir-dependent init happens here.
		net.minecraft.server.MinecraftServer srv = net.minecraft.server.MinecraftServer.getServer();
		if (srv != null) {
			QuestConfigPaths.initWorldDir(srv.getFile(srv.getFolderName()));
		}
		e.registerServerCommand(new QuestsCommand());
		e.registerServerCommand(new TitleCommand());
		// Flush on shutdown.
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override public void run() { PlayerStateCache.saveAll(); }
		}, "QuestCycle-flush"));
	}
}
