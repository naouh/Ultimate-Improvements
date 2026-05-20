package com.nao.neiae;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;

/**
 * Bridges NEI's "?" overlay button to AE's ME Crafting Terminal so a recipe
 * can be auto-filled into the terminal's 3x3 matrix from items stored in the
 * ME network (only — player inventory is intentionally ignored).
 *
 * <p>Flow:
 * <ol>
 *   <li>Client: user clicks "?" while the ME Crafting Terminal is open.
 *       NEI invokes our IOverlayHandler (created via dynamic Proxy because
 *       its method signature uses obfuscated MC types that Voldeloom can't
 *       reconcile at compile time).</li>
 *   <li>Client reads each {@code PositionedStack} ingredient via reflection
 *       (its {@code relx/rely} fields tell us the recipe slot 0–8) and
 *       sends a {@code Packet250CustomPayload} to the server.</li>
 *   <li>Server (see {@link ServerHandler}) looks up the open container, casts
 *       to {@code ContainerCraftingTerminal}, walks {@code SlotCraftingMatrix}
 *       slots, and for each ingredient extracts 1 from the ME network via
 *       {@code IMEInventory.extractItems(ItemStack)} and places it in the
 *       matching matrix slot.</li>
 * </ol>
 */
@Mod(modid = "NeiAe",
     name = "NEI -> AE Recipe Bridge",
     version = "1.2.0",
     dependencies = "required-after:NotEnoughItems;required-after:AppliedEnergistics")
@NetworkMod(clientSideRequired = false, serverSideRequired = false,
            channels = { NeiAeMod.CHANNEL }, packetHandler = ServerHandler.class)
public class NeiAeMod {

    public static final String CHANNEL = "NeiAe";
    public static final String TARGET_GUI = "appeng.me.gui.GuiCraftingTerminal";

    @Init
    public void init(FMLInitializationEvent e) {
        // @NetworkMod above already registers ServerHandler — don't re-register
        // here or onPacketData fires twice per packet.
        registerClientOverlay();
    }

    private static void registerClientOverlay() {
        Class<?> guiCls;
        try { guiCls = Class.forName(TARGET_GUI); }
        catch (ClassNotFoundException ex) {
            System.err.println("[NeiAe] AE class not found: " + TARGET_GUI + " — overlay not registered");
            return;
        }

        // NEI's IOverlayHandler interface contains a method using obfuscated MC
        // types (avf GuiContainer, ur ItemStack) which we can't write at
        // compile time in a Voldeloom project. A reflective Proxy implements
        // the interface without us having to mention those types.
        Class<?> overlayItf;
        Class<?> apiCls;
        try {
            overlayItf = Class.forName("codechicken.nei.api.IOverlayHandler");
            apiCls     = Class.forName("codechicken.nei.api.API");
        } catch (ClassNotFoundException ex) {
            System.err.println("[NeiAe] NEI API not found — overlay not registered");
            return;
        }

        try {
            Object handler = Proxy.newProxyInstance(
                overlayItf.getClassLoader(),
                new Class[] { overlayItf },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        // No per-call logging here — NEI can hit equals/hashCode on
                        // every overlay-handler lookup, so anything printed would
                        // spam the log and burn CPU on the render path.
                        String name = method.getName();
                        if ("overlayRecipe".equals(name)
                                && args != null && args.length == 3) {
                            try {
                                ClientOverlay.handleClick(args[0], args[1], (Boolean) args[2]);
                            } catch (Throwable t) {
                                System.err.println("[NeiAe] handleClick threw:");
                                t.printStackTrace();
                            }
                            return null;
                        }
                        if ("equals".equals(name)) {
                            return Boolean.valueOf(proxy == args[0]);
                        }
                        if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        }
                        if ("toString".equals(name)) {
                            return "NeiAe$DynamicOverlayHandler";
                        }
                        return null;
                    }
                });

            apiCls.getMethod("registerGuiOverlay", Class.class, String.class)
                  .invoke(null, guiCls, "crafting");
            apiCls.getMethod("registerGuiOverlayHandler", Class.class, overlayItf, String.class)
                  .invoke(null, guiCls, handler, "crafting");

            System.out.println("[NeiAe] Registered overlay for " + TARGET_GUI);
        } catch (Throwable t) {
            System.err.println("[NeiAe] Overlay registration failed:");
            t.printStackTrace();
        }
    }
}
