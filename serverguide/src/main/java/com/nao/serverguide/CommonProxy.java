package com.nao.serverguide;

public class CommonProxy {
    public void preInit() {}
    public void init() {}
    public void postInit() {}

    /** Client-side hook to open the guide GUI. No-op on a dedicated server. */
    public void openGuide() {}
}
