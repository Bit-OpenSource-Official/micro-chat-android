package ru.e6atb.chat

object ForwardMessageFormatter {
    const val MAX_MESSAGE_BYTES = 4096

    @JvmStatic fun compose(header: String?, body: String?): String {
        val safeHeader = header.orEmpty().trim()
        val safeBody = body.orEmpty().trim()
        return when {
            safeHeader.isEmpty() -> safeBody
            safeBody.isEmpty() -> safeHeader
            else -> "$safeHeader\n\n$safeBody"
        }
    }

    @JvmStatic fun fitsServerLimit(value: String?) = utf8Length(value) <= MAX_MESSAGE_BYTES

    @JvmStatic fun utf8Length(value: String?): Int {
        if (value.isNullOrEmpty()) return 0
        var bytes = 0
        var index = 0
        while (index < value.length) {
            val character = value[index]
            bytes += when {
                character.code <= 0x7f -> 1
                character.code <= 0x7ff -> 2
                Character.isHighSurrogate(character) && index + 1 < value.length && Character.isLowSurrogate(value[index + 1]) -> { index++; 4 }
                else -> 3
            }
            index++
        }
        return bytes
    }
}
