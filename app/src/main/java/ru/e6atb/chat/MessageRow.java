package ru.e6atb.chat;

final class MessageRow {
	final String text;
	final String imageData;
	final MiniTaLib.FileInfo file;
	final MiniTaLib.Message message;
	final String chatTitle;
	final String chatPreview;
	final boolean chatVerified;
	final long chatDate;
	final int chatUnreadCount;

	private MessageRow(String text, String imageData, MiniTaLib.FileInfo file, MiniTaLib.Message message, String chatTitle, String chatPreview, boolean chatVerified, long chatDate, int chatUnreadCount) {
		this.text = text;
		this.imageData = imageData;
		this.file = file;
		this.message = message;
		this.chatTitle = chatTitle;
		this.chatPreview = chatPreview;
		this.chatVerified = chatVerified;
		this.chatDate = chatDate;
		this.chatUnreadCount = Math.max(0, chatUnreadCount);
	}

	static MessageRow text(String text) {
		return new MessageRow(text, null, null, null, null, null, false, 0, 0);
	}

	static MessageRow messageText(String text, MiniTaLib.Message message) {
		return new MessageRow(text, null, null, message, null, null, false, 0, 0);
	}

	static MessageRow inlineImage(String data, MiniTaLib.Message message) {
		return new MessageRow(null, data, null, message, null, null, false, 0, 0);
	}

	static MessageRow file(String text, MiniTaLib.FileInfo file, MiniTaLib.Message message) {
		return new MessageRow(text, null, file, message, null, null, false, 0, 0);
	}

	static MessageRow chat(String title, String preview) {
		return chat(title, preview, false, 0, 0);
	}

	static MessageRow chat(String title, String preview, boolean verified, long date, int unreadCount) {
		return new MessageRow(null, null, null, null, title, preview, verified, date, unreadCount);
	}
}
