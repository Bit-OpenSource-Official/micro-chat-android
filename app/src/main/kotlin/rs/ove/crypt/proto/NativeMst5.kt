package rs.ove.crypt.proto

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/** JNI adapter for the Rust mst5-client crate. JNI names remain byte-for-byte compatible. */
object NativeMst5 {
    private const val TAG = "MST5"
    private var initialized = false
    private var initializationError: Throwable? = null

    interface Observer { fun isCancelled(): Boolean; fun onProgress(completed: Long, total: Long) }
    class Response(@JvmField val kind: Int, @JvmField val code: Int, @JvmField val payload: Any?)

    @JvmStatic @Synchronized fun initialize(rawContext: Context?) {
        if (initialized) { requireAvailable(); return }; initialized = true
        if (rawContext == null) { initializationError = IllegalStateException("Android context is required for mst5-client"); requireAvailable() }
        try {
            val context = rawContext!!.applicationContext ?: rawContext
            val abi = supportedAbi(context, !ru.e6atb.chat.BuildConfig.MST5_PACKAGED_AS_JNI) ?: throw IOException("APK has no mst5-client library for this CPU")
            if (ru.e6atb.chat.BuildConfig.MST5_PACKAGED_AS_JNI) System.loadLibrary("mst5_android") else {
                val directory = nativeDirectory(context)
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create native library directory")
                val library = File(directory, "libmst5_android_v7_${ru.e6atb.chat.BuildConfig.VERSION_CODE}.so")
                if (!library.isFile || library.length() == 0L) copyAsset(context, "mst5-native/$abi/libmst5_android.so", library)
                System.load(library.absolutePath)
            }
            if (nativeVersion() != 7) throw IOException("incompatible mst5-client JNI bridge")
            val sessionDirectory = context.filesDir
            if (sessionDirectory == null || !nativeOpenSessionStore(sessionDirectory.absolutePath)) throw IOException("cannot initialize native messenger session storage")
            Log.i(TAG, "native mst5-client enabled for $abi")
        } catch (error: Throwable) {
            initializationError = error; Log.e(TAG, "required native mst5-client is unavailable", error); requireAvailable()
        }
    }
    @JvmStatic @Throws(IOException::class) fun installCrashHandler(path: String?) { requireAvailable(); nativeInstallCrashHandler(path) }
    @JvmStatic @Throws(IOException::class) fun sessionSnapshot(): String { requireAvailable(); return nativeSessionSnapshot() }
    @JvmStatic @Throws(IOException::class) fun replaceSession(valuesJson: String?) { requireAvailable(); if (!nativeReplaceSession(valuesJson ?: "{}")) throw IOException("native messenger session store rejected update") }

