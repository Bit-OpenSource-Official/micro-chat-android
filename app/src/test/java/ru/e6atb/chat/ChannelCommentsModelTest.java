package ru.e6atb.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

public final class ChannelCommentsModelTest {
	@Test
	public void roomCarriesViewerPermissionsAndCommentsSetting() {
		MiniTaLib.User room = new MiniTaLib.User(
				"ffffffffffffff9c", "", "news", "News", false, false, 1,
				"everyone", "everyone", "everyone", "channel", "0000000000000001",
				2, 1, new ArrayList<MiniTaLib.User>(), true, true
		);
		assertTrue(room.canManage);
		assertTrue(room.commentsEnabled);
		assertEquals("channel", room.roomKind);
	}

	@Test
	public void commentTargetAndCacheKeyAreKeptSeparateFromChannelHistory() {
		MiniTaLib.User author = new MiniTaLib.User("0000000000000002", "", "bobby", "Bob", false, false);
		MiniTaLib.User channel = new MiniTaLib.User("ffffffffffffff9c", "", "news", "News", false, false);
		MiniTaLib.Message comment = new MiniTaLib.Message(
				7, "chat:-100", author, channel, "Hello", 1, 0, null, null,
				false, false, "", "comment-message-id", 0, "sent", "",
				new ArrayList<MiniTaLib.Reaction>(), null, 0, 5, 0
		);
		assertEquals(5, comment.commentPostId);
		assertFalse(OutboxStore.cachePeer("news", comment.commentPostId).equals("news"));
		assertEquals("comments:news:5", OutboxStore.cachePeer("news", comment.commentPostId));
	}
}
