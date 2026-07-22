package rs.ove.crypt.proto;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** MicroMsg Secure Transport v4: Noise NK + authenticated padded records. */
public final class SecureSessionV4 {
	static final int HANDSHAKE_MESSAGE_LENGTH = 48;
	static final int MAX_PAYLOAD = 20 * 1024 * 1024;
	private static final int TAG_LENGTH = 16;
	private static final int PADDING_BLOCK = 256;
	private static final long MAX_RECORDS = 1L << 20;
	private static final long MAX_PLAINTEXT_BYTES = 1L << 30;
	private static final byte[] CLIENT_MAGIC = new byte[] {'R', 'C', 'P', '4'};
	private static final byte[] SERVER_MAGIC = new byte[] {'R', 'S', 'P', '4'};
	private static final byte[] PROTOCOL_NAME = ascii("Noise_NK_25519_ChaChaPoly_SHA256");
	private static final byte[] PROLOGUE = ascii("MicroMsg Secure Transport v4\0RCP4\0RSP4");
	private static final byte[] RECORD_LABEL = ascii("MST4 record");

	private final CipherState seal;
	private final CipherState open;
	private final byte[] handshakeHash;

	private SecureSessionV4(CipherState seal, CipherState open, byte[] handshakeHash) {
		this.seal = seal;
		this.open = open;
		this.handshakeHash = handshakeHash.clone();
	}

	public static SecureSessionV4 client(
			InputStream input,
			OutputStream output,
			byte[] pinnedServerPublicKey
	) throws IOException, GeneralSecurityException {
		ClientHandshake handshake = createClientHandshake(
				V4KeyPair.generate(),
				pinnedServerPublicKey
		);
		writeHandshake(output, CLIENT_MAGIC, handshake.message);
		byte[] serverMessage = readHandshake(input, SERVER_MAGIC);
		return finishClientHandshake(handshake, serverMessage);
	}

	public static ClientHello createClientHello() throws IOException, GeneralSecurityException {
		ClientHandshake handshake = createClientHandshake(
				V4KeyPair.generate(),
				CryptIdentity.serverPublicKey()
		);
		byte[] envelope = new byte[6 + handshake.message.length];
		System.arraycopy(CLIENT_MAGIC, 0, envelope, 0, 4);
		envelope[4] = 0;
		envelope[5] = (byte)HANDSHAKE_MESSAGE_LENGTH;
		System.arraycopy(handshake.message, 0, envelope, 6, handshake.message.length);
		return new ClientHello(handshake, envelope);
	}

	public static SecureSessionV4 openClient(ClientHello hello, byte[] serverEnvelope)
			throws IOException, GeneralSecurityException {
		if (hello == null || serverEnvelope == null
				|| serverEnvelope.length != 6 + HANDSHAKE_MESSAGE_LENGTH
				|| !matches(serverEnvelope, 0, SERVER_MAGIC)
				|| (serverEnvelope[4] & 0xff) != 0
				|| (serverEnvelope[5] & 0xff) != HANDSHAKE_MESSAGE_LENGTH) {
			throw new IOException("invalid v4 server handshake envelope");
		}
		return finishClientHandshake(
				hello.handshake,
				Arrays.copyOfRange(serverEnvelope, 6, serverEnvelope.length)
		);
	}

	static ClientHandshake createClientHandshake(
			byte[] clientPrivateKey,
			byte[] pinnedServerPublicKey
	) throws IOException, GeneralSecurityException {
		return createClientHandshake(V4KeyPair.fromPrivate(clientPrivateKey), pinnedServerPublicKey);
	}

	private static ClientHandshake createClientHandshake(
			V4KeyPair local,
			byte[] pinnedServerPublicKey
	) throws IOException, GeneralSecurityException {
		if (pinnedServerPublicKey == null || pinnedServerPublicKey.length != 32) {
			throw new IOException("server public key pin is not configured");
		}
		SymmetricState state = new SymmetricState(pinnedServerPublicKey);
		byte[] clientPublic = local.publicKey;
		state.mixHash(clientPublic);
		byte[] es;
		try {
			es = local.shared(pinnedServerPublicKey);
		} catch (IllegalArgumentException ex) {
			throw new IOException("invalid v4 server static key", ex);
		}
		requireNonzeroDh(es);
		state.mixKey(es);
		byte[] tag = state.encryptAndHash(new byte[0]);
		return new ClientHandshake(local, state, concat(clientPublic, tag));
	}

