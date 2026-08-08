package ru.e6atb.chat;

final class ForwardMessageFormatter {
	static final int MAX_MESSAGE_BYTES = 4096;

	private ForwardMessageFormatter() {
	}

	static String compose(String header, String body) {
		String safeHeader = header == null ? "" : header.trim();
		String safeBody = body == null ? "" : body.trim();
		if (safeHeader.length() == 0) return safeBody;
		if (safeBody.length() == 0) return safeHeader;
		return safeHeader + "\n\n" + safeBody;
	}

	static boolean fitsServerLimit(String value) {
		return utf8Length(value) <= MAX_MESSAGE_BYTES;
	}

	static int utf8Length(String value) {
		if (value == null || value.length() == 0) return 0;
		int bytes = 0;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (ch <= 0x7f) {
				bytes += 1;
			} else if (ch <= 0x7ff) {
				bytes += 2;
			} else if (Character.isHighSurrogate(ch)
					&& i + 1 < value.length()
					&& Character.isLowSurrogate(value.charAt(i + 1))) {
				bytes += 4;
				i++;
			} else {
				bytes += 3;
			}
		}
		return bytes;
	}
}
