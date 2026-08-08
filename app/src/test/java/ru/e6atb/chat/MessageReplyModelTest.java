package ru.e6atb.chat;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public final class MessageReplyModelTest {
	@Test
	public void outgoingCopyPreservesReplyTarget() {
		MiniTaLib.User from = new MiniTaLib.User("1", "", "alice", "Alice", false, false, 0);
		MiniTaLib.User to = new MiniTaLib.User("2", "", "bob", "Bob", false, false, 0);
		MiniTaLib.Message message = new MiniTaLib.Message(
				2, "1:2", from, to, "answer", 10, 0, null, null, false, false, "",
				"client-id", 0, "sent", "", new ArrayList<MiniTaLib.Reaction>(), null,
				0, 0, 0, 1
		);

		assertEquals(1, message.asOutgoing().replyToMessageId);
	}
}
