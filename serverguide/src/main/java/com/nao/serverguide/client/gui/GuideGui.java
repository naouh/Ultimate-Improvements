package com.nao.serverguide.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.nao.serverguide.client.ClientGuideState;
import com.nao.serverguide.client.GuideKeyHandler;
import com.nao.serverguide.config.GuideContent;
import com.nao.serverguide.config.GuidePage;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The guide book. One tab per content page (Rules / Banned Items / Getting Started); each page is a
 * scrollable, word-wrapped text area that understands the lightweight markup documented in
 * {@link GuideContent}. Pure drawRect rendering, matching the QuestCycle book style.
 */
public final class GuideGui extends GuiScreen {

    private static final int W = 360;
    private static final int H = 220;
    private static final int TAB_H = 18;
    private static final int PAD = 8;
    private static final int LINE_H = 11;
    private static final int GAP_H = 5;
    private static final int SCROLLBAR_W = 4;

    private int left;
    private int top;
    private int pageIndex = 0;
    private int scrollPx = 0;

    private List<GuidePage> pages = new ArrayList<GuidePage>();
    private List<Line> lines = new ArrayList<Line>();
    private int contentHeight = 0;

    /** One fully-resolved drawable line. */
    private static final class Line {
        final String text;   // null = blank spacer
        final int color;
        final int xOffset;   // horizontal offset from the content left edge
        final int topPad;    // extra space before this line (used for headings)
        final boolean dot;   // draw a bullet dot just left of the text
        Line(String text, int color, int xOffset, int topPad, boolean dot) {
            this.text = text; this.color = color; this.xOffset = xOffset; this.topPad = topPad; this.dot = dot;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        left = (width - W) / 2;
        top = (height - H) / 2;
        // Prefer the server-pushed content; fall back to local files (single-player / server without the mod).
        pages = ClientGuideState.get();
        if (pages == null || pages.isEmpty()) pages = GuideContent.load();
        if (pageIndex >= pages.size()) pageIndex = 0;
        rebuild();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private int contentLeft()  { return left + PAD; }
    private int contentTop()   { return top + TAB_H + PAD; }
    private int contentBottom(){ return top + H - PAD; }
    private int contentWidth() { return W - PAD * 2 - SCROLLBAR_W - 2; }
    private int viewHeight()   { return contentBottom() - contentTop(); }

    /** Re-parse the active page into drawable lines for the current width. */
    private void rebuild() {
        lines = new ArrayList<Line>();
        scrollPx = 0;
        if (pages.isEmpty()) return;
        FontRenderer fr = mc.fontRenderer;
        int w = contentWidth();
        String text = pages.get(pageIndex).text;
        String[] raw = text.split("\n", -1);
        for (int i = 0; i < raw.length; i++) {
            String r = trimRight(raw[i]);
            if (r.length() == 0) { lines.add(new Line(null, 0, 0, 0, false)); continue; }
            if (r.startsWith("## ")) {
                addWrapped(fr, r.substring(3), GuiPalette.SUBHEADING, 0, 5, false, w);
            } else if (r.startsWith("# ")) {
                addWrapped(fr, "§l" + r.substring(2), GuiPalette.HEADING, 0, 7, false, w);
            } else if (r.startsWith("- ")) {
                int dotW = fr.getStringWidth("• ");
                addWrapped(fr, r.substring(2), GuiPalette.TEXT, dotW, 0, true, w - dotW);
            } else {
                addWrapped(fr, r, GuiPalette.TEXT, 0, 0, false, w);
            }
        }
        // Total height for scroll clamping.
        contentHeight = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            contentHeight += l.topPad + (l.text == null ? GAP_H : LINE_H);
        }
    }

    /** Wrap {@code body} to {@code maxPx} and append the resulting lines. The first line carries the
     *  bullet dot (if any) and the topPad; continuation lines hang at {@code xOffset}. */
    private void addWrapped(FontRenderer fr, String body, int color, int xOffset, int topPad, boolean dot, int maxPx) {
        List<String> wrapped = wrap(body, maxPx, fr);
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(new Line(wrapped.get(i), color, xOffset, i == 0 ? topPad : 0, dot && i == 0));
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawRect(left, top, left + W, top + H, GuiPalette.BG);
        drawBorder(left, top, W, H, GuiPalette.BORDER);

        drawTabs(mx, my);
        drawContent();
        drawScrollbar();

        super.drawScreen(mx, my, pt);
    }

