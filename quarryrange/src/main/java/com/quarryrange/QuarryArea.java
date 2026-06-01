package com.quarryrange;

import net.minecraftforge.common.ForgeDirection;

/**
 * Computes a quarry's axis-aligned box from its position, facing and the chosen size/anchor.
 *
 * <p>{@code size} is the TOTAL box edge (frame included); the mined interior is {@code size-2}.
 * The vertical extent mirrors BuildCraft's default (5 tall, from the quarry level upward); the
 * quarry still digs straight down to bedrock under the whole footprint.
 *
 * <p>The extend direction matches BuildCraft's own default placement: {@code o = facingMeta
 * .getOpposite()} (i.e. the way the player was looking when placing). The area always starts one
 * block in front of the quarry; the anchor decides how it spreads sideways:
 * <ul>
 *   <li>{@code FRONT}        – centered on the quarry,</li>
 *   <li>{@code CORNER_LEFT}  – the quarry sits at the right edge, area spreads to its left,</li>
 *   <li>{@code CORNER_RIGHT} – the quarry sits at the left edge, area spreads to its right.</li>
 * </ul>
 */
public final class QuarryArea {

    public static final int ANCHOR_FRONT        = 0;
    public static final int ANCHOR_CORNER_LEFT  = 1;
    public static final int ANCHOR_CORNER_RIGHT = 2;

    private static final int Y_SIZE = 5;

    private QuarryArea() {}

    /** @return {xMin, yMin, zMin, xMax, yMax, zMax} */
    public static int[] compute(int qx, int qy, int qz, int meta, int size, int anchor) {
        ForgeDirection o = orient(meta);
        int half = (size - 1) / 2;

        // Depth: the box always extends one block in front of the quarry along the facing axis.
        int xMin = 0, zMin = 0, xMax = 0, zMax = 0;
        switch (o) {
            case EAST:  xMin = qx + 1;     xMax = qx + size; break;
            case WEST:  xMax = qx - 1;     xMin = qx - size; break;
            case SOUTH: zMin = qz + 1;     zMax = qz + size; break;
            default:    zMax = qz - 1;     zMin = qz - size; break; // NORTH
        }

        // Sideways spread, perpendicular to the facing axis. "left"/"right" are from the quarry's
        // point of view looking along o.
        int lo, hi; // perpendicular min/max
        if (anchor == ANCHOR_FRONT) {
            lo = -half; hi = lo + size - 1;          // centered
        } else if (anchor == ANCHOR_CORNER_RIGHT) {
            lo = 0; hi = size - 1;                    // spread to the right
        } else { // ANCHOR_CORNER_LEFT
            hi = 0; lo = -(size - 1);                 // spread to the left
        }
        // Map left/right onto the world axis depending on facing.
        switch (o) {
            case EAST:  zMin = qz + lo; zMax = qz + hi; break;          // right = +z
            case WEST:  zMin = qz - hi; zMax = qz - lo; break;          // right = -z
            case SOUTH: xMin = qx - hi; xMax = qx - lo; break;          // right = -x
            default:    xMin = qx + lo; xMax = qx + hi; break;          // NORTH: right = +x
        }

        return new int[] { xMin, qy, zMin, xMax, qy + Y_SIZE - 1, zMax };
    }

    /** Mirrors BuildCraft TileQuarry.setBoundaries: the area extends toward facing.getOpposite(). */
    public static ForgeDirection orient(int meta) {
        ForgeDirection[] vals = ForgeDirection.values();
        ForgeDirection base = (meta >= 0 && meta < vals.length) ? vals[meta] : ForgeDirection.NORTH;
        ForgeDirection o = base.getOpposite();
        if (o != ForgeDirection.EAST && o != ForgeDirection.WEST
                && o != ForgeDirection.SOUTH && o != ForgeDirection.NORTH) {
            return ForgeDirection.NORTH;
        }
        return o;
    }
}
