package com.nao.tfcfixes.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * GregTech wraps the ItemBlocks of IC2/AdvancedMachines machines with its own
 * {@code GT_MetaMachine_Item}, whose {@code placeBlockAt} casts the freshly placed TE to
 * {@code gregtechmod.api.BaseMetaTileEntity}. Placing an AdvancedMachines machine (foreign TE)
 * therefore ClassCastExceptions server-side after the block is already in the world.
 *
 * The block is set before the cast, so for a foreign TE we just return {@code true} (placement
 * succeeded) and skip GregTech's metatile init - which is meaningless for a non-GT machine.
 * Injected right before each {@code CHECKCAST gregtechmod/api/BaseMetaTileEntity}:
 * <pre>if (!(te instanceof BaseMetaTileEntity)) return true;</pre>
 */
public class GtMetaMachineItemTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "gregtechmod.common.items.GT_MetaMachine_Item";
    private static final String TARGET_SLASH = "gregtechmod/common/items/GT_MetaMachine_Item";
    private static final String BASE_META_TE = "gregtechmod/api/BaseMetaTileEntity";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!TARGET_DOT.equals(name) && !TARGET_SLASH.equals(name)) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!"placeBlockAt".equals(m.name)) continue;
                patched += guardCasts(m);
            }

            if (patched == 0) {
                System.err.println("[TFCFixes] GT_MetaMachine_Item.placeBlockAt cast not found - GregTech layout changed?");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] guarded " + patched + " cast(s) in GT_MetaMachine_Item.placeBlockAt against foreign TileEntity");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] GT_MetaMachine_Item transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    private static int guardCasts(MethodNode m) {
        int count = 0;
        // Collect target casts first (we mutate the list while iterating otherwise).
        java.util.List<TypeInsnNode> targets = new java.util.ArrayList<TypeInsnNode>();
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.CHECKCAST && insn instanceof TypeInsnNode
                    && BASE_META_TE.equals(((TypeInsnNode) insn).desc)) {
                targets.add((TypeInsnNode) insn);
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            TypeInsnNode cast = targets.get(i);
            LabelNode proceed = new LabelNode();
            InsnList li = new InsnList();
            // stack: [te]
            li.add(new InsnNode(Opcodes.DUP));                          // [te, te]
            li.add(new TypeInsnNode(Opcodes.INSTANCEOF, BASE_META_TE)); // [te, int]
            li.add(new JumpInsnNode(Opcodes.IFNE, proceed));            // GT TE -> [te] proceed
            li.add(new InsnNode(Opcodes.POP));                          // foreign -> [] discard te
            li.add(new InsnNode(Opcodes.ICONST_1));                     // true (block already placed)
            li.add(new InsnNode(Opcodes.IRETURN));
            li.add(proceed);                                            // [te] -> original CHECKCAST
            m.instructions.insertBefore(cast, li);
            count++;
        }
        return count;
    }
}
