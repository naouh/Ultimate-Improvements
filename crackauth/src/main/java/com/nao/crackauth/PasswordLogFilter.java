package com.nao.crackauth;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * java.util.logging {@link Filter} that redacts passwords from the server console/log.
 *
 * <p>On MCPC+/CraftBukkit a player command is echoed to the log as
 * {@code "<name> issued server command: /login <password>"}. For the auth commands that leaks the
 * password in plain text, so this filter rewrites everything after the command word to {@code ***}
 * (e.g. {@code "/login ***"}). It chains to whatever filter was already on the logger/handler and is
 * fail-safe: any error leaves the record untouched, and it never drops a record.
 */
final class PasswordLogFilter implements Filter {

	/** A sensitive command word followed by arguments — the arguments are the secret to hide. */
	private static final Pattern SECRET = Pattern.compile(
			"(?i)(/(?:login|l|register|reg|changepassword|changepass|cp)\\b)\\s+\\S.*");

	private final Filter delegate;

	PasswordLogFilter(Filter delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean isLoggable(LogRecord record) {
		try {
			String msg = record.getMessage();
			if (msg != null && msg.indexOf('/') >= 0 && SECRET.matcher(msg).find()) {
				record.setMessage(SECRET.matcher(msg).replaceAll("$1 ***"));
			}
		} catch (Throwable ignored) {
			// never let redaction break logging
		}
		return delegate == null || delegate.isLoggable(record);
	}
}
