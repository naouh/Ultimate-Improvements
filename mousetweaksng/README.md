# Mouse Tweaks NG

A clean, from-scratch reimplementation of the classic **Mouse Tweaks** mod for
**Minecraft 1.4.7 / Forge `1.4.7-6.6.2.534`** (Ultimate Remastered era), built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom). It replaces the old
obfuscated `_1.4.7__Mouse_Tweaks_BETA_4.5.zip`.

## Why rewrite it

The old jar drove its clicks from a ModLoader in-GUI hook that fires **every render frame**. While
you held a tweak (e.g. dragging items out, or right-click distributing), it re-issued window-clicks
far faster than 1.4.7's window-click protocol — which confirms one click at a time via
`Packet102WindowClick` → `Packet106Transaction` — could keep up. Under that flood, the client's view
of the slots drifts from the server's: **grabbed or crafted items don't appear until you click
again**. TickThreading's packet jitter makes it worse. No items are ever lost — it's a pure display
desync — but it's annoying.

This rewrite keeps the same three tweaks but **paces its own automated clicks** so each transaction
confirms before the next is sent, killing the desync.

## The three tweaks

| Tweak | Trigger | What it does |
|---|---|---|
| Right-click distribute | hold **RMB** with a stack on the cursor, drag over slots | drops one item into each empty / same-item slot |
| Left-click merge | hold **LMB** with a stack on the cursor, drag over matching slots | shift-moves them out (with sneak) or pulls+returns to merge stacks that fit |
| Left-click sweep | hold **LMB** empty-handed **+ sneak**, drag over slots | shift-moves (quick-moves) each swept slot out |

It acts at most **once per newly hovered slot**, so resting the button on one slot never spams it.

## How it stays compatible with modded GUIs

Instead of re-deriving slot geometry, it reads the GUI's own `GuiContainer.theSlot` — the hovered
slot the game already computes each frame in `drawScreen`. That inherits every modded inventory's
real slot hit-testing, so there's no per-mod compatibility layer to maintain. `theSlot` is private,
so it's read by reflection, trying both the deobf name (`theSlot`) and the shipped obfuscated name
(`p`). The creative inventory is skipped (its slots are destructive).

## Config

`config/MouseTweaksNG.cfg`:

- `rmbTweak`, `lmbTweakWithItem`, `lmbTweakWithoutItem` — toggle each tweak (default on).
- `minClickGapMs` — minimum ms between automated clicks (default `50`). This is the anti-desync
  pacing. `0` disables it; raise toward `100`–`150` if you still see items not showing until you
  click (e.g. on a heavily TickThreaded / high-latency server).

Note: `minClickGapMs` only paces **this mod's** automated clicks. If you still get desync while
clicking **by hand** very fast (e.g. rapid manual shift-click crafting), that's the vanilla 1.4.7 /
TickThreading transaction race and needs a server-side fix, not this mod.

## Build & deploy

```
./gradlew build
```

Voldeloom compiles against MCP names and remaps to obfuscated. `build` then:

1. renames any `_1.4.7__Mouse_Tweaks_BETA_4.5.zip` in the target `mods/` folders aside to
   `…zip.disabled` (so both don't run), and
2. copies `mousetweaksng-1.0.0.jar` into the client and pack `mods/` folders.

It's a plain FML mod → `mods/`, not `coremods/`. Client-side only, but a `SidedProxy` keeps the
client classes off a dedicated server and `serverSideRequired = false` lets it connect to servers
without it.
