package com.nao.discordbridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Talks to the Discord HTTP API with plain {@link HttpURLConnection} - no JDA, no websocket.
 *
 * <p>Outbound (MC -&gt; Discord) messages are POSTed either through a channel webhook (so each player's
 * name is the author) or through the bot account. Inbound (Discord -&gt; MC) messages are fetched by
 * POLLING {@code GET /channels/{id}/messages?after=...} on a fixed interval. Polling - rather than the
 * gateway - means we never need the privileged "Message Content" intent: the REST API returns content to
 * any bot that can see the channel.
 *
 * <p>All network I/O runs on a single background thread, so {@code lastMessageId} and the send queue need
 * no extra synchronisation. New inbound lines are handed to a {@link MessageSink} (the plugin hops them
 * back onto the main server thread before broadcasting).
 */
final class DiscordClient {

	private static final String API = "https://discord.com/api/v10";
	private static final String USER_AGENT = "DiscordBridge (https://github.com/Naouh, 1.0)";
	/** Discord epoch (2015-01-01) used to synthesise a "messages after now" snowflake. */
	private static final long DISCORD_EPOCH = 1420070400000L;

	private static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:(\\w+):\\d+>");

	/** One inbound Discord message, reduced to what Minecraft needs. */
	static final class ChatLine {
		final String user;
		final String message;
		ChatLine(String user, String message) {
			this.user = user;
			this.message = message;
		}
	}

	/** Receives batches of new inbound messages (called on the I/O thread). */
	interface MessageSink {
		void accept(List<ChatLine> lines);
	}

	private final Logger log;
	private final String token;
	private final String channelId;
	private final String webhookUrl;
	private final String webhookId;   // our webhook's id, so we never relay our own MC->Discord posts back
	private final String avatarTemplate; // per-player webhook avatar, {player} substituted; null = webhook default
	private final boolean ignoreBots;

	private ScheduledExecutorService exec;
	private volatile boolean initialized;
	private String lastMessageId; // only touched on the I/O thread
	private String selfId;        // our own bot user id, so we never echo our own messages
	private boolean warnedEmptyContent; // log the "enable Message Content Intent" hint only once

	DiscordClient(Logger log, String token, String channelId, String webhookUrl, String avatarTemplate,
			boolean ignoreBots) {
		this.log = log;
		this.token = token;
		this.channelId = channelId;
		this.webhookUrl = (webhookUrl == null || webhookUrl.trim().isEmpty()) ? null : webhookUrl.trim();
		this.webhookId = extractWebhookId(this.webhookUrl);
		this.avatarTemplate = (avatarTemplate == null || avatarTemplate.trim().isEmpty()) ? null : avatarTemplate.trim();
		this.ignoreBots = ignoreBots;
	}

	/** Pull the numeric id out of a webhook URL (.../webhooks/{id}/{token}); null if not a webhook URL. */
	private static String extractWebhookId(String url) {
		if (url == null) return null;
		int marker = url.indexOf("/webhooks/");
		if (marker < 0) return null;
		int start = marker + "/webhooks/".length();
		int end = url.indexOf('/', start);
		String id = end < 0 ? url.substring(start) : url.substring(start, end);
		return id.isEmpty() ? null : id;
	}

	boolean hasWebhook() {
		return webhookUrl != null;
	}

