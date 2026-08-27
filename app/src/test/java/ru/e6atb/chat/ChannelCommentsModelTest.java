package ru.e6atb.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

public final class ChannelCommentsModelTest {
	@Test
	public void roomCarriesViewerPermissionsAndCommentsSetting() {
		MST5.User room = new MST5.User(
				"ffffffffffffff9c", "", "news", "News", false, false, 1,
				"everyone", "everyone", "everyone", "channel", "0000000000000001",
				2, 1, new ArrayList<MST5.User>(), true, true
		);
		assertTrue(room.canManage);
		assertTrue(room.commentsEnabled);
		assertEquals("channel", room.roomKind);
	}

	@Test
	public void commentTargetAndCacheKeyAreKeptSeparateFromChannelHistory() {
		MST5.User author = new MST5.User("0000000000000002", "", "bobby", "Bob", false, false);
		MST5.User channel = new MST5.User("ffffffffffffff9c", "", "news", "News", false, false);
		MST5.Message comment = new MST5.Message(
				7, "chat:-100", author, channel, "Hello", 1, 0, null, null,
				false, false, "", "comment-message-id", 0, "sent", "",
				new ArrayList<MST5.Reaction>(), null, 0, 5, 0
		);
		assertEquals(5, comment.commentPostId);
		assertFalse(OutboxStore.cachePeer("news", comment.commentPostId).equals("news"));
		assertEquals("comments:news:5", OutboxStore.cachePeer("news", comment.commentPostId));
	}
}
