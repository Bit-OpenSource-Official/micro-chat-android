package rs.ove.crypt.proto;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptSession {
	public static final int MAX_FRAME_SIZE = 64 * 1024 * 1024;
	private static final byte[] CLIENT_MAGIC = new byte[] {'R', 'C', 'P', '3'};
	private static final byte[] SERVER_MAGIC = new byte[] {'R', 'S', 'P', '3'};
	private static final int FAST_TAG_LENGTH = 16;
	private static final int SEQ_LENGTH = 8;
	private static final int IV_LENGTH = 16;
	private static final String AES_CTR = "AES/CTR/NoPadding";
	private static final String AES_ECB = "AES/ECB/NoPadding";
	private static final String HMAC_SHA256 = "HmacSHA256";

	private final SecretKeySpec sealKey;
	private final SecretKeySpec sealMacKey;
	private final SecretKeySpec openKey;
	private final SecretKeySpec openMacKey;
	private final byte[] sealNonce;
	private final byte[] openNonce;
	private final AtomicLong sealSeq = new AtomicLong();
	private final AtomicLong openSeq = new AtomicLong();
	private final Object sealLock = new Object();
	private final Object openLock = new Object();
	private Cipher sealCtrCipher;
	private Cipher openCtrCipher;
	private Mac sealMac;
	private Mac openMac;

	private CryptSession(KeyMaterial keys) {
		sealKey = new SecretKeySpec(keys.clientEnc, "AES");
		sealMacKey = new SecretKeySpec(keys.clientMac, HMAC_SHA256);
		openKey = new SecretKeySpec(keys.serverEnc, "AES");
		openMacKey = new SecretKeySpec(keys.serverMac, HMAC_SHA256);
		sealNonce = Arrays.copyOf(keys.clientNonce, 4);
		openNonce = Arrays.copyOf(keys.serverNonce, 4);
	}

	public static ClientHello createClientHello() {
		X25519.KeyPair keyPair = X25519.generate(CryptoRandom.instance());
		byte[] publicKey = keyPair.publicKey();
		return new ClientHello(keyPair, writeHello(CLIENT_MAGIC, publicKey), publicKey);
	}

	public static CryptSession client(InputStream input, OutputStream output) throws IOException, GeneralSecurityException {
		return client(input, output, CryptIdentity.serverPublicKey());
	}

	public static CryptSession client(InputStream input, OutputStream output, SecureRandom random) throws IOException, GeneralSecurityException {
		return client(input, output, CryptIdentity.serverPublicKey());
	}

	public static CryptSession client(InputStream input, OutputStream output, byte[] pinnedServerPublicKey) throws IOException, GeneralSecurityException {
		ClientHello hello = createClientHello();
		output.write(hello.message());
		output.flush();
		byte[] serverHello = readHello(input, SERVER_MAGIC);
		return openClient(hello, serverHello, pinnedServerPublicKey);
	}

	public static CryptSession openClient(ClientHello hello, byte[] serverHello) throws IOException, GeneralSecurityException {
		return openClient(hello, serverHello, CryptIdentity.serverPublicKey());
	}

	public static CryptSession openClient(ClientHello hello, byte[] serverHello, SecureRandom random) throws IOException, GeneralSecurityException {
		return openClient(hello, serverHello, CryptIdentity.serverPublicKey());
	}

	public static CryptSession openClient(ClientHello hello, byte[] serverHello, byte[] pinnedServerPublicKey) throws IOException, GeneralSecurityException {
		byte[] payload = parseHello(serverHello, SERVER_MAGIC);
		if (payload.length != 80) {
			throw new IOException("invalid v3 server hello length");
		}
		byte[] staticServerPublic = Arrays.copyOfRange(payload, 0, 32);
		byte[] ephemeralServerPublic = Arrays.copyOfRange(payload, 32, 64);
		byte[] serverTag = Arrays.copyOfRange(payload, 64, 80);
		verifyPinnedServerKey(staticServerPublic, pinnedServerPublicKey);
		KeyMaterial keys = deriveV3(
				hello.keyPair(), hello.publicKey(), staticServerPublic,
				ephemeralServerPublic, serverTag
		);
		return new CryptSession(keys);
	}

	public byte[] seal(byte[] plain) throws IOException, GeneralSecurityException {
		byte[] input = plain == null ? new byte[0] : plain;
		long seq = sealSeq.getAndIncrement();
		byte[] sealed = new byte[SEQ_LENGTH + input.length + FAST_TAG_LENGTH];
		writeLong(sealed, 0, seq);
		synchronized (sealLock) {
			sealCtrCipher = ctrCrypt(sealCtrCipher, sealKey, frameIv(sealNonce, seq), input, 0, input.length, sealed, SEQ_LENGTH);
			sealMac = ensureMac(sealMac, sealMacKey);
			byte[] tag = hmac(sealMac, sealed, 0, SEQ_LENGTH + input.length);
			System.arraycopy(tag, 0, sealed, SEQ_LENGTH + input.length, FAST_TAG_LENGTH);
		}
		return sealed;
	}

	public byte[] open(byte[] sealed) throws IOException, GeneralSecurityException {
		if (sealed == null || sealed.length < SEQ_LENGTH + FAST_TAG_LENGTH) {
			throw new IOException("encrypted frame is too small");
		}
		long seq = readLong(sealed, 0);
		long expected = openSeq.get();
		if (seq != expected) {
			throw new IOException("encrypted frame sequence mismatch");
		}
		int cipherLength = sealed.length - SEQ_LENGTH - FAST_TAG_LENGTH;
		byte[] plain = new byte[cipherLength];
		synchronized (openLock) {
			openMac = ensureMac(openMac, openMacKey);
			byte[] expectedTag = hmac(openMac, sealed, 0, SEQ_LENGTH + cipherLength);
			if (!tagEquals(expectedTag, sealed, SEQ_LENGTH + cipherLength, FAST_TAG_LENGTH)) {
				throw new IOException("encrypted frame authentication failed");
			}
			openCtrCipher = ctrCrypt(openCtrCipher, openKey, frameIv(openNonce, seq), sealed, SEQ_LENGTH, cipherLength, plain, 0);
		}
		openSeq.incrementAndGet();
		return plain;
	}

	public void writeEncryptedFrame(OutputStream output, byte[] plain) throws IOException, GeneralSecurityException {
		writeRawFrame(output, seal(plain));
	}

	public byte[] readEncryptedFrame(InputStream input) throws IOException, GeneralSecurityException {
		return open(readRawFrame(input, MAX_FRAME_SIZE));
	}

	public static void writeRawFrame(OutputStream output, byte[] frame) throws IOException {
		byte[] data = frame == null ? new byte[0] : frame;
		if (data.length > MAX_FRAME_SIZE) {
			throw new IOException("frame is too large");
		}
		output.write((data.length >>> 24) & 0xff);
		output.write((data.length >>> 16) & 0xff);
		output.write((data.length >>> 8) & 0xff);
		output.write(data.length & 0xff);
		output.write(data);
		output.flush();
	}

	public static byte[] readRawFrame(InputStream input, int maxSize) throws IOException {
		int b0 = readByte(input);
		int b1 = readByte(input);
		int b2 = readByte(input);
		int b3 = readByte(input);
		int length = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
		if (length < 0 || length > maxSize) {
			throw new IOException("frame is too large");
		}
		byte[] data = new byte[length];
		readFully(input, data, 0, data.length);
		return data;
	}

	private static void verifyPinnedServerKey(byte[] serverPublic, byte[] pinnedServerPublicKey) throws IOException {
		if (pinnedServerPublicKey == null || pinnedServerPublicKey.length != 32) {
			throw new IOException("server public key pin is not configured");
		}
		if (!Arrays.equals(serverPublic, pinnedServerPublicKey)) {
			throw new IOException(
					"server public key mismatch: got "
							+ CryptIdentity.fingerprint(serverPublic)
							+ " expected "
							+ CryptIdentity.fingerprint(pinnedServerPublicKey)
			);
		}
	}

	private static KeyMaterial deriveV3(
			X25519.KeyPair local,
			byte[] clientPublic,
			byte[] staticServerPublic,
			byte[] ephemeralServerPublic,
			byte[] serverTag
	) throws GeneralSecurityException, IOException {
		if (staticServerPublic == null || staticServerPublic.length != 32 ||
				ephemeralServerPublic == null || ephemeralServerPublic.length != 32) {
			throw new IOException("invalid X25519 server public key length");
		}
		byte[] staticShared;
		byte[] ephemeralShared;
		try {
			staticShared = X25519.shared(local.privateKey(), staticServerPublic);
			ephemeralShared = X25519.shared(local.privateKey(), ephemeralServerPublic);
		} catch (IllegalArgumentException ex) {
			throw new IOException("invalid X25519 server public key", ex);
		}
		if (allZero(staticShared) || allZero(ephemeralShared)) {
			throw new IOException("invalid X25519 shared secret");
		}
		byte[] transcript = concat(
				ascii("micromsg-v3"), clientPublic,
				staticServerPublic, ephemeralServerPublic
		);
		MessageDigest sha = MessageDigest.getInstance("SHA-256");
		byte[] salt = sha.digest(transcript);
		byte[] staticPrk = hmac(
				new SecretKeySpec(salt, HMAC_SHA256),
				staticShared, 0, staticShared.length
		);
		byte[] authKey = hkdf(staticPrk, "micromsg v3 server auth", 32);
		byte[] expectedTag = hmac(
				new SecretKeySpec(authKey, HMAC_SHA256),
				transcript, 0, transcript.length
		);
		if (!tagEquals(expectedTag, serverTag, 0, 16)) {
			throw new IOException("server handshake authentication failed");
		}
		byte[] ikm = concat(staticShared, ephemeralShared);
		byte[] prk = hmac(new SecretKeySpec(salt, HMAC_SHA256), ikm, 0, ikm.length);
		return new KeyMaterial(
				hkdf(prk, "micromsg v3 client enc", 32),
				hkdf(prk, "micromsg v3 client mac", 32),
				hkdf(prk, "micromsg v3 server enc", 32),
				hkdf(prk, "micromsg v3 server mac", 32),
				hkdf(prk, "micromsg v3 client nonce", 4),
				hkdf(prk, "micromsg v3 server nonce", 4)
		);
	}

	private static boolean allZero(byte[] value) {
		int combined = 0;
		for (int i = 0; i < value.length; i++) {
			combined |= value[i] & 0xff;
		}
		return combined == 0;
	}

	private static byte[] concat(byte[]... values) {
		int size = 0;
		for (int i = 0; i < values.length; i++) {
			size += values[i].length;
		}
		byte[] out = new byte[size];
		int offset = 0;
		for (int i = 0; i < values.length; i++) {
			System.arraycopy(values[i], 0, out, offset, values[i].length);
			offset += values[i].length;
		}
		return out;
	}

	private static byte[] hmac(SecretKeySpec key, byte[] data, int offset, int length) throws GeneralSecurityException {
		Mac mac = Mac.getInstance(HMAC_SHA256);
		mac.init(key);
		mac.update(data, offset, length);
		return mac.doFinal();
	}

	private static Mac ensureMac(Mac mac, SecretKeySpec key) throws GeneralSecurityException {
		if (mac == null) {
			mac = Mac.getInstance(HMAC_SHA256);
			mac.init(key);
		}
		return mac;
	}

	private static byte[] hmac(Mac mac, byte[] data, int offset, int length) {
		mac.reset();
		mac.update(data, offset, length);
		return mac.doFinal();
	}

	private static byte[] hkdf(byte[] prk, String info, int length) throws GeneralSecurityException {
		byte[] infoBytes = ascii(info);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] previous = new byte[0];
		int counter = 1;
		while (out.size() < length) {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(prk, HMAC_SHA256));
			mac.update(previous);
			mac.update(infoBytes);
			mac.update((byte)counter);
			previous = mac.doFinal();
			out.write(previous, 0, Math.min(previous.length, length - out.size()));
			counter++;
		}
		return out.toByteArray();
	}

	private static byte[] frameIv(byte[] nonce, long seq) {
		byte[] iv = new byte[IV_LENGTH];
		System.arraycopy(nonce, 0, iv, 0, 4);
		writeLong(iv, 4, seq);
		iv[12] = 0;
		iv[13] = 0;
		iv[14] = 0;
		iv[15] = 0;
		return iv;
	}

	private static void ctrCrypt(SecretKeySpec key, byte[] iv, byte[] input, int inputOffset, int length, byte[] output, int outputOffset) throws GeneralSecurityException {
		ctrCrypt(null, key, iv, input, inputOffset, length, output, outputOffset);
	}

	private static Cipher ctrCrypt(Cipher cipher, SecretKeySpec key, byte[] iv, byte[] input, int inputOffset, int length, byte[] output, int outputOffset) throws GeneralSecurityException {
		if (length == 0) {
			return cipher;
		}
		try {
			if (cipher == null) {
				cipher = Cipher.getInstance(AES_CTR);
			}
			cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
			int written = cipher.update(input, inputOffset, length, output, outputOffset);
			try {
				written += cipher.doFinal(output, outputOffset + written);
			} catch (javax.crypto.ShortBufferException ex) {
				throw new GeneralSecurityException(ex);
			}
			if (written != length) {
				throw new GeneralSecurityException("AES CTR output length mismatch");
			}
			return cipher;
		} catch (java.security.NoSuchAlgorithmException ex) {
			ctrCryptEcb(key, iv, input, inputOffset, length, output, outputOffset);
		} catch (javax.crypto.NoSuchPaddingException ex) {
			ctrCryptEcb(key, iv, input, inputOffset, length, output, outputOffset);
		}
		return null;
	}

	private static void ctrCryptEcb(SecretKeySpec key, byte[] iv, byte[] input, int inputOffset, int length, byte[] output, int outputOffset) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(AES_ECB);
		cipher.init(Cipher.ENCRYPT_MODE, key);
		byte[] counter = iv.clone();
		byte[] stream = new byte[IV_LENGTH];
		int done = 0;
		while (done < length) {
			int produced = cipher.update(counter, 0, IV_LENGTH, stream, 0);
			if (produced != IV_LENGTH) {
				stream = cipher.doFinal(counter);
			}
			int block = Math.min(IV_LENGTH, length - done);
			for (int i = 0; i < block; i++) {
				output[outputOffset + done + i] = (byte)(input[inputOffset + done + i] ^ stream[i]);
			}
			done += block;
			incrementCounter(counter);
		}
		cipher.doFinal();
	}

	private static void incrementCounter(byte[] counter) throws GeneralSecurityException {
		for (int i = counter.length - 1; i >= 12; i--) {
			counter[i]++;
			if (counter[i] != 0) {
				return;
			}
		}
		throw new GeneralSecurityException("AES CTR frame counter overflow");
	}

	private static boolean tagEquals(byte[] expected, byte[] actual, int actualOffset, int length) {
		if (expected.length < length || actualOffset < 0 || actualOffset + length > actual.length) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < length; i++) {
			diff |= expected[i] ^ actual[actualOffset + i];
		}
		return diff == 0;
	}

	private static byte[] writeHello(byte[] magic, byte[] publicKey) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(6 + publicKey.length);
		out.write(magic, 0, magic.length);
		out.write((publicKey.length >>> 8) & 0xff);
		out.write(publicKey.length & 0xff);
		out.write(publicKey, 0, publicKey.length);
		return out.toByteArray();
	}

	private static byte[] readHello(InputStream input, byte[] expectedMagic) throws IOException {
		byte[] magic = new byte[4];
		readFully(input, magic, 0, magic.length);
		if (!Arrays.equals(magic, expectedMagic)) {
			throw new IOException("invalid crypto handshake magic");
		}
		int length = (readByte(input) << 8) | readByte(input);
		if (length <= 0 || length > 4096) {
			throw new IOException("invalid crypto public key length");
		}
		byte[] publicKey = new byte[length];
		readFully(input, publicKey, 0, publicKey.length);
		return writeHello(expectedMagic, publicKey);
	}

	private static byte[] parseHello(byte[] message, byte[] expectedMagic) throws IOException {
		if (message == null || message.length < 7) {
			throw new IOException("invalid crypto handshake");
		}
		for (int i = 0; i < expectedMagic.length; i++) {
			if (message[i] != expectedMagic[i]) {
				throw new IOException("invalid crypto handshake magic");
			}
		}
		int length = ((message[4] & 0xff) << 8) | (message[5] & 0xff);
		if (length <= 0 || length > 4096 || message.length != 6 + length) {
			throw new IOException("invalid crypto public key length");
		}
		return Arrays.copyOfRange(message, 6, message.length);
	}

	private static int readByte(InputStream input) throws IOException {
		int value = input.read();
		if (value < 0) {
			throw new EOFException();
		}
		return value;
	}

	private static void readFully(InputStream input, byte[] data, int offset, int length) throws IOException {
		while (length > 0) {
			int read = input.read(data, offset, length);
			if (read < 0) {
				throw new EOFException();
			}
			offset += read;
			length -= read;
		}
	}

	private static void writeLong(byte[] out, int offset, long value) {
		for (int i = 7; i >= 0; i--) {
			out[offset + i] = (byte)(value & 0xff);
			value >>>= 8;
		}
	}

	private static long readLong(byte[] data, int offset) {
		long value = 0;
		for (int i = 0; i < 8; i++) {
			value = (value << 8) | (data[offset + i] & 0xffL);
		}
		return value;
	}

	private static byte[] ascii(String value) {
		try {
			return value.getBytes("US-ASCII");
		} catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static final class ClientHello {
		private final X25519.KeyPair keyPair;
		private final byte[] message;
		private final byte[] publicKey;

		private ClientHello(X25519.KeyPair keyPair, byte[] message, byte[] publicKey) {
			this.keyPair = keyPair;
			this.message = message.clone();
			this.publicKey = publicKey.clone();
		}

		public byte[] message() {
			return message.clone();
		}

		X25519.KeyPair keyPair() {
			return keyPair;
		}

		byte[] publicKey() {
			return publicKey.clone();
		}
	}

	private static final class KeyMaterial {
		private final byte[] clientEnc;
		private final byte[] clientMac;
		private final byte[] serverEnc;
		private final byte[] serverMac;
		private final byte[] clientNonce;
		private final byte[] serverNonce;

		private KeyMaterial(byte[] clientEnc, byte[] clientMac, byte[] serverEnc, byte[] serverMac, byte[] clientNonce, byte[] serverNonce) {
			this.clientEnc = clientEnc;
			this.clientMac = clientMac;
			this.serverEnc = serverEnc;
			this.serverMac = serverMac;
			this.clientNonce = clientNonce;
			this.serverNonce = serverNonce;
		}
	}
}
