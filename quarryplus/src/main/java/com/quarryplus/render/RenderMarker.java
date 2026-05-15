package com.quarryplus.render;

import org.lwjgl.opengl.GL11;

import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * Box-edge overlay for linked markers. Only the marker that owns the lowest-coordinate
 * corner of the box does the actual drawing — every other linked marker in the box draws
 * nothing — so we don't paint the same 12 edges N times for N markers.
 *
 * <p>Pure immediate-mode GL ({@code GL_LINES}). No textures, no model files — 1.4.7 fits
 * this kind of overlay naturally and the result reads the same as the 1.7.10 original.
 */
@SideOnly(Side.CLIENT)
public class RenderMarker extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double rx, double ry, double rz, float partialTick) {
        if (!(te instanceof TileMarker)) return;
        TileMarker tm = (TileMarker) te;
        if (!tm.linked) return;

        // Only the bottom-NW-floor marker draws — the others in the same box bail out.
        if (te.xCoord != tm.xMin || te.yCoord != tm.yMin || te.zCoord != tm.zMin) return;

        // Translate from this tile's render origin back to world-space, then to the box min.
        double ox = rx - te.xCoord;
        double oy = ry - te.yCoord;
        double oz = rz - te.zCoord;

        double x1 = ox + tm.xMin;
        double y1 = oy + tm.yMin;
        double z1 = oz + tm.zMin;
        double x2 = ox + tm.xMax + 1.0;
        double y2 = oy + tm.yMax + 1.0;
        double z2 = oz + tm.zMax + 1.0;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glLineWidth(2.0f);
        GL11.glColor4f(1.0f, 0.0f, 0.0f, 1.0f); // red = X edges
        drawBoxEdges(x1, y1, z1, x2, y2, z2);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopAttrib();
    }

    private static void drawBoxEdges(double x1, double y1, double z1,
                                     double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_LINES);
        // bottom rectangle
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y1, z1);
        // top rectangle
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        // verticals
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glEnd();
    }
}
