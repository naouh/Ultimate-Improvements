# VoiceChat

![Talking player with the mic indicator](screenshot.png)

In-game proximity voice chat for **MC 1.4.7 / Forge `1.4.7-6.6.2.534`** (built with
[Voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom)). Hold **V** to talk;
players nearby hear you, quieter the further away they are, and a microphone icon shows above
whoever is speaking.

## How it works

- Audio is captured at 8 kHz, μ-law mono, in 20 ms frames, and played back per talker with
  distance attenuation.
- Every frame rides the normal Minecraft connection as a custom-payload packet on channel `VC`,
  relayed by the server to the players in range. There is **no extra UDP port** to open, which is
  also what makes it work behind TCP-only edges such as OVH's "Protégé" firewall. (The first
  version used a UDP side-channel on 25566; that transport is gone, so client and server must run
  the same version.)
- **V** is push-to-talk and **B** opens the voice menu (both rebindable).

## Install

`mods/` on **both** sides.

## Build

`./gradlew build` — no third-party jars needed.
