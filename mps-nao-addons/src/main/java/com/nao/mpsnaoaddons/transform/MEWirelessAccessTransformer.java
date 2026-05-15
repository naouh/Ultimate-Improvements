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
 * Patches two Applied Energistics classes so the MPS Power Tool with the
 * ME Wireless Terminal module installed can be linked the same way as AE's
 * own wireless terminal:
 *
 * <ul>
 *   <li>{@code appeng.slot.SlotWirelessTerminal.a(ItemStack)Z} — the slot's
 *       acceptance check. Stock implementation only allows
 *       {@code Items.itemWirelessTerminal}. We prepend a call to
 *       {@code MEWirelessHelper.acceptInSlot} that returns true for any
 *       power tool with the ME module installed.</li>
 *   <li>{@code appeng.me.tile.TileController.encodeWireless(ItemStack)V} —
 *       the NBT-stamping step that runs the moment a terminal lands in the
 *       slot. Stock implementation gates on the same id check. We prepend
 *       a call to {@code MEWirelessHelper.tryEncodePowerTool} that writes
 *       the same five NBT keys (encKey, d, x, y, z) onto the power tool and
 *       early-returns so AE's own write doesn't fire for the wrong item.</li>
 * </ul>
 *
 * <p>If AE isn't present the target classes never load, so nothing here
 * fires. Helper calls inside the patched methods are no-ops when the module
 * isn't installed.
 */
public class MEWirelessAccessTransformer implements IClassTransformer {

    private static final String SLOT_DOT    = "appeng.slot.SlotWirelessTerminal";
    private static final String SLOT_SLASH  = "appeng/slot/SlotWirelessTerminal";
    private static final String TILE_DOT    = "appeng.me.tile.TileController";
    private static final String TILE_SLASH  = "appeng/me/tile/TileController";

    private static final String HELPER      = "com/nao/mpsnaoaddons/MEWirelessHelper";
    private static final String OBF_ITEMSTK = "ur";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;

        boolean isSlot = SLOT_DOT.equals(name) || SLOT_SLASH.equals(name);
        boolean isTile = TILE_DOT.equals(name) || TILE_SLASH.equals(name);
        if (!isSlot && !isTile) return bytes;

        System.out.println("[MpsNaoAddons] MEWirelessAccessTransformer invoked on " + name);

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (isSlot && "a".equals(m.name) && ("(L" + OBF_ITEMSTK + ";)Z").equals(m.desc)) {
                    prependSlotCheck(m);
                    patched++;
                } else if (isTile && "encodeWireless".equals(m.name)
                        && ("(L" + OBF_ITEMSTK + ";)V").equals(m.desc)) {
                    prependEncodeCheck(m);
                    patched++;
                }
            }
            if (patched == 0) {
                System.err.println("[MpsNaoAddons] " + name + ": target method not found, leaving untouched");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MpsNaoAddons] Patched " + patched + " method(s) in " + name);
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MpsNaoAddons] MEWirelessAccessTransformer failed on " + name + ":");
            t.printStackTrace();
            return bytes;
        }
    }

    /**
     * Prepend at the start of {@code SlotWirelessTerminal.a(ItemStack)Z}:
     * <pre>{@code
     *     if (MEWirelessHelper.acceptInSlot(stack)) return true;
     *     // original body follows
     * }</pre>
     */
    private static void prependSlotCheck(MethodNode m) {
        InsnList li = new InsnList();
        LabelNode skip = new LabelNode();
        li.add(new VarInsnNode(Opcodes.ALOAD, 1));
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER,
                "acceptInSlot", "(L" + OBF_ITEMSTK + ";)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        li.add(new InsnNode(Opcodes.ICONST_1));
        li.add(new InsnNode(Opcodes.IRETURN));
        li.add(skip);
        m.instructions.insert(li);
    }

    /**
     * Prepend at the start of {@code TileController.encodeWireless(ItemStack)V}:
     * <pre>{@code
     *     if (MEWirelessHelper.tryEncodePowerTool(this, stack)) return;
     *     // original body follows
     * }</pre>
     */
    private static void prependEncodeCheck(MethodNode m) {
        InsnList li = new InsnList();
        LabelNode skip = new LabelNode();
        li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
        li.add(new VarInsnNode(Opcodes.ALOAD, 1)); // stack
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER,
                "tryEncodePowerTool", "(Ljava/lang/Object;L" + OBF_ITEMSTK + ";)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(skip);
        m.instructions.insert(li);
    }
}
