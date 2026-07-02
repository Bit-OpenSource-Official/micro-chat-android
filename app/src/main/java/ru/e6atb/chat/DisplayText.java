package ru.e6atb.chat;

import android.os.Build;

final class DisplayText {
	private DisplayText() {
	}

	static String safe(String value) {
		if (value == null || value.length() == 0) return "";
		boolean stripSupplementary = legacyEmojiLayoutBug();
		boolean changed = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isHighSurrogate(c)) {
				if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
					if (stripSupplementary) {
						changed = true;
						break;
					}
					i++;
				} else {
					changed = true;
					break;
				}
			} else if (Character.isLowSurrogate(c)) {
				changed = true;
				break;
			}
		}
		if (!changed) return value;
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isHighSurrogate(c)) {
				if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
					if (stripSupplementary) {
						out.append('\uFFFD');
					} else {
						out.append(c);
						out.append(value.charAt(i + 1));
					}
					i++;
				} else {
					out.append('\uFFFD');
				}
			} else if (Character.isLowSurrogate(c)) {
				out.append('\uFFFD');
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static boolean legacyEmojiLayoutBug() {
		return Build.VERSION.SDK_INT > 0 && Build.VERSION.SDK_INT < 21;
	}
}
