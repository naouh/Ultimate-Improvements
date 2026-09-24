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
| Left-click merge | hold **LMB** with a stack on the cursor, drag over matching slots | shift-moves them out (with sneak) or merges matching stacks onto the cursor when they fit |
| Left-click sweep | hold **LMB** empty-handed **+ sneak**, drag over slots | shift-moves (quick-moves) each swept slot out |

It acts at most **once per newly hovered slot**, so resting the button on one slot never spams it.
It also never touches the slot the button was **pressed** on: that press is vanilla's own click
(`GuiContainer.mouseClicked` handles it), so the mod only takes over from the next slot the cursor
enters while the button stays held. 1.0.0 re-clicked the pressed slot as well, which is why a
right-click drag used to put **two** items in the first slot.

## How it stays compatible with modded GUIs

Instead of re-deriving slot geometry, it reads the GUI's own `GuiContainer.theSlot` — the hovered
slot the game already computes each frame in `drawScreen`. That inherits every modded inventory's
real slot hit-testing, so there's no per-mod compatibility layer to maintain. `theSlot` is private,
so it's read by reflection, trying both the deobf name (`theSlot`) and the shipped obfuscated name
(`p`). The creative inventory is skipped (its slots are destructive).

- **NEI** replaces `GuiContainer` with its own patched copy; the hovered-slot field is still there
  under the same obfuscated name, so nothing special is needed.
- **Inventory Tweaks** has no drag feature of its own, so the two don't fight over clicks.
- Stacks are matched the way vanilla's `Container.slotClick` merges them — same id, same damage for
  sub-typed items **and same NBT** — so dragging over a same-looking stack with different tags (an
  MPS-stamped item, for instance) is skipped instead of letting vanilla swap the two stacks.

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
   `…zip.disabled` (so both don't run),
2. removes older `mousetweaksng-*.jar` builds from those folders, and
3. copies `mousetweaksng-1.0.1.jar` into the client and pack `mods/` folders.

It's a plain FML mod → `mods/`, not `coremods/`. Client-side only, but a `SidedProxy` keeps the
client classes off a dedicated server and `serverSideRequired = false` lets it connect to servers
without it.

## Changelog

- **1.0.1** — Don't re-click the slot the button was pressed on (fixes the first slot of a
  right-click drag receiving two items, and a left-click merge bouncing the stack straight back
  onto the cursor). Match stacks by NBT as well as id/damage. Only merge when the slot's own stack
  limit allows it.
- **1.0.0** — Initial rewrite.
