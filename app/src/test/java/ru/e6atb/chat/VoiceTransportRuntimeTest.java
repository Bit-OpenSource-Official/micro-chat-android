package ru.e6atb.chat;

import org.junit.Assume;
import org.junit.Test;
import org.json.JSONObject;

import static org.junit.Assert.assertTrue;

public final class VoiceTransportRuntimeTest {
	@Test
	public void obtainsTicketAndOpensTlsWebSocket() throws Exception {
		String address = System.getenv("MICROMSG_TEST_HTTPS_URL");
		Assume.assumeTrue(address != null && address.startsWith("https://"));
		String login = "voice_runtime_" + Long.toString(System.nanoTime(), 36);
		String peerLogin = "vrp_" + Long.toString(System.nanoTime(), 36);
		TlsHttpClient client = new TlsHttpClient(address);
		JSONObject registered = request(client, "", "/register",
				new JSONObject().put("username", login).put("password", "secret"));
		request(client, "", "/register",
				new JSONObject().put("username", peerLogin).put("password", "secret"));
		String token = registered.getString("token");
		JSONObject ticketBody = request(client, token, "/voice-ticket",
				new JSONObject().put("peer", peerLogin));
		String ticket = ticketBody.getString("ticket");
		String url = new MiniTaLib(address, token).voiceSocketUrl(ticket);
		assertTrue(url.startsWith("wss://"));
		SimpleWebSocket socket = new SimpleWebSocket();
		try {
			socket.connect(url);
			byte[] silence = new byte[640];
			socket.sendBinary(silence, silence.length);
		} finally {
			socket.close();
		}
	}

	private static JSONObject request(TlsHttpClient client, String token, String path, JSONObject body)
			throws Exception {
		TlsHttpClient.Response response = client.request(token, "POST", path, body.toString().getBytes("UTF-8"), 10000);
		String text = new String(response.body(), "UTF-8");
		if (response.code() / 100 != 2) throw new AssertionError(response.code() + ": " + text);
		return new JSONObject(text);
	}
}
