package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class OAuthCodeParserTest {
	@Test
	public void acceptsCodeAndBothLinkFormats() {
		assertEquals("AB12-CD34", OAuthCodeParser.parse("ab12-cd34"));
		assertEquals("AB12-CD34", OAuthCodeParser.parse("ovechat://authorize?user_code=AB12-CD34"));
		assertEquals("AB12-CD34", OAuthCodeParser.parse("https://m.ove.rs/oauth/device?user_code=AB12-CD34"));
	}

	@Test
	public void rejectsTextWithoutCode() {
		assertEquals("", OAuthCodeParser.parse("https://m.ove.rs/oauth/device"));
	}
}
