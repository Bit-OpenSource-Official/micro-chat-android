package ru.e6atb.chat

import android.os.Build

object DisplayText {
    @JvmStatic
    fun safe(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val stripSupplementary = Build.VERSION.SDK_INT in 1..20
        var changed = false
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                Character.isHighSurrogate(character) && index + 1 < value.length && Character.isLowSurrogate(value[index + 1]) -> {
                    if (stripSupplementary) { changed = true; break }
                    index++
                }
                Character.isHighSurrogate(character) || Character.isLowSurrogate(character) -> { changed = true; break }
            }
            index++
        }
        if (!changed) return value
        return buildString(value.length) {
            var cursor = 0
            while (cursor < value.length) {
                val character = value[cursor]
                when {
                    Character.isHighSurrogate(character) && cursor + 1 < value.length && Character.isLowSurrogate(value[cursor + 1]) -> {
                        if (stripSupplementary) append('\uFFFD') else append(character).append(value[cursor + 1])
                        cursor++
                    }
                    Character.isHighSurrogate(character) || Character.isLowSurrogate(character) -> append('\uFFFD')
                    else -> append(character)
                }
                cursor++
            }
        }
    }
}
