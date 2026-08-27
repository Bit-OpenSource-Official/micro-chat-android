package ru.e6atb.chat;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public final class MessageReplyModelTest {
	@Test
	public void outgoingCopyPreservesReplyTarget() {
		MST5.User from = new MST5.User("1", "", "alice", "Alice", false, false, 0);
		MST5.User to = new MST5.User("2", "", "bob", "Bob", false, false, 0);
		MST5.Message message = new MST5.Message(
				2, "1:2", from, to, "answer", 10, 0, new ArrayList<MST5.FileInfo>(), null, false, false, "",
				"client-id", 0, "sent", "", new ArrayList<MST5.Reaction>(), null,
				0, 0, 0, 1
		);

		assertEquals(1, message.asOutgoing().replyToMessageId);
	}
}
