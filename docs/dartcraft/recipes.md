# Recipes

All crafting / smelting / machine recipes registered by DartCraft. Some are conditional on which mods are installed — those sections are flagged.

Legend: `I` = Force Ingot, `S` = Force Stick, `G` = Force Gem, `N` = Force Nugget, `F` = Force Stick (in bow recipe), `R` = Force Rod, `.` = empty slot.

## Core resources

### Force Ingot

```
shapeless: 1 Force Gem + 2 Iron Ingot       →  2 Force Ingot
shaped:    FFF / FFF / FFF (Force Nugget)   →  1 Force Ingot
shapeless: 1 Force Ingot                    →  9 Force Nugget
```

With Forestry ore-dict (shapeless):

```
1 Force Gem + 2 Bronze Ingot                →  2 Force Ingot
1 Force Gem + 2 Refined Iron / Silver       →  3 Force Ingot
1 Force Gem + 2 Xychoridite (any variant)   →  3 Force Ingot
```

### Force Sticks

```
W  (Force Wood plank, meta 1)               →  8 Force Sticks
W
```

Logs convert to planks shapeless: `1 Force Log → 4 Force Planks`.

### Golden Power Source

Furnace: `1 Force Plank (forceLog meta 0) → 2 Golden Power Source` (2.0 XP). The output is used in:

```
G   →  6 Torches
S       (Golden Power + Stick)
```

## Force Tools

```
Force Pickaxe                  Force Sword                Force Shovel
III                            I                          I
.S.                            I                          S
.S.                            S                          S

Force Axe                      Force Shears
II                             F.
IS                             .F
.S
```

```
Force Bow (either)             Force Arrow (×6)
 FS                            N
F S          or                S
 FS                            F   (F = feather)
```

```
Force Bow (mirror)
SF.
S F
SF.
```

## Force Rod

Two tiers:

```
.. I       (vanilla stick → 48 durability)
.S.
N..
```

```
.. I       (Force Stick → 512 durability)
.S.
N..
```

## Containers and decoration

### Force Pack

```
ILI    L = Leather
LCL    I = Force Ingot
ILI    C = Chest
```

### Force Brick → Slab / Stairs

For each color metadata `i` ∈ 0..15:

```
BBB        →  6 Slab(i)
                                  (B = Force Brick of color i)
S    →  1 Brick(i)
S          (S = Slab(i))

B..        →  4 Stair(i)
BB.
BBB

SS         →  6 Brick(i)
SS         (S = Stair(i))
```

For wood variants (slab meta 16, stair meta 16):
- 3× Force Wood plank (forceLog meta 1) → 6× wood-slab.
- 1×2 wood slab → 1 plank.
- Stair stair (3+2+1) of planks → 4 wood stairs.
- 2×2 wood stairs → 6 planks.

## Force Engine

Requires BC oredict if BC is installed (otherwise the shapeless Force Gear recipe registers first).

```
Force Gear (only if no BuildCraft)         Force Engine
.F.                                        III
FIF   (F = Force Ingot, I = Iron)          .G.
.F.                                        FPF
                                           
                                           I = Force Ingot
                                           G = Glass (amq.P)
                                           F = Force Gear
                                           P = Piston (amq.ac)
```

Also auto-registers a fallback **vanilla Piston recipe** so you can always make one:

```
PPP     P = plank
CIC     C = cobblestone
CRC     I = Force Ingot
        R = Redstone
```

## Inert Core (Nether Star alternative)

Only if you don't have Forestry, or if `netherStarRecipe = true`.

```
STS     S = Soul Sand
CDC     T = Tear / Force Flax
STS     C = Claw
        D = Diamond *or* Sapphire
```

## Clipboard

```
PIP     P = Paper
PpP     I = Iron Ingot
PpP     p = Piston
```

## Fortune Cookie

```
shapeless: Cookie + Paper       →  1 Fortune Cookie
shapeless: 1 Fortune            →  1 Paper
```

## Smelting

| Input | Output | XP |
|---|---|---:|
| Raw Lambchop | Cooked Lambchop | 1.0 |
| Force Log (meta 0, plank) | 2× Golden Power Source | 2.0 |

## Vanilla recipe additions

