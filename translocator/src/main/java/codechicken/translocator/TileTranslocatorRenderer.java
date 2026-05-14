package codechicken.translocator;

import java.util.Map;

import codechicken.core.ClientUtils;
import codechicken.core.alg.MathHelper;
import codechicken.core.colour.CustomGradient;
import codechicken.core.render.CCModel;
import codechicken.core.render.CCRenderState;
import codechicken.core.render.RenderUtils;
import codechicken.core.vec.CoordinateSystem;
import codechicken.core.vec.Matrix4;
import codechicken.core.vec.Rotation;
import codechicken.core.vec.SwapYZ;
import codechicken.core.vec.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.liquids.LiquidStack;
import org.lwjgl.opengl.GL11;

/**
 * Renders both translocator variants. The 3D model lives in
 * {@code gfx/model.obj}; it's a single "Plate" face plus an extruded "Insert"
 * peg that gets scaled vertically by {@code a_insertpos}.
 *
 * <p>For item translocators we draw the moving item stacks in transit.
 * For liquid translocators we draw two interleaved spiral ribbons whose
 * texture comes from the liquid's icon — fast mode adds a second ribbon
 * offset by half a wavelength so the flow looks denser.
 */
public class TileTranslocatorRenderer extends TileEntitySpecialRenderer {

    public static Vector3[] sidePos = new Vector3[] {
        new Vector3(0.5, 0.0, 0.5), new Vector3(0.5, 1.0, 0.5),
        new Vector3(0.5, 0.5, 0.0), new Vector3(0.5, 0.5, 1.0),
        new Vector3(0.0, 0.5, 0.5), new Vector3(1.0, 0.5, 0.5)
    };
    public static Vector3[] sideVec = new Vector3[] {
        new Vector3(0.0, -1.0, 0.0), new Vector3(0.0, 1.0, 0.0),
        new Vector3(0.0, 0.0, -1.0), new Vector3(0.0, 0.0, 1.0),
        new Vector3(-1.0, 0.0, 0.0), new Vector3(1.0, 0.0, 0.0)
    };
    public static CCModel[] plates = new CCModel[6];
    public static CCModel insert;

    private CustomGradient gradient = new CustomGradient("/codechicken/translocator/gfx/grad.png");

    static {
        Map models = CCModel.parseObjModels("/codechicken/translocator/gfx/model.obj", (CoordinateSystem) new SwapYZ());
        plates[0] = (CCModel) models.get("Plate");
        insert = (CCModel) models.get("Insert");
        CCModel.generateSidedModels(plates, 0, new Vector3());
    }

