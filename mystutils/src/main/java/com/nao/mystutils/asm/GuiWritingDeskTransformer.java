package com.nao.mystutils.asm;

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
 * Wires keyboard and click handling for the writing-desk search box into
 * {@code com.xcompwiz.mystcraft.client.gui.GuiWritingDesk}.
 *
 * <p>Two of the desk's overridden GuiScreen methods are obfuscated to {@code a(...)}:
 * <ul>
 *   <li>{@code keyTyped} = {@code a(CI)V}: at entry, the keystroke is offered to
 *       {@link com.nao.mystutils.client.SearchHook#keyTyped}. While the search box is focused the
 *       hook consumes the key (returns {@code true}) and the method returns early — this is what
 *       lets you type letters into the box without the inventory key closing the desk or the arrow
 *       keys cycling notebooks.</li>
 *   <li>{@code mouseClicked} = {@code a(III)V}: at entry, the click is reported to
 *       {@link com.nao.mystutils.client.SearchHook#deskClicked} so a click outside the box (e.g. on
 *       the title field) defocuses the search. Focus-on happens in the page-surface hook.</li>
 * </ul>
 *
 * <p>Methods are matched by descriptor so the obfuscated single-letter names are not assumed; both
 * the obfuscated ({@code a}) and dev/SRG names are accepted.
 */
public class GuiWritingDeskTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "com.xcompwiz.mystcraft.client.gui.GuiWritingDesk";
    private static final String TARGET_SLASH = "com/xcompwiz/mystcraft/client/gui/GuiWritingDesk";

    private static final String HOOK = "com/nao/mystutils/client/SearchHook";

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
                boolean isA = "a".equals(m.name);
                if ((isA || "keyTyped".equals(m.name)) && "(CI)V".equals(m.desc)) {
                    if (patchKeyTyped(m)) patched++;
                } else if ((isA || "mouseClicked".equals(m.name)) && "(III)V".equals(m.desc)) {
                    if (patchMouseClicked(m)) patched++;
                }
            }

            if (patched == 0) {
                System.err.println("[MystUtils] GuiWritingDesk: no hook sites found — search input disabled");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MystUtils] Patched GuiWritingDesk (" + patched + "/2 hooks) — search input enabled");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MystUtils] GuiWritingDesk transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** {@code if (SearchHook.keyTyped(c, i)) return;} at method entry. */
    private static boolean patchKeyTyped(MethodNode m) {
        LabelNode pass = new LabelNode();
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ILOAD, 1)); // char c
        li.add(new VarInsnNode(Opcodes.ILOAD, 2)); // int i (key code)
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "keyTyped", "(CI)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, pass));
        li.add(new InsnNode(Opcodes.RETURN));
        li.add(pass);
        m.instructions.insert(li);
        return true;
    }

    /** {@code SearchHook.deskClicked(i, j, k);} at method entry (non-consuming). */
    private static boolean patchMouseClicked(MethodNode m) {
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ILOAD, 1)); // i (mouseX)
        li.add(new VarInsnNode(Opcodes.ILOAD, 2)); // j (mouseY)
        li.add(new VarInsnNode(Opcodes.ILOAD, 3)); // k (button)
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "deskClicked", "(III)V"));
        m.instructions.insert(li);
        return true;
    }
}
