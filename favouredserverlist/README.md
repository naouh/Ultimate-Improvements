# Favoured Server List

![Custom multiplayer entry with icon and multi-line MOTD](screenshot.png)

Client-side mod for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)) that replaces the
vanilla multiplayer screen with a custom list for **The Favoured Craft**: the server comes
pre-listed with a bundled icon and a multi-line, colour-coded MOTD.

## How it works

- A client tick handler swaps the vanilla multiplayer screen for the mod's own list the moment it
  opens, so no vanilla class is patched.
- The server ping keeps the `\n` in the MOTD (vanilla 1.4.7 collapses it), which is what allows the
  two-line description.
- The icon ships inside the jar (`favouredcraft/`).

## Install

Client-only: `mods/`. Nothing to install on the server.

## Build

`./gradlew build` — no third-party jars needed.
