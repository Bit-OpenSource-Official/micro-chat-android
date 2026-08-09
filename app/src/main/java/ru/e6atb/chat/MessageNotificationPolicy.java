package ru.e6atb.chat;

import java.util.Set;

final class MessageNotificationPolicy {
	private MessageNotificationPolicy() {
	}

	static boolean shouldNotify(boolean bootstrapComplete, boolean sentByMe,
			long messageId, long readAt, Set<Long> readMessageIds) {
		return bootstrapComplete
				&& !sentByMe
				&& readAt <= 0
				&& (readMessageIds == null || !readMessageIds.contains(messageId));
	}
}
