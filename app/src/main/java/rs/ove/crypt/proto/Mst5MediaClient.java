package rs.ove.crypt.proto;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.security.MessageDigest;

/** One-file-per-connection MST5.1 media streaming client. */
public final class Mst5MediaClient {
	private static final long FEATURE_STRUCTURED_ERRORS = 1L << 1;
	private static final long FEATURE_MEDIA_STREAMS = 1L << 6;
	private static final long FEATURES = (1L << 7) - 1L;
	private static final int OP_UPLOAD = 1;
	private static final int OP_DOWNLOAD = 2;
	private static final int CHUNK_SIZE = 256 * 1024;

	public interface Observer {
		boolean isCancelled();
		void onConnected(Socket socket, InputStream source);
		void onProgress(long completed, long total);
		void onClosed();
	}

	private Mst5MediaClient() {}

	public static void upload(String endpoint, String publicKeyB64, String ticket, String fileId,
	                          long size, InputStream source, Observer observer) throws Exception {
		Connection connection = connect(endpoint, publicKeyB64, ticket, source, observer);
		try {
			long id = 2L;
			JSONObject open = new JSONObject();
			open.put("file_id", fileId);
			open.put("size", size);
			connection.write(new Mst5Frame(Mst5Frame.STREAM_OPEN, OP_UPLOAD, id, MiniCbor.encode(open)));
			Mst5Frame accepted = connection.read();
			if (accepted.id != id || accepted.kind != Mst5Frame.ACK || accepted.code != 100) {
				throw new IOException("media upload rejected");
			}
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[CHUNK_SIZE];
			long sent = 0L;
			while (sent < size) {
				if (observer != null && observer.isCancelled()) throw new IOException("upload cancelled");
				int wanted = (int)Math.min(buffer.length, size - sent);
				int count = source.read(buffer, 0, wanted);
				if (count < 0) throw new IOException("file size changed");
				byte[] chunk = new byte[count];
				System.arraycopy(buffer, 0, chunk, 0, count);
				digest.update(chunk);
				connection.write(new Mst5Frame(Mst5Frame.STREAM_DATA, 0, id, chunk));
				sent += count;
				if (observer != null) observer.onProgress(sent, size);
			}
			if (source.read() >= 0) throw new IOException("file size changed");
			JSONObject end = new JSONObject();
			end.put("size", sent);
			end.put("sha256", hex(digest.digest()));
			connection.write(new Mst5Frame(Mst5Frame.STREAM_END, 200, id, MiniCbor.encode(end)));
			Mst5Frame result = connection.read();
			if (result.id != id || result.kind != Mst5Frame.RESULT || result.code != 200) {
				throw new IOException("media upload failed");
			}
		} catch (Exception error) {
			try { connection.write(new Mst5Frame(Mst5Frame.STREAM_ABORT, 400, 2L, new byte[0])); }
			catch (Exception ignored) {}
			throw error;
		} finally {
			connection.close(observer);
		}
	}

	public static long download(String endpoint, String publicKeyB64, String ticket, String fileId,
	                            long expectedSize, OutputStream target, Observer observer) throws Exception {
		Connection connection = connect(endpoint, publicKeyB64, ticket, null, observer);
		try {
			long id = 2L;
			JSONObject open = new JSONObject();
			open.put("file_id", fileId);
			connection.write(new Mst5Frame(Mst5Frame.STREAM_OPEN, OP_DOWNLOAD, id, MiniCbor.encode(open)));
			Mst5Frame accepted = connection.read();
			if (accepted.id != id || accepted.kind != Mst5Frame.ACK || accepted.code != 100) {
				throw new IOException("media download rejected");
			}
			JSONObject metadata = asObject(MiniCbor.decode(accepted.payload));
			long announced = metadata.optLong("size", -1L);
			if (expectedSize >= 0 && announced != expectedSize) throw new IOException("media size changed");
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			long received = 0L;
			while (true) {
				if (observer != null && observer.isCancelled()) throw new IOException("download cancelled");
				Mst5Frame frame = connection.read();
				if (frame.id != id) throw new IOException("media stream id mismatch");
				if (frame.kind == Mst5Frame.STREAM_DATA) {
					if (frame.payload.length == 0 || frame.payload.length > CHUNK_SIZE) throw new IOException("invalid media chunk");
					target.write(frame.payload);
					digest.update(frame.payload);
					received += frame.payload.length;
					if (received > announced) throw new IOException("media exceeds announced size");
					if (observer != null) observer.onProgress(received, announced);
				} else if (frame.kind == Mst5Frame.STREAM_END) {
					JSONObject end = asObject(MiniCbor.decode(frame.payload));
					if (received != announced || end.optLong("size", -1L) != received
							|| !hex(digest.digest()).equals(end.optString("sha256"))) {
						throw new IOException("media download checksum mismatch");
					}
					target.flush();
					return received;
				} else {
					throw new IOException("unexpected media download frame");
				}
			}
		} finally {
			connection.close(observer);
		}
	}

