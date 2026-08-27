@file:Suppress("EXPOSED_FUNCTION_RETURN_TYPE")

package ru.e6atb.chat

import org.json.JSONArray
import org.json.JSONObject

/** Stateless JSON-to-model mapping used by the MST5 facade. */
object Mst5Json {
    @JvmStatic fun user(source: JSONObject?): MST5.User {
        if (source == null) return MST5.User("", "", "", "", false, false, 0)
        return MST5.User(
            source.optString("id"), source.optString("email"),
            source.optString("username", source.optString("login")),
            source.optString("name", source.optString("nick", source.optString("title"))),
            source.optBoolean("verified"), source.optBoolean("bot"), source.optLong("created_at"),
            source.optString("message_privacy", "everyone"), source.optString("call_privacy", "everyone"),
            source.optString("invite_privacy", "everyone"),
            source.optString("kind", source.optString("room_kind")), source.optString("owner_id"),
            source.optInt("members"), source.optInt("admins"), null,
            source.optBoolean("can_manage"), source.optBoolean("comments_enabled"),
            source.optString("description"), file(source.optJSONObject("avatar")),
        )
    }

    @JvmStatic fun roomUser(source: JSONObject?): MST5.User {
        if (source == null) return MST5.User("", "", "", "", false, false, 0)
        val members = ArrayList<MST5.User>()
        source.optJSONArray("member_users")?.let { raw ->
            for (index in 0 until raw.length()) raw.optJSONObject(index)?.let { members += user(it) }
        }
        return MST5.User(
            source.optString("id"), "", source.optString("username"), source.optString("title"),
            false, false, source.optLong("created_at"), "everyone", "everyone", "everyone",
            source.optString("kind"), source.optString("owner_id"), source.optInt("members"),
            source.optInt("admins"), members, source.optBoolean("can_manage"),
            source.optBoolean("comments_enabled"), source.optString("description"),
        )
    }

    @JvmStatic fun reactions(raw: JSONArray?): ArrayList<MST5.Reaction> = ArrayList<MST5.Reaction>().also { out ->
        raw ?: return@also
        for (index in 0 until raw.length()) {
            val item = raw.optJSONObject(index) ?: continue
            val emoji = item.optString("emoji")
            val count = item.optLong("count")
            if (emoji.isNotEmpty() && count > 0) out += MST5.Reaction(emoji, count, item.optBoolean("mine"))
        }
    }

    @JvmStatic fun media(raw: JSONArray?): ArrayList<MST5.FileInfo> = ArrayList<MST5.FileInfo>().also { out ->
        raw ?: return@also
        for (index in 0 until raw.length()) file(raw.optJSONObject(index))?.let(out::add)
    }

    @JvmStatic fun paidReaction(raw: JSONObject?): MST5.PaidReaction? =
        raw?.takeIf { it.optLong("amount") > 0 }?.let { MST5.PaidReaction(it.optLong("amount"), it.optLong("mine_amount")) }

    @JvmStatic fun jsonObjectString(value: JSONObject?): String = value?.toString().orEmpty()

    @JvmStatic fun call(source: JSONObject?): MST5.Call? = source?.let {
        MST5.Call(user(it.optJSONObject("from")), user(it.optJSONObject("to")), it.optLong("date"))
    }

    @JvmStatic fun stickerPack(source: JSONObject?): MST5.StickerPack {
        if (source == null) return MST5.StickerPack("", "", 0, false, ArrayList())
        return MST5.StickerPack(
            source.optString("id"), source.optString("title"), source.optLong("price_dsr"),
            source.optBoolean("owned"), media(source.optJSONArray("stickers")),
        )
    }

    @JvmStatic fun file(source: JSONObject?): MST5.FileInfo? = source?.let {
        MST5.FileInfo(it.optString("id"), it.optString("name"), it.optString("mime"), it.optLong("size"))
    }

    @JvmStatic fun buttons(raw: JSONArray?): ArrayList<MST5.Button> = ArrayList<MST5.Button>().also { out ->
        raw ?: return@also
        for (index in 0 until raw.length()) {
            val row = raw.optJSONArray(index)
            if (row != null) for (buttonIndex in 0 until row.length()) addButton(out, row.optJSONObject(buttonIndex), index)
            else raw.optJSONObject(index)?.let { item -> addButton(out, item, if (item.has("row")) item.optInt("row", index) else index) }
        }
    }

    @JvmStatic fun walletInfo(source: JSONObject?): MST5.WalletInfo {
        if (source == null) return MST5.WalletInfo(0, "dastars", "DSR", 0, "", "")
        return MST5.WalletInfo(parseUserId(source.opt("user_id")), source.optString("currency", "dastars"),
            source.optString("code", "DSR"), source.optLong("balance"), source.optString("receive_code"), source.optString("instruction"))
    }

    @JvmStatic fun walletTransaction(source: JSONObject?): MST5.WalletTransaction {
        if (source == null) return MST5.WalletTransaction(0, 0, "", "", 0, "", "", 0, "", 0)
        return MST5.WalletTransaction(source.optLong("id"), source.optLong("from_user_id"), source.optString("from_username"), "",
            source.optLong("to_user_id"), source.optString("to_username"), "", source.optLong("amount"), source.optString("comment"), source.optLong("date"))
    }

    @JvmStatic fun parseUserId(value: Any?): Long {
        if (value is Number) return value.toLong()
        val raw = value?.toString()?.trim().orEmpty()
        return raw.toLongOrNull() ?: raw.takeIf { it.length == 16 }?.toULongOrNull(16)?.toLong() ?: 0
    }

    private fun addButton(out: MutableList<MST5.Button>, item: JSONObject?, row: Int) {
        item ?: return
        out += MST5.Button(item.optString("text"), item.optString("url"), item.optString("callback"),
            item.optLong("pay_dsr"), row.coerceAtLeast(0), item.optString("confirm") == "swipe" || item.optBoolean("swipe_confirm"))
    }
}
