@file:Suppress("EXPOSED_FUNCTION_RETURN_TYPE", "EXPOSED_PARAMETER_TYPE")

package ru.e6atb.chat

import org.json.JSONObject

/** Maps a wire message while keeping private-key ownership in the MST5 facade. */
object Mst5MessageMapper {
    interface Security {
        @Throws(Exception::class)
        fun decrypt(from: MST5.User, to: MST5.User, chatId: String, payload: JSONObject): String
        fun rememberEncryptedMedia(from: MST5.User, to: MST5.User, media: List<MST5.FileInfo?>)
        fun hasIdentity(): Boolean
        @Throws(Exception::class)
        fun verifyPeer(peer: String)
    }

    @JvmStatic
    fun message(source: JSONObject?, userId: String, security: Security): MST5.Message? {
        source ?: return null
        val from = Mst5Json.user(source.optJSONObject("from"))
        val to = Mst5Json.user(source.optJSONObject("to"))
        var text = source.optString("text")
        val chatId = source.optString("chat_id")
        val isRoomMessage = chatId.startsWith("chat:")
        val e2e = source.optJSONObject("e2e")
        // Private messages carry a single v3/v4 envelope. Room messages use the
        // v5 envelope with a recipient-specific v4 envelope. Keep the wire
        // format opaque to the UI and let MST5 select/decrypt the local entry.
        val encrypted = e2e != null && (!isRoomMessage || (e2e.optInt("version") == 5 && e2e.optJSONObject("recipients") != null))
        val system = source.optBoolean("system")
        val media = Mst5Json.media(source.optJSONArray("media"))
        if (encrypted) {
            text = security.decrypt(from, to, chatId, e2e!!)
            security.rememberEncryptedMedia(from, to, media)
        } else if (!system && !isRoomMessage && media.isEmpty() && security.hasIdentity() && from.id.isNotEmpty() && to.id.isNotEmpty()) {
            try {
                security.verifyPeer(if (from.id == userId) to.id else from.id)
                text = "[unencrypted message blocked]"
            } catch (_: Exception) {
                // A missing legacy peer key must not erase an otherwise readable message.
            }
        }
        return MST5.Message(
            source.optLong("id"), chatId, from, to, text, source.optLong("date"), source.optLong("read_at"),
            media, Mst5Json.buttons(source.optJSONArray("buttons")), encrypted, system,
            Mst5Json.jsonObjectString(source.optJSONObject("data")), source.optString("client_message_id"),
            source.optLong("edited_at"), "sent", "", Mst5Json.reactions(source.optJSONArray("reactions")),
            Mst5Json.paidReaction(source.optJSONObject("paid_reaction")), source.optLong("reaction_version"),
            source.optLong("comment_post_id"), source.optInt("comments_count"), source.optLong("reply_to_message_id"),
        )
    }
}
