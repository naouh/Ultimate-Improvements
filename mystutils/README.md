# Myst Utils

![Writing desk search bar and /instabilities](screenshot.png)

Two small quality-of-life add-ons for **Mystcraft** on **MC 1.4.7 / Forge `1.4.7-6.6.2.534`**
(built with [Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)):

- **`/instabilities`** — reports the instability effects active in the Age you are standing in
  (or tells you the Age is stable).
- **Writing desk search** — a search bar above the notebook pages in the Writing Desk. Type part
  of a symbol name (`lava_` above) and only the matching pages are shown, with a count.

## How it works

The command reads `AgeData.getEffects` reflectively. The search bar is injected with ASM into
Mystcraft's `GuiWritingDesk` / `GuiElementPageSurface`, which then ask `SearchHook` which pages to
draw — no Mystcraft class is replaced.

## Install

Coremod: `coremods/` on **both** sides (the command is server-side, the search bar client-side).

## Build

`./gradlew build` — no third-party jars needed.
