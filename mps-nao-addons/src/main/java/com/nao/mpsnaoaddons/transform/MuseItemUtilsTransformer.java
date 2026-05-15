package com.nao.mpsnaoaddons.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Hooks {@code MuseItemUtils.removeModule(ItemStack, String)} so we can run
 * cleanup when one of our modules is salvaged from the power tool. Currently
 * used to wipe the AE wireless link NBT (encKey/d/x/y/z) when the ME
 * Wireless Terminal module is removed — otherwise the salvaged tool would
 * still be "linked" if a player re-installs the module and right-clicks.
 *
 * <p>Prepends:
 * <pre>{@code
 *     MEWirelessHelper.onModuleRemoved(stack, moduleName);
 *     // original body follows
 * }</pre>
 */
public class MuseItemUtilsTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "net.machinemuse.api.MuseItemUtils";
    private static final String TARGET_SLASH = "net/machinemuse/api/MuseItemUtils";

    private static final String ME_HELPER    = "com/nao/mpsnaoaddons/MEWirelessHelper";
    private static final String OBF_ITEMSTK  = "ur";
    /** {@code removeModule(ItemStack, String) -> boolean}. */
    private static final String DESC_REMOVE  = "(L" + OBF_ITEMSTK + ";Ljava/lang/String;)Z";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!TARGET_DOT.equals(name) && !TARGET_SLASH.equals(name)) return bytes;
        System.out.println("[MpsNaoAddons] MuseItemUtilsTransformer invoked on " + name);

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if ("removeModule".equals(m.name) && DESC_REMOVE.equals(m.desc)) {
                    prependCleanup(m);
                    patched++;
                }
            }
            if (patched == 0) {
                System.err.println("[MpsNaoAddons] MuseItemUtils.removeModule(stack, name) not found");
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MpsNaoAddons] Patched MuseItemUtils.removeModule for salvage cleanup");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MpsNaoAddons] MuseItemUtilsTransformer failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** Static method — locals 0 = stack, 1 = moduleName. */
    private static void prependCleanup(MethodNode m) {
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 0));
        li.add(new VarInsnNode(Opcodes.ALOAD, 1));
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ME_HELPER,
                "onModuleRemoved",
                "(L" + OBF_ITEMSTK + ";Ljava/lang/String;)V"));
        m.instructions.insert(li);
    }
}
