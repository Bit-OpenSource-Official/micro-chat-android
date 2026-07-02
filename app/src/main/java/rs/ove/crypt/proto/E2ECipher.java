package rs.ove.crypt.proto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class E2ECipher {
	private static final String AES_CBC = "AES/CBC/PKCS5Padding";
	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final byte[] CONTEXT = ascii("micromsg-e2e-v1");

	private E2ECipher() {
	}

	public static Identity generateIdentity() {
		X25519.KeyPair pair = X25519.generate(CryptoRandom.instance());
		return new Identity(Base64Codec.encode(pair.privateKey()), Base64Codec.encode(pair.publicKey()));
	}

	public static Identity identityFromPrivate(String privateKeyB64) {
		X25519.KeyPair pair = X25519.fromPrivate(Base64Codec.decode(privateKeyB64));
		return new Identity(Base64Codec.encode(pair.privateKey()), Base64Codec.encode(pair.publicKey()));
	}

	public static Envelope seal(
			Identity identity,
			String peerPublicKeyB64,
			String from,
			String to,
			String text
	) throws GeneralSecurityException, IOException {
		return seal(session(identity, peerPublicKeyB64, from, to), from, to, text);
	}

	public static Session session(
			Identity identity,
			String peerPublicKeyB64,
			String from,
			String to
	) throws GeneralSecurityException, IOException {
		byte[] peerPublic = decodePublic(peerPublicKeyB64);
		byte[] shared = X25519.shared(Base64Codec.decode(identity.privateKeyB64), peerPublic);
		rejectAllZero(shared);
		return new Session(derive(shared, from, to));
	}

	public static Envelope seal(
			Session session,
			String from,
			String to,
			String text
	) throws GeneralSecurityException, IOException {
		if (session == null) {
			throw new IOException("E2E session is required");
		}
		byte[] iv = new byte[16];
		CryptoRandom.nextBytes(iv);
		byte[] ciphertext = session.crypt(Cipher.ENCRYPT_MODE, iv, utf8(text == null ? "" : text));
		byte[] aad = aad(from, to);
		byte[] tag = session.hmac(aad, iv, ciphertext);
		return new Envelope(
				1,
				Base64Codec.encode(iv),
				Base64Codec.encode(ciphertext),
				Base64Codec.encode(Arrays.copyOf(tag, 16))
		);
	}

	public static String open(
			Identity identity,
			String peerPublicKeyB64,
			String from,
			String to,
			Envelope envelope
	) throws GeneralSecurityException, IOException {
		return open(session(identity, peerPublicKeyB64, from, to), from, to, envelope);
	}

	public static String open(
			Session session,
			String from,
			String to,
			Envelope envelope
	) throws GeneralSecurityException, IOException {
		if (envelope == null || envelope.version != 1) {
			throw new IOException("unsupported e2e message version");
		}
		if (session == null) {
			throw new IOException("E2E session is required");
		}
		byte[] iv = Base64Codec.decode(envelope.nonce);
		byte[] ciphertext = Base64Codec.decode(envelope.ciphertext);
		byte[] tag = Base64Codec.decode(envelope.tag);
		if (iv.length != 16 || ciphertext.length == 0 || tag.length != 16) {
			throw new IOException("invalid e2e message");
		}
		byte[] expected = session.hmac(aad(from, to), iv, ciphertext);
		if (!constantTimeEquals(expected, tag, 16)) {
			throw new IOException("e2e message authentication failed");
		}
		return new String(session.crypt(Cipher.DECRYPT_MODE, iv, ciphertext), "UTF-8");
	}

	public static String fingerprint(String publicKeyB64) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(decodePublic(publicKeyB64));
			StringBuilder out = new StringBuilder(47);
			for (int i = 0; i < 16; i++) {
				if (i > 0) out.append(':');
				int value = hash[i] & 0xff;
				if (value < 16) out.append('0');
				out.append(Integer.toHexString(value));
			}
			return out.toString();
		} catch (Exception ex) {
			throw new IllegalArgumentException("invalid e2e public key", ex);
		}
	}

	private static KeyMaterial derive(byte[] shared, String from, String to) throws GeneralSecurityException {
		String left = from == null ? "" : from.trim().toLowerCase(Locale.US);
		String right = to == null ? "" : to.trim().toLowerCase(Locale.US);
		if (left.compareTo(right) > 0) {
			String swap = left;
			left = right;
			right = swap;
		}
		byte[] salt = MessageDigest.getInstance("SHA-256").digest(
				concat(CONTEXT, utf8(left), new byte[] {0}, utf8(right))
		);
		byte[] prk = hmac(salt, shared);
		return new KeyMaterial(
				hkdf(prk, "micromsg e2e enc", 32),
				hkdf(prk, "micromsg e2e mac", 32)
		);
	}

	private static byte[] aad(String from, String to) {
		return concat(CONTEXT, new byte[] {0}, utf8(from), new byte[] {0}, utf8(to));
	}

	private static byte[] decodePublic(String value) {
		byte[] key = Base64Codec.decode(value == null ? "" : value);
		if (key.length != 32) {
			throw new IllegalArgumentException("e2e public key must be 32 bytes");
		}
		return key;
	}

	private static void rejectAllZero(byte[] value) throws IOException {
		int combined = 0;
		for (int i = 0; i < value.length; i++) combined |= value[i] & 0xff;
		if (combined == 0) throw new IOException("invalid X25519 shared secret");
	}

	private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance(HMAC_SHA256);
		mac.init(new SecretKeySpec(key, HMAC_SHA256));
		return mac.doFinal(data);
	}

	private static byte[] hkdf(byte[] prk, String info, int length) throws GeneralSecurityException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		byte[] previous = new byte[0];
		byte[] infoBytes = ascii(info);
		int counter = 1;
		while (out.size() < length) {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(prk, HMAC_SHA256));
			mac.update(previous);
			mac.update(infoBytes);
			mac.update((byte) counter++);
			previous = mac.doFinal();
			out.write(previous, 0, Math.min(previous.length, length - out.size()));
		}
		return out.toByteArray();
	}

	private static boolean constantTimeEquals(byte[] expected, byte[] actual, int length) {
		if (expected.length < length || actual.length != length) return false;
		int diff = 0;
		for (int i = 0; i < length; i++) diff |= expected[i] ^ actual[i];
		return diff == 0;
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

	private static byte[] utf8(String value) {
		try {
			return (value == null ? "" : value).getBytes("UTF-8");
		} catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static byte[] ascii(String value) {
		try {
			return value.getBytes("US-ASCII");
		} catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static final class Identity {
		public final String privateKeyB64;
		public final String publicKeyB64;

		private Identity(String privateKeyB64, String publicKeyB64) {
			this.privateKeyB64 = privateKeyB64;
			this.publicKeyB64 = publicKeyB64;
		}
	}

	public static final class Envelope {
		public final int version;
		public final String nonce;
		public final String ciphertext;
		public final String tag;

		public Envelope(int version, String nonce, String ciphertext, String tag) {
			this.version = version;
			this.nonce = nonce;
			this.ciphertext = ciphertext;
			this.tag = tag;
		}
	}

	public static final class Session {
		private final SecretKeySpec encKey;
		private final SecretKeySpec macKey;
		private Cipher cipher;
		private Mac mac;

		private Session(KeyMaterial keys) {
			this.encKey = new SecretKeySpec(keys.enc, "AES");
			this.macKey = new SecretKeySpec(keys.mac, HMAC_SHA256);
		}

		private synchronized byte[] crypt(int mode, byte[] iv, byte[] input) throws GeneralSecurityException {
			if (cipher == null) {
				cipher = Cipher.getInstance(AES_CBC);
			}
			cipher.init(mode, encKey, new IvParameterSpec(iv));
			return cipher.doFinal(input);
		}

		private synchronized byte[] hmac(byte[] first, byte[] second, byte[] third) throws GeneralSecurityException {
			if (mac == null) {
				mac = Mac.getInstance(HMAC_SHA256);
				mac.init(macKey);
			}
			mac.reset();
			mac.update(first);
			mac.update(second);
			mac.update(third);
			return mac.doFinal();
		}
	}

	private static final class KeyMaterial {
		private final byte[] enc;
		private final byte[] mac;

		private KeyMaterial(byte[] enc, byte[] mac) {
			this.enc = enc;
			this.mac = mac;
		}
	}
}
