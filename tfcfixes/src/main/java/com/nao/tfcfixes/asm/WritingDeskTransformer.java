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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * Silences the Mystcraft writing-desk worldgen spam.
 *
 * <p>Mystcraft's village "Archivist House" places the writing desk as two blocks — a head and a
 * foot. {@code BlockWritingDesk.onBlockAdded} (obf {@code g(Lyc;III)V}) then unconditionally tries
 * to attach a {@code TileEntityDesk} to <em>both</em> halves:
 *
 * <pre>
 *     public void g(World world, int i, int j, int k) {
 *         super.g(world, i, j, k);
 *         world.setBlockTileEntity(i, j, k, this.createTileEntity(world, world.getBlockMetadata(i, j, k)));
 *     }
 * </pre>
 *
 * The foot block reports {@code hasTileEntity(meta) == false}, so vanilla {@code Chunk} refuses the
 * tile entity and logs <em>"Attempted to place a tile entity ... where there was no entity tile!"</em>
 * plus a {@code new Exception().printStackTrace()} — a [SEVERE] stack trace on every archivist house
 * generated (very visible when players spam {@code /rtp}). It is noise, not a real crash, but it is
 * easy to remove cleanly.
 *
 * <p>This transformer injects, right after the {@code super.g(...)} call, an early return for the
 * foot block:
 *
 * <pre>
 *     if (isBlockFoot(world.getBlockMetadata(i, j, k))) return;
 * </pre>
 *
 * The head block keeps its tile entity exactly as before, so the desk still works; the foot block
 * simply skips the bogus tile-entity placement. Block.onBlockAdded is a no-op in 1.4.7, so skipping
 * the remainder of the method for the foot changes nothing else.
 *
 * <p>To stay obfuscation-proof the metadata getter ({@code World.getBlockMetadata}, obf {@code h})
 * is not hardcoded — it is cloned from the {@code (III)I} virtual call already present in the
 * method. {@code isBlockFoot(I)Z} is a static method on the desk class itself, so it is referenced
 * via the class's own (un-obfuscated) name.
 */
public class WritingDeskTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "com.xcompwiz.mystcraft.block.BlockWritingDesk";
    private static final String TARGET_SLASH = "com/xcompwiz/mystcraft/block/BlockWritingDesk";

    private static final String METHOD_OBF   = "g";            // onBlockAdded, obfuscated
    private static final String METHOD_CLEAN = "onBlockAdded"; // dev/SRG environment

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
                if (!isOnBlockAdded(m)) continue;
                if (injectFootGuard(cn, m)) patched++;
            }

            if (patched == 0) {
                System.err.println("[TFCFixes] BlockWritingDesk.onBlockAdded not patched in " + name
                        + " (method or metadata getter not found) — leaving it untouched");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[TFCFixes] Patched BlockWritingDesk.onBlockAdded — desk-foot tile-entity spam silenced");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[TFCFixes] WritingDesk transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** onBlockAdded has the shape {@code (Lworld;III)V} — one object then three ints, void return. */
    private static boolean isOnBlockAdded(MethodNode m) {
        boolean nameOk = METHOD_OBF.equals(m.name) || METHOD_CLEAN.equals(m.name);
        return nameOk && m.desc != null && m.desc.matches("^\\(L[^;]+;III\\)V$");
    }

    private static boolean injectFootGuard(ClassNode cn, MethodNode m) {
        // The super.g(...) call: the first INVOKESPECIAL whose name matches this method's name.
        AbstractInsnNode superCall = null;
        // The metadata getter World.getBlockMetadata(III)I: the (only) INVOKEVIRTUAL returning int.
        MethodInsnNode metaGetter = null;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && superCall == null) {
                MethodInsnNode min = (MethodInsnNode) insn;
                if (m.name.equals(min.name)) superCall = insn;
            } else if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL && metaGetter == null) {
                MethodInsnNode min = (MethodInsnNode) insn;
                if ("(III)I".equals(min.desc)) metaGetter = min;
            }
        }
        if (superCall == null || metaGetter == null) return false;

        LabelNode pass = new LabelNode();
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 1)); // world
        li.add(new VarInsnNode(Opcodes.ILOAD, 2)); // i
        li.add(new VarInsnNode(Opcodes.ILOAD, 3)); // j
        li.add(new VarInsnNode(Opcodes.ILOAD, 4)); // k
        li.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, metaGetter.owner, metaGetter.name, metaGetter.desc));
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, cn.name, "isBlockFoot", "(I)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, pass)); // not a foot -> fall through to original body
        li.add(new InsnNode(Opcodes.RETURN));         // foot -> skip the bogus tile-entity placement
        li.add(pass);
        m.instructions.insert(superCall, li);
        return true;
    }
}
