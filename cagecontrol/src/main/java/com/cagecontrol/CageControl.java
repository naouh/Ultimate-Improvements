package com.cagecontrol;

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
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = CageControl.MODID, name = CageControl.NAME, version = CageControl.VERSION,
     dependencies = "required-after:SoulShards")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            channels = { CageControl.CHANNEL }, packetHandler = PacketHandler.class)
public class CageControl {

    public static final String MODID   = "CageControl";
    public static final String NAME    = "CageControl";
    public static final String VERSION = "1.0.0";
    public static final String CHANNEL = "CageCtrl";

    @Instance(MODID)
    public static CageControl instance;

    @SidedProxy(clientSide = "com.cagecontrol.ClientProxy",
                serverSide = "com.cagecontrol.CommonProxy")
    public static CommonProxy proxy;

    @PreInit
    public void preInit(FMLPreInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new EventHandlerCage());
        NetworkRegistry.instance().registerGuiHandler(this, new GuiHandler());
        TickRegistry.registerTickHandler(new CageCleanupTick(), Side.SERVER);
    }

    @Init
    public void init(FMLInitializationEvent e) {
        proxy.init();
    }

    @ServerStarting
    public void onServerStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new CommandShard());
        e.registerServerCommand(new CommandCageControl());
    }
}
