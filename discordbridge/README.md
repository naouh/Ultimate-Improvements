# DiscordBridge

![Discord and in-game chat bridged both ways](screenshot.png)

A lightweight **two-way chat bridge** between a Minecraft server and a Discord channel,
built for **MCPC+ 1.4.7** (Forge + Bukkit hybrid). Drop the jar in `plugins/`, fill in a
bot token + channel id, and players can talk between the game and Discord both ways.

* Minecraft chat → Discord, and Discord → Minecraft.
* Optional: announce **joins / quits**, **server start / stop**, and **deaths**.
* **No JDA, no websocket, no shading** — talks to Discord over plain HTTPS and *polls*
  the channel for new messages. The jar has **zero runtime dependencies** (~30 KB).
* You **must enable the *Message Content* intent** for the bot (Developer Portal → Bot →
  Privileged Gateway Intents). Discord blanks message content for bots without it, over
  the REST API as well as the gateway — so without it Discord → Minecraft arrives empty.

## How it works

| Direction        | Mechanism                                                              |
|------------------|-----------------------------------------------------------------------|
| MC → Discord     | HTTP POST per message (channel webhook if set, otherwise the bot).     |
| Discord → MC     | `GET /channels/{id}/messages?after=…` every few seconds (default 3s).  |

All network I/O runs on a single daemon thread, and inbound messages are broadcast on
the main server thread, so the game is never blocked by HTTP. Trade-off vs. a gateway
bot: Discord → Minecraft has a small (poll-interval) delay. This is far lighter on the
server than embedding JDA.

## Setup

1. **Create the bot**
   - <https://discord.com/developers/applications> → *New Application* → *Bot* tab → *Reset Token* → copy it.
   - On the same *Bot* tab → *Privileged Gateway Intents* → turn **ON** *MESSAGE CONTENT INTENT* → *Save*.
     This is **required** — without it Discord sends empty message content (over REST too), so
     Discord → Minecraft won't receive anything. It's free for bots in fewer than 100 servers.

2. **Invite it to your server**
   - *OAuth2* → *URL Generator* → scope `bot` → permissions **View Channel**,
     **Read Message History**, **Send Messages** → open the generated URL and add it.

3. **Get the channel id**
   - Discord → *Settings* → *Advanced* → enable **Developer Mode**.
   - Right-click the target channel → **Copy Channel ID**.

4. **(Optional) webhook for nicer MC → Discord messages**
   - Channel → *Edit Channel* → *Integrations* → *Webhooks* → *New Webhook* → *Copy Webhook URL*.
   - With a webhook, each player's name shows up as the Discord author. Without one,
     all Minecraft chat is posted by the bot as `**Player**: message`.

5. **Configure & load**
   - Edit `plugins/DiscordBridge/config.yml`: set `token`, `channel-id`, and optionally `webhook-url`.
   - `/discordbridge reload` (or restart the server).
   - Check it with `/discordbridge status`.

## Commands & permissions

| Command                  | Description                          | Permission             |
|--------------------------|--------------------------------------|------------------------|
| `/discordbridge reload`  | Re-read config and reconnect.        | `discordbridge.admin`  |
| `/discordbridge status`  | Show whether the bridge is active.   | `discordbridge.admin`  |

Alias: `/dbridge`. `discordbridge.admin` defaults to OP.

## Configuration

See [`config.yml`](src/main/resources/config.yml) for the fully commented file. Key options:

| Key                          | Default              | Description                                              |
|------------------------------|----------------------|---------------------------------------------------------|
| `discord.token`              | —                    | Bot token (required).                                    |
| `discord.channel-id`         | —                    | Channel to bridge (required).                            |
| `discord.webhook-url`        | *(empty)*            | Optional webhook for per-player authoring.               |
| `discord.poll-interval`      | `3`                  | Seconds between Discord polls (min 1).                   |
| `discord.ignore-bots`        | `true`              | Skip other bots / webhooks (prevents echo loops).        |
| `events.join-quit`           | `true`               | Announce joins and quits in Discord.                     |
| `events.server-start-stop`   | `true`               | Announce server start / stop in Discord.                 |
| `events.deaths`              | `false`              | Relay player death messages.                             |
| `messages.*`                 | see file             | Templates. `&` colour codes work for `discord-to-mc`.    |

## Notes

* Mentions from in-game (`@everyone`, role/user pings) are **disabled** on outgoing
  messages (`allowed_mentions: parse: []`), so players can't ping the server from chat.
* Incoming Discord user mentions are rendered as `@Name`, custom emojis as `:name:`,
  and attachments are appended as their URLs.
* The bot must be able to see the channel; without **Read Message History** the
  Discord → Minecraft direction won't receive anything.

## Build

```sh
./gradlew build
```

Compiles against the server's `mcpc-plus.jar` (the dead 1.4.7 Bukkit Maven repos aren't
needed) and, on success, copies the jar into the server's `plugins/` folder
(`installToPlugins`). Adjust `serverDir` in [`build.gradle`](build.gradle) if your server
lives elsewhere.
