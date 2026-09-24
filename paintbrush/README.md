# PaintBrush

![Painted IC2 cables](screenshot.png)

A small standalone item mod for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (FTB Ultimate /
Ultimate Remastered era), built with [Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom).

Adds one reusable item — the **Paint Brush** — for colouring IndustrialCraft 2 cables.

## What it does

* **Right-click** an IC2 *Glass Fibre Cable* (or any other IC2 cable) with the brush — the
  whole connected cable run is painted the brush's current colour. The run is flood-filled
  (every cable of the same type, reached by adjacency, up to 512) so it stays one uniform
  colour: IC2 only connects cables of equal/neutral colour, so a half-painted run would show
  gaps where the colours meet.
* **Sneak + right-click** any block, or the air — cycles the brush to the next of the 16 dye
  colours. The new colour is printed in chat; the held colour is also shown in the tooltip.
* The brush has no durability — it never wears out.

The brush paints anything implementing IC2's `ic2.api.IPaintableBlock`, so it works on every
IC2 cable type, not just glass fibre.

The colour is stored in NBT, not item metadata — creative mode silently restores an item's
metadata after a use, which would otherwise revert the colour on every click.

## Render-glitch fix

When a cable's colour changes, IC2 only re-renders that one block, leaving neighbouring
cables with stale connection geometry until the chunk re-renders for some other reason — a
segment of cable appears to vanish. This is an IC2 bug; the vanilla IC2 Painter triggers it
too. `PaintRenderFix` (client-side) watches for a right-click on any paintable block and
re-renders the surrounding region for a short window afterwards, so the fix also covers the
IC2 Painter — not just this brush.

## Recipes

| Result | Recipe |
|---|---|
| Paint Brush | `stick` + `string` + any one dye (shapeless) — one recipe per colour |
| Re-colour | an existing Paint Brush + any one dye (shapeless) |

The dye slot is ore-dictionary (`dyeRed`, `dyeBlue`, …), so vanilla and modded dyes both work.

## Building

```bash
./gradlew build
```

The remapped jar lands in `build/libs/paintbrush-0.1.3.jar`.

### `libs/` — third-party jar

`libs/*.jar` is git-ignored. Before building, drop in:

| `libs/` needs | How |
|---|---|
| `ic2api.jar` | Just `ic2/api/IPaintableBlock.class`. Extract it from `IC2.jar`: `jar cf libs/ic2api.jar -C <extract-dir> ic2/api/IPaintableBlock.class` |

`modCompileOnly` lets Voldeloom remap the obfuscated `World` reference inside
`IPaintableBlock.colorBlock(World,int,int,int,int)` to a clean MCP name.

## Config

`config/PaintBrush.cfg` holds the item ID (default `14785`). Change it there if it collides
with another mod in a heavily-loaded pack.

## Texture

`tools/GenTexture.java` regenerates the 16-colour item sheet:

```bash
cd paintbrush && java tools/GenTexture.java
```

## Credits

Cable painting goes through IndustrialCraft 2's public `IPaintableBlock` API. By Naouh.
