package com.nao.mystutils.asm;

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
 * Adds the search bar behaviour to Mystcraft's notebook page grid
 * ({@code com.xcompwiz.mystcraft.client.gui.GuiElementPageSurface}).
 *
 * <p>The element keeps its own page list in a private {@code pages} field and renders it from
 * {@code render}; {@code update(List)} is called every client tick with the desk's full surface
 * page list. Three hooks (all routed to {@link com.nao.mystutils.client.SearchHook}) implement the
 * search without touching any Minecraft type in the injected bytecode:
 *
 * <ul>
 *   <li>{@code update(List)} — the incoming list is replaced with a filtered + re-laid-out copy
 *       when a search query is active, so every downstream calculation (scroll bounds, rendering,
 *       hit-testing) just works against the filtered list.</li>
 *   <li>{@code render(float,int,int)} — after the grid is drawn, the search box is painted on top
 *       and its on-screen rectangle is cached for hit-testing.</li>
 *   <li>{@code mouseClicked(int,int,int)} — a click inside the search box focuses it and is
 *       swallowed (no page is placed/taken).</li>
 * </ul>
 *
 * <p>{@code GuiElementPageSurface} is a Mystcraft class, so its own method names are not
 * obfuscated and can be matched directly. The hook signatures use only {@code Object}, primitives
 * and {@code java.util.List} so the injected call descriptors are stable under deobf/notch
 * remapping.
 */
public class GuiPageSurfaceTransformer implements IClassTransformer {

    private static final String TARGET_DOT   = "com.xcompwiz.mystcraft.client.gui.GuiElementPageSurface";
    private static final String TARGET_SLASH = "com/xcompwiz/mystcraft/client/gui/GuiElementPageSurface";

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
                if ("update".equals(m.name) && "(Ljava/util/List;)V".equals(m.desc)) {
                    if (patchUpdate(m)) patched++;
                } else if ("render".equals(m.name) && "(FII)V".equals(m.desc)) {
                    if (patchRender(m)) patched++;
                } else if ("mouseClicked".equals(m.name) && "(III)Z".equals(m.desc)) {
                    if (patchMouseClicked(m)) patched++;
                }
            }

            if (patched == 0) {
                System.err.println("[MystUtils] GuiElementPageSurface: no hook sites found — search bar disabled");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[MystUtils] Patched GuiElementPageSurface (" + patched + "/3 hooks) — writing-desk search enabled");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[MystUtils] GuiElementPageSurface transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    /** {@code in = SearchHook.applyFilter(in);} at method entry. */
    private static boolean patchUpdate(MethodNode m) {
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 1));
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "applyFilter",
                "(Ljava/util/List;)Ljava/util/List;"));
        li.add(new VarInsnNode(Opcodes.ASTORE, 1));
        m.instructions.insert(li);
        return true;
    }

    /** {@code SearchHook.drawBox(this, mouseX, mouseY);} before every RETURN. */
    private static boolean patchRender(MethodNode m) {
        boolean any = false;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                InsnList li = new InsnList();
                li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                li.add(new VarInsnNode(Opcodes.ILOAD, 2)); // mouseX
                li.add(new VarInsnNode(Opcodes.ILOAD, 3)); // mouseY
                li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "drawBox",
                        "(Ljava/lang/Object;II)V"));
                m.instructions.insertBefore(insn, li);
                any = true;
            }
        }
        return any;
    }

    /** {@code if (SearchHook.surfaceClicked(this,i,j,k)) return true;} at method entry. */
    private static boolean patchMouseClicked(MethodNode m) {
        LabelNode pass = new LabelNode();
        InsnList li = new InsnList();
        li.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
        li.add(new VarInsnNode(Opcodes.ILOAD, 1)); // i
        li.add(new VarInsnNode(Opcodes.ILOAD, 2)); // j
        li.add(new VarInsnNode(Opcodes.ILOAD, 3)); // k
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "surfaceClicked",
                "(Ljava/lang/Object;III)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, pass));
        li.add(new InsnNode(Opcodes.ICONST_1));
        li.add(new InsnNode(Opcodes.IRETURN));
        li.add(pass);
        m.instructions.insert(li);
        return true;
    }
}
