# Force Infusions (Upgrades)

Force Infusions are the upgrade system applied at a [Force Infuser](blocks.md#force-infuser). DartCraft defines around 20 distinct upgrade types, organised in 7 tiers (0–6 in the 0.1.11 wiki; 0.1.10 also exposes a Tier 7 "Explosion" slot). The tier on an upgrade is the **minimum Force Tome tier** required to apply it.

> This page was reconstructed from the original 0.1.11 thread/wiki at the [Minecraft Forum archive](https://web.archive.org/web/20130509170724/http://www.minecraftforum.net:80/topic/1686840-151-dartcraft-beta-0111/) (the canonical reference) and cross-checked against the decompiled `bluedart.api.ForceUpgradeManager` from the 0.1.10 (1.4.7) jar. Where the two disagree the 1.4.7 build is authoritative for that section.

## How the tome levels up

When you apply an infusion the tome gains points from every material in slots 3–10:

| Material tier | Points per material |
|:---:|---:|
| 0 | 1 |
| 1 | 5 |
| 2 | 15 |
| 3, 4 | 25 |
| 5, 6 | 50 |

On top of that, **the first time** the tome ever sees a given *upgrade type* it gets a flat **+25 bonus**. That's why your first Force I infusion suddenly hands you 26 points and the next ones barely move the bar.

Some materials also carry an explicit bonus (the "10 point bonus" on Ghast Tears, etc.) — that's added on top.

**Tome tier thresholds** (Force Points needed for next tier):

| Tome tier | Points required |
|:---:|---:|
| 1 (start) | 0 |
| 2 | 96 |
| 3 | 270 |
| 4 | 600 |
| 5 | 1020 |
| 6 | 1440 |
| 7 (Mastered) | 2400 |

### Bricks-for-points trick

A **Stone Brick** in the upgrade slot accepts a Force upgrade. That's 1 point per brick (Tier 0 upgrade) — or 26 the first time. Cheap way to push the tome past a wall.

## Upgrade material list (Tier-by-Tier)

The format is `Upgrade Name (Max Level)` then the materials that produce it. Where a material gives bonus points to the tome on top of its tier base, the bonus is noted in parentheses.

### Tier 0 — available from any tome

| Upgrade | Max | Material(s) |
|---|:---:|---|
| **Force** | 1 | Force Nugget |
| **Damage** | 5 | Claw |

### Tier 1

| Upgrade | Max | Material(s) |
|---|:---:|---|
| **Heat** | 4 | Coal · Charcoal · Golden Power Source (+5) · Blaze Powder (+10) |
| **Speed** | 5 | Sugar |
| **Lumberjack** | 1 | Force Log |

### Tier 2

| Upgrade | Max | Material(s) |
|---|:---:|---|
| **Luck** | 4 | Fortune (the cookie item) |
| **Grinding** | 1 | Flint |
| **Rainbow** | 1 | Lapis Lazuli |
| **Bane** | 5 | Spider Eye / Fermented Spider Eye |
| **Holding** | 1 | Glass Bottle (0.1.11) / Force Flask (later builds) |
| **Experience** | 3 | Bottle o' Enchanting |
| **Discovery** *(0.1.10 only)* | 1 | Compass |

### Tier 3

| Upgrade | Max | Material(s) |
|---|:---:|---|
| **Touch** | 1 | Cobweb |
| **Swiftness** | 1 | Arrow |
| **Healing** | 2 | Ghast Tear (+10) · DartCraft Tear |
| **Camo** *(0.1.11+)* | 1 | Any Potion of Invisibility |

### Tier 4

| Upgrade | Max | Material(s) |
|---|:---:|---|
| **Wing** | 1 | Feather |
| **Charge** | 5 | IC2 RE-Battery |

### Tier 5

| Upgrade | Max | Material(s) |
|---|:---:|---|
| **Ender** | 1 | Ender Pearl |
| **Charge2** *(0.1.11+)* | 5 | IC2 Energy Crystal |

### Tier 6

| Upgrade | Max | Material(s) |
|---|:---:|---|
| **Light** | 5 | Glowstone Dust |
| **Sturdy** | 3 | Brick · Obsidian |

### Tier 7 — 0.1.10 internal only

`ForceUpgradeManager` in the 0.1.10 jar exposes an **Explosion** entry at tier 7 (ID 60, unique, max level 1). The 0.1.11 wiki doesn't list it as a finished upgrade — treat it as experimental on the 1.4.7 build.

## What each upgrade *does* (per target)

The upgrade target dictates the effect. The same upgrade applied to different gear behaves differently.

### Force Sword
- **Damage** — vanilla Sharpness.
- **Heat** — vanilla Fire Aspect.
- **Bane** — vanilla Bane of Arthropods.
- **Luck** — vanilla Looting.
- **Wing** — *transforms into a Wing Sword*. While held, holding **Space** slows your fall (uses the Wing Meter). **Shift+Space+RightClick** initiates flight. The meter shows top-left and recharges on the ground.
- **Light** — emits a light source while held.

### Force Pickaxe / Shovel
- **Heat** — auto-smelts every drop that has a smelting recipe.
- **Speed** — extra mining speed.
- **Luck** — vanilla Fortune.
- **Grinding** — runs drops through Thermal Expansion's Pulverizer (or IC2 Macerator if TE absent). Uses extra durability per block — pairs with Sturdy.
- **Touch** — vanilla Silk Touch.
- **Sturdy** — vanilla Unbreaking (1-to-1 ratio of upgrade level to enchant level).

### Force Axe
- **Same as Pickaxe** plus **Lumberjack** — breaks every wood block in a vertical pillar instantly, at 2× durability cost per block. Axe-exclusive.
- **Grinding** on axe routes drops through TE's Sawmill instead of the Pulverizer.

### Force Bow
- **Damage** — vanilla Power.
- **Heat** — turns the bow into a **Heat Bow** (sets the target on fire).
- **Luck** — simulated Looting on mob kills (custom logic — bows can't have vanilla Looting).
- **Swiftness** — vanilla Punch (knockback) plus extra arrow velocity.
- **Ender** — turns the bow into the **Ender Bow** (teleports the player to the impact point on hit).

### Force Shears
- **Heat** — sets the sheared sheep on fire. (Listed as a flavour joke in the wiki.)
- **Rainbow** — sheep drops random-colour wool regardless of its actual colour.
- **Luck** — extra drops.
- **Grinding** — runs wool through the macerator pathway, yielding string.

### Force Rod (one upgrade per rod)
The Force Rod becomes a different tool depending on which single upgrade you give it:

| Infusion | Resulting rod |
|---|---|
| **Heat** | Heats up — light fires / cook food in a hand. |
| **Speed** | Rod of Speed — applies vanilla Speed potion effect, Mining Speed, and a short jump boost while held. |
| **Holding** | Lets you bottle entities into a **Force Flask** by right-clicking an entity within 16 blocks. Creepers are blacklisted by default; admins can lock the feature to passive mobs only. |
| **Healing** | Applies Regeneration to the holder. |
| **Ender** | Rod of Return — name + bind to a location, then later right-click to teleport back. Each return drains a chunk of durability. |

### Force Tome
- **Force** — using a Force Nugget transmutes a blank book into an Upgrade Tome (no tome needs to be present).
- **Experience** — using a Bottle o' Enchanting transmutes a book into an Experience Tome. Shift-right-click to deposit XP, right-click to withdraw, 10 XP per click.

### Upgrade Cores (single-type, stackable to max level)
Same upgrade can stack on a Core up to its max level. Once infused the Core is finalized — you can't re-infuse a different upgrade. Cores accept: Force · Damage · Heat · Speed · Lumberjack · Luck · Grinding · Holding · Touch · Wing · Sturdy. **Socket** the Core into a Power Tool or Force Armor to give that piece the upgrade.

### Force Armor (Camo / Charge / Charge2 in the Infuser; everything else via sockets)
Force Armor is **socketable**, and additionally accepts three upgrades directly in the Force Infuser:
- **Camo** — makes the armor render invisibly while worn (full set still gives its stats).
- **Charge** — turns the piece into an IC2 IElectricItem. Dormant if uncharged.
- **Charge2** *(0.1.11+)* — same idea, higher capacity.

Once you put **any** Infuser upgrade on a piece of armor, it can never enter the Infuser again — but it can still be socketed.

Sockets recognise these upgrades and produce specific effects on armor:

| Upgrade socketed | Effect |
|---|---|
| **Wing** (any piece) | +100 % Wing Meter per piece. Full set → 5× meter. Allows **Shift+Space+RightClick** flight from any held item; you can swap held items mid-flight without falling. Full Wing set flight ≈ 40 s. |
| **Sturdy** | Damage reduction from all sources. Max Sturdy on every armor piece ≈ 75 % damage reduction (rounded to half-hearts). |
| **Force** (3+ pieces) | "Minecraft Monk": you can punch un-tooled blocks bare-handed, +50 % tool efficiency, faster block-breaking, negates Wing's efficiency penalty, ambient *thwack* sound, slight knockback in your look direction. Cost is durability on the armor when you punch a block above your hand's normal tier. |

## Valid upgrades — at a glance

| Target | Accepted upgrades |
|---|---|
| Force Sword | Damage · Heat · Luck · Wing · Bane · Light |
| Force Pickaxe | Heat · Speed · Luck · Grinding · Touch · Sturdy |
| Force Shovel | Heat · Speed · Luck · Grinding · Touch · Sturdy |
| Force Axe | Heat · Speed · Lumberjack · Luck · Grinding · Touch · Sturdy |
| Force Bow | Damage · Heat · Swiftness · Luck · Ender |
| Force Shears | Heat · Rainbow · Luck · Grinding |
| Force Tome | Force · Experience |
| Force Rod | Heat · Speed · Holding · Healing · Ender (one type only) |
| Upgrade Core | Force · Damage · Heat · Speed · Lumberjack · Luck · Grinding · Holding · Touch · Wing · Sturdy (one type only, stacks to max) |
| Force Armor | Camo · Charge · Charge2 (plus sockets for everything else) |

## Wildcard / weird infusions

| Input | Result |
|---|---|
| **Stone Brick** + Force | Force Brick (0.1.11 wiki names this explicitly — used for cheap +1 tome points farming) |
| **White Wool** + Force | "Force I" wool (0.1.10 — `IForceWildCard` in code) |
| **IC2 Mining Drill** + Force Nugget | Power Drill (socketable) |
| **IC2 Diamond Drill** + Force Nugget | Power Drill, with the original diamonds flung out as drops |
| **IC2 Chainsaw** + Force Nugget | Power Saw (socketable) |

## Mod-integration caveats

### GregTech "integration" — *misguided*

DartCraft has a post-init block that calls `Class.forName("gregtechmod.GT_Mod")` and, if GregTech is loaded, runs `IMP.restore()` — bluedart's attempt to **revert GregTech's harder IC2 recipes** (e.g. the more-expensive Macerator). The author's intent was to keep the IC2 vanilla recipe set.

This is unnecessary and counter-productive on modern packs:

1. **GregTech ships its own config** (`GregTech_HardRecipes.cfg`, individual flags per machine) that lets you disable every "harder" recipe individually.
2. DartCraft's blanket reset wipes the lot whether you wanted it or not.
3. Ordering is fragile — it depends on the post-init ordering with GT.

**Fix in Upsilon / similar modpacks**: [UpsilonFixes](https://github.com/RewindMC/UpsilonFixes) ships a `DartCraftCoreTransformer` that uses NilLoader ASM to patch the `Class.forName` literal to a non-existent class so the IMP.restore() call never fires. The relevant config is `fixDartCraftForceDisablingGregTechTweaks`.

If you're hand-rolling a 1.4.7 pack with both DartCraft and GregTech installed and want GT's recipes to actually take effect, either ship UpsilonFixes or just don't ship GregTech alongside DartCraft.

### Other UpsilonFixes patches that touch DartCraft

- **`fixDartCraftMobSpawnRegistration`** — DartCraft registers `EntityEnderTot` to spawn in *every biome* including Mushroom Island, ignoring the standard exclusion. UpsilonFixes moves the spawn registration to `init()` (was post-init) and adds the Mushroom Island + Mushroom Island Shore exclusion.

### Other integrations (working as intended)

- **BuildCraft** — Force Gear ↔ Stone Gear (BC's recipe), BC Fuel/Oil are valid Force Engine fuels, Force Bricks/Logs are facade materials, Force Gears substitute for Gold Gears in Diamond Gears / Fillers / Quarries. Pre-1.5.1 also added the BC Triggers `Has Tome` / `Can Upgrade`.
- **Forestry** — Force Gems → 1.5 buckets Liquid Force in the Squeezer · Force Ingot recipes in the Carpenter using Liquid Force · Force Logs ferment into Biomass · Backpacks are excluded from Force Packs by default · Ethanol is a valid Force Engine fuel · Milk is a valid Throttle at +25 % vs water · "a very rare, obscure bee-related liquid" doubles throttle output (bluedart's own words — the Honey variants from bees) · Liquid Force / Milk fit Forestry Cans and Capsules · right-click a Cow with an empty Can fills it with milk.
- **IC2** — Drills/Chainsaw → Power versions · Macerator: Lead → Lead Dust · Macerator: oreTungsten → 2× Diamond (an EE2 throwback) · all colored wools macerated to string · Electronic Circuit recipe using Force Ingots · Energy Crystal recipe using `gemRuby` instead of diamond · Mixed Metal Ingots using Force Ingots yield 4 instead of 2 · UU-Matter → Force Gems · scrapbox can spit out Eggs, Forestry Cans/Capsules, Force Gems, Power Ore, Force Shards · Force Transmutations for bronze tools/armor.
- **Thermal Expansion** — Force Logs Sawmill recipe · Grinding upgrade uses TE machinations.
- **Thaumcraft** — DartCraft items get sensible aspects (Force Nugget = cheap Magic; Claws / Tears = rare aspects; Terra Wart = pure Purus); Thaumium tools/armor are Force-transmutable back to Thaumium ingots (offsets the Thaumium-Hoes-in-dungeons problem).
- **XYCraft** — Xychoridite (any color) is a Force Ingot recipe input.
- **Railcraft** — Steel tools/armor are Force-transmutable.

## Tips & gotchas (from the original post)

- **Vanilla enchant first → no Infuser**: Enchanting a Force Tool in a vanilla Enchanting Table is allowed but **locks it out of the Force Infuser forever**. Pick one path.
- **Each tool can only be Infuser-upgraded once** in 0.1.11. Pick your upgrades carefully; you can stack multiple compatible upgrades in the same Infuser run.
- **Duplicate same-type stacks don't multi-bonus the tome**: Heat 1 + Luck 4 in the same run gives Heat-base + Luck-base, not 4× Luck base.
- **The blue tier text in the GUI is the tome tier required**, not a description of the upgrade.
- **Bottling**: certain entities (Creeper by default) are blacklisted. Right-click an entity within 16 blocks with the Force Rod (Holding) + a Force Flask in inventory to capture; the bottle drops when you right-click in-air.
- **Sword's Wing is client-side** — bluedart specifically did this "to avoid death by lag".
