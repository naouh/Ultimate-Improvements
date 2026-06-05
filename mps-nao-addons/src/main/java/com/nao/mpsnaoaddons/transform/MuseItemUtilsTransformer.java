package com.nao.mpsnaoaddons.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Patches {@code net.machinemuse.api.MuseItemUtils} in two places:
 *
 * <ol>
 *   <li>{@code removeModule(ItemStack, String)} — prepends
 *       {@code MEWirelessHelper.onModuleRemoved(stack, moduleName)} so we wipe the AE wireless link
 *       NBT (encKey/d/x/y/z) when the ME Wireless Terminal module is salvaged, otherwise a re-installed
 *       module would still be "linked".</li>
 *   <li>{@code getMuseItemTag(ItemStack)} — prepends a {@link com.nao.mpsnaoaddons.MuseTagGuard} guard
 *       so MPS only ever creates/persists its {@code mmmpsmod} NBT compound on real modular items. By
 *       default MPS stamps an empty {@code mmmpsmod} tag onto ANY item it inspects (ingots, food, ...),
 *       and a stamped item won't stack with an untagged identical one — the long-standing
 *       "furnace output won't stack" bug. The guard also strips the leftover stamp off already-tagged
 *       non-modular items so old stacks self-heal.</li>
 * </ol>
 */
public class MuseItemUtilsTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "net.machinemuse.api.MuseItemUtils";
    private static final String TARGET_SLASH = "net/machinemuse/api/MuseItemUtils";

    private static final String ME_HELPER    = "com/nao/mpsnaoaddons/MEWirelessHelper";
    private static final String GUARD        = "com/nao/mpsnaoaddons/MuseTagGuard";
    private static final String OBF_ITEMSTK  = "ur";
    private static final String OBF_NBT      = "bq";   // NBTTagCompound (confirmed via /nbt raw dump)
    /** {@code removeModule(ItemStack, String) -> boolean}. */
    private static final String DESC_REMOVE  = "(L" + OBF_ITEMSTK + ";Ljava/lang/String;)Z";
    /** {@code getMuseItemTag(ItemStack) -> NBTTagCompound}. */
    private static final String DESC_GETTAG  = "(L" + OBF_ITEMSTK + ";)L" + OBF_NBT + ";";

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
                    System.out.println("[MpsNaoAddons] Patched MuseItemUtils.removeModule for salvage cleanup");
                    patched++;
                } else if ("getMuseItemTag".equals(m.name) && DESC_GETTAG.equals(m.desc)) {
                    prependTagGuard(m);
                    System.out.println("[MpsNaoAddons] Patched MuseItemUtils.getMuseItemTag - stops stamping non-modular items + strips old mmmpsmod stamps (stacking fix)");
                    patched++;
                }
            }
            if (patched == 0) {
                System.err.println("[MpsNaoAddons] MuseItemUtils: no target methods found (removeModule / getMuseItemTag)");
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
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

    /**
     * Inject at the very top of {@code getMuseItemTag(ItemStack) -> NBTTagCompound}:
     * <pre>{@code
     *     NBTTagCompound t = MuseTagGuard.museTagFor(stack);
     *     if (t != null) return t;   // non-modular: detached empty tag (no stamp; strips old stamps)
     *     // else fall through to MPS' original create-and-persist path (real modular item)
     * }</pre>
     * This kills the bug where MPS stamps an empty {@code mmmpsmod} compound on ordinary items
     * (ingots, food, ...) which then refuse to stack with untagged copies. Locals: 0 = stack.
     */
    private static void prependTagGuard(MethodNode m) {
        InsnList li = new InsnList();
        LabelNode proceed = new LabelNode();
        li.add(new VarInsnNode(Opcodes.ALOAD, 0));                                  // stack
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GUARD,
                "museTagFor", "(L" + OBF_ITEMSTK + ";)L" + OBF_NBT + ";"));         // -> t
        li.add(new InsnNode(Opcodes.DUP));                                          // [t, t]
        li.add(new JumpInsnNode(Opcodes.IFNULL, proceed));                          // t == null -> proceed
        li.add(new InsnNode(Opcodes.ARETURN));                                      // t != null -> return t
        li.add(proceed);
        li.add(new InsnNode(Opcodes.POP));                                          // drop the null copy
        m.instructions.insert(li);
    }
}
