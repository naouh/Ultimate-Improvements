package com.nao.voicechat;

import com.nao.voicechat.network.HandshakePacketHandler;
import com.nao.voicechat.proto.VoiceProto;
import com.nao.voicechat.server.PlayerJoinTracker;
import com.nao.voicechat.server.VoiceServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.Mod.ServerStarting;
import cpw.mods.fml.common.Mod.ServerStopping;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(modid = VoiceChatMod.MODID, name = VoiceChatMod.NAME, version = VoiceChatMod.VERSION,
     acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(clientSideRequired = false, serverSideRequired = false,
            channels = { VoiceProto.CTRL_CHANNEL }, packetHandler = HandshakePacketHandler.class)
public class VoiceChatMod {

    public static final String MODID   = "VoiceChat";
    public static final String NAME    = "VoiceChat";
    public static final String VERSION = "0.1.0";

    @Instance(MODID)
    public static VoiceChatMod instance;

    @SidedProxy(clientSide = "com.nao.voicechat.client.ClientProxy",
                serverSide = "com.nao.voicechat.CommonProxy")
    public static CommonProxy proxy;

    @PreInit
    public void preInit(FMLPreInitializationEvent e) {
        VoiceConfig.load(e.getSuggestedConfigurationFile());
        java.io.File configDir = e.getSuggestedConfigurationFile().getParentFile();
        com.nao.voicechat.client.MuteList.load(configDir);
        if (FMLCommonHandler.instance().getSide().isClient()) {
            com.nao.voicechat.client.MicIconTexture.setConfigDir(configDir);
        }
        proxy.preInit();
    }

    @Init
    public void init(FMLInitializationEvent e) {
        if (FMLCommonHandler.instance().getSide().isServer() ||
            FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            // Integrated server registers it again on ServerStarting; nothing to do here.
        }
        proxy.init();
    }

    @PostInit
    public void postInit(FMLPostInitializationEvent e) {
        proxy.postInit();
    }

    @ServerStarting
    public void onServerStarting(FMLServerStartingEvent e) {
        VoiceServer.start(VoiceConfig.udpPort);
        GameRegistry.registerPlayerTracker(new PlayerJoinTracker());
    }

    @ServerStopping
    public void onServerStopping(FMLServerStoppingEvent e) {
        VoiceServer.stop();
    }
}
