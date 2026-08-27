package ru.e6atb.chat

internal class MessageRow private constructor(
    @JvmField val text: String?,
    @JvmField val imageData: String?,
    @JvmField val file: MST5.FileInfo?,
    @JvmField val message: MST5.Message?,
    @JvmField val chatTitle: String?,
    @JvmField val chatPreview: String?,
    @JvmField val chatVerified: Boolean,
    @JvmField val chatDate: Long,
    chatUnreadCount: Int,
) {
    @JvmField val chatUnreadCount: Int = chatUnreadCount.coerceAtLeast(0)

    companion object {
        @JvmStatic fun text(text: String?) = MessageRow(text, null, null, null, null, null, false, 0, 0)
        @JvmStatic fun messageText(text: String?, message: MST5.Message?) = MessageRow(text, null, null, message, null, null, false, 0, 0)
        @JvmStatic fun inlineImage(data: String?, message: MST5.Message?) = MessageRow(null, data, null, message, null, null, false, 0, 0)
        @JvmStatic fun file(text: String?, file: MST5.FileInfo?, message: MST5.Message?) = MessageRow(text, null, file, message, null, null, false, 0, 0)
        @JvmStatic fun chat(title: String?, preview: String?) = chat(title, preview, false, 0, 0)
        @JvmStatic fun chat(title: String?, preview: String?, verified: Boolean, date: Long, unreadCount: Int) =
            MessageRow(null, null, null, null, title, preview, verified, date, unreadCount)
    }
}
