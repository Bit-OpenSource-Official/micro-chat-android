package ru.e6atb.chat;

import java.net.URI;
import java.net.URLDecoder;
import java.util.regex.Pattern;

final class BotDeepLinkParser {
	private static final Pattern LOGIN = Pattern.compile("[a-z0-9](?:[a-z0-9_]{3,30}[a-z0-9])");
	private static final Pattern PAYLOAD = Pattern.compile("[A-Za-z0-9_-]{1,128}");

	static final class Link {
		final String login;
		final String payload;

		Link(String login, String payload) {
			this.login = login;
			this.payload = payload;
		}

		String startCommand() {
			return payload.length() == 0 ? "/start" : "/start " + payload;
		}
	}

	private BotDeepLinkParser() {
	}

	static Link parse(String raw) {
		if (raw == null || raw.trim().length() == 0) return null;
		try {
			URI uri = new URI(raw.trim());
			String scheme = lower(uri.getScheme());
			String host = lower(uri.getHost());
			String path = uri.getPath() == null ? "" : uri.getPath();
			String login;
			if ("https".equals(scheme) && "ms.ove.rs".equals(host) && path.startsWith("/bot/")) {
				login = path.substring("/bot/".length());
			} else if ("ovechat".equals(scheme) && "bot".equals(host) && path.startsWith("/")) {
				login = path.substring(1);
			} else {
				return null;
			}
			if (login.contains("/") || !LOGIN.matcher(login).matches()
					|| !(login.startsWith("bot") || login.endsWith("bot"))) {
				return null;
			}
			String payload = query(uri.getRawQuery(), "start");
			if (payload.length() > 0 && !PAYLOAD.matcher(payload).matches()) return null;
			return new Link(login, payload);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String query(String raw, String name) throws Exception {
		if (raw == null || raw.length() == 0) return "";
		String[] pairs = raw.split("&");
		for (String pair : pairs) {
			int split = pair.indexOf('=');
			String key = split < 0 ? pair : pair.substring(0, split);
			if (name.equals(URLDecoder.decode(key, "UTF-8"))) {
				return split < 0 ? "" : URLDecoder.decode(pair.substring(split + 1), "UTF-8");
			}
		}
		return "";
	}

	private static String lower(String value) {
		return value == null ? "" : value.toLowerCase(java.util.Locale.US);
	}
}