    private void drawTabs(int mx, int my) {
        FontRenderer fr = mc.fontRenderer;
        int n = pages.size();
        if (n == 0) return;
        int tabW = (W - 4) / n;
        for (int i = 0; i < n; i++) {
            int x = left + 2 + i * tabW;
            boolean active = i == pageIndex;
            boolean hover = mx >= x && mx < x + tabW && my >= top + 2 && my < top + 2 + TAB_H;
            int bg = active ? GuiPalette.TAB_ACTIVE : (hover ? GuiPalette.TAB_HOVER : GuiPalette.TAB_IDLE);
            drawRect(x, top + 2, x + tabW - 1, top + 2 + TAB_H, bg);
            drawRect(x, top + 2 + TAB_H - 2, x + tabW - 1, top + 2 + TAB_H, GuiPalette.ACCENT);
            int color = active ? GuiPalette.TEXT : GuiPalette.TEXT_DIM;
            drawCenteredString(fr, pages.get(i).title, x + tabW / 2, top + 6, color);
        }
    }

    private void drawContent() {
        FontRenderer fr = mc.fontRenderer;
        int cTop = contentTop();
        int cBot = contentBottom();
        int cLeft = contentLeft();
        int y = cTop - scrollPx;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            y += l.topPad;
            int lineH = (l.text == null) ? GAP_H : LINE_H;
            // Only draw lines that fit fully inside the viewport, so nothing bleeds past the top
            // edge (into the tabs) or the bottom edge (out of the panel).
            if (l.text != null && y >= cTop && y + LINE_H <= cBot) {
                if (l.dot) drawString(fr, "•", cLeft, y, GuiPalette.BULLET);
                drawString(fr, l.text, cLeft + l.xOffset, y, l.color);
            }
            y += lineH;
        }
    }

    private void drawScrollbar() {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int trackX = left + W - PAD + 2;
        int trackTop = contentTop();
        int trackH = viewHeight();
        drawRect(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, GuiPalette.SCROLL_TRACK);
        int thumbH = Math.max(16, trackH * trackH / contentHeight);
        int thumbY = trackTop + (trackH - thumbH) * scrollPx / maxScroll;
        drawRect(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, GuiPalette.SCROLL_THUMB);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewHeight());
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) return;
        if (my >= top + 2 && my < top + 2 + TAB_H && pages.size() > 0) {
            int tabW = (W - 4) / pages.size();
            int idx = (mx - left - 2) / tabW;
            if (idx >= 0 && idx < pages.size() && idx != pageIndex) {
                pageIndex = idx;
                rebuild();
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0) return;
        int step = LINE_H * 3;
        scrollPx -= (dWheel > 0 ? step : -step);
        clampScroll();
    }

    private void clampScroll() {
        int max = maxScroll();
        if (scrollPx < 0) scrollPx = 0;
        if (scrollPx > max) scrollPx = max;
    }

    @Override
    protected void keyTyped(char c, int code) {
        if (code == Keyboard.KEY_ESCAPE || code == GuideKeyHandler.KEY_OPEN_GUIDE.keyCode) {
            mc.displayGuiScreen(null);
            return;
        }
        if (code == Keyboard.KEY_DOWN) { scrollPx += LINE_H * 2; clampScroll(); }
        else if (code == Keyboard.KEY_UP) { scrollPx -= LINE_H * 2; clampScroll(); }
        else if (code == Keyboard.KEY_NEXT) { scrollPx += viewHeight(); clampScroll(); }
        else if (code == Keyboard.KEY_PRIOR) { scrollPx -= viewHeight(); clampScroll(); }
        else if (code == Keyboard.KEY_LEFT && pageIndex > 0) { pageIndex--; rebuild(); }
        else if (code == Keyboard.KEY_RIGHT && pageIndex < pages.size() - 1) { pageIndex++; rebuild(); }
    }

    // ---- helpers ----------------------------------------------------

    private static String trimRight(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\r' || s.charAt(end - 1) == '\t')) end--;
        return s.substring(0, end);
    }

    private List<String> wrap(String text, int maxPx, FontRenderer fr) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.length() == 0) return out;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String candidate = line.length() == 0 ? w : line + " " + w;
            if (fr.getStringWidth(candidate) > maxPx && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static void drawBorder(int x, int y, int w, int h, int color) {
        drawRect(x, y, x + w, y + 1, color);
        drawRect(x, y + h - 1, x + w, y + h, color);
        drawRect(x, y, x + 1, y + h, color);
        drawRect(x + w - 1, y, x + w, y + h, color);
    }
}
