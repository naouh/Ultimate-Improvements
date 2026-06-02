# TFCFixes

A bundle of small ASM bug fixes for the **Ultimate Remastered** (Minecraft 1.4.7 / Forge) modpack,
packaged as a single FML coremod. Each fix is an independent `IClassTransformer` that is
**fail-safe**: if its target class/method isn't present at load time (different mod version,
different obfuscation, mod absent), it logs and returns the original bytes so the pack keeps
running.

## Fixes included

| Transformer | Target | What it fixes |
|---|---|---|
| `WritingDeskTransformer` | `com.xcompwiz.mystcraft.block.BlockWritingDesk` | Mystcraft's village "Archivist House" places the writing desk as a head + foot block. `onBlockAdded` tried to attach a `TileEntityDesk` to *both* halves; the foot block has no tile entity, so vanilla `Chunk` logged `"Attempted to place a tile entity ... where there was no entity tile!"` + a `[SEVERE]` stack trace on every archivist house generated (very visible during `/rtp` spam). The transformer adds an early return for the foot block, so the desk still works and the spam is gone. Not a crash — pure log noise — but cleanly removed. |
| `NetworkManagerTransformer` | `ic2.core.network.NetworkManager` | *(merged from ic2netfix)* IC2 server→client send methods (`updateTileEntityField`, `initiateTileEntityEvent`, `initiateItemEvent`, `announceBlockUpdate`) funnel into `sendUpdatePacket`, which casts every player to `EntityPlayerMP` and `ClassCastException`s against the client's `EntityClientPlayerMP`. Guards each with an early return on the logical client (where the send is meaningless). Uses `Ic2NetFixHook.skipClientSend()`. |
| `BlockMultiIDTransformer` | `ic2.core.block.BlockMultiID` | *(merged from ic2netfix)* `onBlockPlacedBy` casts the placed TE to IC2's `TileEntityBlock`; an AdvancedMachines block has a foreign TE → server-side `ClassCastException`. Before the cast, sets the facing reflectively on the foreign TE and returns. Uses `Ic2NetFixHook.setForeignFacing(...)`. |
| `GtMetaMachineItemTransformer` | `gregtechmod.common.items.GT_MetaMachine_Item` | *(merged from ic2netfix)* GregTech's `placeBlockAt` casts the placed TE to `BaseMetaTileEntity`; placing a wrapped AdvancedMachines machine (foreign TE) `ClassCastException`s after the block is already set. For a foreign TE it returns `true` (placement succeeded) and skips GT's metatile init. |
| `RightClickGuardTransformer` | `net.minecraft.client.multiplayer.PlayerControllerMP` | *(merged from windowitemsfix)* After teleport/login, block IDs arrive before TileEntity sync packets. Right-clicking a `BlockContainer` whose TE isn't synced yet (null TE, or a GregTech `BaseMetaTileEntity` not yet populated) is swallowed client-side so it doesn't trigger a window-items crash / ghost-item leak. Player just clicks again once TEs sync. Uses `GTCompat.isBrokenGTTile(...)`. |
| `ContainerTransformer` | `net.minecraft.inventory.Container` | *(merged from windowitemsfix)* Safety net: `putStacksInSlots` early-returns when `inventorySlots` is empty, preventing the `IndexOutOfBoundsException` if a 0-slot `Packet104WindowItems` reaches the client through any other path. |

Runtime helpers (no MC compile coupling beyond what voldeloom remaps): `Ic2NetFixHook` (side check + reflective facing) and `GTCompat` (reflective GregTech sync-state check).

## Adding a new fix

1. Write a transformer under `com.nao.tfcfixes.asm` implementing `cpw.mods.fml.relauncher.IClassTransformer`.
   Match both the obfuscated and clean names of your target, and wrap everything in try/catch so a
   miss logs and no-ops instead of crashing the game.
2. Append its fully-qualified name to the array in
   [`TFCFixesCorePlugin#getASMTransformerClass`](src/main/java/com/nao/tfcfixes/TFCFixesCorePlugin.java).

To stay obfuscation-proof, prefer cloning instruction nodes (owner/name/desc) that already exist in
the target method over hardcoding obfuscated MC descriptors — see `WritingDeskTransformer`'s reuse
of the `(III)I` metadata getter.

## Build & deploy

```
./gradlew build
```

Voldeloom compiles + remaps to obfuscated names. `build` then deploys
`tfcfixes-<version>.jar` to **both** the client and server `coremods/` folders
(it's a coremod — it goes in `coremods/`, not `mods/`). Missing deploy dirs fail the
task loudly rather than silently skipping.
