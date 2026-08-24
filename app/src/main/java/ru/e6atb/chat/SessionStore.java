package ru.e6atb.chat;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Properties;

import rs.ove.crypt.proto.NativeE2E;

final class SessionStore {
	private static final String FILE_NAME = "e6atb.session.properties";
	private static final String SERVER = "server";
	private static final String TOKEN = "token";
	private static final String USER_ID = "user_id";
	private static final String USERNAME = "username";
	private static final String LEGACY_LOGIN = "login";
	private static final String LAST_UPDATE = "last_update";
	private static final String BACKGROUND_LAST_UPDATE = "background_last_update";
	private static final String NOTIFICATION_BOOTSTRAP_COMPLETE = "notification_bootstrap_complete";
	private static final String LAST_GITHUB_UPDATE_CHECK_AT = "last_github_update_check_at";
	private static final String SHOW_STATUS = "show_status";
	private static final String USE_INSETS = "use_insets";
	private static final String LANGUAGE = "language";
	private static final String TRANSPORT_PROTOCOL = "transport_protocol";
	static final String TRANSPORT_AUTO = "auto";
	static final String TRANSPORT_MST5 = "mst5";
	static final String TRANSPORT_M5OH = "m5oh";
	private static final String E2E_PEER_PREFIX = "e2e.peer.";

	private SessionStore() {
	}

	static void save(Context context, String server, String token, String username) {
		save(context, server, token, userId(context), username);
	}

	static void save(Context context, String server, String token, String userId, String username) {
		Properties p = load(context);
		p.setProperty(SERVER, normalizeServer(server));
		p.setProperty(TOKEN, safe(token));
		p.setProperty(USER_ID, safe(userId));
		p.setProperty(USERNAME, safe(username));
		p.remove(LEGACY_LOGIN);
		store(context, p);
	}

	static void saveServer(Context context, String server) {
		Properties p = load(context);
		p.setProperty(SERVER, normalizeServer(server));
		store(context, p);
	}

	static void clear(Context context) {
		Properties p = load(context);
		p.remove(TOKEN);
		p.remove(USER_ID);
		p.remove(USERNAME);
		p.remove(LEGACY_LOGIN);
		p.remove(LAST_UPDATE);
		p.remove(BACKGROUND_LAST_UPDATE);
		p.remove(NOTIFICATION_BOOTSTRAP_COMPLETE);
		store(context, p);
	}

	static boolean hasSession(Context context) {
		return token(context).length() > 0;
	}

	static String server(Context context, String fallback) {
		Properties properties = load(context);
		// The production endpoint is fixed; this also migrates old arbitrary and
		// legacy server choices without affecting the user's authentication token.
		String fixed = normalizeServer(fallback);
		if (!fixed.equals(normalizeServer(properties.getProperty(SERVER, "")))) {
			properties.setProperty(SERVER, fixed);
			store(context, properties);
		}
		return fixed;
	}

	static String transportProtocol(Context context) {
		String protocol = get(context, TRANSPORT_PROTOCOL, TRANSPORT_AUTO);
		if (TRANSPORT_MST5.equals(protocol) || TRANSPORT_M5OH.equals(protocol)) return protocol;
		return TRANSPORT_AUTO;
	}

	static void setTransportProtocol(Context context, String protocol) {
		Properties p = load(context);
		if (!TRANSPORT_MST5.equals(protocol) && !TRANSPORT_M5OH.equals(protocol)) {
			protocol = TRANSPORT_AUTO;
		}
		p.setProperty(TRANSPORT_PROTOCOL, protocol);
		store(context, p);
	}

	/**
	 * Resolves the endpoint used for a connection without changing the stable
	 * server key that scopes cached chats and queued messages.
	 */
	static String transportEndpoint(Context context, String fallback) {
		String endpoint = server(context, fallback);
		String protocol = transportProtocol(context);
		if (TRANSPORT_AUTO.equals(protocol)) return endpoint;
		String prefix = TRANSPORT_MST5.equals(protocol) ? "mst5://" : "m5oh://";
		for (String candidate : endpoint.split(java.util.regex.Pattern.quote("|"))) {
			if (candidate.trim().toLowerCase(java.util.Locale.US).startsWith(prefix)) {
				return candidate.trim();
			}
		}
		// A fallback without the requested transport is still usable.
		return endpoint;
	}

	static String normalizeServer(String server) {
		String s = safe(server).trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		String lower = s.toLowerCase(java.util.Locale.US);
		String legacyPrefix = "tcp" + "://";
		if (lower.startsWith(legacyPrefix)) {
			return s.substring(legacyPrefix.length());
		}
		return s;
	}

	static String token(Context context) {
		return get(context, TOKEN, "");
	}

	static String login(Context context) {
		String username = get(context, USERNAME, "");
		return username.length() > 0 ? username : get(context, LEGACY_LOGIN, "");
	}

	static String userId(Context context) {
		return get(context, USER_ID, "");
	}

	static long lastUpdate(Context context) {
		return longValue(context, LAST_UPDATE);
	}

	static void lastUpdate(Context context, long id) {
		setLong(context, LAST_UPDATE, id);
	}

	static long backgroundLastUpdate(Context context) {
		return longValue(context, BACKGROUND_LAST_UPDATE);
	}

	static void backgroundLastUpdate(Context context, long id) {
		setLong(context, BACKGROUND_LAST_UPDATE, id);
	}

	static boolean notificationBootstrapComplete(Context context) {
		// Existing installations predate this flag and already have a valid
		// cursor, so default them to ready. A fresh login explicitly stores
		// false before either polling loop is started.
		return getBoolean(context, NOTIFICATION_BOOTSTRAP_COMPLETE, true);
	}

