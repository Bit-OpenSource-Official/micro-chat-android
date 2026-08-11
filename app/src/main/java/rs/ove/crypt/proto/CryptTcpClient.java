package rs.ove.crypt.proto;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thin Android facade; the complete MST5 implementation lives in the Rust mst5-client crate. */
public final class CryptTcpClient {
	private final Object lock = new Object();
	private long handle;
	private String endpoint = "";

	public Response request(String baseUrl, String token, String method, String path,
	                        byte[] body, int timeoutMs) throws Exception {
		long connection = connection(baseUrl);
		try {
			NativeMst5.Response response = NativeMst5.request(
					connection, token, method, path, timeoutMs, body);
			return new Response(response.code, Collections.<String, String>emptyMap(), response.payload);
		} catch (IOException error) {
			close(connection);
			throw error;
		}
	}

	public void close() {
		synchronized (lock) {
			NativeMst5.close(handle);
			handle = 0;
			endpoint = "";
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try { close(); }
		finally { super.finalize(); }
	}

	private long connection(String rawEndpoint) throws IOException {
		String value = rawEndpoint == null ? "" : rawEndpoint.trim();
		synchronized (lock) {
			if (handle != 0 && !endpoint.equals(value)) {
				NativeMst5.close(handle);
				handle = 0;
			}
			if (handle == 0) {
				handle = NativeMst5.open(value, CryptIdentity.serverPublicKeyBase64());
				endpoint = value;
			}
			return handle;
		}
	}

	private void close(long expected) {
		synchronized (lock) {
			if (handle != expected) return;
			NativeMst5.close(handle);
			handle = 0;
			endpoint = "";
		}
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

		public int code() { return code; }
		public Map<String, String> headers() { return headers; }
		public byte[] body() { return body.clone(); }
	}
}
