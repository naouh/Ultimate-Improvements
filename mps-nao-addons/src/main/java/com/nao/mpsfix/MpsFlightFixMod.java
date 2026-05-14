package com.nao.mpsfix;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Forge mod entry. Bytecode patches are installed by {@link FlightFixCorePlugin}
 * before any class loads. This class only registers the Air Stride module in
 * MPS' {@code ModuleManager} during PostInit.
 *
 * <p>All MPS API calls go through reflection because the deployed MPS jar uses
 * MC obfuscated names in its method signatures, which Voldeloom's compile
 * classpath cannot resolve directly.
 */
@Mod(modid = "MpsFlightFixCM",
     name = "MPS Flight Fix (Coremod)",
     version = "1.0.0",
     dependencies = "required-after:mmmPowersuits")
public class MpsFlightFixMod {

    @Init
    public void init(FMLInitializationEvent e) {
        // Subscribe the right-click handler early so it's active by the time
        // the player can interact with the world. We don't need to wait for
        // PostInit because the handler resolves MPS classes lazily.
        MinecraftForge.EVENT_BUS.register(new OmniWrenchEventHandler());
    }

    @PostInit
    public void postInit(FMLPostInitializationEvent e) {
        try {
            registerAirStride();
        } catch (Throwable t) {
            System.err.println("[MpsFlightFix] Failed to register Air Stride module:");
            t.printStackTrace();
        }
        try {
            registerOmniWrench();
        } catch (Throwable t) {
            System.err.println("[MpsFlightFix] Failed to register OmniWrench module:");
            t.printStackTrace();
        }
        try {
            registerEUReader();
        } catch (Throwable t) {
            System.err.println("[MpsFlightFix] Failed to register EU Reader module:");
            t.printStackTrace();
        }
        try {
            registerTEMultimeter();
        } catch (Throwable t) {
            System.err.println("[MpsFlightFix] Failed to register TE Multimeter module:");
            t.printStackTrace();
        }
    }

    private void registerAirStride() throws Exception {
        // Resolve MPS API entry points reflectively.
        Class<?> cIModularItem    = Class.forName("net.machinemuse.api.IModularItem");
        Class<?> cIPowerModule    = Class.forName("net.machinemuse.api.IPowerModule");
        Class<?> cPowerModule     = Class.forName("net.machinemuse.powersuits.powermodule.PowerModule");
        Class<?> cMuseIcon        = Class.forName("net.machinemuse.general.gui.MuseIcon");
        Class<?> cModularCommon   = Class.forName("net.machinemuse.api.ModularCommon");
        Class<?> cConfig          = Class.forName("net.machinemuse.powersuits.common.Config");
        Class<?> cMPS             = Class.forName("net.machinemuse.powersuits.common.ModularPowersuits");
        Class<?> cItemComp        = Class.forName("net.machinemuse.powersuits.item.ItemComponent");

        // Resolve the head armor instance and category constant.
        Field fHead         = cMPS.getField("powerArmorHead");
        Object powerArmorHead = fHead.get(null);

        Field fCatSpecial   = cModularCommon.getField("CATEGORY_SPECIAL");
        String categorySpecial = (String) fCatSpecial.get(null);

        Field fIconBlue     = cMuseIcon.getField("ORB_1_BLUE");
        Object orbBlue      = fIconBlue.get(null);

        // PowerModule(String, List<IModularItem>, MuseIcon, String) — constructor.
        java.lang.reflect.Constructor<?> pmCtor = cPowerModule.getConstructor(
                String.class, List.class, cMuseIcon, String.class);

        List headOnly = Arrays.asList(cIModularItem.cast(powerArmorHead));
        Object airStride = pmCtor.newInstance(
                AirStrideHelper.MODULE_NAME,
                headOnly,
                orbBlue,
                categorySpecial);

        // setDescription(String) on PowerModule returns PowerModule (chainable).
        Method mSetDesc = cPowerModule.getMethod("setDescription", String.class);
        mSetDesc.invoke(airStride,
                "Inertial dampening field projected at your feet. " +
                "Blocks break at full ground speed even while airborne.");

        // addInstallCost(ItemStack) is on the parent PowerModuleBase; locate via getMethod.
        Method mAddCost = cPowerModule.getMethod("addInstallCost", ItemStack.class);

        // Resolve MPS ItemComponent static instances.
        ItemStack fieldEmitter   = (ItemStack) cItemComp.getField("fieldEmitter").get(null);
        ItemStack controlCircuit = (ItemStack) cItemComp.getField("controlCircuit").get(null);
        ItemStack laserHologram  = (ItemStack) cItemComp.getField("laserHologram").get(null);

        // Config.copyAndResize(ItemStack, int) for proper sizing.
        Method mResize = cConfig.getMethod("copyAndResize", ItemStack.class, int.class);

        mAddCost.invoke(airStride, mResize.invoke(null, fieldEmitter,   Integer.valueOf(4)));
        mAddCost.invoke(airStride, mResize.invoke(null, controlCircuit, Integer.valueOf(8)));
        mAddCost.invoke(airStride, mResize.invoke(null, laserHologram,  Integer.valueOf(2)));
        mAddCost.invoke(airStride, new ItemStack(Item.diamond, 16));
        mAddCost.invoke(airStride, new ItemStack(Block.blockDiamond, 2));

        // Config.addModule(IPowerModule)
        Method mAddModule = cConfig.getMethod("addModule", cIPowerModule);
        mAddModule.invoke(null, airStride);

        System.out.println("[MpsFlightFix] Registered MPS module: " + AirStrideHelper.MODULE_NAME);
    }

