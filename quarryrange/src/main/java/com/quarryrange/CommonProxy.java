package com.quarryrange;

public class CommonProxy {
    public void init() {}

    /** Client opens the editor screen; no-op on the dedicated server. */
    public void openEditor(int x, int y, int z, int meta, int curSize, int anchor, int min, int max) {}
}