    private fun supportedAbi(context: Context, requireAsset: Boolean): String? {
        val candidates = if (Build.VERSION.SDK_INT >= 21) Api21.supportedAbis() else arrayOf("armeabi")
        return candidates.firstOrNull { abi -> (abi == "arm64-v8a" || abi == "armeabi-v7a" || abi == "armeabi" || abi == "x86_64") && (!requireAsset || assetExists(context, "mst5-native/$abi/libmst5_android.so")) }
    }
    private fun assetExists(context: Context, asset: String): Boolean = try { context.assets.open(asset).use { }; true } catch (_: IOException) { false }
    private fun nativeDirectory(context: Context): File = if (Build.VERSION.SDK_INT >= 21) File(Api21.codeCacheDirectory(context), "mst5-native") else context.getDir("mst5-native", Context.MODE_PRIVATE)
    private object Api21 { fun supportedAbis(): Array<String> = Build.SUPPORTED_ABIS ?: emptyArray(); fun codeCacheDirectory(context: Context): File = context.codeCacheDir }
    private fun copyAsset(context: Context, asset: String, target: File) {
        val temporary = File(target.parentFile, "${target.name}.part")
        context.assets.open(asset).use { input -> FileOutputStream(temporary).use { output ->
            val buffer = ByteArray(64 * 1024); while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }; output.fd.sync()
        } }
        if (!temporary.setReadable(true, true) || !temporary.setExecutable(true, true)) { temporary.delete(); throw IOException("cannot set native MST5 library permissions") }
        if (!temporary.setWritable(false, false)) { temporary.delete(); throw IOException("cannot make native MST5 library read-only") }
        if (target.exists() && !target.delete()) { temporary.delete(); throw IOException("cannot replace native MST5 library") }
        if (!temporary.renameTo(target)) { temporary.delete(); throw IOException("cannot install native MST5 library") }
    }
    private fun requireAvailable() { if (initialized && initializationError == null) return; throw IllegalStateException("required native mst5-client is unavailable", initializationError) }

    @JvmStatic @Throws(IOException::class) fun open(endpoint: String?, publicKeyB64: String?, transportMode: String?): Long {
        requireAvailable(); val maker = Build.MANUFACTURER?.trim() ?: ""; val model = Build.MODEL?.trim() ?: ""; val deviceModel = "$maker $model".trim()
        val handle = nativeOpen(endpoint, publicKeyB64, deviceModel, transportMode ?: "auto"); if (handle == 0L) throw IOException("native mst5-client did not open a connection"); Log.i(TAG, "native MST5 connection opened"); return handle
    }
    @JvmStatic @Throws(IOException::class) fun open(endpoint: String?, publicKeyB64: String?): Long = open(endpoint, publicKeyB64, "auto")
    @JvmStatic fun close(handle: Long) { if (handle != 0L) try { nativeClose(handle) } catch (_: Throwable) { } }
    @JvmStatic @Throws(IOException::class) fun request(handle: Long, token: String?, method: String?, path: String?, timeoutMs: Int, body: Any?): Response = try {
        val command = JSONObject().apply { put("token", token ?: ""); put("method", method ?: "GET"); put("path", path ?: "/"); put("timeout_ms", if (timeoutMs > 0) timeoutMs else 10000); put("body", body ?: JSONObject()) }
        JSONObject(nativeCommandJson(handle, command.toString())).let { Response(it.optInt("kind"), it.getInt("code"), it.opt("body")) }
    } catch (error: IOException) { throw error } catch (error: Exception) { throw IOException("invalid native MST5 JSON response", error) }
    @JvmStatic @Throws(IOException::class) fun upload(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, size: Long, descriptor: ParcelFileDescriptor?, observer: Observer?) { requireAvailable(); val fd = descriptor?.fd ?: throw IOException("media descriptor is unavailable"); nativeUpload(endpoint, publicKeyB64, ticket, fileId, size, fd, observer) }
    @JvmStatic @Throws(IOException::class) fun uploadE2E(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, plaintextSize: Long, descriptor: ParcelFileDescriptor?, identityHandle: Long, peer: ByteArray?, from: String?, to: String?, observer: Observer?) { requireAvailable(); val fd = descriptor?.fd ?: throw IOException("media descriptor is unavailable"); nativeUploadE2E(endpoint, publicKeyB64, ticket, fileId, plaintextSize, fd, identityHandle, peer, from, to, observer) }
    @JvmStatic @Throws(IOException::class) fun uploadE2EWithKey(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, plaintextSize: Long, descriptor: ParcelFileDescriptor?, mediaKey: ByteArray?, fileAad: ByteArray?, observer: Observer?) { requireAvailable(); val fd = descriptor?.fd ?: throw IOException("media descriptor is unavailable"); nativeUploadE2EWithKey(endpoint, publicKeyB64, ticket, fileId, plaintextSize, fd, mediaKey, fileAad, observer) }
    @JvmStatic @Throws(IOException::class) fun download(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, expectedSize: Long, descriptor: ParcelFileDescriptor?, observer: Observer?): Long { requireAvailable(); return nativeDownload(endpoint, publicKeyB64, ticket, fileId, expectedSize, descriptor?.fd ?: throw IOException("media descriptor is unavailable"), observer) }
    @JvmStatic @Throws(IOException::class) fun downloadE2E(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, descriptor: ParcelFileDescriptor?, identityHandle: Long, peer: ByteArray?, from: String?, to: String?, observer: Observer?): Long { requireAvailable(); return nativeDownloadE2E(endpoint, publicKeyB64, ticket, fileId, encryptedSize, descriptor?.fd ?: throw IOException("media descriptor is unavailable"), identityHandle, peer, from, to, observer) }
    @JvmStatic @Throws(IOException::class) fun downloadE2EWithKey(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, descriptor: ParcelFileDescriptor?, mediaKey: ByteArray?, fileAad: ByteArray?, observer: Observer?): Long { requireAvailable(); return nativeDownloadE2EWithKey(endpoint, publicKeyB64, ticket, fileId, encryptedSize, descriptor?.fd ?: throw IOException("media descriptor is unavailable"), mediaKey, fileAad, observer) }
    @JvmStatic @Throws(IOException::class) fun downloadE2EBytes(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, maxBytes: Int, identityHandle: Long, peer: ByteArray?, from: String?, to: String?): ByteArray { requireAvailable(); return nativeDownloadE2EBytes(endpoint, publicKeyB64, ticket, fileId, encryptedSize, maxBytes, identityHandle, peer, from, to) }
    @JvmStatic @Throws(IOException::class) fun downloadE2EBytesWithKey(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, maxBytes: Int, mediaKey: ByteArray?, fileAad: ByteArray?): ByteArray { requireAvailable(); return nativeDownloadE2EBytesWithKey(endpoint, publicKeyB64, ticket, fileId, encryptedSize, maxBytes, mediaKey, fileAad) }
    @JvmStatic @Throws(IOException::class) fun downloadBytes(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, expectedSize: Long, maxBytes: Int): ByteArray { requireAvailable(); return nativeDownloadBytes(endpoint, publicKeyB64, ticket, fileId, expectedSize, maxBytes) }
    @JvmStatic @Throws(IOException::class) fun decodeImageFd(fd: Int, maxSide: Int, maxPixels: Long, output: ByteBuffer?): Long { requireAvailable(); return nativeDecodeImageFd(fd, maxSide, maxPixels, output) }
    @JvmStatic @Throws(IOException::class) fun decodeImage(encoded: ByteArray?, maxSide: Int, maxPixels: Long, output: ByteBuffer?): Long { requireAvailable(); val bytes = encoded ?: throw IOException("image input is empty"); if (bytes.isEmpty()) throw IOException("image input is empty"); val input = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }; return nativeDecodeImage(input, bytes.size, maxSide, maxPixels, output) }
    @JvmStatic @Throws(IOException::class) fun prepareWebp(encoded: ByteArray?, maxSide: Int, square: Boolean): ByteArray { val bytes = encoded ?: throw IOException("image input is empty"); if (bytes.isEmpty()) throw IOException("image input is empty"); return nativePrepareWebp(bytes, maxSide, square) }
    @JvmStatic @Throws(IOException::class) fun openVoice(endpoint: String?, publicKeyB64: String?, ticket: String?): Long { requireAvailable(); return nativeVoiceOpen(endpoint, publicKeyB64, ticket).also { if (it == 0L) throw IOException("mst5-client did not open a voice stream") } }
    @JvmStatic @Throws(IOException::class) fun sendVoice(handle: Long, pcm: ByteArray?) { nativeVoiceSend(handle, pcm ?: ByteArray(0)) }
    @JvmStatic @Throws(IOException::class) fun receiveVoice(handle: Long): ByteArray = nativeVoiceReceive(handle)
    @JvmStatic fun closeVoice(handle: Long) { if (handle != 0L) try { nativeVoiceClose(handle) } catch (_: Throwable) { } }
    @JvmStatic @Throws(IOException::class) fun openE2E(path: String?, create: Boolean): Long { requireAvailable(); return nativeE2EOpen(path, create).also { if (it == 0L) throw IOException("mst5-client did not open an E2E identity") } }
    @JvmStatic fun closeE2E(handle: Long) { if (handle != 0L) nativeE2EClose(handle) }
    @JvmStatic @Throws(IOException::class) fun removeE2E(path: String?) = nativeE2ERemove(path)
    @JvmStatic @Throws(IOException::class) fun e2ePublicKey(handle: Long): ByteArray = nativeE2EPublicKey(handle)
    @JvmStatic @Throws(IOException::class) fun e2eFingerprint(handle: Long): String = nativeE2EFingerprint(handle).toString(Charsets.UTF_8)
    @JvmStatic @Throws(IOException::class) fun e2ePublicFingerprint(publicKey: ByteArray?): String = nativeE2EPublicFingerprint(publicKey).toString(Charsets.UTF_8)
    @JvmStatic @Throws(IOException::class) fun e2eSeal(handle: Long, peer: ByteArray?, from: String?, to: String?, plaintext: ByteArray?): ByteArray = nativeE2ESeal(handle, peer, from, to, plaintext)
    @JvmStatic @Throws(IOException::class) fun e2eDecrypt(handle: Long, peer: ByteArray?, from: String?, to: String?, envelope: ByteArray?): ByteArray = nativeE2EDecrypt(handle, peer, from, to, envelope)
    @JvmStatic @Throws(IOException::class) fun e2eMediaKey(handle: Long, peer: ByteArray?, from: String?, to: String?): ByteArray = nativeE2EMediaKey(handle, peer, from, to)
    @JvmStatic @Throws(IOException::class) fun e2eBackup(handle: Long, password: String?): ByteArray = nativeE2EBackup(handle, password)
    @JvmStatic @Throws(IOException::class) fun restoreE2E(path: String?, password: String?, backup: ByteArray?): Long = nativeE2ERestore(path, password, backup)

    @JvmStatic private external fun nativeVersion(): Int
    @JvmStatic @Throws(IOException::class) private external fun nativeOpenSessionStore(root: String?): Boolean
    @JvmStatic @Throws(IOException::class) private external fun nativeSessionSnapshot(): String
    @JvmStatic @Throws(IOException::class) private external fun nativeReplaceSession(valuesJson: String?): Boolean
    @JvmStatic @Throws(IOException::class) private external fun nativeOpen(endpoint: String?, publicKeyB64: String?, deviceModel: String?, transportMode: String?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativeClose(handle: Long)
    @JvmStatic @Throws(IOException::class) private external fun nativeCommandJson(handle: Long, command: String?): String
    @JvmStatic @Throws(IOException::class) private external fun nativeDecodeImageFd(fd: Int, maxSide: Int, maxPixels: Long, output: ByteBuffer?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativeDecodeImage(input: ByteBuffer?, inputLength: Int, maxSide: Int, maxPixels: Long, output: ByteBuffer?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativePrepareWebp(input: ByteArray?, maxSide: Int, square: Boolean): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeUpload(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, size: Long, fd: Int, observer: Observer?): Boolean
    @JvmStatic @Throws(IOException::class) private external fun nativeUploadE2E(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, plaintextSize: Long, fd: Int, identityHandle: Long, peer: ByteArray?, from: String?, to: String?, observer: Observer?): Boolean
    @JvmStatic @Throws(IOException::class) private external fun nativeUploadE2EWithKey(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, plaintextSize: Long, fd: Int, mediaKey: ByteArray?, fileAad: ByteArray?, observer: Observer?): Boolean
    @JvmStatic @Throws(IOException::class) private external fun nativeDownload(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, expectedSize: Long, fd: Int, observer: Observer?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativeDownloadE2E(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, fd: Int, identityHandle: Long, peer: ByteArray?, from: String?, to: String?, observer: Observer?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativeDownloadE2EWithKey(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, fd: Int, mediaKey: ByteArray?, fileAad: ByteArray?, observer: Observer?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativeDownloadE2EBytes(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, maxBytes: Int, identityHandle: Long, peer: ByteArray?, from: String?, to: String?): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeDownloadE2EBytesWithKey(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, maxBytes: Int, mediaKey: ByteArray?, fileAad: ByteArray?): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeDownloadBytes(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, expectedSize: Long, maxBytes: Int): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeVoiceOpen(endpoint: String?, publicKeyB64: String?, ticket: String?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativeVoiceSend(handle: Long, pcm: ByteArray?)
    @JvmStatic @Throws(IOException::class) private external fun nativeVoiceReceive(handle: Long): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeVoiceClose(handle: Long)
    @JvmStatic @Throws(IOException::class) private external fun nativeE2EOpen(path: String?, create: Boolean): Long
    @JvmStatic private external fun nativeE2EClose(handle: Long)
    @JvmStatic @Throws(IOException::class) private external fun nativeE2ERemove(path: String?)
    @JvmStatic @Throws(IOException::class) private external fun nativeE2EPublicKey(handle: Long): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeE2EFingerprint(handle: Long): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeE2EPublicFingerprint(publicKey: ByteArray?): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeE2ESeal(handle: Long, peer: ByteArray?, from: String?, to: String?, plaintext: ByteArray?): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeE2EDecrypt(handle: Long, peer: ByteArray?, from: String?, to: String?, envelope: ByteArray?): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeE2EMediaKey(handle: Long, peer: ByteArray?, from: String?, to: String?): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeE2EBackup(handle: Long, password: String?): ByteArray
    @JvmStatic @Throws(IOException::class) private external fun nativeE2ERestore(path: String?, password: String?, backup: ByteArray?): Long
    @JvmStatic @Throws(IOException::class) private external fun nativeInstallCrashHandler(path: String?)
}
