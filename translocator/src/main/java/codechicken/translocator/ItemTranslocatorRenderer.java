package codechicken.translocator;

import codechicken.core.render.CCRenderState;
import codechicken.core.vec.Vector3;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Custom item-in-hand renderer for the translocator block — instead of
 * the engine flat-billboard fallback (which would look bad for a 3D model),
 * we render the same plate-and-insert geometry the world uses, scaled and
 * shifted to look nice in hand / inventory.
 */
public class ItemTranslocatorRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Vector3 d = new Vector3();
        // 1.4.7 doesn't yet split EQUIPPED into EQUIPPED + EQUIPPED_FIRST_PERSON
        // (that came in 1.5+) — a single EQUIPPED is sufficient here.
        if (type != ItemRenderType.EQUIPPED) {
            d.add(-0.5, -0.5, -0.5);
        } else {
            d.add(0.0, -0.2, -0.2);
        }
        d.add(0.0, 0.0, 0.5);
        GL11.glPushMatrix();
        GL11.glScaled(1.5, 1.5, 1.5);
        CCRenderState.changeTexture("/codechicken/translocator/gfx/tex.png");
        CCRenderState.pullLightmap();
        CCRenderState.setColour(-1);
        CCRenderState.useNormals(true);
        CCRenderState.startDrawing(4);
        TileTranslocatorRenderer.renderAttachment(2, item.getItemDamage(), 1.0, 0, d.x, d.y, d.z);
        CCRenderState.draw();
        GL11.glPopMatrix();
    }
}
