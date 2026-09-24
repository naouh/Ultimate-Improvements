# Hotel de Vente (auction house)

![Auction house GUI (/hdv)](screenshot.png)

A player-to-player auction house for **MCPC+ 1.4.7** (Forge mod, built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)): list items for sale
straight from your inventory, browse and buy what others have listed, all inside a custom GUI and
paid with the server's Essentials economy.

## Usage

Press **H** (rebindable, "Hotel de Vente") or use `/hdv` (aliases `/ah`, `/shop`). On MCPC+ the
command may be denied to non-op players by the Bukkit permission layer; the key always works.

- **Buy** tab: browse the listings (paged, searchable by item or seller name), click *Buy*, pick a
  quantity and confirm. You pay the total; the seller receives it minus the configured tax.
- **Sell** tab: click an item in your inventory, pick a quantity (`-` / `+` / *Max*) and a price per
  unit, then *List for sale*. Your own listings appear on the right with a *Cancel* button that
  returns the items.

Everything is validated server-side (real inventory, Essentials balance, free slots before an item
is handed over), so a lagging or modified client cannot duplicate items or money. Listings persist
in the overworld's save data (`data/hdv_auctions.dat`).

## Config (`config/HDV.cfg`)

| Key | Default | Meaning |
|---|---|---|
| `maxActiveListings` | 14 | Max simultaneous listings per player. |
| `listingFee` | 0 | Flat fee charged when creating a listing. |
| `saleTaxPercent` | 5 | Share of each sale kept as a money sink. |
| `minPricePerUnit` / `maxPricePerUnit` | 1 / 1000000 | Allowed unit price range. |

## Install

Both sides: `mods/` on the server and on the clients. The server needs Essentials — the economy is
reached through Bukkit/Essentials reflectively, so there is no compile-time dependency.

## Build

`./gradlew build` — no third-party jars needed. The jar is copied into the dev client instance and
the pack's `mods/`, replacing any older build.

## Changelog

- **0.1.1** — Item icons no longer poke through the buy dialog's overlay (depth test left on after
  drawing icons). The *Search* button now shows the first page of results instead of keeping the
  current page number. Searching also matches the item's internal name, so modded items are
  findable even though the server cannot translate their display names. `mcmod.info` author key
  fixed.
- **0.1.0** — Initial release.
