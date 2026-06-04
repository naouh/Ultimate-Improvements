# WorldBackup

Low-lag, TickThreading-safe scheduled world backups for **MCPC+ 1.4.7** (Bukkit plugin, zero runtime
dependencies). Zips every world folder into one `.zip` per run, written entirely on a background
thread so it never lags the server.

## How it stays lag-free (and TickThreading-safe)

A naive "copy the world folder" backup lags servers two ways: it does the copy on the main thread
(stalling ticks), and it reads region files while the server is still writing them (corruption). This
plugin avoids both:

1. **On the server (main) thread**, once per run: flush all loaded worlds and turn **autosave off**.
   That costs exactly one normal autosave tick — paid a single time, not continuously.
2. **On a dedicated background thread**: walk every world folder and stream it into the zip. All the
   heavy disk I/O happens here, off the tick loop.
3. **Back on the main thread** when the zip finishes: turn **autosave on** again.

Because saving is frozen for the duration of the copy, region files don't change while they're read,
so the archive is internally consistent. TickThreading ticks worlds on worker threads but still
respects the per-world "saving disabled" flag (the same one `setAutoSave(false)` sets), so this is
safe under TT. If a player walks into a dimension mid-backup, that world's saving is frozen too (via
`WorldLoadEvent`) until the backup ends.

## Forge / MCPC+ world layout

Your dimensions are separate top-level folders — `world`, `world_nether`, `world_the_end`,
`world_twilightforest`, `world_myst`. Whole folders are archived **recursively**, so the Mystcraft
ages stored inside `world_myst` (`world_myst/age2`, …) are included automatically.

Auto-detection (the default) also **scans for world folders that aren't currently loaded** — a
Twilight Forest nobody is in — by looking for a `level.dat`/`region` directory (checked one level
deep so the `world_myst` container is recognised via its ages). So a sleeping dimension still gets
backed up.

## Commands

`/backup` (aliases `/worldbackup`, `/wbackup`), permission `worldbackup.admin` (default: op):

| Command | Action |
|---|---|
| `/backup now` | Run a backup immediately (also the default if you just type `/backup`). |
| `/backup status` | Show running state, last result, schedule, output dir. |
| `/backup list` | List existing archives (newest first) with sizes. |
| `/backup reload` | Reload `config.yml` and reschedule the auto-backup. |

## Configuration (`plugins/WorldBackup/config.yml`)

Key options (full docs in the file):

- `backup.interval-minutes` — minutes between automatic backups (`0` = manual only). Default `120`.
- `backup.output-dir` — where archives go (default `backups/` under the server folder; point it at
  another drive if you can).
- `backup.keep` — how many archives to retain; the oldest are pruned (`0` = keep all). Default `10`.
- `backup.compression-level` — `0` = stored (fastest CPU, biggest), `1` = light/recommended, up to
  `9` = max (slow). Default `1`.
- `backup.worlds` — explicit folder names, or empty to auto-detect every world (recommended).
- `backup.disable-save-during-backup` — freeze saving while copying (strongly recommended `true`).
- `backup.min-free-space-mb` — skip the backup if the drive has less free space than this.
- `backup.throttle-ms` — sleep after each file to spread out disk I/O on a busy server (`0` = full speed).
- `messages.broadcast` — announce start/finish to everyone, or just to console + the op who ran it.

## Restoring

Stop the server, delete (or move aside) the world folders you want to restore, then extract the
chosen `world-backup-YYYY-MM-dd_HH-mm-ss.zip` into the server folder — it recreates `world/`,
`world_nether/`, … exactly as they were. Start the server.

## Build

```
./gradlew build
```

Compiles against the server's `mcpc-plus.jar` (Java 8 bytecode via a JDK 11 toolchain) and copies the
jar into the server's `plugins/` folder. Adjust `ext.serverDir` in `build.gradle` if your server path
differs.
