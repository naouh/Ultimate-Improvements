# Hotel de Vente (auction house)

![Auction house GUI (/hdv)](screenshot.png)

A player-to-player auction house for **MCPC+ 1.4.7** (Forge mod, built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)): list items for sale
straight from your inventory, browse and buy what others have listed, all inside a custom GUI and
paid with the server's Essentials economy.

## Usage

Open it with `/hdv` (alias `/ah`).

- **Buy** tab: browse the listings (paged) and click one to buy it.
- **Sell** tab: click an item in your inventory, pick a quantity (`-` / `+` / *Max*) and a price per
  unit, then *List for sale*. Your own listings appear on the right with a *Cancel* button that
  returns the items.

## Install

Both sides: `mods/` on the server and on the clients. The server needs Essentials — the economy is
reached through Bukkit/Essentials reflectively, so there is no compile-time dependency.

## Build

`./gradlew build` — no third-party jars needed.