	static void notificationBootstrapComplete(Context context, boolean complete) {
		setBoolean(context, NOTIFICATION_BOOTSTRAP_COMPLETE, complete);
	}

	static long lastGithubUpdateCheckAt(Context context) {
		return longValue(context, LAST_GITHUB_UPDATE_CHECK_AT);
	}

	static void lastGithubUpdateCheckAt(Context context, long timestamp) {
		setLong(context, LAST_GITHUB_UPDATE_CHECK_AT, timestamp);
	}

	private static long longValue(Context context, String key) {
		String s = get(context, key, "0");
		try {
			return Long.parseLong(s);
		} catch (Exception e) {
			return 0;
		}
	}

	private static void setLong(Context context, String key, long id) {
		Properties p = load(context);
		p.setProperty(key, String.valueOf(id));
		store(context, p);
	}

	static boolean showStatus(Context context) {
		return getBoolean(context, SHOW_STATUS, true);
	}

	static void showStatus(Context context, boolean enabled) {
		setBoolean(context, SHOW_STATUS, enabled);
	}

	static boolean useInsets(Context context) {
		return getBoolean(context, USE_INSETS, android.os.Build.VERSION.SDK_INT >= 20);
	}

	static void useInsets(Context context, boolean enabled) {
		setBoolean(context, USE_INSETS, enabled);
	}

	static String language(Context context) {
		return get(context, LANGUAGE, AppLocale.SYSTEM);
	}

	static void language(Context context, String language) {
		Properties p = load(context);
		p.setProperty(LANGUAGE, safe(language));
		store(context, p);
	}

	static NativeE2E.Identity e2eIdentity(Context context, String login) {
		try {
			return NativeE2E.open(context, keyID(login), false);
		} catch (Exception ignored) {
			return null;
		}
	}

	static void clearLegacyE2EIdentity(Context context, String login) {
		Properties p = load(context);
		p.remove("e2e.private." + keyID(login));
		store(context, p);
	}

	static NativeE2E.Identity createE2EIdentity(Context context, String login) {
		try {
			return NativeE2E.open(context, keyID(login), true);
		} catch (Exception error) {
			throw new IllegalStateException("cannot create native E2E identity", error);
		}
	}

	static void saveE2EIdentity(Context context, String login, NativeE2E.Identity identity) {
		// NativeE2E restore/save is atomic; managed code never receives private key bytes.
	}

	static void clearE2EIdentity(Context context, String login) {
		try {
			NativeE2E.Identity identity = e2eIdentity(context, login);
			if (identity != null) identity.remove();
		} catch (Exception ignored) {
		}
		Properties p = load(context);
		// Remove legacy Java private keys after the incompatible v3 migration.
		p.remove("e2e.private." + keyID(login));
		store(context, p);
	}

	static boolean pinPeerE2EKey(Context context, String server, String ownLogin, String peer, String publicKey) {
		String key = E2E_PEER_PREFIX + keyID(server + "\n" + ownLogin + "\n" + peer);
		Properties p = load(context);
		String current = p.getProperty(key);
		if (current == null || !current.equals(publicKey)) {
			p.setProperty(key, publicKey);
			store(context, p);
			return current != null;
		}
		return false;
	}

	static String peerE2EFingerprint(Context context, String server, String ownLogin, String peer) {
		String key = E2E_PEER_PREFIX + keyID(server + "\n" + ownLogin + "\n" + peer);
		String publicKey = get(context, key, "");
		if (publicKey.length() == 0) return "";
		try {
			return NativeE2E.fingerprint(publicKey);
		} catch (Exception ignored) {
			return "";
		}
	}

	private static boolean getBoolean(Context context, String key, boolean fallback) {
		String value = get(context, key, fallback ? "true" : "false");
		return "true".equals(value);
	}

	private static void setBoolean(Context context, String key, boolean value) {
		Properties p = load(context);
		p.setProperty(key, value ? "true" : "false");
		store(context, p);
	}

	private static String get(Context context, String key, String fallback) {
		Properties p = load(context);
		String value = p.getProperty(key);
		return value == null ? fallback : value;
	}

	private static synchronized Properties load(Context context) {
		Properties p = new Properties();
		File f = file(context);
		if (f == null || !f.exists()) {
			return p;
		}

		FileInputStream in = null;
		try {
			in = new FileInputStream(f);
			p.load(in);
		} catch (Exception ignored) {
		} finally {
			closeQuietly(in);
		}

		return p;
	}

	private static synchronized void store(Context context, Properties p) {
		File f = file(context);
		if (f == null) {
			return;
		}

		File parent = f.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		FileOutputStream out = null;
		try {
			out = new FileOutputStream(f);
			p.store(out, "");
			out.flush();
		} catch (Exception ignored) {
		} finally {
			closeQuietly(out);
		}
	}

	private static File file(Context context) {
		if (context == null) {
			return null;
		}

		Context app = context.getApplicationContext();
		if (app == null) {
			app = context;
		}

		File dir = app.getFilesDir();
		if (dir == null) {
			return null;
		}

		if (!dir.exists()) {
			dir.mkdirs();
		}

		return new File(dir, FILE_NAME);
	}

	private static void closeQuietly(java.io.Closeable c) {
		if (c == null) {
			return;
		}

		try {
			c.close();
		} catch (Exception ignored) {
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private static String keyID(String value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(safe(value).getBytes("UTF-8"));
			StringBuilder out = new StringBuilder(hash.length * 2);
			for (int i = 0; i < hash.length; i++) {
				int b = hash[i] & 0xff;
				if (b < 16) out.append('0');
				out.append(Integer.toHexString(b));
			}
			return out.toString();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