    /**
     * Registers the OmniWrench mode on the MPS Power Tool. Behaves like
     * OmniTools' OmniWrench (rotate vanilla / IC2 IWrenchable / TE
     * IReconfigurableFacing, plus BC/Railcraft compatibility via the
     * interfaces injected by {@code PowerToolInterfaceTransformer}). Craft
     * cost: 1 OmniWrench item + 2 Field Emitters.
     */
    private void registerOmniWrench() throws Exception {
        Class<?> cIModularItem  = Class.forName("net.machinemuse.api.IModularItem");
        Class<?> cIPowerModule  = Class.forName("net.machinemuse.api.IPowerModule");
        Class<?> cRCPowerModule = Class.forName("net.machinemuse.powersuits.powermodule.RightClickPowerModule");
        Class<?> cMuseIcon      = Class.forName("net.machinemuse.general.gui.MuseIcon");
        Class<?> cModularCommon = Class.forName("net.machinemuse.api.ModularCommon");
        Class<?> cConfig        = Class.forName("net.machinemuse.powersuits.common.Config");
        Class<?> cMPS           = Class.forName("net.machinemuse.powersuits.common.ModularPowersuits");
        Class<?> cItemComp      = Class.forName("net.machinemuse.powersuits.item.ItemComponent");

        Field fPowerTool = cMPS.getField("powerTool");
        Object powerTool = fPowerTool.get(null);

        Field fCatTool = cModularCommon.getField("CATEGORY_TOOL");
        String categoryTool = (String) fCatTool.get(null);

        // TOOL_PINCH looks closest to a wrench icon in the MuseIcon set;
        // POWERTOOL works too but is generic.
        Field fIcon = cMuseIcon.getField("TOOL_PINCH");
        Object icon = fIcon.get(null);

        // RightClickPowerModule(String, List<IModularItem>, MuseIcon, String)
        Constructor<?> pmCtor = cRCPowerModule.getConstructor(
                String.class, List.class, cMuseIcon, String.class);

        List toolOnly = Arrays.asList(cIModularItem.cast(powerTool));
        Object omniWrench = pmCtor.newInstance(
                OmniWrenchHelper.MODULE_NAME,
                toolOnly,
                icon,
                categoryTool);

        Method mSetDesc = cRCPowerModule.getMethod("setDescription", String.class);
        mSetDesc.invoke(omniWrench,
                "Channels the OmniWrench mechanism through the power tool. "
              + "Rotates blocks (vanilla, IC2 machines, TE machines), and works "
              + "as a wrench for BC pipes, Railcraft track and TE conduits. "
              + "Costs 100 J per rotation, 500 J to pick up an IC2 machine.");

        // Install cost: 1 OmniWrench + 2 Field Emitter.
        Method mAddCost = cRCPowerModule.getMethod("addInstallCost", ItemStack.class);
        Method mResize  = cConfig.getMethod("copyAndResize", ItemStack.class, int.class);

        ItemStack fieldEmitter = (ItemStack) cItemComp.getField("fieldEmitter").get(null);
        mAddCost.invoke(omniWrench, mResize.invoke(null, fieldEmitter, Integer.valueOf(2)));

        // OmniTools' wrench is a static field on the OmniTools class. Look it
        // up reflectively so this code links even if OmniTools isn't present
        // — though if OmniTools is missing, the install cost would be
        // unsatisfiable and the module unobtainable, which is fine: we still
        // register it so it shows up in NEI/dev for inspection.
        try {
            Class<?> cOmniTools = Class.forName("omnitools.OmniTools");
            Object wrenchItem = cOmniTools.getField("wrench").get(null);
            ItemStack wrenchStack = new ItemStack((Item) wrenchItem, 1);
            mAddCost.invoke(omniWrench, wrenchStack);
        } catch (Throwable t) {
            System.err.println("[MpsFlightFix] OmniTools not installed — OmniWrench module install cost will be incomplete");
        }

        Method mAddModule = cConfig.getMethod("addModule", cIPowerModule);
        mAddModule.invoke(null, omniWrench);

        System.out.println("[MpsFlightFix] Registered MPS module: " + OmniWrenchHelper.MODULE_NAME);
    }

