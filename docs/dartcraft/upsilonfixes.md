# UpsilonFixes patches for DartCraft

[UpsilonFixes](https://github.com/RewindMC/UpsilonFixes) is a NilLoader coremod that patches DartCraft (and many other 1.4.7 mods) at runtime. It exposes per-fix toggles in its config; this page lists every DartCraft-related patch.

All fixes are **off by default** in UpsilonFixes — enable them per-pack in `config/upsilonfixes.cfg`.

## At a glance

| Config flag | What it fixes |
|---|---|
| `fixDartCraftForceEngineLimit` | Force Engine's hard-coded 250 MJ/cycle cap (it can produce 400 MJ/cycle). |
| `fixDartCraftForceInfuserDimension` | Force Infuser "Go" button forcing the player's request through dimension 0. |
| `fixDartCraftForceDisablingGregTechTweaks` | DartCraft silently reverting GregTech's harder IC2 recipes at post-init. |
| `fixDartCraftMobSpawnRegistration` | Ender Tot spawning in Mushroom Island biomes; reordered spawn-registration call site. |

## `fixDartCraftForceEngineLimit`

**Class touched**: `bluedart.tile.TileEntityForceEngine`, method `transferEnergy()`

The Force Engine internally generates up to **16 MJ/t (400 MJ per 25-tick cycle)** with its high-tier fuel + throttle combinations (Force Liquid + Ice, Fuel + Honey, etc.). But the actual MJ transfer to receivers is hard-capped by a `LDC 250.0f` constant inside `transferEnergy()`. Any production above 10 MJ/t is silently discarded — you pay the fuel cost but the receiver only sees 10 MJ/t.

The patch finds the `LDC 250.0f`, replaces it with `LDC 400.0f`, restoring the engine to its intended cap. bluedart fixed this properly in the 1.6 version of DartCraft by making the per-cycle cap dynamic (computed from the current generation rate); for the 1.4.7 / 1.5.1 build the bytecode swap is enough.

**Symptom without the fix**: your Force Engine "looks" fine in its GUI but downstream machines (Quarry, Pump, etc.) run at ~60% of expected throughput.

## `fixDartCraftForceInfuserDimension`

**Class touched**: `bluedart.core.network.PacketHandler`, method `openTileGui(PacketDimCoords, EntityPlayer)`

Misleadingly-named method — it actually fires when the player clicks the **Go** button in the Force Infuser GUI. The original code calls `MinecraftServer.worldServerForDimension(0)` to look up the world the Infuser sits in. Hard-coded `0` means: it always asks for the Overworld, never the dimension the player is actually playing in.

End result: an Infuser placed in the Nether, the End or any modded dimension **does nothing when you press Go**. No error, no log line — clicks are silently dropped.

The patch swaps the `worldServerForDimension(0)` call for `player.worldObj` (cast to WorldServer), so the Infuser uses the player's actual world. Now it works in every dimension.

## `fixDartCraftForceDisablingGregTechTweaks`

**Class touched**: `bluedart.core.DartCraftCore`, method `postInit()`

DartCraft's post-init contains this block (decompiled):

```java
try {
    Class.forName("gregtechmod.GT_Mod");
    DartCraft.dartLog.info("Applying postliminary IC2 molestation consolement.");
    IMP.restore();
} catch (Exception e) {}
```

The intent is to **revert GregTech's modifications to IC2's recipes** (harder Macerator, Compressor, etc.) so that DartCraft players see the friendlier vanilla IC2 progression. The author considered GT's tweaks "IC2 molestation".

Why this is misguided:

1. **GregTech ships its own config.** `GregTech_HardRecipes.cfg` exposes a per-recipe flag for every "hard" tweak. The modpack author and player have fine-grained control. DartCraft's all-or-nothing reset takes that control away.
2. **Load-order fragility.** If GT applies tweaks AFTER DartCraft's IMP.restore(), the fix doesn't apply. If GT runs before, it does. Behaviour shifts based on `mods/` directory alphabetical order.
3. **Pack-author intent loss.** A pack that ships DartCraft alongside GregTech specifically TO get GT's progression curve is silently overridden.

The patch swaps the literal string `"gregtechmod.GT_Mod"` for `"thisis.aclass.that.will.not.exist.AndIfItDoes$ThenWellCongratulations"` — guaranteed to throw `ClassNotFoundException`, causing the IMP.restore() branch to fall into the empty catch and do nothing. GregTech's recipe tweaks stay applied.

**Symptom without the fix**: in a pack with both mods, GregTech's `HardRecipes` config does nothing. Macerator, Compressor etc. all use the friendlier IC2 defaults regardless of how the pack author configured GT.

## `fixDartCraftMobSpawnRegistration`

**Class touched**: `bluedart.core.DartCraftCore`, methods `init()`, `postInit()`, `loadMonsterSpawns()`

Two issues bundled in one flag:

### Issue 1: Spawn registration in the wrong lifecycle stage

DartCraft originally registered Ender Tots in `postInit()`. By that point Forestry has already finished its biome-population analysis, so the spawn entry sometimes wouldn't propagate to all biomes that came online late.

The patch:
1. Erases the `loadMonsterSpawns()` call from `postInit()`.
2. Inserts the call into `init()` right after `loadEntities()` — earlier in the lifecycle, before Forestry's biome scan.

### Issue 2: Mushroom Island spawns

The original `loadMonsterSpawns()` iterates **every biome** in `BiomeGenBase.biomeList`, only skipping Hell. Mushroom Island and Mushroom Island Shore (which forbid hostile spawns by convention) get hostile Ender Tots anyway.

The patch wraps the `EntityRegistry.addSpawn` call in an `if-not-equals` check that skips both Mushroom biomes.

**Symptom without the fix**: Ender Tots spawning on Mushroom Islands — a vanilla-violating behaviour that breaks the "safe biome" convention.

## How to apply

1. Drop UpsilonFixes' jar into `mods/` (it's a NilLoader coremod — make sure NilLoader is also present).
2. Run the game once to generate `config/upsilonfixes.cfg`.
3. Set the flags you want to `true`:

   ```
   fixDartCraftForceEngineLimit=true
   fixDartCraftForceInfuserDimension=true
   fixDartCraftForceDisablingGregTechTweaks=true
   fixDartCraftMobSpawnRegistration=true
   ```

4. Restart.

## Related

- [Integrations → GregTech](integrations.md#gregtech) — context for the IMP.restore() patch.
- [Blocks → Force Infuser](blocks.md#force-infuser) — the dimension bug.
- [Blocks → Force Engine](blocks.md#force-engine) — the 10/16 MJ cap.
