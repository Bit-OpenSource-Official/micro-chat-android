package ru.e6atb.chat;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

final class AppLocale {
	static final String SYSTEM = "";
	static final String ENGLISH = "en";
	static final String RUSSIAN = "ru";

	private AppLocale() {
	}

	static void apply(Context context) {
		apply(context, SessionStore.language(context));
	}

	static void apply(Context context, String language) {
		if (context == null) return;
		Resources resources = context.getResources();
		if (resources == null) return;
		Configuration config = new Configuration(resources.getConfiguration());
		Locale locale = locale(language);
		config.locale = locale == null ? Locale.getDefault() : locale;
		resources.updateConfiguration(config, resources.getDisplayMetrics());
	}

	private static Locale locale(String language) {
		if (ENGLISH.equals(language)) return Locale.ENGLISH;
		if (RUSSIAN.equals(language)) return new Locale(RUSSIAN);
		return null;
	}
}
