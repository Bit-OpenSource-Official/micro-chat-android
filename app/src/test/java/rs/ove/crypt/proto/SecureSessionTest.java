package rs.ove.crypt.proto;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public final class SecureSessionTest {
	private static final String SERVER_PUBLIC =
			"8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f";
	private static final String SERVER_MESSAGE =
			"358072d6365880d1aeea329adf9121383851ed21a28e3b75e965d0d2cd166254904cd3bcf4773820c215a420e6336c5d";

	@Test
	public void createsPinnedMst5Handshake() throws Exception {
		byte[] clientPrivate = new byte[32];
		for (int i = 0; i < clientPrivate.length; i++) clientPrivate[i] = (byte)(64 + i);
		SecureSession.ClientHandshake handshake = SecureSession.createClientHandshake(
				clientPrivate,
				hex(SERVER_PUBLIC)
		);
		assertEquals(SecureSession.HANDSHAKE_MESSAGE_LENGTH, handshake.message.length);
	}

	@Test(expected = java.security.GeneralSecurityException.class)
	public void rejectsModifiedServerHandshakeTag() throws Exception {
		byte[] clientPrivate = new byte[32];
		for (int i = 0; i < clientPrivate.length; i++) clientPrivate[i] = (byte)(64 + i);
		SecureSession.ClientHandshake handshake = SecureSession.createClientHandshake(
				clientPrivate,
				hex(SERVER_PUBLIC)
		);
		byte[] modified = hex(SERVER_MESSAGE);
		modified[modified.length - 1] ^= 1;
		SecureSession.finishClientHandshake(handshake, modified);
	}

	@Test(expected = IOException.class)
	public void rejectsMissingServerPin() throws Exception {
		SecureSession.createClientHandshake(new byte[32], new byte[0]);
	}

	private static byte[] hex(String value) {
		byte[] output = new byte[value.length() / 2];
		for (int i = 0; i < output.length; i++) {
			output[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
		}
		return output;
	}
}
