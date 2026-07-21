package ru.e6atb.chat;

import org.junit.Assume;
import org.junit.Test;

import rs.ove.crypt.proto.SecureSessionV4;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Base64;
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
			SecureSessionV4.ClientHello hello = SecureSessionV4.createClientHello();
			byte[] message = hello.message();
			socket.sendBinary(message, message.length);
			SimpleWebSocket.Frame frame = socket.readFrame();
			assertEquals(SimpleWebSocket.BINARY, frame.opcode);
			SecureSessionV4.openClient(hello, frame.payload);
		} finally {
			socket.close();
		}
	}

	private static String request(String address, String token, String method, String path, String body)
			throws Exception {
		int split = address.lastIndexOf(':');
		Socket socket = new Socket(address.substring(0, split), Integer.parseInt(address.substring(split + 1)));
		try {
			InputStream input = socket.getInputStream();
			OutputStream output = socket.getOutputStream();
			SecureSessionV4 session = SecureSessionV4.client(
					input,
					output,
					ru.e6atb.chat.BuildConfig.CRYPT_SERVER_PUBLIC_KEY_B64.isEmpty()
							? new byte[0]
							: java.util.Base64.getDecoder().decode(ru.e6atb.chat.BuildConfig.CRYPT_SERVER_PUBLIC_KEY_B64)
			);
			String encodedBody = Base64.getEncoder().encodeToString(body.getBytes("UTF-8"));
			String request = "{\"method\":\"" + method + "\",\"path\":\"" + path + "\"" +
					(token.length() == 0 ? "" : ",\"token\":\"" + token + "\"") +
					",\"body\":\"" + encodedBody + "\"}";
			session.writeEncryptedFrame(output, request.getBytes("UTF-8"));
			String response = new String(session.readEncryptedFrame(input), "UTF-8");
			String encoded = jsonString(response, "body");
			return new String(Base64.getDecoder().decode(encoded), "UTF-8");
		} finally {
			socket.close();
		}
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
