package rs.ove.crypt.proto;

import java.io.ByteArrayOutputStream;

public final class Base64Codec {
	private static final char[] ENC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
	private static final int[] DEC = new int[256];

	static {
		for (int i = 0; i < DEC.length; i++) {
			DEC[i] = -1;
		}
		for (int i = 0; i < ENC.length; i++) {
			DEC[ENC[i]] = i;
		}
		DEC['='] = -2;
	}

	private Base64Codec() {
	}

	public static String encode(byte[] input) {
		if (input == null || input.length == 0) {
			return "";
		}
		StringBuilder out = new StringBuilder(((input.length + 2) / 3) * 4);
		int i = 0;
		while (i < input.length) {
			int b0 = input[i++] & 0xff;
			int b1 = i < input.length ? input[i++] & 0xff : -1;
			int b2 = i < input.length ? input[i++] & 0xff : -1;
			out.append(ENC[b0 >>> 2]);
			out.append(ENC[((b0 & 3) << 4) | (b1 < 0 ? 0 : b1 >>> 4)]);
			out.append(b1 < 0 ? '=' : ENC[((b1 & 15) << 2) | (b2 < 0 ? 0 : b2 >>> 6)]);
			out.append(b2 < 0 ? '=' : ENC[b2 & 63]);
		}
		return out.toString();
	}

	public static byte[] decode(String input) {
		if (input == null || input.length() == 0) {
			return new byte[0];
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(input.length() * 3 / 4);
		int[] block = new int[4];
		int count = 0;
		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);
			if (ch > 255) {
				throw new IllegalArgumentException("invalid base64 character");
			}
			int value = DEC[ch];
			if (value == -1) {
				if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
					continue;
				}
				throw new IllegalArgumentException("invalid base64 character");
			}
			block[count++] = value;
			if (count == 4) {
				decodeBlock(block, out);
				count = 0;
			}
		}
		if (count != 0) {
			throw new IllegalArgumentException("invalid base64 length");
		}
		return out.toByteArray();
	}

	private static void decodeBlock(int[] block, ByteArrayOutputStream out) {
		if (block[0] < 0 || block[1] < 0 || block[2] == -2 && block[3] != -2) {
			throw new IllegalArgumentException("invalid base64 padding");
		}
		int b0 = block[0];
		int b1 = block[1];
		int b2 = block[2] == -2 ? 0 : block[2];
		int b3 = block[3] == -2 ? 0 : block[3];
		out.write((b0 << 2) | (b1 >>> 4));
		if (block[2] != -2) {
			out.write(((b1 & 15) << 4) | (b2 >>> 2));
		}
		if (block[3] != -2) {
			out.write(((b2 & 3) << 6) | b3);
		}
	}
}
