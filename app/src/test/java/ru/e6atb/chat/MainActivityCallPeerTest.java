package ru.e6atb.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MainActivityCallPeerTest {
	@Test
	public void callPeerFallsBackToPublicIdWhenUsernameIsEmpty() throws Exception {
		MST5.User alice = new MST5.User("0000000000000001", "", "", "", false, false);
		MST5.User me = new MST5.User("0000000000000002", "", "", "", false, false);
		MST5.Call incoming = new MST5.Call(alice, me, 1);
		MST5.Call outgoing = new MST5.Call(me, alice, 1);

		assertEquals("0000000000000001", MainActivity.callPeerFor("0000000000000002", "", incoming));
		assertEquals("0000000000000001", MainActivity.callPeerFor("0000000000000002", "", outgoing));
		assertTrue(MainActivity.isOwnUserFor("0000000000000002", "", me));
		assertTrue(MainActivity.isOwnAddressFor("0000000000000002", "me", "@me"));
		assertTrue(MainActivity.isOwnAddressFor("0000000000000002", "me", "0000000000000002"));
	}
}
