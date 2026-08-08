package rs.ove.crypt.proto;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/** Bounded CBOR subset used by MST5 and available on Android API 10. */
final class MiniCbor {
	private static final int MAX_BYTES = 4 * 1024 * 1024;
	private static final int MAX_DEPTH = 64;

	private MiniCbor() {
	}

	static byte[] encode(Object value) throws IOException, JSONException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeValue(out, value, 0);
		if (out.size() > MAX_BYTES) throw new IOException("CBOR payload is too large");
		return out.toByteArray();
	}

	static Object decode(byte[] input) throws IOException, JSONException {
		if (input == null || input.length > MAX_BYTES) throw new IOException("CBOR payload is too large");
		Reader reader = new Reader(input);
		Object value = reader.readValue(0);
		if (!reader.done()) throw new IOException("trailing CBOR data");
		return value;
	}

	private static void writeValue(ByteArrayOutputStream out, Object raw, int depth)
			throws IOException, JSONException {
		if (depth > MAX_DEPTH) throw new IOException("CBOR nesting is too deep");
		Object value = raw == null ? JSONObject.NULL : raw;
		if (value == JSONObject.NULL) {
			out.write(0xf6);
		} else if (value instanceof Boolean) {
			out.write(((Boolean)value).booleanValue() ? 0xf5 : 0xf4);
		} else if (value instanceof byte[]) {
			byte[] bytes = (byte[])value;
			writeTypeLength(out, 2, bytes.length);
			out.write(bytes);
		} else if (value instanceof String) {
			byte[] bytes = utf8((String)value);
			writeTypeLength(out, 3, bytes.length);
			out.write(bytes);
		} else if (value instanceof JSONObject) {
			JSONObject object = (JSONObject)value;
			ArrayList<String> keys = new ArrayList<String>();
			Iterator iterator = object.keys();
			while (iterator.hasNext()) keys.add(String.valueOf(iterator.next()));
			Collections.sort(keys);
			writeTypeLength(out, 5, keys.size());
			for (int i = 0; i < keys.size(); i++) {
				String key = keys.get(i);
				writeValue(out, key, depth + 1);
				writeValue(out, object.get(key), depth + 1);
			}
		} else if (value instanceof JSONArray) {
			JSONArray array = (JSONArray)value;
			writeTypeLength(out, 4, array.length());
			for (int i = 0; i < array.length(); i++) writeValue(out, array.get(i), depth + 1);
		} else if (value instanceof Number) {
			Number number = (Number)value;
			if (value instanceof Float || value instanceof Double) {
				double floating = number.doubleValue();
				long integer = number.longValue();
				if (!Double.isNaN(floating) && !Double.isInfinite(floating) && floating == integer) {
					writeInteger(out, integer);
				} else {
					out.write(0xfb);
					writeLong(out, Double.doubleToLongBits(floating));
				}
			} else {
				writeInteger(out, number.longValue());
			}
		} else {
			throw new IOException("unsupported CBOR value " + value.getClass().getName());
		}
	}

	private static void writeInteger(ByteArrayOutputStream out, long value) throws IOException {
		if (value >= 0) writeTypeLength(out, 0, value);
		else writeTypeLength(out, 1, -1L - value);
	}

	private static void writeTypeLength(ByteArrayOutputStream out, int major, long length)
			throws IOException {
		if (length < 0) throw new IOException("negative CBOR length");
		int prefix = major << 5;
		if (length < 24) {
			out.write(prefix | (int)length);
		} else if (length <= 0xffL) {
			out.write(prefix | 24);
			out.write((int)length);
		} else if (length <= 0xffffL) {
			out.write(prefix | 25);
			out.write((int)(length >>> 8));
			out.write((int)length);
		} else if (length <= 0xffffffffL) {
			out.write(prefix | 26);
			writeInt(out, (int)length);
		} else {
			out.write(prefix | 27);
			writeLong(out, length);
		}
	}

	private static byte[] utf8(String value) throws UnsupportedEncodingException {
		return value.getBytes("UTF-8");
	}

	private static void writeInt(ByteArrayOutputStream out, int value) {
		out.write((value >>> 24) & 0xff);
		out.write((value >>> 16) & 0xff);
		out.write((value >>> 8) & 0xff);
		out.write(value & 0xff);
	}

	private static void writeLong(ByteArrayOutputStream out, long value) {
		for (int i = 7; i >= 0; i--) out.write((int)(value >>> (i * 8)) & 0xff);
	}

	private static final class Reader {
		private final byte[] input;
		private int offset;

		private Reader(byte[] input) {
			this.input = input;
		}

		private boolean done() {
			return offset == input.length;
		}

		private Object readValue(int depth) throws IOException, JSONException {
			if (depth > MAX_DEPTH) throw new IOException("CBOR nesting is too deep");
			int initial = readByte();
			int major = initial >>> 5;
			int additional = initial & 31;
			if (major == 0) return boxedInteger(readLength(additional));
			if (major == 1) return boxedInteger(-1L - readLength(additional));
			if (major == 2) return readBytes(boundedLength(readLength(additional)));
			if (major == 3) return new String(readBytes(boundedLength(readLength(additional))), "UTF-8");
			if (major == 4) {
				int count = boundedCount(readLength(additional));
				JSONArray out = new JSONArray();
				for (int i = 0; i < count; i++) out.put(readValue(depth + 1));
				return out;
			}
			if (major == 5) {
				int count = boundedCount(readLength(additional));
				JSONObject out = new JSONObject();
				for (int i = 0; i < count; i++) {
					Object key = readValue(depth + 1);
					if (!(key instanceof String)) throw new IOException("CBOR map key must be text");
					out.put((String)key, readValue(depth + 1));
				}
				return out;
			}
			if (major == 7 && additional == 20) return Boolean.FALSE;
			if (major == 7 && additional == 21) return Boolean.TRUE;
			if (major == 7 && (additional == 22 || additional == 23)) return JSONObject.NULL;
			if (major == 7 && additional == 27) return Double.valueOf(Double.longBitsToDouble(readLong()));
			throw new IOException("unsupported CBOR value");
		}

		private long readLength(int additional) throws IOException {
			if (additional < 24) return additional;
			if (additional == 24) return readByte();
			if (additional == 25) return ((long)readByte() << 8) | readByte();
			if (additional == 26) return readLongBytes(4);
			if (additional == 27) {
				long value = readLong();
				if (value < 0) throw new IOException("CBOR integer is too large");
				return value;
			}
			throw new IOException("indefinite CBOR is not supported");
		}

		private long readLong() throws IOException {
			return readLongBytes(8);
		}

		private long readLongBytes(int count) throws IOException {
			long value = 0;
			for (int i = 0; i < count; i++) value = (value << 8) | readByte();
			return value;
		}

		private byte[] readBytes(int length) throws IOException {
			if (input.length - offset < length) throw new EOFException("short CBOR byte string");
			byte[] out = new byte[length];
			System.arraycopy(input, offset, out, 0, length);
			offset += length;
			return out;
		}

		private int readByte() throws EOFException {
			if (offset >= input.length) throw new EOFException("short CBOR payload");
			return input[offset++] & 0xff;
		}
	}

	private static int boundedLength(long value) throws IOException {
		if (value < 0 || value > MAX_BYTES) throw new IOException("CBOR string is too large");
		return (int)value;
	}

	private static int boundedCount(long value) throws IOException {
		if (value < 0 || value > 100000) throw new IOException("CBOR collection is too large");
		return (int)value;
	}

	private static Number boxedInteger(long value) {
		return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
				? Integer.valueOf((int)value)
				: Long.valueOf(value);
	}
}
