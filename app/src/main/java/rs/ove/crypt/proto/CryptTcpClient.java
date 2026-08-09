package rs.ove.crypt.proto;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public final class CryptTcpClient {
	private static final long IDLE_RECONNECT_MS = 90000L;
	private static final int OP_REGISTER = 1;
	private static final int OP_LOGIN = 2;
	private static final int OP_EMAIL_AUTH_START = 3;
	private static final int OP_EMAIL_AUTH_VERIFY = 4;
	private static final int OP_ME = 5;
	private static final int OP_ACCOUNT_DELETE = 6;
	private static final int OP_SET_USERNAME = 7;
	private static final int OP_SET_NAME = 8;
	private static final int OP_SET_PRIVACY = 9;
	private static final int OP_CONTACTS = 10;
	private static final int OP_CONTACT_ADD = 11;
	private static final int OP_CONTACT_DELETE = 12;
	private static final int OP_CREATE_GROUP = 13;
	private static final int OP_CREATE_CHANNEL = 14;
	private static final int OP_SET_CHAT_TITLE = 15;
	private static final int OP_SET_CHANNEL_USERNAME = 16;
	private static final int OP_SET_CHANNEL_COMMENTS = 17;
	private static final int OP_SEND_CHANNEL_COMMENT = 18;
	private static final int OP_CHANNEL_COMMENTS = 19;
	private static final int OP_ADD_CHAT_MEMBER = 20;
	private static final int OP_REMOVE_CHAT_MEMBER = 21;
	private static final int OP_SET_CLOUD_PASSWORD = 22;
	private static final int OP_RESET_CLOUD_PASSWORD = 23;
	private static final int OP_SESSIONS = 24;
	private static final int OP_REVOKE_SESSION = 25;
	private static final int OP_REVOKE_OTHER_SESSIONS = 26;
	private static final int OP_CREATE_BOT = 27;
	private static final int OP_RESET_BOT_TOKEN = 28;
	private static final int OP_SET_E2E_KEY = 29;
	private static final int OP_GET_E2E_KEY = 30;
	private static final int OP_SET_E2E_BACKUP = 31;
	private static final int OP_GET_E2E_BACKUP = 32;
	private static final int OP_RESET_E2E = 33;
	private static final int OP_WALLET = 34;
	private static final int OP_WALLET_SEND = 35;
	private static final int OP_WALLET_HISTORY = 36;
	private static final int OP_CALL = 37;
	private static final int OP_VOICE_TICKET = 38;
	private static final int OP_VOICE_PARTICIPANTS = 39;
	private static final int OP_SEND = 40;
	private static final int OP_EDIT = 41;
	private static final int OP_CALLBACK = 42;
	private static final int OP_REACT = 43;
	private static final int OP_REACT_PAID = 44;
	private static final int OP_READ = 45;
	private static final int OP_DELETE = 46;
	private static final int OP_FAVORITE = 47;
	private static final int OP_INIT_UPLOAD = 48;
	private static final int OP_COMPLETE_UPLOAD = 49;
	private static final int OP_NODES_STATUS = 50;
	private static final int OP_CHATS = 51;
	private static final int OP_DELETE_CHAT = 52;
	private static final int OP_BAN_USER = 53;
	private static final int OP_UNBAN_USER = 54;
	private static final int OP_HISTORY = 55;
	private static final int OP_SYNC = 56;
	private static final int OP_OAUTH_DEVICE_REQUEST = 60;
	private static final int OP_OAUTH_DEVICE_DECISION = 61;
	private static final int OP_UPLOAD_LEGACY = 64;
	private static final int OP_FILE_TICKET = 65;
	private static final int OP_UPLOAD_QUOTE = 66;
	private static final int OP_UPLOAD_AUTHORIZE = 67;
	private static final int OP_UPLOAD_CANCEL = 68;
	private static final int OP_FORWARD = 69;

	private final Object lock = new Object();
	private Connection cached;

	public Response request(String baseUrl, String token, String method, String path, byte[] body, int timeoutMs) throws Exception {
		Endpoint endpoint = Endpoint.parse(baseUrl);
		int timeout = timeoutMs > 0 ? timeoutMs : 10000;
		if (!usePersistent(path, timeout)) {
			Connection connection = open(endpoint, timeout, System.currentTimeMillis());
			try {
				return request(connection, token, method, path, body, timeout);
			} finally {
				close(connection);
			}
		}
		Connection connection = connection(endpoint, timeout);
		try {
			return request(connection, token, method, path, body, timeout);
		} catch (IOException ex) {
			closeCached(connection);
			if (isRetryable(method)) {
				connection = connection(endpoint, timeout);
				try {
					return request(connection, token, method, path, body, timeout);
				} catch (IOException retryEx) {
					closeCached(connection);
					throw retryEx;
				}
			}
			throw ex;
		}
	}

	private Response request(Connection connection, String token, String method, String path, byte[] body, int timeoutMs) throws Exception {
		ensureAuthenticated(connection, token == null ? "" : token);
		String normalizedMethod = method == null ? "GET" : method.toUpperCase(Locale.US);
		String requestPath = path == null || path.length() == 0 ? "/" : path;
		int opcode = opcode(normalizedMethod, requestPath);
		JSONObject payload;
		if ("GET".equals(normalizedMethod)) {
			payload = new JSONObject();
			int split = requestPath.indexOf('?');
			if (split >= 0 && split + 1 < requestPath.length()) payload.put("query", requestPath.substring(split + 1));
		} else {
			payload = body == null || body.length == 0
					? new JSONObject()
					: new JSONObject(new String(body, "UTF-8"));
		}
		long requestId = connection.nextId();
		Mst5Frame frame = new Mst5Frame(
				"GET".equals(normalizedMethod) ? Mst5Frame.QUERY : Mst5Frame.COMMAND,
				opcode,
				requestId,
				MiniCbor.encode(payload)
		);
		Mst5Frame response = connection.exchange(frame, timeoutMs);
		if (response.id != requestId || (response.kind != Mst5Frame.RESULT
				&& response.kind != Mst5Frame.EVENT_BATCH && response.kind != Mst5Frame.ERROR)) {
			throw new IOException("unexpected MST5 response");
		}
		connection.lastUsedAt = System.currentTimeMillis();
		Object decoded = MiniCbor.decode(response.payload);
		byte[] responseBytes = jsonBytes(decoded);
		return new Response(response.code, Collections.<String, String>emptyMap(), responseBytes);
	}

	private static byte[] jsonBytes(Object value) throws Exception {
		if (value instanceof JSONObject || value instanceof org.json.JSONArray) {
			return value.toString().getBytes("UTF-8");
		}
		if (value == JSONObject.NULL || value == null) return "null".getBytes("UTF-8");
		if (value instanceof byte[]) return (byte[])value;
		if (value instanceof String) return JSONObject.quote((String)value).getBytes("UTF-8");
		return String.valueOf(value).getBytes("UTF-8");
	}

	private static void ensureAuthenticated(Connection connection, String token) throws Exception {
		synchronized (connection.authLock) {
			if (token.equals(connection.authenticatedToken)) return;
			JSONObject auth = new JSONObject();
			auth.put("token", token);
			long requestId = connection.nextId();
			Mst5Frame frame = new Mst5Frame(Mst5Frame.AUTH, 0, requestId, MiniCbor.encode(auth));
			Mst5Frame response = connection.exchange(frame, 10000);
			if (response.id != requestId || response.kind != Mst5Frame.RESULT || response.code >= 400) {
				throw new IOException("MST5 authentication failed");
			}
			connection.authenticatedToken = token;
		}
	}

	private Connection connection(Endpoint endpoint, int timeoutMs) throws Exception {
		synchronized (lock) {
			long now = System.currentTimeMillis();
			if (cached != null && (!cached.endpoint.same(endpoint) || now - cached.lastUsedAt > IDLE_RECONNECT_MS || cached.isClosed())) {
				closeCachedLocked(cached);
			}
			if (cached == null) cached = open(endpoint, timeoutMs, now);
			return cached;
		}
	}

	private void closeCached(Connection connection) {
		synchronized (lock) {
			closeCachedLocked(connection);
		}
	}

	private void closeCachedLocked(Connection connection) {
		if (connection != null && connection == cached) cached = null;
		close(connection);
	}

	private static Connection open(Endpoint endpoint, int timeoutMs, long now) throws Exception {
		Socket socket = CompatSocketConnector.connect(endpoint.host, endpoint.port, endpoint.tls, timeoutMs);
		InputStream input = socket.getInputStream();
		OutputStream output = socket.getOutputStream();
		SecureSessionV4 session = SecureSessionV4.clientV5(input, output, CryptIdentity.serverPublicKey());
		socket.setSoTimeout(0);
		Connection connection = new Connection(endpoint, socket, input, output, session, now);
		connection.startReader();
		return connection;
	}

	private static void close(Connection connection) {
		if (connection != null) {
			connection.shutdown(new IOException("MST5 connection closed"));
		}
	}

	private static boolean usePersistent(String path, int timeoutMs) {
		String value = path == null ? "" : path;
		return !value.startsWith("/file/");
	}

	private static boolean isRetryable(String method) {
		String normalized = method == null ? "GET" : method.toUpperCase(Locale.US);
		return "GET".equals(normalized);
	}

	public static final class Response {
		private final int code;
		private final Map<String, String> headers;
		private final byte[] body;

		private Response(int code, Map<String, String> headers, byte[] body) {
			this.code = code;
			this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
			this.body = body == null ? new byte[0] : body.clone();
		}

		public int code() {
			return code;
		}

		public Map<String, String> headers() {
			return headers;
		}

		public byte[] body() {
			return body.clone();
		}
	}

	private static final class Connection {
		private final Endpoint endpoint;
		private final Socket socket;
		private final InputStream input;
		private final OutputStream output;
		private final SecureSessionV4 session;
		private final Object writeLock = new Object();
		private final Object pendingLock = new Object();
		private final Object authLock = new Object();
		private final Map<Long, Pending> pending = new HashMap<Long, Pending>();
		private volatile long lastUsedAt;
		private long nextRequestId = 1;
		private volatile String authenticatedToken;
		private volatile IOException failure;
		private volatile boolean closed;

		private Connection(Endpoint endpoint, Socket socket, InputStream input, OutputStream output, SecureSessionV4 session, long lastUsedAt) {
			this.endpoint = endpoint;
			this.socket = socket;
			this.input = input;
			this.output = output;
			this.session = session;
			this.lastUsedAt = lastUsedAt;
		}

		private synchronized long nextId() {
			return nextRequestId++;
		}

		private Mst5Frame exchange(Mst5Frame frame, int timeoutMs) throws Exception {
			Pending waiter = new Pending();
			synchronized (pendingLock) {
				if (failure != null) throw failure;
				pending.put(Long.valueOf(frame.id), waiter);
			}
			try {
				synchronized (writeLock) {
					if (failure != null) throw failure;
					session.writeEncryptedFrame(output, frame.encode(false));
				}
				return waiter.await(timeoutMs);
			} finally {
				synchronized (pendingLock) {
					pending.remove(Long.valueOf(frame.id));
				}
			}
		}

		private void startReader() {
			Thread reader = new Thread(new Runnable() {
				@Override public void run() {
					try {
						while (!closed) {
							Mst5Frame frame = Mst5Frame.decode(session.readEncryptedFrame(input));
							if (frame.kind == Mst5Frame.PING) {
								synchronized (writeLock) {
									session.writeEncryptedFrame(output, new Mst5Frame(Mst5Frame.PONG, 0, frame.id, new byte[0]).encode(false));
								}
								continue;
							}
							Pending waiter;
							synchronized (pendingLock) {
								waiter = pending.get(Long.valueOf(frame.id));
							}
							if (waiter != null) waiter.complete(frame);
						}
					} catch (Exception error) {
						shutdown(error instanceof IOException ? (IOException)error : new IOException("MST5 reader failed", error));
					}
				}
			}, "mst5-reader");
			reader.setDaemon(true);
			reader.start();
		}

		private boolean isClosed() {
			return closed || socket.isClosed() || failure != null;
		}

		private void shutdown(IOException error) {
			Map<Long, Pending> waiters;
			synchronized (pendingLock) {
				if (closed && failure != null) return;
				closed = true;
				failure = error;
				waiters = new HashMap<Long, Pending>(pending);
				pending.clear();
			}
			try { socket.close(); } catch (Exception ignored) {}
			for (Pending waiter : waiters.values()) waiter.fail(error);
		}
	}

	private static final class Pending {
		private Mst5Frame response;
		private IOException error;

		private synchronized void complete(Mst5Frame value) {
			if (response == null && error == null) response = value;
			notifyAll();
		}

		private synchronized void fail(IOException value) {
			if (response == null && error == null) error = value;
			notifyAll();
		}

		private synchronized Mst5Frame await(int timeoutMs) throws IOException {
			long deadline = System.currentTimeMillis() + Math.max(1, timeoutMs);
			while (response == null && error == null) {
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0) throw new java.net.SocketTimeoutException("MST5 request timed out");
				try { wait(remaining); } catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IOException("MST5 request interrupted", interrupted);
				}
			}
			if (error != null) throw error;
			return response;
		}
	}

	private static int opcode(String method, String rawPath) throws IOException {
		String path = rawPath;
		int query = path.indexOf('?');
		if (query >= 0) path = path.substring(0, query);
		if ("POST".equals(method) && "/register".equals(path)) return OP_REGISTER;
		if ("POST".equals(method) && "/login".equals(path)) return OP_LOGIN;
		if ("POST".equals(method) && "/auth/email/start".equals(path)) return OP_EMAIL_AUTH_START;
		if ("POST".equals(method) && "/auth/email/verify".equals(path)) return OP_EMAIL_AUTH_VERIFY;
		if ("GET".equals(method) && "/me".equals(path)) return OP_ME;
		if ("POST".equals(method) && "/account/delete".equals(path)) return OP_ACCOUNT_DELETE;
		if ("POST".equals(method) && "/username".equals(path)) return OP_SET_USERNAME;
		if ("POST".equals(method) && "/name".equals(path)) return OP_SET_NAME;
		if ("POST".equals(method) && "/privacy".equals(path)) return OP_SET_PRIVACY;
		if ("GET".equals(method) && "/contacts".equals(path)) return OP_CONTACTS;
		if ("POST".equals(method) && "/contacts/add".equals(path)) return OP_CONTACT_ADD;
		if ("POST".equals(method) && "/contacts/delete".equals(path)) return OP_CONTACT_DELETE;
		if ("POST".equals(method) && "/groups".equals(path)) return OP_CREATE_GROUP;
		if ("POST".equals(method) && "/channels".equals(path)) return OP_CREATE_CHANNEL;
		if ("POST".equals(method) && "/chats/title".equals(path)) return OP_SET_CHAT_TITLE;
		if ("POST".equals(method) && "/channels/username".equals(path)) return OP_SET_CHANNEL_USERNAME;
		if ("POST".equals(method) && "/channels/comments/settings".equals(path)) return OP_SET_CHANNEL_COMMENTS;
		if ("POST".equals(method) && "/channels/comments/send".equals(path)) return OP_SEND_CHANNEL_COMMENT;
		if ("GET".equals(method) && "/channels/comments".equals(path)) return OP_CHANNEL_COMMENTS;
		if ("POST".equals(method) && "/chats/members/add".equals(path)) return OP_ADD_CHAT_MEMBER;
		if ("POST".equals(method) && "/chats/members/remove".equals(path)) return OP_REMOVE_CHAT_MEMBER;
		if ("POST".equals(method) && "/cloud-password".equals(path)) return OP_SET_CLOUD_PASSWORD;
		if ("POST".equals(method) && "/cloud-password/reset".equals(path)) return OP_RESET_CLOUD_PASSWORD;
		if ("GET".equals(method) && "/sessions".equals(path)) return OP_SESSIONS;
		if ("POST".equals(method) && "/sessions/revoke".equals(path)) return OP_REVOKE_SESSION;
		if ("POST".equals(method) && "/sessions/revoke-others".equals(path)) return OP_REVOKE_OTHER_SESSIONS;
		if ("POST".equals(method) && "/bots".equals(path)) return OP_CREATE_BOT;
		if ("POST".equals(method) && "/bots/token/reset".equals(path)) return OP_RESET_BOT_TOKEN;
		if ("POST".equals(method) && "/e2e/key".equals(path)) return OP_SET_E2E_KEY;
		if ("GET".equals(method) && "/e2e/key".equals(path)) return OP_GET_E2E_KEY;
		if ("POST".equals(method) && "/e2e/backup".equals(path)) return OP_SET_E2E_BACKUP;
		if ("GET".equals(method) && "/e2e/backup".equals(path)) return OP_GET_E2E_BACKUP;
		if ("POST".equals(method) && "/e2e/reset".equals(path)) return OP_RESET_E2E;
		if ("GET".equals(method) && "/wallet".equals(path)) return OP_WALLET;
		if ("POST".equals(method) && "/wallet/send".equals(path)) return OP_WALLET_SEND;
		if ("GET".equals(method) && "/wallet/history".equals(path)) return OP_WALLET_HISTORY;
		if ("POST".equals(method) && "/call".equals(path)) return OP_CALL;
		if ("POST".equals(method) && "/voice-ticket".equals(path)) return OP_VOICE_TICKET;
		if ("GET".equals(method) && "/voice/participants".equals(path)) return OP_VOICE_PARTICIPANTS;
		if ("POST".equals(method) && "/send".equals(path)) return OP_SEND;
		if ("POST".equals(method) && "/edit".equals(path)) return OP_EDIT;
		if ("POST".equals(method) && "/callback".equals(path)) return OP_CALLBACK;
		if ("POST".equals(method) && "/reactions".equals(path)) return OP_REACT;
		if ("POST".equals(method) && "/reactions/paid".equals(path)) return OP_REACT_PAID;
		if ("POST".equals(method) && "/read".equals(path)) return OP_READ;
		if ("POST".equals(method) && "/delete".equals(path)) return OP_DELETE;
		if ("POST".equals(method) && "/favorite".equals(path)) return OP_FAVORITE;
		if ("POST".equals(method) && "/upload/init".equals(path)) return OP_INIT_UPLOAD;
		if ("POST".equals(method) && "/upload/complete".equals(path)) return OP_COMPLETE_UPLOAD;
		if ("POST".equals(method) && "/upload".equals(path)) return OP_UPLOAD_LEGACY;
		if ("GET".equals(method) && "/file/ticket".equals(path)) return OP_FILE_TICKET;
		if ("POST".equals(method) && "/upload/quote".equals(path)) return OP_UPLOAD_QUOTE;
		if ("POST".equals(method) && "/upload/authorize".equals(path)) return OP_UPLOAD_AUTHORIZE;
		if ("POST".equals(method) && "/upload/cancel".equals(path)) return OP_UPLOAD_CANCEL;
		if ("POST".equals(method) && "/forward".equals(path)) return OP_FORWARD;
		if ("GET".equals(method) && "/nodes/status".equals(path)) return OP_NODES_STATUS;
		if ("GET".equals(method) && "/chats".equals(path)) return OP_CHATS;
		if ("POST".equals(method) && "/chats/delete".equals(path)) return OP_DELETE_CHAT;
		if ("POST".equals(method) && "/users/ban".equals(path)) return OP_BAN_USER;
		if ("POST".equals(method) && "/users/unban".equals(path)) return OP_UNBAN_USER;
		if ("GET".equals(method) && "/history".equals(path)) return OP_HISTORY;
		if ("GET".equals(method) && "/updates".equals(path)) return OP_SYNC;
		if ("GET".equals(method) && "/oauth/device/request".equals(path)) return OP_OAUTH_DEVICE_REQUEST;
		if ("POST".equals(method) && "/oauth/device/decision".equals(path)) return OP_OAUTH_DEVICE_DECISION;
		throw new IOException("unsupported MST5 operation " + method + " " + path);
	}

	private static final class Endpoint {
		private final String host;
		private final int port;
		private final boolean tls;

		private Endpoint(String host, int port, boolean tls) {
			this.host = host;
			this.port = port;
			this.tls = tls;
		}

		private boolean same(Endpoint other) {
			return other != null && port == other.port && tls == other.tls && host.equalsIgnoreCase(other.host);
		}

		private static Endpoint parse(String raw) throws Exception {
			String value = raw == null || raw.trim().length() == 0 ? "127.0.0.1:8080" : raw.trim();
			if (value.indexOf("://") < 0) {
				value = "tcp" + "://" + value;
			}
			URI uri = URI.create(value);
			String host = uri.getHost();
			if (host == null || host.length() == 0) {
				throw new IllegalArgumentException("server host is required");
			}
			int port = uri.getPort();
			String scheme = uri.getScheme();
			boolean tls = "https".equalsIgnoreCase(scheme) || "tcps".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
			if (port < 0) {
				port = tls ? 443 : 80;
			}
			return new Endpoint(host, port, tls);
		}
	}
}
