package rs.ove.crypt.proto;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;

public final class SecureSessionV4Test {
	private static final String SERVER_PUBLIC =
			"8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f";
	private static final String CLIENT_MESSAGE =
			"79a631eede1bf9c98f12032cdeadd0e7a079398fc786b88cc846ec89af85a51ac59d6150c833b37035d67e6e92bdf649";
	private static final String SERVER_MESSAGE =
			"358072d6365880d1aeea329adf9121383851ed21a28e3b75e965d0d2cd166254904cd3bcf4773820c215a420e6336c5d";
	private static final String CLIENT_RECORD =
			"000000000000000040ff18e6bd8ad7192332476946046c120fecb5229783e23e4a9585445fbbdba0320e5633af1f03fb428a14f742b37bd762d29b53ebcdd0d2c417d2e239baaf630aa11575e0df0493cd47d50a8cd31ba323f763b2807d49337e980f6a00e86edc936f8c497142f73d4551a1ec3a1b966b88c9b4cc8b849386f8eef86f92d14e00932347c73d68bab372c7797bfcff94d74ab9e617bd29ebcd765d4fcad6257ef25419523f8a110e5e85d86745a4645178198fc64029f14e40b50fc8a9f8fd391338c09817149a792c601bc32e84e6084c1a9457d8ea40a622eccbf1b36bd8fc4703c1c23809b3e99687c5298c3d1ee884e46e361117de18fa53a4db6ee52b1cbe8174d72b2f16b1d9f3d7784445db9123";

	@Test
	public void matchesRustNoiseHandshakeAndRecordVector() throws Exception {
		byte[] clientPrivate = new byte[32];
		for (int i = 0; i < clientPrivate.length; i++) clientPrivate[i] = (byte)(64 + i);
		SecureSessionV4.ClientHandshake handshake = SecureSessionV4.createClientHandshake(
				clientPrivate,
				hex(SERVER_PUBLIC)
		);
		assertArrayEquals(hex(CLIENT_MESSAGE), handshake.message);
		SecureSessionV4 session = SecureSessionV4.finishClientHandshake(
				handshake,
				hex(SERVER_MESSAGE)
		);
		assertArrayEquals(hex(CLIENT_RECORD), session.sealApplicationRecord("vector".getBytes("UTF-8")));
	}

	@Test(expected = java.security.GeneralSecurityException.class)
	public void rejectsModifiedServerHandshakeTag() throws Exception {
		byte[] clientPrivate = new byte[32];
		for (int i = 0; i < clientPrivate.length; i++) clientPrivate[i] = (byte)(64 + i);
		SecureSessionV4.ClientHandshake handshake = SecureSessionV4.createClientHandshake(
				clientPrivate,
				hex(SERVER_PUBLIC)
		);
		byte[] modified = hex(SERVER_MESSAGE);
		modified[modified.length - 1] ^= 1;
		SecureSessionV4.finishClientHandshake(handshake, modified);
	}

	@Test(expected = IOException.class)
	public void rejectsMissingServerPin() throws Exception {
		SecureSessionV4.createClientHandshake(new byte[32], new byte[0]);
	}

	private static byte[] hex(String value) {
		byte[] output = new byte[value.length() / 2];
		for (int i = 0; i < output.length; i++) {
			output[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
		}
		return output;
	}
}
