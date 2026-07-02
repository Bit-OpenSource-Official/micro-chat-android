package ru.e6atb.chat;

final class MessageRow {
	final String text;
	final String imageData;
	final MiniTaLib.FileInfo file;
	final MiniTaLib.Message message;
	final String chatTitle;
	final String chatPreview;

	private MessageRow(String text, String imageData, MiniTaLib.FileInfo file, MiniTaLib.Message message, String chatTitle, String chatPreview) {
		this.text = text;
		this.imageData = imageData;
		this.file = file;
		this.message = message;
		this.chatTitle = chatTitle;
		this.chatPreview = chatPreview;
	}

	static MessageRow text(String text) {
		return new MessageRow(text, null, null, null, null, null);
	}

	static MessageRow messageText(String text, MiniTaLib.Message message) {
		return new MessageRow(text, null, null, message, null, null);
	}

	static MessageRow inlineImage(String data, MiniTaLib.Message message) {
		return new MessageRow(null, data, null, message, null, null);
	}

	static MessageRow file(String text, MiniTaLib.FileInfo file, MiniTaLib.Message message) {
		return new MessageRow(text, null, file, message, null, null);
	}

	static MessageRow chat(String title, String preview) {
		return new MessageRow(null, null, null, null, title, preview);
	}
}
