package ru.e6atb.chat;

import static org.junit.Assert.*;
import org.junit.Test;

public final class BotDeepLinkParserTest {
	@Test
	public void parsesHttpsReferral() {
		BotDeepLinkParser.Link link =
				BotDeepLinkParser.parse("https://ms.ove.rs/bot/bit_proxy_bot?start=42");
		assertNotNull(link);
		assertEquals("bit_proxy_bot", link.login);
		assertEquals("/start 42", link.startCommand());
	}

	@Test
	public void parsesCustomScheme() {
		BotDeepLinkParser.Link link =
				BotDeepLinkParser.parse("ovechat://bot/echo_bot?start=abc_123");
		assertNotNull(link);
		assertEquals("echo_bot", link.login);
		assertEquals("/start abc_123", link.startCommand());
	}

	@Test
	public void rejectsInjectionAndForeignHost() {
		assertNull(BotDeepLinkParser.parse(
				"https://evil.example/bot/bit_proxy_bot?start=42"));
		assertNull(BotDeepLinkParser.parse(
				"https://ms.ove.rs/bot/bit_proxy_bot?start=42%20/admin"));
		assertNull(BotDeepLinkParser.parse(
				"https://ms.ove.rs/bot/not-a-bot"));
	}
}

