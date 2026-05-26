package com.nao.claimteam;

import com.nao.claimteam.chunkload.ChunkLoadManager;
import com.nao.claimteam.chunkload.ChunkLoaderCallback;
import com.nao.claimteam.command.ClaimAdminCommand;
import com.nao.claimteam.command.ClaimCommand;
import com.nao.claimteam.command.TeamCommand;
import com.nao.claimteam.core.ClaimQuery;
import com.nao.claimteam.core.ClaimQueryProvider;
import com.nao.claimteam.event.ProtectionHandler;
import com.nao.claimteam.network.PacketHandler;

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
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = ClaimTeamMod.MODID, name = ClaimTeamMod.NAME, version = ClaimTeamMod.VERSION,
     acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(clientSideRequired = true, serverSideRequired = true,
            channels = { ClaimTeamMod.CHANNEL }, packetHandler = PacketHandler.class)
public class ClaimTeamMod {

    public static final String MODID   = "ClaimTeam";
    public static final String NAME    = "ClaimTeam";
    public static final String VERSION = "0.1.0";
    public static final String CHANNEL = "ClaimTeam";

    @Instance(MODID)
    public static ClaimTeamMod instance;

    @SidedProxy(clientSide = "com.nao.claimteam.client.ClientProxy",
                serverSide = "com.nao.claimteam.CommonProxy")
    public static CommonProxy proxy;

    @PreInit
    public void preInit(FMLPreInitializationEvent e) {
        Config.load(e.getSuggestedConfigurationFile());
        // Make the static query layer answer for real once we have data classes ready.
        ClaimQuery.setProvider(new ClaimQueryProvider());
        proxy.preInit();
    }

    @Init
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new ProtectionHandler());
        ForgeChunkManager.setForcedChunkLoadingCallback(this, new ChunkLoaderCallback());
        proxy.init();
    }

    @PostInit
    public void postInit(FMLPostInitializationEvent e) {
        proxy.postInit();
    }

    @ServerStarting
    public void onServerStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new ClaimCommand("claim",     PacketHandler.ACTION_CLAIM));
        e.registerServerCommand(new ClaimCommand("unclaim",   PacketHandler.ACTION_UNCLAIM));
        e.registerServerCommand(new ClaimCommand("chunkload", PacketHandler.ACTION_TOGGLE_CL));
        e.registerServerCommand(new TeamCommand());
        e.registerServerCommand(new ClaimAdminCommand());
        // Re-attach all chunkload tickets for claims that were chunkload at last shutdown.
        ChunkLoadManager.rebuildFromRegistry();
    }
}