    @Override
    public void renderTileEntityAt(TileEntity tileentity, double x, double y, double z, float f) {
        TileTranslocator ttrans = (TileTranslocator) tileentity;
        double time = ClientUtils.getRenderTime();
        CCRenderState.reset();
        CCRenderState.changeTexture("/codechicken/translocator/gfx/tex.png");
        CCRenderState.pullLightmap();
        CCRenderState.useNormals(true);
        CCRenderState.setColour(-1);
        CCRenderState.startDrawing(4);
        for (int i = 0; i < 6; i++) {
            TileTranslocator.Attachment a = ttrans.attachments[i];
            if (a != null) {
                renderAttachment(i, ttrans.getBlockMetadata(),
                        MathHelper.interpolate(a.b_insertpos, a.a_insertpos, f),
                        a.getIconIndex(), x, y, z);
            }
        }
        CCRenderState.draw();

        if (ttrans instanceof TileItemTranslocator) {
            TileItemTranslocator titrans = (TileItemTranslocator) ttrans;
            for (Object m : titrans.movingItems) {
                GL11.glPushMatrix();
                double d = MathHelper.interpolate(
                        ((TileItemTranslocator.MovingItem) m).b_progress,
                        ((TileItemTranslocator.MovingItem) m).a_progress, f);
                Vector3 pos = getPath(((TileItemTranslocator.MovingItem) m).src,
                                      ((TileItemTranslocator.MovingItem) m).dst, d)
                        .add(itemFloat(((TileItemTranslocator.MovingItem) m).src,
                                       ((TileItemTranslocator.MovingItem) m).dst, d));
                GL11.glTranslated(x + pos.x, y + pos.y - 0.06, z + pos.z);
                GL11.glScaled(0.35, 0.35, 0.35);
                RenderUtils.renderItemUniform(((TileItemTranslocator.MovingItem) m).stack);
                GL11.glPopMatrix();
            }
        }

        if (ttrans instanceof TileLiquidTranslocator) {
            TileLiquidTranslocator tltrans = (TileLiquidTranslocator) ttrans;
            for (Object m : tltrans.movingLiquids()) {
                double start = MathHelper.interpolate(
                        ((TileLiquidTranslocator.MovingLiquid) m).b_start,
                        ((TileLiquidTranslocator.MovingLiquid) m).a_start, f);
                double end = MathHelper.interpolate(
                        ((TileLiquidTranslocator.MovingLiquid) m).b_end,
                        ((TileLiquidTranslocator.MovingLiquid) m).a_end, f);
                this.drawLiquidSpiral(((TileLiquidTranslocator.MovingLiquid) m).src,
                                      ((TileLiquidTranslocator.MovingLiquid) m).dst,
                                      ((TileLiquidTranslocator.MovingLiquid) m).liquid,
                                      start, end, time, 0.0, x, y, z);
                if (((TileLiquidTranslocator.MovingLiquid) m).fast) {
                    this.drawLiquidSpiral(((TileLiquidTranslocator.MovingLiquid) m).src,
                                          ((TileLiquidTranslocator.MovingLiquid) m).dst,
                                          ((TileLiquidTranslocator.MovingLiquid) m).liquid,
                                          start, end, time, 0.5, x, y, z);
                }
            }
        }

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        CCRenderState.changeTexture("/codechicken/translocator/gfx/particle.png");
        CCRenderState.startDrawing(7);
        for (int src = 0; src < 6; src++) {
            TileTranslocator.Attachment asrc = ttrans.attachments[src];
            if (asrc != null && asrc.a_eject) {
                for (int dst = 0; dst < 6; dst++) {
                    TileTranslocator.Attachment adst = ttrans.attachments[dst];
                    if (adst != null && !adst.a_eject) {
                        this.renderLink(src, dst, time, ttrans.xCoord, ttrans.yCoord, ttrans.zCoord);
                    }
                }
            }
        }
        CCRenderState.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private void drawLiquidSpiral(int src, int dst, LiquidStack liquid, double start, double end,
                                  double time, double theta0, double x, double y, double z) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        CCRenderState.setColourOpaque(liquid.asItemStack().getItem().getColorFromItemStack(liquid.asItemStack(), 0));
        Minecraft mc = Minecraft.getMinecraft();
        mc.renderEngine.bindTexture(mc.renderEngine.getTexture("/gui/items.png"));
        // 1.4.7 spritesheet texturing — bindLiquidTexture binds the relevant
        // atlas (terrain or items) and returns an int index into that 16x16
        // grid. We compute UV manually from this index since 1.4.7 doesn't
        // yet have the Icon type that 1.5+ uses for per-sprite UV math.
        int texIdx = RenderUtils.bindLiquidTexture(liquid.itemID, liquid.itemMeta);
        final double uBase = (texIdx % 16) / 16.0;
        final double vBase = (texIdx / 16) / 16.0;
        CCRenderState.startDrawing(7);
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
        t.setTranslation(x, y, z);
        Vector3[] last = new Vector3[] { new Vector3(), new Vector3(), new Vector3(), new Vector3() };
        Vector3[] next = new Vector3[] { new Vector3(), new Vector3(), new Vector3(), new Vector3() };
        double tess = 0.05;
        Vector3 a = getPerp(src, dst);
        boolean rev = sum(a.copy().crossProduct(getPathNormal(src, dst, 0.0))) != sum(sideVec[src]);
        for (double di = end; di <= start; di += tess) {
            Vector3 b = getPathNormal(src, dst, di);
            Vector3 c = getPath(src, dst, di);
            if (rev) {
                b.multiply(-1.0);
            }
            double r = (2.0 * di - time / 10.0 + theta0 + (double) (dst / 6)) * 2.0 * Math.PI;
            double sz = 0.1;
            Vector3 p = c.add(a.copy().multiply(MathHelper.sin(r) * sz))
                         .add(b.copy().multiply(MathHelper.cos(r) * sz));
            double s1 = 0.02;
            double s2 = -0.02;
            next[0].set(p.x + a.x * s1 + b.x * s1, p.y + a.y * s1 + b.y * s1, p.z + a.z * s1 + b.z * s1);
            next[1].set(p.x + a.x * s2 + b.x * s1, p.y + a.y * s2 + b.y * s1, p.z + a.z * s2 + b.z * s1);
            next[2].set(p.x + a.x * s2 + b.x * s2, p.y + a.y * s2 + b.y * s2, p.z + a.z * s2 + b.z * s2);
            next[3].set(p.x + a.x * s1 + b.x * s2, p.y + a.y * s1 + b.y * s2, p.z + a.z * s1 + b.z * s2);
            if (di > end) {
                // Sample within the 16x16 cell of the atlas; ix's stay
                // confined by mod-16-then-fract.
                double u1 = uBase + ((Math.abs(di) * 16.0) % 16.0) / 256.0;
                double u2 = uBase + ((Math.abs(di - tess) * 16.0) % 16.0) / 256.0;
                for (int i = 0; i < 4; i++) {
                    int j = (i + 1) % 4;
                    Vector3 axis = next[j].copy().subtract(next[i]);
                    double v1 = vBase + ((Math.abs(next[i].scalarProject(axis)) * 16.0) % 16.0) / 256.0;
                    double v2 = vBase + ((Math.abs(next[j].scalarProject(axis)) * 16.0) % 16.0) / 256.0;
                    t.addVertexWithUV(next[i].x, next[i].y, next[i].z, u1, v1);
                    t.addVertexWithUV(next[j].x, next[j].y, next[j].z, u1, v2);
                    t.addVertexWithUV(last[j].x, last[j].y, last[j].z, u2, v2);
                    t.addVertexWithUV(last[i].x, last[i].y, last[i].z, u2, v1);
                }
            }
            Vector3[] tmp = last;
            last = next;
            next = tmp;
        }
        CCRenderState.draw();
        t.setTranslation(0.0, 0.0, 0.0);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private double sum(Vector3 v) {
        return v.x + v.y + v.z;
    }

    private void renderLink(int src, int dst, double time, int x, int y, int z) {
        double d = (time + (double) src + (double) (dst * 2)) % 10.0 / 6.0;
        for (int n = 0; n < 20; n++) {
            double dn = d - (double) n * 0.1;
            int spriteX = (int) (7.0 - (double) n * 1.5 - d * 2.0);
            if (MathHelper.between(0.0, dn, 1.0) && spriteX >= 0) {
                Vector3 pos = getPath(src, dst, dn).add((double) x, (double) y, (double) z);
                double b = 1.0;
                double s = 1.0;
                double u1 = (double) spriteX / 8.0;
                double u2 = u1 + 0.125;
                double v1 = 0.0;
                double v2 = 1.0;
                RenderParticle.render(pos.x, pos.y, pos.z,
                        this.gradient.getColour((dn - 0.5) * 1.2 + 0.5).multiplyC(b),
                        s * 0.12, u1, v1, u2, v2);
            }
        }
    }

    private Vector3 itemFloat(int src, int dst, double d) {
        return getPerp(src, dst).multiply(0.01 * MathHelper.sin(d * 4.0 * Math.PI));
    }

    public static Vector3 getPath(int src, int dst, double d) {
        Vector3 v;
        if ((src ^ 1) == dst) {
            v = sideVec[src ^ 1].copy().multiply(d);
        } else {
            Vector3 vsrc = sideVec[src ^ 1];
            Vector3 vdst = sideVec[dst ^ 1];
            Vector3 a = vsrc.copy().multiply(0.3125);
            Vector3 b = vdst.copy().multiply(0.375);
            double sind = MathHelper.sin(d * Math.PI / 2.0);
            double cosd = MathHelper.cos(d * Math.PI / 2.0);
            v = a.multiply(sind).add(b.multiply(cosd - 1.0)).add(vsrc.copy().multiply(0.1875));
        }
        return v.add(sidePos[src]);
    }

    public static Vector3 getPerp(int src, int dst) {
        if ((src ^ 1) == dst) {
            return sideVec[(src + 2) % 6].copy();
        }
        for (int i = 0; i < 3; i++) {
            if (i != src / 2 && i != dst / 2) {
                return sideVec[i * 2].copy();
            }
        }
        return null;
    }

    private static Vector3 getPathNormal(int src, int dst, double d) {
        if ((src ^ 1) == dst) {
            return sideVec[(src + 4) % 6].copy();
        }
        double sind = MathHelper.sin(d * Math.PI / 2.0);
        double cosd = MathHelper.cos(d * Math.PI / 2.0);
        Vector3 vsrc = sideVec[src ^ 1].copy();
        Vector3 vdst = sideVec[dst ^ 1].copy();
        return vsrc.multiply(sind).add(vdst.multiply(cosd)).normalize();
    }

    public static void renderAttachment(int i, int type, double insertpos, int field,
                                        double x, double y, double z) {
        double tx = (double) field / 64.0;
        double ty = (double) type / 2.0;
        plates[i].render(x + 0.5, y + 0.5, z + 0.5, tx, ty);
        // CCC 0.8.1.6 Matrix4 has rotate(Rotation) but not the .apply(ITrans)
        // generic of 0.8.7+; the chain meaning is the same.
        Matrix4 matrix = new Matrix4()
                .translate(new Vector3(x + 0.5, y + 0.5, z + 0.5))
                .rotate(Rotation.sideRotations[i])
                .translate(new Vector3(0.0, -0.5, 0.0))
                .scale(new Vector3(1.0, insertpos * 2.0 / 3.0 + 0.3333333333333333, 1.0));
        insert.render(matrix, tx, ty);
    }
}
