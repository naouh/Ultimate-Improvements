package com.nao.voicechat.client;

import com.nao.voicechat.CommonProxy;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        KeyBindingRegistry.registerKeyBinding(new VoiceKeyHandler());
        TickRegistry.registerTickHandler(new VoiceTickHandler(), Side.CLIENT);
        VoiceHud hud = new VoiceHud();
        TickRegistry.registerTickHandler(hud, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(hud);
        System.out.println("[VoiceChat] ClientProxy: keys + HUD + tick handler registered");
    }
}
