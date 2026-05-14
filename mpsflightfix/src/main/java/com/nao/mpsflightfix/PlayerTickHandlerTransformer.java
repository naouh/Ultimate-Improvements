package com.nao.mpsflightfix;

import nilloader.api.lib.asm.tree.LabelNode;
import nilloader.api.lib.mini.MiniTransformer;
import nilloader.api.lib.mini.PatchContext;
import nilloader.api.lib.mini.annotation.Patch;

/**
 * Patches MPS so the Flight Control helmet module behaves as if it's disabled
 * while the player is on the ground.
 *
 * <p>In source, {@code PlayerTickHandler.handle} contains:
 * <pre>
 * hasFlightControl = MuseItemUtils.itemHasActiveModule(helmet, ModularCommon.MODULE_FLIGHT_CONTROL);
 * </pre>
 * In bytecode this is:
 * <pre>
 *   ALOAD helmet
 *   LDC "Flight Control"
 *   INVOKESTATIC MuseItemUtils.itemHasActiveModule(ItemStack, String)Z
 *   ISTORE hasFlightControl
 * </pre>
 *
 * <p>We inject between the INVOKESTATIC and the ISTORE: if {@code player.onGround}
 * is true, replace the boolean result with {@code false}. Result:
 * {@code hasFlightControl = itemHasActiveModule(...) && !player.onGround}.
 */
@Patch.Class("net.machinemuse.powersuits.tick.PlayerTickHandler")
public class PlayerTickHandlerTransformer extends MiniTransformer {

    @Patch.Method("handle(Lnet/minecraft/entity/player/EntityPlayer;)V")
    public void patchHandle(PatchContext ctx) {
        // Anchor on the LDC of the Flight Control module name then jump past the call.
        ctx.search(LDC("Flight Control")).jumpAfter();
        ctx.search(
            INVOKESTATIC("net/machinemuse/api/MuseItemUtils", "itemHasActiveModule",
                         "(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z")
        ).jumpAfter();

        // Stack here: [Z hasFlightControl]
        // We want: if (player.onGround) replace top of stack with 0.
        // player is local var 1 (handle is non-static, "this" is 0, player is 1).
        LabelNode keep = new LabelNode();
        ctx.add(
            ALOAD(1),                                                            // player
            GETFIELD("net/minecraft/entity/Entity", "onGround", "Z"),            // [Z, Z]
            IFEQ(keep),                                                          // if !onGround, keep original boolean
            POP(),                                                               // discard original
            ICONST_0()                                                           // push false
        );
        ctx.add(keep);
    }
}
