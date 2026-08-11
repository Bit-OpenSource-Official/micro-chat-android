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
	private static volatile boolean available;
	private static boolean initialized;

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
		if (initialized) return;
		initialized = true;
		if (rawContext == null) return;
		try {
			String abi = supportedAbi();
			if (abi == null) return;
			Context context = rawContext.getApplicationContext();
			if (context == null) context = rawContext;
			File directory = nativeDirectory(context);
			if (!directory.isDirectory() && !directory.mkdirs()) return;
			File library = new File(directory, "libmst5_android_" + ru.e6atb.chat.BuildConfig.VERSION_CODE + ".so");
			if (!library.isFile() || library.length() == 0) {
				copyAsset(context, "mst5-native/" + abi + "/libmst5_android.so", library);
			}
			System.load(library.getAbsolutePath());
			available = nativeVersion() == 1;
			if (available) Log.i(TAG, "native mst5-client enabled for " + supportedAbi());
		} catch (Throwable error) {
			available = false;
			Log.w(TAG, "native mst5-client is unavailable", error);
		}
	}

	private static String supportedAbi() {
		if (Build.VERSION.SDK_INT >= 21) return Api21.supportedAbi();
		return "armeabi".equals(Build.CPU_ABI) ? "armeabi" : null;
	}

	private static File nativeDirectory(Context context) {
		if (Build.VERSION.SDK_INT >= 21) return new File(Api21.codeCacheDirectory(context), "mst5-native");
		return context.getDir("mst5-native", Context.MODE_PRIVATE);
	}

	private static final class Api21 {
		private Api21() {}

		static String supportedAbi() {
			String[] supported = Build.SUPPORTED_ABIS;
			if (supported == null) return null;
			for (String abi : supported) {
				if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) return abi;
			}
			return null;
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

	static boolean isAvailable() {
		return available;
	}

	static long open(String endpoint, String publicKeyB64) throws IOException {
		if (!available) throw new IOException("native mst5-client is unavailable");
		long handle = nativeOpen(endpoint, publicKeyB64);
		if (handle == 0) throw new IOException("native mst5-client did not open a connection");
		Log.i(TAG, "native MST5 connection opened");
		return handle;
	}

	static void close(long handle) {
		if (!available || handle == 0) return;
		try { nativeClose(handle); } catch (Throwable ignored) {}
	}

	static Response request(long handle, String token, int kind, int opcode,
	                        byte[] requestNonce, long deadlineMs, byte[] payload) throws IOException {
		byte[] encoded = nativeRequest(handle, token == null ? "" : token, kind, opcode,
				requestNonce == null ? new byte[0] : requestNonce, deadlineMs,
				payload == null ? new byte[0] : payload);
		if (encoded == null || encoded.length < 3) throw new IOException("invalid native MST5 response");
		int code = ((encoded[1] & 0xff) << 8) | (encoded[2] & 0xff);
		byte[] body = new byte[encoded.length - 3];
		System.arraycopy(encoded, 3, body, 0, body.length);
		return new Response(encoded[0] & 0xff, code, body);
	}

	static void upload(String endpoint, String publicKeyB64, String ticket, String fileId,
	                   long size, ParcelFileDescriptor descriptor, Observer observer) throws IOException {
		if (!available) throw new IOException("native mst5-client is unavailable");
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		int fd = descriptor.getFd();
		nativeUpload(endpoint, publicKeyB64, ticket, fileId, size, fd, observer);
	}

	static long download(String endpoint, String publicKeyB64, String ticket, String fileId,
	                     long expectedSize, ParcelFileDescriptor descriptor, Observer observer) throws IOException {
		if (!available) throw new IOException("native mst5-client is unavailable");
		if (descriptor == null) throw new IOException("media descriptor is unavailable");
		int fd = descriptor.getFd();
		return nativeDownload(endpoint, publicKeyB64, ticket, fileId, expectedSize, fd, observer);
	}

	private static native int nativeVersion();
	private static native long nativeOpen(String endpoint, String publicKeyB64) throws IOException;
	private static native void nativeClose(long handle) throws IOException;
	private static native byte[] nativeRequest(long handle, String token, int kind, int opcode,
	                                           byte[] requestNonce, long deadlineMs, byte[] payload) throws IOException;
	private static native boolean nativeUpload(String endpoint, String publicKeyB64, String ticket,
	                                           String fileId, long size, int fd, Observer observer) throws IOException;
	private static native long nativeDownload(String endpoint, String publicKeyB64, String ticket,
	                                          String fileId, long expectedSize, int fd, Observer observer) throws IOException;
}
