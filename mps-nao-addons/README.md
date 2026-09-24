# MPS Nao Addons

![Tinker table with the added power tool modules](screenshot.png)

A coremod for **Modular Powersuits** on **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)). It bundles the
Flight Control ground-feel fix from [`mpsflightfix`](../mpsflightfix/) and adds new modules — run
only one of the two.

## Modules

| Module | Slot | What it does |
|---|---|---|
| **Air Stride** | helmet | Mine at full ground speed while airborne. |
| **OmniWrench** | power tool | Rotate / harvest blocks. `ItemPowerTool` is bytecode-patched to implement BuildCraft `IToolWrench`, MFR `IToolHammer`, Railcraft `IToolCrowbar` and UE `IToolConfigurator`, so those mods treat the tool as a wrench while this mode is active. |
| **EU Reader** | power tool | Measure IC2 EU flow at energy tiles. |
| **TE Multimeter** | power tool | Delegates to Thermal Expansion's multimeter for conduit / tile readings. |
| **ME Wireless Terminal** | power tool | Channels AE's wireless access terminal through the power tool: link the tool in an ME Controller's wireless slot, then right-click anywhere in range to open that network. Keeps the original recipe cost. |

It also fixes MPS stamping an empty `mmmpsmod` NBT tag on every item that passed through a
player's inventory, which stopped ordinary items (furnace output, drops…) from stacking: only
`IModularItem`s get the tag now, and old stamps are stripped.

## Install

Coremod: `coremods/` on **both** sides. All MPS API calls are reflective, so it follows whatever
MPS build the pack ships.

## Build

`./gradlew build` — compiles against `libs/ModularPowersuits.jar` (see the
[root README](../README.md#setup-third-party-jars)) and copies the jar into the client instance and
the pack's `coremods/`, replacing any older build.

## Changelog

- **1.0.1** — Inventory de-stamp sweep now runs for every player (a shared tick counter only ever
  reached the same player when the player count divided 20). Wrench energy is only drained
  server-side (BuildCraft / Railcraft call the interface on both sides). EU Reader measures with the
  total world time instead of the day clock. Dropped the dead `onItemRightClick` injection (MPS
  already overrides it; air clicks go through Forge's `RIGHT_CLICK_AIR`) and its per-click log
  spam.
- **1.0.0** — Initial release.
