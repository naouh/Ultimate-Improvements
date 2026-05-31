"""Discord bot that shows a Minecraft Java server's status in its presence.

The bot's Discord presence is updated on a fixed interval to display the
current online player count, e.g. "Playing on T1F (9/512)". When the server
is unreachable it shows an offline status instead.

Supports both old servers (legacy Server List Ping, e.g. Minecraft 1.4.7 /
FTB Ultimate) and modern servers (1.7+). The legacy ping is tried first, then
the modern protocol as a fallback.

Configuration is read from environment variables (see .env.example):
    DISCORD_TOKEN     - your bot token (required)
    MC_SERVER_ADDRESS - server address, host or host:port (required)
    UPDATE_INTERVAL   - seconds between updates (optional, default 60)
    SERVER_NAME       - label shown in the presence (optional, default "the server")
"""

import os
import socket
import struct
import logging

import discord
from discord.ext import tasks
from dotenv import load_dotenv
from mcstatus import JavaServer

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("mc-status")

TOKEN = os.getenv("DISCORD_TOKEN")
SERVER_ADDRESS = os.getenv("MC_SERVER_ADDRESS")
SERVER_NAME = os.getenv("SERVER_NAME", "the server")
UPDATE_INTERVAL = int(os.getenv("UPDATE_INTERVAL", "60"))

if not TOKEN:
    raise SystemExit("DISCORD_TOKEN is not set. Copy .env.example to .env and fill it in.")
if not SERVER_ADDRESS:
    raise SystemExit("MC_SERVER_ADDRESS is not set. Copy .env.example to .env and fill it in.")

# No privileged intents are required just to update the presence.
client = discord.Client(intents=discord.Intents.none())


def parse_address(addr, default_port=25565):
    """Split 'host' or 'host:port' into (host, port)."""
    if ":" in addr:
        host, _, port = addr.rpartition(":")
        return host, int(port)
    return addr, default_port


def legacy_ping(host, port, timeout=5):
    """Old-style Server List Ping (0xFE 0x01), for servers <= 1.6 such as 1.4.7.

    Returns (online, max) or raises on any failure.
    """
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        sock.sendall(b"\xfe\x01")
        data = b""
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            data += chunk
            if len(data) >= 3:
                expected = 3 + struct.unpack(">H", data[1:3])[0] * 2
                if len(data) >= expected:
                    break

    if len(data) < 3 or data[0] != 0xFF:
        raise ValueError("not a legacy ping response")

    length = struct.unpack(">H", data[1:3])[0]
    payload = data[3:3 + length * 2].decode("utf-16-be")

    if payload.startswith("\xa7\x31\x00"):  # "§1\0" -> 1.4+ format
        # §1 \0 protocol \0 version \0 motd \0 online \0 max
        parts = payload.split("\x00")
    else:  # pre-1.4 format: motd §online §max
        parts = payload.split("\xa7")

    return int(parts[-2]), int(parts[-1])


def query_status():
    """Return (online, max) player counts, or None if the server is offline."""
    host, port = parse_address(SERVER_ADDRESS)

    # Try the legacy ping first (works with old servers like 1.4.7 / FTB Ultimate).
    try:
        return legacy_ping(host, port)
    except Exception as exc:
        log.debug("Legacy ping failed (%s), trying modern protocol...", exc)

    # Fall back to the modern protocol (1.7+ servers).
    try:
        server = JavaServer.lookup(SERVER_ADDRESS)
        status = server.status()
        return status.players.online, status.players.max
    except Exception as exc:  # network error, timeout, server down, etc.
        log.warning("Could not reach %s: %s", SERVER_ADDRESS, exc)
        return None


@tasks.loop(seconds=UPDATE_INTERVAL)
async def update_presence():
    result = query_status()

    if result is None:
        activity = discord.Activity(
            type=discord.ActivityType.watching,
            name=f"{SERVER_NAME} (offline)",
        )
        await client.change_presence(status=discord.Status.dnd, activity=activity)
        return

    online, maximum = result
    activity = discord.Activity(
        type=discord.ActivityType.playing,
        name=f"on {SERVER_NAME} ({online}/{maximum})",
    )
    await client.change_presence(status=discord.Status.online, activity=activity)
    log.info("Presence updated: %s/%s players online", online, maximum)


@update_presence.before_loop
async def before_update():
    await client.wait_until_ready()


@client.event
async def on_ready():
    log.info("Logged in as %s (id: %s)", client.user, client.user.id)
    if not update_presence.is_running():
        update_presence.start()


if __name__ == "__main__":
    client.run(TOKEN)
