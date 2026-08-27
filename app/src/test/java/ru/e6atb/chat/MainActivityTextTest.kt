package ru.e6atb.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityTextTest {
    @Test
    fun safeDisplayTextReplacesDanglingHighSurrogate() {
        assertEquals("message \uFFFD", MainActivity.safeDisplayText("message \uD83D"))
    }

    @Test
    fun safeDisplayTextReplacesDanglingLowSurrogate() {
        assertEquals("message \uFFFD", MainActivity.safeDisplayText("message \uDE00"))
    }

    @Test
    fun safeDisplayTextKeepsValidEmojiPair() {
        assertEquals("message \uD83D\uDE00", MainActivity.safeDisplayText("message \uD83D\uDE00"))
    }

    @Test
    fun cloudPasswordRequiredErrorIsRecognized() {
        assertEquals(true, MST5.isCloudPasswordRequiredError(RuntimeException("cloud password required")))
        assertEquals(true, MST5.isCloudPasswordRequiredError(RuntimeException("cloud_password_required")))
        assertEquals(false, MST5.isCloudPasswordRequiredError(RuntimeException("unauthorized")))
        assertEquals(false, MST5.isInvalidTokenError(RuntimeException("unauthorized")))
        assertEquals(true, MST5.isInvalidTokenError(RuntimeException("invalid token")))
    }

    @Test
    fun githubOtaVersionComparisonUsesNumericParts() {
        assertEquals(true, GithubOtaUpdater.compareVersionNames("v1.10.0", "1.2.9") > 0)
        assertEquals(true, GithubOtaUpdater.compareVersionNames("1.0.0", "v1") === 0)
        assertEquals(true, GithubOtaUpdater.isNewer("0.1", 42, "9.9", 41))
        assertEquals(false, GithubOtaUpdater.isNewer("9.9", 42, "0.1", 42))
    }
}
