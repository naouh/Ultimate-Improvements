package codechicken.translocator;

import codechicken.core.ClientUtils;
import codechicken.core.inventory.InventorySimple;
import codechicken.core.packet.ICustomPacketTile;
import codechicken.core.packet.PacketCustom;
import codechicken.core.packet.PacketCustom.ICustomPacketHandler.IClientPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.NetClientHandler;
import net.minecraft.tileentity.TileEntity;

/**
 * Client packet handler.
 *
 * <p>Types 1/2/3 are description packets routed to whatever
 * {@link ICustomPacketTile} sits at the indicated coords. Type 4 opens the
 * filter GUI server-initiated (so the player sees the same window that the
 * server-side {@link net.minecraft.inventory.Container} is bound to). Type 5
 * is a "large itemstack" update (count &gt; 127) used by the filter slots,
 * which need to display arbitrary stack sizes beyond what a normal slot can
 * convey.
 */
public class TranslocatorCPH implements IClientPacketHandler {

    public static Object channel = Translocator.instance;

    @Override
    public void handlePacket(PacketCustom packet, NetClientHandler nethandler, Minecraft mc) {
        switch (packet.getType()) {
            case 1:
            case 2:
            case 3: {
                TileEntity tile = mc.theWorld.getBlockTileEntity(
                        packet.readInt(), packet.readInt(), packet.readInt());
                if (tile instanceof ICustomPacketTile) {
                    ((ICustomPacketTile) tile).handleDescriptionPacket(packet);
                }
                break;
            }
            case 4: {
                int windowId = packet.readUnsignedByte();
                GuiTranslocator gui = new GuiTranslocator(new ContainerItemTranslocator(
                        new InventorySimple(9, packet.readUnsignedShort(), packet.readString()),
                        mc.thePlayer.inventory));
                ClientUtils.openSMPGui(windowId, gui);
                break;
            }
            case 5: {
                mc.thePlayer.openContainer.putStackInSlot(
                        packet.readUnsignedByte(), packet.readItemStack(true));
                break;
            }
        }
    }
}
