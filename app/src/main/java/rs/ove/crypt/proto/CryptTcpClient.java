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

public final class CryptTcpClient {
	private static final long IDLE_RECONNECT_MS = 90000L;

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
		synchronized (lock) {
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
			} catch (Exception ex) {
				closeCached(connection);
				throw ex;
			}
		}
	}

	private Response request(Connection connection, String token, String method, String path, byte[] body, int timeoutMs) throws Exception {
		connection.socket.setSoTimeout(timeoutMs);
		JSONObject request = new JSONObject();
		request.put("method", method == null ? "GET" : method.toUpperCase(Locale.US));
		request.put("path", path == null || path.length() == 0 ? "/" : path);
		if (token != null && token.length() > 0) {
			request.put("token", token);
		}
		if (body != null) {
			request.put("body", Base64Codec.encode(body));
		}
		connection.session.writeEncryptedFrame(connection.output, request.toString().getBytes("UTF-8"));
		byte[] responseBytes = connection.session.readEncryptedFrame(connection.input);
		connection.lastUsedAt = System.currentTimeMillis();
		JSONObject response = new JSONObject(new String(responseBytes, "UTF-8"));
		Map<String, String> headers = new LinkedHashMap<String, String>();
		JSONObject rawHeaders = response.optJSONObject("headers");
		if (rawHeaders != null) {
			java.util.Iterator keys = rawHeaders.keys();
			while (keys.hasNext()) {
				String key = String.valueOf(keys.next());
				headers.put(key, rawHeaders.optString(key));
			}
		}
		return new Response(response.optInt("code", 500), headers, Base64Codec.decode(response.optString("body", "")));
	}

	private Connection connection(Endpoint endpoint, int timeoutMs) throws Exception {
		long now = System.currentTimeMillis();
		if (cached != null && (!cached.endpoint.same(endpoint) || now - cached.lastUsedAt > IDLE_RECONNECT_MS || cached.socket.isClosed())) {
			closeCached(cached);
		}
		if (cached == null) {
			cached = open(endpoint, timeoutMs, now);
		}
		return cached;
	}

	private void closeCached(Connection connection) {
		if (connection != null && connection == cached) {
			cached = null;
		}
		close(connection);
	}

	private static Connection open(Endpoint endpoint, int timeoutMs, long now) throws Exception {
		Socket socket = CompatSocketConnector.connect(endpoint.host, endpoint.port, endpoint.tls, timeoutMs);
		InputStream input = socket.getInputStream();
		OutputStream output = socket.getOutputStream();
		CryptSession session = CryptSession.client(input, output);
		return new Connection(endpoint, socket, input, output, session, now);
	}

	private static void close(Connection connection) {
		if (connection != null) {
			try {
				connection.socket.close();
			} catch (Exception ignored) {
			}
		}
	}

	private static boolean usePersistent(String path, int timeoutMs) {
		String value = path == null ? "" : path;
		return timeoutMs <= 15000 && !value.startsWith("/updates") && !value.startsWith("/file/");
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
		private final CryptSession session;
		private long lastUsedAt;

		private Connection(Endpoint endpoint, Socket socket, InputStream input, OutputStream output, CryptSession session, long lastUsedAt) {
			this.endpoint = endpoint;
			this.socket = socket;
			this.input = input;
			this.output = output;
			this.session = session;
			this.lastUsedAt = lastUsedAt;
		}
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
