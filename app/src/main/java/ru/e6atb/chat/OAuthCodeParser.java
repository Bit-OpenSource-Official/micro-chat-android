package ru.e6atb.chat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OAuthCodeParser {
	private static final Pattern CODE = Pattern.compile("(?i)([0-9a-f]{4}-[0-9a-f]{4})");

	private OAuthCodeParser() {
	}

	static String parse(String value) {
		String input = value == null ? "" : value.trim();
		Matcher matcher = CODE.matcher(input);
		return matcher.find() ? matcher.group(1).toUpperCase(Locale.US) : "";
	}
}