	/** Start the background I/O thread and schedule polling every {@code pollSeconds}. */
	void start(int pollSeconds, final MessageSink sink) {
		exec = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
			@Override public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "DiscordBridge-IO");
				t.setDaemon(true);
				return t;
			}
		});
		int period = Math.max(1, pollSeconds);
		exec.scheduleWithFixedDelay(new Runnable() {
			@Override public void run() {
				try {
					poll(sink);
				} catch (Throwable t) {
					// Never let an exception escape: scheduleWithFixedDelay would cancel all future runs.
					log.log(Level.FINE, "[DiscordBridge] poll cycle failed", t);
				}
			}
		}, period, period, TimeUnit.SECONDS);
	}

	void stop() {
		if (exec != null) {
			exec.shutdownNow();
			exec = null;
		}
	}

	// --- Outbound (MC -> Discord) ------------------------------------------------------------------

	/** Queue a message to be sent through the bot account. */
	void sendAsBot(final String content) {
		if (exec == null) return;
		exec.submit(new Runnable() {
			@Override public void run() {
				try {
					postBotMessage(content);
				} catch (Exception e) {
					log.log(Level.FINE, "[DiscordBridge] sendAsBot failed", e);
				}
			}
		});
	}

	/** Queue a message to be sent through the channel webhook with {@code username} as the author. */
	void sendAsWebhook(final String username, final String content) {
		if (exec == null || webhookUrl == null) return;
		exec.submit(new Runnable() {
			@Override public void run() {
				try {
					postWebhookMessage(username, content);
				} catch (Exception e) {
					log.log(Level.FINE, "[DiscordBridge] sendAsWebhook failed", e);
				}
			}
		});
	}

	/**
	 * Send a bot message synchronously on the calling thread. Used for the "server stopping" notice during
	 * onDisable, when the background executor is already being torn down.
	 */
	void sendAsBotBlocking(String content) {
		try {
			postBotMessage(content);
		} catch (Exception e) {
			log.log(Level.FINE, "[DiscordBridge] blocking send failed", e);
		}
	}

	private void postBotMessage(String content) throws IOException {
		String body = "{\"content\":\"" + Json.esc(content) + "\",\"allowed_mentions\":{\"parse\":[]}}";
		send(API + "/channels/" + channelId + "/messages", true, body);
	}

	private void postWebhookMessage(String username, String content) throws IOException {
		StringBuilder body = new StringBuilder(128);
		body.append("{\"username\":\"").append(Json.esc(trimName(username))).append('"');
		if (avatarTemplate != null) {
			body.append(",\"avatar_url\":\"")
					.append(Json.esc(avatarTemplate.replace("{player}", username)))
					.append('"');
		}
		body.append(",\"content\":\"").append(Json.esc(content)).append('"');
		body.append(",\"allowed_mentions\":{\"parse\":[]}}");
		send(webhookUrl, false, body.toString());
	}

	/** Discord webhook usernames are capped at 80 chars and can't be empty. */
	private static String trimName(String name) {
		if (name == null || name.isEmpty()) return "Minecraft";
		return name.length() > 80 ? name.substring(0, 80) : name;
	}

	// --- Inbound (Discord -> MC) -------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private void poll(MessageSink sink) throws IOException {
		if (!initialized) {
			initialize();
			initialized = true;
			return; // first cycle only establishes the baseline; don't replay history
		}

		String url = API + "/channels/" + channelId + "/messages?limit=50";
		if (lastMessageId != null) url += "&after=" + lastMessageId;

		String body = get(url);
		if (body == null) return;

		Object parsed;
		try {
			parsed = Json.parse(body);
		} catch (RuntimeException e) {
			log.log(Level.FINE, "[DiscordBridge] could not parse poll response", e);
			return;
		}
		if (!(parsed instanceof List)) return;
		List<Object> arr = (List<Object>) parsed;
		if (arr.isEmpty()) return;

		// Discord returns newest-first. Walk oldest-first so Minecraft sees them in order.
		List<ChatLine> out = new ArrayList<ChatLine>();
		for (int k = arr.size() - 1; k >= 0; k--) {
			ChatLine line = toChatLine((Map<String, Object>) arr.get(k));
			if (line != null) out.add(line);
		}

		// Advance the cursor to the newest id (arr[0]) even if everything was filtered out.
		Object newest = ((Map<String, Object>) arr.get(0)).get("id");
		if (newest instanceof String) lastMessageId = (String) newest;

		if (!out.isEmpty()) sink.accept(out);
	}

	@SuppressWarnings("unchecked")
	private ChatLine toChatLine(Map<String, Object> msg) {
		Map<String, Object> author = (Map<String, Object>) msg.get("author");
		boolean isBot = author != null && Boolean.TRUE.equals(author.get("bot"));
		boolean isWebhook = msg.get("webhook_id") != null;

		// Never relay our own messages back into the game (would loop), and optionally skip other bots.
		if (author != null && selfId != null && selfId.equals(author.get("id"))) return null;
		if (webhookId != null && webhookId.equals(msg.get("webhook_id"))) return null;
		if (ignoreBots && (isBot || isWebhook)) return null;

		String raw = (String) msg.get("content");
		List<Object> attachments = (List<Object>) msg.get("attachments");
		boolean noAttachments = attachments == null || attachments.isEmpty();
		// A human message with NO content AND no attachment is the tell-tale sign that the bot's
		// "Message Content Intent" is OFF - Discord blanks the content field over REST too.
		if ((raw == null || raw.isEmpty()) && noAttachments && !warnedEmptyContent) {
			warnedEmptyContent = true;
			log.warning("[DiscordBridge] Got a Discord message with EMPTY content. This almost certainly "
					+ "means the 'Message Content Intent' is OFF. Enable it: Discord Developer Portal -> your "
					+ "app -> Bot -> Privileged Gateway Intents -> turn ON 'MESSAGE CONTENT INTENT' -> Save. "
					+ "Then /discordbridge reload.");
		}

		String content = cleanContent(raw == null ? "" : raw, (List<Object>) msg.get("mentions"));
		content = appendAttachments(content, attachments);
		if (content.isEmpty()) return null;

		return new ChatLine(displayName(author), content);
	}

	@SuppressWarnings("unchecked")
	private String cleanContent(String content, List<Object> mentions) {
		// Custom emojis <:name:id> / <a:name:id> -> :name:
		content = CUSTOM_EMOJI.matcher(content).replaceAll(":$1:");
		// User mentions <@id> / <@!id> -> @display-name (resolved from the message's mention list)
		if (mentions != null) {
			for (Object o : mentions) {
				Map<String, Object> u = (Map<String, Object>) o;
				Object id = u.get("id");
				if (!(id instanceof String)) continue;
				String at = "@" + displayName(u);
				content = content.replace("<@" + id + ">", at).replace("<@!" + id + ">", at);
			}
		}
		return content.trim();
	}

	@SuppressWarnings("unchecked")
	private String appendAttachments(String content, List<Object> attachments) {
		if (attachments == null) return content;
		StringBuilder b = new StringBuilder(content);
		for (Object o : attachments) {
			Object u = ((Map<String, Object>) o).get("url");
			if (u instanceof String) {
				if (b.length() > 0) b.append(' ');
				b.append((String) u);
			}
		}
		return b.toString();
	}

	private static String displayName(Map<String, Object> user) {
		if (user == null) return "unknown";
		Object global = user.get("global_name");
		if (global instanceof String && !((String) global).isEmpty()) return (String) global;
		Object name = user.get("username");
		return name instanceof String ? (String) name : "unknown";
	}

	/** First poll cycle: learn our own user id (also validates the token) and set the read cursor. */
	@SuppressWarnings("unchecked")
	private void initialize() throws IOException {
		String me = get(API + "/users/@me");
		if (me != null) {
			Object parsed = Json.parse(me);
			if (parsed instanceof Map) {
				Object id = ((Map<String, Object>) parsed).get("id");
				if (id instanceof String) selfId = (String) id;
			}
		}

		String latest = get(API + "/channels/" + channelId + "/messages?limit=1");
		if (latest == null) {
			// Reading the channel failed (almost always: the BOT lacks View Channel / Read Message History
			// on this channel). A working webhook does NOT grant this - the webhook carries its own auth.
			log.warning("[DiscordBridge] Cannot read channel " + channelId + " - Discord -> Minecraft is "
					+ "DISABLED. Give the BOT (not just the webhook) 'View Channel' + 'Read Message History' "
					+ "on that channel, then /discordbridge reload. (See the HTTP line logged just above.)");
		} else {
			Object parsed = Json.parse(latest);
			if (parsed instanceof List && !((List<Object>) parsed).isEmpty()) {
				Object id = ((Map<String, Object>) ((List<Object>) parsed).get(0)).get("id");
				if (id instanceof String) lastMessageId = (String) id;
			}
			log.info("[DiscordBridge] Channel read OK - Discord -> Minecraft is active.");
		}
		// Empty channel (or failed lookup): only relay messages newer than "now".
		if (lastMessageId == null) {
			lastMessageId = Long.toString((System.currentTimeMillis() - DISCORD_EPOCH) << 22);
		}
	}

	// --- Low-level HTTP ----------------------------------------------------------------------------

	/** GET a Discord endpoint with the bot token. Returns the body, or null on a non-2xx response. */
	private String get(String url) throws IOException {
		for (int attempt = 0; attempt < 3; attempt++) {
			HttpURLConnection c = open(url, "GET", true);
			int code = c.getResponseCode();
			if (code == 429) { backoff(c); continue; }
			String body = readBody(c);
			if (code >= 200 && code < 300) return body;
			warnHttp("GET", url, code, body);
			return null;
		}
		return null;
	}

	/** POST a JSON body. {@code auth} adds the bot token (use false for webhook URLs). */
	private void send(String url, boolean auth, String jsonBody) throws IOException {
		byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
		for (int attempt = 0; attempt < 3; attempt++) {
			HttpURLConnection c = open(url, "POST", auth);
			c.setRequestProperty("Content-Type", "application/json");
			c.setDoOutput(true);
			OutputStream os = c.getOutputStream();
			try {
				os.write(data);
				os.flush();
			} finally {
				os.close();
			}
			int code = c.getResponseCode();
			if (code == 429) { backoff(c); continue; }
			if (code < 200 || code >= 300) warnHttp("POST", url, code, readBody(c));
			return;
		}
	}

	private HttpURLConnection open(String url, String method, boolean auth) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setRequestMethod(method);
		c.setConnectTimeout(10000);
		c.setReadTimeout(15000);
		c.setRequestProperty("User-Agent", USER_AGENT);
		if (auth) c.setRequestProperty("Authorization", "Bot " + token);
		return c;
	}

	/** Honour a 429 by sleeping for the Retry-After the API reports (default 1s). */
	private void backoff(HttpURLConnection c) {
		double seconds = 1.0;
		String header = c.getHeaderField("Retry-After");
		if (header != null) {
			try { seconds = Double.parseDouble(header.trim()); } catch (NumberFormatException ignored) { }
		}
		try {
			Thread.sleep((long) (seconds * 1000) + 250);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void warnHttp(String method, String url, int code, String body) {
		String detail = body == null ? "" : body.replace('\n', ' ');
		if (detail.length() > 300) detail = detail.substring(0, 300);
		log.warning("[DiscordBridge] " + method + " " + stripQuery(url) + " -> HTTP " + code + " " + detail);
	}

	private static String stripQuery(String url) {
		int q = url.indexOf('?');
		return q < 0 ? url : url.substring(0, q);
	}

	private static String readBody(HttpURLConnection c) throws IOException {
		InputStream is = null;
		try {
			int code = c.getResponseCode();
			is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
			if (is == null) return "";
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			int n;
			while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
			return new String(buf.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			if (is != null) {
				try { is.close(); } catch (IOException ignored) { }
			}
		}
	}
}
