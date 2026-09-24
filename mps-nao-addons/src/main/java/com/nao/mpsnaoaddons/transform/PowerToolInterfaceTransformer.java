package com.nao.mpsnaoaddons.transform;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Bolts the cross-mod wrench/hammer/crowbar interfaces onto MPS'
 * {@code ItemPowerTool} so that mods like BuildCraft, MineFactoryReloaded,
 * Railcraft and Universal Electricity recognise the power tool as a valid
 * wrench when the OmniWrench module is the active mode.
 *
 * <p>The added methods delegate to {@link com.nao.mpsnaoaddons.OmniWrenchHelper},
 * which inspects {@code player.getHeldItem()} to decide whether the OmniWrench
 * mode is currently active. canX methods return that boolean. onX/wrenchUsed
 * methods drain energy and play the swing animation.
 *
 * <p>Each interface is added conditionally: if its mod isn't installed in this
 * runtime (the interface class isn't on the loader's path), we skip it so we
 * don't poison MPS with unresolvable parents. The generated signatures match
 * the 1.4.7-era APIs shipped in the pack (Railcraft's {@code IToolCrowbar}
 * already carries the {@code ItemStack} parameter there).
 *
 * <p>Air right-clicks (ME Wireless Terminal) are NOT handled here: MPS'
 * {@code ItemPowerTool} already overrides {@code onItemRightClick}, and Forge
 * 1.4.7 fires {@code PlayerInteractEvent.RIGHT_CLICK_AIR} server-side from
 * {@code NetServerHandler}, so {@code OmniWrenchEventHandler} covers it.
 */
public class PowerToolInterfaceTransformer implements IClassTransformer {

    private static final String TARGET_OBF   = "net.machinemuse.powersuits.item.ItemPowerTool";
    private static final String TARGET_SLASH = "net/machinemuse/powersuits/item/ItemPowerTool";

