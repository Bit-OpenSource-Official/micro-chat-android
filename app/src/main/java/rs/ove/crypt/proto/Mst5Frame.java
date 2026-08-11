package rs.ove.crypt.proto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class Mst5Frame {
	static final int AUTH = 1;
	static final int COMMAND = 2;
	static final int QUERY = 3;
	static final int RESULT = 4;
	static final int EVENT_BATCH = 5;
	static final int ACK = 6;
	static final int ERROR = 7;
	static final int PING = 8;
	static final int PONG = 9;
	static final int HELLO = 11;
	static final int CANCEL = 12;
	static final int KEY_UPDATE = 13;
	static final int STREAM_OPEN = 14;
	static final int STREAM_DATA = 15;
	static final int STREAM_END = 16;
	static final int STREAM_ABORT = 17;

	private static final int HEADER_LENGTH = 40;
	private static final int FLAG_DEFLATE = 1;
	private static final int MAX_PAYLOAD = 4 * 1024 * 1024;
	private static final int COMPRESSION_THRESHOLD = 1024;
	private static final int MAX_COMPRESSION_RATIO = 64;

	final int kind;
	final int flags;
	final int code;
	final long id;
	final byte[] requestNonce;
	final long deadlineMs;
	final byte[] payload;

	Mst5Frame(int kind, int code, long id, byte[] payload) throws IOException {
		this(kind, 0, code, id, new byte[16], 0L, payload);
	}

	Mst5Frame(int kind, int code, long id, byte[] requestNonce, long deadlineMs, byte[] payload) throws IOException {
		this(kind, 0, code, id, requestNonce, deadlineMs, payload);
	}

	private Mst5Frame(int kind, int flags, int code, long id, byte[] requestNonce, long deadlineMs, byte[] payload) throws IOException {
		if (!validKind(kind)) throw new IOException("invalid MST5 frame kind");
		if (code < 0 || code > 0xffff) throw new IOException("invalid MST5 code");
		if (requestNonce == null || requestNonce.length != 16) throw new IOException("invalid MST5 request nonce");
		if (payload == null) payload = new byte[0];
		if (payload.length > MAX_PAYLOAD) throw new IOException("MST5 payload is too large");
		this.kind = kind;
		this.flags = flags;
		this.code = code;
		this.id = id;
		this.requestNonce = requestNonce.clone();
		this.deadlineMs = deadlineMs;
		this.payload = payload.clone();
	}

	byte[] encode(boolean allowCompression) throws IOException {
		byte[] body = payload;
		int outputFlags = flags;
		if (allowCompression && compressible(kind) && body.length >= COMPRESSION_THRESHOLD) {
			byte[] candidate = deflate(body);
			if ((long)candidate.length * 8L <= (long)body.length * 7L
					&& (long)body.length <= (long)candidate.length * MAX_COMPRESSION_RATIO) {
				body = candidate;
				outputFlags |= FLAG_DEFLATE;
			}
		}
		byte[] out = new byte[HEADER_LENGTH + body.length];
		out[0] = (byte)kind;
		out[1] = (byte)outputFlags;
		out[2] = (byte)(code >>> 8);
		out[3] = (byte)code;
		writeLong(out, 4, id);
		System.arraycopy(requestNonce, 0, out, 12, requestNonce.length);
		writeLong(out, 28, deadlineMs);
		writeInt(out, 36, body.length);
		System.arraycopy(body, 0, out, HEADER_LENGTH, body.length);
		return out;
	}

	static Mst5Frame decode(byte[] input) throws IOException {
		if (input == null || input.length < HEADER_LENGTH) throw new IOException("short MST5 frame");
		int kind = input[0] & 0xff;
		int flags = input[1] & 0xff;
		if (!validKind(kind) || (flags & ~FLAG_DEFLATE) != 0) {
			throw new IOException("invalid MST5 frame header");
		}
		if ((flags & FLAG_DEFLATE) != 0 && !compressible(kind)) {
			throw new IOException("compressed MST5 control frame");
		}
		int code = ((input[2] & 0xff) << 8) | (input[3] & 0xff);
		long id = readLong(input, 4);
		byte[] requestNonce = new byte[16];
		System.arraycopy(input, 12, requestNonce, 0, requestNonce.length);
		long deadlineMs = readLong(input, 28);
		int length = readInt(input, 36);
		if (length < 0 || length > MAX_PAYLOAD || input.length != HEADER_LENGTH + length) {
			throw new IOException("invalid MST5 payload length");
		}
		byte[] body = new byte[length];
		System.arraycopy(input, HEADER_LENGTH, body, 0, length);
		if ((flags & FLAG_DEFLATE) != 0) body = inflate(body);
		return new Mst5Frame(kind, flags, code, id, requestNonce, deadlineMs, body);
	}

	private static boolean validKind(int kind) {
		return (kind >= AUTH && kind <= PONG) || (kind >= HELLO && kind <= STREAM_ABORT);
	}

	private static boolean compressible(int kind) {
		return kind == RESULT || kind == EVENT_BATCH;
	}

	private static byte[] deflate(byte[] input) throws IOException {
		Deflater deflater = new Deflater(Deflater.BEST_SPEED, true);
		try {
			deflater.setInput(input);
			deflater.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 2);
			byte[] buffer = new byte[4096];
			while (!deflater.finished()) {
				int count = deflater.deflate(buffer);
				if (count <= 0 && deflater.needsInput()) break;
				out.write(buffer, 0, count);
			}
			return out.toByteArray();
		} finally {
			deflater.end();
		}
	}

	private static byte[] inflate(byte[] input) throws IOException {
		Inflater inflater = new Inflater(true);
		try {
			inflater.setInput(input);
			ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(MAX_PAYLOAD, input.length * 4));
			byte[] buffer = new byte[4096];
			while (!inflater.finished()) {
				int count;
				try {
					count = inflater.inflate(buffer);
				} catch (DataFormatException error) {
					throw new IOException("invalid MST5 DEFLATE payload", error);
				}
				if (count == 0) {
					if (inflater.needsDictionary() || inflater.needsInput()) break;
					throw new IOException("stalled MST5 inflater");
				}
				if (out.size() + count > MAX_PAYLOAD) throw new IOException("inflated MST5 payload is too large");
				out.write(buffer, 0, count);
			}
			if (!inflater.finished()) throw new IOException("truncated MST5 DEFLATE payload");
			if (input.length > 0 && out.size() > input.length * (long)MAX_COMPRESSION_RATIO) throw new IOException("MST5 compression ratio exceeds limit");
			return out.toByteArray();
		} finally {
			inflater.end();
		}
	}

	private static int readInt(byte[] input, int offset) {
		return ((input[offset] & 0xff) << 24) | ((input[offset + 1] & 0xff) << 16)
				| ((input[offset + 2] & 0xff) << 8) | (input[offset + 3] & 0xff);
	}

	private static void writeInt(byte[] output, int offset, int value) {
		output[offset] = (byte)(value >>> 24);
		output[offset + 1] = (byte)(value >>> 16);
		output[offset + 2] = (byte)(value >>> 8);
		output[offset + 3] = (byte)value;
	}

	private static long readLong(byte[] input, int offset) {
		long value = 0;
		for (int i = 0; i < 8; i++) value = (value << 8) | (input[offset + i] & 0xffL);
		return value;
	}

	private static void writeLong(byte[] output, int offset, long value) {
		for (int i = 7; i >= 0; i--) {
			output[offset + i] = (byte)value;
			value >>>= 8;
		}
	}
}
