# Discord Rich Presence

![Discord activity while playing](screenshot.png)

Client-side mod for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)) that shows what you
are playing in your Discord activity: the pack name and icon, the elapsed time and, once connected
to a server, its player count.

## How it works

The mod talks to the running Discord client through its local IPC socket (`discord-ipc-N`), the
same channel the official Rich Presence SDK uses, so there is no extra library and no bot involved.
The activity is refreshed while you play.

## Install

Client-only: drop the jar in `mods/`. A server without it is not affected. Discord has to be running
on the same machine for the presence to show up.

## Build

`./gradlew build` — no third-party jars needed.
