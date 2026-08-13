package rs.ove.crypt.proto;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** JNI adapter for the Rust mst5-client crate. */
public final class NativeMst5 {
	private static final String TAG = "MST5";
	private static boolean initialized;
	private static Throwable initializationError;
	private static final ThreadLocal<ByteBuffer> RESPONSE_BUFFER = new ThreadLocal<ByteBuffer>() {
		@Override protected ByteBuffer initialValue() { return ByteBuffer.allocateDirect(4 * 1024 * 1024); }
	};

	interface Observer {
		boolean isCancelled();
		void onProgress(long completed, long total);
	}

	static final class Response {
		final int kind;
		final int code;
		final Object payload;

		Response(int kind, int code, Object payload) {
			this.kind = kind;
			this.code = code;
			this.payload = payload;
		}
	}

	private NativeMst5() {}

	public static synchronized void initialize(Context rawContext) {
		if (initialized) {
			requireAvailable();
			return;
		}
		initialized = true;
		if (rawContext == null) {
			initializationError = new IllegalStateException("Android context is required for mst5-client");
			requireAvailable();
		}
		try {
			Context context = rawContext.getApplicationContext();
			if (context == null) context = rawContext;
			String abi = supportedAbi(context, !ru.e6atb.chat.BuildConfig.MST5_PACKAGED_AS_JNI);
			if (abi == null) throw new IOException("APK has no mst5-client library for this CPU");
			if (ru.e6atb.chat.BuildConfig.MST5_PACKAGED_AS_JNI) {
				System.loadLibrary("mst5_android");
			} else {
				File directory = nativeDirectory(context);
				if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("cannot create native library directory");
				File library = new File(directory, "libmst5_android_v3_" + ru.e6atb.chat.BuildConfig.VERSION_CODE + ".so");
				if (!library.isFile() || library.length() == 0) {
					copyAsset(context, "mst5-native/" + abi + "/libmst5_android.so", library);
				}
				System.load(library.getAbsolutePath());
			}
			if (nativeVersion() != 3) throw new IOException("incompatible mst5-client JNI bridge");
			Log.i(TAG, "native mst5-client enabled for " + abi);
		} catch (Throwable error) {
			initializationError = error;
			Log.e(TAG, "required native mst5-client is unavailable", error);
			requireAvailable();
		}
	}

	public static void installCrashHandler(String path) throws IOException {
		requireAvailable();
		nativeInstallCrashHandler(path);
	}

