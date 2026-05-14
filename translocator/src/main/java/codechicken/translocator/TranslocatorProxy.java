package codechicken.translocator;

import codechicken.core.CommonUtils;
import codechicken.core.packet.PacketCustom;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Server proxy — does the bulk of the registration work that is needed on both
 * sides. The client proxy extends this and adds rendering / key handlers.
 */
public class TranslocatorProxy {

    public void preInit() {
        Translocator.blockTranslocator = (BlockTranslocator) new BlockTranslocator(
                Translocator.config.getTag("translocator.id").getIntValue(CommonUtils.getFreeBlockID(3942)))
                .setBlockName("translocator")
                .setCreativeTab(CreativeTabs.tabRedstone);

        Translocator.blockCraftingGrid = (BlockCraftingGrid) new BlockCraftingGrid(
                Translocator.config.getTag("crafting grid.id").getIntValue(CommonUtils.getFreeBlockID(3943)))
                .setBlockName("craftingGrid");

        // 1.4.7 doesn't have the per-icon registration of 1.5+; items pull
        // their texture from a 256x256 spritesheet (the items.png we packed
        // with the diamond nugget at cell (0,0)).
        Translocator.itemDiamondNugget = new Item(
                Translocator.config.getTag("diamondNugget.id").getIntValue(24120))
                .setItemName("translocator:diamondNugget")
                .setTextureFile("/mods/translocator/textures/items/items.png")
                .setIconCoord(0, 0)
                .setCreativeTab(CreativeTabs.tabMaterials);

        GameRegistry.registerBlock(Translocator.blockTranslocator, ItemTranslocator.class, "translocator");
        GameRegistry.registerBlock(Translocator.blockCraftingGrid, "craftingGrid");

        MinecraftForge.EVENT_BUS.register(Translocator.blockTranslocator);
        MinecraftForge.EVENT_BUS.register(Translocator.blockCraftingGrid);

        OreDictionary.registerOre("diamondNugget", Translocator.itemDiamondNugget);

        LanguageRegistry.addName(new ItemStack(Translocator.blockTranslocator, 1, 0), "Item Translocator");
        LanguageRegistry.addName(new ItemStack(Translocator.blockTranslocator, 1, 1), "Liquid Translocator");
        LanguageRegistry.addName(Translocator.itemDiamondNugget, "Diamond Nugget");
        // Crafting grid has no inventory item form, so addName(itemstack) doesn't
        // apply — register the bare lang key the block tooltip looks up instead.
        LanguageRegistry.instance().addStringLocalization("tile.craftingGrid.name", "Crafting Grid");
    }

    public void load() {
        GameRegistry.registerTileEntity(TileItemTranslocator.class, "itemTranslocator");
        GameRegistry.registerTileEntity(TileLiquidTranslocator.class, "liquidTranslocator");
        GameRegistry.registerTileEntity(TileCraftingGrid.class, "craftingGrid");

        PacketCustom.assignHandler(TranslocatorSPH.channel, new TranslocatorSPH());

        // Recipes — match the original 1.5.2 Translocator. 'r'=redstone,
        // 'e'=enderpearl, 'i'=iron, 'p'=glass pane, 'g'=gold, 'l'=lapis dye.
        GameRegistry.addRecipe(new ItemStack(Translocator.blockTranslocator, 2, 0),
                "rer", "ipi", "rgr",
                'r', Item.redstone,
                'e', Item.enderPearl,
                'i', Item.ingotIron,
                'g', Item.ingotGold,
                'p', Block.thinGlass);
        GameRegistry.addRecipe(new ItemStack(Translocator.blockTranslocator, 2, 1),
                "rer", "ipi", "rlr",
                'r', Item.redstone,
                'e', Item.enderPearl,
                'i', Item.ingotIron,
                'l', new ItemStack(Item.dyePowder, 1, 4),
                'p', Block.thinGlass);

        // 9 diamond nuggets -> 1 diamond, and back.
        GameRegistry.addShapelessRecipe(new ItemStack(Item.diamond),
                Translocator.itemDiamondNugget, Translocator.itemDiamondNugget, Translocator.itemDiamondNugget,
                Translocator.itemDiamondNugget, Translocator.itemDiamondNugget, Translocator.itemDiamondNugget,
                Translocator.itemDiamondNugget, Translocator.itemDiamondNugget, Translocator.itemDiamondNugget);
        GameRegistry.addShapelessRecipe(new ItemStack(Translocator.itemDiamondNugget, 9), Item.diamond);
    }
}
