package rs.ove.crypt.proto;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import ru.e6atb.chat.BuildConfig;

public final class CryptIdentity {
	public static final String SERVER_PUBLIC_PROPERTY = "rs.ove.crypt.server_public_key_b64";

	private CryptIdentity() {
	}

	public static byte[] serverPublicKey() {
		String value = System.getProperty(SERVER_PUBLIC_PROPERTY);
		if (value == null || value.trim().length() == 0) {
			value = BuildConfig.CRYPT_SERVER_PUBLIC_KEY_B64;
		}
		if (value == null || value.trim().length() == 0) {
			throw new IllegalStateException("server public key pin is not configured");
		}
		return decodePublicKey(value);
	}

	public static byte[] decodePublicKey(String value) {
		byte[] key = Base64Codec.decode(value == null ? "" : value.trim());
		if (key.length != 32) {
			throw new IllegalArgumentException("server public key must be 32 bytes");
		}
		return key;
	}

	public static String fingerprint(byte[] key) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(key == null ? new byte[0] : key);
			StringBuilder out = new StringBuilder(23);
			for (int i = 0; i < 8; i++) {
				if (i > 0) {
					out.append(':');
				}
				int b = hash[i] & 0xff;
				if (b < 16) {
					out.append('0');
				}
				out.append(Integer.toHexString(b));
			}
			return out.toString();
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
