package com.nao.serverguide;

import com.nao.serverguide.command.GuideCommand;
import com.nao.serverguide.config.GuideContent;
import com.nao.serverguide.network.GuidePacketHandler;

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

/**
 * A small client+server mod that shows an in-game guide book with several pages
 * (Rules, Banned Items, Getting Started). The content is read from editable text
 * files under {@code config/serverguide/} so the pack maintainer can change it
 * without recompiling.
 *
 * Opening: the client keybind (default {@code G}) opens the GUI directly. The
 * {@code /guide} command (registered server-side) sends a one-byte "open" packet
 * to the player so it works from chat too.
 */
@Mod(modid = ServerGuideMod.MODID, name = ServerGuideMod.NAME, version = ServerGuideMod.VERSION,
     acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            channels = { ServerGuideMod.CHANNEL }, packetHandler = GuidePacketHandler.class)
public class ServerGuideMod {

    public static final String MODID   = "serverguide";
    public static final String NAME    = "Server Guide";
    public static final String VERSION = "0.1.0";
    public static final String CHANNEL = "SGuide";

    @Instance(MODID)
    public static ServerGuideMod instance;

    @SidedProxy(clientSide = "com.nao.serverguide.client.ClientProxy",
                serverSide = "com.nao.serverguide.CommonProxy")
    public static CommonProxy proxy;

    @PreInit
    public void preInit(FMLPreInitializationEvent e) {
        // The config dir is known on both sides at preInit. Write default content files if absent.
        GuideContent.init(e.getModConfigurationDirectory());
        proxy.preInit();
    }

    @Init
    public void init(FMLInitializationEvent e) {
        // Push the server's authoritative content to each client on login.
        GameRegistry.registerPlayerTracker(new GuideLoginHandler());
        proxy.init();
    }

    @PostInit
    public void postInit(FMLPostInitializationEvent e) {
        proxy.postInit();
    }

    @ServerStarting
    public void onServerStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new GuideCommand());
    }
}
