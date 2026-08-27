package ru.e6atb.chat

import java.util.Locale

object OAuthCodeParser {
    private val code = Regex("([0-9a-f]{4}-[0-9a-f]{4})", RegexOption.IGNORE_CASE)
    @JvmStatic fun parse(value: String?): String = code.find(value.orEmpty().trim())?.groupValues?.get(1)?.uppercase(Locale.US).orEmpty()
}