	static SecureSessionV4 finishClientHandshake(
			ClientHandshake handshake,
			byte[] serverMessage
	) throws IOException, GeneralSecurityException {
		if (serverMessage == null || serverMessage.length != HANDSHAKE_MESSAGE_LENGTH) {
			throw new IOException("invalid v4 server handshake length");
		}
		byte[] serverEphemeral = Arrays.copyOfRange(serverMessage, 0, 32);
		handshake.state.mixHash(serverEphemeral);
		byte[] ee;
		try {
			ee = handshake.local.shared(serverEphemeral);
		} catch (IllegalArgumentException ex) {
			throw new IOException("invalid v4 server ephemeral key", ex);
		}
		requireNonzeroDh(ee);
		handshake.state.mixKey(ee);
		byte[] payload = handshake.state.decryptAndHash(
				Arrays.copyOfRange(serverMessage, 32, serverMessage.length)
		);
		if (payload.length != 0) {
			throw new IOException("v4 server handshake payload must be empty");
		}
		CipherState[] split = handshake.state.split();
		return new SecureSessionV4(split[0], split[1], handshake.state.handshakeHash);
	}

	public void writeEncryptedFrame(OutputStream output, byte[] payload)
			throws IOException, GeneralSecurityException {
		byte[] record = sealApplicationRecord(payload);
		writeInt(output, record.length);
		output.write(record);
		output.flush();
	}

	public byte[] readEncryptedFrame(InputStream input)
			throws IOException, GeneralSecurityException {
		int length = readInt(input);
		if (length < 8 + TAG_LENGTH || length > maxRecordLength()) {
			throw new IOException("invalid v4 encrypted frame length");
		}
		byte[] record = new byte[length];
		readFully(input, record, 0, length);
		DecodedRecord decoded = openRecord(record);
		if (decoded.contentType == 1) {
			throw new EOFException("authenticated v4 close");
		}
		return decoded.payload;
	}

	public byte[] sealApplicationRecord(byte[] payload)
			throws IOException, GeneralSecurityException {
		return sealRecord(0, payload == null ? new byte[0] : payload);
	}

	public byte[] openApplicationRecord(byte[] record)
			throws IOException, GeneralSecurityException {
		DecodedRecord decoded = openRecord(record);
		if (decoded.contentType != 0) {
			throw new EOFException("authenticated v4 close");
		}
		return decoded.payload;
	}

	public byte[] sealCloseRecord() throws IOException, GeneralSecurityException {
		return sealRecord(1, new byte[0]);
	}

	private byte[] sealRecord(int contentType, byte[] payload)
			throws IOException, GeneralSecurityException {
		byte[] plaintext = encodePlaintext(contentType, payload);
		seal.checkLimit(plaintext.length);
		long sequence = seal.nonce;
		int frameLength = 8 + plaintext.length + TAG_LENGTH;
		byte[] aad = recordAad(handshakeHash, frameLength, sequence);
		byte[] ciphertext = aead(true, seal.key, sequence, aad, plaintext);
		byte[] record = new byte[frameLength];
		writeLong(record, 0, sequence);
		System.arraycopy(ciphertext, 0, record, 8, ciphertext.length);
		seal.commit(plaintext.length);
		return record;
	}

	private DecodedRecord openRecord(byte[] record)
			throws IOException, GeneralSecurityException {
		if (record == null || record.length < 8 + TAG_LENGTH || record.length > maxRecordLength()) {
			throw new IOException("invalid v4 encrypted record length");
		}
		long sequence = readLong(record, 0);
		if (sequence != open.nonce) {
			throw new IOException("v4 encrypted record sequence mismatch");
		}
		int plaintextLength = record.length - 8 - TAG_LENGTH;
		open.checkLimit(plaintextLength);
		byte[] aad = recordAad(handshakeHash, record.length, sequence);
		byte[] plaintext = aead(
				false,
				open.key,
				sequence,
				aad,
				Arrays.copyOfRange(record, 8, record.length)
		);
		DecodedRecord decoded = decodePlaintext(plaintext);
		open.commit(plaintext.length);
		return decoded;
	}

