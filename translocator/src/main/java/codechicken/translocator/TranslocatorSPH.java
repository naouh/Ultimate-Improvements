package codechicken.translocator;

import codechicken.core.packet.PacketCustom;
import codechicken.core.packet.PacketCustom.ICustomPacketHandler.IServerPacketHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.tileentity.TileEntity;

/**
 * Server packet handler. The client tells us to either place a crafting grid
 * (placeBlock) or run its recipe (craft). Packet type byte selects the action.
 */
public class TranslocatorSPH implements IServerPacketHandler {

    public static Object channel = Translocator.instance;

    @Override
    public void handlePacket(PacketCustom packet, NetServerHandler nethandler, EntityPlayerMP sender) {
        switch (packet.getType()) {
            case 1: {
                Translocator.blockCraftingGrid.placeBlock(
                        sender.worldObj, (EntityPlayer) sender,
                        packet.readInt(), packet.readInt(), packet.readInt(),
                        packet.readUnsignedByte());
                break;
            }
            case 2: {
                TileEntity tile = sender.worldObj.getBlockTileEntity(
                        packet.readInt(), packet.readInt(), packet.readInt());
                if (tile instanceof TileCraftingGrid) {
                    ((TileCraftingGrid) tile).craft((EntityPlayer) sender);
                }
                break;
            }
        }
    }
}
