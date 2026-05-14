package codechicken.translocator;

import java.util.EnumSet;

import codechicken.core.packet.PacketCustom;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.LanguageRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MovingObjectPosition;

/**
 * Bound to "G" by default. If the player is looking at a crafting-grid,
 * triggers a server-side craft. If they're looking at a placeable spot,
 * tells the server to place a new crafting-grid there.
 */
public class CraftingGridKeyHandler extends KeyBindingRegistry.KeyHandler {

    public CraftingGridKeyHandler() {
        super(new KeyBinding[] { new KeyBinding("key.craftingGrid", 46) }, new boolean[1]);
        LanguageRegistry.instance().addStringLocalization("key.craftingGrid", "Crafting Grid");
    }

    @Override
    public void keyDown(EnumSet types, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || tickEnd) {
            return;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != EnumMovingObjectType.TILE) {
            return;
        }
        if (mc.theWorld.getBlockId(hit.blockX, hit.blockY, hit.blockZ) == Translocator.blockCraftingGrid.blockID) {
            PacketCustom packet = new PacketCustom(TranslocatorCPH.channel, 2);
            packet.writeCoord(hit.blockX, hit.blockY, hit.blockZ);
            packet.sendToServer();
            mc.thePlayer.swingItem();
        } else if (Translocator.blockCraftingGrid.placeBlock(mc.theWorld, mc.thePlayer, hit.blockX, hit.blockY, hit.blockZ, hit.sideHit)) {
            PacketCustom packet = new PacketCustom(TranslocatorCPH.channel, 1);
            packet.writeCoord(hit.blockX, hit.blockY, hit.blockZ);
            packet.writeByte(hit.sideHit);
            packet.sendToServer();
        }
    }

    @Override
    public void keyUp(EnumSet types, KeyBinding kb, boolean tickEnd) {
    }

    @Override
    public String getLabel() {
        return "Key Bind handler";
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.CLIENT);
    }
}
