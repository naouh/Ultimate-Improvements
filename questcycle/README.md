# QuestCycle

![Quest book (Q key)](screenshot.png)

A minimal, config-driven quest mod for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)) with a prestige cycle
and titles.

## Usage

Press **Q** (rebindable, "QuestCycle Book") to open the book, or use `/quests`:

- **Prestige** — the quest chapters and their quests; each quest lists its tasks with a progress
  bar.
- **Achievements**, **Profile**, **Leaderboard** — one-off achievements, your prestige level and
  titles, and the server-wide ranking.

Quests are defined in `config/questcycle/`; progress is saved server-side under
`config/questcycle/persist/`. `/questcycle` is the admin command.

## Install

`mods/` on **both** sides.

## Build

`./gradlew build` — no third-party jars needed.
