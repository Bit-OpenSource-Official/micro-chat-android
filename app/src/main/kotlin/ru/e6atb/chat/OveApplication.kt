package ru.e6atb.chat

import android.app.Application
import rs.ove.crypt.proto.NativeMst5

class OveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GithubOtaUpdater.initialize(this)
        CrashReportStore.install(this)
        NativeMst5.initialize(this)
        runCatching { NativeMst5.installCrashHandler(CrashReportStore.nativeReportPath(this)) }
    }
}
