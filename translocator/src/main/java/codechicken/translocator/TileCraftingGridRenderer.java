package codechicken.translocator;

import codechicken.core.ClientUtils;
import codechicken.core.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Renders the crafting-grid block: a 1x1 flat texture (a 3x3 dotted overlay
 * baked into the terrain atlas), 9 floating items rotating with the player,
 * and — if the recipe matches — the result item hovering above the centre.
 *
 * <p>The "icon" is sampled from {@link BlockCraftingGrid#gridIcon} bound to
 * the terrain texture (so the grid uses the same atlas as world blocks).
 */
public class TileCraftingGridRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float f) {
        TileCraftingGrid tcraft = (TileCraftingGrid) tile;
        Minecraft mc = Minecraft.getMinecraft();
        mc.renderEngine.bindTexture(mc.renderEngine.getTexture("/terrain.png"));
        // In 1.4.7 the BlockCraftingGrid texture is a fixed slot in the
        // terrain spritesheet rather than a registered Icon, so we sample
        // it via the texture index returned by the block.
        int texIdx = Translocator.blockCraftingGrid.getBlockTextureFromSideAndMetadata(1, 0);
        double iconU = (double) (texIdx % 16) / 16.0;
        double iconV = (double) (texIdx / 16) / 16.0;
        double iconU2 = iconU + 1.0 / 16.0;
        double iconV2 = iconV + 1.0 / 16.0;

        Tessellator t = Tessellator.instance;
        t.setTranslation(x, y + 0.001, z);
        t.startDrawingQuads();
        t.setNormal(0.0f, 1.0f, 0.0f);
        t.addVertexWithUV(1.0, 0.0, 0.0, iconU2, iconV);
        t.addVertexWithUV(0.0, 0.0, 0.0, iconU,  iconV);
        t.addVertexWithUV(0.0, 0.0, 1.0, iconU,  iconV2);
        t.addVertexWithUV(1.0, 0.0, 1.0, iconU2, iconV2);
        t.draw();
        t.setTranslation(0.0, 0.0, 0.0);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef((tcraft.rotation + 2) * 90, 0.0f, -1.0f, 0.0f);
        for (int i = 0; i < 9; i++) {
            ItemStack item = tcraft.items[i];
            if (item != null) {
                int row = i / 3;
                int col = i % 3;
                GL11.glPushMatrix();
                GL11.glTranslated((col - 1) * 5 / 16.0,
                                  0.1 + 0.01 * Math.sin((double) i * 1.7 + ClientUtils.getRenderTime() / 5.0),
                                  (row - 1) * 5 / 16.0);
                GL11.glScaled(0.5, 0.5, 0.5);
                RenderUtils.renderItemUniform(item);
                GL11.glPopMatrix();
            }
        }
        if (tcraft.result != null) {
            GL11.glPushMatrix();
            GL11.glTranslated(0.0, 0.6 + 0.02 * Math.sin(ClientUtils.getRenderTime() / 10.0), 0.0);
            GL11.glScaled(0.8, 0.8, 0.8);
            GL11.glRotatef((float) ClientUtils.getRenderTime(), 0.0f, 1.0f, 0.0f);
            RenderUtils.renderItemUniform(tcraft.result);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
    }
}
