package ru.e6atb.chat;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class MessagePeerResolverTest {
	@Test
	public void channelPostCannotDowngradeCurrentChannelMetadata() {
		MiniTaLib.User channel = new MiniTaLib.User(
				"ffffffffffffff9c", "", "news", "News", false, false, 0,
				"everyone", "everyone", "everyone", "channel", "0000000000000001",
				1, 1, new ArrayList<MiniTaLib.User>(), true, false
		);
		MiniTaLib.User incompleteChannel = new MiniTaLib.User(
				channel.id, "", channel.login, channel.nick, false, false, 0
		);
		MiniTaLib.Message post = new MiniTaLib.Message(
				7, "chat:-100", incompleteChannel, incompleteChannel, "post", 1, 0,
				null, new ArrayList<MiniTaLib.Button>(), false, false, "", "post-1", 0,
				"sent", "", new ArrayList<MiniTaLib.Reaction>(), null, 0, 0, 0
		);

		assertEquals("news", MessagePeerResolver.peer(post, "alice", "0000000000000001", "news", channel));
		assertSame(channel, MessagePeerResolver.peerUser(post, "alice", "0000000000000001", channel));
	}
}
