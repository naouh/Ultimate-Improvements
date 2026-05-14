# Nao's Minecraft 1.4.7 Mods

Five mods targeting MC 1.4.7 / Forge `1.4.7-6.6.2.534` (FTB Ultimate / Ultimate Remastered era), built with [Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom).

| Folder | What it does |
|---|---|
| [`cagecontrol/`](cagecontrol/) | Disables SoulShards 1.26 cage auto-activation. Right-click a placed cage with its shard to register an owner, then `/shard <name> start \| stop` to control activation. Co-owners via `/shard <name> owner add/remove/list`. |
| [`mpsflightfix/`](mpsflightfix/) | Original standalone version of the Flight Control ground-feel fix (NilLoader nilmod). **Superseded by `mps-nao-addons` — only run one of the two.** Kept here for reference / for users who don't want the extra modules. |
| [`mps-nao-addons/`](mps-nao-addons/) | MPS coremod that bundles the Flight Control ground fix from `mpsflightfix` and adds four modules: **Air Stride** (helmet — mine at full speed while airborne), **OmniWrench** / **EU Reader** / **TE Multimeter** (power tool tool-modes). Bytecode-patches `ItemPowerTool` to implement BuildCraft `IToolWrench`, MFR `IToolHammer`, Railcraft `IToolCrowbar` and UE `IToolConfigurator` so other mods recognise the power tool as a wrench when the OmniWrench mode is active. |
| [`neiae/`](neiae/) | Wires NEI's `?` recipe-overlay button to AE's ME Crafting Terminal. Shift-click the `?` in NEI while a crafting terminal is open to extract the recipe's ingredients from the ME network into the 3×3 matrix. |
| [`translocator/`](translocator/) | 1.4.7 backport of ChickenBones' Translocator 1.1.0.2 — item translocator, liquid translocator and crafting grid. Uses CCC 0.8.1.6's `RayTracer`/`Vector3`/`PacketCustom` directly; Voldeloom's `modCompileOnly` remaps CCC's MC obf references at build time. |

## Building

Each mod is its own Gradle project. From any mod folder:

```bash
./gradlew build
```

The remapped jar lands in `<mod>/build/libs/<mod>-<version>.jar`.

## Setup: third-party jars

`libs/*.jar` is git-ignored — every mod compiles against closed-source jars I don't own, so they aren't included here. Before you can build a given mod, drop the matching jar(s) into its `libs/` directory:

| Mod | `libs/` needs |
|---|---|
| `cagecontrol` | `SoulShards.jar` |
| `mpsflightfix` | `ModularPowersuits.jar` (the MPS 0.3.2-199 era jar) |
| `mps-nao-addons` | *(empty — uses reflection for all MPS API calls)* |
| `neiae` | `AppEng.jar`, `NEI.jar` |
| `translocator` | `CodeChickenCore-0.8.1.6.jar` |

The jars are typically extractable from any 1.4.7 modpack that includes the upstream mod (FTB Ultimate has all of them).

## Why Voldeloom

These mods need to compile against MC 1.4.7's obfuscated classes (`yc` = World, `qx` = EntityPlayer, etc.) but be written in human-readable MCP names. Voldeloom's MCP-mappings + tiny-remapper pipeline handles the round-trip:

* source → MCP names → SRG (intermediate) → obf (runtime jar)
* `modCompileOnly` deps (CodeChickenCore for the translocator port) get the same remap so their bytecode-internal references to MC obf types resolve cleanly against our compile classpath.

It's the only sane way to write fresh 1.4.7 code in 2024+; the original MCP/Ant toolchain is dead in too many places to be usable.
