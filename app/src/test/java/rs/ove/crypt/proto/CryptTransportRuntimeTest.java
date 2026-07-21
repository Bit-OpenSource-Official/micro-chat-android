package rs.ove.crypt.proto;

import org.junit.Assume;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static org.junit.Assert.assertTrue;

public final class CryptTransportRuntimeTest {
	@Test
	public void javaClientTalksToV4Server() throws Exception {
		String address = System.getenv("MICROMSG_TEST_ADDR");
		String publicKey = System.getenv("MICROMSG_TEST_PUBLIC_KEY_B64");
		Assume.assumeTrue(address != null && publicKey != null);
		int split = address.lastIndexOf(':');
		Socket socket = new Socket(address.substring(0, split), Integer.parseInt(address.substring(split + 1)));
		try {
			InputStream input = socket.getInputStream();
			OutputStream output = socket.getOutputStream();
			SecureSessionV4 session = SecureSessionV4.client(input, output, Base64Codec.decode(publicKey));
			String login = "runtime_java_" + Long.toString(System.nanoTime(), 36);
			String body = Base64Codec.encode(("{\"username\":\"" + login + "\",\"password\":\"secret\"}").getBytes("UTF-8"));
			String request = "{\"method\":\"POST\",\"path\":\"/register\",\"body\":\"" + body + "\"}";
			session.writeEncryptedFrame(output, request.getBytes("UTF-8"));
			String response = new String(session.readEncryptedFrame(input), "UTF-8");
			assertTrue(response, response.contains("\"code\":200"));
		} finally {
			socket.close();
		}
	}
}
