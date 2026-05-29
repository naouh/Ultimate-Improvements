package com.nao.ic2netfix;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Guards the cast in IC2's {@code BlockMultiID.onBlockPlacedBy} so placing an AdvancedMachines
 * block (whose TE is {@code ic2.advancedmachines.common.TileEntityRotaryMacerator}, NOT
 * {@code ic2.core.block.TileEntityBlock}) no longer throws a server-side ClassCastException.
 *
 * onBlockPlacedBy = obf {@code void a(World, int, int, int, EntityLiving)}. It does:
 * <pre>((ic2.core.block.TileEntityBlock) world.getBlockTileEntity(x,y,z)).setFacing(...)</pre>
 * We inject, right before that {@code CHECKCAST}, a check: if the TE isn't IC2's TileEntityBlock,
 * set the facing reflectively on the foreign TE (it carries the same {@code setFacing(short)})
 * and return - skipping the bad cast while keeping the machine oriented toward the player.
 */
public class BlockMultiIDTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "ic2.core.block.BlockMultiID";
    private static final String TARGET_SLASH = "ic2/core/block/BlockMultiID";
    private static final String TE_BLOCK     = "ic2/core/block/TileEntityBlock";

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
                if (!isOnBlockPlacedBy(m)) continue;
                if (guardPlacementCast(m)) patched++;
            }

            if (patched == 0) {
                System.err.println("[IC2NetFix] BlockMultiID.onBlockPlacedBy cast not found - IC2 layout changed?");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[IC2NetFix] guarded BlockMultiID.onBlockPlacedBy against foreign TileEntity");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[IC2NetFix] BlockMultiID transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** onBlockPlacedBy(World, int, int, int, EntityLiving): obf name "a", desc (Lx;IIILy;)V,
     *  and it contains the TileEntityBlock cast we care about. */
    private static boolean isOnBlockPlacedBy(MethodNode m) {
        if (!"a".equals(m.name) || m.desc == null) return false;
        if (!m.desc.matches("^\\(L[^;]+;IIIL[^;]+;\\)V$")) return false;
        return findCheckcast(m) != null;
    }

    private static TypeInsnNode findCheckcast(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.CHECKCAST && insn instanceof TypeInsnNode
                    && TE_BLOCK.equals(((TypeInsnNode) insn).desc)) {
                return (TypeInsnNode) insn;
            }
        }
        return null;
    }

    private static boolean guardPlacementCast(MethodNode m) {
        TypeInsnNode cast = findCheckcast(m);
        if (cast == null) return false;

        LabelNode proceed = new LabelNode();
        InsnList li = new InsnList();
        // stack at this point: [te]
        li.add(new InsnNode(Opcodes.DUP));                                   // [te, te]
        li.add(new TypeInsnNode(Opcodes.INSTANCEOF, TE_BLOCK));              // [te, int]
        li.add(new JumpInsnNode(Opcodes.IFNE, proceed));                     // IC2 TE -> [te] proceed
        // foreign TE: setForeignFacing(te, entityliving) then return
        li.add(new VarInsnNode(Opcodes.ALOAD, 5));                           // [te, entityliving]
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/nao/ic2netfix/Ic2NetFixHook", "setForeignFacing",
                "(Ljava/lang/Object;Ljava/lang/Object;)V"));                 // []
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(proceed);                                                     // [te] -> original CHECKCAST
        m.instructions.insertBefore(cast, li);
        return true;
    }
}
