# Force Infusions (Upgrades)

Force Infusions are the upgrade system applied at a [Force Infuser](blocks.md#force-infuser). DartCraft defines **21 distinct upgrade types**, organised in 8 tiers (0–7). The upgrade's tier is the **minimum Force Tome tier** needed to apply it.

## Upgrade table

| ID | Name | Tier | Max level | Unique? | What it does (typical) |
|---:|---|:---:|:---:|:---:|---|
| 0 | **Force** | 1 | 1 | — | Generic re-color "Force I" mark; tier-0 upgrade applied via brick wildcard. |
| 5 | **Damage** | 1 | 5 | — | +damage on weapons (1–5 levels). |
| 1 | **Heat** | 2 | 4 | Yes | Smelts what you mine / cook drops / Fire Aspect on swords / converts Force Bow into Heat Bow. |
| 2 | **Speed (Mining)** | 2 | 5 | — | Faster mining speed on the tool head. |
| 3 | **Lumberjack** | 2 | 1 | — | Axe chops the whole tree. |
| 4 | **Discovery** | 2 | 1 | — | Compass-like behaviour (XP-orb attraction / ore detection). |
| 10 | **Luck** | 3 | 4 | — | Fortune on pick/spade/axe, Looting on sword. Adds a real vanilla Fortune/Looting enchant. |
| 11 | **Grinding** | 3 | 1 | — | Bonus drops / cobble breaks faster (flint material). |
| 12 | **Rainbow** | 3 | 1 | — | Cosmetic — gives the tool the active dye color (lapis-tier 4 dye). |
| 13 | **Bane** | 3 | 5 | — | Like Bane of Arthropods — extra damage to spiders/insects. |
| 14 | **Holding** | 3 | 1 (stacks) | — | +8 slots per level on a Force Pack. Up to 5 (40 slot cap). |
| 15 | **Experience** | 3 | 3 | — | Tool absorbs XP; sword grants XP per kill. Bottle-o'-enchanting is the material. |
| 21 | **Touch** | 4 | 1 | — | Silk Touch on the tool. Adds the real vanilla Silk Touch enchant. |
| 23 | **Swiftness** | 4 | 1 | — | Speed boost on boots / armor. |
| 24 | **Healing** | 4 | 2 | — | Passive regen on armor. |
| 30 | **Wing** | 5 | 1 | Yes | Creative-style flight on chestplate (uses Liquid Force fuel). |
| 31 | **Charge** | 5 | 1 | — | EU storage / battery-like charge (IC2). |
| 40 | **Ender** | 6 | 1 | Yes | Teleport: Ender Bow teleports to impact, Ender boots teleport on use, Ender Pack accesses the player's ender storage. |
| 50 | **Light** | 7 | 5 | Yes | Emits light around the holder / bow shoots light arrows. |
| 51 | **Sturdy** | 7 | 3 | — | Reduces durability loss / blast resistance. |
| 60 | **Explosion** | 7 | 1 | Yes | Splosion — area effect. |

Tier numbers in the code: `0 = none`, `1 = Force/Damage`, `2 = Heat/Mining/Lumberjack/Discovery`, `3 = Luck/Grinding/Rainbow/Bane/Holding/Experience`, `4 = Touch/Swiftness/Healing`, `5 = Wing/Charge`, `6 = Ender`, `7 = Light/Sturdy/Explosion`.

"Unique" upgrades cannot stack with other unique upgrades on the same item.

## Upgrade materials

Drop these into Infuser slots 3–10 (8 slots) to apply the matching upgrade. **Material amount = upgrade level** (3 nuggets = Force III, up to the upgrade's max level).

| Material | Upgrade | Tier req. | Bonus points | Efficiency (energy/liquid) |
|---|---|:---:|---:|---:|
| **Force Nugget** | Force | 1 | 0 | 0.75 |
| **Claw** | Damage | 1 | 0 | 0.75 |
| **Redstone** | Speed (Mining) | 2 | 0 | 1.00 |
| **Force Wood** (data 5, special log) | Lumberjack | 2 | 5 | 1.50 |
| **Coal** | Heat | 2 | 0 | 1.00 |
| **Charcoal** | Heat | 2 | 0 | 1.00 |
| **Golden Power Source** | Heat | 2 | 5 | 1.50 |
| **Blaze Powder** | Heat | 2 | 10 | 2.00 |
| **Lapis Lazuli** (dye 4) | Rainbow | 3 | 0 | 0.25 |
| **Obsidian** | Sturdy | 7 | 0 | 1.25 |
| **Bedrock-ish** *(amq.as)* | Sturdy | 7 | 10 | 1.75 |
| **Fortune** | Luck | 3 | 0 | 2.00 |
| **Cobweb** | Touch | 4 | 5 | 2.00 |
| **Sugar** | Swiftness | 4 | 0 | 3.00 |
| **Fermented Spider Eye** | Bane | 3 | 10 | 1.50 |
| **Ender Pearl** | Holding | 3 | 0 | 0.25 |
| **Feather** | Wing | 5 | 0 | 3.00 |
| **Flint** | Grinding | 3 | 0 | 1.50 |
| **Ghast Tear** | Healing | 4 | 10 | 2.00 |
| **Tear** (DartCraft drop) | Healing | 4 | 0 | 1.50 |
| **Compass** | Discovery | 2 | 5 | 3.00 |
| **Eye of Ender** | Ender | 6 | 0 | 5.00 |
| **Bottle o' Enchanting** | Experience | 3 | 5 | 2.25 |
| **Glowstone Dust** | Light | 7 | 0 | 3.00 |
| **RE-Battery** (IC2) | Charge | 5 | 10 | 2.00 |

> The `bonus` field is the bonus XP added per material slot used during infusion. It bumps you toward the next Tome tier on top of the standard +25 first-time-applied points.

## How materials become tome points

When an infusion succeeds, every material slot contributes to the tome:

```
bonusXp += ((mat.bonus / 5) × mat.efficiency + upgrade.tier × 5) ÷ 2 + 1
```

Each upgrade type also adds a flat **+25 points** the first time it lands on the tome.

So a basic Force I (Force Nugget):
- `(0/5 × 0.75 + 1×5) / 2 + 1 = 3.5` (rounded to 3 or 4 depending on stacking)
- Plus +25 first-time bonus → **~28 points** for the first Force I you ever apply.

A Healing II (2 Ghast Tears):
- `(10/5 × 2.0 + 4×5) / 2 + 1 = 13` per tear, twice → 26
- Plus +25 first-time → **~51 points**.

## Valid infusion targets

Slot 2 of the Infuser accepts:

| Target | Valid upgrades |
|---|---|
| Force Sword | Force, Damage, Heat, Bane, Luck, Touch, Healing, Sturdy, Experience, Light, Explosion |
| Force Pickaxe / Shovel / Axe | Force, Speed, Heat, Luck, Touch, Sturdy, Grinding, Lumberjack (axe only), Healing |
| Force Bow | Force, Heat (→ Heat Bow), Ender (→ Ender Bow), Light, Damage |
| Force Shears | Force, Speed, Touch |
| Force Armor | Force, Damage, Heat, Sturdy, Wing (chest), Swiftness (boots), Healing (helmet), Ender (boots) |
| Force Pack | Holding, Ender (mutually exclusive) — sturdy + experience |
| Upgrade Core | Any one upgrade type only |
| Force Tome | Force, Experience |
| Force Rod | One type at a time |

The Infuser auto-detects compatibility (`IForceUpgradable.validUpgrades()` per item, plus the wildcard system for IC2 drills/chainsaws).

## Wildcard items

Some non-DartCraft items can also be infused via the wildcard system:

| Vanilla / Modded item | Treated as |
|---|---|
| **White wool** (`amq.bp` meta 0) | Force I material → applies to Force Brick meta 11 |
| **IC2 Mining Drill** | Treated as a Power Drill base (Force upgrade) |
| **IC2 Diamond Drill** | Same |
| **IC2 Chainsaw** | Treated as a Power Saw base (Force upgrade) |

## Pack sizing

Force Pack starts at `Config.packSize` (default 8). Each Holding upgrade adds +8 slots (max 40, capped at 5 holding levels). The Infuser refuses to apply Holding if it would exceed `(currentSize - initialSize) / 8 > activeTomeTier - 1`, so you need to climb the tome to push a pack past mid-size.

## Tier-7 (Mastered) bonus slot

The 11th inventory slot (`inventoryContents[10]`) only accepts materials when the tome is mastered (Tier 7+). Otherwise its contents are dropped automatically. This is the slot for **Explosion**, **Light V**, **Sturdy III** stacking, and any other tier-7 unique.
