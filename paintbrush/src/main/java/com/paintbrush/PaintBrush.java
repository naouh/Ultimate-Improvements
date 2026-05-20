package com.paintbrush;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * PaintBrush — a small standalone item mod for MC 1.4.7 / FTB Ultimate.
 *
 * <p>Adds one reusable item, the Paint Brush. Right-clicking an IC2 {@code IPaintableBlock}
 * (glass fibre cables and the other IC2 cables) flood-fills the connected cable run with the
 * brush's colour; sneak + right-click cycles through the 16 dye colours. The brush is crafted
 * from a stick, a string and any dye, and can be re-coloured by crafting it with a different dye.
 *
 * <p>On the client side, {@link #init} also registers {@link PaintRenderFix}, which clears a
 * long-standing IC2 render glitch (a freshly-painted cable's neighbours keeping stale geometry).
 * That bug affects IC2's own Painter too, so the fix is keyed off the interaction event, not
 * off this item.
 */
@Mod(modid = "PaintBrush",
     name = "PaintBrush",
     version = "0.1.3",
     useMetadata = false,
     dependencies = "after:IC2",
     acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod(clientSideRequired = false, serverSideRequired = false)
public class PaintBrush {

    public static Item itemPaintBrush;

    @PreInit
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
        itemPaintBrush = new ItemPaintBrush();
    }

    @Init
    public void init(FMLInitializationEvent event) {
        GameRegistry.registerItem(itemPaintBrush, "paintBrush");
        registerNames();
        registerRecipes();
        // PaintRenderFix is @SideOnly(CLIENT) and references Minecraft, so it must only load
        // on the client. The reference here is resolved lazily — the server never executes
        // the branch, so the class is never loaded server-side. No SidedProxy needed.
        if (FMLCommonHandler.instance().getSide().isClient()) {
            PaintRenderFix fix = new PaintRenderFix();
            MinecraftForge.EVENT_BUS.register(fix);
            TickRegistry.registerTickHandler(fix, Side.CLIENT);
        }
    }

    /**
     * Localisation. 1.4.7 doesn't auto-load lang files from the jar, so we ship the en_US
     * file under {@code mods/paintbrush/lang/} and also register the name in code so the
     * item resolves even if the lang resource can't be reached.
     */
    private void registerNames() {
        LanguageRegistry.instance().loadLocalization("/mods/paintbrush/lang/en_US.lang", "en_US", false);
        LanguageRegistry.instance().addStringLocalization("item.paintBrush.name", "Paint Brush");
    }

    private void registerRecipes() {
        // New brush: 1 stick + 1 string + 1 dye -> a brush of that dye's colour. One recipe
        // per colour so NEI can list them; the dye slot is ore-dictionary, so modded dyes
        // (Forestry, etc.) work as well as vanilla dye.
        for (int c = 0; c < 16; c++) {
            GameRegistry.addRecipe(new ShapelessOreRecipe(
                    new ItemStack(itemPaintBrush, 1, c),
                    Item.stick, Item.silk, PaintColors.DYE_ORE[c]));
        }
        // Re-colour an already-crafted brush with any dye (handles all 16 input colours).
        GameRegistry.addRecipe(new RecipePaintBrush());
    }
}
