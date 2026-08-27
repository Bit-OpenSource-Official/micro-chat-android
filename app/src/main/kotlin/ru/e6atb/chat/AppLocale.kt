package ru.e6atb.chat

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLocale {
    @JvmField val SYSTEM = ""
    @JvmField val ENGLISH = "en"
    @JvmField val RUSSIAN = "ru"

    @JvmStatic fun apply(context: Context) = apply(context, SessionStore.language(context))
    @JvmStatic fun apply(context: Context?, language: String?) {
        context ?: return
        val resources = context.resources ?: return
        val config = Configuration(resources.configuration)
        config.locale = when (language) {
            ENGLISH -> Locale.ENGLISH
            RUSSIAN -> Locale(RUSSIAN)
            else -> Locale.getDefault()
        }
        @Suppress("DEPRECATION") resources.updateConfiguration(config, resources.displayMetrics)
    }
}
