package ru.e6atb.chat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/** Standard HTTPS transport for all client-to-server API requests. */
final class TlsHttpClient {
	private static final int DEFAULT_TIMEOUT_MS = 10000;
	private final String baseUrl;

	TlsHttpClient(String baseUrl) {
		this.baseUrl = normalizeBaseUrl(baseUrl);
	}

	Response request(String token, String method, String path, byte[] body, int timeoutMs) throws IOException {
		int timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
		String requestPath = path == null || path.length() == 0 ? "/" : path;
		if (!requestPath.startsWith("/")) {
			throw new IOException("request path must start with /");
		}
		HttpURLConnection raw = (HttpURLConnection)new URL(baseUrl + requestPath).openConnection();
		if (!(raw instanceof HttpsURLConnection)) {
			raw.disconnect();
			throw new IOException("TLS is required for server connections");
		}
		HttpsURLConnection connection = (HttpsURLConnection)raw;
		try {
			connection.setConnectTimeout(Math.min(timeout, DEFAULT_TIMEOUT_MS));
			connection.setReadTimeout(timeout);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod(method == null ? "GET" : method.toUpperCase(Locale.US));
			connection.setRequestProperty("Accept", "application/json");
			if (token != null && token.length() > 0) {
				connection.setRequestProperty("Authorization", "Bearer " + token);
			}
			if (body != null) {
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
				connection.setFixedLengthStreamingMode(body.length);
				OutputStream output = connection.getOutputStream();
				try {
					output.write(body);
					output.flush();
				} finally {
					output.close();
				}
			}
			int code = connection.getResponseCode();
			InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
			byte[] responseBody = input == null ? new byte[0] : readAll(input);
			return new Response(code, responseHeaders(connection), responseBody);
		} finally {
			connection.disconnect();
		}
	}

	static String normalizeBaseUrl(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.length() == 0) {
			value = "127.0.0.1:8080";
		}
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		int schemeEnd = value.indexOf("://");
		if (schemeEnd < 0) {
			value = "https://" + value;
		} else {
			String scheme = value.substring(0, schemeEnd).toLowerCase(Locale.US);
			if ("tcps".equals(scheme) || "wss".equals(scheme)) {
				value = "https" + value.substring(schemeEnd);
			} else if (!"https".equals(scheme)) {
				throw new IllegalArgumentException("TLS is required; use an https:// server address");
			}
		}
		URI uri = URI.create(value);
		if (uri.getHost() == null || uri.getHost().length() == 0) {
			throw new IllegalArgumentException("server host is required");
		}
		if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
			throw new IllegalArgumentException("server address must not contain query or fragment");
		}
		return value;
	}

	private static Map<String, String> responseHeaders(HttpURLConnection connection) {
		LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
		Map<String, List<String>> raw = connection.getHeaderFields();
		if (raw == null) {
			return headers;
		}
		for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
			List<String> values = entry.getValue();
			if (entry.getKey() != null && values != null && !values.isEmpty()) {
				headers.put(entry.getKey(), values.get(0));
			}
		}
		return headers;
	}

	private static byte[] readAll(InputStream input) throws IOException {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			for (;;) {
				int count = input.read(buffer);
				if (count < 0) break;
				if (count > 0) output.write(buffer, 0, count);
			}
			return output.toByteArray();
		} finally {
			input.close();
		}
	}

	static final class Response {
		private final int code;
		private final Map<String, String> headers;
		private final byte[] body;

		private Response(int code, Map<String, String> headers, byte[] body) {
			this.code = code;
			this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
			this.body = body == null ? new byte[0] : body.clone();
		}

		int code() { return code; }
		Map<String, String> headers() { return headers; }
		byte[] body() { return body.clone(); }
	}
}
