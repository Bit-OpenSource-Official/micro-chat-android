package ru.e6atb.chat

import android.content.Context
import android.util.Log

internal object CrashReportDispatcher {
    private const val LOG_BOT = "logbot"
    private const val MAX_PER_PASS = 3

    @JvmStatic @Synchronized
    fun dispatch(context: Context?, client: MST5?): Int {
        if (context == null || client == null) return 0
        if (client.token().isEmpty()) {
            Log.w(TAG, "cannot deliver crash reports without an authenticated session")
            return 0
        }
        val reports = CrashReportStore.pending(context)
        if (reports.isEmpty()) return 0
        var sent = 0
        for (report in reports) {
            if (sent >= MAX_PER_PASS) break
            try {
                // Keep a stable client id: if the response is lost after the
                // server committed the message, the next pass is idempotent.
                val body = client.prepareMessage(LOG_BOT, report.text, "android-crash:${report.id}", true, 0)
                client.sendPreparedMessage(body)
                CrashReportStore.remove(report)
                sent++
                Log.i(TAG, "delivered crash report ${report.id}")
            } catch (error: Throwable) {
                // Never remove a report before the server confirms it.  The
                // foreground sync service will retry it on the next pass.
                Log.e(TAG, "failed to deliver crash report ${report.id}; keeping it pending", error)
                break
            }
        }
        return sent
    }

    private const val TAG = "OVE.CrashReport"
}
