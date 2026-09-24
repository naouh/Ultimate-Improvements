package com.nao.mousetweaksng;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;

/**
 * A clean, from-scratch reimplementation of the classic 1.4.7 Mouse Tweaks mod, replacing the
 * old obfuscated BETA 4.5 jar.
 *
 * Behaviour parity with the original three tweaks (right-click distribute, left-click-with-item
 * merge/quick-move, left-click-without-item sweep) but with two improvements:
 *
 *  - it defers to the GUI's own hovered slot ({@code GuiContainer.theSlot}) instead of replicating
 *    slot geometry, so it works on modded inventories without a per-mod compatibility layer; and
 *  - it paces its own automated clicks (see {@link Config#minClickGapMs}) so 1.4.7's one-at-a-time
 *    window-click transaction protocol keeps up — fixing the visual slot desync (crafted/grabbed
 *    items not appearing until you click again) that the old build triggered, worsened by
 *    TickThreading.
 *
 * Client-side only. A {@link SidedProxy} keeps all client classes off a dedicated server, and
 * {@code serverSideRequired = false} lets it connect to servers that don't have it.
 */
@Mod(modid = MouseTweaksNG.MODID, name = MouseTweaksNG.NAME, version = MouseTweaksNG.VERSION)
@NetworkMod(clientSideRequired = true, serverSideRequired = false)
public class MouseTweaksNG {

    public static final String MODID   = "MouseTweaksNG";
    public static final String NAME    = "Mouse Tweaks NG";
    public static final String VERSION = "1.0.1";

    @Instance(MODID)
    public static MouseTweaksNG instance;

    @SidedProxy(clientSide = "com.nao.mousetweaksng.client.ClientProxy",
                serverSide = "com.nao.mousetweaksng.CommonProxy")
    public static CommonProxy proxy;

    @PreInit
    public void preInit(FMLPreInitializationEvent e) {
        Config.load(e.getSuggestedConfigurationFile());
    }

    @Init
    public void init(FMLInitializationEvent e) {
        proxy.init();
    }
}
