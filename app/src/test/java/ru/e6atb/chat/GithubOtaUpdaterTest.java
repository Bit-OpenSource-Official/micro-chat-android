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
				.put(asset("ove-rs-0.10.2-universal.apk", 20));
		assertEquals("ove-rs-0.10.2-universal.apk",
				GithubOtaUpdater.findApkAsset(assets, "ove-rs-0.10.2-universal.apk").getString("name"));
	}

	@Test
	public void missingMetadataAssetDoesNotSelectAnotherAbi() throws Exception {
		JSONArray assets = new JSONArray().put(asset("ove-rs-0.10.2-arm64.apk", 10));
		assertNull(GithubOtaUpdater.findApkAsset(assets, "ove-rs-0.10.2-universal.apk"));
	}

	@Test
	public void legacyFallbackPrefersUniversalAndSkipsDebug() throws Exception {
		JSONArray assets = new JSONArray()
				.put(asset("app-debug.apk", 1))
				.put(asset("ove-rs-0.10.2-arm64.apk", 10))
				.put(asset("ove-rs-0.10.2-all.apk", 20));
		assertEquals("ove-rs-0.10.2-all.apk",
				GithubOtaUpdater.findApkAsset(assets, "").getString("name"));
	}

	@Test
	public void legacyFallbackStillRecognizesOldUniversalName() throws Exception {
		JSONArray assets = new JSONArray()
				.put(asset("ove-rs-0.10.2-arm64.apk", 10))
				.put(asset("ove-rs-0.10.2-universal.apk", 20));
		assertEquals("ove-rs-0.10.2-universal.apk",
				GithubOtaUpdater.findApkAsset(assets, "").getString("name"));
	}

	@Test
	public void legacyFallbackUsesReleaseApkWhenUniversalIsAbsent() throws Exception {
		JSONArray assets = new JSONArray().put(asset("ove-rs-0.10.2-armv7.apk", 10));
		assertEquals("ove-rs-0.10.2-armv7.apk",
				GithubOtaUpdater.findApkAsset(assets, null).getString("name"));
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
