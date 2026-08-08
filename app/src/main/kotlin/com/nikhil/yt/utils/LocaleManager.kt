package com.nikhil.yt.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleManager {
    private const val PREF_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun setLocale(context: Context, languageCode: String): Context {
        val locale = if (languageCode.isNotEmpty()) {
            Locale(languageCode)
        } else {
            Locale.ENGLISH
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    fun getCurrentLocale(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun saveLocale(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun applyLocale(context: Context): Context {
        val languageCode = getCurrentLocale(context)
        return setLocale(context, languageCode)
    }
}