	private static byte[] encodePlaintext(int contentType, byte[] payload) throws IOException {
		if (contentType < 0 || contentType > 1 || (contentType == 1 && payload.length != 0)) {
			throw new IOException("invalid v4 content type");
		}
		if (payload.length > MAX_PAYLOAD) {
			throw new IOException("v4 payload is too large");
		}
		int base = 5 + payload.length;
		int padded = ((base + PADDING_BLOCK - 1) / PADDING_BLOCK) * PADDING_BLOCK;
		byte[] plaintext = new byte[padded];
		plaintext[0] = (byte)contentType;
		writeInt(plaintext, 1, payload.length);
		System.arraycopy(payload, 0, plaintext, 5, payload.length);
		return plaintext;
	}

	private static DecodedRecord decodePlaintext(byte[] plaintext) throws IOException {
		if (plaintext.length < PADDING_BLOCK || plaintext.length % PADDING_BLOCK != 0) {
			throw new IOException("invalid v4 plaintext padding length");
		}
		int contentType = plaintext[0] & 0xff;
		int payloadLength = readInt(plaintext, 1);
		if (payloadLength < 0 || payloadLength > MAX_PAYLOAD || 5 + payloadLength > plaintext.length) {
			throw new IOException("invalid v4 payload length");
		}
		int paddingDiff = 0;
		for (int i = 5 + payloadLength; i < plaintext.length; i++) {
			paddingDiff |= plaintext[i] & 0xff;
		}
		if (paddingDiff != 0) {
			throw new IOException("invalid v4 record padding");
		}
		if (contentType == 1 && payloadLength == 0) {
			return new DecodedRecord(contentType, new byte[0]);
		}
		if (contentType != 0) {
			throw new IOException("invalid v4 record content type");
		}
		return new DecodedRecord(0, Arrays.copyOfRange(plaintext, 5, 5 + payloadLength));
	}

	private static byte[] recordAad(byte[] handshakeHash, int frameLength, long sequence) {
		byte[] aad = new byte[RECORD_LABEL.length + 32 + 4 + 8];
		int offset = 0;
		System.arraycopy(RECORD_LABEL, 0, aad, offset, RECORD_LABEL.length);
		offset += RECORD_LABEL.length;
		System.arraycopy(handshakeHash, 0, aad, offset, 32);
		offset += 32;
		writeInt(aad, offset, frameLength);
		offset += 4;
		writeLong(aad, offset, sequence);
		return aad;
	}

	private static byte[] aead(
			boolean encrypt,
			byte[] key,
			long nonce,
			byte[] aad,
			byte[] input
	) throws GeneralSecurityException {
		ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
		cipher.init(
				encrypt,
				new AEADParameters(new KeyParameter(key), 128, noiseNonce(nonce), aad)
		);
		byte[] output = new byte[cipher.getOutputSize(input.length)];
		int count = cipher.processBytes(input, 0, input.length, output, 0);
		try {
			count += cipher.doFinal(output, count);
		} catch (InvalidCipherTextException ex) {
			throw new GeneralSecurityException("v4 authentication failed", ex);
		}
		return count == output.length ? output : Arrays.copyOf(output, count);
	}

	private static byte[] noiseNonce(long nonce) {
		byte[] value = new byte[12];
		for (int i = 0; i < 8; i++) {
			value[4 + i] = (byte)(nonce >>> (i * 8));
		}
		return value;
	}

	private static void writeHandshake(OutputStream output, byte[] magic, byte[] message)
			throws IOException {
		if (message.length != HANDSHAKE_MESSAGE_LENGTH) {
			throw new IOException("invalid v4 handshake message length");
		}
		output.write(magic);
		output.write(0);
		output.write(HANDSHAKE_MESSAGE_LENGTH);
		output.write(message);
		output.flush();
	}

	private static byte[] readHandshake(InputStream input, byte[] expectedMagic) throws IOException {
		byte[] magic = new byte[4];
		readFully(input, magic, 0, magic.length);
		if (!Arrays.equals(magic, expectedMagic)) {
			throw new IOException("invalid v4 handshake magic");
		}
		int length = (readByte(input) << 8) | readByte(input);
		if (length != HANDSHAKE_MESSAGE_LENGTH) {
			throw new IOException("invalid v4 handshake message length");
		}
		byte[] message = new byte[length];
		readFully(input, message, 0, length);
		return message;
	}

