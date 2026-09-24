# ClearLag (nao's rewrite)

![ClearLag sweep message](screenshot.png)

A clean entity/lag cleaner for **MCPC+ 1.4.7** (Bukkit plugin, zero runtime dependencies), built to
replace bob7l's ClearLag. Same idea — clear junk entities on a timer — but with the three things the
original is missing on a modded survival server:

1. **Ground items near players are kept.** A dropped item (or XP orb) within a configurable radius of
   any player is never removed, so nobody loses a fresh drop while picking it up.
2. **Animals are never touched.** Passive mobs, tamed pets, villagers, golems and bosses are
   protected — player farms and breeders survive.
3. **Modded mobs are handled.** Hostility is detected through Minecraft's `IMob` interface at runtime,
   so modded monsters (Twilight Forest, etc.) get cleared too — not just a hard-coded vanilla list.

**Safety policy:** only entities positively identified as hostile or as removable junk are deleted.
Anything the plugin can't classify is left alone, so a clear can never wipe something it shouldn't.

## How modded classification works

`mcpc-plus.jar` ships only the Bukkit API (the `net.minecraft.*` classes are deobfuscated by FML at
runtime, not bundled), so the plugin resolves `net.minecraft.entity.monster.IMob`,
`...passive.IAnimals` and `...boss.IBossDisplayData` reflectively and tests each entity's handle
against them. If those classes can't be loaded, it falls back to Bukkit's `Monster`/`Animals`
interfaces (vanilla-only) — it just skips unknown mobs, never deletes the wrong thing. Nothing
`net.minecraft.*` is referenced at compile time.

## Commands

`/clearlag` (aliases `/lagg`, `/cl`):

| Command | Permission | Action |
|---|---|---|
| `/clearlag clear` | `clearlag.clear` | Remove entities now (manual). |
| `/clearlag check` | `clearlag.check` | TPS (current + 1-min avg) and per-world entity/chunk counts + top entity types. |
| `/clearlag unloadchunks [world\|all]` | `clearlag.unload` | Politely unload chunks with no nearby players (won't fight chunkloaders). |
| `/clearlag reload` | `clearlag.admin` | Reload `config.yml`. |

All default to op.

## Configuration (`plugins/ClearLag/config.yml`)

- `auto.interval-minutes` (default `20`), `auto.warn-seconds` (default `30`), `auto.broadcast`,
  `auto.worlds` (empty = all).
- `protect.item-radius` (default `8`) — items/XP within this many blocks of a player are kept.
- `protect.animals / tamed / villagers / golems / bosses / ridden` — all `true` by default.
- `protect.entity-types` — extra type/class names to always spare (for a specific modded mob).
- `remove.monsters / items / xp-orbs / arrows / falling-blocks / boats / minecarts / projectiles` —
  what's eligible for removal (`xp-orbs`, `boats`, `minecarts` default `false`).
- `unloadchunks.keep-radius-chunks` (default `4`) — radius kept loaded around players/spawn.

Edit, then `/clearlag reload`.

## TPS meter

A tiny sync task runs ~once per second and compares real elapsed time to the expected time to compute
TPS (20 = perfect), keeping a 1-minute rolling average. Shown by `/clearlag check`.

## Replacing bob7l's ClearLag

This plugin uses the same name (`ClearLag`), so the old one must be removed/disabled or Bukkit will
reject a duplicate plugin name. In this repo's server it was disabled by renaming
`plugins/ClearLag.jar` → `ClearLag.jar.disabled` and moving the old config to
`plugins/ClearLag-bob7l.bak/`. Delete those once you're happy with the rewrite.

## Build

```
./gradlew build
```

Compiles against the server's `mcpc-plus.jar` (Java 8 bytecode via a JDK 11 toolchain) and copies the
jar into the server's `plugins/` folder. Adjust `ext.serverDir` in `build.gradle` if your path differs.
