# QuarryPlus (1.4.7 backport)

1.4.7 backport of [QuarryPlus 1.7.10 (v2.1.1)](https://www.curseforge.com/minecraft/mc-mods/yog-quarry-plus) by yogpstop. **Heavily trimmed** — only the mining quarry and its support pieces are kept.

## Scope

**Kept:**

| Machine / Item | Why |
|---|---|
| **QuarryPlus** | The mining quarry (mining mode only — no Filler mode). |
| **MarkerPlus** | Long-range markers used to define the quarry's mining area. |
| **Frame** | The visible frame the quarry builds around its work area. |
| **WorkbenchPlus** | Workbench with NBT recipe memory. |
| **EnchantMover** | Transfers enchantments between items (so you can enchant the quarry without the original Laser system). |
| **ListEditor** | Item — opens a GUI to edit a machine's block-id whitelist (Fortune list, Silk Touch list). |
| **StatusChecker** | Item — right-click a machine to read its enchants/state. |

**Dropped:**

| Feature | Reason |
|---|---|
| MiningwellPlus | 1.4.7 BC already has Mining Well — redundant. |
| BreakerPlus | Translocator + filters cover this. |
| PlacerPlus | BC Builder covers this. |
| PumpPlus + LiquidSelector | Vanilla BC Pump is enough. |
| RefineryPlus | Vanilla BC Refinery is enough. |
| LaserPlus + BlockPlainPipe + Laser entity/renderers | Enchantment delivery moves to **EnchantMover** instead. |
| Filler Mode | The quarry stays a quarry; no structure-building mode. |
| 3 Mirrors (Overworld / Dimensional / Magic) | Niche teleport items, not in scope. |
| SpawnerController | Mob-spawner editor — not in scope. |
| ElectricArmorPlus | IC2 EU armor — outside the MJ-only target. |
| InfinityMJSource | Creative debug item. |

Power: **BuildCraft 3.x MJ** only. No RF (doesn't exist in 1.4.7), no EU.

## Status

**🛠️ Code-complete pending in-game test.** Phase 5/6 mostly landed; the missing pieces are
custom GUIs (Workbench/Mover use the vanilla chest UI as a placeholder), the actual auto-craft
loop inside `TileWorkbench` (recipe registry exists, the consumption tick is a stub), and
ForgeChunkManager wiring on the markers (they currently unload with their chunks).

- [x] **Phase 0** — skeleton (`build.gradle`, `settings.gradle`, dir tree, gradle wrapper)
- [x] **Phase 1** — decompile reference jar (CFR → `src-decomp/sources/`, 90 classes), final scope locked
- [x] **Phase 2** — foundation: `QuarryPlus` mod class, proxies, `Config`, packet handler
- [x] **Phase 3** — MarkerPlus: `BlockMarker`, `TileMarker` (full link-scanning + IAreaProvider), `RenderMarker` (immediate-mode wireframe), `APacketTile` parent class
- [x] **Phase 4** — QuarryPlus core: `PowerManager` (BC 3.x MJ port), `APowerTile`, `IEnchantableTile`, `BlockFrame`, `BlockQuarry`, `ItemBlockQuarry`, `TileQuarry` (full state machine: MAKEFRAME → NOTNEEDBREAK → MOVEHEAD → BREAKBLOCK, NBT enchant preservation, drops pushed into adjacent inventories), `EnchantmentHelper`
- [x] **Phase 5** — `BlockWorkbench` + `TileWorkbench` (27-slot inventory, vanilla chest GUI), `BlockMover` + `TileMover` (3-slot inventory + `tryMove(int enchantId)` enchantment-transfer API), `ItemTool` (StatusChecker meta 0 / ListEditor meta 1), `WorkbenchRecipe` registry with all the cross-machine recipes
- [x] **Phase 6** — lang file, IAreaProvider on `TileMarker` (so the quarry can read marker pairs), Block/Item recipes
- [ ] **Phase 6 carry-over** — custom GUIs (Workbench/Mover), `TileWorkbench.craft()` loop, ForgeChunkManager on `TileMarker`, real drill-animation `RenderQuarry`, dedicated textures

## Build dependencies (`libs/`)

Drop these jars into `libs/` before running `./gradlew build`:

| File | Source | Why |
|---|---|---|
| `QuarryPlus-1.7.10.jar` | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/yog-quarry-plus) | Reference for the port. Decompiled to `src-decomp/` (gitignored). |
| `buildcraft-A-3.7.x-pre.jar` | FTB Ultimate resources | Provides `buildcraft.api.power.*`, `buildcraft.api.transport.*`, `buildcraft.api.builders.*`, `buildcraft.core.IMachine`. |

The originals belong to their authors and aren't redistributed here. Pull them out of any 1.4.7 modpack that ships them (FTB Ultimate has them all).

## Building

```bash
./gradlew build
```

Output: `build/libs/quarryplus-0.1.0.jar`.

## Compatibility

Designed for **Ultimate Remastered** (and FTB Ultimate-class modpacks):

- **BuildCraft 3.7.x** for power, pipes, gates
- **DartCraft** Force Engine works as a power source (it exposes MJ)
- **IndustrialCraft 2** — use an EU→MJ converter (BC's own Energy Bridge or Forestry's Electrical Engine) if you want to run the quarry off IC2
- **Forestry / Thaumcraft / Railcraft** — no direct integration, the quarry just mines their ores
