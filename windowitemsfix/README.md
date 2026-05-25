# WindowItemsFix

A small FML coremod for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (FTB Ultimate / Ultimate
Remastered era), built with [Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom).

Fixes the post-teleport / post-login race where right-clicking a TileEntity-backed block
before its TE has synced from the server causes a client crash or scrambled inventory —
most commonly seen with GregTech-Addon machines, but the same race exists for any
`BlockContainer` whose TE arrives in a separate packet.

## The bug

After a chunk is sent, block IDs arrive before the TileEntity sync packets. If the player
right-clicks a machine during that window, the server processes the use-block as if the
client were in sync; the response is a GUI-open packet followed by a `WindowItems` slot
update. The client has nothing to back the container (TE still null), and one of two
things happens:

* `Container.putStacksInSlots` calls `getSlot(0)` on an empty `ArrayList` and the client
  crashes with `IndexOutOfBoundsException: Index: 0, Size: 0` in
  `NetClientHandler.handleWindowItems`:

  ```
  java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
      at java.util.ArrayList.rangeCheck(...)
      at java.util.ArrayList.get(...)
      at rq.b(Container.java)                            // Container.getSlot
      at rq.a(Container.java)                            // Container.putStacksInSlots
      at di.a(NetClientHandler.java)                     // handleWindowItems
      at gx.a(Packet104WindowItems.java:62)
  ```

* Or, if that crash is patched in isolation, the placeholder container quietly accepts
  the slots and the items leak into the wrong inventory once the real TE finally arrives.

The cleanest fix is to *not send the click at all* until the TE has shown up.

## What it patches

The coremod registers two ASM transformers.

### `RightClickGuardTransformer` → `net.minecraft.client.multiplayer.PlayerControllerMP`

Primary fix. Patches `onPlayerRightClick` so the use-block packet is not sent (and
`Block.onBlockActivated` not invoked) when:

* the target block is a `BlockContainer` whose `world.getBlockTileEntity(x,y,z)` returns
  `null` — the TE simply hasn't arrived yet, or
* the TE is a GregTech `BaseMetaTileEntity` whose `mMetaTileEntity` field is null (the
  *"You ran into a serious Bug"* placeholder state). Checked reflectively through
  [`GTCompat`](src/main/java/com/nao/windowitemsfix/GTCompat.java), so the coremod has no
  compile-time dependency on GregTech and still loads in packs that don't ship it.

When the guard trips, the click is a no-op — the player just right-clicks again a moment
later, once the TE has actually synced.

### `ContainerTransformer` → `net.minecraft.inventory.Container`

Safety net. Patches `Container.putStacksInSlots(ItemStack[])` to bail when
`inventorySlots` is empty:

```java
if (this.inventorySlots.isEmpty()) return;
```

If a 0-slot `WindowItems` packet somehow still reaches the client through an interaction
path the right-click guard doesn't cover, this prevents the original
`IndexOutOfBoundsException` crash.

Both transformers handle MC 1.4.7's obfuscated names (`rq` / `aul` / `aen` etc.) and the
clean MCP names, so the coremod loads in both a normal client and a dev environment.

## Installation

Drop `windowitemsfix-1.0.0.jar` into the `coremods/` folder (not `mods/`) of a 1.4.7
Forge profile. No config, no dependencies.

## Building

```bash
./gradlew build
```

The remapped jar lands in `build/libs/windowitemsfix-1.0.0.jar`.

### `libs/` — third-party jars

None — this coremod only needs MC + Forge, both of which Voldeloom pulls in
automatically. There's nothing to drop into `libs/`.

## Credits

By Naouh. Originally written after repeatedly crashing the FTB Ultimate Remastered client
when logging in next to a GregTech industrial centrifuge.
