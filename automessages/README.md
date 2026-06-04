# AutoMessages

Periodic chat announcements for **MCPC+ 1.4.7** (Bukkit plugin, zero runtime dependencies). A timer
cycles through a list of messages and shows the next one to every online player — and each player can
silence them for themselves with a command that is **saved** across reconnects and restarts.

## Behaviour

- Posts the next configured message every `interval-minutes` (decimals allowed; floor 5 seconds).
- `order: sequential` walks the list and loops; `order: random` picks one each time (never the same
  twice in a row).
- Colour codes via `&`; an entry may contain `\n` for multiple lines. A configurable `prefix` is
  prepended to every message.
- Skips players who opted out. If nobody is online, it sends nothing and doesn't advance the rotation.

## Per-player opt-out (persisted)

Each player controls their own view:

- `/automsg` — toggle on/off
- `/automsg off` — hide announcements
- `/automsg on` — show them again

The choice is stored in `plugins/AutoMessages/data.yml` under `opted-out`, keyed by lowercase player
name (the server runs offline/cracked), so it sticks after relog and restart.

## Commands

`/automessages` (aliases `/automsg`, `/am`):

| Command | Permission | Action |
|---|---|---|
| `/automsg [toggle]` | `automessages.toggle` (default: **all**) | Toggle your own messages. |
| `/automsg on` / `off` | `automessages.toggle` | Explicitly enable/disable for yourself. |
| `/automsg reload` | `automessages.admin` (default: op) | Reload `config.yml` + reschedule. |
| `/automsg status` | `automessages.admin` | Show count, interval, order, opt-out count. |
| `/automsg list` | `automessages.admin` | Preview all messages (rendered with colours). |
| `/automsg next` | `automessages.admin` | Send the next message now (for testing). |

## Configuration (`plugins/AutoMessages/config.yml`)

```yaml
interval-minutes: 5.0          # minutes between messages (decimals ok)
order: sequential              # sequential | random
prefix: "&8[&bInfo&8]&r "      # prepended to every message ("" for none)
messages:
  - "&7First announcement..."
  - "&7Second announcement..."
```

Edit the list, then `/automsg reload`.

## Build

```
./gradlew build
```

Compiles against the server's `mcpc-plus.jar` (Java 8 bytecode via a JDK 11 toolchain) and copies the
jar into the server's `plugins/` folder. Adjust `ext.serverDir` in `build.gradle` if your path differs.
