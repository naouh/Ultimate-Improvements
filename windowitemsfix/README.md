# WindowItemsFix

A small FML coremod for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (FTB Ultimate / Ultimate
Remastered era), built with [Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom).

Fixes a client-side crash and a related stuck-GUI bug that show up when a server
sends a `WindowItems` packet for a `Container` whose slots haven't been built yet
— typically during the TileEntity sync race right after teleporting or logging in
near a GregTech-Addon machine (and any other mod that briefly exposes a
placeholder block before its TE arrives).

## The bug

When the placeholder block is right-clicked during that sync window, the server
dispatches a GUI-open packet. The client builds the `Container`, but because the
TE hasn't synced yet the container ends up with **zero slots**. The next
`Packet104WindowItems` then calls `getSlot(0)` on an empty `ArrayList` and the
client crashes with:

```
java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
    at java.util.ArrayList.rangeCheck(...)
    at java.util.ArrayList.get(...)
    at rq.b(Container.java)                            // Container.getSlot
    at rq.a(Container.java)                            // Container.putStacksInSlots
    at di.a(NetClientHandler.java)                     // handleWindowItems
    at gx.a(Packet104WindowItems.java:62)
```

## What it patches

The coremod registers two ASM transformers.

### `ContainerTransformer` → `net.minecraft.inventory.Container`

Patches `Container.putStacksInSlots(ItemStack[])` to bail when
`inventorySlots` is empty:

```java
if (this.inventorySlots.isEmpty()) return;
```

That alone stops the crash, but leaves an empty placeholder GUI open with no way
to close it short of typing `/tp` somewhere else, because the `Container` never
gets re-synced.

### `DisplayGuiScreenTransformer` → `net.minecraft.client.Minecraft`

Patches `Minecraft.displayGuiScreen(GuiScreen)` to refuse to open any
`GuiContainer` whose underlying `Container` has zero slots — so the empty GUI
never appears in the first place. The player just right-clicks again a second
later, once the TE has arrived.

Both transformers handle MC 1.4.7's obfuscated names (`rq` / `avf` / `aul` etc.)
and the clean MCP names, so the coremod loads in both a normal client and a dev
environment.

## Installation

Drop `windowitemsfix-1.0.0.jar` into the `coremods/` folder (not `mods/`) of a
1.4.7 Forge profile. No config, no dependencies.

## Building

```bash
./gradlew build
```

The remapped jar lands in `build/libs/windowitemsfix-1.0.0.jar`.

### `libs/` — third-party jars

None — this coremod only needs MC + Forge, both of which Voldeloom pulls in
automatically. There's nothing to drop into `libs/`.

## Credits

By Naouh. Originally written after repeatedly crashing the FTB Ultimate
Remastered client when logging in next to a GregTech industrial centrifuge.
