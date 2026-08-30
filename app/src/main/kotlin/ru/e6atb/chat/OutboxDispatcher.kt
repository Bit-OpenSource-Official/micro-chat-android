package ru.e6atb.chat

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.Executors

internal object OutboxDispatcher {
    interface Listener { fun onChanged(entry: OutboxStore.Entry?, sent: MST5.Message?) }
    private val workers = Executors.newFixedThreadPool(3); private val active = HashSet<String>(); private val transfers = HashMap<String, MST5.TransferControl>()
    @JvmStatic fun dispatch(rawContext: Context?, client: MST5?, listener: Listener?) {
        if (rawContext == null || client == null || !SessionStore.hasSession(rawContext)) return
        val context = rawContext.applicationContext ?: rawContext; val server = SessionStore.server(context, MainActivity.DEFAULT_SERVER); val endpoint = SessionStore.transportEndpoint(context, MainActivity.DEFAULT_SERVER); val user = accountKey(context)
        OutboxStore.peersReady(context, server, user, System.currentTimeMillis()).forEach { peer ->
            val key = "$server\n$user\n$peer"; synchronized(active) { if (!active.add(key)) return@forEach }
            workers.execute { val worker = MST5(context, endpoint, SessionStore.token(context), SessionStore.userId(context), SessionStore.login(context)); try { drainPeer(context, worker, server, user, peer, listener) } finally { worker.close(); synchronized(active) { active.remove(key) } } }
        }
    }
    @JvmStatic fun cancel(clientMessageId: String?) { synchronized(transfers) { transfers[clientMessageId]?.cancel() } }
    private fun drainPeer(context: Context, client: MST5, server: String, user: String, peer: String, listener: Listener?) {
        while (SessionStore.hasSession(context)) {
            val entry = OutboxStore.claimNext(context, server, user, peer, System.currentTimeMillis()) ?: return; var lastUpdate = 0L; var lastPercent = -1
            val transfer = MST5.TransferControl(object : MST5.ProgressListener {
                override fun onProgress(completed: Long, total: Long) { val percent = if (total <= 0) 0 else minOf(100, (completed * 100L / total).toInt()); val now = System.currentTimeMillis(); if (percent != lastPercent && (now - lastUpdate >= 100 || percent >= 100)) { lastPercent = percent; lastUpdate = now; entry.progressPhase = "sending"; entry.progressPercent = percent; notifyListener(listener, entry, null) } }
            })
            synchronized(transfers) { transfers[entry.id] = transfer }; entry.progressPhase = if (entry.kind == "media" || entry.kind == "file") "sending" else ""; entry.progressPercent = 0; notifyListener(listener, entry, null)
            try {
                val sent = if (entry.kind == "media" || entry.kind == "file") {
                    val roomUser = if (entry.encrypted && entry.room) client.getPeer(entry.peer) else null
                    val prepared = if (roomUser == null) client.prepareMessage(entry.peer, entry.text, entry.id, entry.room, entry.replyToMessageId) else JSONObject()
                    if (entry.commentPostId > 0) prepared.put("comment_post_id", entry.commentPostId)
                    val media = ArrayList<MST5.MessageMedia>(); entry.attachments.forEach { attachment ->
                        val source = if (attachment.fileId.isNullOrEmpty()) object : MST5.UploadSource {
                            override fun open(): InputStream = when { !attachment.localPath.isNullOrEmpty() -> FileInputStream(File(attachment.localPath)); !attachment.sourceUri.isNullOrEmpty() -> context.contentResolver.openInputStream(Uri.parse(attachment.sourceUri)) ?: throw IllegalStateException("media source is unavailable"); else -> throw IllegalStateException("media source is unavailable") }
                            override fun openDescriptor(): ParcelFileDescriptor? = when { !attachment.localPath.isNullOrEmpty() -> ParcelFileDescriptor.open(File(attachment.localPath), ParcelFileDescriptor.MODE_READ_ONLY); !attachment.sourceUri.isNullOrEmpty() -> context.contentResolver.openFileDescriptor(Uri.parse(attachment.sourceUri), "r"); else -> null }
                        } else null
                        media += MST5.MessageMedia(attachment.clientId, attachment.fileId, attachment.name, attachment.mime, attachment.size, source, attachment.photo)
                    }; if (roomUser != null) client.sendGroupE2eMedia(roomUser, entry.text, media, transfer, entry.maxDsrAmount, entry.id, entry.replyToMessageId, entry.commentPostId).asOutgoing() else client.sendMessageWithMedia(prepared, media, transfer, entry.maxDsrAmount).asOutgoing()
                } else if (entry.commentPostId > 0 && entry.encrypted && entry.room) client.sendGroupE2eMessage(client.getPeer(entry.peer), entry.text, entry.id, entry.replyToMessageId, entry.commentPostId).asOutgoing()
                else if (entry.commentPostId > 0) client.sendChannelComment(entry.peer, entry.commentPostId, entry.text, entry.id, entry.replyToMessageId).asOutgoing()
                else if (entry.encrypted && entry.room) client.sendGroupE2eMessage(client.getPeer(entry.peer), entry.text, entry.id, entry.replyToMessageId).asOutgoing()
                else client.sendPreparedMessage(client.prepareMessage(entry.peer, entry.text, entry.id, entry.room, entry.replyToMessageId)).asOutgoing()
                ChatCache.appendMessage(context, server, SessionStore.login(context), OutboxStore.cachePeer(entry.peer, entry.commentPostId), sent); OutboxStore.complete(context, server, user, entry.id); notifyListener(listener, entry, sent)
            } catch (error: Throwable) {
                if (transfer.isCancelled || OutboxStore.find(context, server, user, entry.id) == null) return
                entry.attempts++; entry.progressPhase = ""; entry.progressPercent = 0; entry.error = error.message ?: error.javaClass.simpleName
                when { MST5.isInvalidTokenError(error) -> { entry.state = OutboxStore.QUEUED; entry.retryAt = System.currentTimeMillis() + 5000L }; MST5.isTransientError(error) -> { entry.state = OutboxStore.QUEUED; entry.retryAt = System.currentTimeMillis() + minOf(60000L, 1000L shl minOf(entry.attempts, 6)) }; else -> { entry.state = OutboxStore.FAILED; entry.retryAt = 0 } }
                OutboxStore.update(context, server, user, entry); notifyListener(listener, entry, null); return
            } finally { synchronized(transfers) { transfers.remove(entry.id) } }
        }
    }
    private fun notifyListener(listener: Listener?, entry: OutboxStore.Entry, sent: MST5.Message?) { listener?.onChanged(entry, sent) }
    @JvmStatic fun accountKey(context: Context?): String { val id = SessionStore.userId(context); return if (id.isEmpty()) SessionStore.login(context) else id }
}
