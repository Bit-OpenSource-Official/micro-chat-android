package ru.e6atb.chat;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class MessageAuthorResolverTest {
	@Test
	public void channelTitleReplacesIdOnlyMessageAuthor() {
		MiniTaLib.User idOnly = new MiniTaLib.User("ffffffffffffff9c", "", "", "", false, false, 0);
		MiniTaLib.User channel = new MiniTaLib.User(
				idOnly.id, "", "news", "Daily News", false, false, 0,
				"everyone", "everyone", "everyone", "channel", "0000000000000001",
				3, 1, new ArrayList<MiniTaLib.User>(), true, false
		);
		MiniTaLib.Message post = message(idOnly, idOnly);

		assertEquals("Daily News", MessageAuthorResolver.resolve(post, channel).nick);
	}

	@Test
	public void completeRecipientMetadataRepairsChannelAuthorWithoutOpenRoom() {
		MiniTaLib.User idOnly = new MiniTaLib.User("ffffffffffffff9c", "", "", "", false, false, 0);
		MiniTaLib.User channel = new MiniTaLib.User(
				idOnly.id, "", "news", "Daily News", false, false, 0,
				"everyone", "everyone", "everyone", "channel", "", 0, 0, null
		);

		assertSame(channel, MessageAuthorResolver.resolve(message(idOnly, channel), null));
	}

	@Test
	public void groupMemberIsNotReplacedByRoom() {
		MiniTaLib.User alice = new MiniTaLib.User("1", "", "alice", "Alice", false, false, 0);
		MiniTaLib.User group = new MiniTaLib.User(
				"ffffffffffffff9c", "", "team", "Team", false, false, 0,
				"everyone", "everyone", "everyone", "group", "1", 2, 1, null
		);

		assertSame(alice, MessageAuthorResolver.resolve(message(alice, group), group));
	}

	private static MiniTaLib.Message message(MiniTaLib.User from, MiniTaLib.User to) {
		return new MiniTaLib.Message(
				7, "chat:-100", from, to, "hello", 1, 0, null, null,
				false, false, ""
		);
	}
}
