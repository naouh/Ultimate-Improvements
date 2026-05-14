package codechicken.translocator;

import codechicken.core.packet.PacketCustom;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import net.minecraftforge.client.MinecraftForgeClient;

/**
 * Client proxy — runs in addition to the server proxy. Binds the two
 * TileEntitySpecialRenderers, registers the custom item renderer for the
 * translocator-in-hand model, and the "g" key binding that drops a crafting
 * grid in front of you.
 */
public class TranslocatorClientProxy extends TranslocatorProxy {

    @Override
    public void load() {
        super.load();
        ClientRegistry.bindTileEntitySpecialRenderer(TileItemTranslocator.class, new TileTranslocatorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileLiquidTranslocator.class, new TileTranslocatorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileCraftingGrid.class, new TileCraftingGridRenderer());
        PacketCustom.assignHandler(TranslocatorCPH.channel, new TranslocatorCPH());
        MinecraftForgeClient.registerItemRenderer(Translocator.blockTranslocator.blockID, new ItemTranslocatorRenderer());
        KeyBindingRegistry.registerKeyBinding(new CraftingGridKeyHandler());
    }
}
