package codechicken.translocator;

import codechicken.core.colour.Colour;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;

/**
 * Helper that pushes one billboarded particle quad to the active Tessellator
 * using the camera-aligned rotation vectors that {@link ActiveRenderInfo}
 * exposes statically (rebuilt once per frame by EntityRenderer). Used to
 * draw the moving "transfer" sparkles between attachments.
 *
 * <p>In 1.5+, EntityFX promoted these rotation values to its own static
 * fields ({@code interpPosX/Y/Z/an/ao}); in 1.4.7 we still go through
 * ActiveRenderInfo.
 */
public class RenderParticle {

    public static void render(double x, double y, double z, Colour colour, double s,
                              double u1, double v1, double u2, double v2) {
        x -= RenderManager.renderPosX;
        y -= RenderManager.renderPosY;
        z -= RenderManager.renderPosZ;
        float par3 = ActiveRenderInfo.rotationX;
        float par4 = ActiveRenderInfo.rotationXZ;
        float par5 = ActiveRenderInfo.rotationZ;
        float par6 = ActiveRenderInfo.rotationYZ;
        float par7 = ActiveRenderInfo.rotationXY;
        Tessellator t = Tessellator.instance;
        t.setColorRGBA(colour.r & 0xFF, colour.g & 0xFF, colour.b & 0xFF, colour.a & 0xFF);
        t.addVertexWithUV(x - par3 * s - par6 * s, y - par4 * s, z - par5 * s - par7 * s, u2, v2);
        t.addVertexWithUV(x - par3 * s + par6 * s, y + par4 * s, z - par5 * s + par7 * s, u2, v1);
        t.addVertexWithUV(x + par3 * s + par6 * s, y + par4 * s, z + par5 * s + par7 * s, u1, v1);
        t.addVertexWithUV(x + par3 * s - par6 * s, y - par4 * s, z + par5 * s - par7 * s, u1, v2);
    }
}
