package com.nao.mfrzone;

import com.nao.mfrzone.event.InteractHandler;
import com.nao.mfrzone.network.PacketHandler;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import net.minecraftforge.common.MinecraftForge;

/**
 * Shows a MineFactory Reloaded machine's working area as a BuildCraft laser box for a few seconds:
 * sneak + left-click (empty hand) a Planter / Harvester / Fertilizer and its current radius
 * (including the radius upgrade inside it) lights up around it. No GUI, no behaviour change.
 */
@Mod(modid = MFRZoneMod.MODID, name = MFRZoneMod.NAME, version = MFRZoneMod.VERSION,
     dependencies = "after:MineFactoryReloaded;after:BuildCraft|Core")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            channels = { MFRZoneMod.CHANNEL }, packetHandler = PacketHandler.class)
public class MFRZoneMod {

    public static final String MODID   = "MFRZone";
    public static final String NAME    = "MFR Zone Preview";
    public static final String VERSION = "1.0.0";
    public static final String CHANNEL = "MFRZone";

    @Instance(MODID)
    public static MFRZoneMod instance;

    @SidedProxy(clientSide = "com.nao.mfrzone.client.ClientProxy",
                serverSide = "com.nao.mfrzone.CommonProxy")
    public static CommonProxy proxy;

    @PreInit
    public void preInit(FMLPreInitializationEvent e) {
        Config.load(e.getSuggestedConfigurationFile());
    }

    @Init
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new InteractHandler());
        proxy.init();
    }
}
