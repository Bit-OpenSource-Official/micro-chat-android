package ru.e6atb.chat;

import org.junit.Assume;
import org.junit.Test;

import rs.ove.crypt.proto.CryptTcpClient;
import rs.ove.crypt.proto.SecureSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class VoiceTransportRuntimeTest {
	@Test
	public void obtainsTicketAndCompletesVoiceHandshake() throws Exception {
		String address = System.getenv("MICROMSG_TEST_ADDR");
		Assume.assumeTrue(address != null && address.length() > 0);
		String login = "voice_runtime_" + Long.toString(System.nanoTime(), 36);
		String peerLogin = "vrp_" + Long.toString(System.nanoTime(), 36);
		String registered = request(address, "", "POST", "/register",
				"{\"username\":\"" + login + "\",\"password\":\"secret\"}");
		request(address, "", "POST", "/register",
				"{\"username\":\"" + peerLogin + "\",\"password\":\"secret\"}");
		String token = jsonString(registered, "token");
		String ticketBody = request(address, token, "POST", "/voice-ticket",
				"{\"peer\":\"" + peerLogin + "\"}");
		String ticket = jsonString(ticketBody, "ticket");
		String url = new MiniTaLib(address, token).voiceSocketUrl(ticket);
		assertTrue(url.startsWith("ws://" + address + "/voice?ticket="));
		SimpleWebSocket socket = new SimpleWebSocket();
		try {
			socket.connect(url);
			SecureSession.ClientHello hello = SecureSession.createClientHello();
			byte[] message = hello.message();
			socket.sendBinary(message, message.length);
			SimpleWebSocket.Frame frame = socket.readFrame();
			assertEquals(SimpleWebSocket.BINARY, frame.opcode);
			SecureSession.openClient(hello, frame.payload);
		} finally {
			socket.close();
		}
	}

	private static String request(String address, String token, String method, String path, String body)
			throws Exception {
		CryptTcpClient.Response response = new CryptTcpClient().request(
				address,
				token,
				method,
				path,
				body == null ? null : body.getBytes("UTF-8"),
				10000
		);
		assertEquals(new String(response.body(), "UTF-8"), 200, response.code());
		return new String(response.body(), "UTF-8");
	}

	private static String jsonString(String json, String name) {
		String marker = "\"" + name + "\":\"";
		int start = json.indexOf(marker);
		if (start < 0) throw new AssertionError("missing " + name + ": " + json);
		start += marker.length();
		int end = json.indexOf('"', start);
		if (end < 0) throw new AssertionError("bad JSON: " + json);
		return json.substring(start, end);
	}
}
