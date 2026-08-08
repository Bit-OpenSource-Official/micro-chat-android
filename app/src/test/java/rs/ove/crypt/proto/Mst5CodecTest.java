package rs.ove.crypt.proto;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class Mst5CodecTest {
	@Test
	public void canonicalAuthCborAndFrameMatchWireContract() throws Exception {
		JSONObject auth = new JSONObject();
		auth.put("token", "abc");
		byte[] payload = MiniCbor.encode(auth);
		assertArrayEquals(hex("a165746f6b656e63616263"), payload);
		Mst5Frame frame = new Mst5Frame(Mst5Frame.AUTH, 0, 1, payload);
		assertArrayEquals(
				hex("0100000000000000000000010000000ba165746f6b656e63616263"),
				frame.encode(false)
		);
		Mst5Frame decoded = Mst5Frame.decode(frame.encode(false));
		assertEquals(1, decoded.id);
		assertEquals("abc", ((JSONObject)MiniCbor.decode(decoded.payload)).getString("token"));
	}

	@Test
	public void cborRoundTripsNestedJsonAndBinary() throws Exception {
		JSONObject root = new JSONObject();
		root.put("ok", true);
		root.put("negative", -12);
		root.put("bytes", new byte[] {0, 1, (byte)255});
		root.put("items", new JSONArray().put("hello").put(42).put(JSONObject.NULL));
		JSONObject decoded = (JSONObject)MiniCbor.decode(MiniCbor.encode(root));
		assertTrue(decoded.getBoolean("ok"));
		assertEquals(-12, decoded.getInt("negative"));
		assertArrayEquals(new byte[] {0, 1, (byte)255}, (byte[])decoded.get("bytes"));
		assertEquals("hello", decoded.getJSONArray("items").getString(0));
	}

	@Test(expected = IOException.class)
	public void rejectsTrailingCborData() throws Exception {
		MiniCbor.decode(new byte[] {(byte)0xa0, 0});
	}

	@Test
	public void compressesOnlyLargeResultFrames() throws Exception {
		byte[] payload = new byte[8192];
		for (int i = 0; i < payload.length; i++) payload[i] = 'a';
		Mst5Frame encoded = Mst5Frame.decode(
				new Mst5Frame(Mst5Frame.RESULT, 200, 9, payload).encode(true)
		);
		assertArrayEquals(payload, encoded.payload);
	}

	private static byte[] hex(String value) {
		byte[] output = new byte[value.length() / 2];
		for (int i = 0; i < output.length; i++) {
			output[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
		}
		return output;
	}
}
