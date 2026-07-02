package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MainActivityCallPeerTest {
	@Test
	public void callPeerFallsBackToPublicIdWhenUsernameIsEmpty() throws Exception {
		MiniTaLib.User alice = new MiniTaLib.User("0000000000000001", "", "", "", false, false);
		MiniTaLib.User me = new MiniTaLib.User("0000000000000002", "", "", "", false, false);
		MiniTaLib.Call incoming = new MiniTaLib.Call(alice, me, 1);
		MiniTaLib.Call outgoing = new MiniTaLib.Call(me, alice, 1);

		assertEquals("0000000000000001", MainActivity.callPeerFor("0000000000000002", "", incoming));
		assertEquals("0000000000000001", MainActivity.callPeerFor("0000000000000002", "", outgoing));
		assertTrue(MainActivity.isOwnUserFor("0000000000000002", "", me));
	}
}
