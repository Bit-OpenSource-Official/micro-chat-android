package ru.e6atb.chat;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public final class MiniTaLibUrlTest {
	@Test
	public void websocketBaseMatchesServerScheme() throws Exception {
		assertEquals("wss://chat.example:8080", wsBase("chat.example:8080"));
		assertEquals("wss://chat.example:443", wsBase("tcps://chat.example:443"));
		assertEquals("wss://chat.example", wsBase("https://chat.example"));
		assertEquals("wss://[::1]:8080", wsBase("[::1]:8080"));
	}

	@Test
	public void fileHttpBaseMatchesServerScheme() throws Exception {
		assertEquals("https://chat.example:8080", httpBase("chat.example:8080"));
		assertEquals("https://chat.example:443", httpBase("tcps://chat.example:443"));
		assertEquals("https://chat.example", httpBase("wss://chat.example"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void cleartextHttpServerIsRejected() {
		new MiniTaLib("http://chat.example:8080");
	}

	@Test(expected = IllegalArgumentException.class)
	public void cleartextWebSocketServerIsRejected() {
		new MiniTaLib("ws://chat.example:8080");
	}

	private static String wsBase(String raw) throws Exception {
		return invokeBase(raw, "wsBaseUrl");
	}

	private static String httpBase(String raw) throws Exception {
		return invokeBase(raw, "wsHttpBaseUrl");
	}

	private static String invokeBase(String raw, String methodName) throws Exception {
		MiniTaLib client = new MiniTaLib(raw);
		Method method = MiniTaLib.class.getDeclaredMethod(methodName);
		method.setAccessible(true);
		return (String) method.invoke(client);
	}
}
