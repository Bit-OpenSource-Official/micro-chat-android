package ru.e6atb.chat;

import android.content.Context;

import org.json.JSONObject;

import java.util.List;

final class CrashReportDispatcher {
	private static final String LOG_BOT = "logbot";
	private static final int MAX_PER_PASS = 3;

	private CrashReportDispatcher() {
	}

	static synchronized int dispatch(Context context, MiniTaLib client) throws Exception {
		if (context == null || client == null || client.token().length() == 0) return 0;
		List<CrashReportStore.PendingReport> pending = CrashReportStore.pending(context);
		int sent = 0;
		for (CrashReportStore.PendingReport report : pending) {
			if (sent >= MAX_PER_PASS) break;
			JSONObject body = client.prepareMessage(
					LOG_BOT,
					report.text,
					"android-crash:" + report.id,
					true,
					0
			);
			client.sendPreparedMessage(body);
			CrashReportStore.remove(report);
			sent++;
		}
		return sent;
	}
}
