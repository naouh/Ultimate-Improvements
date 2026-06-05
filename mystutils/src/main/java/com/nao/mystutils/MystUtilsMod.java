package com.nao.mystutils;

import com.nao.mystutils.command.CommandInstabilities;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.ServerStarting;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * The plain {@code @Mod} half of Myst Utils, loaded from the coremod jar because the manifest sets
 * {@code FMLCorePluginContainsFMLMod: true}.
 *
 * <p>Its only job is registering the {@code /instabilities} command. The writing-desk search bar is
 * implemented entirely by the coremod's ASM transformers (see {@link MystUtilsCorePlugin}) and needs
 * nothing here.
 */
@Mod(modid = "mystutils", name = "Myst Utils", version = "1.0.0", dependencies = "after:Mystcraft")
public class MystUtilsMod {

    @ServerStarting
    public void onServerStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new CommandInstabilities());
    }
}
