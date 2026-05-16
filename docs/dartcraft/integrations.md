# Mod integrations

DartCraft auto-detects these mods on FML load and adds recipes / hooks / behaviours accordingly. Where there's a known bug (or in the GregTech case, an intentional-but-misguided behaviour) it's flagged with a pointer to [UpsilonFixes](upsilonfixes.md).

## BuildCraft (BC)

DartCraft's tightest integration — the Force Engine is itself a BC `IPowerProvider` source.

| Hook | What |
|---|---|
| **Force Engine** | Outputs MJ on its facing direction. Two-liquid (fuel + throttle). See [blocks.md → Force Engine](blocks.md#force-engine). |
| **Force Gear** | Recipe is `.F. / FIF / .F.` with **Stone Gear** as I when BC is loaded (instead of vanilla iron). Force Gears can substitute for Gold Gears in BC's Diamond Gear / Filler / Quarry / Combustion Engine recipes. |
| **BC Fuel / Oil** | Valid Force Engine fuels (3.0× and 1.5× modifiers respectively). |
| **Facades** | Force Bricks and Force Wood Logs/Planks can be turned into BC facades. |
| **BC Triggers** | Pre-1.5.1 only: the Force Infuser registers `Has Tome` and `Can Upgrade` BC triggers usable in BC gates. Removed in 1.5.1+. |
| **Emerald Pipes** | DartCraft adds a recipe variant using `gemGreenSapphire` (ore-dict) since vanilla emeralds are rare. |

> **Bug**: The Force Engine's actual MJ/cycle capacity is 400 MJ (16 MJ/t), but `TileEntityForceEngine.transferEnergy` hardcodes the cap at 250 MJ (10 MJ/t). Energy above 10 MJ/t is wasted. [UpsilonFixes](upsilonfixes.md) raises the cap.

> **Bug**: The Force Infuser "Go" button hardcodes `worldServerForDimension(0)` — so the Infuser is broken outside the Overworld. [UpsilonFixes](upsilonfixes.md) routes it through the player's actual world.

## IndustrialCraft 2 (IC2)

DartCraft loves IC2. Big integration surface.

| Hook | What |
|---|---|
| **Mining Drill / Diamond Drill → Power Drill** | Right-click in the Force Infuser with a Force Nugget. The Diamond Drill variant returns the diamonds as drops in the world (bluedart's anti-waste design). |
| **Chainsaw → Power Saw** | Same flow. |
| **Power Drill / Power Saw** | Three Upgrade Core sockets each. Both items implement IElectricItem and draw from batpacks / lappacks / Charge-socketed Force Tunic. |
| **Macerator recipes** | `oreLead → 2 dustLead` (whichever Lead dust is first in OreDict, typically TE's). `oreTungsten → 2 diamonds` (a knowing throwback to EE2). All missing colors of wool macerate to 1 string. |
| **Energy Crystal recipe** | Alternative recipe: 8 redstone + 1 `gemRuby` → 1 Energy Crystal. Saves diamonds for builders with rubies handy. |
| **Mixed Metal Ingot** | Alternate recipe using Force Ingots: `3 refined iron + 3 force ingot + 3 tin = 4 mixed metal ingots` (vs vanilla 2). |
| **UU-Matter recipes** | `3 UU` corner pattern → **8 Force Gem**. Also 5 UU → 1 Lapis Block, 5 UU → 1 Eye of Ender. |
| **Cheaper Electronic Circuit** | A second recipe path using Force Ingots returns 2 circuits per craft. |
| **Scrapbox loot** | DartCraft injects Force Shard (2.0%), Power Ore (0.75%), Force Gem (0.85%), Forestry capsules / cans (1.5%), Paper (2.5%), Eggs (2.5%). |
| **Bronze Force Transmutations** | Bronze tools/armor → bronze ingots (sword=2, pick=3, shovel=1, axe=3, hoe=2, helm=5, chest=8, legs=7, boots=4). |
| **Silver Dust smelting** | Forces the silver dust → silver ingot recipe to output exactly 1 (in case another mod sets it to 2). |
| **IC2 Battery** | Becomes the **Charge** upgrade material (Tier 4) — the gate to making Force Armor IC2-rechargable. |

## Forestry

| Hook | What |
|---|---|
| **Force Ingots from Carpenter** | 750 mB Liquid Force + a 2×2 / 1×2 of iron / bronze / refined iron / silver / xychoridite — yields 2 or 3 ingots depending on the material. |
| **Inert Cores from Carpenter** | 1 bucket Liquid Force + `STS / CDC / STS` (soul sand + tear + claw + diamond/sapphire). |
| **Squeezer** | 1 Force Gem → 1500 mB Liquid Force + 10% Force Shard. 1 Force Container → 1000 mB Liquid Force. 1 Force Log → 100 mB Liquid Force. |
| **Fermenter** | Force Sapling → Biomass (0.5× modifier, 2000 ticks). |
| **Crated items** | Force Gems are cratable. Crated Nikolite and Crated Brass have their broken recipes re-enabled by DartCraft. Crated UU-Matter recipe restored too (a Forestry patch had broken it). |
| **Proven Frame recipe** | `SSS / SFS / SSS` (S = Force Stick, F = Impregnated Frame). |
| **Liquid containers** | Liquid Force and milk both fit Forestry Cans, Wax Capsules and Refractory Capsules. |
| **Milk cans** | Right-click a Cow or Cold Cow with an empty Can / Capsule to fill with milk. |
| **Ethanol fuel** | Forestry Ethanol burns in the Force Engine. |
| **Milk throttle** | Milk is a +25% throttle vs water in the Force Engine. |
| **Honey throttle** | "A very rare, obscure bee-related liquid" (Honey from advanced apiaries) doubles throttle output vs water. |
| **Force Tree provider** | DartCraft registers itself with `CropProviders.arborealCrops` so Forestry's Arboretum farms can plant Force Saplings. (Pre-1.5.1 only — growth is intentionally slow.) |
| **Backpacks excluded** | Forestry backpacks are blacklisted from Force Packs by default (configurable). |
| **Hunter backpack loot** | DartCraft adds Tears, Claws, Raw Lambchops, Cooked Lambchops to the Hunter backpack loot. |

## Thermal Expansion (TE)

| Hook | What |
|---|---|
| **Sawmill** | 1 Force Log → 6 Force Planks + Sawdust (200 ticks). |
| **Grinding upgrade** | When the Grinding infusion fires on a Force Pick / Shovel, the broken block's drops are routed through TE's Pulverizer recipe set. On a Force Axe it goes through the Sawmill instead. (Falls back to IC2 Macerator if TE is absent.) |

## Thaumcraft

DartCraft items get sensible aspects:

| Item | Aspects |
|---|---|
| Force Nugget | Magic (cheap, easy to farm) |
| Force Ingot | Power 4 + Magic 4 + Metal 4 |
| Force Gem | Magic 4 + Power 4 + Crystal 4 |
| Force Rod | Exchange 8 + Magic 2 + Metal 2 |
| Force Tome / Fortune | Knowledge 8 + Valuable 4 |
| Tear | Spirit 4 + Water 4 + Evil 4 |
| Claw | Beast 4 + Eldritch 2 + Poison 2 |
| Force Wood | Plant 2 + Magic 1 + Wood 8 |
| Force Leaves | Plant 2 |
| Force Sapling | Plant 2 + Magic 1 + Power 1 |
| Force Stick | Magic 1 + Wood 4 |
| Force Brick | Rock 2 + Valuable 2 |
| Golden Power | Fire 2 + Power 4 + Magic 1 |
| Lambchop (raw) | Flesh 4 + Beast 2 |
| Lambchop (cooked) | Flesh 4 + Beast 2 + Life 4 |
| Milk Container (any) | Heal 2 |
| **IC2 Terra Wart** | Pure (Purus) 2 — DartCraft re-tags it because Thaumcraft itself didn't. |

**Thaumium Force Transmutations**: Thaumium tools and armor can be Force-transmuted in the Infuser back to their thaumium-ingot cost (sword=2, pick=3, etc.). Explicitly added because dungeon chests overflow with Thaumium Hoes and Axes in Thaumcraft 2.

## Railcraft

| Hook | What |
|---|---|
| **Steel Force Transmutations** | Steel tools and armor are Force-transmutable back to their steel-ingot cost (sword=2, pick=3, shovel=1, axe=3, hoe=2, helm=5, chest=8, legs=7, boots=4). |

No other Railcraft hooks — DartCraft doesn't touch Railcraft tracks / boilers / steam.

## XYCraft

| Hook | What |
|---|---|
| **Xychoridite as Force Ingot material** | Any color of Xychoridite (`xychoriditeBlue`, `xychoriditeRefined`, `xychoriditeRed`, `xychoriditeDark`, `xychoriditeLight` via ore-dict) makes 3 Force Ingots when paired with a Force Gem. |

## NotEnoughItems (NEI)

DartCraft ships an NEI handler (`NEIIntegration`) so the Force Infuser and Workbench-style recipes show up in NEI's recipe panel. No config required.

## EnderStorage

DartCraft has a hook stub for EnderStorage integration but the 0.1.10 build doesn't actually do anything with it (`loadEnderStorageIntegration()` is empty). The Ender Pack item exists either way — it's tied to your standard Ender Chest contents, NOT to ChickenBones' EnderStorage frequencies.

## GregTech

> **⚠️ This integration is harmful in modern packs. Use [UpsilonFixes](upsilonfixes.md) (`fixDartCraftForceDisablingGregTechTweaks`) to disable it.**

When GregTech is loaded, DartCraft runs `IMP.restore()` at post-init. This call **rewinds GregTech's IC2 recipe modifications** — the more-expensive Macerator, Compressor, Extractor recipes etc. are reset to their vanilla IC2 forms. bluedart logs the operation as "Applying postliminary IC2 molestation consolement."

bluedart's intent: he disliked GregTech's harder recipes and wanted DartCraft users to keep the friendlier IC2 defaults. The problem:

1. GregTech already exposes a config (`GregTech_HardRecipes.cfg`) to disable each harder recipe individually. The pack author and the player both have full control.
2. DartCraft's blanket reset wipes ALL GT recipe tweaks regardless of which ones you actually wanted.
3. The reset is sensitive to load order — depending on when GT applies its tweaks vs when DartCraft hits IMP.restore(), behaviour can vary.

The result is a feature designed to "help" users that actively interferes with the modpack author's intended recipe progression. [UpsilonFixes](upsilonfixes.md) patches the `Class.forName("gregtechmod.GT_Mod")` string to a non-existent class so the IMP.restore() branch never fires, letting GT's tweaks take effect normally.

## What DartCraft does NOT integrate with (yet)

- **Mystcraft** — no agebook page recipes.
- **ComputerCraft** — no peripherals.
- **Applied Energistics** — no ME crafting patterns or grinder recipes.
- **Power Converters** — no FE / RF / EU bridges (the only DartCraft fuel-bridge is the BC MJ output from the Force Engine itself).