	private static String supportedAbi(Context context, boolean requireAsset) {
		String[] candidates = Build.VERSION.SDK_INT >= 21
				? Api21.supportedAbis()
				: new String[] {"armeabi"};
		for (String abi : candidates) {
			if (("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi) || "armeabi".equals(abi) || "x86_64".equals(abi))
					&& (!requireAsset || assetExists(context, "mst5-native/" + abi + "/libmst5_android.so"))) return abi;
		}
		return null;
	}

	private static boolean assetExists(Context context, String asset) {
		try {
			InputStream input = context.getAssets().open(asset);
			input.close();
			return true;
		} catch (IOException ignored) {
			return false;
		}
	}

	private static File nativeDirectory(Context context) {
		if (Build.VERSION.SDK_INT >= 21) return new File(Api21.codeCacheDirectory(context), "mst5-native");
		return context.getDir("mst5-native", Context.MODE_PRIVATE);
	}

	private static final class Api21 {
		private Api21() {}

		static String[] supportedAbis() {
			String[] supported = Build.SUPPORTED_ABIS;
			return supported == null ? new String[0] : supported;
		}

		static File codeCacheDirectory(Context context) {
			return context.getCodeCacheDir();
		}
	}

	private static void copyAsset(Context context, String asset, File target) throws IOException {
		File temporary = new File(target.getParentFile(), target.getName() + ".part");
		InputStream input = context.getAssets().open(asset);
		FileOutputStream output = new FileOutputStream(temporary);
		try {
			byte[] buffer = new byte[64 * 1024];
			int count;
			while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
			output.getFD().sync();
		} finally {
			try { output.close(); } finally { input.close(); }
		}
		if (!temporary.setReadable(true, true) || !temporary.setExecutable(true, true)) {
			temporary.delete();
			throw new IOException("cannot set native MST5 library permissions");
		}
		if (!temporary.setWritable(false, false)) {
			temporary.delete();
			throw new IOException("cannot make native MST5 library read-only");
		}
		if (target.exists() && !target.delete()) {
			temporary.delete();
			throw new IOException("cannot replace native MST5 library");
		}
		if (!temporary.renameTo(target)) {
			temporary.delete();
			throw new IOException("cannot install native MST5 library");
		}
	}

	private static void requireAvailable() {
		if (initialized && initializationError == null) return;
		throw new IllegalStateException("required native mst5-client is unavailable", initializationError);
	}

	static long open(String endpoint, String publicKeyB64) throws IOException {
		requireAvailable();
		long handle = nativeOpen(endpoint, publicKeyB64);
		if (handle == 0) throw new IOException("native mst5-client did not open a connection");
		Log.i(TAG, "native MST5 connection opened");
		return handle;
	}

	static void close(long handle) {
		if (handle == 0) return;
		try { nativeClose(handle); } catch (Throwable ignored) {}
	}

	static Response request(long handle, String token, String method, String path,
	                        int timeoutMs, Object body) throws IOException {
		String requestMethod = method == null ? "GET" : method.toUpperCase(java.util.Locale.US);
		String requestPath = path == null ? "/" : path;
		byte[] cbor = CborCodec.encodeRequest(requestMethod, requestPath, body);
		ByteBuffer input = ByteBuffer.allocateDirect(Math.max(1, cbor.length));
		input.put(cbor).flip();
		ByteBuffer output = RESPONSE_BUFFER.get();
		output.clear();
		long packed = nativeCall(handle, token == null ? "" : token,
				"GET".equals(requestMethod) ? 3 : 2, opcode(requestMethod, requestPath),
				timeoutMs > 0 ? timeoutMs : 10000, input, cbor.length, output);
		if (packed < 0) throw new IOException("native MST5 request failed");
		int kind = (int) ((packed >>> 48) & 0xff);
		int code = (int) ((packed >>> 32) & 0xffff);
		int length = (int) (packed & 0xffffffffL);
		if (length < 0 || length > output.capacity()) throw new IOException("invalid native MST5 response length");
		return new Response(kind, code, CborCodec.decodeValue(output, length));
	}

	private static int opcode(String method, String rawPath) throws IOException {
		String path = rawPath;
		int query = path.indexOf('?');
		if (query >= 0) path = path.substring(0, query);
		String key = method + " " + path;
		if ("POST /register".equals(key)) return 1;
		if ("POST /login".equals(key)) return 2;
		if ("POST /auth/email/start".equals(key)) return 3;
		if ("POST /auth/email/verify".equals(key)) return 4;
		if ("GET /me".equals(key)) return 5;
		if ("POST /account/delete".equals(key)) return 6;
		if ("POST /username".equals(key)) return 7;
		if ("POST /name".equals(key)) return 8;
		if ("POST /privacy".equals(key)) return 9;
		if ("GET /contacts".equals(key)) return 10;
		if ("POST /contacts/add".equals(key)) return 11;
		if ("POST /contacts/delete".equals(key)) return 12;
		if ("POST /groups".equals(key)) return 13;
		if ("POST /channels".equals(key)) return 14;
		if ("POST /chats/title".equals(key)) return 15;
		if ("POST /channels/username".equals(key)) return 16;
		if ("POST /channels/comments/settings".equals(key)) return 17;
		if ("POST /channels/comments/send".equals(key)) return 18;
		if ("GET /channels/comments".equals(key)) return 19;
		if ("POST /chats/members/add".equals(key)) return 20;
		if ("POST /chats/members/remove".equals(key)) return 21;
		if ("POST /cloud-password".equals(key)) return 22;
		if ("POST /cloud-password/reset".equals(key)) return 23;
		if ("GET /sessions".equals(key)) return 24;
		if ("POST /sessions/revoke".equals(key)) return 25;
		if ("POST /sessions/revoke-others".equals(key)) return 26;
		if ("POST /bots".equals(key)) return 27;
		if ("POST /bots/token/reset".equals(key)) return 28;
		if ("POST /e2e/key".equals(key)) return 29;
		if ("GET /e2e/key".equals(key)) return 30;
		if ("POST /e2e/backup".equals(key)) return 31;
		if ("GET /e2e/backup".equals(key)) return 32;
		if ("POST /e2e/reset".equals(key)) return 33;
		if ("GET /wallet".equals(key)) return 34;
		if ("POST /wallet/send".equals(key)) return 35;
		if ("GET /wallet/history".equals(key)) return 36;
		if ("POST /call".equals(key)) return 37;
		if ("POST /voice-ticket".equals(key)) return 38;
		if ("GET /voice/participants".equals(key)) return 39;
		if ("POST /send".equals(key)) return 40;
		if ("POST /edit".equals(key)) return 41;
		if ("POST /callback".equals(key)) return 42;
		if ("POST /reactions".equals(key)) return 43;
		if ("POST /reactions/paid".equals(key)) return 44;
		if ("POST /read".equals(key)) return 45;
		if ("POST /delete".equals(key)) return 46;
		if ("POST /favorite".equals(key)) return 47;
		if ("GET /nodes/status".equals(key)) return 50;
		if ("GET /chats".equals(key)) return 51;
		if ("POST /chats/delete".equals(key)) return 52;
		if ("POST /users/ban".equals(key)) return 53;
		if ("POST /users/unban".equals(key)) return 54;
		if ("GET /history".equals(key)) return 55;
		if ("GET /updates".equals(key)) return 56;
		if ("GET /oauth/device/request".equals(key)) return 60;
		if ("POST /oauth/device/decision".equals(key)) return 61;
		if ("GET /file/ticket".equals(key)) return 65;
		if ("POST /forward".equals(key)) return 69;
		if ("POST /media/quote".equals(key)) return 70;
		if ("POST /messages/prepare".equals(key)) return 71;
		if ("POST /messages/commit".equals(key)) return 72;
		if ("POST /messages/cancel".equals(key)) return 73;
		if ("POST /profiles/description".equals(key)) return 74;
		throw new IOException("unsupported MST5 operation " + key);
	}

	static void upload(String endpoint, String publicKeyB64, String ticket, String fileId,
	                   long size, ParcelFileDescriptor descriptor, Observer observer) throws IOException {
		requireAvailable();
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		int fd = descriptor.getFd();
		nativeUpload(endpoint, publicKeyB64, ticket, fileId, size, fd, observer);
	}

	static void uploadBatch(String[] endpoints, String[] publicKeys, String[] tickets,
	                       String[] fileIds, long[] sizes, int[] fds, Observer observer) throws IOException {
		requireAvailable();
		nativeUploadBatch(endpoints, publicKeys, tickets, fileIds, sizes, fds, observer);
	}

	static long download(String endpoint, String publicKeyB64, String ticket, String fileId,
	                     long expectedSize, ParcelFileDescriptor descriptor, Observer observer) throws IOException {
		requireAvailable();
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		int fd = descriptor.getFd();
		return nativeDownload(endpoint, publicKeyB64, ticket, fileId, expectedSize, fd, observer);
	}

	static byte[] downloadBytes(String endpoint, String publicKeyB64, String ticket, String fileId,
	                            long expectedSize, int maxBytes) throws IOException {
		requireAvailable();
		return nativeDownloadBytes(endpoint, publicKeyB64, ticket, fileId, expectedSize, maxBytes);
	}

	static long decodeImageFd(int fd, int maxSide, long maxPixels, ByteBuffer output) throws IOException {
		requireAvailable();
		return nativeDecodeImageFd(fd, maxSide, maxPixels, output);
	}

	static long decodeImage(byte[] encoded, int maxSide, long maxPixels, ByteBuffer output) throws IOException {
		requireAvailable();
		if (encoded == null || encoded.length == 0) throw new IOException("image input is empty");
		ByteBuffer input = ByteBuffer.allocateDirect(encoded.length);
		input.put(encoded).flip();
		return nativeDecodeImage(input, encoded.length, maxSide, maxPixels, output);
	}

	public static long openVoice(String endpoint, String publicKeyB64, String ticket) throws IOException {
		requireAvailable();
		long handle = nativeVoiceOpen(endpoint, publicKeyB64, ticket);
		if (handle == 0) throw new IOException("mst5-client did not open a voice stream");
		return handle;
	}

	public static void sendVoice(long handle, byte[] pcm) throws IOException {
		nativeVoiceSend(handle, pcm == null ? new byte[0] : pcm);
	}

	public static byte[] receiveVoice(long handle) throws IOException {
		return nativeVoiceReceive(handle);
	}

	public static void closeVoice(long handle) {
		if (handle == 0) return;
		try { nativeVoiceClose(handle); } catch (Throwable ignored) {}
	}

	static long openE2E(String path, boolean create) throws IOException {
		requireAvailable();
		long handle = nativeE2EOpen(path, create);
		if (handle == 0) throw new IOException("mst5-client did not open an E2E identity");
		return handle;
	}

	static void closeE2E(long handle) {
		if (handle != 0) nativeE2EClose(handle);
	}

	static void removeE2E(String path) throws IOException { nativeE2ERemove(path); }
	static byte[] e2ePublicKey(long handle) throws IOException { return nativeE2EPublicKey(handle); }
	static String e2eFingerprint(long handle) throws IOException {
		return new String(nativeE2EFingerprint(handle), "UTF-8");
	}
	static String e2ePublicFingerprint(byte[] publicKey) throws IOException {
		return new String(nativeE2EPublicFingerprint(publicKey), "UTF-8");
	}
	static byte[] e2eSeal(long handle, byte[] peer, String from, String to, byte[] plaintext) throws IOException {
		return nativeE2ESeal(handle, peer, from, to, plaintext);
	}
	static byte[] e2eDecrypt(long handle, byte[] peer, String from, String to, byte[] envelope) throws IOException {
		return nativeE2EDecrypt(handle, peer, from, to, envelope);
	}
	static byte[] e2eBackup(long handle, String password) throws IOException { return nativeE2EBackup(handle, password); }
	static long restoreE2E(String path, String password, byte[] backup) throws IOException {
		return nativeE2ERestore(path, password, backup);
	}

	private static native int nativeVersion();
	private static native long nativeOpen(String endpoint, String publicKeyB64) throws IOException;
	private static native void nativeClose(long handle) throws IOException;
	private static native long nativeCall(long handle, String token, int kind, int opcode,
	                                     int timeoutMs, ByteBuffer input, int inputLength,
	                                     ByteBuffer output) throws IOException;
	private static native long nativeDecodeImageFd(int fd, int maxSide, long maxPixels,
	                                              ByteBuffer output) throws IOException;
	private static native long nativeDecodeImage(ByteBuffer input, int inputLength, int maxSide,
	                                            long maxPixels, ByteBuffer output) throws IOException;
	private static native boolean nativeUpload(String endpoint, String publicKeyB64, String ticket,
	                                           String fileId, long size, int fd, Observer observer) throws IOException;
	private static native boolean nativeUploadBatch(String[] endpoints, String[] publicKeys,
	                                                String[] tickets, String[] fileIds, long[] sizes,
	                                                int[] fds, Observer observer) throws IOException;
	private static native long nativeDownload(String endpoint, String publicKeyB64, String ticket,
	                                          String fileId, long expectedSize, int fd, Observer observer) throws IOException;
	private static native byte[] nativeDownloadBytes(String endpoint, String publicKeyB64, String ticket,
	                                                String fileId, long expectedSize, int maxBytes) throws IOException;
	private static native long nativeVoiceOpen(String endpoint, String publicKeyB64, String ticket) throws IOException;
	private static native void nativeVoiceSend(long handle, byte[] pcm) throws IOException;
	private static native byte[] nativeVoiceReceive(long handle) throws IOException;
	private static native void nativeVoiceClose(long handle) throws IOException;
	private static native long nativeE2EOpen(String path, boolean create) throws IOException;
	private static native void nativeE2EClose(long handle);
	private static native void nativeE2ERemove(String path) throws IOException;
	private static native byte[] nativeE2EPublicKey(long handle) throws IOException;
	private static native byte[] nativeE2EFingerprint(long handle) throws IOException;
	private static native byte[] nativeE2EPublicFingerprint(byte[] publicKey) throws IOException;
	private static native byte[] nativeE2ESeal(long handle, byte[] peer, String from, String to, byte[] plaintext) throws IOException;
	private static native byte[] nativeE2EDecrypt(long handle, byte[] peer, String from, String to, byte[] envelope) throws IOException;
	private static native byte[] nativeE2EBackup(long handle, String password) throws IOException;
	private static native long nativeE2ERestore(String path, String password, byte[] backup) throws IOException;
	private static native void nativeInstallCrashHandler(String path) throws IOException;
}
