package ru.e6atb.chat

object MessageNotificationPolicy {
    @JvmStatic fun shouldNotify(bootstrapComplete: Boolean, sentByMe: Boolean, messageId: Long, readAt: Long, readMessageIds: Set<Long>?): Boolean =
        bootstrapComplete && !sentByMe && readAt <= 0 && (readMessageIds == null || !readMessageIds.contains(messageId))
}
