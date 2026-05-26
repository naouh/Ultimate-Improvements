package com.nao.claimteam.client;

import org.lwjgl.input.Keyboard;

import com.nao.claimteam.ClaimTeamMod;
import com.nao.claimteam.network.PacketHandler;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.world.World;

/**
 * FTB Chunks-style grid map. Left click = claim/unclaim, right click = toggle chunkload.
 *
 * Rendering: centered NxN grid of cells. Each cell is colored by its kind:
 *   - UNCLAIMED          dark gray
 *   - OWN                green
 *   - OWN_CHUNKLOAD      bright green + inner mark
 *   - ALLY               blue
 *   - FOREIGN            red
 *   - FOREIGN_CHUNKLOAD  dark red + inner mark
 *
 * The cell under the player is outlined yellow.
 */
public class ClaimMapGui extends GuiScreen {

    private static final int DEFAULT_CELL_SIZE = 22;
    private int cellSize = DEFAULT_CELL_SIZE;
    private int gridPx;
    private int originX, originY;

    private int radius = 6;
    private int hoverIndex = -1;

    /** ARGB color per cell sampled from the world; null if not yet sampled. */
    private int[] terrainColors;
    /** Player's chunk at the moment of sampling — re-sample if it moves. */
    private int sampleCx = Integer.MIN_VALUE;
    private int sampleCz = Integer.MIN_VALUE;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        // Use the server-advertised radius if we already have data; otherwise request defaults.
        int r = ClientPacketHandler.gridRadius;
        if (r >= 2) radius = r;
        recomputeLayout();
        sampleTerrain();
        sendGridRequest();
    }

    private void recomputeLayout() {
        int side = radius * 2 + 1;
        // Cap cell size so the grid fits the screen height with room for legend
        int maxCellH = (height - 80) / side;
        int maxCellW = (width  - 60) / side;
        cellSize = Math.min(DEFAULT_CELL_SIZE, Math.min(maxCellH, maxCellW));
        if (cellSize < 8) cellSize = 8;
        gridPx = side * cellSize;
        originX = (width - gridPx) / 2;
        originY = (height - gridPx) / 2 + 10;
    }

    private void sendGridRequest() {
        EntityClientPlayerMP me = Minecraft.getMinecraft().thePlayer;
        if (me == null) return;
        int cx = ((int) Math.floor(me.posX)) >> 4;
        int cz = ((int) Math.floor(me.posZ)) >> 4;
        byte[] data = PacketHandler.buildGridRequest(cx, cz, radius);
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = ClaimTeamMod.CHANNEL;
        pkt.data = data;
        pkt.length = data.length;
        PacketDispatcher.sendPacketToServer(pkt);
    }

    private void sendAction(byte action, int cx, int cz) {
        byte[] data = PacketHandler.buildAction(action, cx, cz);
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = ClaimTeamMod.CHANNEL;
        pkt.data = data;
        pkt.length = data.length;
        PacketDispatcher.sendPacketToServer(pkt);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    /**
     * Close only on Esc. The open-map key cannot also close the GUI: the very same key
     * press that opened the screen would otherwise be re-delivered to {@link #keyTyped}
     * by MC's input pipeline and close it again — making the GUI appear to never open.
     */
    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(null);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        int idx = cellAt(mx, my);
        if (idx < 0) return;
        int dx = (idx % (radius * 2 + 1)) - radius;
        int dz = (idx / (radius * 2 + 1)) - radius;
        int cx = ClientPacketHandler.gridCenterCx + dx;
        int cz = ClientPacketHandler.gridCenterCz + dz;
        byte cell = ClientPacketHandler.gridCells == null
                    ? PacketHandler.CELL_UNCLAIMED : ClientPacketHandler.gridCells[idx];
        if (btn == 1) {
            // right click = toggle chunk-load on owned claim only
            if (cell == PacketHandler.CELL_OWN || cell == PacketHandler.CELL_OWN_CHUNKLOAD) {
                sendAction(PacketHandler.ACTION_TOGGLE_CL, cx, cz);
            }
            return;
        }
        if (btn == 0) {
            if (cell == PacketHandler.CELL_UNCLAIMED) {
                sendAction(PacketHandler.ACTION_CLAIM, cx, cz);
            } else if (cell == PacketHandler.CELL_OWN || cell == PacketHandler.CELL_OWN_CHUNKLOAD) {
                sendAction(PacketHandler.ACTION_UNCLAIM, cx, cz);
            }
        }
    }

    private int cellAt(int mx, int my) {
        if (mx < originX || my < originY) return -1;
        int cx = (mx - originX) / cellSize;
        int cz = (my - originY) / cellSize;
        int side = radius * 2 + 1;
        if (cx < 0 || cx >= side || cz < 0 || cz >= side) return -1;
        return cz * side + cx;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "ClaimTeam — map", width / 2, 8, 0xFFFFFF);

        // status line
        String team = ClientPacketHandler.myTeam;
        if (team == null || team.length() == 0) team = "(no team)";
        String status = "Team: §b" + team + "§r   Claims: "
                + ClientPacketHandler.usedClaims + "/" + fmtLimit(ClientPacketHandler.maxClaims)
                + "   Chunk-loaded: "
                + ClientPacketHandler.usedChunkloads + "/" + fmtLimit(ClientPacketHandler.maxChunkloads);
        drawCenteredString(fontRenderer, status, width / 2, 22, 0xCCCCCC);

        // grid background frame
        drawRect(originX - 2, originY - 2, originX + gridPx + 2, originY + gridPx + 2, 0xFF202020);

        int side = radius * 2 + 1;
        byte[] cells = ClientPacketHandler.gridCells;
        String[] teams = ClientPacketHandler.gridTeams;

        hoverIndex = cellAt(mx, my);

        // Pass 1: terrain (darkened on claimed cells) + claim tint + chunkload marker.
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                int idx = j * side + i;
                int x0 = originX + i * cellSize;
                int y0 = originY + j * cellSize;
                int x1 = x0 + cellSize - 1;
                int y1 = y0 + cellSize - 1;
                byte cell = (cells != null && idx < cells.length) ? cells[idx] : PacketHandler.CELL_UNCLAIMED;
                boolean claimed = cell != PacketHandler.CELL_UNCLAIMED;

                int terrain = (terrainColors != null && idx < terrainColors.length) ? terrainColors[idx] : 0;
                if (terrain == 0) terrain = 0xFF3A3A3A;
                // Darken terrain on claimed cells so the tint contrasts cleanly.
                if (claimed) terrain = shade(terrain, 55);
                drawRect(x0, y0, x1, y1, terrain);

                int tint = tintFor(cell);
                if (tint != 0) drawRect(x0, y0, x1, y1, tint);

                if (cell == PacketHandler.CELL_OWN_CHUNKLOAD || cell == PacketHandler.CELL_FOREIGN_CL) {
                    int s = Math.max(4, cellSize / 4);
                    int cx0 = x0 + cellSize / 2 - s / 2;
                    int cy0 = y0 + cellSize / 2 - s / 2;
                    drawRect(cx0, cy0, cx0 + s, cy0 + s, 0xFFFFFFFF);
                }
            }
        }

        // Pass 2: double-line claim borders on the OUTER edges of each claim group.
        // For each side facing a non-same-team neighbor:
        //   - outer ~2px in bright accent
        //   - inner ~1px in dark variant for a 2-tone outline that pops on any background
        final int OUTER_W = Math.max(2, cellSize / 7);
        final int INNER_W = Math.max(1, cellSize / 14);
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                int idx = j * side + i;
                byte cell = (cells != null && idx < cells.length) ? cells[idx] : PacketHandler.CELL_UNCLAIMED;
                int colO = borderFor(cell);
                int colI = borderInnerFor(cell);
                if (colO == 0) continue;

                int x0 = originX + i * cellSize;
                int y0 = originY + j * cellSize;
                int x1 = x0 + cellSize;
                int y1 = y0 + cellSize;

                byte nN = (j > 0)        ? cells[idx - side] : PacketHandler.CELL_UNCLAIMED;
                byte nS = (j < side - 1) ? cells[idx + side] : PacketHandler.CELL_UNCLAIMED;
                byte nW = (i > 0)        ? cells[idx - 1]    : PacketHandler.CELL_UNCLAIMED;
                byte nE = (i < side - 1) ? cells[idx + 1]    : PacketHandler.CELL_UNCLAIMED;

                if (!sameClaimGroup(cell, nN)) {
                    drawRect(x0, y0,            x1, y0 + OUTER_W,           colO);
                    drawRect(x0, y0 + OUTER_W,  x1, y0 + OUTER_W + INNER_W, colI);
                }
                if (!sameClaimGroup(cell, nS)) {
                    drawRect(x0, y1 - OUTER_W,           x1, y1,           colO);
                    drawRect(x0, y1 - OUTER_W - INNER_W, x1, y1 - OUTER_W, colI);
                }
                if (!sameClaimGroup(cell, nW)) {
                    drawRect(x0,            y0, x0 + OUTER_W,           y1, colO);
                    drawRect(x0 + OUTER_W,  y0, x0 + OUTER_W + INNER_W, y1, colI);
                }
                if (!sameClaimGroup(cell, nE)) {
                    drawRect(x1 - OUTER_W,           y0, x1,           y1, colO);
                    drawRect(x1 - OUTER_W - INNER_W, y0, x1 - OUTER_W, y1, colI);
                }
            }
        }

        // Pass 3: hover outline on top of everything.
        if (hoverIndex >= 0) {
            int hi = hoverIndex % side;
            int hj = hoverIndex / side;
            int x0 = originX + hi * cellSize;
            int y0 = originY + hj * cellSize;
            int x1 = x0 + cellSize - 1;
            int y1 = y0 + cellSize - 1;
            drawRect(x0, y0, x1, y0 + 1, 0xFFFFFFFF);
            drawRect(x0, y1 - 1, x1, y1, 0xFFFFFFFF);
            drawRect(x0, y0, x0 + 1, y1, 0xFFFFFFFF);
            drawRect(x1 - 1, y0, x1, y1, 0xFFFFFFFF);
        }

        // outline the player's own chunk (center cell)
        int pIdx = radius * side + radius;
        int pX0 = originX + radius * cellSize;
        int pY0 = originY + radius * cellSize;
        drawRect(pX0, pY0, pX0 + cellSize - 1, pY0 + 2, 0xFFFFEE33);
        drawRect(pX0, pY0 + cellSize - 3, pX0 + cellSize - 1, pY0 + cellSize - 1, 0xFFFFEE33);
        drawRect(pX0, pY0, pX0 + 2, pY0 + cellSize - 1, 0xFFFFEE33);
        drawRect(pX0 + cellSize - 3, pY0, pX0 + cellSize - 1, pY0 + cellSize - 1, 0xFFFFEE33);

        // hover tooltip
        if (hoverIndex >= 0 && teams != null) {
            int dx = (hoverIndex % side) - radius;
            int dz = (hoverIndex / side) - radius;
            int hcx = ClientPacketHandler.gridCenterCx + dx;
            int hcz = ClientPacketHandler.gridCenterCz + dz;
            String t = teams[hoverIndex];
            String label;
            if (t == null || t.length() == 0) label = "chunk (" + hcx + "," + hcz + ") — unclaimed";
            else label = "chunk (" + hcx + "," + hcz + ") — §b" + t + "§r";
            drawCenteredString(fontRenderer, label, width / 2, originY + gridPx + 8, 0xFFFFFF);
        }

        // legend at the bottom
        int ly = height - 28;
        drawCenteredString(fontRenderer,
                "§aown  §2own+CL  §9ally  §cforeign  §4foreign+CL  §7unclaimed",
                width / 2, ly, 0xFFFFFF);
        drawCenteredString(fontRenderer,
                "Left-click: claim / unclaim   Right-click: toggle chunk-load   Esc: close",
                width / 2, ly + 12, 0xAAAAAA);
    }

    private static String fmtLimit(int v) { return v < 0 ? "∞" : String.valueOf(v); }

    /** Solid base color (no terrain available). */
    private static int colorFor(byte cell) {
        switch (cell) {
            case PacketHandler.CELL_OWN:            return 0xFF2E8B57;
            case PacketHandler.CELL_OWN_CHUNKLOAD:  return 0xFF38C172;
            case PacketHandler.CELL_ALLY:           return 0xFF2E5FB8;
            case PacketHandler.CELL_FOREIGN:        return 0xFF8B2E2E;
            case PacketHandler.CELL_FOREIGN_CL:     return 0xFF5A1717;
            case PacketHandler.CELL_UNCLAIMED:
            default:                                 return 0xFF3A3A3A;
        }
    }

    /** Strong tint over a darkened terrain (~50% alpha) — pops against the dimmed background. */
    private static int tintFor(byte cell) {
        switch (cell) {
            case PacketHandler.CELL_OWN:            return 0x802E8B57;
            case PacketHandler.CELL_OWN_CHUNKLOAD:  return 0x9038C172;
            case PacketHandler.CELL_ALLY:           return 0x803A78FF;
            case PacketHandler.CELL_FOREIGN:        return 0x80C13030;
            case PacketHandler.CELL_FOREIGN_CL:     return 0x90801616;
            case PacketHandler.CELL_UNCLAIMED:
            default:                                 return 0;
        }
    }

    /** Bright outer border for claim outer edges (max contrast). */
    private static int borderFor(byte cell) {
        switch (cell) {
            case PacketHandler.CELL_OWN:
            case PacketHandler.CELL_OWN_CHUNKLOAD:  return 0xFF6CFF6C;   // bright lime
            case PacketHandler.CELL_ALLY:           return 0xFF66CCFF;   // cyan
            case PacketHandler.CELL_FOREIGN:
            case PacketHandler.CELL_FOREIGN_CL:     return 0xFFFF4040;   // bright red
            default:                                 return 0;
        }
    }

    /** Darker inner border for double-line effect. */
    private static int borderInnerFor(byte cell) {
        switch (cell) {
            case PacketHandler.CELL_OWN:
            case PacketHandler.CELL_OWN_CHUNKLOAD:  return 0xFF0A4015;
            case PacketHandler.CELL_ALLY:           return 0xFF11447A;
            case PacketHandler.CELL_FOREIGN:
            case PacketHandler.CELL_FOREIGN_CL:     return 0xFF4A0808;
            default:                                 return 0;
        }
    }

    /** Are these two cells in the same "claim group" for border-merging purposes? */
    private static boolean sameClaimGroup(byte a, byte b) {
        if (a == b) return true;
        // own + own+CL share a border; foreign + foreign+CL too.
        if ((a == PacketHandler.CELL_OWN && b == PacketHandler.CELL_OWN_CHUNKLOAD)
         || (b == PacketHandler.CELL_OWN && a == PacketHandler.CELL_OWN_CHUNKLOAD)) return true;
        if ((a == PacketHandler.CELL_FOREIGN && b == PacketHandler.CELL_FOREIGN_CL)
         || (b == PacketHandler.CELL_FOREIGN && a == PacketHandler.CELL_FOREIGN_CL)) return true;
        return false;
    }

    /**
     * Sample the top-block color of each visible chunk. Loaded chunks only — distant cells stay
     * the default gray. Heights are used to apply a brighter/darker shade per cell so relief
     * shows on the map.
     */
    private void sampleTerrain() {
        Minecraft m = Minecraft.getMinecraft();
        if (m == null || m.theWorld == null || m.thePlayer == null) {
            terrainColors = null;
            return;
        }
        World w = m.theWorld;
        sampleCx = ((int) Math.floor(m.thePlayer.posX)) >> 4;
        sampleCz = ((int) Math.floor(m.thePlayer.posZ)) >> 4;

        int side = radius * 2 + 1;
        int n = side * side;
        int[] colors = new int[n];
        int[] heights = new int[n];
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                int cx = sampleCx + (i - radius);
                int cz = sampleCz + (j - radius);
                int wx = (cx << 4) + 8;
                int wz = (cz << 4) + 8;
                int idx = j * side + i;
                colors[idx] = 0;          // 0 = no terrain (default gray rendered later)
                heights[idx] = 64;
                if (!w.blockExists(wx, 64, wz)) continue;
                int y = w.getHeightValue(wx, wz);
                // getHeightValue returns 1 + topY. Walk down past air/transparent to find a real block.
                for (int probe = 0; probe < 16 && y > 0; probe++) {
                    int id = w.getBlockId(wx, y - 1, wz);
                    if (id != 0) break;
                    y--;
                }
                int blockId = w.getBlockId(wx, y - 1, wz);
                if (blockId <= 0 || blockId >= Block.blocksList.length) continue;
                Block bl = Block.blocksList[blockId];
                if (bl == null) continue;
                Material mat = bl.blockMaterial;
                int rgb = 0x666666;
                if (mat != null) {
                    MapColor mc = mat.materialMapColor;
                    if (mc != null) rgb = mc.colorValue;
                }
                colors[idx] = 0xFF000000 | rgb;
                heights[idx] = y;
            }
        }
        // Relief shading: compare each cell to its west neighbor. Higher → brighter, lower → darker.
        int[] shaded = new int[n];
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                int idx = j * side + i;
                int rgb = colors[idx];
                if (rgb == 0) { shaded[idx] = 0; continue; }
                int h = heights[idx];
                int prevH = (i > 0) ? heights[idx - 1] : h;
                int delta = h - prevH;
                int factor;
                if      (delta >  2) factor = 130;
                else if (delta < -2) factor = 72;
                else                 factor = 100;
                shaded[idx] = shade(rgb, factor);
            }
        }
        terrainColors = shaded;
    }

    private static int shade(int argb, int factorPct) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >> 16) & 0xFF) * factorPct / 100;
        int g = ((argb >> 8) & 0xFF) * factorPct / 100;
        int b = (argb & 0xFF) * factorPct / 100;
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
