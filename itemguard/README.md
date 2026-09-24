# ItemGuard

![Placement limit messages](screenshot.png)

A Bukkit plugin for **MCPC+ 1.4.7** that keeps a few items out of players' hands: an item
blacklist (no crafting, no placing) and per-block placement caps.

## Features

- **Blacklist by item ID** — blacklisted items cannot be crafted or placed. An `<id>@<world>` entry
  limits the ban to one world.
- **Placement limits** — cap how many blocks of a given type a player may have placed (the
  screenshot shows a cap of two for block 243), per permission group. Players are told how many
  they have used, and the counts live in `data.yml` so they survive restarts.
- `itemguard.bypass` (default op) skips every check.

## Commands

| Command | What it does |
|---|---|
| `/itemguard reload` (alias `/ig`) | Reload the config. |
| `/itemguard count [player]` | Show a player's placement counts. |

Permission: `itemguard.admin` (default op).

## Install

`plugins/` on the server. Nothing needed on clients.
