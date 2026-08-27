package rs.ove.crypt.proto;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONObject;

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
				File library = new File(directory, "libmst5_android_v7_" + ru.e6atb.chat.BuildConfig.VERSION_CODE + ".so");
				if (!library.isFile() || library.length() == 0) {
					copyAsset(context, "mst5-native/" + abi + "/libmst5_android.so", library);
				}
				System.load(library.getAbsolutePath());
			}
			if (nativeVersion() != 7) throw new IOException("incompatible mst5-client JNI bridge");
			File sessionDirectory = context.getFilesDir();
			if (sessionDirectory == null || !nativeOpenSessionStore(sessionDirectory.getAbsolutePath())) {
				throw new IOException("cannot initialize native messenger session storage");
			}
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

	/** Platform adapter for the persistent Rust-owned messenger session. */
	public static String sessionSnapshot() throws IOException {
		requireAvailable();
		return nativeSessionSnapshot();
	}

	/** Platform adapter for the persistent Rust-owned messenger session. */
	public static void replaceSession(String valuesJson) throws IOException {
		requireAvailable();
		if (!nativeReplaceSession(valuesJson == null ? "{}" : valuesJson)) {
			throw new IOException("native messenger session store rejected update");
		}
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

	static long open(String endpoint, String publicKeyB64, String transportMode) throws IOException {
		requireAvailable();
		String maker = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER.trim();
		String model = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL.trim();
		String deviceModel = (maker + " " + model).trim();
		long handle = nativeOpen(endpoint, publicKeyB64, deviceModel,
				transportMode == null ? "auto" : transportMode);
		if (handle == 0) throw new IOException("native mst5-client did not open a connection");
		Log.i(TAG, "native MST5 connection opened");
		return handle;
	}

	static long open(String endpoint, String publicKeyB64) throws IOException {
		return open(endpoint, publicKeyB64, "auto");
	}

	static void close(long handle) {
		if (handle == 0) return;
		try { nativeClose(handle); } catch (Throwable ignored) {}
	}

	static Response request(long handle, String token, String method, String path,
	                        int timeoutMs, Object body) throws IOException {
		try {
			JSONObject command = new JSONObject();
			command.put("token", token == null ? "" : token);
			command.put("method", method == null ? "GET" : method);
			command.put("path", path == null ? "/" : path);
			command.put("timeout_ms", timeoutMs > 0 ? timeoutMs : 10000);
			command.put("body", body == null ? new JSONObject() : body);
			JSONObject response = new JSONObject(nativeCommandJson(handle, command.toString()));
			return new Response(response.optInt("kind"), response.getInt("code"), response.opt("body"));
		} catch (IOException error) {
			throw error;
		} catch (Exception error) {
			throw new IOException("invalid native MST5 JSON response", error);
		}
	}

	static void upload(String endpoint, String publicKeyB64, String ticket, String fileId,
	                   long size, ParcelFileDescriptor descriptor, Observer observer) throws IOException {
		requireAvailable();
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		int fd = descriptor.getFd();
		nativeUpload(endpoint, publicKeyB64, ticket, fileId, size, fd, observer);
	}

	static void uploadE2E(String endpoint, String publicKeyB64, String ticket, String fileId,
	                      long plaintextSize, ParcelFileDescriptor descriptor, long identityHandle,
	                      byte[] peer, String from, String to, Observer observer) throws IOException {
		requireAvailable();
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		nativeUploadE2E(endpoint, publicKeyB64, ticket, fileId, plaintextSize, descriptor.getFd(),
				identityHandle, peer, from, to, observer);
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

	static long downloadE2E(String endpoint, String publicKeyB64, String ticket, String fileId,
	                        long encryptedSize, ParcelFileDescriptor descriptor, long identityHandle,
	                        byte[] peer, String from, String to, Observer observer) throws IOException {
		requireAvailable();
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		return nativeDownloadE2E(endpoint, publicKeyB64, ticket, fileId, encryptedSize, descriptor.getFd(),
				identityHandle, peer, from, to, observer);
	}

	static byte[] downloadE2EBytes(String endpoint, String publicKeyB64, String ticket, String fileId,
	                               long encryptedSize, int maxBytes, long identityHandle, byte[] peer,
	                               String from, String to) throws IOException {
		requireAvailable();
		return nativeDownloadE2EBytes(endpoint, publicKeyB64, ticket, fileId, encryptedSize, maxBytes,
				identityHandle, peer, from, to);
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

	static byte[] prepareWebp(byte[] encoded, int maxSide, boolean square) throws IOException {
		if (encoded == null || encoded.length == 0) throw new IOException("image input is empty");
		return nativePrepareWebp(encoded, maxSide, square);
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
	private static native boolean nativeOpenSessionStore(String root) throws IOException;
	private static native String nativeSessionSnapshot() throws IOException;
	private static native boolean nativeReplaceSession(String valuesJson) throws IOException;
	private static native long nativeOpen(String endpoint, String publicKeyB64, String deviceModel,
	                                     String transportMode) throws IOException;
	private static native void nativeClose(long handle) throws IOException;
	private static native String nativeCommandJson(long handle, String command) throws IOException;
	private static native long nativeCall(long handle, String token, int kind, int opcode,
	                                     int timeoutMs, ByteBuffer input, int inputLength,
	                                     ByteBuffer output) throws IOException;
	private static native long nativeDecodeImageFd(int fd, int maxSide, long maxPixels,
	                                              ByteBuffer output) throws IOException;
	private static native long nativeDecodeImage(ByteBuffer input, int inputLength, int maxSide,
			long maxPixels, ByteBuffer output) throws IOException;
	private static native byte[] nativePrepareWebp(byte[] input, int maxSide, boolean square) throws IOException;
	private static native boolean nativeUpload(String endpoint, String publicKeyB64, String ticket,
	                                           String fileId, long size, int fd, Observer observer) throws IOException;
	private static native boolean nativeUploadE2E(String endpoint, String publicKeyB64, String ticket,
	                                              String fileId, long plaintextSize, int fd, long identityHandle,
	                                              byte[] peer, String from, String to, Observer observer) throws IOException;
	private static native boolean nativeUploadBatch(String[] endpoints, String[] publicKeys,
	                                                String[] tickets, String[] fileIds, long[] sizes,
	                                                int[] fds, Observer observer) throws IOException;
	private static native long nativeDownload(String endpoint, String publicKeyB64, String ticket,
	                                          String fileId, long expectedSize, int fd, Observer observer) throws IOException;
	private static native long nativeDownloadE2E(String endpoint, String publicKeyB64, String ticket,
	                                             String fileId, long encryptedSize, int fd, long identityHandle,
	                                             byte[] peer, String from, String to, Observer observer) throws IOException;
	private static native byte[] nativeDownloadE2EBytes(String endpoint, String publicKeyB64, String ticket,
	                                                    String fileId, long encryptedSize, int maxBytes,
	                                                    long identityHandle, byte[] peer, String from, String to) throws IOException;
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