    private static final String HELPER          = "com/nao/mpsnaoaddons/OmniWrenchHelper";
    // Obfuscated 1.4.7 MC type internals — runtime expects these in descriptors.
    private static final String OBF_PLAYER   = "qx";
    private static final String OBF_ITEMSTK  = "ur";
    private static final String OBF_MINECART = "py";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!TARGET_OBF.equals(name)
                && !TARGET_SLASH.equals(name)) {
            return bytes;
        }
        System.out.println("[MpsNaoAddons] PowerToolInterfaceTransformer invoked on " + name);

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            List<String> addedInterfaces = new ArrayList<String>();

            // BuildCraft IToolWrench.
            if (isClassPresent("buildcraft.api.tools.IToolWrench")) {
                cn.interfaces.add("buildcraft/api/tools/IToolWrench");
                cn.methods.add(makeCanWrenchMethod("canWrench"));
                cn.methods.add(makeOnWrenchMethod("wrenchUsed"));
                addedInterfaces.add("buildcraft.api.tools.IToolWrench");
            }

            // Universal Electricity IToolConfigurator — same shape as BC.
            if (isClassPresent("universalelectricity.prefab.implement.IToolConfigurator")) {
                cn.interfaces.add("universalelectricity/prefab/implement/IToolConfigurator");
                // UE uses the same method names as BC. If BC was added above
                // those methods already exist; only add if not present.
                if (!hasMethod(cn, "canWrench", "(L" + OBF_PLAYER + ";III)Z")) {
                    cn.methods.add(makeCanWrenchMethod("canWrench"));
                    cn.methods.add(makeOnWrenchMethod("wrenchUsed"));
                }
                addedInterfaces.add("universalelectricity.prefab.implement.IToolConfigurator");
            }

            // MFR/TE IToolHammer — marker interface, no methods.
            if (isClassPresent("powercrystals.minefactoryreloaded.api.IToolHammer")) {
                cn.interfaces.add("powercrystals/minefactoryreloaded/api/IToolHammer");
                addedInterfaces.add("powercrystals.minefactoryreloaded.api.IToolHammer (marker)");
            }

            // Railcraft IToolCrowbar.
            if (isClassPresent("railcraft.common.api.core.items.IToolCrowbar")) {
                cn.interfaces.add("railcraft/common/api/core/items/IToolCrowbar");
                cn.methods.add(makeCanWhackMethod("canWhack"));
                cn.methods.add(makeOnWhackMethod("onWhack"));
                cn.methods.add(makeCanCartMethod("canLink"));
                cn.methods.add(makeOnCartMethod("onLink"));
                cn.methods.add(makeCanCartMethod("canBoost"));
                cn.methods.add(makeOnCartMethod("onBoost"));
                addedInterfaces.add("railcraft.common.api.core.items.IToolCrowbar");
            }

            if (addedInterfaces.isEmpty()) {
                System.out.println("[MpsNaoAddons] No wrench-side interfaces available — skipping ItemPowerTool patch");
                return bytes;
            }

            // COMPUTE_MAXS only; see note in EntityPlayerTransformer.
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MpsNaoAddons] ItemPowerTool now implements: " + addedInterfaces);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MpsNaoAddons] PowerToolInterfaceTransformer failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    private static boolean isClassPresent(String fqcn) {
        try {
            Class.forName(fqcn, false, PowerToolInterfaceTransformer.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasMethod(ClassNode cn, String name, String desc) {
        for (Object o : cn.methods) {
            MethodNode m = (MethodNode) o;
            if (name.equals(m.name) && desc.equals(m.desc)) return true;
        }
        return false;
    }

    /**
     * {@code public boolean canWrench(EntityPlayer p, int x, int y, int z) {
     *     return OmniWrenchHelper.canWrenchAt(p, x, y, z);
     * }}
     */
    private static MethodNode makeCanWrenchMethod(String methodName) {
        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                "(L" + OBF_PLAYER + ";III)Z",
                null, null);
        m.visitVarInsn(Opcodes.ALOAD, 1); // player
        m.visitVarInsn(Opcodes.ILOAD, 2); // x
        m.visitVarInsn(Opcodes.ILOAD, 3); // y
        m.visitVarInsn(Opcodes.ILOAD, 4); // z
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER,
                "canWrenchAt", "(L" + OBF_PLAYER + ";III)Z");
        m.visitInsn(Opcodes.IRETURN);
        return m;
    }

    /**
     * {@code public void wrenchUsed(EntityPlayer p, int x, int y, int z) {
     *     OmniWrenchHelper.onWrenchUsed(p);
     * }}
     */
    private static MethodNode makeOnWrenchMethod(String methodName) {
        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                "(L" + OBF_PLAYER + ";III)V",
                null, null);
        m.visitVarInsn(Opcodes.ALOAD, 1); // player
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER,
                "onWrenchUsed", "(L" + OBF_PLAYER + ";)V");
        m.visitInsn(Opcodes.RETURN);
        return m;
    }

    /**
     * {@code public boolean canWhack(EntityPlayer p, ItemStack s, int x, int y, int z) {
     *     return OmniWrenchHelper.canWrenchAt(p, x, y, z);
     * }}
     */
    private static MethodNode makeCanWhackMethod(String methodName) {
        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                "(L" + OBF_PLAYER + ";L" + OBF_ITEMSTK + ";III)Z",
                null, null);
        m.visitVarInsn(Opcodes.ALOAD, 1); // player
        m.visitVarInsn(Opcodes.ILOAD, 3); // x (slot 3 because s takes 2)
        m.visitVarInsn(Opcodes.ILOAD, 4); // y
        m.visitVarInsn(Opcodes.ILOAD, 5); // z
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER,
                "canWrenchAt", "(L" + OBF_PLAYER + ";III)Z");
        m.visitInsn(Opcodes.IRETURN);
        return m;
    }

    private static MethodNode makeOnWhackMethod(String methodName) {
        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                "(L" + OBF_PLAYER + ";L" + OBF_ITEMSTK + ";III)V",
                null, null);
        m.visitVarInsn(Opcodes.ALOAD, 1); // player
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER,
                "onWrenchUsed", "(L" + OBF_PLAYER + ";)V");
        m.visitInsn(Opcodes.RETURN);
        return m;
    }

    /**
     * {@code public boolean canLink/canBoost(EntityPlayer p, ItemStack s, EntityMinecart c) {
     *     return OmniWrenchHelper.isOmniWrenchModeActive(p);
     * }} — the cart isn't needed, only whether the mode is active.
     */
    private static MethodNode makeCanCartMethod(String methodName) {
        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                "(L" + OBF_PLAYER + ";L" + OBF_ITEMSTK + ";L" + OBF_MINECART + ";)Z",
                null, null);
        m.visitVarInsn(Opcodes.ALOAD, 1); // player
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER,
                "isOmniWrenchModeActive", "(L" + OBF_PLAYER + ";)Z");
        m.visitInsn(Opcodes.IRETURN);
        return m;
    }

    private static MethodNode makeOnCartMethod(String methodName) {
        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                "(L" + OBF_PLAYER + ";L" + OBF_ITEMSTK + ";L" + OBF_MINECART + ";)V",
                null, null);
        m.visitVarInsn(Opcodes.ALOAD, 1); // player
        m.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER,
                "onWrenchUsed", "(L" + OBF_PLAYER + ";)V");
        m.visitInsn(Opcodes.RETURN);
        return m;
    }
}