DartCraft also adds these vanilla recipes that work with stock Minecraft:

```
Cobweb (amq.Z)        Vanilla Piston (described above)         Anvil-Helper "S S"
S.S                                                            S = String
.S.
S.S
```

DartCraft additionally re-registers a Bottle o' Enchanting recipe:

```
G       G = Glass Bottle
R       R = Redstone
B       B = Bookshelf (?) — see source for exact mapping
```

## Forestry integration

### Carpenter Manager

| Time | Liquid | Box | Output | Pattern |
|---:|---|---|---|---|
| 20 | 1000 mB Liquid Force | — | Inert Core | `STS / CDC / STS` (S = soul sand, T = tear, C = claw, D = diamond or sapphire) |
| 10 | 750 mB Liquid Force | — | 2 Force Ingot | `II / II` or `I / I` (I = Iron) |
| 10 | 750 mB Liquid Force | — | 2 Force Ingot | as above with Bronze |
| 10 | 750 mB Liquid Force | — | 3 Force Ingot | as above with Refined Iron / Silver / Xychoridite |
| 5 | 100 mB Water | Forestry Crate | Crated Nikolite | 3×3 Nikolite dust |
| 5 | 100 mB Water | Forestry Crate | Crated Brass | 3×3 Brass ingot |
| 5 | 100 mB Water | Forestry Crate | Crated UU-Matter | 3×3 UU-Matter |
| — | — | — | Crated Force Gems | 9 Force Gem (and inverse) |
| — | — | — | Crated Nikolite/Brass/UUM | Reverse from crate |

### Squeezer Manager

| Time | Inputs | Liquid | Side output | Chance |
|---:|---|---|---|---:|
| 8 | 1 Force Gem | 1500 mB Liquid Force | Force Shard | 10% |
| 5 | 1 Force Container (any) | 1000 mB Liquid Force | — | — |
| 8 | 1 Force Log | 100 mB Liquid Force | — | — |

### Fermenter Manager

| Input | Time | Modifier | Output |
|---|---:|---:|---|
| Force Sapling | 2000 ticks | 0.5× | Biomass |

## IC2 integration

| Recipe | Output |
|---|---|
| Macerate **oreLead** | 2× Lead Dust |
| Macerate **oreTungsten** | 2× Diamond |
| Macerate any colored wool | 1× String |
| 3 Redstone + 1 Ruby | 1 Energy Crystal (alternative to vanilla) |
| 3 Refined Iron + 3 Force Ingot + 3 Tin Ingot | 4 Mixed Metal Ingot |
| 3 UU-Matter (corner pattern) | 8 Force Gem |
| 5 UU-Matter (corner pattern) | 1 Lapis Block (amq.bU) |
| 5 UU-Matter (T pattern) | 1 Eye of Ender |
| Electronic Circuit + Force Ingot + Cable + Redstone | 2 Electronic Circuits (cheaper recipe) |
| Scrapbox drops | Force Shard (2.0%), Power Ore (0.75%), Force Gem (0.85%), Forestry capsules/cans (1.5%), Paper (2.5%) |

Smelting fix: 1 Silver Dust → 1 Silver Ingot (the result amount is forced to 1).

## Thermal Expansion integration

| Machine | Time | Input | Output | Side |
|---|---:|---|---|---|
| Sawmill | 200 | 1 Force Log | 6 Force Planks | + Sawdust |

## Liquid registrations

| Liquid | Slot | Container ↔ Empty |
|---|---|---|
| Liquid Force | 1000 mB | Force Bucket ↔ Vanilla Bucket |
| Liquid Force | 1000 mB | Force Can (Forestry) ↔ Empty Can |
| Liquid Force | 1000 mB | Wax Capsule (Forestry) ↔ Empty Capsule |
| Liquid Force | 1000 mB | Refractory Capsule (Forestry) ↔ Empty Refractory |
| Milk | 1000 mB | Milk Can/Capsule (Forestry) ↔ matching empties |

Liquid Force is also registered in the **Forge LiquidDictionary** as `liquidForce`.

## Force Transmutations (via Force Rod / Infuser)

See [items.md → Force Transmutations](items.md#force-transmutations) for the input-output table.
