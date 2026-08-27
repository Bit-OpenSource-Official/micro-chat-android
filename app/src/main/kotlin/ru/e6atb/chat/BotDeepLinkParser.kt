package ru.e6atb.chat

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

object BotDeepLinkParser {
    private val loginPattern = Regex("[a-z0-9](?:[a-z0-9_]{3,30}[a-z0-9])")
    private val payloadPattern = Regex("[A-Za-z0-9_-]{1,128}")

    class Link(@JvmField val login: String, @JvmField val payload: String) {
        fun startCommand() = if (payload.isEmpty()) "/start" else "/start $payload"
    }

    @JvmStatic fun parse(raw: String?): Link? {
        return try {
        if (raw.isNullOrBlank()) return null
        val uri = URI(raw.trim())
        val scheme = uri.scheme.orEmpty().lowercase(Locale.US)
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty()
        val login = when {
            scheme == "https" && host == "ms.ove.rs" && path.startsWith("/bot/") -> path.removePrefix("/bot/")
            scheme == "ovechat" && host == "bot" && path.startsWith('/') -> path.removePrefix("/")
            else -> return null
        }
        if ('/' in login || !loginPattern.matches(login) || !(login.startsWith("bot") || login.endsWith("bot"))) return null
        val payload = query(uri.rawQuery, "start")
        if (payload.isNotEmpty() && !payloadPattern.matches(payload)) null else Link(login, payload)
        } catch (_: Exception) { null }
    }

    private fun query(raw: String?, name: String): String {
        if (raw.isNullOrEmpty()) return ""
        for (pair in raw.split('&')) {
            val split = pair.indexOf('=')
            val key = if (split < 0) pair else pair.substring(0, split)
            if (name == URLDecoder.decode(key, "UTF-8")) return if (split < 0) "" else URLDecoder.decode(pair.substring(split + 1), "UTF-8")
        }
        return ""
    }
}
