package rs.ove.crypt.proto;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

final class X25519 {
	private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
	private static final BigInteger A24 = BigInteger.valueOf(121665);
	private static final byte[] BASE_POINT = new byte[] {
		9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	};

	private X25519() {
	}

	static KeyPair generate(SecureRandom random) {
		byte[] privateKey = new byte[32];
		if (random == null) {
			CryptoRandom.nextBytes(privateKey);
		} else {
			random.nextBytes(privateKey);
		}
		clamp(privateKey);
		byte[] publicKey = scalarMult(privateKey, BASE_POINT);
		return new KeyPair(privateKey, publicKey);
	}

	static KeyPair fromPrivate(byte[] privateKey) {
		if (privateKey == null || privateKey.length != 32) {
			throw new IllegalArgumentException("X25519 private key must be 32 bytes");
		}
		byte[] scalar = Arrays.copyOf(privateKey, privateKey.length);
		clamp(scalar);
		return new KeyPair(scalar, scalarMult(scalar, BASE_POINT));
	}

	static byte[] shared(byte[] privateKey, byte[] publicKey) {
		if (privateKey == null || privateKey.length != 32) {
			throw new IllegalArgumentException("X25519 private key must be 32 bytes");
		}
		if (publicKey == null || publicKey.length != 32) {
			throw new IllegalArgumentException("X25519 public key must be 32 bytes");
		}
		return scalarMult(privateKey, publicKey);
	}

	private static byte[] scalarMult(byte[] scalar, byte[] point) {
		byte[] k = Arrays.copyOf(scalar, 32);
		clamp(k);
		BigInteger x1 = decodeU(point);
		BigInteger x2 = BigInteger.ONE;
		BigInteger z2 = BigInteger.ZERO;
		BigInteger x3 = x1;
		BigInteger z3 = BigInteger.ONE;
		int swap = 0;
		for (int t = 254; t >= 0; t--) {
			int kt = ((k[t >>> 3] & 0xff) >>> (t & 7)) & 1;
			swap ^= kt;
			BigInteger[] xSwap = conditionalSwap(x2, x3, swap);
			x2 = xSwap[0];
			x3 = xSwap[1];
			BigInteger[] zSwap = conditionalSwap(z2, z3, swap);
			z2 = zSwap[0];
			z3 = zSwap[1];
			swap = kt;

			BigInteger a = x2.add(z2).mod(P);
			BigInteger aa = a.multiply(a).mod(P);
			BigInteger b = x2.subtract(z2).mod(P);
			BigInteger bb = b.multiply(b).mod(P);
			BigInteger e = aa.subtract(bb).mod(P);
			BigInteger c = x3.add(z3).mod(P);
			BigInteger d = x3.subtract(z3).mod(P);
			BigInteger da = d.multiply(a).mod(P);
			BigInteger cb = c.multiply(b).mod(P);
			BigInteger daPlusCb = da.add(cb).mod(P);
			BigInteger daMinusCb = da.subtract(cb).mod(P);
			x3 = daPlusCb.multiply(daPlusCb).mod(P);
			z3 = x1.multiply(daMinusCb.multiply(daMinusCb).mod(P)).mod(P);
			x2 = aa.multiply(bb).mod(P);
			z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P);
		}
		BigInteger[] xSwap = conditionalSwap(x2, x3, swap);
		x2 = xSwap[0];
		BigInteger[] zSwap = conditionalSwap(z2, z3, swap);
		z2 = zSwap[0];
		if (z2.signum() == 0) {
			return new byte[32];
		}
		return encodeU(x2.multiply(invert(z2)).mod(P));
	}

	private static BigInteger[] conditionalSwap(BigInteger left, BigInteger right, int swap) {
		BigInteger mask = BigInteger.valueOf(-swap);
		BigInteger changed = left.xor(right).and(mask);
		return new BigInteger[] {left.xor(changed), right.xor(changed)};
	}

	private static BigInteger invert(BigInteger value) {
		return value.mod(P).modInverse(P);
	}

	private static void clamp(byte[] k) {
		k[0] = (byte)(k[0] & 248);
		k[31] = (byte)(k[31] & 127);
		k[31] = (byte)(k[31] | 64);
	}

	private static BigInteger decodeU(byte[] in) {
		byte[] copy = Arrays.copyOf(in, 32);
		copy[31] = (byte)(copy[31] & 127);
		byte[] be = new byte[32];
		for (int i = 0; i < 32; i++) {
			be[i] = copy[31 - i];
		}
		return new BigInteger(1, be).mod(P);
	}

	private static byte[] encodeU(BigInteger value) {
		byte[] out = new byte[32];
		byte[] be = value.mod(P).toByteArray();
		int src = be.length - 1;
		int dst = 0;
		while (src >= 0 && dst < out.length) {
			out[dst++] = be[src--];
		}
		return out;
	}

	static final class KeyPair {
		private final byte[] privateKey;
		private final byte[] publicKey;

		private KeyPair(byte[] privateKey, byte[] publicKey) {
			this.privateKey = privateKey.clone();
			this.publicKey = publicKey.clone();
		}

		byte[] privateKey() {
			return privateKey.clone();
		}

		byte[] publicKey() {
			return publicKey.clone();
		}
	}
}
