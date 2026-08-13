package ru.e6atb.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GithubOtaUpdaterTest {
	private static JSONObject asset(String name, long size) throws Exception {
		return new JSONObject().put("name", name).put("size", size)
				.put("browser_download_url", "https://example.invalid/" + name);
	}

	@Test
	public void metadataNameWinsRegardlessOfAssetOrder() throws Exception {
		JSONArray assets = new JSONArray()
				.put(asset("ove-rs-0.10.2-arm64.apk", 10))
				.put(asset("ove-rs-0.10.2-armv7.apk", 20));
		assertEquals("ove-rs-0.10.2-armv7.apk",
				GithubOtaUpdater.findApkAsset(assets, "ove-rs-0.10.2-armv7.apk", "armv7").getString("name"));
	}

	@Test
	public void missingMetadataAssetDoesNotSelectAnotherAbi() throws Exception {
		JSONArray assets = new JSONArray().put(asset("ove-rs-0.10.2-arm64.apk", 10));
		assertNull(GithubOtaUpdater.findApkAsset(assets, "ove-rs-0.10.2-armv7.apk", "armv7"));
	}

	@Test
	public void fallbackSelectsOnlyRequestedArchitectureAndSkipsDebug() throws Exception {
		JSONArray assets = new JSONArray()
				.put(asset("app-debug.apk", 1))
				.put(asset("ove-rs-0.10.2-arm64.apk", 10))
				.put(asset("ove-rs-0.10.2-armv7.apk", 20));
		assertEquals("ove-rs-0.10.2-armv7.apk",
				GithubOtaUpdater.findApkAsset(assets, "", "armv7").getString("name"));
	}

	@Test
	public void fallbackDoesNotUseAnotherArchitecture() throws Exception {
		JSONArray assets = new JSONArray()
				.put(asset("ove-rs-0.10.2-arm64.apk", 10));
		assertNull(GithubOtaUpdater.findApkAsset(assets, "", "armv7"));
	}

	@Test
	public void metadataContainsIndependentApkForEveryArchitecture() throws Exception {
		JSONObject metadata = new JSONObject()
				.put("apkName", "app-all.apk")
				.put("apks", new JSONObject()
				.put("armv6", new JSONObject().put("apkName", "app-armv6.apk"))
				.put("armv7", new JSONObject().put("apkName", "app-armv7.apk"))
				.put("arm64", new JSONObject().put("apkName", "app-arm64.apk"))
				.put("x86_64", new JSONObject().put("apkName", "app-x86_64.apk")));
		assertEquals("app-armv6.apk", GithubOtaUpdater.findApkMetadata(metadata, "armv6").getString("apkName"));
		assertEquals("app-armv7.apk", GithubOtaUpdater.findApkMetadata(metadata, "armv7").getString("apkName"));
		assertEquals("app-arm64.apk", GithubOtaUpdater.findApkMetadata(metadata, "arm64").getString("apkName"));
		assertEquals("app-x86_64.apk", GithubOtaUpdater.findApkMetadata(metadata, "x86_64").getString("apkName"));
	}

	@Test
	public void androidAbisMapToReleaseArchitectures() {
		assertEquals("arm64", GithubOtaUpdater.selectArchitecture(new String[] {"arm64-v8a", "armeabi-v7a"}));
		assertEquals("armv7", GithubOtaUpdater.selectArchitecture(new String[] {"armeabi-v7a", "armeabi"}));
		assertEquals("armv6", GithubOtaUpdater.selectArchitecture(new String[] {"armeabi"}));
		assertEquals("x86_64", GithubOtaUpdater.selectArchitecture(new String[] {"x86_64"}));
	}

	@Test
	public void armv6TlsFallbackIsLimitedToLegacyArmv6() {
		assertEquals(true, Armv6OtaTls.shouldUse(10, new String[] {"armeabi"}));
		assertEquals(true, Armv6OtaTls.shouldUse(19, new String[] {"armv6"}));
		assertEquals(false, Armv6OtaTls.shouldUse(20, new String[] {"armeabi"}));
		assertEquals(false, Armv6OtaTls.shouldUse(19, new String[] {"armeabi-v7a", "armeabi"}));
		assertEquals(false, Armv6OtaTls.shouldUse(19, new String[] {"armeabi", "armeabi-v7a"}));
		assertEquals(false, Armv6OtaTls.shouldUse(19, new String[] {"x86"}));
	}

	@Test
	public void armv6TlsAllowsOnlyGithubHttps() {
		assertEquals(true, Armv6OtaTls.isAllowedUrl("https://api.github.com/repos/a/b/releases/latest"));
		assertEquals(true, Armv6OtaTls.isAllowedUrl("https://release-assets.githubusercontent.com/file"));
		assertEquals(false, Armv6OtaTls.isAllowedUrl("http://github.com/file"));
		assertEquals(false, Armv6OtaTls.isAllowedUrl("https://github.com.evil.test/file"));
		assertEquals(false, Armv6OtaTls.isAllowedUrl("https://user@github.com/file"));
		assertEquals(false, Armv6OtaTls.isAllowedUrl("https://github.com:444/file"));
	}

	@Test
	public void metadataAndGithubAssetSizesMustMatch() throws Exception {
		GithubOtaUpdater.validateAssetSize(20, 20);
		GithubOtaUpdater.validateAssetSize(-1, 20);
		try {
			GithubOtaUpdater.validateAssetSize(20, 10);
			fail("size mismatch must be rejected");
		} catch (java.io.IOException expected) {
			assertEquals("release APK size mismatch", expected.getMessage());
		}
	}
}
