package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ForwardMessageFormatterTest {
	@Test
	public void forwardedTextKeepsOriginalAuthorAndBody() {
		assertEquals(
				"Forwarded from Alice\n\nHello Bob",
				ForwardMessageFormatter.compose("Forwarded from Alice", "Hello Bob")
		);
	}

	@Test
	public void serverLimitIsCountedInUtf8Bytes() {
		assertEquals(12, ForwardMessageFormatter.utf8Length("Привет"));
		assertEquals(4, ForwardMessageFormatter.utf8Length("😀"));
		assertTrue(ForwardMessageFormatter.fitsServerLimit(repeat('a', 4096)));
		assertFalse(ForwardMessageFormatter.fitsServerLimit(repeat('я', 4096)));
	}

	private static String repeat(char value, int count) {
		StringBuilder out = new StringBuilder(count);
		for (int i = 0; i < count; i++) out.append(value);
		return out.toString();
	}
}
