package ru.e6atb.chat;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class MessageAuthorResolverTest {
	@Test
	public void channelTitleReplacesIdOnlyMessageAuthor() {
		MST5.User idOnly = new MST5.User("ffffffffffffff9c", "", "", "", false, false, 0);
		MST5.User channel = new MST5.User(
				idOnly.id, "", "news", "Daily News", false, false, 0,
				"everyone", "everyone", "everyone", "channel", "0000000000000001",
				3, 1, new ArrayList<MST5.User>(), true, false
		);
		MST5.Message post = message(idOnly, idOnly);

		assertEquals("Daily News", MessageAuthorResolver.resolve(post, channel).nick);
	}

	@Test
	public void completeRecipientMetadataRepairsChannelAuthorWithoutOpenRoom() {
		MST5.User idOnly = new MST5.User("ffffffffffffff9c", "", "", "", false, false, 0);
		MST5.User channel = new MST5.User(
				idOnly.id, "", "news", "Daily News", false, false, 0,
				"everyone", "everyone", "everyone", "channel", "", 0, 0, null
		);

		assertSame(channel, MessageAuthorResolver.resolve(message(idOnly, channel), null));
	}

	@Test
	public void groupMemberIsNotReplacedByRoom() {
		MST5.User alice = new MST5.User("1", "", "alice", "Alice", false, false, 0);
		MST5.User group = new MST5.User(
				"ffffffffffffff9c", "", "team", "Team", false, false, 0,
				"everyone", "everyone", "everyone", "group", "1", 2, 1, null
		);

		assertSame(alice, MessageAuthorResolver.resolve(message(alice, group), group));
	}

	private static MST5.Message message(MST5.User from, MST5.User to) {
		return new MST5.Message(
				7, "chat:-100", from, to, "hello", 1, 0, null, null,
				false, false, ""
		);
	}
}
