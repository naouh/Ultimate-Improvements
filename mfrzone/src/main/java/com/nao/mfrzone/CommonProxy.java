package com.nao.mfrzone;

/** Server-side proxy. The laser preview is client-only, so showing it is a no-op here. */
public class CommonProxy {

    public void init() {}

    /** Show a laser box spanning the given AABB corners. No-op on the server. */
    public void showBox(int x1, int y1, int z1, int x2, int y2, int z2) {}
}
