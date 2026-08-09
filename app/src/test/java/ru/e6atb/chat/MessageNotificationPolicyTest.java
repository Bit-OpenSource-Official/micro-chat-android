package ru.e6atb.chat;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MessageNotificationPolicyTest {
	@Test
	public void firstSyncAndReadMessagesStaySilent() {
		assertFalse(MessageNotificationPolicy.shouldNotify(false, false, 7, 0, Collections.<Long>emptySet()));
		assertFalse(MessageNotificationPolicy.shouldNotify(true, false, 7, 123, Collections.<Long>emptySet()));
		assertFalse(MessageNotificationPolicy.shouldNotify(true, false, 7, 0, Collections.singleton(7L)));
		assertFalse(MessageNotificationPolicy.shouldNotify(true, true, 7, 0, Collections.<Long>emptySet()));
		assertTrue(MessageNotificationPolicy.shouldNotify(true, false, 7, 0, Collections.<Long>emptySet()));
	}
}
