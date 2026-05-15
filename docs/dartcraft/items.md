# Items

Every item DartCraft adds, with its default ID and what it actually does in-game. Default IDs are configurable in `dartcraft.cfg`.

## Resources

| Name | ID | Description |
|---|---:|---|
| **Force Gem** | 6000 | Drop from Power Ore. The base resource. 1 gem in the Infuser tank = 1000 mB Liquid Force. |
| **Force Ingot** | 6001 | Forged from a Force Gem + 2 iron (or via Forestry/IC2 ore-dict routes). Material for all Force tools and the Infuser. |
| **Force Nugget** | 6020 | 9 nuggets ↔ 1 ingot. Cheap **Force I** upgrade material (efficiency 0.75). |
| **Force Shard** | 6003 | Squeezer byproduct, dungeon loot. Fills 1000 mB *and* adds 10 bonus points to the tome when fed via the gem slot. |
| **Force Stick** | 6022 | 1 Force Plank → 8 sticks. Replaces vanilla sticks in Force tool recipes. |
| **Force Gear** | 6024 | Used in the Force Engine recipe (and any BC machine that takes a Gear, if BC isn't installed). |
| **Golden Power Source** | 6002 | Output of smelting a Force Plank (forceLog meta 0). Used in furnace-tier recipes (1 Golden Power + 1 stick → 6 torches). Also serves as the **Heat tier-1+** infusion material (0.5×→1.5× efficiency). |
| **Inert Core** | 6045 | A non-Forestry alternative to a Nether Star. Crafted from soul sand + tears + claws + diamond/sapphire. |
| **Crated Force Gems** | 6027 | Forestry crate of 9 gems. |

## Liquids and containers

| Name | ID | Notes |
|---|---:|---|
| **Liquid Force** | 6031 | The mod's fuel. Stored at 1000 mB per unit; tools/Infuser consume it. |
| **Force Bucket** | 6032 | Holds 1000 mB Liquid Force. Drops empty bucket when emptied. |
| **Force Container** | 6033 | Forestry can / wax capsule / refractory capsule variants holding Liquid Force (meta 0/1/2). |
| **Milk Container** | 6034 | Same three variants but for milk. Doubles as a furnace-fuel slot trick (3 milk containers → 1 cake recipe). |
| **Entity Bottle** | 6036 | Right-click a mob to bottle it (Holding upgrade). Right-click in-air to release. Creepers and Ghasts are blacklisted by default. |

## Food

| Name | ID | Notes |
|---|---:|---|
| **Raw Lambchop** | 6029 | Heals 3 hunger, low saturation. Drops from Cold Sheep variants in some configurations; also Forestry Hunter Backpack loot. |
| **Cooked Lambchop** | 6030 | Smelting result of raw. 7 hunger, 0.8 saturation. |
| **Fortune Cookie** | 6010 | Cookie + paper → Fortune Cookie. Eating it gives you a **Fortune** item. |
| **Fortune** | 6011 | Random fortune message. Crafts back into 1 paper. Tier-2 Luck upgrade material. |

## Tools (Force tier — durability 512, enchantability 50)

All Force tools share the same base durability (`Constants.toolDurability = 512`), enchantability (50), and a built-in 0.75× speed modifier on heads (`Constants.speedModifier`). They tier as **diamond** (harvest level 3) for the pick/drill and **iron**/equivalent for the axe/saw.

| Tool | ID | What it does |
|---|---:|---|
| **Force Pickaxe** | 6018 | Diamond-tier pickaxe, infusable. |
| **Force Shovel** | 6021 | Standard shovel, infusable. |
| **Force Axe** | 6023 | Diamond-tier axe, infusable. With **Lumberjack** infusion, chops whole trees. |
| **Force Sword** | 6014 | Standard sword, infusable. **Damage / Bane / Heat** apply. |
| **Force Shears** | 6026 | Infusable shears. |
| **Force Bow** | 6025 | Bow that consumes Force Arrows (or vanilla). Becomes **Heat Bow** with Heat infusion, **Ender Bow** with Ender infusion (teleports the player to the impact). |
| **Power Drill** | 6017 | IC2 EU-powered pickaxe. Only registers if IC2 is loaded. Recognised as an upgrade for vanilla IC2 mining/diamond drill. |
| **Power Saw** | 6016 | IC2 EU-powered axe. Replaces IC2 chainsaw. |
| **Heat Bow** | 6037 | Variant of Force Bow created automatically when you infuse Heat onto a Force Bow. |
| **Ender Bow** | 6040 | Same, with Ender infusion. |

## Armor (Force set)

A complete 4-piece set with infusion support. Default damage reduction is comparable to diamond. Specific infusion effects on armor (Swiftness boots, Wing chestplate, Healing helmet, Sturdy plate, Ender) are gated by the Tome tier.

| Piece | ID |
|---|---:|
| **Force Cap** | 6005 |
| **Force Tunic** | 6006 |
| **Force Pants** | 6007 |
| **Force Boots** | 6008 |

The set has no built-in special; **all behaviour is from infusions**. Common loadouts:

- **Boots + Swiftness** → faster walk.
- **Boots + Sturdy** → fall-damage reduction.
- **Tunic + Wing** → creative-style flight (consumes Liquid Force).
- **Boots + Ender** → teleport pulse.
- **Cap + Healing** → passive regeneration ticks.

## Storage

| Item | ID | What |
|---|---:|---|
| **Force Pack** | 6012 | Backpack. Starts at 8 slots (config), upgradable to 40 (5 × Holding upgrades). Right-click to open. Can be renamed with a Force Rod. |
| **Ender Pack** | 6028 | Force Pack variant tied to your Ender storage (player-bound, like an ender chest). Mutually exclusive with normal Holding upgrades. |
| **Clipboard** | 6004 | A 9-slot crafting clipboard you can carry — opens a portable workbench-style UI. |

## Books and progression

| Item | ID | What |
|---|---:|---|
| **Force Tome** | 6013 | Holds infusion progress. Three types: **Upgrade Tome** (type 0, default), **Craft Tome** (type 1), **Experience Tome** (type 2). |
| **Force Rod** | 6015 | Multi-tool: applies socket actions to a held Force tool, renames Force Packs, used to read fortune cookies, etc. Crafted in two tiers (vanilla stick = 48 durability, Force Stick = full durability). |
| **Upgrade Core** | 6019 | Single-upgrade module. Infuse one upgrade into it (max 1 type), and you can right-click it onto a compatible Force tool to grant that upgrade. |

### Tome types

- **Upgrade Tome** — tracks Force Points. Required in the Infuser to climb tiers. Tier display turns blue. Press **SHIFT** to see points/next-tier.
- **Experience Tome** — stores XP. Right-click to deposit/withdraw 10 XP at a time (sneak to deposit). One pre-stored at 1337 XP can be found in dungeon chests.
- **Craft Tome** — internal type used for tool-craft recipes.

### Force Rod actions

The Force Rod is the right-click action delivery system. Behavior depends on what you point at:

- Right-click an **Item Frame** holding a Force Pack to rename it.
- Right-click a **Force Tome** to view points (also via SHIFT-hover).
- Right-click a **Fortune** item to read it.
- Right-click an entity to apply tool actions if the held Force tool has matching infusion (e.g. Punish, Tear, etc.).

## Misc / drops

| Item | ID | Source |
|---|---:|---|
| **Claw** | 6044 | Drop from hostile mobs (zombies, skeletons, spiders). Used in **Damage** infusions and in the Inert Core recipe. |
| **Tear** | 6043 | Drop from passive mobs / dungeons / Force Flax. Used in **Healing** infusions and the Inert Core recipe. |
| **Force Arrow** | 6009 | Arrow with bonus damage that doesn't drop on impact. 1 Nugget + 1 Stick + 1 Feather → 6 arrows. |

## Force Transmutations

Right-click a vanilla item with a Force Rod (in the Infuser?) to transmute. The mod registers:

| Input | Output | Stack | Consumed |
|---|---|---:|:---:|
| Book (`up.aL`) | Force Tome | 1 | ✗ |
| Iron Helmet | Force Cap | 1 | ✓ |
| Iron Chest | Force Tunic | 1 | ✓ |
| Iron Pants | Force Pants | 1 | ✓ |
| Iron Boots | Force Boots | 1 | ✓ |
| Fortune Cookie | Fortune | 16 | ✗ |

With **Forestry**: bronze tools/armor → 2–8 bronze ingots (sword=2, pick=3, shovel=1, axe=3, hoe=2, helm=5, chest=8, legs=7, boots=4).

With **Railcraft**: same conversions for steel tools/armor.
