package ru.e6atb.chat;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class GithubOtaUpdater {
	private static final String API_BASE = "https://api.github.com/repos/";
	private static final String APK_MIME = "application/vnd.android.package-archive";
	private static final int CONNECT_TIMEOUT_MS = 15000;
	private static final int READ_TIMEOUT_MS = 30000;
	private static Context applicationContext;

	private GithubOtaUpdater() {
	}

	static Update findLatest(String repository, String packageName, String currentVersionName, int currentVersionCode) throws Exception {
		return findLatest(repository, packageName, currentVersionName, currentVersionCode, deviceArchitecture());
	}

	static Update findLatest(String repository, String packageName, String currentVersionName, int currentVersionCode,
			String architecture) throws Exception {
		repository = repository == null ? "" : repository.trim();
		if (repository.length() == 0 || repository.indexOf('/') <= 0) {
			throw new IOException("GitHub repository is not configured");
		}
		JSONObject release = fetchJson(API_BASE + repository + "/releases/latest");
		JSONArray assets = release.optJSONArray("assets");
		JSONObject metadata = null;
		JSONObject metadataAsset = findMetadataAsset(assets);
		if (metadataAsset != null) {
			metadata = fetchJson(metadataAsset.optString("browser_download_url", ""));
		}
		JSONObject apkMetadata = findApkMetadata(metadata, architecture);
		if (metadata != null && metadata.optJSONObject("apks") != null && apkMetadata == null) {
			throw new IOException("release metadata has no APK for " + architecture);
		}
		String metadataApkName = apkMetadata == null ? "" : apkMetadata.optString("apkName", "").trim();
		if (metadataApkName.length() > 0
				&& !matchesArchitecture(metadataApkName.toLowerCase(Locale.US), architecture)) {
			throw new IOException("release APK architecture mismatch: " + metadataApkName);
		}
		JSONObject apkAsset = findApkAsset(assets, metadataApkName, architecture);
		if (apkAsset == null) {
			if (metadataApkName.length() > 0) {
				throw new IOException("release APK asset not found: " + metadataApkName);
			}
			throw new IOException("release APK asset not found");
		}

		String tag = release.optString("tag_name", "");
		String versionName = metadata == null ? "" : metadata.optString("versionName", "");
		if (versionName.length() == 0) versionName = stripVersionPrefix(tag);
		if (versionName.length() == 0) versionName = release.optString("name", "");

		int versionCode = metadata == null ? -1 : metadata.optInt("versionCode", -1);
		String metadataPackage = metadata == null ? "" : metadata.optString("packageName", "");
		if (metadataPackage.length() > 0 && !metadataPackage.equals(packageName)) {
			throw new IOException("release package mismatch: " + metadataPackage);
		}

		if (!isNewer(versionName, versionCode, currentVersionName, currentVersionCode)) {
			return null;
		}

		Update update = new Update();
		update.versionName = versionName;
		update.versionCode = versionCode;
		update.apkName = apkAsset.optString("name", "update.apk");
		update.apkUrl = apkAsset.optString("browser_download_url", "");
		update.apkMime = APK_MIME;
		update.apkSha256 = apkMetadata == null ? "" : apkMetadata.optString("apkSha256", "");
		update.apkSize = apkMetadata == null ? -1L : apkMetadata.optLong("apkSize", -1L);
		long assetSize = apkAsset.optLong("size", -1L);
		validateAssetSize(update.apkSize, assetSize);
		if (update.apkUrl.length() == 0) {
			throw new IOException("release APK URL not found");
		}
		return update;
	}

	static File download(Context context, Update update) throws Exception {
		if (context == null) throw new IOException("context is not available");
		initialize(context);
		if (update == null || update.apkUrl == null || update.apkUrl.length() == 0) {
			throw new IOException("update URL is empty");
		}
		File dir = context.getExternalFilesDir(null);
		if (dir == null) dir = context.getFilesDir();
		if (dir == null) throw new IOException("download folder is not available");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("download folder cannot be created");
		}
		String version = update.versionName == null || update.versionName.length() == 0 ? "latest" : update.versionName;
		File target = new File(dir, "ove-rs-update-" + safeFilePart(version) + ".apk");
		File tmp = new File(target.getPath() + ".tmp");
		if (tmp.isFile() && !tmp.delete()) {
			throw new IOException("old update temp file cannot be removed");
		}
		if (Armv6OtaTls.isRequired()) {
			long maxBytes = update.apkSize >= 0 ? update.apkSize : 256L * 1024L * 1024L;
			Armv6OtaTls.download(context, update.apkUrl, tmp.getAbsolutePath(), maxBytes);
			verifyFile(tmp, update.apkSha256, update.apkSize);
		} else {
			downloadToFile(update.apkUrl, tmp, update.apkSha256, update.apkSize);
		}
		if (target.isFile() && !target.delete()) {
			throw new IOException("old update file cannot be removed");
		}
		if (!tmp.renameTo(target)) {
			throw new IOException("update file cannot be saved");
		}
		return target;
	}

	static JSONObject findApkAsset(JSONArray assets, String expectedName, String architecture) {
		if (assets == null) return null;
		expectedName = expectedName == null ? "" : expectedName.trim();
		if (expectedName.length() > 0) {
			for (int i = 0; i < assets.length(); i++) {
				JSONObject asset = assets.optJSONObject(i);
				if (asset != null && expectedName.equals(asset.optString("name", ""))) return asset;
			}
			return null;
		}
		for (int i = 0; i < assets.length(); i++) {
			JSONObject asset = assets.optJSONObject(i);
			if (asset == null) continue;
			String name = asset.optString("name", "").toLowerCase(Locale.US);
			if (!name.endsWith(".apk")) continue;
			if (name.indexOf("debug") >= 0) continue;
			if (matchesArchitecture(name, architecture)) return asset;
		}
		return null;
	}

	static JSONObject findApkMetadata(JSONObject metadata, String architecture) {
		if (metadata == null) return null;
		JSONObject apks = metadata.optJSONObject("apks");
		if (apks != null) return apks.optJSONObject(architecture == null ? "" : architecture);
		// Read old single-APK metadata only when it names an APK for this architecture.
		String name = metadata.optString("apkName", "").trim();
		return matchesArchitecture(name.toLowerCase(Locale.US), architecture) ? metadata : null;
	}

	static String selectArchitecture(String[] abis) {
		if (abis == null) return "";
		for (String abi : abis) {
			String value = abi == null ? "" : abi.trim().toLowerCase(Locale.US);
			if (value.contains("arm64") || value.contains("aarch64")) return "arm64";
			if (value.contains("x86_64") || value.contains("x86-64")) return "x86_64";
			if (value.contains("armeabi-v7a") || value.contains("armv7")) return "armv7";
			if (value.equals("armeabi") || value.contains("armv6")) return "armv6";
		}
		return "";
	}

	private static String deviceArchitecture() throws IOException {
		String[] abis;
		if (Build.VERSION.SDK_INT >= 21) {
			abis = Build.SUPPORTED_ABIS;
		} else {
			abis = new String[] {Build.CPU_ABI, Build.CPU_ABI2};
		}
		String architecture = selectArchitecture(abis);
		if (architecture.length() == 0) throw new IOException("unsupported update architecture");
		return architecture;
	}

	private static boolean matchesArchitecture(String name, String architecture) {
		if (name == null || architecture == null || architecture.length() == 0) return false;
		return name.endsWith("-" + architecture.toLowerCase(Locale.US) + ".apk");
	}

	static void validateAssetSize(long metadataSize, long assetSize) throws IOException {
		if (metadataSize >= 0 && assetSize >= 0 && metadataSize != assetSize) {
			throw new IOException("release APK size mismatch");
		}
	}

	private static JSONObject findMetadataAsset(JSONArray assets) {
		if (assets == null) return null;
		for (int i = 0; i < assets.length(); i++) {
			JSONObject asset = assets.optJSONObject(i);
			if (asset == null) continue;
			String name = asset.optString("name", "").toLowerCase(Locale.US);
			if ("update.json".equals(name) || name.endsWith("-update.json")) {
				return asset;
			}
		}
		return null;
	}

	private static JSONObject fetchJson(String url) throws Exception {
		String body = readUrl(url);
		return new JSONObject(body);
	}

	private static String readUrl(String url) throws IOException {
		if (Armv6OtaTls.isRequired()) {
			Context context = applicationContext;
			if (context == null) throw new IOException("OTA context is unavailable");
			return new String(Armv6OtaTls.get(context, url), "UTF-8");
		}
		HttpURLConnection connection = open(url);
		InputStream input = null;
		try {
			int code = connection.getResponseCode();
			input = new BufferedInputStream(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
			String body = readAll(input);
			if (code < 200 || code >= 300) {
				throw new IOException("GitHub HTTP " + code);
			}
			return body;
		} finally {
			if (input != null) input.close();
			connection.disconnect();
		}
	}

	private static void downloadToFile(String url, File target, String expectedSha256, long expectedSize) throws Exception {
		HttpURLConnection connection = open(url);
		InputStream input = null;
		FileOutputStream output = null;
		try {
			int code = connection.getResponseCode();
			if (code < 200 || code >= 300) {
				throw new IOException("download HTTP " + code);
			}
			input = new BufferedInputStream(connection.getInputStream());
			output = new FileOutputStream(target);
			MessageDigest digest = expectedSha256 == null || expectedSha256.length() == 0 ? null : MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[64 * 1024];
			long total = 0;
			int read;
			while ((read = input.read(buffer)) >= 0) {
				if (read == 0) continue;
				output.write(buffer, 0, read);
				total += read;
				if (digest != null) digest.update(buffer, 0, read);
			}
			output.flush();
			if (expectedSize >= 0 && total != expectedSize) {
				throw new IOException("APK size mismatch");
			}
			if (digest != null) {
				String actual = hex(digest.digest());
				if (!actual.equalsIgnoreCase(expectedSha256)) {
					throw new IOException("APK checksum mismatch");
				}
			}
		} finally {
			if (output != null) output.close();
			if (input != null) input.close();
			connection.disconnect();
			if (!target.isFile() || target.length() == 0) target.delete();
		}
	}

	private static void verifyFile(File target, String expectedSha256, long expectedSize) throws Exception {
		if (!target.isFile()) throw new IOException("update file was not created");
		if (expectedSize >= 0 && target.length() != expectedSize) {
			target.delete();
			throw new IOException("APK size mismatch");
		}
		if (expectedSha256 == null || expectedSha256.length() == 0) return;
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		InputStream input = new BufferedInputStream(new java.io.FileInputStream(target));
		try {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
		} finally {
			input.close();
		}
		if (!hex(digest.digest()).equalsIgnoreCase(expectedSha256)) {
			target.delete();
			throw new IOException("APK checksum mismatch");
		}
	}

	static void initialize(Context context) {
		if (context == null) return;
		Context application = context.getApplicationContext();
		applicationContext = application == null ? context : application;
	}

	private static HttpURLConnection open(String url) throws IOException {
		if (url == null || url.length() == 0) throw new IOException("URL is empty");
		HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
		connection.setRequestProperty("User-Agent", "ove-rs-android/" + BuildConfig.VERSION_NAME);
		return connection;
	}

	private static String readAll(InputStream input) throws IOException {
		if (input == null) return "";
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) >= 0) {
			if (read > 0) out.write(buffer, 0, read);
		}
		return new String(out.toByteArray(), "UTF-8");
	}

	static boolean isNewer(String versionName, int versionCode, String currentVersionName, int currentVersionCode) {
		if (versionCode >= 0) return versionCode > currentVersionCode;
		return compareVersionNames(versionName, currentVersionName) > 0;
	}

	static int compareVersionNames(String left, String right) {
		String[] a = stripVersionPrefix(left).split("[^0-9A-Za-z]+");
		String[] b = stripVersionPrefix(right).split("[^0-9A-Za-z]+");
		int max = Math.max(a.length, b.length);
		for (int i = 0; i < max; i++) {
			String av = i < a.length ? a[i] : "0";
			String bv = i < b.length ? b[i] : "0";
			if (av.length() == 0) av = "0";
			if (bv.length() == 0) bv = "0";
			boolean an = isDigits(av);
			boolean bn = isDigits(bv);
			int cmp = an && bn ? compareNumericStrings(av, bv) : av.compareToIgnoreCase(bv);
			if (cmp != 0) return cmp;
		}
		return 0;
	}

	private static int compareNumericStrings(String left, String right) {
		left = trimLeadingZeroes(left);
		right = trimLeadingZeroes(right);
		if (left.length() != right.length()) return left.length() > right.length() ? 1 : -1;
		return left.compareTo(right);
	}

	private static boolean isDigits(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < '0' || c > '9') return false;
		}
		return value.length() > 0;
	}

	private static String trimLeadingZeroes(String value) {
		int i = 0;
		while (i + 1 < value.length() && value.charAt(i) == '0') i++;
		return value.substring(i);
	}

	private static String stripVersionPrefix(String value) {
		value = value == null ? "" : value.trim();
		while (value.startsWith("v") || value.startsWith("V")) {
			value = value.substring(1);
		}
		return value;
	}

	private static String safeFilePart(String value) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
				out.append(c);
			} else {
				out.append('_');
			}
		}
		return out.length() == 0 ? "latest" : out.toString();
	}

	private static String hex(byte[] bytes) {
		char[] chars = new char[bytes.length * 2];
		final char[] table = "0123456789abcdef".toCharArray();
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xff;
			chars[i * 2] = table[value >>> 4];
			chars[i * 2 + 1] = table[value & 0x0f];
		}
		return new String(chars);
	}

	static final class Update {
		String versionName;
		int versionCode;
		String apkName;
		String apkUrl;
		String apkMime;
		String apkSha256;
		long apkSize;
	}
}
