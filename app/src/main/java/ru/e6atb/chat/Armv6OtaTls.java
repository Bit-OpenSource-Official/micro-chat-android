package ru.e6atb.chat;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/** TLS 1.2 transport used only by OTA on legacy ARMv6 Android. */
final class Armv6OtaTls {
	private static final String ROOT = "armv6-ota-tls/";
	private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
	private static boolean initialized;
	private static byte[] caBundle;
	private static IOException initializationError;

	private Armv6OtaTls() {}

	static boolean isRequired() {
		String[] abis = Build.VERSION.SDK_INT >= 21
				? Api21.supportedAbis()
				: new String[] {Build.CPU_ABI, Build.CPU_ABI2};
		return shouldUse(Build.VERSION.SDK_INT, abis);
	}

	static boolean shouldUse(int sdk, String[] abis) {
		if (sdk > 19 || abis == null) return false;
		for (String abi : abis) {
			if (abi == null) continue;
			String value = abi.trim().toLowerCase(java.util.Locale.US);
			if (value.contains("armeabi-v7a") || value.contains("armv7")) return false;
		}
		for (String abi : abis) {
			if (abi == null) continue;
			String value = abi.trim().toLowerCase(java.util.Locale.US);
			if (value.equals("armeabi") || value.contains("armv6")) return true;
		}
		return false;
	}

	static byte[] get(Context context, String url) throws IOException {
		if (!isAllowedUrl(url)) throw new IOException("ARMv6 OTA HTTPS URL is not allowed");
		initialize(context);
		return nativeGet(caBundle, url, MAX_JSON_BYTES);
	}

	static long download(Context context, String url, String path, long maxBytes) throws IOException {
		if (!isAllowedUrl(url)) throw new IOException("ARMv6 OTA HTTPS URL is not allowed");
		initialize(context);
		return nativeDownload(caBundle, url, path, maxBytes);
	}

	static boolean isAllowedUrl(String value) {
		try {
			URL url = new URL(value);
			if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null
					|| (url.getPort() != -1 && url.getPort() != 443)) return false;
			String host = url.getHost().toLowerCase(java.util.Locale.US);
			return host.equals("github.com") || host.endsWith(".github.com")
					|| host.endsWith(".githubusercontent.com");
		} catch (Exception ignored) {
			return false;
		}
	}

	private static synchronized void initialize(Context rawContext) throws IOException {
		if (initialized) {
			if (initializationError != null) throw initializationError;
			return;
		}
		initialized = true;
		try {
			if (rawContext == null) throw new IOException("ARMv6 OTA TLS context is unavailable");
			Context context = rawContext.getApplicationContext();
			if (context == null) context = rawContext;
			caBundle = readAsset(context, ROOT + "cacert.pem", 1024 * 1024);
			File directory = context.getDir("armv6-ota-tls", Context.MODE_PRIVATE);
			File library = new File(directory, "libove_ota_tls_" + BuildConfig.VERSION_CODE + ".so");
			if (!library.isFile() || library.length() == 0) {
				copyAsset(context, ROOT + "libove_ota_tls.so", library);
			}
			System.load(library.getAbsolutePath());
			if (nativeVersion() != 1) throw new IOException("incompatible ARMv6 OTA TLS bridge");
		} catch (IOException error) {
			initializationError = error;
			throw error;
		} catch (Throwable error) {
			initializationError = new IOException("cannot initialize ARMv6 OTA TLS", error);
			throw initializationError;
		}
	}

	private static byte[] readAsset(Context context, String name, int maxBytes) throws IOException {
		InputStream input = context.getAssets().open(name);
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = input.read(buffer)) >= 0) {
				if (read == 0) continue;
				total += read;
				if (total > maxBytes) throw new IOException("ARMv6 OTA TLS asset is too large");
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		} finally {
			input.close();
		}
	}

	private static void copyAsset(Context context, String name, File target) throws IOException {
		File temporary = new File(target.getParentFile(), target.getName() + ".part");
		InputStream input = context.getAssets().open(name);
		FileOutputStream output = new FileOutputStream(temporary);
		try {
			byte[] buffer = new byte[32 * 1024];
			int read;
			while ((read = input.read(buffer)) >= 0) if (read > 0) output.write(buffer, 0, read);
			output.getFD().sync();
		} finally {
			try { output.close(); } finally { input.close(); }
		}
		if (!temporary.setReadable(true, true) || !temporary.setExecutable(true, true)) {
			temporary.delete();
			throw new IOException("cannot set ARMv6 OTA TLS permissions");
		}
		if (target.exists() && !target.delete()) {
			temporary.delete();
			throw new IOException("cannot replace ARMv6 OTA TLS library");
		}
		if (!temporary.renameTo(target)) {
			temporary.delete();
			throw new IOException("cannot install ARMv6 OTA TLS library");
		}
	}

	private static final class Api21 {
		private Api21() {}
		static String[] supportedAbis() { return Build.SUPPORTED_ABIS; }
	}

	private static native int nativeVersion();
	private static native byte[] nativeGet(byte[] caPem, String url, int maxBytes) throws IOException;
	private static native long nativeDownload(byte[] caPem, String url, String path, long maxBytes) throws IOException;
}
