package com.nao.claimteam.client;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.client.gui.GuiTextField;
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
    /** Chunk the terrain was last sampled around — re-sampled when the grid center moves. */
    private int sampleCx = Integer.MIN_VALUE;
    private int sampleCz = Integer.MIN_VALUE;
    /** Last player chunk we asked the server for a grid around — drives "follow the player". */
    private int lastReqCx = Integer.MIN_VALUE;
    private int lastReqCz = Integer.MIN_VALUE;

    // ---- Team management panel (right side) ----

    private static final byte MODE_NONE             = 0;
    private static final byte MODE_INPUT_CREATE     = 1;
    private static final byte MODE_INPUT_INVITE     = 2;
    private static final byte MODE_PICK_KICK        = 3;
    private static final byte MODE_PICK_PROMOTE     = 4;
    private static final byte MODE_CONFIRM_DISBAND  = 5;
    private static final byte MODE_CONFIRM_LEAVE    = 6;
    private static final byte MODE_INPUT_ALLY       = 7;
    private static final byte MODE_PICK_UNALLY      = 8;

    private byte teamMode = MODE_NONE;
    private GuiTextField teamInput;
    private String teamInputError;
    /** Buttons currently rendered for the team panel; rebuilt every frame so we can do context-aware buttons. */
    private final List<TeamBtn> teamBtns = new ArrayList<TeamBtn>();
    /** Confirm/Cancel buttons rendered while a modal is open. */
    private final List<TeamBtn> modalBtns = new ArrayList<TeamBtn>();

    private static final class TeamBtn {
        int x, y, w, h;
        String label;
        int color;
        byte action;
        String arg; // optional payload (e.g. member username to kick)
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        // Use the server-advertised radius if we already have data; otherwise request defaults.
        int r = ClientPacketHandler.gridRadius;
        if (r >= 2) radius = r;
        recomputeLayout();

        EntityClientPlayerMP me = Minecraft.getMinecraft().thePlayer;
        int cx = me == null ? 0 : (((int) Math.floor(me.posX)) >> 4);
        int cz = me == null ? 0 : (((int) Math.floor(me.posZ)) >> 4);
        lastReqCx = cx;
        lastReqCz = cz;
        // Optimistically center on the player so the first frame's terrain (sampled below) lines
        // up with the cells, before the server's grid response (which echoes this same center)
        // arrives. Overwrites any stale center left over from a previous open.
        ClientPacketHandler.gridCenterCx = cx;
        ClientPacketHandler.gridCenterCz = cz;
        sampleTerrain(cx, cz);
        sendGridRequest();
    }

    /**
     * Keep the map glued to the player while it's open. The GUI doesn't pause the game
     * ({@link #doesGuiPauseGame()} is false) and 1.4.7 leaves a held movement key "pressed" when a
     * screen opens, so the player can keep walking with the map up. If we never re-centered, the
     * grid would stay on the chunk the map was opened on while the player drifts away, and every
     * click would claim a chunk offset from the cell under the cursor.
     *
     * So: each time the player crosses into a new chunk, request a fresh grid centered on them;
     * and re-sample terrain whenever it falls out of sync with the center the latest server grid
     * used, so the painted terrain and the claim cells never drift apart.
     */
    @Override
    public void updateScreen() {
        EntityClientPlayerMP me = Minecraft.getMinecraft().thePlayer;
        if (me == null) return;
        int cx = ((int) Math.floor(me.posX)) >> 4;
        int cz = ((int) Math.floor(me.posZ)) >> 4;
        if (cx != lastReqCx || cz != lastReqCz) {
            lastReqCx = cx;
            lastReqCz = cz;
            sendGridRequest();
        }
        if (sampleCx != ClientPacketHandler.gridCenterCx || sampleCz != ClientPacketHandler.gridCenterCz) {
            sampleTerrain(ClientPacketHandler.gridCenterCx, ClientPacketHandler.gridCenterCz);
        }
    }

    /** Build the right-side button list based on the current team state. */
    private void rebuildTeamButtons() {
        teamBtns.clear();
        int panelX = originX + gridPx + 14;
        int y = originY;
        int btnW = 120;
        int btnH = 18;
        int gap = 4;

        String team = ClientPacketHandler.myTeam;
        boolean inTeam = team != null && team.length() > 0;
        boolean isOwner = inTeam && ClientPacketHandler.myTeamOwner != null
                && ClientPacketHandler.myTeamOwner.equalsIgnoreCase(Minecraft.getMinecraft().thePlayer.username);

        if (!inTeam) {
            addBtn(panelX, y, btnW, btnH, "Create team", 0xFF2E8B57, MODE_INPUT_CREATE, null); y += btnH + gap;
        } else if (isOwner) {
            addBtn(panelX, y, btnW, btnH, "Invite player", 0xFF2E8B57, MODE_INPUT_INVITE, null);    y += btnH + gap;
            addBtn(panelX, y, btnW, btnH, "Kick member",   0xFFAA5555, MODE_PICK_KICK,    null);    y += btnH + gap;
            addBtn(panelX, y, btnW, btnH, "Promote member",0xFFAA8833, MODE_PICK_PROMOTE, null);    y += btnH + gap;
            addBtn(panelX, y, btnW, btnH, "Add ally",      0xFF2E5FB8, MODE_INPUT_ALLY,   null);    y += btnH + gap;
            addBtn(panelX, y, btnW, btnH, "Remove ally",   0xFF3A6098, MODE_PICK_UNALLY,  null);    y += btnH + gap;
            addBtn(panelX, y, btnW, btnH, "Disband team",  0xFFB22222, MODE_CONFIRM_DISBAND, null); y += btnH + gap;
        } else {
            addBtn(panelX, y, btnW, btnH, "Leave team",    0xFFAA5555, MODE_CONFIRM_LEAVE, null);   y += btnH + gap;
        }
    }

    private void addBtn(int x, int y, int w, int h, String label, int color, byte action, String arg) {
        TeamBtn b = new TeamBtn();
        b.x = x; b.y = y; b.w = w; b.h = h;
        b.label = label; b.color = color; b.action = action; b.arg = arg;
        teamBtns.add(b);
    }

    /** Open the modal matching {@code action}. Caller is responsible for closing any prior modal. */
    private void openModal(byte action, String arg) {
        teamMode = action;
        teamInputError = null;
        if (action == MODE_INPUT_CREATE || action == MODE_INPUT_INVITE || action == MODE_INPUT_ALLY
                || action == MODE_PICK_KICK || action == MODE_PICK_PROMOTE || action == MODE_PICK_UNALLY) {
            // Position the text field where drawModal will draw the panel so they line up.
            int pw = 260;
            int px = (width - pw) / 2;
            int py = modalPanelY(action);
            int inputW = pw - 24;
            int inputX = px + (pw - inputW) / 2;
            int inputY = py + 30;
            teamInput = new GuiTextField(fontRenderer, inputX, inputY, inputW, 18);
            teamInput.setMaxStringLength(16);
            teamInput.setFocused(true);
            if (arg != null) teamInput.setText(arg);
        } else {
            teamInput = null;
        }
    }

    /** Panel height for the given modal mode. */
    private int modalPanelHeight(byte mode) {
        if (mode == MODE_PICK_KICK || mode == MODE_PICK_PROMOTE || mode == MODE_PICK_UNALLY) return 170;
        if (mode == MODE_INPUT_CREATE || mode == MODE_INPUT_INVITE || mode == MODE_INPUT_ALLY) return 110;
        return 90; // confirm dialogs
    }

    /** True while a member/ally picker modal is open (kick, promote, or remove-ally). */
    private boolean isPickerMode() {
        return teamMode == MODE_PICK_KICK || teamMode == MODE_PICK_PROMOTE || teamMode == MODE_PICK_UNALLY;
    }

    /** The list backing the current picker modal: allies for remove-ally, members otherwise. */
    private String[] pickerList() {
        return teamMode == MODE_PICK_UNALLY ? ClientPacketHandler.myTeamAllies
                                            : ClientPacketHandler.myTeamMembers;
    }

    private int modalPanelY(byte mode) {
        int ph = modalPanelHeight(mode);
        return (height - ph) / 2;
    }

    /**
     * Screen rect {x, y, w, h} of picker-chip {@code i} in the kick/promote/remove-ally picker.
     * Shared by {@link #drawModal} and {@link #handleModalClick} so the drawn chips and
     * their click hit-boxes stay aligned (they used to drift apart and swallow clicks).
     */
    private int[] pickerChipRect(int i) {
        int cw = 110, ch = 14, gap = 4;
        int cx = width / 2 - cw - gap / 2;
        int cy = modalPanelY(teamMode) + 70;
        int row = i / 2;
        int col = i % 2;
        return new int[] { cx + col * (cw + gap), cy + row * (ch + gap), cw, ch };
    }

    private void closeModal() {
        teamMode = MODE_NONE;
        teamInput = null;
        teamInputError = null;
    }

    /**
     * Send a team-management action to the server over the mod's own packet channel.
     * We deliberately do NOT dispatch a {@code /team} chat command: on Bukkit/MCPC+ servers
     * the Forge command is gated behind a permission node most players lack, so the GUI would
     * silently fail for them. The server applies its own ownership checks in TeamActions and
     * pushes a fresh grid/team_info packet back, so we don't need to re-request anything here.
     */
    private void sendTeamCmd(byte sub, String arg) {
        byte[] data = PacketHandler.buildTeamCmd(sub, arg == null ? "" : arg);
        Packet250CustomPayload pkt = new Packet250CustomPayload();
        pkt.channel = ClaimTeamMod.CHANNEL;
        pkt.data = data;
        pkt.length = data.length;
        PacketDispatcher.sendPacketToServer(pkt);
    }

    private void confirmModal() {
        switch (teamMode) {
            case MODE_INPUT_CREATE: {
                String name = teamInput == null ? "" : teamInput.getText().trim();
                if (!name.matches("[A-Za-z0-9_\\-]{3,16}")) { teamInputError = "Name: 3-16 chars A-Z 0-9 _ -"; return; }
                sendTeamCmd(PacketHandler.TEAM_CREATE, name);
                closeModal();
                return;
            }
            case MODE_INPUT_INVITE: {
                String n = teamInput == null ? "" : teamInput.getText().trim();
                if (n.length() < 2) { teamInputError = "Enter a player name."; return; }
                sendTeamCmd(PacketHandler.TEAM_INVITE, n);
                closeModal();
                return;
            }
            case MODE_PICK_KICK: {
                String n = teamInput == null ? "" : teamInput.getText().trim();
                if (n.length() < 2) { teamInputError = "Pick or type a member name."; return; }
                sendTeamCmd(PacketHandler.TEAM_KICK, n);
                closeModal();
                return;
            }
            case MODE_PICK_PROMOTE: {
                String n = teamInput == null ? "" : teamInput.getText().trim();
                if (n.length() < 2) { teamInputError = "Pick or type a member name."; return; }
                sendTeamCmd(PacketHandler.TEAM_PROMOTE, n);
                closeModal();
                return;
            }
            case MODE_INPUT_ALLY: {
                String n = teamInput == null ? "" : teamInput.getText().trim();
                if (n.length() < 2) { teamInputError = "Enter a player or team-owner name."; return; }
                sendTeamCmd(PacketHandler.TEAM_ALLY, n);
                closeModal();
                return;
            }
            case MODE_PICK_UNALLY: {
                String n = teamInput == null ? "" : teamInput.getText().trim();
                if (n.length() < 2) { teamInputError = "Pick or type an ally name."; return; }
                sendTeamCmd(PacketHandler.TEAM_UNALLY, n);
                closeModal();
                return;
            }
            case MODE_CONFIRM_DISBAND:
                sendTeamCmd(PacketHandler.TEAM_DISBAND, "");
                closeModal();
                // No team anymore -> close the map; user can re-open later.
                mc.displayGuiScreen(null);
                return;
            case MODE_CONFIRM_LEAVE:
                sendTeamCmd(PacketHandler.TEAM_LEAVE, "");
                closeModal();
                mc.displayGuiScreen(null);
                return;
            default: closeModal();
        }
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
        if (teamMode != MODE_NONE) {
            if (code == Keyboard.KEY_ESCAPE) { closeModal(); return; }
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) { confirmModal(); return; }
            if (teamInput != null) teamInput.textboxKeyTyped(c, code);
            return;
        }
        if (code == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(null);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        // Modal swallows clicks.
        if (teamMode != MODE_NONE) {
            handleModalClick(mx, my, btn);
            return;
        }
        // Team-panel buttons take priority over grid clicks.
        for (int i = 0; i < teamBtns.size(); i++) {
            TeamBtn b = teamBtns.get(i);
            if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                openModal(b.action, b.arg);
                return;
            }
        }
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

        drawTeamPanel();

        if (teamMode != MODE_NONE) drawModal();
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
    private void sampleTerrain(int centerCx, int centerCz) {
        Minecraft m = Minecraft.getMinecraft();
        if (m == null || m.theWorld == null || m.thePlayer == null) {
            terrainColors = null;
            return;
        }
        World w = m.theWorld;
        sampleCx = centerCx;
        sampleCz = centerCz;

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

    private void handleModalClick(int mx, int my, int btn) {
        if (btn != 0) return;
        // Text input click (focus).
        if (teamInput != null) teamInput.mouseClicked(mx, my, btn);
        // Chip click (Kick / Promote / Remove-ally pickers) - autofills the text field.
        if (isPickerMode()) {
            String[] list = pickerList();
            if (list != null) {
                for (int i = 0; i < list.length && i < 8; i++) {
                    int[] r = pickerChipRect(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        if (teamInput != null) { teamInput.setText(list[i]); teamInput.setFocused(true); }
                        return;
                    }
                }
            }
        }
        // Confirm / Cancel buttons.
        for (int i = 0; i < modalBtns.size(); i++) {
            TeamBtn b = modalBtns.get(i);
            if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                if (b.action == MODE_NONE) closeModal();
                else confirmModal();
                return;
            }
        }
    }

    private void drawTeamPanel() {
        rebuildTeamButtons();
        for (int i = 0; i < teamBtns.size(); i++) drawButton(teamBtns.get(i));
    }

    private void drawButton(TeamBtn b) {
        // Slight darken on hover for feedback.
        int color = b.color;
        // 1px border.
        drawRect(b.x, b.y, b.x + b.w, b.y + b.h, 0xFF101418);
        drawRect(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, color);
        drawCenteredString(fontRenderer, b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, 0xFFFFFF);
    }

    private void drawModal() {
        modalBtns.clear();
        // Dim backdrop.
        drawRect(0, 0, width, height, 0xC0000000);
        int pw = 260;
        int ph = modalPanelHeight(teamMode);
        int px = (width - pw) / 2;
        int py = modalPanelY(teamMode);
        // Panel + 1px top/bottom highlight.
        drawRect(px, py, px + pw, py + ph, 0xFF101418);
        drawRect(px + 1, py + 1, px + pw - 1, py + 2, 0xFF606060);
        drawRect(px + 1, py + ph - 2, px + pw - 1, py + ph - 1, 0xFF606060);

        String title;
        switch (teamMode) {
            case MODE_INPUT_CREATE:    title = "Create team"; break;
            case MODE_INPUT_INVITE:    title = "Invite player"; break;
            case MODE_PICK_KICK:       title = "Kick member"; break;
            case MODE_PICK_PROMOTE:    title = "Promote member to owner"; break;
            case MODE_INPUT_ALLY:      title = "Add ally"; break;
            case MODE_PICK_UNALLY:     title = "Remove ally"; break;
            case MODE_CONFIRM_DISBAND: title = "Disband team?"; break;
            case MODE_CONFIRM_LEAVE:   title = "Leave team?"; break;
            default:                   title = "";
        }
        drawCenteredString(fontRenderer, title, width / 2, py + 10, 0xFFFFFF);

        if (teamInput != null) {
            teamInput.drawTextBox();
        }

        if (teamMode == MODE_CONFIRM_DISBAND) {
            drawCenteredString(fontRenderer, "§7All team claims will be released.",
                    width / 2, py + 36, 0xFFAAAAAA);
        } else if (teamMode == MODE_CONFIRM_LEAVE) {
            drawCenteredString(fontRenderer, "§7You will lose access to team claims.",
                    width / 2, py + 36, 0xFFAAAAAA);
        }

        // Chips for kick/promote/remove-ally pickers. Drawn below the input field.
        if (isPickerMode()) {
            drawCenteredString(fontRenderer, "§7Click a name to fill, or type it.",
                    width / 2, py + 56, 0xFFAAAAAA);
            String[] list = pickerList();
            if (list == null || list.length == 0) {
                String empty = teamMode == MODE_PICK_UNALLY ? "§8(no allies)" : "§8(no members)";
                drawCenteredString(fontRenderer, empty, width / 2, py + 74, 0xFF888888);
            } else {
                for (int i = 0; i < list.length && i < 8; i++) {
                    int[] r = pickerChipRect(i);
                    drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF2A3A4A);
                    drawCenteredString(fontRenderer, list[i], r[0] + r[2] / 2, r[1] + 3, 0xFFCCDDEE);
                }
            }
        }

        // Error message just above the buttons.
        if (teamInputError != null) {
            drawCenteredString(fontRenderer, "§c" + teamInputError,
                    width / 2, py + ph - 36, 0xFFFF5555);
        }

        // Confirm + Cancel buttons at the panel's bottom.
        int by = py + ph - 22;
        TeamBtn confirm = new TeamBtn();
        confirm.x = width / 2 - 84; confirm.y = by; confirm.w = 80; confirm.h = 16;
        confirm.label = "Confirm"; confirm.color = 0xFF2E8B57; confirm.action = teamMode;
        TeamBtn cancel = new TeamBtn();
        cancel.x = width / 2 + 4; cancel.y = by; cancel.w = 80; cancel.h = 16;
        cancel.label = "Cancel"; cancel.color = 0xFF555555; cancel.action = MODE_NONE;
        modalBtns.add(confirm);
        modalBtns.add(cancel);
        drawButton(confirm);
        drawButton(cancel);
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
