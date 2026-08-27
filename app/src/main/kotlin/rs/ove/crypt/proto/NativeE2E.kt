package rs.ove.crypt.proto

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException

/** Opaque Android adapter for mst5-client E2E v4. Private keys remain in Rust. */
object NativeE2E {
    @JvmStatic @Throws(IOException::class) fun open(context: Context?, account: String?, create: Boolean): Identity {
        val root = context ?: throw IOException("Android context is required")
        val directory = File(root.filesDir, "mst5-e2e"); if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create E2E directory")
        val safe = account.orEmpty().replace(Regex("[^A-Za-z0-9_.-]"), "_"); if (safe.isEmpty()) throw IOException("E2E account is required")
        val path = File(directory, "$safe.key").absolutePath; return Identity(NativeMst5.openE2E(path, create), path)
    }
    @JvmStatic @Throws(IOException::class) fun restore(context: Context?, account: String?, password: String?, backup: Backup?): Identity {
        if (backup == null || backup.version != 2) throw IOException("invalid E2E backup v2")
        val root = context ?: throw IOException("Android context is required"); val directory = File(root.filesDir, "mst5-e2e"); if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create E2E directory")
        val safe = account.orEmpty().replace(Regex("[^A-Za-z0-9_.-]"), "_"); val path = File(directory, "$safe.key").absolutePath
        return Identity(NativeMst5.restoreE2E(path, password, backup.encoded), path)
    }
    @JvmStatic fun session(identity: Identity?, peerPublicKey: String?, from: String?, to: String?): Session {
        val actual = identity ?: throw IllegalArgumentException("E2E identity is required"); val peer = Base64Codec.decode(peerPublicKey.orEmpty())
        if (peer.size != 32) throw IllegalArgumentException("E2E public key must be 32 bytes"); return Session(actual, peer, from, to)
    }
    @JvmStatic @Throws(IOException::class) fun seal(session: Session, from: String?, to: String?, text: String?): Envelope = Envelope.decode(NativeMst5.e2eSeal(session.identity.requireHandle(), session.peer, from, to, text.orEmpty().toByteArray(Charsets.UTF_8)))
    @JvmStatic @Throws(IOException::class) fun open(session: Session, from: String?, to: String?, envelope: Envelope): String = NativeMst5.e2eDecrypt(session.identity.requireHandle(), session.peer, from, to, envelope.encoded).toString(Charsets.UTF_8)
    @JvmStatic @Throws(IOException::class) fun encryptedMediaSize(plaintextSize: Long): Long {
        if (plaintextSize < 0) throw IOException("invalid E2E media size")
        return try { Math.addExact(plaintextSize, Math.addExact(32L, Math.multiplyExact((plaintextSize + 65535L) / 65536L, 20L))) } catch (error: ArithmeticException) { throw IOException("E2E media size overflow", error) }
    }
    @JvmStatic @Throws(Exception::class) fun uploadMedia(session: Session, endpoint: String?, publicKey: String?, ticket: String?, fileId: String?, plaintextSize: Long, source: ParcelFileDescriptor?, observer: Mst5MediaClient.Observer?) = Mst5MediaClient.uploadE2EDescriptor(endpoint, publicKey, ticket, fileId, plaintextSize, source, session.identity.requireHandle(), session.peer, session.from, session.to, observer)
    @JvmStatic @Throws(Exception::class) fun downloadMedia(session: Session, endpoint: String?, publicKey: String?, ticket: String?, fileId: String?, encryptedSize: Long, target: ParcelFileDescriptor?, observer: Mst5MediaClient.Observer?): Long = Mst5MediaClient.downloadE2EDescriptor(endpoint, publicKey, ticket, fileId, encryptedSize, target, session.identity.requireHandle(), session.peer, session.from, session.to, observer)
    @JvmStatic @Throws(Exception::class) fun downloadMediaBytes(session: Session, endpoint: String?, publicKey: String?, ticket: String?, fileId: String?, encryptedSize: Long, maxBytes: Int): ByteArray = Mst5MediaClient.downloadE2EBytes(endpoint, publicKey, ticket, fileId, encryptedSize, maxBytes, session.identity.requireHandle(), session.peer, session.from, session.to)
    @JvmStatic @Throws(IOException::class) fun fingerprint(publicKey: String?): String { val key = Base64Codec.decode(publicKey.orEmpty()); if (key.size != 32) throw IllegalArgumentException("E2E public key must be 32 bytes"); return NativeMst5.e2ePublicFingerprint(key) }
    @JvmStatic @Throws(IOException::class) fun backup(identity: Identity, password: String?): Backup = Backup(2, NativeMst5.e2eBackup(identity.requireHandle(), password))

    class Identity internal constructor(private var handle: Long, @JvmField internal val path: String) {
        @JvmField val publicKeyB64: String = Base64Codec.encode(NativeMst5.e2ePublicKey(handle))
        @Synchronized @Throws(IOException::class) fun fingerprint(): String = NativeMst5.e2eFingerprint(requireHandle())
        @Synchronized fun close() { NativeMst5.closeE2E(handle); handle = 0 }
        @Synchronized @Throws(IOException::class) fun remove() { close(); NativeMst5.removeE2E(path) }
        @Synchronized @Throws(IOException::class) internal fun requireHandle(): Long { if (handle == 0L) throw IOException("E2E identity is closed"); return handle }
    }
    class Session internal constructor(@JvmField internal val identity: Identity, @JvmField internal val peer: ByteArray, @JvmField internal val from: String?, @JvmField internal val to: String?)
    class Envelope private constructor(@JvmField internal val encoded: ByteArray) {
        @JvmField val version: Int = encoded[0].toInt() and 0xff
        @JvmField val nonce: String = Base64Codec.encode(encoded.copyOfRange(1, 25))
        @JvmField val ciphertext: String = Base64Codec.encode(encoded.copyOfRange(25, encoded.size))
        @JvmField val tag: String = ""
        constructor(version: Int, nonce: String?, ciphertext: String?, ignoredTag: String?) : this(composeEnvelope(version, nonce, ciphertext))
        companion object {
            private fun composeEnvelope(version: Int, nonce: String?, ciphertext: String?): ByteArray {
                val n = Base64Codec.decode(nonce.orEmpty()); val c = Base64Codec.decode(ciphertext.orEmpty())
                if ((version != 3 && version != 4) || n.size != 24 || c.size < 16) throw IllegalArgumentException("unsupported E2E envelope")
                return ByteArray(1 + n.size + c.size).also { out -> out[0] = version.toByte(); n.copyInto(out, 1); c.copyInto(out, 1 + n.size) }
            }
            @JvmStatic @Throws(IOException::class) fun decode(value: ByteArray?): Envelope { if (value == null || value.size < 41 || (value[0].toInt() != 3 && value[0].toInt() != 4)) throw IOException("unsupported E2E envelope; update the application"); return Envelope(value) }
        }
    }
    class Backup(@JvmField val version: Int, @JvmField val encoded: ByteArray?)
}
