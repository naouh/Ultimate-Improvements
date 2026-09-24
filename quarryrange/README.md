# QuarryRange

![Quarry area editor with live laser preview](screenshot.png)

Placement-time area editor for the **BuildCraft Quarry** on **MC 1.4.7 / Forge
`1.4.7-6.6.2.534`** (built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)). Placing a quarry no
longer starts it: an editor opens where you pick the mining size and where the area sits, with a
live laser preview in the world. The quarry stays idle — even when powered — until you close the
editor.

## Usage

- **Size**: type it, use `-` / `+`, or jump to *Min* / *Max* (the default is the maximum). The label
  shows the frame edge and the mined area inside it (`14 x 14 (mines 12 x 12)`).
- **Position**: cycle between *In front* (BuildCraft's usual placement), *Corner* and *Centered* on
  the quarry.
- **Confirm** (or Escape) applies the size shown and releases the quarry; **Cancel** reverts to the
  default size (`defaultSize`, max unless configured) and releases it too.

## Config (`config/QuarryRange.cfg`)

| Key | Default | Meaning |
|---|---|---|
| `minSize` | 11 | Smallest selectable box edge (11 = BuildCraft's own 9×9 mined area). |
| `maxSize` | 64 | Largest selectable box edge. |
| `defaultSize` | 64 | Edge applied on placement and on Cancel. |

## How it works

Standalone mod, no BuildCraft patch: the placement event is intercepted, the quarry is held idle
through reflection while the editor is open, every change is previewed live, and the chosen box
is written to the quarry as its mining area when you close the editor.

## Install

`mods/` on **both** sides (the server applies the area, the client shows the editor).

## Build

`./gradlew build` — no third-party jars needed.
