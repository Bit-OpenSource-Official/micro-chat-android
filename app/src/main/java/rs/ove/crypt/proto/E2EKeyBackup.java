package rs.ove.crypt.proto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class E2EKeyBackup {
	private static final int ITERATIONS = 120000;
	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final String PBKDF2_SHA256 = "PBKDF2WithHmacSHA256";
	private static final boolean FAST_PBKDF2 = fastPbkdf2Compatible();

	private E2EKeyBackup() {
	}

	public static Backup seal(E2ECipher.Identity identity, String password)
			throws GeneralSecurityException, IOException {
		if (identity == null || password == null || password.length() < 3) {
			throw new IOException("password and E2E identity are required");
		}
		byte[] salt = new byte[16];
		byte[] iv = new byte[16];
		CryptoRandom.nextBytes(salt);
		CryptoRandom.nextBytes(iv);
		byte[] material = pbkdf2(password, salt, ITERATIONS, 64);
		byte[] encKey = Arrays.copyOfRange(material, 0, 32);
		byte[] macKey = Arrays.copyOfRange(material, 32, 64);
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"), new IvParameterSpec(iv));
		byte[] ciphertext = cipher.doFinal(Base64Codec.decode(identity.privateKeyB64));
		byte[] tag = Arrays.copyOf(hmac(macKey, concat(salt, iv, ciphertext)), 16);
		return new Backup(
				1, Base64Codec.encode(salt), Base64Codec.encode(iv),
				Base64Codec.encode(ciphertext), Base64Codec.encode(tag)
		);
	}

	public static E2ECipher.Identity open(Backup backup, String password)
			throws GeneralSecurityException, IOException {
		if (backup == null || backup.version != 1) {
			throw new IOException("unsupported E2E backup");
		}
		byte[] salt = Base64Codec.decode(backup.salt);
		byte[] iv = Base64Codec.decode(backup.iv);
		byte[] ciphertext = Base64Codec.decode(backup.ciphertext);
		byte[] tag = Base64Codec.decode(backup.tag);
		if (salt.length != 16 || iv.length != 16 || ciphertext.length == 0 || tag.length != 16) {
			throw new IOException("invalid E2E backup");
		}
		byte[] material = pbkdf2(password, salt, ITERATIONS, 64);
		byte[] encKey = Arrays.copyOfRange(material, 0, 32);
		byte[] macKey = Arrays.copyOfRange(material, 32, 64);
		byte[] expected = hmac(macKey, concat(salt, iv, ciphertext));
		if (!constantTimeEquals(expected, tag, 16)) {
			throw new IOException("wrong password or damaged E2E backup");
		}
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"), new IvParameterSpec(iv));
		byte[] privateKey = cipher.doFinal(ciphertext);
		if (privateKey.length != 32) {
			throw new IOException("invalid E2E private key");
		}
		return E2ECipher.identityFromPrivate(Base64Codec.encode(privateKey));
	}

	private static byte[] pbkdf2(String password, byte[] salt, int iterations, int length)
			throws GeneralSecurityException {
		if (FAST_PBKDF2) {
			try {
				return fastPbkdf2(password, salt, iterations, length);
			} catch (GeneralSecurityException ignored) {
			}
		}
		try {
			return slowPbkdf2((password == null ? "" : password).getBytes("UTF-8"), salt, iterations, length);
		} catch (java.io.UnsupportedEncodingException ex) {
			throw new GeneralSecurityException(ex);
		}
	}

	private static byte[] slowPbkdf2(byte[] password, byte[] salt, int iterations, int length)
			throws GeneralSecurityException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		for (int block = 1; out.size() < length; block++) {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(password, HMAC_SHA256));
			mac.update(salt);
			mac.update((byte)(block >>> 24));
			mac.update((byte)(block >>> 16));
			mac.update((byte)(block >>> 8));
			mac.update((byte)block);
			byte[] u = mac.doFinal();
			byte[] mixed = u.clone();
			for (int i = 1; i < iterations; i++) {
				u = mac.doFinal(u);
				for (int j = 0; j < mixed.length; j++) mixed[j] ^= u[j];
			}
			out.write(mixed, 0, Math.min(mixed.length, length - out.size()));
		}
		return out.toByteArray();
	}

	private static byte[] fastPbkdf2(String password, byte[] salt, int iterations, int length)
			throws GeneralSecurityException {
		PBEKeySpec spec = new PBEKeySpec(
				(password == null ? "" : password).toCharArray(),
				salt,
				iterations,
				length * 8
		);
		try {
			return SecretKeyFactory.getInstance(PBKDF2_SHA256).generateSecret(spec).getEncoded();
		} finally {
			spec.clearPassword();
		}
	}

	private static boolean fastPbkdf2Compatible() {
		try {
			byte[] salt = new byte[] {
					1, 2, 3, 4, 5, 6, 7, 8,
					9, 10, 11, 12, 13, 14, 15, 16
			};
			String password = "pass\u00f6";
			byte[] slow = slowPbkdf2(password.getBytes("UTF-8"), salt, 2, 32);
			byte[] fast = fastPbkdf2(password, salt, 2, 32);
			return Arrays.equals(slow, fast);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance(HMAC_SHA256);
		mac.init(new SecretKeySpec(key, HMAC_SHA256));
		return mac.doFinal(data);
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

	public static final class Backup {
		public final int version;
		public final String salt;
		public final String iv;
		public final String ciphertext;
		public final String tag;

		public Backup(int version, String salt, String iv, String ciphertext, String tag) {
			this.version = version;
			this.salt = salt;
			this.iv = iv;
			this.ciphertext = ciphertext;
			this.tag = tag;
		}
	}
}
