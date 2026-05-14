package codechicken.translocator;

import codechicken.core.inventory.InventoryUtils;
import codechicken.core.render.FontUtils;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * Filter / Regulate GUI. Renders the standard 3x3 inventory background
 * (reusing the dispenser-style trap.png) plus 9 dummy slots and the player
 * inventory. Each slot's quantity is drawn via {@link FontUtils#drawItemQuantity}
 * which supports stacks up to short max (vanilla slot rendering caps at byte).
 */
public class GuiTranslocator extends GuiContainer {

    public GuiTranslocator(Container par1Container) {
        super(par1Container);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int i, int j) {
        GL11.glPushMatrix();
        GL11.glTranslated(this.guiLeft, this.guiTop, 0.0);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        // 1.4.7 RenderEngine.bindTexture only accepts an int handle, so go
        // through getTexture(path) -> bindTexture(handle).
        this.mc.renderEngine.bindTexture(this.mc.renderEngine.getTexture("/gui/trap.png"));
        this.drawTexturedModalRect(0, 0, 0, 0, this.xSize, this.ySize);
        this.fontRenderer.drawString(
                ((ContainerItemTranslocator) this.inventorySlots).getInvName(), 6, 6, 0x404040);
        this.fontRenderer.drawString(
                StatCollector.translateToLocal("container.inventory"), 6, 72, 0x404040);
        GL11.glPopMatrix();
    }

    /**
     * Called via {@link codechicken.core.inventory.SlotDummy} so we can show
     * the (potentially large) stack count via {@link FontUtils}. In 1.4.7
     * the {@code renderItemOverlayIntoGUI} method has no string-label arg
     * (that got added in 1.5+), so we use the 5-arg form.
     */
    public void drawSlotItem(Slot par1Slot, ItemStack itemstack, int i, int j, String s) {
        itemRenderer.renderItemIntoGUI(this.fontRenderer, this.mc.renderEngine, itemstack, i, j);
        FontUtils.drawItemQuantity(i, j, itemstack, null, 0);
        itemRenderer.renderItemOverlayIntoGUI(
                this.fontRenderer, this.mc.renderEngine,
                InventoryUtils.copyStack(itemstack, 1), i, j);
    }
}
