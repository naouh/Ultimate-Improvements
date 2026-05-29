package com.nao.hdv;

/** Server-side proxy (no-op hooks). The client proxy overrides these to register the keybind. */
public class CommonProxy {
	public void preInit() {}
	public void init() {}
	public void postInit() {}
}
