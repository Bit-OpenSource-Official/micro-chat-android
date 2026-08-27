package ru.e6atb.chat

import android.content.Context

internal object CrashReportDispatcher {
    private const val LOG_BOT = "logbot"
    private const val MAX_PER_PASS = 3

    @JvmStatic @Synchronized @Throws(Exception::class)
    fun dispatch(context: Context?, client: MST5?): Int {
        if (context == null || client == null || client.token().isEmpty()) return 0
        var sent = 0
        for (report in CrashReportStore.pending(context)) {
            if (sent >= MAX_PER_PASS) break
            val body = client.prepareMessage(LOG_BOT, report.text, "android-crash:${report.id}", true, 0)
            client.sendPreparedMessage(body)
            CrashReportStore.remove(report)
            sent++
        }
        return sent
    }
}
