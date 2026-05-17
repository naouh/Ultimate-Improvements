package com.quarryplus.render;

import org.lwjgl.opengl.GL11;

import com.quarryplus.tile.TileQuarry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * Quarry drill-arm overlay.
 *
 * <p>Draws the BC-quarry-style mining arm structure on top of the work area:
 *
 * <ul>
 *   <li>An <b>X-bridge</b> running across the work area at {@code y = yMax + 1}, level with
 *       the head's Z position.</li>
 *   <li>A <b>Z-bridge</b> running along the X-bridge, level with the head's X position.</li>
 *   <li>A <b>vertical drill column</b> hanging from the bridge intersection down to the
 *       drill head (the bit that's actually mining).</li>
 *   <li>A small <b>solid drill head</b> cube at the broadcast {@code headPos}.</li>
 * </ul>
 *
 * <p>Pure immediate-mode GL — no models, no animations beyond the head following the
 * server-broadcast position. The "drilling motion" effect is just the head visibly moving
 * across the work area as the server steps {@link TileQuarry#headPosX} etc.
 */
@SideOnly(Side.CLIENT)
public class RenderQuarry extends TileEntitySpecialRenderer {

    private static final float ARM_R = 1.00f, ARM_G = 0.70f, ARM_B = 0.10f;  // BC-quarry orange
    private static final float HEAD_R = 1.00f, HEAD_G = 0.85f, HEAD_B = 0.30f;
    private static final float ROPE_R = 0.40f, ROPE_G = 0.40f, ROPE_B = 0.40f; // grey rope

    @Override
    public void renderTileEntityAt(TileEntity te, double rx, double ry, double rz, float partialTick) {
        if (!(te instanceof TileQuarry)) return;
        TileQuarry tq = (TileQuarry) te;
        if (tq.getNow() == TileQuarry.NONE) return;
        // Don't render if the box isn't set up yet (e.g. server only just resolved it).
        if (tq.xMin == tq.xMax || tq.zMin == tq.zMax) return;

        // Translate from this tile's render origin (rx,ry,rz) back to world space, then to
        // the box / head positions. Everything is computed in render-local coords.
        double ox = rx - te.xCoord;
        double oy = ry - te.yCoord;
        double oz = rz - te.zCoord;

        // Smooth the drill head between server-broadcast positions. partialTick is the
        // 0..1 fraction of the current client frame within the current server tick — lerping
        // prev → current across that window blends jumps into a slide.
        double t = partialTick;
        double lerpedHeadX = tq.prevHeadPosX + (tq.headPosX - tq.prevHeadPosX) * t;
        double lerpedHeadY = tq.prevHeadPosY + (tq.headPosY - tq.prevHeadPosY) * t;
        double lerpedHeadZ = tq.prevHeadPosZ + (tq.headPosZ - tq.prevHeadPosZ) * t;

        double bridgeY = oy + tq.yMax + 1.5;      // bridge sits one block above the top frame
        double headX   = ox + lerpedHeadX;
        double headY   = oy + lerpedHeadY;
        double headZ   = oz + lerpedHeadZ;
        double x1      = ox + tq.xMin + 0.5;
        double x2      = ox + tq.xMax + 0.5;
        double z1      = oz + tq.zMin + 0.5;
        double z2      = oz + tq.zMax + 0.5;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);

        // X-bridge: spans the full X range of the work area, parallel to X, at the head's Z.
        GL11.glLineWidth(4.0f);
        GL11.glColor4f(ARM_R, ARM_G, ARM_B, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, bridgeY, headZ);
        GL11.glVertex3d(x2, bridgeY, headZ);
        // Z-bridge: spans Z, at the head's X position. The two together visually form the
        // bridge + crab/carriage of a BC quarry.
        GL11.glVertex3d(headX, bridgeY, z1);
        GL11.glVertex3d(headX, bridgeY, z2);
        GL11.glEnd();

        // Vertical drill rope from the bridge intersection down to just above the head.
        GL11.glLineWidth(2.0f);
        GL11.glColor4f(ROPE_R, ROPE_G, ROPE_B, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(headX, bridgeY, headZ);
        GL11.glVertex3d(headX, headY + 0.25, headZ);
        GL11.glEnd();

        // Solid drill head — small filled cube at the broadcast headPos.
        GL11.glColor4f(HEAD_R, HEAD_G, HEAD_B, 1.0f);
        drawFilledCube(headX - 0.20, headY - 0.20, headZ - 0.20,
                       headX + 0.20, headY + 0.20, headZ + 0.20);

        // Wireframe outline of the head for a bit of pop.
        GL11.glLineWidth(2.0f);
        GL11.glColor4f(0.4f, 0.25f, 0.0f, 1.0f);
        drawWireCube(headX - 0.21, headY - 0.21, headZ - 0.21,
                     headX + 0.21, headY + 0.21, headZ + 0.21);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopAttrib();
    }

    private static void drawFilledCube(double x1, double y1, double z1,
                                       double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_QUADS);
        // -Y
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        // +Y
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y2, z1);
        // -Z
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y1, z1);
        // +Z
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        // -X
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        // +X
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y1, z2);
        GL11.glEnd();
    }

    private static void drawWireCube(double x1, double y1, double z1,
                                     double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glEnd();
    }
}
