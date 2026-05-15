package com.quarryplus;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;

/**
 * 1.4.7 backport of yogpstop's QuarryPlus 2.1.1.
 *
 * <p>The original mod is a sprawling 1.7.10 BuildCraft companion. This backport keeps only the
 * mining quarry (no Filler mode), its markers, the NBT workbench, the enchantment mover and a
 * pair of utility items. Power is BuildCraft 3.x MJ only — RF doesn't exist in 1.4.7.
 *
 * <p>FML lifecycle stages are routed through {@link QuarryPlusI}, which holds the static block /
 * item references and performs registration. The sided proxy ({@link ProxyCommon} /
 * {@link ProxyClient}) binds renderers and other client-only resources.
 */
@Mod(modid = "QuarryPlus",
     name = "QuarryPlus",
     version = "0.1.0-1.4.7",
     useMetadata = false,
     dependencies = "after:BuildCraft|Core",
     acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(channels = {"QuarryPlus"},
            clientSideRequired = true, serverSideRequired = true,
            packetHandler = PacketHandler.class)
public class QuarryPlus {

    public static final String MODID = "QuarryPlus";
    public static final String CHANNEL = "QuarryPlus";

    @SidedProxy(clientSide = "com.quarryplus.ProxyClient",
                serverSide = "com.quarryplus.ProxyCommon")
    public static ProxyCommon proxy;

    @Instance("QuarryPlus")
    public static QuarryPlus instance;

    @PreInit
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
        QuarryPlusI.preInit(event);
    }

    @Init
    public void init(FMLInitializationEvent event) {
        QuarryPlusI.init();
        proxy.registerRenderers();
    }
}
