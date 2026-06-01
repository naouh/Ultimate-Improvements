package com.nao.serverguide.client;

import java.util.List;

import com.nao.serverguide.config.GuidePage;

/**
 * Client-side cache of the guide pages the server pushed on login. When set, the GUI shows these
 * instead of the client's local files, so the server's {@code config/serverguide/} is the single
 * source of truth. Stays null in single-player until the integrated server pushes (or if the server
 * lacks the mod), in which case the GUI falls back to local files.
 */
public final class ClientGuideState {
    private ClientGuideState() {}

    private static volatile List<GuidePage> pages;

    public static void set(List<GuidePage> p) { pages = p; }
    public static List<GuidePage> get() { return pages; }
}
