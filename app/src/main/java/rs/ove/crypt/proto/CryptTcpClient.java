package rs.ove.crypt.proto;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;

/** Thin Android facade; the complete MST5 implementation lives in the Rust mst5-client crate. */
public final class CryptTcpClient {
	private static final Object ACCOUNTS_LOCK = new Object();
	private static final Map<String, SharedAccount> ACCOUNTS = new HashMap<String, SharedAccount>();
	private SharedAccount account;

	private static final class SharedAccount {
		final String endpoint;
		final long handle;
		int references;

		SharedAccount(String endpoint, long handle) {
			this.endpoint = endpoint;
			this.handle = handle;
		}
	}

	public Response request(String baseUrl, String token, String method, String path,
	                        Object body, int timeoutMs) throws Exception {
		long connection = connection(baseUrl);
		NativeMst5.Response response = NativeMst5.request(
				connection, token, method, path, timeoutMs, body);
		return new Response(response.code, Collections.<String, String>emptyMap(), response.payload);
	}

	public void close() {
		synchronized (ACCOUNTS_LOCK) {
			if (account == null) return;
			account.references--;
			if (account.references <= 0) {
				ACCOUNTS.remove(account.endpoint);
				NativeMst5.close(account.handle);
			}
			account = null;
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try { close(); }
		finally { super.finalize(); }
	}

	private long connection(String rawEndpoint) throws IOException {
		String value = rawEndpoint == null ? "" : rawEndpoint.trim();
		synchronized (ACCOUNTS_LOCK) {
			if (account != null && account.endpoint.equals(value)) return account.handle;
			close();
			SharedAccount shared = ACCOUNTS.get(value);
			if (shared == null) {
				shared = new SharedAccount(value, NativeMst5.open(value, CryptIdentity.serverPublicKeyBase64()));
				ACCOUNTS.put(value, shared);
			}
			shared.references++;
			account = shared;
			return shared.handle;
		}
	}

	public static final class Response {
		private final int code;
		private final Map<String, String> headers;
		private final Object body;

		private Response(int code, Map<String, String> headers, Object body) {
			this.code = code;
			this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
			this.body = body;
		}

		public int code() { return code; }
		public Map<String, String> headers() { return headers; }
		public Object body() { return body; }
	}
}
