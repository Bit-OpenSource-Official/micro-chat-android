package ru.e6atb.chat

import android.app.Application
import android.util.Log
import rs.ove.crypt.proto.NativeMst5

class OveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GithubOtaUpdater.initialize(this)
        CrashReportStore.install(this)
        NativeMst5.initialize(this)
        runCatching { NativeMst5.installCrashHandler(CrashReportStore.nativeReportPath(this)) }
            .onFailure { error -> Log.e("OVE.CrashReport", "native crash handler is unavailable", error) }
    }
}
