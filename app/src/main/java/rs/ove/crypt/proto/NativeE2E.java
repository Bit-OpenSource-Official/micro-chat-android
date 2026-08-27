package rs.ove.crypt.proto;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

/** Opaque Android adapter for mst5-client E2E v4. Private keys remain in Rust. */
public final class NativeE2E {
	private NativeE2E() {}

	public static Identity open(Context context, String account, boolean create) throws IOException {
		if (context == null) throw new IOException("Android context is required");
		File directory = new File(context.getFilesDir(), "mst5-e2e");
		if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("cannot create E2E directory");
		String safe = account == null ? "" : account.replaceAll("[^A-Za-z0-9_.-]", "_");
		if (safe.length() == 0) throw new IOException("E2E account is required");
		File path = new File(directory, safe + ".key");
		long handle = NativeMst5.openE2E(path.getAbsolutePath(), create);
		return new Identity(handle, path.getAbsolutePath());
	}

	public static Identity restore(Context context, String account, String password, Backup backup) throws IOException {
		if (backup == null || backup.version != 2) throw new IOException("invalid E2E backup v2");
		File directory = new File(context.getFilesDir(), "mst5-e2e");
		if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("cannot create E2E directory");
		String safe = account == null ? "" : account.replaceAll("[^A-Za-z0-9_.-]", "_");
		File path = new File(directory, safe + ".key");
		return new Identity(NativeMst5.restoreE2E(path.getAbsolutePath(), password, backup.encoded), path.getAbsolutePath());
	}

	public static Session session(Identity identity, String peerPublicKey, String from, String to) {
		if (identity == null) throw new IllegalArgumentException("E2E identity is required");
		byte[] peer = Base64Codec.decode(peerPublicKey == null ? "" : peerPublicKey);
		if (peer.length != 32) throw new IllegalArgumentException("E2E public key must be 32 bytes");
		return new Session(identity, peer, from, to);
	}

	public static Envelope seal(Session session, String from, String to, String text) throws IOException {
		byte[] encoded = NativeMst5.e2eSeal(session.identity.requireHandle(), session.peer, from, to,
				(text == null ? "" : text).getBytes("UTF-8"));
		return Envelope.decode(encoded);
	}

	public static String open(Session session, String from, String to, Envelope envelope) throws IOException {
		return new String(NativeMst5.e2eDecrypt(session.identity.requireHandle(), session.peer, from, to, envelope.encoded), "UTF-8");
	}


	/** Exact V2 media-node size: header plus authenticated 64 KiB chunks. */
	public static long encryptedMediaSize(long plaintextSize) throws IOException {
		if (plaintextSize < 0) throw new IOException("invalid E2E media size");
		long chunks = (plaintextSize + 65535L) / 65536L;
		try { return Math.addExact(plaintextSize, Math.addExact(32L, Math.multiplyExact(chunks, 20L))); }
		catch (ArithmeticException error) { throw new IOException("E2E media size overflow", error); }
	}

	public static void uploadMedia(Session session, String endpoint, String publicKey, String ticket,
	                               String fileId, long plaintextSize, ParcelFileDescriptor source,
	                               Mst5MediaClient.Observer observer) throws Exception {
		Mst5MediaClient.uploadE2EDescriptor(endpoint, publicKey, ticket, fileId, plaintextSize, source,
				session.identity.requireHandle(), session.peer, session.from, session.to, observer);
	}

	public static long downloadMedia(Session session, String endpoint, String publicKey, String ticket,
	                                 String fileId, long encryptedSize, ParcelFileDescriptor target,
	                                 Mst5MediaClient.Observer observer) throws Exception {
		return Mst5MediaClient.downloadE2EDescriptor(endpoint, publicKey, ticket, fileId, encryptedSize, target,
				session.identity.requireHandle(), session.peer, session.from, session.to, observer);
	}

	public static byte[] downloadMediaBytes(Session session, String endpoint, String publicKey, String ticket,
	                                        String fileId, long encryptedSize, int maxBytes) throws Exception {
		return Mst5MediaClient.downloadE2EBytes(endpoint, publicKey, ticket, fileId, encryptedSize, maxBytes,
				session.identity.requireHandle(), session.peer, session.from, session.to);
	}

	public static String fingerprint(String publicKey) throws IOException {
		byte[] key = Base64Codec.decode(publicKey == null ? "" : publicKey);
		if (key.length != 32) throw new IllegalArgumentException("E2E public key must be 32 bytes");
		return NativeMst5.e2ePublicFingerprint(key);
	}

	public static Backup backup(Identity identity, String password) throws IOException {
		return new Backup(2, NativeMst5.e2eBackup(identity.requireHandle(), password));
	}

	public static final class Identity {
		private long handle;
		final String path;
		public final String publicKeyB64;
		Identity(long handle, String path) throws IOException {
			this.handle = handle;
			this.path = path;
			this.publicKeyB64 = Base64Codec.encode(NativeMst5.e2ePublicKey(handle));
		}
		public synchronized String fingerprint() throws IOException { return NativeMst5.e2eFingerprint(requireHandle()); }
		public synchronized void close() { NativeMst5.closeE2E(handle); handle = 0; }
		public synchronized void remove() throws IOException { close(); NativeMst5.removeE2E(path); }
		private synchronized long requireHandle() throws IOException {
			if (handle == 0) throw new IOException("E2E identity is closed");
			return handle;
		}
	}

	public static final class Session {
		final Identity identity; final byte[] peer; final String from; final String to;
		Session(Identity identity, byte[] peer, String from, String to) {
			this.identity = identity; this.peer = peer; this.from = from; this.to = to;
		}
	}

	public static final class Envelope {
		public final int version; public final String nonce; public final String ciphertext; public final String tag;
		final byte[] encoded;
		private Envelope(byte[] encoded) {
			this.encoded = encoded; this.version = encoded[0] & 0xff;
			byte[] nonceBytes = new byte[24]; System.arraycopy(encoded, 1, nonceBytes, 0, 24);
			byte[] body = new byte[encoded.length - 25]; System.arraycopy(encoded, 25, body, 0, body.length);
			this.nonce = Base64Codec.encode(nonceBytes); this.ciphertext = Base64Codec.encode(body); this.tag = "";
		}
		public Envelope(int version, String nonce, String ciphertext, String ignoredTag) {
			byte[] n = Base64Codec.decode(nonce); byte[] c = Base64Codec.decode(ciphertext);
			if ((version != 3 && version != 4) || n.length != 24 || c.length < 16) throw new IllegalArgumentException("unsupported E2E envelope");
			this.encoded = new byte[1 + n.length + c.length]; this.encoded[0] = (byte)version;
			System.arraycopy(n, 0, encoded, 1, n.length); System.arraycopy(c, 0, encoded, 1 + n.length, c.length);
			this.version = version; this.nonce = nonce; this.ciphertext = ciphertext; this.tag = "";
		}
		static Envelope decode(byte[] value) throws IOException {
			if (value == null || value.length < 41 || (value[0] != 3 && value[0] != 4)) throw new IOException("unsupported E2E envelope; update the application");
			return new Envelope(value);
		}
	}

	public static final class Backup {
		public final int version; public final byte[] encoded;
		public Backup(int version, byte[] encoded) { this.version = version; this.encoded = encoded; }
	}
}
