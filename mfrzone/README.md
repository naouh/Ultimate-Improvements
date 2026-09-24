# MFR Zone Preview

![Planter working area shown as a BuildCraft laser box](screenshot.png)

Shows the working area of a MineFactory Reloaded **Planter**, **Harvester** or **Fertilizer** on
**MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)). Sneak + left-click
the machine with an empty hand and its current radius — radius upgrade included — lights up around
it as a BuildCraft laser box for a few seconds. No GUI, no behaviour change: the click is cancelled
so the machine is not damaged.

## How it works

The radius upgrade lives in the machine's inventory, which only the server knows, and Forge 1.4.7
only fires the left-click interaction event server-side. So the server reads the machine's
`HarvestAreaManager` (reflectively, no MFR compile dependency), cancels the dig and sends the box
to that player; the client draws it with BuildCraft's laser entities (also reflective) and removes
it after `showSeconds`.

## Config (`config/MFRZone.cfg`)

| Key | Default | Meaning |
|---|---|---|
| `maxDistance` | 8 | Max distance from the machine for the preview to trigger. |
| `maxRadius` | 24 | Safety cap on the previewed radius. |
| `showSeconds` | 6 | How long the box stays. |
| `laserColor` | Red | BuildCraft laser kind: Red, Blue or Stripes. |

## Install

`mods/` on **both** sides. Needs MineFactory Reloaded; BuildCraft must be on the client for the
lasers.

## Build

`./gradlew build` — no third-party jars needed.
