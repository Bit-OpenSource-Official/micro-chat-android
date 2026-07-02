package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MainActivityTextTest {
	@Test
	public void safeDisplayTextReplacesDanglingHighSurrogate() {
		assertEquals("message \uFFFD", MainActivity.safeDisplayText("message \uD83D"));
	}

	@Test
	public void safeDisplayTextReplacesDanglingLowSurrogate() {
		assertEquals("message \uFFFD", MainActivity.safeDisplayText("message \uDE00"));
	}

	@Test
	public void safeDisplayTextKeepsValidEmojiPair() {
		assertEquals("message \uD83D\uDE00", MainActivity.safeDisplayText("message \uD83D\uDE00"));
	}

	@Test
	public void cloudPasswordRequiredErrorIsRecognized() {
		assertEquals(true, MiniTaLib.isCloudPasswordRequiredError(new RuntimeException("cloud password required")));
		assertEquals(true, MiniTaLib.isCloudPasswordRequiredError(new RuntimeException("cloud_password_required")));
		assertEquals(false, MiniTaLib.isCloudPasswordRequiredError(new RuntimeException("unauthorized")));
		assertEquals(false, MiniTaLib.isInvalidTokenError(new RuntimeException("unauthorized")));
		assertEquals(true, MiniTaLib.isInvalidTokenError(new RuntimeException("invalid token")));
	}

	@Test
	public void githubOtaVersionComparisonUsesNumericParts() {
		assertEquals(true, GithubOtaUpdater.compareVersionNames("v1.10.0", "1.2.9") > 0);
		assertEquals(true, GithubOtaUpdater.compareVersionNames("1.0.0", "v1") == 0);
		assertEquals(true, GithubOtaUpdater.isNewer("0.1", 42, "9.9", 41));
		assertEquals(false, GithubOtaUpdater.isNewer("9.9", 42, "0.1", 42));
	}
}
