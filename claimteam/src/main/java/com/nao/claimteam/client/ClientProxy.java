package com.nao.claimteam.client;

import com.nao.claimteam.CommonProxy;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        KeyBindingRegistry.registerKeyBinding(new ClaimKeyHandler());
        TickRegistry.registerTickHandler(new ClaimKeyTickHandler(), Side.CLIENT);
        System.out.println("[ClaimTeam] ClientProxy: KeyHandler + KeyTickHandler registered");
    }
}
