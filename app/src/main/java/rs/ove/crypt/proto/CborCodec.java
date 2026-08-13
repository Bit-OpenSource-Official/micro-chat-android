package rs.ove.crypt.proto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;

/** Minimal deterministic CBOR codec used only at the typed JNI boundary. */
final class CborCodec {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private CborCodec() {}

	static byte[] encodeRequest(String method, String path, Object body) throws IOException {
		Object value;
		if ("GET".equals(method)) {
			int separator = path.indexOf('?');
			JSONObject query = new JSONObject();
			if (separator >= 0 && separator + 1 < path.length()) {
				try { query.put("query", path.substring(separator + 1)); }
				catch (Exception error) { throw new IOException("cannot encode MST5 query", error); }
			}
			value = query;
		} else if (body == null) {
			value = new JSONObject();
		} else {
			value = body;
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		encode(output, value);
		return output.toByteArray();
	}

	static Object decodeValue(ByteBuffer source, int length) throws IOException {
		ByteBuffer input = source.duplicate();
		input.position(0);
		input.limit(length);
		Object value = decode(input, 0);
		if (input.hasRemaining()) throw new IOException("trailing CBOR response data");
		return value;
	}

	private static void encode(ByteArrayOutputStream output, Object value) throws IOException {
		if (value == null || value == JSONObject.NULL) {
			output.write(0xf6);
		} else if (value instanceof Boolean) {
			output.write(((Boolean) value).booleanValue() ? 0xf5 : 0xf4);
		} else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
			long number = ((Number) value).longValue();
			if (number >= 0) writeHead(output, 0, number);
			else writeHead(output, 1, -1L - number);
		} else if (value instanceof Number) {
			output.write(0xfb);
			writeU64(output, Double.doubleToLongBits(((Number) value).doubleValue()));
		} else if (value instanceof String) {
			byte[] bytes = ((String) value).getBytes(UTF8);
			writeHead(output, 3, bytes.length);
			output.write(bytes, 0, bytes.length);
		} else if (value instanceof JSONArray) {
			JSONArray array = (JSONArray) value;
			writeHead(output, 4, array.length());
			for (int i = 0; i < array.length(); i++) encode(output, array.opt(i));
		} else if (value instanceof JSONObject) {
			JSONObject object = (JSONObject) value;
			writeHead(output, 5, object.length());
			Iterator<String> keys = object.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				encode(output, key);
				encode(output, object.opt(key));
			}
		} else {
			throw new IOException("unsupported typed MST5 value " + value.getClass().getName());
		}
	}

	private static Object decode(ByteBuffer input, int depth) throws IOException {
		if (depth > 64 || !input.hasRemaining()) throw new IOException("invalid CBOR response");
		int initial = input.get() & 0xff;
		int major = initial >>> 5;
		int additional = initial & 31;
		if (major == 0) return narrow(readLength(input, additional));
		if (major == 1) return narrow(-1L - readLength(input, additional));
		if (major == 2) {
			int length = checkedLength(readLength(input, additional), input.remaining());
			JSONArray bytes = new JSONArray();
			for (int i = 0; i < length; i++) bytes.put(input.get() & 0xff);
			return bytes;
		}
		if (major == 3) {
			int length = checkedLength(readLength(input, additional), input.remaining());
			byte[] bytes = new byte[length];
			input.get(bytes);
			return new String(bytes, UTF8);
		}
		if (major == 4) {
			int length = checkedLength(readLength(input, additional), Integer.MAX_VALUE);
			JSONArray array = new JSONArray();
			for (int i = 0; i < length; i++) array.put(decode(input, depth + 1));
			return array;
		}
		if (major == 5) {
			int length = checkedLength(readLength(input, additional), Integer.MAX_VALUE);
			JSONObject object = new JSONObject();
			for (int i = 0; i < length; i++) {
				Object key = decode(input, depth + 1);
				if (!(key instanceof String)) throw new IOException("CBOR response map key is not text");
				try { object.put((String) key, decode(input, depth + 1)); }
				catch (Exception error) { throw new IOException("invalid CBOR response map", error); }
			}
			return object;
		}
		if (major == 7) {
			if (additional == 20) return Boolean.FALSE;
			if (additional == 21) return Boolean.TRUE;
			if (additional == 22) return JSONObject.NULL;
			if (additional == 26) return Float.valueOf(Float.intBitsToFloat((int) readU32(input)));
			if (additional == 27) return Double.valueOf(Double.longBitsToDouble(readU64(input)));
		}
		throw new IOException("unsupported CBOR response value");
	}

	private static Number narrow(long value) {
		return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? Integer.valueOf((int) value) : Long.valueOf(value);
	}

	private static void writeHead(ByteArrayOutputStream output, int major, long value) {
		if (value < 24) output.write((major << 5) | (int) value);
		else if (value <= 0xff) { output.write((major << 5) | 24); output.write((int) value); }
		else if (value <= 0xffff) { output.write((major << 5) | 25); output.write((int) (value >>> 8)); output.write((int) value); }
		else if (value <= 0xffff_ffffL) { output.write((major << 5) | 26); writeU32(output, value); }
		else { output.write((major << 5) | 27); writeU64(output, value); }
	}

	private static long readLength(ByteBuffer input, int additional) throws IOException {
		if (additional < 24) return additional;
		if (additional == 24) return require(input, 1).get() & 0xffL;
		if (additional == 25) return ((require(input, 2).get() & 0xffL) << 8) | (input.get() & 0xffL);
		if (additional == 26) return readU32(input);
		if (additional == 27) {
			long value = readU64(input);
			if (value < 0) throw new IOException("CBOR length exceeds signed range");
			return value;
		}
		throw new IOException("indefinite CBOR values are not supported");
	}

	private static int checkedLength(long value, int available) throws IOException {
		if (value < 0 || value > Integer.MAX_VALUE || value > available) throw new IOException("invalid CBOR response length");
		return (int) value;
	}

	private static ByteBuffer require(ByteBuffer input, int count) throws IOException {
		if (input.remaining() < count) throw new IOException("truncated CBOR response");
		return input;
	}

	private static long readU32(ByteBuffer input) throws IOException {
		require(input, 4);
		return ((input.get() & 0xffL) << 24) | ((input.get() & 0xffL) << 16)
				| ((input.get() & 0xffL) << 8) | (input.get() & 0xffL);
	}

	private static long readU64(ByteBuffer input) throws IOException {
		require(input, 8);
		return (readU32(input) << 32) | readU32(input);
	}

	private static void writeU32(ByteArrayOutputStream output, long value) {
		output.write((int) (value >>> 24)); output.write((int) (value >>> 16));
		output.write((int) (value >>> 8)); output.write((int) value);
	}

	private static void writeU64(ByteArrayOutputStream output, long value) {
		writeU32(output, value >>> 32); writeU32(output, value);
	}
}