    /** EU Reader: cheap meter for IC2 IEnergyTile flows. Same registration
     *  pattern as OmniWrench but uses INDICATOR_1_GREEN (no good wrench icon
     *  in the MuseIcon set, so we reuse one). Install cost: 2 Control Circuits. */
    private void registerEUReader() throws Exception {
        Object module = buildToolModule(EUReaderHelper.MODULE_NAME, "INDICATOR_1_GREEN",
                "Channels IC2's EC Meter through your power tool. Right-click an IC2 "
              + "machine or cable twice — the second click reports average EU/t in, out "
              + "and net, over the elapsed window.");
        addCost(module, "controlCircuit", 2);
        registerModule(module);
    }

    /** TE Multimeter: delegates to TE's multimeter item. Install cost:
     *  1 ThermalExpansion Multimeter + 4 Control Circuits. */
    private void registerTEMultimeter() throws Exception {
        Object module = buildToolModule(TEMultimeterHelper.MODULE_NAME, "INDICATOR_1_BLUE",
                "Channels TE's multimeter through your power tool. Right-click an "
              + "energy conduit, liquiduit, or powered tile to print its current "
              + "saturation, throughput or energy request to your chat.");
        addCost(module, "controlCircuit", 4);
        // The TE multimeter itself as install cost; the helper finds it by
        // class-instance scan since GameRegistry.findItemStack is 1.5+.
        ItemStack teMulti = TEMultimeterHelper.getMultimeterStack();
        if (teMulti != null) {
            addCostStack(module, teMulti);
        }
        registerModule(module);
    }

    /**
     * Shared module-construction shim. Builds a RightClickPowerModule targeting
     * the Power Tool, in the Tool category, with a description and the given
     * MuseIcon name (looked up from {@code MuseIcon} static fields).
     */
    private Object buildToolModule(String name, String iconField, String description) throws Exception {
        Class<?> cIModularItem  = Class.forName("net.machinemuse.api.IModularItem");
        Class<?> cRCPowerModule = Class.forName("net.machinemuse.powersuits.powermodule.RightClickPowerModule");
        Class<?> cMuseIcon      = Class.forName("net.machinemuse.general.gui.MuseIcon");
        Class<?> cModularCommon = Class.forName("net.machinemuse.api.ModularCommon");
        Class<?> cMPS           = Class.forName("net.machinemuse.powersuits.common.ModularPowersuits");

        Object powerTool   = cMPS.getField("powerTool").get(null);
        String categoryTool = (String) cModularCommon.getField("CATEGORY_TOOL").get(null);
        Object icon        = cMuseIcon.getField(iconField).get(null);

        Constructor<?> ctor = cRCPowerModule.getConstructor(
                String.class, List.class, cMuseIcon, String.class);
        Object module = ctor.newInstance(
                name,
                Arrays.asList(cIModularItem.cast(powerTool)),
                icon,
                categoryTool);
        cRCPowerModule.getMethod("setDescription", String.class).invoke(module, description);
        return module;
    }

    /** Add {@code n} of the named ItemComponent (Field Emitter, Control
     *  Circuit, etc.) as an install cost on {@code module}. */
    private void addCost(Object module, String componentField, int n) throws Exception {
        Class<?> cRCPowerModule = Class.forName("net.machinemuse.powersuits.powermodule.RightClickPowerModule");
        Class<?> cItemComp      = Class.forName("net.machinemuse.powersuits.item.ItemComponent");
        Class<?> cConfig        = Class.forName("net.machinemuse.powersuits.common.Config");

        ItemStack base = (ItemStack) cItemComp.getField(componentField).get(null);
        ItemStack sized = (ItemStack) cConfig
                .getMethod("copyAndResize", ItemStack.class, int.class)
                .invoke(null, base, Integer.valueOf(n));
        cRCPowerModule.getMethod("addInstallCost", ItemStack.class).invoke(module, sized);
    }

    /** Add a literal ItemStack as install cost (no resize). */
    private void addCostStack(Object module, ItemStack stack) throws Exception {
        Class<?> cRCPowerModule = Class.forName("net.machinemuse.powersuits.powermodule.RightClickPowerModule");
        cRCPowerModule.getMethod("addInstallCost", ItemStack.class).invoke(module, stack);
    }

    /** Final step: hand the module off to MPS' ModuleManager. */
    private void registerModule(Object module) throws Exception {
        Class<?> cIPowerModule = Class.forName("net.machinemuse.api.IPowerModule");
        Class<?> cConfig       = Class.forName("net.machinemuse.powersuits.common.Config");
        cConfig.getMethod("addModule", cIPowerModule).invoke(null, module);

        // The module class is generic; pull its name out for the log line.
        String name = (String) Class.forName("net.machinemuse.powersuits.powermodule.PowerModule")
                .getMethod("getName").invoke(module);
        System.out.println("[MpsFlightFix] Registered MPS module: " + name);
    }
}
