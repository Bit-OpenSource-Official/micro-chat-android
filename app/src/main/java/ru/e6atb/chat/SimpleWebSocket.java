package ru.e6atb.chat;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import rs.ove.crypt.proto.CompatSocketConnector;
import rs.ove.crypt.proto.Base64Codec;

final class SimpleWebSocket implements Closeable {
	static final int TEXT = 1;
	static final int BINARY = 2;
	static final int CLOSE = 8;
	static final int PING = 9;
	static final int PONG = 10;

	private static final int CONNECT_TIMEOUT_MS = 30000;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final Random maskRandom = new Random(secureSeed());
	private Socket socket;
	private InputStream in;
	private OutputStream out;

	void connect(String rawUrl) throws Exception {
		URI uri = URI.create(rawUrl);
		String scheme = uri.getScheme();
		boolean tls = "wss".equalsIgnoreCase(scheme);
		if (!tls) {
			throw new IOException("TLS is required; use a wss:// websocket URL");
		}
		String host = uri.getHost();
		if (host == null || host.length() == 0) {
			throw new IOException("websocket host is required");
		}
		int port = uri.getPort();
		if (port < 0) {
			port = tls ? 443 : 80;
		}
		socket = CompatSocketConnector.connect(host, port, tls, CONNECT_TIMEOUT_MS);
		if (!(socket instanceof SSLSocket)) {
			throw new IOException("TLS websocket connection was not established");
		}
		SSLSession session = ((SSLSocket)socket).getSession();
		if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)) {
			try { socket.close(); } catch (Exception ignored) {}
			throw new IOException("TLS hostname verification failed for " + host);
		}
		in = socket.getInputStream();
		out = socket.getOutputStream();

		byte[] nonce = new byte[16];
		synchronized (SECURE_RANDOM) {
			SECURE_RANDOM.nextBytes(nonce);
		}
		String key = Base64Codec.encode(nonce);
		String path = uri.getRawPath();
		if (path == null || path.length() == 0) {
			path = "/";
		}
		if (uri.getRawQuery() != null) {
			path += "?" + uri.getRawQuery();
		}

		String hostHeader = host;
		if (hostHeader.indexOf(':') >= 0 && !hostHeader.startsWith("[")) {
			hostHeader = "[" + hostHeader + "]";
		}
		if (uri.getPort() >= 0) {
			hostHeader += ":" + uri.getPort();
		}
		String req = "GET " + path + " HTTP/1.1\r\n"
			+ "Host: " + hostHeader + "\r\n"
			+ "Upgrade: websocket\r\n"
			+ "Connection: Upgrade\r\n"
			+ "Sec-WebSocket-Version: 13\r\n"
			+ "Sec-WebSocket-Key: " + key + "\r\n\r\n";
		out.write(req.getBytes("US-ASCII"));
		out.flush();

		String response = readHeader();
		if (!response.startsWith("HTTP/1.1 101") && !response.startsWith("HTTP/1.0 101")) {
			int end = response.indexOf("\r\n");
			String status = end > 0 ? response.substring(0, end) : response.trim();
			throw new IOException("websocket upgrade failed: " + status);
		}
		String expectedAccept = websocketAccept(key);
		String actualAccept = headerValue(response, "Sec-WebSocket-Accept");
		if (!expectedAccept.equals(actualAccept)) {
			throw new IOException("websocket upgrade returned an invalid accept key");
		}
		// Voice frames can legitimately be silent for longer than the connection
		// timeout. Keep the timeout for connect/upgrade only.
		socket.setSoTimeout(0);
	}

	private static String websocketAccept(String key) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
		return Base64Codec.encode(digest.digest(
				(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("US-ASCII")
		));
	}

	private static String headerValue(String headers, String name) {
		String[] lines = headers.split("\\r\\n");
		for (int i = 1; i < lines.length; i++) {
			int colon = lines[i].indexOf(':');
			if (colon > 0 && name.equalsIgnoreCase(lines[i].substring(0, colon).trim())) {
				return lines[i].substring(colon + 1).trim();
			}
		}
		return "";
	}

	synchronized void sendBinary(byte[] payload, int length) throws IOException {
		writeFrame(BINARY, payload, length);
	}

	Frame readFrame() throws IOException {
		for (;;) {
			Frame f = readOneFrame();
			if (f.opcode == PING) {
				synchronized (this) {
					writeFrame(PONG, f.payload, f.payload.length);
				}
				continue;
			}
			if (f.opcode == PONG || f.opcode == TEXT) {
				continue;
			}
			return f;
		}
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			if (out != null) {
				writeFrame(CLOSE, new byte[0], 0);
			}
		} catch (Exception ignored) {
		}
		if (socket != null) {
			socket.close();
		}
	}

	private String readHeader() throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		int state = 0;
		for (;;) {
			int c = in.read();
			if (c < 0) {
				throw new EOFException();
			}
			b.write(c);
			if ((state == 0 || state == 2) && c == '\r') {
				state++;
			} else if ((state == 1 || state == 3) && c == '\n') {
				state++;
			} else {
				state = 0;
			}
			if (state == 4) {
				return b.toString("US-ASCII");
			}
			if (b.size() > 8192) {
				throw new IOException("websocket header too large");
			}
		}
	}

	private Frame readOneFrame() throws IOException {
		int b0 = in.read();
		int b1 = in.read();
		if (b0 < 0 || b1 < 0) {
			throw new EOFException();
		}
		int opcode = b0 & 0x0f;
		boolean masked = (b1 & 0x80) != 0;
		long length = b1 & 0x7f;
		if (length == 126) {
			length = ((long)readByte() << 8) | readByte();
		} else if (length == 127) {
			length = 0;
			for (int i = 0; i < 8; i++) {
				length = (length << 8) | readByte();
			}
		}
		if (length > 1 << 20) {
			throw new IOException("websocket frame too large");
		}
		byte[] mask = new byte[4];
		if (masked) {
			readFully(mask, 0, mask.length);
		}
		byte[] payload = new byte[(int)length];
		readFully(payload, 0, payload.length);
		if (masked) {
			for (int i = 0; i < payload.length; i++) {
				payload[i] ^= mask[i % 4];
			}
		}
		return new Frame(opcode, payload);
	}

	private void writeFrame(int opcode, byte[] payload, int length) throws IOException {
		if (payload == null) {
			payload = new byte[0];
		}
		if (length < 0 || length > payload.length) {
			throw new IOException("bad payload length");
		}
		if (length > 1 << 20) {
			throw new IOException("websocket frame too large");
		}
		int headerLength = length < 126 ? 6 : (length <= 0xffff ? 8 : 14);
		byte[] frame = new byte[headerLength + length];
		int offset = 0;
		frame[offset++] = (byte)(0x80 | opcode);
		if (length < 126) {
			frame[offset++] = (byte)(0x80 | length);
		} else if (length <= 0xffff) {
			frame[offset++] = (byte)(0x80 | 126);
			frame[offset++] = (byte)((length >>> 8) & 0xff);
			frame[offset++] = (byte)(length & 0xff);
		} else {
			frame[offset++] = (byte)(0x80 | 127);
			long n = length;
			for (int i = 7; i >= 0; i--) {
				frame[offset++] = (byte)((n >>> (8 * i)) & 0xff);
			}
		}
		int mask = maskRandom.nextInt();
		frame[offset++] = (byte)((mask >>> 24) & 0xff);
		frame[offset++] = (byte)((mask >>> 16) & 0xff);
		frame[offset++] = (byte)((mask >>> 8) & 0xff);
		frame[offset++] = (byte)(mask & 0xff);
		for (int i = 0; i < length; i++) {
			frame[offset + i] = (byte)(payload[i] ^ frame[headerLength - 4 + (i & 3)]);
		}
		out.write(frame);
		out.flush();
	}

	private static long secureSeed() {
		byte[] seed = new byte[8];
		synchronized (SECURE_RANDOM) {
			SECURE_RANDOM.nextBytes(seed);
		}
		long value = 0;
		for (int i = 0; i < seed.length; i++) {
			value = (value << 8) | (seed[i] & 0xffL);
		}
		return value ^ System.nanoTime();
	}

	private int readByte() throws IOException {
		int b = in.read();
		if (b < 0) {
			throw new EOFException();
		}
		return b;
	}

	private void readFully(byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int n = in.read(b, off, len);
			if (n < 0) {
				throw new EOFException();
			}
			off += n;
			len -= n;
		}
	}

	static final class Frame {
		final int opcode;
		final byte[] payload;

		Frame(int opcode, byte[] payload) {
			this.opcode = opcode;
			this.payload = payload;
		}
	}
}
