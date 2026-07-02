package ru.e6atb.chat;

import android.widget.TextView;

final class ChatPreviewHolder {
	final TextView title;
	final TextView preview;

	ChatPreviewHolder(TextView title, TextView preview) {
		this.title = title;
		this.preview = preview;
	}
}
