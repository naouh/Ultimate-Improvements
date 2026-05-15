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
 * Hooks {@code MuseRenderer.drawIconAt} and {@code drawIconPartial} so we can
 * intercept renders for our {@code ItemMuseIcon} subclass and draw the actual
 * item texture instead of the MuseIcon sprite. Every MPS render path goes
 * through one of these two methods — patching here covers the Tinker Table
 * grid, the mode-switcher HUD above the hotbar, and anything else MPS adds
 * later.
 *
 * <p>Prepends:
 * <pre>{@code
 *     if (CustomIconRenderer.tryDraw(icon, x, y)) return;
 *     // original body follows
 * }</pre>
 */
public class MuseRendererTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "net.machinemuse.general.MuseRenderer";
    private static final String TARGET_SLASH = "net/machinemuse/general/MuseRenderer";

    private static final String HELPER       = "com/nao/mpsnaoaddons/CustomIconRenderer";
    private static final String MUSE_ICON    = "net/machinemuse/general/gui/MuseIcon";
    private static final String COLOUR       = "net/machinemuse/general/geometry/Colour";

    // descriptor of drawIconAt: (D, D, MuseIcon, Colour) -> void
    private static final String DESC_ICON_AT      = "(DDL" + MUSE_ICON + ";L" + COLOUR + ";)V";
    // descriptor of drawIconPartial: (D, D, MuseIcon, Colour, D, D, D, D) -> void
    private static final String DESC_ICON_PARTIAL = "(DDL" + MUSE_ICON + ";L" + COLOUR + ";DDDD)V";

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        if (!TARGET_DOT.equals(name) && !TARGET_SLASH.equals(name)) return bytes;
        System.out.println("[MpsNaoAddons] MuseRendererTransformer invoked on " + name);

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if ("drawIconAt".equals(m.name) && DESC_ICON_AT.equals(m.desc)) {
                    prependFullCheck(m);
                    patched++;
                } else if ("drawIconPartial".equals(m.name) && DESC_ICON_PARTIAL.equals(m.desc)) {
                    prependPartialCheck(m);
                    patched++;
                }
            }
            if (patched == 0) {
                System.err.println("[MpsNaoAddons] MuseRenderer: draw methods not found");
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MpsNaoAddons] Patched " + patched + " draw method(s) on MuseRenderer");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MpsNaoAddons] MuseRendererTransformer failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /**
     * {@code drawIconAt(double x, double y, MuseIcon icon, Colour c)} — push
     * (icon, x, y) and call {@code tryDraw(MuseIcon, double, double)}.
     */
    private static void prependFullCheck(MethodNode m) {
        InsnList li = new InsnList();
        LabelNode skip = new LabelNode();
        li.add(new VarInsnNode(Opcodes.ALOAD, 4)); // MuseIcon icon
        li.add(new VarInsnNode(Opcodes.DLOAD, 0)); // double x
        li.add(new VarInsnNode(Opcodes.DLOAD, 2)); // double y
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER,
                "tryDraw", "(L" + MUSE_ICON + ";DD)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(skip);
        m.instructions.insert(li);
    }

    /**
     * {@code drawIconPartial(double x, double y, MuseIcon icon, Colour c,
     * double l, double t, double r, double b)} — push everything (icon, x,
     * y, l, t, r, b) and call {@code tryDrawPartial}. The l/t/r/b bounds
     * are the visible sub-rect within the 16x16 icon; the helper uses them
     * to set up a GL scissor and clip the item rendering so prev/next
     * mode icons peek out from the hotbar instead of overlapping it.
     */
    private static void prependPartialCheck(MethodNode m) {
        InsnList li = new InsnList();
        LabelNode skip = new LabelNode();
        li.add(new VarInsnNode(Opcodes.ALOAD, 4));   // MuseIcon icon
        li.add(new VarInsnNode(Opcodes.DLOAD, 0));   // double x
        li.add(new VarInsnNode(Opcodes.DLOAD, 2));   // double y
        li.add(new VarInsnNode(Opcodes.DLOAD, 6));   // double l
        li.add(new VarInsnNode(Opcodes.DLOAD, 8));   // double t
        li.add(new VarInsnNode(Opcodes.DLOAD, 10));  // double r
        li.add(new VarInsnNode(Opcodes.DLOAD, 12));  // double b
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER,
                "tryDrawPartial", "(L" + MUSE_ICON + ";DDDDDD)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(skip);
        m.instructions.insert(li);
    }
}
