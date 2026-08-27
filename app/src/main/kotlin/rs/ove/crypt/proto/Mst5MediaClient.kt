package rs.ove.crypt.proto

import android.os.ParcelFileDescriptor
import java.io.IOException

/** File-descriptor facade for Rust mst5-client media streams. */
object Mst5MediaClient {
    interface Observer { fun isCancelled(): Boolean; fun onProgress(completed: Long, total: Long) }
    class Upload(@JvmField val endpoint: String?, @JvmField val publicKey: String?, @JvmField val ticket: String?, @JvmField val fileId: String?, @JvmField val size: Long, @JvmField val source: ParcelFileDescriptor?)
    @JvmStatic @Throws(Exception::class) fun uploadE2EDescriptor(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, plaintextSize: Long, source: ParcelFileDescriptor?, identityHandle: Long, peer: ByteArray?, from: String?, to: String?, observer: Observer?) = NativeMst5.uploadE2E(endpoint, publicKeyB64, ticket, fileId, plaintextSize, source, identityHandle, peer, from, to, adapt(observer))
    @JvmStatic @Throws(Exception::class) fun uploadDescriptors(uploads: List<Upload>?, observer: Observer?) {
        if (uploads.isNullOrEmpty()) return
        var total = 0L
        uploads.forEach { upload ->
            if (upload.source == null || upload.size < 0) throw IOException("media descriptor is unavailable")
            if (Long.MAX_VALUE - total < upload.size) throw IOException("media batch size overflow")
            total += upload.size
        }
        var completed = 0L
        uploads.forEach { upload ->
            val base = completed
            NativeMst5.upload(upload.endpoint, upload.publicKey, upload.ticket, upload.fileId, upload.size, upload.source,
                object : NativeMst5.Observer {
                    override fun isCancelled(): Boolean = observer?.isCancelled() == true
                    override fun onProgress(done: Long, ignored: Long) { observer?.onProgress(minOf(total, base + maxOf(0, done)), total) }
                })
            completed += upload.size
        }
    }
    @JvmStatic @Throws(Exception::class) fun downloadDescriptor(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, expectedSize: Long, target: ParcelFileDescriptor?, observer: Observer?): Long = NativeMst5.download(endpoint, publicKeyB64, ticket, fileId, expectedSize, target, adapt(observer))
    @JvmStatic @Throws(Exception::class) fun downloadE2EDescriptor(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, target: ParcelFileDescriptor?, identityHandle: Long, peer: ByteArray?, from: String?, to: String?, observer: Observer?): Long = NativeMst5.downloadE2E(endpoint, publicKeyB64, ticket, fileId, encryptedSize, target, identityHandle, peer, from, to, adapt(observer))
    @JvmStatic @Throws(Exception::class) fun downloadE2EBytes(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, encryptedSize: Long, maxBytes: Int, identityHandle: Long, peer: ByteArray?, from: String?, to: String?): ByteArray = NativeMst5.downloadE2EBytes(endpoint, publicKeyB64, ticket, fileId, encryptedSize, maxBytes, identityHandle, peer, from, to)
    @JvmStatic @Throws(Exception::class) fun downloadBytes(endpoint: String?, publicKeyB64: String?, ticket: String?, fileId: String?, expectedSize: Long, maxBytes: Int): ByteArray = NativeMst5.downloadBytes(endpoint, publicKeyB64, ticket, fileId, expectedSize, maxBytes)
    private fun adapt(observer: Observer?): NativeMst5.Observer? = observer?.let { original -> object : NativeMst5.Observer { override fun isCancelled() = original.isCancelled(); override fun onProgress(completed: Long, total: Long) = original.onProgress(completed, total) } }
}
