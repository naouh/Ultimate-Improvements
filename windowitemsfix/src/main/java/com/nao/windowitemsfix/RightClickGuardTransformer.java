package com.nao.windowitemsfix;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
 * Patches {@code PlayerControllerMP.onPlayerRightClick} to short-circuit when
 * the targeted block is a {@code BlockContainer} but the client has no
 * {@code TileEntity} for it yet.
 *
 * Why: when chunks have just loaded (after teleport/login) the block IDs
 * arrive before the TileEntity sync packets. If the player right-clicks
 * during that window, the server still processes the interaction normally
 * — and for blocks whose TE state actually matters (machines), the server
 * ends up dispatching open-window + slot-update packets that on the client
 * either crash or, with our other patches, leak fake items into the real
 * inventory (because the server's response gets routed through whichever
 * Container the client currently has open).
 *
 * The cleanest fix is to refuse to send the click in the first place when
 * the TE isn't ready yet. The player just has to right-click again a couple
 * seconds later when the chunk's TileEntities have synced.
 *
 * Injected at method entry:
 * <pre>
 *     int id = world.getBlockId(x, y, z);
 *     if (id &gt; 0
 *             &amp;&amp; Block.blocksList[id] instanceof BlockContainer
 *             &amp;&amp; world.getBlockTileEntity(x, y, z) == null) {
 *         return false;
 *     }
 * </pre>
 */
public class RightClickGuardTransformer implements IClassTransformer {

    private static final String TARGET_OBF = "ayo"; // PlayerControllerMP
    private static final String TARGET_CLEAN = "net.minecraft.client.multiplayer.PlayerControllerMP";
    private static final String TARGET_CLEAN_SLASH = "net/minecraft/client/multiplayer/PlayerControllerMP";

    private static final String METHOD_OBF = "a";
    private static final String METHOD_DESC_OBF = "(Lqx;Lyc;Lur;IIIILaoj;)Z";
    private static final String METHOD_CLEAN = "onPlayerRightClick";

    // Block / BlockContainer / World / blocksList
    private static final String BLOCK_OBF           = "amq";
    private static final String BLOCKCONTAINER_OBF  = "akb";
    private static final String WORLD_OBF           = "yc";
    private static final String BLOCKSLIST_FIELD    = "p";        // Block.blocksList
    private static final String BLOCKSLIST_DESC     = "[Lamq;";
    private static final String GETBLOCKID_NAME     = "a";
    private static final String GETBLOCKID_DESC     = "(III)I";
    private static final String GETTE_NAME          = "q";
    private static final String GETTE_DESC          = "(III)Lany;"; // returns TileEntity

    @Override
    public byte[] transform(String name, byte[] bytes) {
        if (bytes == null) return null;
        boolean isObf = TARGET_OBF.equals(name);
        boolean isClean = TARGET_CLEAN.equals(name) || TARGET_CLEAN_SLASH.equals(name);
        if (!isObf && !isClean) return bytes;

        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            int patched = 0;
            for (Object o : cn.methods) {
                MethodNode m = (MethodNode) o;
                if (!matchesOnPlayerRightClick(m)) continue;
                injectGuard(m);
                patched++;
            }

            if (patched == 0) {
                System.err.println("[WindowItemsFix] PlayerControllerMP.onPlayerRightClick not found");
                return bytes;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            System.out.println("[WindowItemsFix] Patched PlayerControllerMP.onPlayerRightClick");
            return cw.toByteArray();
        } catch (Throwable t) {
            System.err.println("[WindowItemsFix] PlayerControllerMP transform failed:");
            t.printStackTrace();
            return bytes;
        }
    }

    private static boolean matchesOnPlayerRightClick(MethodNode m) {
        if (m.desc == null) return false;
        if (METHOD_CLEAN.equals(m.name)) return true;
        if (METHOD_OBF.equals(m.name) && METHOD_DESC_OBF.equals(m.desc)) return true;
        return false;
    }

    /**
     * Param indices for onPlayerRightClick(EntityPlayer, World, ItemStack, int x, int y, int z, int side, Vec3):
     *   0 = this, 1 = player, 2 = world, 3 = itemStack, 4 = x, 5 = y, 6 = z, 7 = side, 8 = hitVec
     */
    private static void injectGuard(MethodNode m) {
        LabelNode skip = new LabelNode();
        LabelNode popAndSkip = new LabelNode();
        LabelNode bail = new LabelNode();
        LabelNode teIsNull = new LabelNode();

        InsnList li = new InsnList();

        // int id = world.getBlockId(x, y, z);
        li.add(new VarInsnNode(Opcodes.ALOAD, 2));    // world
        li.add(new VarInsnNode(Opcodes.ILOAD, 4));    // x
        li.add(new VarInsnNode(Opcodes.ILOAD, 5));    // y
        li.add(new VarInsnNode(Opcodes.ILOAD, 6));    // z
        li.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                WORLD_OBF, GETBLOCKID_NAME, GETBLOCKID_DESC));
        li.add(new InsnNode(Opcodes.DUP));
        li.add(new JumpInsnNode(Opcodes.IFLE, popAndSkip));

        // Block block = Block.blocksList[id];
        li.add(new FieldInsnNode(Opcodes.GETSTATIC,
                BLOCK_OBF, BLOCKSLIST_FIELD, BLOCKSLIST_DESC));
        li.add(new InsnNode(Opcodes.SWAP));            // [array, id]
        li.add(new InsnNode(Opcodes.AALOAD));          // [block]

        // if (!(block instanceof BlockContainer)) goto skip;
        li.add(new TypeInsnNode(Opcodes.INSTANCEOF, BLOCKCONTAINER_OBF));
        li.add(new JumpInsnNode(Opcodes.IFEQ, skip));

        // TileEntity te = world.getBlockTileEntity(x, y, z);
        li.add(new VarInsnNode(Opcodes.ALOAD, 2));
        li.add(new VarInsnNode(Opcodes.ILOAD, 4));
        li.add(new VarInsnNode(Opcodes.ILOAD, 5));
        li.add(new VarInsnNode(Opcodes.ILOAD, 6));
        li.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                WORLD_OBF, GETTE_NAME, GETTE_DESC));
        li.add(new InsnNode(Opcodes.DUP));
        li.add(new JumpInsnNode(Opcodes.IFNULL, teIsNull));

        // GTCompat.isBrokenGTTile(te) — consumes te.
        li.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/nao/windowitemsfix/GTCompat",
                "isBrokenGTTile",
                "(Ljava/lang/Object;)Z"));
        li.add(new JumpInsnNode(Opcodes.IFEQ, skip));   // not broken → proceed normally
        li.add(new JumpInsnNode(Opcodes.GOTO, bail));   // broken → bail

        li.add(teIsNull);
        li.add(new InsnNode(Opcodes.POP));              // discard leftover null te

        li.add(bail);
        li.add(new InsnNode(Opcodes.ICONST_0));
        li.add(new InsnNode(Opcodes.IRETURN));

        li.add(popAndSkip);
        li.add(new InsnNode(Opcodes.POP));              // discard leftover id
        li.add(skip);
        m.instructions.insert(li);
    }
}
