package ru.e6atb.chat;

import android.app.Application;

import rs.ove.crypt.proto.NativeMst5;

public final class OveApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		CrashReportStore.install(this);
		NativeMst5.initialize(this);
		try {
			NativeMst5.installCrashHandler(CrashReportStore.nativeReportPath(this));
		} catch (Exception ignored) {
		}
	}
}
