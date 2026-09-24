# CageControl

![CageControl menu (K key)](screenshot.png)

A Soul Shards add-on for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (FTB Ultimate / Ultimate
Remastered era), built with [Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom).

Soul Cages no longer spawn on their own. Every cage gets an owner and a name, and only its owner,
a co-owner or an admin can switch it on and off.

## How it works

1. Place a Soul Cage and right-click it with its Soul Shard. You are asked for a name and become
   the cage's owner.
2. Open the menu with **K** (rebindable, "CageControl Menu"). The *Mine* tab lists your cages with
   their mob, tier, dimension and position; *All cages* is the admin view. Select a cage to
   **Start** / **Stop** it, rename it, or add and remove co-owners.
3. The same actions exist as commands:

| Command | What it does |
|---|---|
| `/shard <name> start` / `stop` | Activate or deactivate the cage. |
| `/shard <name> owner add <player>` / `remove <player>` / `list` | Manage co-owners. |
| `/cagecontrol` | Admin / maintenance command. |

## Install

Plain FML mod: drop the jar in `mods/` on **both** the server and the clients (the server owns the
cage state, the client draws the menu). Requires Soul Shards 1.26.

## Build

`./gradlew build` — needs `libs/SoulShards.jar` (see the
[root README](../README.md#setup-third-party-jars)).
