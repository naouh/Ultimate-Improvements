package com.quarryrange;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = QuarryRange.MODID, name = QuarryRange.NAME, version = QuarryRange.VERSION,
     dependencies = "after:BuildCraft|Factory")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            channels = { QuarryRange.CHANNEL }, packetHandler = PacketHandler.class)
public class QuarryRange {

    public static final String MODID   = "QuarryRange";
    public static final String NAME    = "QuarryRange";
    public static final String VERSION = "1.0.1";
    public static final String CHANNEL = "QRange";

    @Instance(MODID)
    public static QuarryRange instance;

    @SidedProxy(clientSide = "com.quarryrange.ClientProxy",
                serverSide = "com.quarryrange.CommonProxy")
    public static CommonProxy proxy;

    @PreInit
    public void preInit(FMLPreInitializationEvent e) {
        Config.load(e.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        TickRegistry.registerTickHandler(new ServerTick(), Side.SERVER);
    }

    @Init
    public void init(FMLInitializationEvent e) {
        proxy.init();
    }
}
