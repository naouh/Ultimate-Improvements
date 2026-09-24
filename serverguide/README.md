# Server Guide

![Server Guide book](screenshot.png)

An in-game guide book for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (FTB Ultimate /
Ultimate Remastered era), built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom).

A clean multi-page GUI that shows your server's **rules**, **banned items**, and a
**beginner's tutorial** for the modpack. All content is plain editable text, so the
pack maintainer can rewrite it without recompiling.

## Opening it

- **Keybind** — default `G`, rebindable in Controls → `Server Guide`.
- **Command** — `/guide` (opens the book for the sender).
- `/guide reload` — op-only; re-creates any missing content files on disk.

Navigate with the tabs at the top, the mouse wheel / arrow keys / PageUp-PageDown to
scroll, and `←` / `→` to switch pages. `Esc` (or the guide key) closes it.

## Content — `config/serverguide/`

Three editable UTF-8 text files are created on first launch:

| File | Tab | Notes |
|---|---|---|
| `rules.txt` | Rules | Placeholder rules — edit to your server's. |
| `banned.txt` | Banned Items | Placeholder banned/restricted list — edit it. |
| `tutorial.txt` | Getting Started | A filled-in modpack tutorial (rubber → lava power → ore doubling → progression). Edit freely. |

### Markup

The renderer understands a tiny markup so the pages look tidy:

```
# Heading          → gold, bold
## Sub-heading     → yellow
- bullet line      → bulleted, hanging indent
(blank line)       → vertical gap
anything else      → paragraph, word-wrapped
```

Vanilla section-sign colour codes (`§a`, `§e`, …) work inside any line.

## Install

Drop `serverguide-0.1.0.jar` into the `mods/` folder of a 1.4.7 Forge profile.
Both client and server need the jar
(`@NetworkMod(clientSideRequired = true, serverSideRequired = true)`); the GUI and its
content files live on the **client**, the `/guide` command is registered on the server
and simply tells the client to open the book.

## How it works

- `@Mod` + `@NetworkMod` with one S→C packet (`open guide`).
- The keybind opens `GuideGui` directly client-side; `/guide` sends the open packet.
- `GuideContent` owns `config/serverguide/`, writes defaults if missing, and is re-read
  fresh every time the GUI opens — edit a file, reopen the book, see the change.
