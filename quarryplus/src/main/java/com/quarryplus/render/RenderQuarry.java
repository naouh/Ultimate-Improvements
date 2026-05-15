package com.quarryplus.render;

import org.lwjgl.opengl.GL11;

import com.quarryplus.tile.TileQuarry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * Drill-head overlay for a working {@link TileQuarry}.
 *
 * <p>Draws a colored cube floating at the broadcast head position so a player can see what
 * the quarry is currently chewing on without staring at the work area. Pure immediate-mode
 * GL — no entity / no separate tile — keeps the same architecture as {@link RenderMarker}
 * (the marker box overlay).
 *
 * <p>The actual rope/laser from the quarry up to the head is a Phase 6 polish item; this
 * file ships the basic floating-cube indicator so a debugger can confirm the head is moving.
 */
@SideOnly(Side.CLIENT)
public class RenderQuarry extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double rx, double ry, double rz, float partialTick) {
        if (!(te instanceof TileQuarry)) return;
        TileQuarry tq = (TileQuarry) te;
        if (tq.getNow() == TileQuarry.NONE) return;

        double ox = rx - te.xCoord;
        double oy = ry - te.yCoord;
        double oz = rz - te.zCoord;

        double hx = ox + tq.headPosX;
        double hy = oy + tq.headPosY;
        double hz = oz + tq.headPosZ;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(2.0f);
        GL11.glColor4f(0.2f, 0.8f, 1.0f, 1.0f);
        drawCube(hx - 0.15, hy - 0.15, hz - 0.15, hx + 0.15, hy + 0.15, hz + 0.15);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopAttrib();
    }

    private static void drawCube(double x1, double y1, double z1,
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
