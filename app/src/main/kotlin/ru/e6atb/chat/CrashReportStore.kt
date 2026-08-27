package ru.e6atb.chat

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

internal object CrashReportStore {
    const val MARKER = "[OVE_ANDROID_CRASH_V1]"
    private const val DIRECTORY = "crash-reports"
    private const val SUFFIX = ".pending"
    private const val MAX_REPORT_BYTES = 3400
    private const val MAX_REPORTS = 8
    private val authValue = Pattern.compile("(?i)((?:token|password|authorization|secret)\\s*[=:]\\s*)([^\\s,;]+)")
    private val bearer = Pattern.compile("(?i)(bearer\\s+)([^\\s,;]+)")
    private val longSecret = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{64,}(?![A-Za-z0-9])")
    @Volatile private var installed = false

    @JvmStatic fun nativeReportPath(context: Context?): String = directory(context)?.let { File(it, "native-crash$SUFFIX").absolutePath } ?: ""

    @JvmStatic @Synchronized fun install(context: Context?) {
        if (installed || context == null) return
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { capture(app, thread, error) } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
        installed = true
    }

    private fun capture(context: Context, thread: Thread?, error: Throwable?) {
        val directory = directory(context) ?: return
        val id = "${System.currentTimeMillis()}-${java.lang.Long.toHexString(System.nanoTime())}"
        val report = formatReport(id, utcNow(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            Build.VERSION.RELEASE, Build.VERSION.SDK_INT, "${Build.MANUFACTURER} ${Build.MODEL}",
            thread?.name ?: "unknown", error)
        val temporary = File(directory, "$id.tmp")
        val destination = File(directory, "$id$SUFFIX")
        FileOutputStream(temporary).use { output ->
            output.write(report.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            return
        }
        prune(directory)
    }

    @JvmStatic fun formatReport(id: String?, timestamp: String?, versionName: String?, versionCode: Int,
                                 androidRelease: String?, sdk: Int, device: String?, threadName: String?, error: Throwable?): String {
        val stack = StringWriter()
        PrintWriter(stack).use { writer ->
            if (error == null) writer.println("Unknown uncaught exception") else error.printStackTrace(writer)
        }
        val report = "$MARKER\nReport-ID: ${safe(id)}\nUTC: ${safe(timestamp)}\nApp: ${safe(versionName)} ($versionCode)" +
            "\nAndroid: ${safe(androidRelease)} (SDK $sdk)\nDevice: ${safe(device)}\nThread: ${safe(threadName)}\n\n$stack"
        return truncateUtf8(redact(report), MAX_REPORT_BYTES)
    }

    @JvmStatic @Synchronized fun pending(context: Context?): List<PendingReport> {
        val directory = directory(context) ?: return emptyList()
        val reports = ArrayList<PendingReport>()
        val files = directory.listFiles() ?: return reports
        files.sortBy { it.name }
        for (file in files) {
            if (!file.isFile || !file.name.endsWith(SUFFIX)) continue
            val text = read(file)
            if (text.isEmpty() && file.name == "native-crash$SUFFIX") continue
            if (text.isEmpty() || !text.startsWith(MARKER)) {
                file.delete()
                continue
            }
            reports += PendingReport(file.name.removeSuffix(SUFFIX), text, file)
        }
        return reports
    }

    @JvmStatic @Synchronized fun remove(report: PendingReport?) {
        val file = report?.file ?: return
        if (file.name == "native-crash$SUFFIX") {
            try { FileOutputStream(file, false).use { it.fd.sync() } } catch (_: Exception) { }
        } else file.delete()
    }

    private fun directory(context: Context?): File? {
        val root = context?.filesDir ?: return null
        val result = File(root, DIRECTORY)
        return if (result.exists() || result.mkdirs()) result else null
    }

    private fun prune(directory: File) {
        val files = directory.listFiles() ?: return
        if (files.size <= MAX_REPORTS) return
        files.sortBy { it.name }
        var remaining = files.size - MAX_REPORTS
        for (file in files) if (remaining > 0 && file.isFile && file.delete()) remaining--
    }

    private fun read(file: File): String = try {
        FileInputStream(file).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var total = 0
            while (total < MAX_REPORT_BYTES) {
                val count = input.read(buffer)
                if (count < 0) break
                val accepted = minOf(count, MAX_REPORT_BYTES - total)
                output.write(buffer, 0, accepted)
                total += accepted
            }
            output.toString("UTF-8")
        }
    } catch (_: Exception) { "" }

    private fun redact(value: String): String = longSecret.matcher(
        bearer.matcher(authValue.matcher(value).replaceAll("\$1[REDACTED]")).replaceAll("\$1[REDACTED]")
    ).replaceAll("[REDACTED_LONG_VALUE]")

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        return try {
            if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
            val suffix = "\n...[truncated]"
            var low = 0
            var high = value.length
            while (low < high) {
                val middle = (low + high + 1) / 2
                if ((value.substring(0, middle) + suffix).toByteArray(Charsets.UTF_8).size <= maxBytes) low = middle else high = middle - 1
            }
            value.substring(0, low) + suffix
        } catch (_: Exception) { if (value.length <= maxBytes) value else value.substring(0, maxBytes) }
    }

    private fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
    private fun safe(value: String?): String = value ?: ""

    internal class PendingReport(@JvmField val id: String, @JvmField val text: String, @JvmField val file: File)
}
