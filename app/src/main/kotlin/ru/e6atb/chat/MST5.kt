package ru.e6atb.chat

import android.content.Context
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import rs.ove.crypt.proto.CryptTcpClient
import rs.ove.crypt.proto.Mst5MediaClient
import rs.ove.crypt.proto.NativeE2E
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

class MST5(context: Context?, baseUrl: String, private var token: String, userId: String, login: String) {
    private val baseUrl: String
    private val transport: CryptTcpClient = CryptTcpClient()
    private val context: Context?
    private val transportProtocol: String
    private val e2eSessions: Mst5E2ESessionCache = Mst5E2ESessionCache()
    private val encryptedMedia: HashMap<String, MediaE2EContext?> = HashMap<String, MediaE2EContext?>()
    private var userId: String
    private var login: String
    private var e2eIdentity: NativeE2E.Identity? = null

    class InvalidTokenException(message: String) : RuntimeException(message)

    constructor(context: Context?, baseUrl: String) : this(context, baseUrl, "", "")

    constructor(context: Context?, baseUrl: String, token: String, login: String) : this(
        context,
        baseUrl,
        token,
        "",
        login
    )

    init {
        this.context = context?.applicationContext
        this.baseUrl = ru.e6atb.chat.MST5.Companion.trimSlash(baseUrl)
        this.transportProtocol = if (this.context == null)
            SessionStore.TRANSPORT_AUTO
        else
            SessionStore.transportProtocol(this.context)
        this.userId = userId
        this.login = login
        if (this.context != null && accountKey().isNotEmpty()) {
            this.e2eIdentity = localE2EIdentity()
        }
    }

    fun baseUrl(): String {
        return baseUrl
    }

    fun token(): String {
        return token
    }

    fun isEncryptedMediaFile(fileId: String): Boolean {
        return encryptedMedia.containsKey(fileId)
    }

    fun close() {
        transport.close()
        val identity: NativeE2E.Identity? = e2eIdentity
        e2eIdentity = null
        e2eSessions.clear()
        if (identity != null) identity.close()
    }

    @kotlin.Throws(Exception::class)
    fun startEmailAuth(email: String): String {
        val body: JSONObject = JSONObject()
        body.put("email", email)
        val out: JSONObject = post("/auth/email/start", body, 10000)
        return out.optString("debug_code")
    }

    @kotlin.Throws(Exception::class)
    fun verifyEmailAuth(email: String, code: String, cloudPassword: String): User {
        val body: JSONObject = JSONObject()
        body.put("email", email)
        body.put("code", code)
        body.put("cloud_password", cloudPassword)
        val out: JSONObject = post("/auth/email/verify", body, 10000)
        token = out.getString("token")
        val result: User = ru.e6atb.chat.MST5.Companion.user(out.getJSONObject("user"))
        tryActivateE2E(result, cloudPassword)
        return result
    }

    @kotlin.Throws(Exception::class)
    fun me(): User {
        val result: User = ru.e6atb.chat.MST5.Companion.user(get("/me", 10000).getJSONObject("user"))
        tryActivateE2E(result, null)
        return result
    }

    @kotlin.Throws(Exception::class)
    fun oauthDeviceRequest(userCode: String): OAuthDeviceRequest {
        val out: JSONObject = get(
            "/oauth/device/request?user_code=" + ru.e6atb.chat.MST5.Companion.enc(userCode.trim()),
            10000
        )
        return ru.e6atb.chat.MST5.OAuthDeviceRequest(
            out.optString("user_code"),
            out.optString("client_id"),
            out.optString("client_name"),
            out.optString("audience"),
            out.optString("action_description"),
            out.optLong("expires_at"),
            out.optString("status")
        )
    }