	private static void requireNonzeroDh(byte[] shared) throws IOException {
		int combined = 0;
		for (int i = 0; i < shared.length; i++) combined |= shared[i] & 0xff;
		if (combined == 0) throw new IOException("invalid v4 X25519 shared secret");
	}

	private static int maxRecordLength() {
		int base = 5 + MAX_PAYLOAD;
		int padded = ((base + PADDING_BLOCK - 1) / PADDING_BLOCK) * PADDING_BLOCK;
		return 8 + padded + TAG_LENGTH;
	}

	private static byte[][] hkdf(byte[] chainingKey, byte[] ikm, int count)
			throws GeneralSecurityException {
		byte[] temporaryKey = hmac(chainingKey, ikm);
		byte[][] output = new byte[count][];
		byte[] previous = new byte[0];
		for (int i = 0; i < count; i++) {
			previous = hmac(temporaryKey, concat(previous, new byte[] {(byte)(i + 1)}));
			output[i] = previous;
		}
		return output;
	}

	private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	private static byte[] sha256(byte[] data) throws GeneralSecurityException {
		return MessageDigest.getInstance("SHA-256").digest(data);
	}

	private static byte[] concat(byte[]... values) {
		int length = 0;
		for (int i = 0; i < values.length; i++) length += values[i].length;
		byte[] output = new byte[length];
		int offset = 0;
		for (int i = 0; i < values.length; i++) {
			System.arraycopy(values[i], 0, output, offset, values[i].length);
			offset += values[i].length;
		}
		return output;
	}

	private static boolean matches(byte[] input, int offset, byte[] expected) {
		if (offset < 0 || input.length - offset < expected.length) return false;
		int diff = 0;
		for (int i = 0; i < expected.length; i++) diff |= input[offset + i] ^ expected[i];
		return diff == 0;
	}

