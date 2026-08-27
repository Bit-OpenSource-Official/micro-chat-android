package ru.e6atb.chat;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class MessagePeerResolverTest {
	@Test
	public void channelPostCannotDowngradeCurrentChannelMetadata() {
		MST5.User channel = new MST5.User(
				"ffffffffffffff9c", "", "news", "News", false, false, 0,
				"everyone", "everyone", "everyone", "channel", "0000000000000001",
				1, 1, new ArrayList<MST5.User>(), true, false
		);
		MST5.User incompleteChannel = new MST5.User(
				channel.id, "", channel.login, channel.nick, false, false, 0
		);
		MST5.Message post = new MST5.Message(
				7, "chat:-100", incompleteChannel, incompleteChannel, "post", 1, 0,
				null, new ArrayList<MST5.Button>(), false, false, "", "post-1", 0,
				"sent", "", new ArrayList<MST5.Reaction>(), null, 0, 0, 0
		);

		assertEquals("news", MessagePeerResolver.peer(post, "alice", "0000000000000001", "news", channel));
		assertSame(channel, MessagePeerResolver.peerUser(post, "alice", "0000000000000001", channel));
	}
}
