package ru.e6atb.chat;

import android.app.Application;

public final class OveApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		CrashReportStore.install(this);
	}
}
