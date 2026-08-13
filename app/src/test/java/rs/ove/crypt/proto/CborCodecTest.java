package rs.ove.crypt.proto;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CborCodecTest {
	@Test
	public void typedRequestRoundTripsWithoutJsonBytes() throws Exception {
		JSONObject body = new JSONObject();
		body.put("to", "0000000000000001");
		body.put("count", 42);
		body.put("enabled", true);
		body.put("items", new JSONArray().put("a").put(7));
		byte[] encoded = CborCodec.encodeRequest("POST", "/send", body);
		ByteBuffer direct = ByteBuffer.allocateDirect(encoded.length);
		direct.put(encoded);
		Object decoded = CborCodec.decodeValue(direct, encoded.length);
		assertTrue(decoded instanceof JSONObject);
		JSONObject value = (JSONObject) decoded;
		assertEquals("0000000000000001", value.getString("to"));
		assertEquals(42, value.getInt("count"));
		assertTrue(value.getBoolean("enabled"));
		assertEquals(7, value.getJSONArray("items").getInt(1));
	}

	@Test
	public void queryIsEncodedAsTypedField() throws Exception {
		byte[] encoded = CborCodec.encodeRequest("GET", "/history?peer=1&limit=30", null);
		ByteBuffer direct = ByteBuffer.allocateDirect(encoded.length);
		direct.put(encoded);
		JSONObject value = (JSONObject) CborCodec.decodeValue(direct, encoded.length);
		assertEquals("peer=1&limit=30", value.getString("query"));
	}
}
