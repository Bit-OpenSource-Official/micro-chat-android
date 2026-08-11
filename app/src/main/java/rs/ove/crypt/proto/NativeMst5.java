package rs.ove.crypt.proto;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
		final byte[] payload;

		Response(int kind, int code, byte[] payload) {
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
			String abi = supportedAbi(context);
			if (abi == null) throw new IOException("APK has no mst5-client library for this CPU");
			File directory = nativeDirectory(context);
			if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("cannot create native library directory");
			File library = new File(directory, "libmst5_android_v2_" + ru.e6atb.chat.BuildConfig.VERSION_CODE + ".so");
			if (!library.isFile() || library.length() == 0) {
				copyAsset(context, "mst5-native/" + abi + "/libmst5_android.so", library);
			}
			System.load(library.getAbsolutePath());
			if (nativeVersion() != 2) throw new IOException("incompatible mst5-client JNI bridge");
			Log.i(TAG, "native mst5-client enabled for " + abi);
		} catch (Throwable error) {
			initializationError = error;
			Log.e(TAG, "required native mst5-client is unavailable", error);
			requireAvailable();
		}
	}

	private static String supportedAbi(Context context) {
		String[] candidates = Build.VERSION.SDK_INT >= 21
				? Api21.supportedAbis()
				: new String[] {"armeabi"};
		for (String abi : candidates) {
			if (("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi) || "armeabi".equals(abi))
					&& assetExists(context, "mst5-native/" + abi + "/libmst5_android.so")) return abi;
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
	                        int timeoutMs, byte[] body) throws IOException {
		byte[] encoded = nativeRequest(handle, token == null ? "" : token,
				method == null ? "GET" : method, path == null ? "/" : path,
				timeoutMs > 0 ? timeoutMs : 10000, body == null ? new byte[0] : body);
		if (encoded == null || encoded.length < 3) throw new IOException("invalid native MST5 response");
		int code = ((encoded[1] & 0xff) << 8) | (encoded[2] & 0xff);
		byte[] payload = new byte[encoded.length - 3];
		System.arraycopy(encoded, 3, payload, 0, payload.length);
		return new Response(encoded[0] & 0xff, code, payload);
	}

	static void upload(String endpoint, String publicKeyB64, String ticket, String fileId,
	                   long size, ParcelFileDescriptor descriptor, Observer observer) throws IOException {
		requireAvailable();
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		int fd = descriptor.getFd();
		nativeUpload(endpoint, publicKeyB64, ticket, fileId, size, fd, observer);
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

	private static native int nativeVersion();
	private static native long nativeOpen(String endpoint, String publicKeyB64) throws IOException;
	private static native void nativeClose(long handle) throws IOException;
	private static native byte[] nativeRequest(long handle, String token, String method, String path,
	                                           int timeoutMs, byte[] body) throws IOException;
	private static native boolean nativeUpload(String endpoint, String publicKeyB64, String ticket,
	                                           String fileId, long size, int fd, Observer observer) throws IOException;
	private static native long nativeDownload(String endpoint, String publicKeyB64, String ticket,
	                                          String fileId, long expectedSize, int fd, Observer observer) throws IOException;
	private static native byte[] nativeDownloadBytes(String endpoint, String publicKeyB64, String ticket,
	                                                String fileId, long expectedSize, int maxBytes) throws IOException;
	private static native long nativeVoiceOpen(String endpoint, String publicKeyB64, String ticket) throws IOException;
	private static native void nativeVoiceSend(long handle, byte[] pcm) throws IOException;
	private static native byte[] nativeVoiceReceive(long handle) throws IOException;
	private static native void nativeVoiceClose(long handle) throws IOException;
}