    @kotlin.Throws(Exception::class)
    fun oauthDeviceDecision(userCode: String, approve: Boolean) {
        val body: JSONObject = JSONObject()
        body.put("user_code", userCode.trim())
        body.put("decision", if (approve) "approve" else "reject")
        post("/oauth/device/decision", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun approveQrLogin(code: String, approve: Boolean) {
        val body = JSONObject().put("code", code.trim()).put("decision", if (approve) "approve" else "reject")
        post("/auth/qr/approve", body, 10000)
    }

    class OAuthDeviceRequest(
        @JvmField val userCode: String,
        @JvmField val clientID: String,
        @JvmField val clientName: String,
        @JvmField val audience: String,
        @JvmField val actionDescription: String,
        @JvmField val expiresAt: Long,
        @JvmField val status: String
    )

    @kotlin.Throws(Exception::class)
    fun setCloudPassword(password: String) {
        val value = password
        val body: JSONObject = JSONObject()
        body.put("password", value)
        if (value.length > 0) {
            val backup: NativeE2E.Backup? = e2eBackupForCloudPassword(value)
            if (backup != null) {
                body.put("e2e_backup", e2eBackupJson(backup))
            }
        }
        post("/cloud-password", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun resetCloudPassword(code: String) {
        val body: JSONObject = JSONObject()
        body.put("code", code.trim())
        post("/cloud-password/reset", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun resetE2EKey() {
        val body: JSONObject = JSONObject()
        body.put("confirm", "reset_e2e")
        post("/e2e/reset", body, 10000)
        if (context != null && accountKey().isNotEmpty()) {
            SessionStore.clearE2EIdentity(context, accountKey())
        }
        e2eIdentity = null
        e2eSessions.clearSessions()
        activateE2E(ru.e6atb.chat.MST5.User(userId, "", login, "", false, false), null)
    }

    /** Registers the active identity for a group/channel-specific E2E slot. */
    @kotlin.Throws(Exception::class)
    fun registerChatE2E(chatId: String) {
        val identity = e2eIdentity ?: throw SecurityException("E2E private key is unavailable on this device")
        val body = JSONObject()
            .put("version", 3)
            .put("public_key", identity.publicKeyB64)
            .put("chat_id", chatId.trim())
        post("/e2e/key", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun resetCloudPassword(email: String, code: String): User {
        val body: JSONObject = JSONObject()
        body.put("email", email.trim())
        body.put("code", code.trim())
        val out: JSONObject = post("/cloud-password/reset", body, 10000)
        token = out.getString("token")
        val result: User = ru.e6atb.chat.MST5.Companion.user(out.getJSONObject("user"))
        tryActivateE2E(result, null)
        return result
    }

    @kotlin.Throws(Exception::class)
    fun deleteAccount(code: String) {
        val body: JSONObject = JSONObject()
        body.put("code", code.trim())
        post("/account/delete", body, 10000)
        token = ""
    }

    data class InactivityPolicy(val periodMonths: Int, val scheduledAt: Long)

    @kotlin.Throws(Exception::class)
    fun accountInactivity(): InactivityPolicy {
        val value = get("/account/inactivity", 10000)
        return InactivityPolicy(value.optInt("period_months", 0), value.optLong("scheduled_at", 0L))
    }

    @kotlin.Throws(Exception::class)
    fun setAccountInactivity(periodMonths: Int): InactivityPolicy {
        if (periodMonths !in intArrayOf(0, 1, 3, 6, 12, 24)) throw IllegalArgumentException("unsupported inactivity period")
        val body = JSONObject().put("period_months", periodMonths)
        val value = post("/account/inactivity", body, 10000)
        return InactivityPolicy(value.optInt("period_months", 0), value.optLong("scheduled_at", 0L))
    }

    @kotlin.Throws(Exception::class)
    fun setUsername(username: String): User {
        val body: JSONObject = JSONObject()
        body.put("username", username.trim())
        val out: JSONObject = post("/username", body, 10000)
        val result: User = ru.e6atb.chat.MST5.Companion.user(out.getJSONObject("user"))
        tryActivateE2E(result, null)
        return result
    }

    @kotlin.Throws(Exception::class)
    fun setName(name: String): User {
        val body: JSONObject = JSONObject()
        body.put("name", name.trim())
        val out: JSONObject = post("/name", body, 10000)
        return ru.e6atb.chat.MST5.Companion.user(out.getJSONObject("user"))
    }

    @kotlin.Throws(Exception::class)
    fun setProfileDescription(profile: String, description: String): User? {
        val body: JSONObject = JSONObject()
        if (profile != null && profile.trim().length > 0) body.put("profile", profile.trim())
        body.put("description", if (description == null) "" else description.trim())
        val out: JSONObject = post("/profiles/description", body, 10000).getJSONObject("profile")
        return if (out.has("kind")) ru.e6atb.chat.MST5.Companion.roomUser(out) else ru.e6atb.chat.MST5.Companion.user(
            out
        )
    }

    @kotlin.Throws(Exception::class)
    fun setPrivacy(messagePrivacy: String, callPrivacy: String): User {
        return setPrivacy(messagePrivacy, callPrivacy, "everyone")
    }

    @kotlin.Throws(Exception::class)
    fun setPrivacy(messagePrivacy: String, callPrivacy: String, invitePrivacy: String): User {
        val body: JSONObject = JSONObject()
        body.put("message_privacy", if (messagePrivacy == null) "" else messagePrivacy.trim())
        body.put("call_privacy", if (callPrivacy == null) "" else callPrivacy.trim())
        body.put("invite_privacy", if (invitePrivacy == null) "" else invitePrivacy.trim())
        val out: JSONObject = post("/privacy", body, 10000)
        return ru.e6atb.chat.MST5.Companion.user(out.getJSONObject("user"))
    }

    @get:kotlin.Throws(Exception::class)
    val contacts: List<User>
        get() {
            val arr: JSONArray = get("/contacts", 10000).getJSONArray("contacts")
        val contacts: ArrayList<User> = ArrayList<User>(arr.length())
            for (i in 0..<arr.length()) {
                contacts.add(ru.e6atb.chat.MST5.Companion.user(arr.getJSONObject(i)))
            }
            return contacts
        }

    @kotlin.Throws(Exception::class)
    fun addContact(address: String): User {
        val body: JSONObject = JSONObject()
        body.put("user", if (address == null) "" else address.trim())
        return ru.e6atb.chat.MST5.Companion.user(post("/contacts/add", body, 10000).getJSONObject("contact"))
    }

    @kotlin.Throws(Exception::class)
    fun deleteContact(address: String) {
        val body: JSONObject = JSONObject()
        body.put("user", if (address == null) "" else address.trim())
        post("/contacts/delete", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun addChatMember(chat: String, user: String): Chat {
        val body: JSONObject = JSONObject()
        body.put("chat", if (chat == null) "" else chat.trim())
        body.put("user", if (user == null) "" else user.trim())
        val out: JSONObject? = post("/chats/members/add", body, 10000).getJSONObject("chat")
        val room: User = ru.e6atb.chat.MST5.Companion.roomUser(out)
        return ru.e6atb.chat.MST5.Chat(room.id, room, null, false)
    }

    @kotlin.Throws(Exception::class)
    fun removeChatMember(chat: String, user: String): Chat {
        val body: JSONObject = JSONObject()
        body.put("chat", if (chat == null) "" else chat.trim())
        body.put("user", if (user == null) "" else user.trim())
        val out: JSONObject? = post("/chats/members/remove", body, 10000).getJSONObject("chat")
        val room: User = ru.e6atb.chat.MST5.Companion.roomUser(out)
        return ru.e6atb.chat.MST5.Chat(room.id, room, null, false)
    }

    @kotlin.Throws(Exception::class)
    fun leaveChat(chat: String, me: String) {
        removeChatMember(chat, me)
    }

    @kotlin.Throws(Exception::class)
    fun setChatTitle(chat: String, title: String): Chat {
        val body: JSONObject = JSONObject()
        body.put("chat", if (chat == null) "" else chat.trim())
        body.put("title", if (title == null) "" else title.trim())
        val out: JSONObject? = post("/chats/title", body, 10000).getJSONObject("chat")
        val room: User = ru.e6atb.chat.MST5.Companion.roomUser(out)
        return ru.e6atb.chat.MST5.Chat(room.id, room, null, false)
    }

    @kotlin.Throws(Exception::class)
    fun setChannelUsername(chat: String, username: String): Chat {
        val body: JSONObject = JSONObject()
        body.put("chat", if (chat == null) "" else chat.trim())
        body.put("username", if (username == null) "" else username.trim())
        val out: JSONObject? = post("/channels/username", body, 10000).getJSONObject("chat")
        val room: User = ru.e6atb.chat.MST5.Companion.roomUser(out)
        return ru.e6atb.chat.MST5.Chat(room.id, room, null, false)
    }

    @kotlin.Throws(Exception::class)
    fun setChannelComments(chat: String, enabled: Boolean): Chat {
        val body: JSONObject = JSONObject()
        body.put("chat", if (chat == null) "" else chat.trim())
        body.put("enabled", enabled)
        val out: JSONObject? = post("/channels/comments/settings", body, 10000).getJSONObject("chat")
        val room: User = ru.e6atb.chat.MST5.Companion.roomUser(out)
        return ru.e6atb.chat.MST5.Chat(room.id, room, null, false)
    }

    @kotlin.Throws(Exception::class)
    fun sendChannelComment(chat: String, postId: Long, text: String, clientMessageId: String): Message {
        return sendChannelComment(chat, postId, text, clientMessageId, 0)
    }

    @kotlin.Throws(Exception::class)
    fun sendChannelComment(
        chat: String,
        postId: Long,
        text: String,
        clientMessageId: String,
        replyToMessageId: Long
    ): Message {
        val body: JSONObject = JSONObject()
        body.put("chat", if (chat == null) "" else chat.trim())
        body.put("post_id", postId)
        body.put("text", if (text == null) "" else text.trim())
        body.put("client_message_id", if (clientMessageId == null) "" else clientMessageId)
        if (replyToMessageId > 0) body.put("reply_to_message_id", replyToMessageId)
        return message(post("/channels/comments/send", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun getChannelComments(chat: String, postId: Long, before: Long, limit: Int): CommentPage {
        val path = ("/channels/comments?chat=" + ru.e6atb.chat.MST5.Companion.enc(chat) + "&post_id=" + postId
                + "&before=" + before + "&limit=" + limit)
        val out: JSONObject = get(path, 10000)
        val raw: JSONArray = out.getJSONArray("messages")
        val messages: ArrayList<Message> = ArrayList<Message>(raw.length())
        for (i in 0..<raw.length()) messages.add(message(raw.getJSONObject(i))!!)
        return ru.e6atb.chat.MST5.CommentPage(
            ru.e6atb.chat.MST5.Companion.roomUser(out.optJSONObject("peer")),
            message(out.optJSONObject("post")),
            messages
        )
    }

    @kotlin.Throws(Exception::class)
    fun createGroup(title: String, members: List<String>?): Chat {
        return createRoom("/groups", title, "", members)
    }

    @kotlin.Throws(Exception::class)
    fun createChannel(title: String, username: String, members: List<String>?): Chat {
        return createRoom("/channels", title, username, members)
    }

    @kotlin.Throws(Exception::class)
    private fun createRoom(path: String, title: String, username: String, members: List<String>?): Chat {
        val body: JSONObject = JSONObject()
        body.put("title", if (title == null) "" else title.trim())
        if (username != null && username.trim().length > 0) body.put("username", username.trim())
        val arr: JSONArray = JSONArray()
        if (members != null) {
            for (member in members) {
                if (member != null && member.trim().length > 0) arr.put(member.trim())
            }
        }
        body.put("members", arr)
        val out: JSONObject? = post(path, body, 10000).getJSONObject("chat")
        val room: User = ru.e6atb.chat.MST5.Companion.roomUser(out)
        return ru.e6atb.chat.MST5.Chat(room.id, room, null, false)
    }

    @kotlin.Throws(Exception::class)
    fun sendMessage(to: String, text: String): Message {
        return sendPreparedMessage(prepareMessage(to, text, null, false, 0))
    }

    @kotlin.Throws(Exception::class)
    fun prepareMessage(to: String, text: String, clientMessageId: String?, plain: Boolean): JSONObject {
        return prepareMessage(to, text, clientMessageId, plain, 0)
    }

    @kotlin.Throws(Exception::class)
    fun prepareMessage(
        to: String,
        text: String,
        clientMessageId: String?,
        plain: Boolean,
        replyToMessageId: Long
    ): JSONObject {
        if (plain) {
            val body: JSONObject = JSONObject()
            body.put("to", to)
            body.put("text", text)
            if (clientMessageId != null && clientMessageId.length > 0) body.put("client_message_id", clientMessageId)
            if (replyToMessageId > 0) body.put("reply_to_message_id", replyToMessageId)
            return body
        }
        if (e2eIdentity == null) {
            throw SecurityException("E2E private key is unavailable on this device")
        }
        val envelope: NativeE2E.Envelope
        try {
            val peer = peerE2EKey(to)
            envelope = NativeE2E.seal(
                e2eSession(peer, accountAddress(), peer.user.id),
                accountAddress(),
                peer.user.id,
                text
            )
        } catch (e: RuntimeException) {
            if (e !is ApiException || !"E2E_KEY_NOT_REGISTERED".equals(e.errorCode)) {
                throw e
            }
            return prepareMessage(to, text, clientMessageId, true, replyToMessageId)
        }
        val body: JSONObject = JSONObject()
        body.put("to", to)
        if (clientMessageId != null && clientMessageId.length > 0) body.put("client_message_id", clientMessageId)
        if (replyToMessageId > 0) body.put("reply_to_message_id", replyToMessageId)
        val e2e: JSONObject = JSONObject()
        e2e.put("version", envelope.version)
        e2e.put("nonce", envelope.nonce)
        e2e.put("ciphertext", envelope.ciphertext)
        body.put("e2e", e2e)
        return body
    }

    @kotlin.Throws(Exception::class)
    fun sendPreparedMessage(body: JSONObject?): Message {
        return message(post("/send", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun sendPlainMessage(to: String, text: String): Message {
        return sendPreparedMessage(prepareMessage(to, text, null, true, 0))
    }

    @kotlin.Throws(Exception::class)
    fun createBot(login: String): BotCreation {
        val body: JSONObject = JSONObject()
        body.put("username", login)
        val out: JSONObject = post("/bots", body, 10000)
        return ru.e6atb.chat.MST5.BotCreation(
            ru.e6atb.chat.MST5.Companion.user(out.getJSONObject("user")),
            out.optString("token")
        )
    }

    @kotlin.Throws(Exception::class)
    fun resetBotToken(login: String): BotCreation {
        val body: JSONObject = JSONObject()
        body.put("username", login)
        val out: JSONObject = post("/bots/token/reset", body, 10000)
        return ru.e6atb.chat.MST5.BotCreation(
            ru.e6atb.chat.MST5.Companion.user(out.getJSONObject("user")),
            out.optString("token")
        )
    }

    @kotlin.Throws(Exception::class)
    fun getBotCommands(bot: String): List<BotCommand> {
        val values: JSONArray? =
            get("/bots/commands?bot=" + ru.e6atb.chat.MST5.Companion.enc(if (bot == null) "" else bot.trim()), 10000)
                .optJSONArray("commands")
        val result: ArrayList<BotCommand> = ArrayList<BotCommand>()
        var index = 0
        while (values != null && index < values.length()) {
            val value: JSONObject? = values.optJSONObject(index)
            if (value == null) {
                index++
                continue
            }
            val command: String = value.optString("command").trim()
            if (command.length > 0) result.add(ru.e6atb.chat.MST5.BotCommand(command, value.optString("description")))
            index++
        }
        return result
    }

    @get:kotlin.Throws(Exception::class)
    val stickerPacks: List<StickerPack>
        get() {
            val values: JSONArray? = get("/stickers/packs", 10000).optJSONArray("packs")
            val result: ArrayList<StickerPack> = ArrayList<StickerPack>()
            var index = 0
            while (values != null && index < values.length()) {
                val value: JSONObject? = values.optJSONObject(index)
                if (value != null) result.add(ru.e6atb.chat.MST5.Companion.stickerPack(value))
                index++
            }
            return result
        }

    @kotlin.Throws(Exception::class)
    fun purchaseStickerPack(id: String): StickerPack {
        val body: JSONObject = JSONObject()
        body.put("id", if (id == null) "" else id.trim())
        return ru.e6atb.chat.MST5.Companion.stickerPack(
            post(
                "/stickers/packs/purchase",
                body,
                20000
            ).getJSONObject("pack")
        )
    }

    @kotlin.Throws(Exception::class)
    fun sendSticker(to: String, packId: String, fileId: String, clientMessageId: String): Message {
        val body: JSONObject = JSONObject()
        body.put("to", if (to == null) "" else to.trim())
        body.put("pack_id", if (packId == null) "" else packId.trim())
        body.put("file_id", if (fileId == null) "" else fileId.trim())
        body.put("client_message_id", if (clientMessageId == null) "" else clientMessageId.trim())
        return message(post("/stickers/send", body, 15000).getJSONObject("message"))!!
    }

    interface UploadSource {
        @kotlin.Throws(Exception::class)
        fun open(): InputStream

        @kotlin.Throws(Exception::class)
        fun openDescriptor(): ParcelFileDescriptor?
    }

    class MessageMedia(
        clientId: String,
        fileId: String,
        name: String,
        mime: String,
        @JvmField val size: Long,
        @JvmField val source: UploadSource?,
        @JvmField val photo: Boolean
    ) {
        @JvmField val clientId: String
        @JvmField val fileId: String
        @JvmField val name: String
        @JvmField val mime: String

        constructor(
            clientId: String,
            fileId: String,
            name: String,
            mime: String,
            size: Long,
            source: UploadSource?
        ) : this(clientId, fileId, name, mime, size, source, false)

        init {
            this.clientId = if (clientId == null) "" else clientId
            this.fileId = if (fileId == null) "" else fileId
            this.name = if (name == null || name.length === 0) "file" else name
            this.mime = if (mime == null || mime.length === 0) "application/octet-stream" else mime
        }

        companion object {
            fun existing(file: FileInfo?): MessageMedia {
                return ru.e6atb.chat.MST5.MessageMedia(
                    "",
                    if (file == null) "" else file.id,
                    if (file == null) "file" else file.name,
                    if (file == null) "application/octet-stream" else file.mime,
                    if (file == null) 0 else file.size,
                    null
                )
            }
        }
    }

    interface ProgressListener {
        fun onProgress(completed: Long, total: Long)
    }

    class TransferControl(private val listener: ProgressListener?) {
        @kotlin.concurrent.Volatile
        var isCancelled: Boolean = false
            private set

        fun cancel() {
            this.isCancelled = true
        }

        fun progress(completed: Long, total: Long) {
            if (listener != null) listener.onProgress(completed, total)
        }
    }

    @kotlin.Throws(Exception::class)
    fun quoteMedia(sizeBytes: Long): MediaQuote {
        val body: JSONObject = JSONObject()
        val media: JSONArray = JSONArray()
        val item: JSONObject = JSONObject()
        item.put("client_id", "quote-attachment-0001")
        item.put("name", "file")
        item.put("mime", "application/octet-stream")
        item.put("size", sizeBytes)
        media.put(item)
        body.put("media", media)
        val out: JSONObject = post("/media/quote", body, 10000)
        return ru.e6atb.chat.MST5.MediaQuote(
            out.optLong("size_bytes", sizeBytes),
            out.optLong("dsr_amount", out.optLong("dsr_required")),
            out.optBoolean("free")
        )
    }

    @kotlin.Throws(Exception::class)
    fun quoteMedia(items: List<MessageMedia>?): MediaQuote {
        val body: JSONObject = JSONObject()
        body.put("media", ru.e6atb.chat.MST5.Companion.mediaRequest(items, false))
        var size: Long = 0
        if (items != null) for (item in items) if (item!!.fileId.length === 0) size += item.size
        val out: JSONObject = post("/media/quote", body, 10000)
        return ru.e6atb.chat.MST5.MediaQuote(
            out.optLong("size_bytes", size),
            out.optLong("dsr_amount", out.optLong("dsr_required")),
            out.optBoolean("free")
        )
    }

    @kotlin.Throws(Exception::class)
    fun uploadPublicAvatar(image: File, transfer: TransferControl?): FileInfo {
        if (image == null || !image.isFile() || image.length() <= 0 || image.length() > 1048576L) {
            throw IOException("avatar image must be 1 MiB or smaller")
        }
        val request: JSONObject = JSONObject()
        request.put("name", "avatar.webp")
        request.put("mime", "image/webp")
        request.put("size", image.length())
        val prepared: JSONObject = post("/avatars/prepare", request, 10000)
        val upload: JSONObject = prepared.getJSONObject("upload")
        val descriptor: ParcelFileDescriptor = ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
        val uploads: ArrayList<Mst5MediaClient.Upload> = ArrayList<Mst5MediaClient.Upload>()
            uploads.add(
                Mst5MediaClient.Upload(
                    upload.getString("endpoint"), upload.getString("server_public_key"),
                    upload.getString("ticket"), upload.getString("file_id"), upload.getLong("size"), descriptor
                )
            )
            Mst5MediaClient.uploadDescriptors(uploads, if (transfer == null) null else object : Mst5MediaClient.Observer {
                override fun isCancelled(): Boolean = transfer.isCancelled
                override fun onProgress(done: Long, total: Long) {
                    transfer.progress(done, total)
                }
            })
        } finally {
            try {
                descriptor.close()
            } catch (ignored: Exception) {
            }
        }
        val complete: JSONObject = JSONObject()
        complete.put("file_id", upload.getString("file_id"))
        return ru.e6atb.chat.MST5.Companion.file(post("/avatars/commit", complete, 30000).getJSONObject("avatar"))!!
    }

    @kotlin.Throws(Exception::class)
    fun deletePublicAvatar(): User = ru.e6atb.chat.MST5.Companion.user(
        post("/avatars/delete", JSONObject(), 10_000).getJSONObject("user")
    )

    @kotlin.Throws(Exception::class)
    fun sendMessageWithMedia(
        preparedBody: JSONObject?, items: List<MessageMedia>?,
        transfer: TransferControl?, maxDsrAmount: Long
    ): Message {
        val body: JSONObject = JSONObject(if (preparedBody == null) "{}" else preparedBody.toString())
        val preparedPhotoFiles: ArrayList<File> = ArrayList<File>()
        val uploadItems = preparePhotoUploads(items, preparedPhotoFiles)
        val encryptMedia = body.optJSONObject("e2e") != null
        body.put("media", ru.e6atb.chat.MST5.Companion.mediaRequest(uploadItems, encryptMedia))
        body.put("max_dsr_amount", Math.max(0, maxDsrAmount))
        val prepared: JSONObject = post("/messages/prepare", body, 10000)
        if (prepared.optBoolean("complete") && prepared.optJSONObject("message") != null) {
            return message(prepared.getJSONObject("message"))!!
        }
        val operationId: String = prepared.getString("operation_id")
        val uploads: JSONArray? = prepared.optJSONArray("uploads")
        var total: Long = 0
        if (uploadItems != null) for (item in uploadItems) if (item!!.fileId.length === 0) total += item.size
        val progressTotal = total
        val descriptors: ArrayList<ParcelFileDescriptor?> = ArrayList<ParcelFileDescriptor?>()
        val batch: ArrayList<Mst5MediaClient.Upload> = ArrayList<Mst5MediaClient.Upload>()
        var encryptedCompleted: Long = 0
        try {
            var i = 0
            while (uploads != null && i < uploads.length()) {
                val ticket: JSONObject = uploads.getJSONObject(i)
                val clientId: String = ticket.getString("client_id")
                var source: MessageMedia? = null
                if (uploadItems != null) for (item in uploadItems) if (item!!.clientId.equals(clientId)) {
                    source = item
                    break
                }
                val expectedSize =
                    if (source == null) -1 else (if (encryptMedia) NativeE2E.encryptedMediaSize(source.size) else source.size)
                if (source == null || source.source == null || ticket.optLong("size") !== expectedSize) {
                    throw IOException("server returned an invalid media ticket")
                }
                val descriptor: ParcelFileDescriptor = source.source.openDescriptor()
                    ?: throw IOException("media descriptor is not available")
                descriptors.add(descriptor)
                if (encryptMedia) {
                    val to: String = body.optString("to")
                    val peer = peerE2EKey(to)
                    val progressBase = encryptedCompleted
                    val sourceSize = source.size
                    NativeE2E.uploadMedia(
                        e2eSession(peer, accountAddress(), peer.user.id),
                        ticket.getString("endpoint"), ticket.getString("server_public_key"),
                        ticket.getString("ticket"), ticket.getString("file_id"), source.size, descriptor,
                        if (transfer == null) null else object : Mst5MediaClient.Observer {
                            override fun isCancelled(): Boolean = transfer.isCancelled
                            override fun onProgress(done: Long, ignored: Long) {
                                transfer.progress(
                                    Math.min(progressTotal, progressBase + Math.min(sourceSize, done)),
                                    progressTotal
                                )
                            }
                        })
                    encryptedCompleted += source.size
                } else {
                    batch.add(
                        Mst5MediaClient.Upload(
                            ticket.getString("endpoint"), ticket.getString("server_public_key"),
                            ticket.getString("ticket"), ticket.getString("file_id"), expectedSize, descriptor
                        )
                    )
                }
                i++
            }
            Mst5MediaClient.uploadDescriptors(
                batch,
                if (transfer == null) null else object : Mst5MediaClient.Observer {
                    override fun isCancelled(): Boolean = transfer.isCancelled
                    override fun onProgress(completed: Long, ignored: Long) {
                        transfer.progress(completed, progressTotal)
                    }
                })
        } catch (error: Exception) {
            try {
                val cancel: JSONObject = JSONObject()
                cancel.put("operation_id", operationId)
                post("/messages/cancel", cancel, 10000)
            } catch (ignored: Exception) {
            }
            throw error
        } finally {
            for (descriptor in descriptors) {
                try {
                    descriptor?.close()
                } catch (ignored: Exception) {
                }
            }
            for (photo in preparedPhotoFiles) photo.delete()
        }
        val complete: JSONObject = JSONObject()
        complete.put("operation_id", operationId)
        return message(post("/messages/commit", complete, 30000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    private fun preparePhotoUploads(items: List<MessageMedia>?, temporary: MutableList<File>): List<MessageMedia> {
        val prepared: ArrayList<MessageMedia> = ArrayList<MessageMedia>()
        if (items == null) return prepared
        for (item in items) {
            if (!item.photo || item.fileId.length > 0 || item.source == null) {
                prepared.add(item)
                continue
            }
            val input: InputStream = item.source?.open() ?: throw IOException("photo source is not available")
            var original: ByteArray?
            try {
                original = ru.e6atb.chat.MST5.Companion.readExactly(input, item.size)
            } finally {
                try {
                    input.close()
                } catch (ignored: Exception) {
                }
            }
            val webp: ByteArray = rs.ove.crypt.proto.Mst5ImageDecoder.prepareWebp(original, 1920, false)
            if (webp.size <= 0 || webp.size > (12 shl 20)) throw IOException("compressed photo is too large")
            val photo: File =
                File.createTempFile("mst5-photo-", ".webp", if (context == null) null else context.getCacheDir())
            val output: FileOutputStream = FileOutputStream(photo)
            try {
                output.write(webp)
            } finally {
                try {
                    output.close()
                } catch (ignored: Exception) {
                }
            }
            temporary.add(photo)
            prepared.add(
                ru.e6atb.chat.MST5.MessageMedia(
                    item.clientId,
                    item.fileId,
                    ru.e6atb.chat.MST5.Companion.webpName(item.name),
                    "image/webp",
                    photo.length(),
                    object : UploadSource {
                        @kotlin.Throws(Exception::class)
                        override fun open(): InputStream {
                            return FileInputStream(photo)
                        }

                        @kotlin.Throws(Exception::class)
                        override fun openDescriptor(): ParcelFileDescriptor {
                            return ParcelFileDescriptor.open(photo, ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                    },
                    true
                )
            )
        }
        return prepared
    }

    @kotlin.Throws(Exception::class)
    fun cancelMessageOperation(clientMessageId: String): JSONObject {
        val body: JSONObject = JSONObject()
        body.put("client_message_id", if (clientMessageId == null) "" else clientMessageId)
        return post("/messages/cancel", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun forwardMedia(messageId: Long, to: String, clientMessageId: String): Message {
        val body: JSONObject = JSONObject()
        body.put("message_id", messageId)
        body.put("to", to)
        if (clientMessageId != null && clientMessageId.length > 0) body.put("client_message_id", clientMessageId)
        return message(post("/forward", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun editMessage(id: Long, peer: String, text: String, plain: Boolean): Message {
        val body: JSONObject = prepareMessage(peer, text, null, plain)
        body.remove("to")
        body.put("id", id)
        return message(post("/edit", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun markRead(peer: String) {
        val body: JSONObject = JSONObject()
        body.put("peer", peer)
        post("/read", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun sendCallback(to: String, messageId: Long, callback: String) {
        val body: JSONObject = JSONObject()
        body.put("to", to)
        body.put("message_id", messageId)
        body.put("callback", if (callback == null) "" else callback)
        post("/callback", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun deleteMessage(id: Long): Message {
        val body: JSONObject = JSONObject()
        body.put("id", id)
        return message(post("/delete", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun favoriteMessage(id: Long): Message {
        val body: JSONObject = JSONObject()
        body.put("id", id)
        return message(post("/favorite", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun deleteChat(peer: String) {
        val body: JSONObject = JSONObject()
        body.put("peer", peer)
        post("/chats/delete", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun banUser(login: String) {
        val body: JSONObject = JSONObject()
        body.put("username", login)
        post("/users/ban", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun unbanUser(login: String) {
        val body: JSONObject = JSONObject()
        body.put("username", login)
        post("/users/unban", body, 10000)
    }

    @kotlin.Throws(Exception::class)
    fun downloadFileBytes(fileID: String, maxBytes: Int): ByteArray {
        val ticket: JSONObject = get("/file/ticket?id=" + ru.e6atb.chat.MST5.Companion.enc(fileID), 10000)
        val announced: Long = ticket.optLong("size", -1)
        val e2e: MediaE2EContext? = encryptedMedia.get(if (fileID == null) "" else fileID)
        if (e2e != null) {
            return NativeE2E.downloadMediaBytes(
                e2eSessionForMedia(e2e), ticket.getString("endpoint"),
                ticket.getString("server_public_key"), ticket.getString("ticket"), ticket.getString("file_id"),
                announced, if (maxBytes > 0) maxBytes else Integer.MAX_VALUE
            )
        }
        if (maxBytes > 0 && announced > maxBytes) throw RuntimeException("file is too large")
        return Mst5MediaClient.downloadBytes(
            ticket.getString("endpoint"), ticket.getString("server_public_key"),
            ticket.getString("ticket"), ticket.getString("file_id"), announced,
            if (maxBytes > 0) maxBytes else Integer.MAX_VALUE
        )
    }

    @kotlin.Throws(Exception::class)
    fun downloadFile(fileID: String, target: File, maxBytes: Long, listener: ProgressListener?) {
        val ticket: JSONObject = get("/file/ticket?id=" + ru.e6atb.chat.MST5.Companion.enc(fileID), 10000)
        val announced: Long = ticket.optLong("size", -1)
        if (maxBytes > 0 && announced > maxBytes) throw IOException("file is too large")
        val temporary: File = File(target.getParentFile(), target.getName() + ".part")
        try {
            if (announced < 0) throw IOException("server did not announce media size")
            val descriptor: ParcelFileDescriptor = ParcelFileDescriptor.open(
                temporary,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            )
            try {
                val e2e: MediaE2EContext? = encryptedMedia.get(fileID)
                val observer: Mst5MediaClient.Observer? = if (listener == null) null else object : Mst5MediaClient.Observer {
                    override fun isCancelled(): Boolean = false
                    override fun onProgress(completed: Long, total: Long) {
                        listener.onProgress(completed, total)
                    }
                }
                if (e2e == null) {
                    Mst5MediaClient.downloadDescriptor(
                        ticket.getString("endpoint"), ticket.getString("server_public_key"),
                        ticket.getString("ticket"), ticket.getString("file_id"), announced, descriptor, observer
                    )
                } else {
                    NativeE2E.downloadMedia(
                        e2eSessionForMedia(e2e), ticket.getString("endpoint"), ticket.getString("server_public_key"),
                        ticket.getString("ticket"), ticket.getString("file_id"), announced, descriptor, observer
                    )
                }
            } finally {
                try {
                    descriptor.close()
                } catch (ignored: Exception) {
                }
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) throw IOException("cannot save downloaded file")
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    @kotlin.Throws(Exception::class)
    private fun e2eSessionForMedia(media: MediaE2EContext): NativeE2E.Session {
        if (e2eIdentity == null) throw SecurityException("E2E private key is unavailable on this device")
        val sentByMe = if (userId.length > 0) userId!!.equals(media.from.id) else login!!.equals(media.from.login)
        val peerUser = if (sentByMe) media.to else media.from
        val peerAddress: String = if (peerUser.id.length > 0) peerUser.id else peerUser.login
        val peer = peerE2EKey(peerAddress)
        return e2eSession(peer, media.from.id, media.to.id)
    }

    @kotlin.Throws(Exception::class)
    fun sendCall(to: String, action: String) {
        val body: JSONObject = JSONObject()
        body.put("to", to)
        body.put("action", action)
        post("/call", body, 10000)
    }

    @get:kotlin.Throws(Exception::class)
    val chats: List<Chat>
        get() {
            val arr: JSONArray = get("/chats", 10000).getJSONArray("chats")
        val chats: ArrayList<Chat> = ArrayList<Chat>(arr.length())
            for (i in 0..<arr.length()) {
                chats.add(chat(arr.getJSONObject(i)))
            }
            return chats
        }

    @kotlin.Throws(Exception::class)
    fun getHistory(peer: String, after: Long, limit: Int): List<Message> {
        val path = "/history?peer=" + ru.e6atb.chat.MST5.Companion.enc(peer) + "&after=" + after + "&limit=" + limit
        return historyPage(path).messages
    }

    @kotlin.Throws(Exception::class)
    fun getHistoryBefore(peer: String, before: Long, limit: Int): List<Message> {
        return getHistoryPageBefore(peer, before, limit).messages
    }

    /** Server-side chat search and media-only filtering. */
    @kotlin.Throws(Exception::class)
    fun searchHistory(peer: String, query: String, mediaOnly: Boolean, limit: Int): List<Message> {
        var path = "/history?peer=" + ru.e6atb.chat.MST5.Companion.enc(peer) + "&limit=" + limit.coerceIn(1, 100)
        if (query.isNotBlank()) path += "&q=" + ru.e6atb.chat.MST5.Companion.enc(query.trim())
        if (mediaOnly) path += "&media=1"
        return historyPage(path).messages
    }

    @kotlin.Throws(Exception::class)
    fun getHistoryPageBefore(peer: String, before: Long, limit: Int): HistoryPage {
        val path = "/history?peer=" + ru.e6atb.chat.MST5.Companion.enc(peer) + "&before=" + before + "&limit=" + limit
        return historyPage(path)
    }

    @kotlin.Throws(Exception::class)
    private fun historyPage(path: String): HistoryPage {
        val out: JSONObject = get(path, 10000)
        val arr: JSONArray = out.getJSONArray("messages")
        val messages: ArrayList<Message> = ArrayList<Message>(arr.length())
        for (i in 0..<arr.length()) {
            messages.add(message(arr.getJSONObject(i))!!)
        }
        val peer: JSONObject? = out.optJSONObject("peer")
        return ru.e6atb.chat.MST5.HistoryPage(
            if (peer != null && peer.has("title")) ru.e6atb.chat.MST5.Companion.roomUser(
                peer
            ) else ru.e6atb.chat.MST5.Companion.user(peer), messages
        )
    }

    @kotlin.Throws(Exception::class)
    fun getUpdates(after: Long, timeoutSec: Int): List<Update> {
        val path = "/updates?after=" + after + "&timeout=" + timeoutSec
        val arr: JSONArray = get(path, (timeoutSec + 5) * 1000).getJSONArray("updates")
        val updates: ArrayList<Update> = ArrayList<Update>(arr.length())
        for (i in 0..<arr.length()) {
            updates.add(update(arr.getJSONObject(i)))
        }
        return updates
    }

    @get:kotlin.Throws(Exception::class)
    val wallet: WalletInfo
        get() = ru.e6atb.chat.MST5.Companion.walletInfo(get("/wallet", 10000).getJSONObject("wallet"))

    @kotlin.Throws(Exception::class)
    fun sendDastars(to: String, amount: Long, comment: String): WalletInfo {
        val body: JSONObject = JSONObject()
        body.put("to", to)
        body.put("amount", amount)
        body.put("comment", if (comment == null) "" else comment.trim())
        return ru.e6atb.chat.MST5.Companion.walletInfo(post("/wallet/send", body, 10000).getJSONObject("wallet"))
    }

    @kotlin.Throws(Exception::class)
    fun reactMessage(messageId: Long, emoji: String): Message {
        val body: JSONObject = JSONObject()
        body.put("message_id", messageId)
        body.put("emoji", if (emoji == null) "" else emoji)
        return message(post("/reactions", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun sendPaidReaction(messageId: Long, amount: Long, idempotencyKey: String): Message {
        val body: JSONObject = JSONObject()
        body.put("message_id", messageId)
        body.put("amount", amount)
        body.put("idempotency_key", idempotencyKey)
        return message(post("/reactions/paid", body, 10000).getJSONObject("message"))!!
    }

    @kotlin.Throws(Exception::class)
    fun getWalletHistory(limit: Int): List<WalletTransaction> {
        val arr: JSONArray = get("/wallet/history?limit=" + limit, 10000).getJSONArray("transactions")
        val out: ArrayList<WalletTransaction> = ArrayList<WalletTransaction>(arr.length())
        for (i in 0..<arr.length()) {
            out.add(ru.e6atb.chat.MST5.Companion.walletTransaction(arr.getJSONObject(i)))
        }
        return out
    }

    @get:kotlin.Throws(Exception::class)
    val nodeStatuses: List<NodeStatus>
        get() {
            val arr: JSONArray = get("/nodes/status", 10000).getJSONArray("nodes")
            val out: ArrayList<NodeStatus> = ArrayList<NodeStatus>(arr.length())
            for (i in 0..<arr.length()) {
                val item: JSONObject = arr.getJSONObject(i)
                out.add(
                    ru.e6atb.chat.MST5.NodeStatus(
                        item.optString("type"),
                        item.optString("name"),
                        item.optString("status"),
                        item.optInt("available"),
                        item.optInt("total")
                    )
                )
            }
            return out
        }

    @get:kotlin.Throws(Exception::class)
    val sessions: List<SessionInfo>
        get() {
            val arr: JSONArray = get("/sessions", 10000).getJSONArray("sessions")
            val out: ArrayList<SessionInfo> =
                ArrayList<SessionInfo>(arr.length())
            for (i in 0..<arr.length()) {
                val item: JSONObject = arr.getJSONObject(i)
                out.add(
                    ru.e6atb.chat.MST5.SessionInfo(
                        item.optString("id"),
                        item.optLong("created_at"),
                        item.optLong("last_seen"),
                        item.optString("label"),
                        item.optString("device_model"),
                        item.optBoolean("current")
                    )
                )
            }
            return out
        }

    @kotlin.Throws(Exception::class)
    fun revokeOtherSessions(): Int {
        val out: JSONObject = post("/sessions/revoke-others", JSONObject(), 10000)
        return out.optInt("revoked")
    }

    @kotlin.Throws(Exception::class)
    fun revokeSession(id: String): Int {
        val body: JSONObject = JSONObject()
        body.put("id", if (id == null) "" else id)
        return post("/sessions/revoke", body, 10000).optInt("revoked")
    }

    class VoiceAccess(@JvmField val endpoint: String, @JvmField val serverPublicKey: String, @JvmField val ticket: String)

    @kotlin.Throws(Exception::class)
    fun voiceAccess(peer: String): VoiceAccess {
        val body: JSONObject = JSONObject()
        body.put("peer", peer)
        val response: JSONObject = post("/voice-ticket", body, 10000)
        val ticket: String = response.optString("ticket")
        val endpoint: String = response.optString("endpoint")
        val serverPublicKey: String = response.optString("server_public_key")
        if (ticket == null || ticket.length === 0 || endpoint == null || endpoint.length === 0 || serverPublicKey == null || serverPublicKey.length === 0) {
            throw IOException("server did not return a voice ticket")
        }
        return ru.e6atb.chat.MST5.VoiceAccess(endpoint, serverPublicKey, ticket)
    }

    @kotlin.Throws(Exception::class)
    fun voiceParticipants(chat: String): List<User> {
        val arr: JSONArray = get(
            "/voice/participants?chat=" + ru.e6atb.chat.MST5.Companion.enc(if (chat == null) "" else chat.trim()),
            10000
        )
            .getJSONArray("participants")
        val out: ArrayList<User> = ArrayList<User>(arr.length())
        for (i in 0..<arr.length()) {
            out.add(ru.e6atb.chat.MST5.Companion.user(arr.getJSONObject(i)))
        }
        return out
    }

    @kotlin.Throws(Exception::class)
    fun e2eFingerprint(peer: String): String {
        return NativeE2E.fingerprint(peerE2EKey(peer).publicKey)
    }

    @kotlin.Throws(Exception::class)
    fun ownE2EPublicKey(): String {
        if (accountAddress().length === 0) {
            return ""
        }
        return fetchE2EKey(accountAddress()).publicKey
    }

    @kotlin.Throws(Exception::class)
    private fun get(path: String, readTimeoutMs: Int): JSONObject {
        return request("GET", path, null, readTimeoutMs)
    }

    @kotlin.Throws(Exception::class)
    private fun post(path: String, body: JSONObject?, readTimeoutMs: Int): JSONObject {
        return request("POST", path, body, readTimeoutMs)
    }

    @kotlin.Throws(Exception::class)
    private fun request(method: String, path: String, body: JSONObject?, readTimeoutMs: Int): JSONObject {
        val response = transport.request(
            baseUrl, transportProtocol, token(), method, path, body, readTimeoutMs,
        )
        val result = response.body() as? JSONObject ?: JSONObject()
        if (response.code() !in 200..299) {
            throw apiException(
                response.code(),
                result.optString("code", "APPLICATION_ERROR"),
                result.optString("message", result.optString("error", "MST5 ${response.code()}")),
            )
        }
        return result
    }

    private fun apiException(code: Int, errorCode: String, message: String): RuntimeException {
        val text = if (message == null || message.length === 0) "TCP " + code else message
        if (code == 401 && ru.e6atb.chat.MST5.Companion.isCloudPasswordRequiredMessage(text)) {
            return RuntimeException(text)
        }
        if (token().length > 0 && (code == 401 || ru.e6atb.chat.MST5.Companion.isInvalidTokenMessage(text))) {
            return ru.e6atb.chat.MST5.InvalidTokenException(text)
        }
        return ru.e6atb.chat.MST5.ApiException(code, errorCode, text)
    }

    fun message(o: JSONObject?): Message? {
        return Mst5MessageMapper.message(o, userId, object : Mst5MessageMapper.Security {
            override fun decrypt(from: User, to: User, payload: JSONObject): String {
                return decryptMessage(from, to, payload)
            }

            override fun rememberEncryptedMedia(from: User, to: User, media: List<FileInfo?>) {
                for (file in media) {
                    if (file != null && file.id.length > 0) encryptedMedia.put(
                        file.id,
                        ru.e6atb.chat.MST5.MediaE2EContext(from, to)
                    )
                }
            }

            override fun hasIdentity(): Boolean {
                return e2eIdentity != null
            }

            @kotlin.Throws(Exception::class)
            override fun verifyPeer(peer: String) {
                peerE2EKey(peer)
            }
        })
    }

    private fun chat(o: JSONObject): Chat {
        val last: JSONObject? = o.optJSONObject("last")
        val banned: Boolean = o.optBoolean("banned")
        return ru.e6atb.chat.MST5.Chat(
            o.optString("id"),
            if (o.optJSONObject("room") != null) ru.e6atb.chat.MST5.Companion.roomUser(o.optJSONObject("room")) else ru.e6atb.chat.MST5.Companion.user(
                o.optJSONObject("peer")
            ),
            if (last == null) null else message(last),
            o.optInt("unread_count", 0),
            banned,
            o.optBoolean("banned_by_me", banned),
            o.optBoolean("banned_me", false)
        )
    }

    private fun update(o: JSONObject): Update {
        return ru.e6atb.chat.MST5.Update(
            o.optLong("id"),
            o.optString("type"),
            message(o.optJSONObject("message")),
            ru.e6atb.chat.MST5.Companion.call(o.optJSONObject("call")),
            ru.e6atb.chat.MST5.Companion.roomUser(o.optJSONObject("room"))
        )
    }

    @kotlin.Throws(Exception::class)
    private fun activateE2E(user: User?, password: String?) {
        if (user != null) {
            userId = user.id
            login = user.login
        }
        e2eSessions.clearSessions()
        val key = accountKey()
        val address = accountAddress()
        if (context == null || key.length === 0 || address.length === 0) {
            return
        }
        var local: NativeE2E.Identity? = localE2EIdentity()
        var registeredKey: PeerE2EKey? = null
        try {
            registeredKey = fetchE2EKey(address)
        } catch (ignored: RuntimeException) {
        }
        if (registeredKey != null && registeredKey.version != 3) {
            val reset: JSONObject = JSONObject()
            reset.put("confirm", "reset_e2e")
            post("/e2e/reset", reset, 10000)
            SessionStore.clearE2EIdentity(context, key)
            local = null
            registeredKey = null
        }
        SessionStore.clearLegacyE2EIdentity(context, key)
        val registered: String = (if (registeredKey == null) "" else registeredKey.publicKey)!!
        if (registered.length > 0) {
            if (local != null && local.publicKeyB64.equals(registered)) {
                e2eIdentity = local
                if (password != null) uploadE2EBackupAsync(local, password)
                return
            }
            if (password != null) {
                try {
                    val restored: NativeE2E.Identity? = downloadE2EBackup(password)
                    if (restored != null && restored.publicKeyB64.equals(registered)) {
                        e2eIdentity = restored
                        return
                    }
                } catch (ignored: Exception) {
                }
            }
            e2eIdentity = null
            return
        }
        if (local == null) {
            local = SessionStore.createE2EIdentity(context, key)
        }
        val body: JSONObject = JSONObject()
        body.put("version", 3)
        body.put("public_key", local.publicKeyB64)
        post("/e2e/key", body, 10000)
        if (password != null) uploadE2EBackupAsync(local, password)
        e2eIdentity = local
    }

    private fun tryActivateE2E(user: User?, password: String?) {
        try {
            activateE2E(user, password)
        } catch (ignored: Exception) {
            e2eIdentity = null
        }
    }

    private fun accountKey(): String {
        return (if (userId == null || userId.length === 0) (if (login == null) "" else login) else userId)!!
    }

    private fun accountAddress(): String {
        return accountKey()
    }

    private fun localE2EIdentity(): NativeE2E.Identity? {
        if (context == null || accountKey().length === 0) return null
        var identity: NativeE2E.Identity? = SessionStore.e2eIdentity(context, accountKey())
        if (identity == null && userId != null && userId.length > 0 && login != null && login.length > 0 && !userId!!.equals(
                login
            )
        ) {
            identity = SessionStore.e2eIdentity(context, login)
            if (identity != null) {
            }
        }
        return identity
    }

    @kotlin.Throws(Exception::class)
    private fun uploadE2EBackup(identity: NativeE2E.Identity, password: String) {
        val backup: NativeE2E.Backup = NativeE2E.backup(identity, password)
        post("/e2e/backup", e2eBackupJson(backup), 10000)
    }

    @kotlin.Throws(Exception::class)
    private fun e2eBackupForCloudPassword(password: String): NativeE2E.Backup? {
        val identity: NativeE2E.Identity? = e2eIdentityForBackup()
        return if (identity == null) null else NativeE2E.backup(identity, password)
    }

    @kotlin.Throws(Exception::class)
    private fun e2eBackupJson(backup: NativeE2E.Backup): JSONObject {
        val body: JSONObject = JSONObject()
        body.put("version", backup.version)
        val salt = ByteArray(16)
        val nonce = ByteArray(24)
        val encoded = backup.encoded ?: throw IOException("E2E backup is empty")
        val ciphertext = ByteArray(encoded.size - 41)
        System.arraycopy(encoded, 1, salt, 0, 16)
        System.arraycopy(encoded, 17, nonce, 0, 24)
        System.arraycopy(encoded, 41, ciphertext, 0, ciphertext.size)
        body.put("salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
        body.put("nonce", android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP))
        body.put("ciphertext", android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP))
        return body
    }

    @kotlin.Throws(Exception::class)
    private fun e2eIdentityForBackup(): NativeE2E.Identity? {
        val key = accountKey()
        val address = accountAddress()
        if (context == null || key.length === 0 || address.length === 0) {
            return null
        }
        var local: NativeE2E.Identity? = localE2EIdentity()
        var registeredKey: PeerE2EKey? = null
        try {
            registeredKey = fetchE2EKey(address)
        } catch (ex: RuntimeException) {
            if (ex.message == null || !ex.message.orEmpty().contains("not registered")) {
                throw ex
            }
        }
        if (registeredKey != null && registeredKey.version != 3) {
            val reset: JSONObject = JSONObject()
            reset.put("confirm", "reset_e2e")
            post("/e2e/reset", reset, 10000)
            SessionStore.clearE2EIdentity(context, key)
            local = null
            registeredKey = null
        }
        val registered: String = (if (registeredKey == null) "" else registeredKey.publicKey)!!
        if (registered.length === 0) {
            if (local == null) {
                local = SessionStore.createE2EIdentity(context, key)
            }
            val body: JSONObject = JSONObject()
            body.put("version", 3)
            val created = local ?: throw IOException("cannot create E2E identity")
            body.put("public_key", created.publicKeyB64)
            post("/e2e/key", body, 10000)
            e2eIdentity = created
            return created
        }
        val active = e2eIdentity
        if (active != null && active.publicKeyB64.equals(registered)) {
            return active
        }
        if (local != null && local.publicKeyB64.equals(registered)) {
            e2eIdentity = local
            return local
        }
        throw SecurityException("E2E key mismatch; restore this account before changing cloud password")
    }

    private fun uploadE2EBackupAsync(identity: NativeE2E.Identity, password: String) {
        val thread: Thread = Thread(object : Runnable {
            override fun run() {
                try {
                    uploadE2EBackup(identity, password)
                } catch (ignored: Exception) {
                }
            }
        }, "e2e-backup")
        thread.isDaemon = true
        thread.start()
    }

    @kotlin.Throws(Exception::class)
    private fun downloadE2EBackup(password: String): NativeE2E.Identity? {
        try {
            val raw: JSONObject = get("/e2e/backup", 10000).getJSONObject("backup")
            val salt: ByteArray = android.util.Base64.decode(raw.optString("salt"), android.util.Base64.DEFAULT)
            val nonce: ByteArray = android.util.Base64.decode(raw.optString("nonce"), android.util.Base64.DEFAULT)
            val ciphertext: ByteArray =
                android.util.Base64.decode(raw.optString("ciphertext"), android.util.Base64.DEFAULT)
            val encoded = ByteArray(1 + salt.size + nonce.size + ciphertext.size)
            encoded[0] = 2
            System.arraycopy(salt, 0, encoded, 1, salt.size)
            System.arraycopy(nonce, 0, encoded, 17, nonce.size)
            System.arraycopy(ciphertext, 0, encoded, 41, ciphertext.size)
            return NativeE2E.restore(context, accountKey(), password, NativeE2E.Backup(raw.optInt("version"), encoded))
        } catch (ex: RuntimeException) {
            if (ex.message?.contains("not registered") == true) return null
            throw ex
        }
    }

    @kotlin.Throws(Exception::class)
    private fun fetchE2EKey(address: String): PeerE2EKey {
        val response: JSONObject = get("/e2e/key?user=" + ru.e6atb.chat.MST5.Companion.enc(address), 10000)
        val user: User = ru.e6atb.chat.MST5.Companion.user(response.getJSONObject("user"))
        if (user.id.length === 0) {
            throw IOException("e2e user id is unavailable")
        }
        return ru.e6atb.chat.MST5.PeerE2EKey(user, response.optInt("version", 1), response.getString("public_key"))
    }

    @kotlin.Throws(Exception::class)
    private fun fetchChatE2EKey(address: String, chatId: String): PeerE2EKey {
        val response = get(
            "/e2e/key?user=" + ru.e6atb.chat.MST5.Companion.enc(address) +
                "&chat_id=" + ru.e6atb.chat.MST5.Companion.enc(chatId), 10000
        )
        val user = ru.e6atb.chat.MST5.user(response.getJSONObject("user"))
        if (user.id.isEmpty()) throw IOException("e2e user id is unavailable")
        return ru.e6atb.chat.MST5.PeerE2EKey(user, response.optInt("version", 1), response.getString("public_key"))
    }

    @kotlin.Throws(Exception::class)
    fun chatE2EPublicKey(address: String, chatId: String): String =
        fetchChatE2EKey(address, chatId).publicKey

    @kotlin.Throws(Exception::class)
    private fun peerE2EKey(peer: String): PeerE2EKey {
        val normalized: String = if (peer == null) "" else peer.trim().toLowerCase(Locale.US)
        val result = fetchE2EKey(normalized)
        if (result.version != 3) {
            throw RuntimeException("e2e public key not registered for MST5 E2E v3")
        }
        val stablePeer: String = result.user.id.toLowerCase(Locale.US)
        val publicKey = result.publicKey
        e2eSessions.peerKeyChanged(stablePeer, publicKey)
        if (context != null) {
            if (SessionStore.pinPeerE2EKey(context, baseUrl, accountKey(), stablePeer, publicKey)) {
                e2eSessions.clearSessions()
            }
        }
        return result
    }

    @kotlin.Throws(Exception::class)
    private fun e2eSession(peer: PeerE2EKey, from: String, to: String): NativeE2E.Session {
        val cacheKey = peer.user.id.toString() + "\n" + from + "\n" + to
        val cached: NativeE2E.Session? = e2eSessions.session(cacheKey)
        if (cached != null) return cached
        val created: NativeE2E.Session = NativeE2E.session(
            e2eIdentity ?: throw SecurityException("E2E private key is unavailable on this device"), peer.publicKey, from, to
        )
        return e2eSessions.rememberSession(cacheKey, created)
    }

    private fun decryptMessage(from: User, to: User, raw: JSONObject): String {
        if (e2eIdentity == null) {
            return "[encrypted: private key unavailable]"
        }
        try {
            if (raw.optInt("version") !== 3 && raw.optInt("version") !== 4) return "Обновите приложение"
            val envelope: NativeE2E.Envelope = NativeE2E.Envelope(
                raw.optInt("version"),
                raw.optString("nonce"),
                raw.optString("ciphertext"),
                raw.optString("tag")
            )
            val sentByMe = if (userId.length > 0)
                userId.equals(from.id)
            else
                login.equals(from.login)
            val peerUser = if (sentByMe) to else from
            val peerAddress: String = if (peerUser.id.length > 0) peerUser.id else peerUser.login
            val peer = peerE2EKey(peerAddress)
            val fromContext: String = from.id
            val toContext: String = to.id
            return NativeE2E.open(
                e2eSession(peer, fromContext, toContext),
                fromContext,
                toContext,
                envelope
            )
        } catch (ex: Exception) {
            return "[encrypted: verification failed]"
        }
    }

    private class PeerE2EKey(val user: User, val version: Int, val publicKey: String)

    private class MediaE2EContext(val from: User, val to: User)

    class User(
        id: String,
        email: String,
        login: String,
        nick: String,
        @JvmField val verified: Boolean,
        @JvmField val bot: Boolean,
        @JvmField val createdAt: Long,
        messagePrivacy: String,
        callPrivacy: String,
        invitePrivacy: String,
        roomKind: String,
        ownerId: String,
        memberCount: Int,
        adminCount: Int,
        memberUsers: List<User>?,
        @JvmField val canManage: Boolean,
        @JvmField val commentsEnabled: Boolean,
        description: String,
        @JvmField val avatar: FileInfo?
    ) {
        @JvmField val id: String
        @JvmField val email: String
        @JvmField val login: String
        @JvmField val nick: String
        @JvmField val description: String
        @JvmField val messagePrivacy: String
        @JvmField val callPrivacy: String
        @JvmField val invitePrivacy: String
        @JvmField val roomKind: String
        @JvmField val ownerId: String
        @JvmField val memberCount: Int
        @JvmField val adminCount: Int
        @JvmField val memberUsers: List<User>

        constructor(id: String, email: String, login: String, nick: String, verified: Boolean, bot: Boolean) : this(
            id,
            email,
            login,
            nick,
            verified,
            bot,
            0
        )

        constructor(
            id: String,
            email: String,
            login: String,
            nick: String,
            verified: Boolean,
            bot: Boolean,
            createdAt: Long
        ) : this(id, email, login, nick, verified, bot, createdAt, "everyone", "everyone", "everyone")

        constructor(
            id: String,
            email: String,
            login: String,
            nick: String,
            verified: Boolean,
            bot: Boolean,
            createdAt: Long,
            messagePrivacy: String,
            callPrivacy: String
        ) : this(id, email, login, nick, verified, bot, createdAt, messagePrivacy, callPrivacy, "everyone")

        constructor(
            id: String,
            email: String,
            login: String,
            nick: String,
            verified: Boolean,
            bot: Boolean,
            createdAt: Long,
            messagePrivacy: String,
            callPrivacy: String,
            invitePrivacy: String
        ) : this(
            id,
            email,
            login,
            nick,
            verified,
            bot,
            createdAt,
            messagePrivacy,
            callPrivacy,
            invitePrivacy,
            "",
            "",
            0,
            0,
            ArrayList<User>(),
            false,
            false
        )

        constructor(
            id: String,
            email: String,
            login: String,
            nick: String,
            verified: Boolean,
            bot: Boolean,
            createdAt: Long,
            messagePrivacy: String,
            callPrivacy: String,
            invitePrivacy: String,
            roomKind: String,
            ownerId: String,
            memberCount: Int,
            adminCount: Int,
            memberUsers: List<User>?
        ) : this(
            id,
            email,
            login,
            nick,
            verified,
            bot,
            createdAt,
            messagePrivacy,
            callPrivacy,
            invitePrivacy,
            roomKind,
            ownerId,
            memberCount,
            adminCount,
            memberUsers,
            false,
            false
        )

        constructor(
            id: String,
            email: String,
            login: String,
            nick: String,
            verified: Boolean,
            bot: Boolean,
            createdAt: Long,
            messagePrivacy: String,
            callPrivacy: String,
            invitePrivacy: String,
            roomKind: String,
            ownerId: String,
            memberCount: Int,
            adminCount: Int,
            memberUsers: List<User>?,
            canManage: Boolean,
            commentsEnabled: Boolean
        ) : this(
            id,
            email,
            login,
            nick,
            verified,
            bot,
            createdAt,
            messagePrivacy,
            callPrivacy,
            invitePrivacy,
            roomKind,
            ownerId,
            memberCount,
            adminCount,
            memberUsers,
            canManage,
            commentsEnabled,
            ""
        )

        constructor(
            id: String,
            email: String,
            login: String,
            nick: String,
            verified: Boolean,
            bot: Boolean,
            createdAt: Long,
            messagePrivacy: String,
            callPrivacy: String,
            invitePrivacy: String,
            roomKind: String,
            ownerId: String,
            memberCount: Int,
            adminCount: Int,
            memberUsers: List<User>?,
            canManage: Boolean,
            commentsEnabled: Boolean,
            description: String
        ) : this(
            id, email, login, nick, verified, bot, createdAt, messagePrivacy, callPrivacy, invitePrivacy,
            roomKind, ownerId, memberCount, adminCount, memberUsers, canManage, commentsEnabled, description, null
        )

        init {
            this.id = if (id == null) "" else id
            this.email = if (email == null) "" else email
            this.login = if (login == null) "" else login
            this.nick = if (nick == null) "" else nick
            this.description = if (description == null) "" else description
            this.messagePrivacy = ru.e6atb.chat.MST5.Companion.normalizePrivacy(messagePrivacy)
            this.callPrivacy = ru.e6atb.chat.MST5.Companion.normalizePrivacy(callPrivacy)
            this.invitePrivacy = ru.e6atb.chat.MST5.Companion.normalizeInvitePrivacy(invitePrivacy)
            this.roomKind = if (roomKind == null) "" else roomKind
            this.ownerId = if (ownerId == null) "" else ownerId
            this.memberCount = Math.max(0, memberCount)
            this.adminCount = Math.max(0, adminCount)
            this.memberUsers = if (memberUsers == null) ArrayList<User>() else memberUsers
        }
    }

    class BotCreation(@JvmField val user: User, @JvmField val token: String)

    class Message(
        @JvmField val id: Long,
        @JvmField val chatId: String,
        @JvmField val from: User,
        @JvmField val to: User,
        @JvmField val text: String,
        @JvmField val date: Long,
        @JvmField val readAt: Long,
        media: ArrayList<FileInfo>?,
        buttons: ArrayList<Button>?,
        @JvmField val encrypted: Boolean,
        @JvmField val system: Boolean,
        data: String,
        clientMessageId: String,
        @JvmField val editedAt: Long,
        deliveryState: String,
        localFilePath: String,
        reactions: ArrayList<Reaction>?,
        @JvmField val paidReaction: PaidReaction?,
        @JvmField val reactionVersion: Long,
        @JvmField val commentPostId: Long,
        commentsCount: Int,
        replyToMessageId: Long,
        deliveryProgress: Int,
        deliveryPhase: String
    ) {
        @JvmField val file: FileInfo?
        @JvmField val media: ArrayList<FileInfo>
        @JvmField val buttons: ArrayList<Button>
        @JvmField val data: String
        @JvmField val clientMessageId: String
        @JvmField val deliveryState: String
        @JvmField val localFilePath: String
        @JvmField val reactions: ArrayList<Reaction>
        @JvmField val commentsCount: Int
        @JvmField val replyToMessageId: Long
        @JvmField val deliveryProgress: Int
        @JvmField val deliveryPhase: String

        constructor(
            id: Long,
            chatId: String,
            from: User,
            to: User,
            text: String,
            date: Long,
            readAt: Long,
            file: FileInfo?,
            buttons: ArrayList<Button>?,
            encrypted: Boolean,
            system: Boolean,
            data: String
        ) : this(id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data, "", 0, "sent", "")

        constructor(
            id: Long,
            chatId: String,
            from: User,
            to: User,
            text: String,
            date: Long,
            readAt: Long,
            file: FileInfo?,
            buttons: ArrayList<Button>?,
            encrypted: Boolean,
            system: Boolean,
            data: String,
            clientMessageId: String,
            editedAt: Long,
            deliveryState: String,
            localFilePath: String
        ) : this(
            id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system,
            data, clientMessageId, editedAt, deliveryState, localFilePath,
            ArrayList<Reaction>(), null, 0
        )

        constructor(
            id: Long,
            chatId: String,
            from: User,
            to: User,
            text: String,
            date: Long,
            readAt: Long,
            file: FileInfo?,
            buttons: ArrayList<Button>?,
            encrypted: Boolean,
            system: Boolean,
            data: String,
            clientMessageId: String,
            editedAt: Long,
            deliveryState: String,
            localFilePath: String,
            reactions: ArrayList<Reaction>?,
            paidReaction: PaidReaction?,
            reactionVersion: Long
        ) : this(
            id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data,
            clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
            reactionVersion, 0, 0
        )

        constructor(
            id: Long,
            chatId: String,
            from: User,
            to: User,
            text: String,
            date: Long,
            readAt: Long,
            file: FileInfo?,
            buttons: ArrayList<Button>?,
            encrypted: Boolean,
            system: Boolean,
            data: String,
            clientMessageId: String,
            editedAt: Long,
            deliveryState: String,
            localFilePath: String,
            reactions: ArrayList<Reaction>?,
            paidReaction: PaidReaction?,
            reactionVersion: Long,
            commentPostId: Long,
            commentsCount: Int
        ) : this(
            id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data,
            clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
            reactionVersion, commentPostId, commentsCount, 0
        )

        constructor(
            id: Long,
            chatId: String,
            from: User,
            to: User,
            text: String,
            date: Long,
            readAt: Long,
            file: FileInfo?,
            buttons: ArrayList<Button>?,
            encrypted: Boolean,
            system: Boolean,
            data: String,
            clientMessageId: String,
            editedAt: Long,
            deliveryState: String,
            localFilePath: String,
            reactions: ArrayList<Reaction>?,
            paidReaction: PaidReaction?,
            reactionVersion: Long,
            commentPostId: Long,
            commentsCount: Int,
            replyToMessageId: Long
        ) : this(
            id, chatId, from, to, text, date, readAt, file, buttons, encrypted, system, data,
            clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
            reactionVersion, commentPostId, commentsCount, replyToMessageId, 0, ""
        )

        constructor(
            id: Long,
            chatId: String,
            from: User,
            to: User,
            text: String,
            date: Long,
            readAt: Long,
            file: FileInfo?,
            buttons: ArrayList<Button>?,
            encrypted: Boolean,
            system: Boolean,
            data: String,
            clientMessageId: String,
            editedAt: Long,
            deliveryState: String,
            localFilePath: String,
            reactions: ArrayList<Reaction>?,
            paidReaction: PaidReaction?,
            reactionVersion: Long,
            commentPostId: Long,
            commentsCount: Int,
            replyToMessageId: Long,
            deliveryProgress: Int,
            deliveryPhase: String
        ) : this(
            id,
            chatId,
            from,
            to,
            text,
            date,
            readAt,
            ru.e6atb.chat.MST5.Message.Companion.singleMedia(file),
            buttons,
            encrypted,
            system,
            data,
            clientMessageId,
            editedAt,
            deliveryState,
            localFilePath,
            reactions,
            paidReaction,
            reactionVersion,
            commentPostId,
            commentsCount,
            replyToMessageId,
            deliveryProgress,
            deliveryPhase
        )

        constructor(
            id: Long,
            chatId: String,
            from: User,
            to: User,
            text: String,
            date: Long,
            readAt: Long,
            media: ArrayList<FileInfo>?,
            buttons: ArrayList<Button>?,
            encrypted: Boolean,
            system: Boolean,
            data: String,
            clientMessageId: String,
            editedAt: Long,
            deliveryState: String,
            localFilePath: String,
            reactions: ArrayList<Reaction>?,
            paidReaction: PaidReaction?,
            reactionVersion: Long,
            commentPostId: Long,
            commentsCount: Int,
            replyToMessageId: Long
        ) : this(
            id, chatId, from, to, text, date, readAt, media, buttons, encrypted, system, data,
            clientMessageId, editedAt, deliveryState, localFilePath, reactions, paidReaction,
            reactionVersion, commentPostId, commentsCount, replyToMessageId, 0, ""
        )

        init {
            this.media = if (media == null) ArrayList<FileInfo>() else media
            this.file = this.media.firstOrNull()
            this.buttons = if (buttons == null) ArrayList<Button>() else buttons
            this.data = if (data == null) "" else data
            this.clientMessageId = if (clientMessageId == null) "" else clientMessageId
            this.deliveryState = if (deliveryState == null) "sent" else deliveryState
            this.localFilePath = if (localFilePath == null) "" else localFilePath
            this.reactions = if (reactions == null) ArrayList<Reaction>() else reactions
            this.commentsCount = Math.max(0, commentsCount)
            this.replyToMessageId = Math.max(0, replyToMessageId)
            this.deliveryProgress = Math.max(0, Math.min(100, deliveryProgress))
            this.deliveryPhase = if (deliveryPhase == null) "" else deliveryPhase
        }

        fun asOutgoing(): Message {
            return ru.e6atb.chat.MST5.Message(
                id, chatId, from, to, text, date, readAt, media, buttons, encrypted, system,
                data, clientMessageId, editedAt, "sent-own", localFilePath, reactions, paidReaction,
                reactionVersion, commentPostId, commentsCount, replyToMessageId
            )
        }

        companion object {
            private fun singleMedia(file: FileInfo?): ArrayList<FileInfo> {
                val out: ArrayList<FileInfo> = ArrayList<FileInfo>()
                if (file != null) out.add(file)
                return out
            }
        }
    }

    class Reaction(emoji: String, @JvmField val count: Long, @JvmField val mine: Boolean) {
        @JvmField val emoji: String

        init {
            this.emoji = if (emoji == null) "" else emoji
        }
    }

    class PaidReaction(@JvmField val amount: Long, @JvmField val mineAmount: Long)

    class MediaQuote(@JvmField val sizeBytes: Long, @JvmField val dsrRequired: Long, @JvmField val free: Boolean)

    class ApiException(@JvmField val code: Int, errorCode: String, message: String) : RuntimeException(message) {
        @JvmField val errorCode: String

        init {
            this.errorCode = if (errorCode == null) "" else errorCode
        }
    }

    class Button(
        @JvmField val text: String,
        @JvmField val url: String,
        @JvmField val callback: String,
        @JvmField val payDsr: Long,
        row: Int,
        @JvmField val swipeConfirm: Boolean
    ) {
        @JvmField val row: Int

        init {
            this.row = Math.max(0, Math.min(11, row))
        }
    }

    class SessionInfo(
        id: String,
        @JvmField val createdAt: Long,
        @JvmField val lastSeen: Long,
        label: String,
        deviceModel: String,
        @JvmField val current: Boolean
    ) {
        @JvmField val id: String
        @JvmField val label: String
        @JvmField val deviceModel: String

        init {
            this.id = if (id == null) "" else id
            this.label = if (label == null) "" else label
            this.deviceModel = if (deviceModel == null) "" else deviceModel
        }
    }

    class BotCommand(command: String, description: String) {
        @JvmField val command: String
        @JvmField val description: String

        init {
            this.command = if (command == null) "" else command
            this.description = if (description == null) "" else description
        }
    }

    class StickerPack(
        id: String,
        title: String,
        priceDsr: Long,
        @JvmField val owned: Boolean,
        stickers: List<FileInfo>?
    ) {
        @JvmField val id: String
        @JvmField val title: String
        @JvmField val priceDsr: Long
        @JvmField val stickers: List<FileInfo>

        init {
            this.id = if (id == null) "" else id
            this.title = if (title == null) "" else title
            this.priceDsr = Math.max(0, priceDsr)
            this.stickers = if (stickers == null) ArrayList<FileInfo>() else stickers
        }
    }

    class FileInfo(@JvmField val id: String, @JvmField val name: String, @JvmField val mime: String, @JvmField val size: Long)

    class WalletInfo(
        @JvmField val userId: Long,
        @JvmField val currency: String,
        @JvmField val code: String,
        @JvmField val balance: Long,
        @JvmField val receiveCode: String,
        @JvmField val instruction: String
    )

    class WalletTransaction(
        @JvmField val id: Long,
        @JvmField val fromUserId: Long,
        fromLogin: String,
        fromNick: String,
        @JvmField val toUserId: Long,
        toLogin: String,
        toNick: String,
        @JvmField val amount: Long,
        comment: String,
        @JvmField val date: Long
    ) {
        @JvmField val fromLogin: String
        @JvmField val fromNick: String
        @JvmField val toLogin: String
        @JvmField val toNick: String
        @JvmField val comment: String

        init {
            this.fromLogin = if (fromLogin == null) "" else fromLogin
            this.fromNick = if (fromNick == null) "" else fromNick
            this.toLogin = if (toLogin == null) "" else toLogin
            this.toNick = if (toNick == null) "" else toNick
            this.comment = if (comment == null) "" else comment
        }
    }

    class NodeStatus(type: String, name: String, status: String, @JvmField val available: Int, @JvmField val total: Int) {
        @JvmField val type: String
        @JvmField val name: String
        @JvmField val status: String

        init {
            this.type = if (type == null) "" else type
            this.name = if (name == null) "" else name
            this.status = if (status == null) "" else status
        }
    }

    class Chat(
        @JvmField val id: String,
        @JvmField val peer: User,
        @JvmField val last: Message?,
        unreadCount: Int,
        @JvmField val banned: Boolean,
        @JvmField val bannedByMe: Boolean,
        @JvmField val bannedMe: Boolean
    ) {
        @JvmField val unreadCount: Int

        constructor(id: String, peer: User, last: Message?, banned: Boolean) : this(
            id,
            peer,
            last,
            0,
            banned,
            banned,
            false
        )

        constructor(
            id: String,
            peer: User,
            last: Message?,
            banned: Boolean,
            bannedByMe: Boolean,
            bannedMe: Boolean
        ) : this(id, peer, last, 0, banned, bannedByMe, bannedMe)

        init {
            this.unreadCount = Math.max(0, unreadCount)
        }
    }

    class HistoryPage(@JvmField val peer: User, @JvmField val messages: List<Message>)

    class CommentPage(@JvmField val peer: User, @JvmField val post: Message?, @JvmField val messages: List<Message>)

    class Update(@JvmField val id: Long, @JvmField val type: String, @JvmField val message: Message?, @JvmField val call: Call?, @JvmField val room: User)

    class Call(@JvmField val from: User, @JvmField val to: User, @JvmField val date: Long)
    companion object {
        private fun webpName(name: String): String {
            var value = if (name == null) "photo" else name.trim()
            val dot: Int = value.lastIndexOf('.')
            if (dot > 0) value = value.substring(0, dot)
            return (if (value.length === 0) "photo" else value) + ".webp"
        }

        @kotlin.Throws(Exception::class)
        private fun mediaRequest(items: List<MessageMedia>?, encryptMedia: Boolean): JSONArray {
            val media: JSONArray = JSONArray()
            if (items == null) return media
            for (item in items) {
                val raw: JSONObject = JSONObject()
                if (item!!.fileId.length > 0) {
                    raw.put("file_id", item.fileId)
                } else {
                    raw.put("client_id", item.clientId)
                    raw.put("name", item.name)
                    raw.put("mime", item.mime)
                    raw.put("size", if (encryptMedia) NativeE2E.encryptedMediaSize(item.size) else item.size)
                }
                media.put(raw)
            }
            return media
        }

        @kotlin.Throws(IOException::class)
        private fun readExactly(input: InputStream, expected: Long): ByteArray {
            if (expected < 0 || expected > Integer.MAX_VALUE) throw IOException("invalid media size")
            val out = ByteArray(expected.toInt())
            var offset = 0
            while (offset < out.size) {
                val read: Int = input.read(out, offset, out.size - offset)
                if (read < 0) throw IOException("media source was truncated")
                offset += read
            }
            if (input.read() >= 0) throw IOException("media source size changed")
            return out
        }

        @JvmStatic fun isInvalidTokenError(error: Throwable?): Boolean {
            return error is InvalidTokenException
                    || (error != null && ru.e6atb.chat.MST5.Companion.isExplicitInvalidTokenMessage(error.message))
        }

        fun isTransientError(error: Throwable): Boolean {
            if (error is ApiException) {
                val code = (error as ApiException).code
                return code == 408 || code == 429 || code >= 500
            }
            var value: Throwable? = error
            while (value != null) {
                if (value is IOException) return true
                value = value.cause
            }
            return false
        }

        @JvmStatic fun isCloudPasswordRequiredError(error: Throwable?): Boolean {
            return error != null && ru.e6atb.chat.MST5.Companion.isCloudPasswordRequiredMessage(error.message)
        }

        private fun isCloudPasswordRequiredMessage(message: String?): Boolean {
            if (message == null) return false
            val text: String = message.toLowerCase(Locale.US)
            return text.contains("cloud password required")
                    || text.contains("cloud_password_required")
        }

        private fun isInvalidTokenMessage(message: String?): Boolean {
            if (message == null) return false
            val text: String = message.toLowerCase(Locale.US)
            return text.contains("unauthorized")
                    || ru.e6atb.chat.MST5.Companion.isExplicitInvalidTokenMessage(message)
        }

        private fun isExplicitInvalidTokenMessage(message: String?): Boolean {
            if (message == null) return false
            val text: String = message.toLowerCase(Locale.US)
            return text.contains("invalid token")
                    || text.contains("bad token")
                    || (text.contains("token") && text.contains("invalid"))
                    || (text.contains("токен") && (text.contains("невер") || text.contains("не вер")))
        }

        @kotlin.Throws(Exception::class)
        private fun enc(s: String): String {
            return URLEncoder.encode(s, "UTF-8")
        }

        private fun trimSlash(s: String): String {
            var s = s
            if (s == null || s.length === 0) {
                return "127.0.0.1:8080"
            }
            s = s.trim()
            while (s.endsWith("/")) {
                s = s.substring(0, s.length - 1)
            }
            val legacyPrefix = "tcp" + "://"
            if (s.toLowerCase(Locale.US).startsWith(legacyPrefix)) {
                return s.substring(legacyPrefix.length)
            }
            return s
        }

        private fun normalizePrivacy(value: String): String {
            if ("contacts".equals(value) || "chats".equals(value) || "nobody".equals(value)) return value
            return "everyone"
        }

        private fun normalizeInvitePrivacy(value: String): String {
            if ("contacts".equals(value) || "nobody".equals(value)) return value
            return "everyone"
        }

        private fun user(o: JSONObject?): User {
            return Mst5Json.user(o)
        }

        private fun roomUser(o: JSONObject?): User {
            return Mst5Json.roomUser(o)
        }

        private fun call(o: JSONObject?): Call? {
            return Mst5Json.call(o)
        }

        private fun stickerPack(o: JSONObject?): StickerPack {
            return Mst5Json.stickerPack(o)
        }

        private fun file(o: JSONObject?): FileInfo? {
            return Mst5Json.file(o)
        }

        private fun walletInfo(o: JSONObject?): WalletInfo {
            return Mst5Json.walletInfo(o)
        }

        private fun walletTransaction(o: JSONObject?): WalletTransaction {
            return Mst5Json.walletTransaction(o)
        }

    }
}
