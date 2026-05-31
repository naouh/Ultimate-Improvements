# Minecraft Server Status — Discord Bot

A small Discord bot that shows a Minecraft **Java** server's status in its
presence, e.g. `Playing on T1F (9/512)`. When the server is down it shows
`Watching T1F (offline)` with a "Do Not Disturb" status.

## Setup

1. **Create the bot application**
   - Go to https://discord.com/developers/applications → *New Application*.
   - Open the *Bot* tab → *Add Bot* → copy the **token**.
   - No privileged intents are needed (the bot only updates its own presence).

2. **Invite the bot to your server**
   - *OAuth2* tab → *URL Generator* → scope `bot` → copy the URL and open it.

3. **Configure**
   ```sh
   cp .env.example .env
   ```
   Edit `.env` and set `DISCORD_TOKEN`, `MC_SERVER_ADDRESS`, and `SERVER_NAME`.

4. **Install dependencies & run**
   ```sh
   python -m venv .venv
   # Windows:
   .venv\Scripts\activate
   # macOS/Linux:
   source .venv/bin/activate

   pip install -r requirements.txt
   python bot.py
   ```

## Configuration

| Variable            | Required | Default        | Description                                   |
|---------------------|----------|----------------|-----------------------------------------------|
| `DISCORD_TOKEN`     | yes      | —              | Your Discord bot token.                       |
| `MC_SERVER_ADDRESS` | yes      | —              | `host` or `host:port` of the Java server.     |
| `SERVER_NAME`       | no       | `the server`   | Label shown in the presence.                  |
| `UPDATE_INTERVAL`   | no       | `60`           | Seconds between status refreshes.             |

## Notes

- The presence is global (the same across every server the bot is in).
- Discord may take a few seconds to display a presence change.
- Keep `UPDATE_INTERVAL` reasonable (≥ 30s) to avoid rate limits.