	public static byte[] downloadBytes(String endpoint, String publicKeyB64, String ticket,
	                                   String fileId, long expectedSize, int maxBytes) throws Exception {
		if (expectedSize < 0 || expectedSize > Integer.MAX_VALUE || (maxBytes > 0 && expectedSize > maxBytes)) {
			throw new IOException("file is too large");
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream((int)expectedSize);
		download(endpoint, publicKeyB64, ticket, fileId, expectedSize, output, null);
		return output.toByteArray();
	}

	private static Connection connect(String endpoint, String publicKeyB64, String ticket,
	                                  InputStream source, Observer observer) throws Exception {
		Endpoint parsed = Endpoint.parse(endpoint);
		Socket socket = CompatSocketConnector.connect(parsed.host, parsed.port, false, 10000);
		socket.setSoTimeout(30000);
		if (observer != null) observer.onConnected(socket, source);
		InputStream input = socket.getInputStream();
		OutputStream output = socket.getOutputStream();
		SecureSession session = SecureSession.client(input, output, CryptIdentity.decodePublicKey(publicKeyB64));
		Connection connection = new Connection(socket, input, output, session);
		JSONObject hello = new JSONObject();
		hello.put("transport_major", 5);
		hello.put("rpc_major", 1);
		hello.put("rpc_minor", 1);
		hello.put("features", FEATURES);
		hello.put("required_features", FEATURE_STRUCTURED_ERRORS | FEATURE_MEDIA_STREAMS);
		hello.put("max_frame", 4 * 1024 * 1024);
		connection.write(new Mst5Frame(Mst5Frame.HELLO, 0, 0L, MiniCbor.encode(hello)));
		Mst5Frame helloResponse = connection.read();
		JSONObject serverHello = asObject(MiniCbor.decode(helloResponse.payload));
		if (helloResponse.kind != Mst5Frame.HELLO || helloResponse.code != 200
				|| (serverHello.optLong("features") & FEATURE_MEDIA_STREAMS) == 0) {
			throw new IOException("media node did not negotiate MEDIA_STREAMS");
		}
		JSONObject auth = new JSONObject();
		auth.put("mechanism", "media_ticket");
		auth.put("ticket", ticket);
		connection.write(new Mst5Frame(Mst5Frame.AUTH, 0, 1L, MiniCbor.encode(auth)));
		Mst5Frame authResponse = connection.read();
		if (authResponse.id != 1L || authResponse.kind != Mst5Frame.RESULT || authResponse.code != 200) {
			throw new IOException("media node authentication failed");
		}
		return connection;
	}

	private static JSONObject asObject(Object value) throws Exception {
		if (value instanceof JSONObject) return (JSONObject)value;
		throw new IOException("invalid media CBOR object");
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			int b = value & 0xff;
			if (b < 16) out.append('0');
			out.append(Integer.toHexString(b));
		}
		return out.toString();
	}

	private static final class Connection {
		final Socket socket;
		final InputStream input;
		final OutputStream output;
		final SecureSession session;
		Connection(Socket socket, InputStream input, OutputStream output, SecureSession session) {
			this.socket = socket; this.input = input; this.output = output; this.session = session;
		}
		void write(Mst5Frame frame) throws Exception { session.writeEncryptedFrame(output, frame.encode(false)); }
		Mst5Frame read() throws Exception { return Mst5Frame.decode(session.readEncryptedFrame(input)); }
		void close(Observer observer) {
			try { socket.close(); } catch (Exception ignored) {}
			if (observer != null) observer.onClosed();
		}
	}

	private static final class Endpoint {
		final String host; final int port;
		Endpoint(String host, int port) { this.host = host; this.port = port; }
		static Endpoint parse(String raw) {
			String value = raw == null ? "" : raw.trim();
			if (value.indexOf("://") < 0) value = "tcp://" + value;
			URI uri = URI.create(value);
			if (!"tcp".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1) {
				throw new IllegalArgumentException("media endpoint must be tcp://host:port");
			}
			return new Endpoint(uri.getHost(), uri.getPort());
		}
	}
}