	private static byte[] ascii(String value) {
		try {
			return value.getBytes("US-ASCII");
		} catch (java.io.UnsupportedEncodingException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static void readFully(InputStream input, byte[] output, int offset, int length)
			throws IOException {
		int done = 0;
		while (done < length) {
			int count = input.read(output, offset + done, length - done);
			if (count < 0) throw new EOFException("unexpected end of v4 stream");
			if (count == 0) continue;
			done += count;
		}
	}

	private static int readByte(InputStream input) throws IOException {
		int value = input.read();
		if (value < 0) throw new EOFException("unexpected end of v4 stream");
		return value;
	}

	private static int readInt(InputStream input) throws IOException {
		return (readByte(input) << 24)
				| (readByte(input) << 16)
				| (readByte(input) << 8)
				| readByte(input);
	}

	private static int readInt(byte[] input, int offset) {
		return ((input[offset] & 0xff) << 24)
				| ((input[offset + 1] & 0xff) << 16)
				| ((input[offset + 2] & 0xff) << 8)
				| (input[offset + 3] & 0xff);
	}

	private static void writeInt(OutputStream output, int value) throws IOException {
		output.write((value >>> 24) & 0xff);
		output.write((value >>> 16) & 0xff);
		output.write((value >>> 8) & 0xff);
		output.write(value & 0xff);
	}

	private static void writeInt(byte[] output, int offset, int value) {
		output[offset] = (byte)(value >>> 24);
		output[offset + 1] = (byte)(value >>> 16);
		output[offset + 2] = (byte)(value >>> 8);
		output[offset + 3] = (byte)value;
	}

	private static long readLong(byte[] input, int offset) {
		long value = 0;
		for (int i = 0; i < 8; i++) value = (value << 8) | (input[offset + i] & 0xffL);
		return value;
	}

	private static void writeLong(byte[] output, int offset, long value) {
		for (int i = 7; i >= 0; i--) {
			output[offset + i] = (byte)value;
			value >>>= 8;
		}
	}

	static final class ClientHandshake {
		final V4KeyPair local;
		final SymmetricState state;
		final byte[] message;

		private ClientHandshake(V4KeyPair local, SymmetricState state, byte[] message) {
			this.local = local;
			this.state = state;
			this.message = message;
		}
	}

	public static final class ClientHello {
		private final ClientHandshake handshake;
		private final byte[] message;

		private ClientHello(ClientHandshake handshake, byte[] message) {
			this.handshake = handshake;
			this.message = message.clone();
		}

		public byte[] message() {
			return message.clone();
		}
	}

	private static final class V4KeyPair {
		private final X25519PrivateKeyParameters privateKey;
		private final byte[] publicKey;

		private V4KeyPair(X25519PrivateKeyParameters privateKey) {
			this.privateKey = privateKey;
			this.publicKey = privateKey.generatePublicKey().getEncoded();
		}

		private static V4KeyPair generate() {
			return new V4KeyPair(new X25519PrivateKeyParameters(CryptoRandom.instance()));
		}

		private static V4KeyPair fromPrivate(byte[] privateKey) {
			if (privateKey == null || privateKey.length != 32) {
				throw new IllegalArgumentException("X25519 private key must be 32 bytes");
			}
			return new V4KeyPair(new X25519PrivateKeyParameters(privateKey));
		}

		private byte[] shared(byte[] remotePublic) {
			if (remotePublic == null || remotePublic.length != 32) {
				throw new IllegalArgumentException("X25519 public key must be 32 bytes");
			}
			byte[] shared = new byte[32];
			privateKey.generateSecret(new X25519PublicKeyParameters(remotePublic), shared, 0);
			return shared;
		}
	}

	private static final class SymmetricState {
		private byte[] chainingKey;
		private byte[] handshakeHash;
		private byte[] cipherKey;
		private long cipherNonce;

		private SymmetricState(byte[] serverStaticPublic) throws GeneralSecurityException {
			handshakeHash = PROTOCOL_NAME.length <= 32
					? Arrays.copyOf(PROTOCOL_NAME, 32)
					: sha256(PROTOCOL_NAME);
			chainingKey = handshakeHash.clone();
			mixHash(PROLOGUE);
			mixHash(serverStaticPublic);
		}

		private void mixHash(byte[] data) throws GeneralSecurityException {
			handshakeHash = sha256(concat(handshakeHash, data));
		}

		private void mixKey(byte[] ikm) throws GeneralSecurityException {
			byte[][] output = hkdf(chainingKey, ikm, 2);
			chainingKey = output[0];
			cipherKey = output[1];
			cipherNonce = 0;
		}

		private byte[] encryptAndHash(byte[] plaintext) throws GeneralSecurityException {
			byte[] ciphertext = cipherKey == null
					? plaintext.clone()
					: aead(true, cipherKey, cipherNonce++, handshakeHash, plaintext);
			mixHash(ciphertext);
			return ciphertext;
		}

		private byte[] decryptAndHash(byte[] ciphertext) throws GeneralSecurityException {
			byte[] plaintext = cipherKey == null
					? ciphertext.clone()
					: aead(false, cipherKey, cipherNonce, handshakeHash, ciphertext);
			if (cipherKey != null) cipherNonce++;
			mixHash(ciphertext);
			return plaintext;
		}

		private CipherState[] split() throws GeneralSecurityException {
			byte[][] output = hkdf(chainingKey, new byte[0], 2);
			return new CipherState[] {new CipherState(output[0]), new CipherState(output[1])};
		}
	}

	private static final class CipherState {
		private final byte[] key;
		private long nonce;
		private long plaintextBytes;

		private CipherState(byte[] key) {
			this.key = key.clone();
		}

		private void checkLimit(int plaintextLength) throws IOException {
			if (nonce >= MAX_RECORDS) throw new IOException("v4 record limit reached; reconnect required");
			if (plaintextLength < 0 || plaintextBytes > MAX_PLAINTEXT_BYTES - plaintextLength) {
				throw new IOException("v4 byte limit reached; reconnect required");
			}
		}

		private void commit(int plaintextLength) {
			plaintextBytes += plaintextLength;
			nonce++;
		}
	}

	private static final class DecodedRecord {
		private final int contentType;
		private final byte[] payload;

		private DecodedRecord(int contentType, byte[] payload) {
			this.contentType = contentType;
			this.payload = payload;
		}
	}
}
