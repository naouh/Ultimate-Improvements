package com.quarryplus.render;

import org.lwjgl.opengl.GL11;

import com.quarryplus.Config;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.tile.TileMarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * Visual overlay for {@link TileMarker} — three roles in one TESR:
 *
 * <ol>
 *   <li>An <b>unlinked, redstone-powered marker</b> draws three axis beams up to
 *       {@link Config#markerMaxRange} blocks until they hit another marker. This is how a
 *       player aligns two markers along an axis before clicking to form a box.</li>
 *   <li>A <b>linked marker</b> with the lowest-coordinate corner of the box draws the 12-edge
 *       wireframe. Only the min-corner draws so we don't paint the box N times.</li>
 *   <li>A <b>linked marker with a partially-defined box</b> (e.g. only the X axis paired)
 *       additionally shows beams along the still-free axes, again only from the min-corner.</li>
 * </ol>
 *
 * <p>Pure immediate-mode GL ({@code GL_LINES}). No textures, no model files.
 */
@SideOnly(Side.CLIENT)
public class RenderMarker extends TileEntitySpecialRenderer {

    private static final float BEAM_R = 1.0f, BEAM_G = 0.0f, BEAM_B = 0.0f; // red beams
    private static final float BOX_R  = 0.2f, BOX_G  = 0.9f, BOX_B  = 0.2f; // green box

    @Override
    public void renderTileEntityAt(TileEntity te, double rx, double ry, double rz, float partialTick) {
        if (!(te instanceof TileMarker)) return;
        TileMarker tm = (TileMarker) te;

        // Center offset from render origin to the marker's block centre.
        double cx = rx + 0.5;
        double cy = ry + 0.5;
        double cz = rz + 0.5;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glLineWidth(2.0f);

        if (tm.linked && te.xCoord == tm.xMin && te.yCoord == tm.yMin && te.zCoord == tm.zMin) {
            // Linked + this is the min-corner marker → always draw the wireframe box.
            double ox = rx - te.xCoord;
            double oy = ry - te.yCoord;
            double oz = rz - te.zCoord;
            double x1 = ox + tm.xMin, y1 = oy + tm.yMin, z1 = oz + tm.zMin;
            double x2 = ox + tm.xMax + 1.0, y2 = oy + tm.yMax + 1.0, z2 = oz + tm.zMax + 1.0;
            GL11.glColor4f(BOX_R, BOX_G, BOX_B, 1.0f);
            drawBoxEdges(x1, y1, z1, x2, y2, z2);
        }

        // Alignment beams only when redstone-powered (matches the BC landmark / DartCraft
        // original behaviour). Power state is broadcast by the server in
        // TileMarker.broadcastUpdate / synced via NBT in onDataPacket.
        if (tm.poweredLaser) {
            GL11.glColor4f(BEAM_R, BEAM_G, BEAM_B, 1.0f);
            // If linked, only show beams on axes that aren't fully defined yet (player wants
            // to extend the box). If unlinked, show all three axes.
            if (tm.linked) {
                drawBeams(tm, cx, cy, cz,
                        tm.xMin == tm.xMax,
                        tm.yMin == tm.yMax,
                        tm.zMin == tm.zMax);
            } else {
                drawBeams(tm, cx, cy, cz, true, true, true);
            }
        }

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopAttrib();
    }

    /** Draw an axis beam up to {@link Config#markerMaxRange} for each enabled axis. */
    private static void drawBeams(TileMarker tm, double cx, double cy, double cz,
                                  boolean axisX, boolean axisY, boolean axisZ) {
        int range = Config.markerMaxRange;
        if (axisX) {
            int hit = scanForMarker(tm, +1, 0, 0, range);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(cx, cy, cz); GL11.glVertex3d(cx + hit, cy, cz);
            GL11.glEnd();
            hit = scanForMarker(tm, -1, 0, 0, range);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(cx, cy, cz); GL11.glVertex3d(cx - hit, cy, cz);
            GL11.glEnd();
        }
        if (axisY) {
            int hit = scanForMarker(tm, 0, +1, 0, range);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(cx, cy, cz); GL11.glVertex3d(cx, cy + hit, cz);
            GL11.glEnd();
            hit = scanForMarker(tm, 0, -1, 0, range);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(cx, cy, cz); GL11.glVertex3d(cx, cy - hit, cz);
            GL11.glEnd();
        }
        if (axisZ) {
            int hit = scanForMarker(tm, 0, 0, +1, range);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(cx, cy, cz); GL11.glVertex3d(cx, cy, cz + hit);
            GL11.glEnd();
            hit = scanForMarker(tm, 0, 0, -1, range);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(cx, cy, cz); GL11.glVertex3d(cx, cy, cz - hit);
            GL11.glEnd();
        }
    }

    /** Scan from the marker for another marker block in the given direction. */
    private static int scanForMarker(TileMarker tm, int dx, int dy, int dz, int max) {
        for (int d = 1; d <= max; d++) {
            int x = tm.xCoord + dx * d;
            int y = tm.yCoord + dy * d;
            int z = tm.zCoord + dz * d;
            if (tm.getWorldObj().getBlockId(x, y, z) == QuarryPlusI.blockMarker.blockID) return d;
        }
        return max;
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
