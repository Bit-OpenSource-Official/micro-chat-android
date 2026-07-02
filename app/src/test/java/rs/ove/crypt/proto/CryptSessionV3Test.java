package rs.ove.crypt.proto;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;

public final class CryptSessionV3Test {
	@Test
	public void acceptsAuthenticatedEphemeralServerHello() throws Exception {
		CryptSession.ClientHello client = CryptSession.createClientHello();
		X25519.KeyPair staticServer = X25519.generate(new SecureRandom());
		X25519.KeyPair ephemeralServer = X25519.generate(new SecureRandom());
		byte[] staticShared = X25519.shared(staticServer.privateKey(), client.publicKey());
		byte[] transcript = concat(
				ascii("micromsg-v3"), client.publicKey(),
				staticServer.publicKey(), ephemeralServer.publicKey()
		);
		byte[] salt = MessageDigest.getInstance("SHA-256").digest(transcript);
		byte[] staticPrk = hmac(salt, staticShared);
		byte[] authKey = hkdf(staticPrk, "micromsg v3 server auth", 32);
		byte[] tag = Arrays.copyOf(hmac(authKey, transcript), 16);
		byte[] payload = concat(staticServer.publicKey(), ephemeralServer.publicKey(), tag);
		byte[] hello = hello(new byte[] {'R', 'S', 'P', '3'}, payload);

		CryptSession session = CryptSession.openClient(client, hello, staticServer.publicKey());
		byte[] sealed = session.seal("payload".getBytes("UTF-8"));
		assertArrayEquals(
				new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
				Arrays.copyOf(sealed, 8)
		);
	}

	@Test(expected = java.io.IOException.class)
	public void rejectsChangedServerAuthenticationTag() throws Exception {
		CryptSession.ClientHello client = CryptSession.createClientHello();
		X25519.KeyPair staticServer = X25519.generate(new SecureRandom());
		X25519.KeyPair ephemeralServer = X25519.generate(new SecureRandom());
		byte[] payload = concat(
				staticServer.publicKey(), ephemeralServer.publicKey(), new byte[16]
		);
		CryptSession.openClient(
				client,
				hello(new byte[] {'R', 'S', 'P', '3'}, payload),
				staticServer.publicKey()
		);
	}

	private static byte[] hello(byte[] magic, byte[] payload) {
		byte[] out = new byte[6 + payload.length];
		System.arraycopy(magic, 0, out, 0, 4);
		out[4] = (byte)(payload.length >>> 8);
		out[5] = (byte)payload.length;
		System.arraycopy(payload, 0, out, 6, payload.length);
		return out;
	}

	private static byte[] hmac(byte[] key, byte[] data) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	private static byte[] hkdf(byte[] prk, String info, int length) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] previous = new byte[0];
		int counter = 1;
		while (out.size() < length) {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(prk, "HmacSHA256"));
			mac.update(previous);
			mac.update(ascii(info));
			mac.update((byte)counter++);
			previous = mac.doFinal();
			out.write(previous, 0, Math.min(previous.length, length - out.size()));
		}
		return out.toByteArray();
	}

	private static byte[] concat(byte[]... values) {
		int size = 0;
		for (int i = 0; i < values.length; i++) size += values[i].length;
		byte[] out = new byte[size];
		int offset = 0;
		for (int i = 0; i < values.length; i++) {
			System.arraycopy(values[i], 0, out, offset, values[i].length);
			offset += values[i].length;
		}
		return out;
	}

	private static byte[] ascii(String value) throws Exception {
		return value.getBytes("US-ASCII");
	}
}
