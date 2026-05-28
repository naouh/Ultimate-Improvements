package com.nao.questcycle;

/**
 * Common-side proxy. Server uses this directly; client extends via ClientProxy.
 * Hooks for things that need a per-side implementation (keybinds, HUD, etc.).
 */
public class CommonProxy {
	public void preInit() {
	}

	public void init() {
	}

	public void postInit() {
	}
}
