package ru.e6atb.chat

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.Locale

/** TLS 1.2 transport used only by OTA on legacy ARMv6 Android. */
internal object Armv6OtaTls {
    private const val ROOT = "armv6-ota-tls/"; private const val MAX_JSON_BYTES = 2 * 1024 * 1024
    private var initialized = false; private var caBundle: ByteArray? = null; private var initializationError: IOException? = null
    @JvmStatic fun isRequired(): Boolean = shouldUse(Build.VERSION.SDK_INT, if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS else arrayOf(Build.CPU_ABI, Build.CPU_ABI2))
    @JvmStatic fun shouldUse(sdk: Int, abis: Array<String?>?): Boolean { if (sdk > 19 || abis == null) return false; if (abis.any { it?.trim()?.lowercase(Locale.US)?.let { value -> value.contains("armeabi-v7a") || value.contains("armv7") } == true }) return false; return abis.any { it?.trim()?.lowercase(Locale.US)?.let { value -> value == "armeabi" || value.contains("armv6") } == true } }
    @JvmStatic @Throws(IOException::class) fun get(context: Context?, url: String?): ByteArray { if (!isAllowedUrl(url)) throw IOException("ARMv6 OTA HTTPS URL is not allowed"); initialize(context); return nativeGet(caBundle, url, MAX_JSON_BYTES) }
    @JvmStatic @Throws(IOException::class) fun download(context: Context?, url: String?, path: String?, maxBytes: Long): Long { if (!isAllowedUrl(url)) throw IOException("ARMv6 OTA HTTPS URL is not allowed"); initialize(context); return nativeDownload(caBundle, url, path, maxBytes) }
    @JvmStatic fun isAllowedUrl(value: String?): Boolean = try { val url = URL(value); if (!url.protocol.equals("https", true) || url.userInfo != null || (url.port != -1 && url.port != 443)) false else url.host.lowercase(Locale.US).let { it == "github.com" || it.endsWith(".github.com") || it.endsWith(".githubusercontent.com") } } catch (_: Exception) { false }
    @Synchronized private fun initialize(rawContext: Context?) { if (initialized) { initializationError?.let { throw it }; return }; initialized = true; try { val context = rawContext?.applicationContext ?: rawContext ?: throw IOException("ARMv6 OTA TLS context is unavailable"); caBundle = readAsset(context, ROOT + "cacert.pem", 1024 * 1024); val directory = context.getDir("armv6-ota-tls", Context.MODE_PRIVATE); val library = File(directory, "libove_ota_tls_${BuildConfig.VERSION_CODE}.so"); if (!library.isFile || library.length() == 0L) copyAsset(context, ROOT + "libove_ota_tls.so", library); System.load(library.absolutePath); if (nativeVersion() != 1) throw IOException("incompatible ARMv6 OTA TLS bridge") } catch (error: IOException) { initializationError = error; throw error } catch (error: Throwable) { throw IOException("cannot initialize ARMv6 OTA TLS", error).also { initializationError = it } } }
    private fun readAsset(context: Context, name: String, maxBytes: Int): ByteArray = context.assets.open(name).use { input -> val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0; while (true) { val read = input.read(buffer); if (read < 0) break; if (read == 0) continue; total += read; if (total > maxBytes) throw IOException("ARMv6 OTA TLS asset is too large"); output.write(buffer, 0, read) }; output.toByteArray() }
    private fun copyAsset(context: Context, name: String, target: File) { val temporary = File(target.parentFile, "${target.name}.part"); context.assets.open(name).use { input -> FileOutputStream(temporary).use { output -> val buffer = ByteArray(32 * 1024); while (true) { val read = input.read(buffer); if (read < 0) break; if (read > 0) output.write(buffer, 0, read) }; output.fd.sync() } }; if (!temporary.setReadable(true, true) || !temporary.setExecutable(true, true)) { temporary.delete(); throw IOException("cannot set ARMv6 OTA TLS permissions") }; if (target.exists() && !target.delete()) { temporary.delete(); throw IOException("cannot replace ARMv6 OTA TLS library") }; if (!temporary.renameTo(target)) { temporary.delete(); throw IOException("cannot install ARMv6 OTA TLS library") } }
    @JvmStatic private external fun nativeVersion(): Int
    @JvmStatic @Throws(IOException::class) private external fun nativeGet(caPem: ByteArray?, url: String?, maxBytes: Int): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeDownload(caPem: ByteArray?, url: String?, path: String?, maxBytes: Long): Long
}
