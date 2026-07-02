package rs.ove.crypt.proto;

import java.security.SecureRandom;

final class CryptoRandom {
	private static final SecureRandom RNG = new SecureRandom();

	private CryptoRandom() {
	}

	static void nextBytes(byte[] out) {
		synchronized (RNG) {
			RNG.nextBytes(out);
		}
	}

	static SecureRandom instance() {
		return RNG;
	}
}
