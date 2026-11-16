package com.github.fakegps

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/**
 * Resolves resources in the language the user picked in the app.
 *
 * <p>Activity contexts already use that language, but Application, Service and
 * overlay contexts keep the system language. AppCompat's
 * [AppCompatDelegate.getApplicationLocales] is not reliable here either: on
 * API 33+ it needs a live Activity delegate and returns an empty list once the
 * last screen has finished, which is exactly when the floating panel and the
 * foreground notification are shown. Reading the framework's per-app locales
 * directly covers that case.</p>
 */
object AppLocale {

    @JvmStatic
    fun context(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        if (!applyAppLocale(context, configuration)) {
            return context
        }
        return context.createConfigurationContext(configuration)
    }

    private fun applyAppLocale(context: Context, configuration: Configuration): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                configuration.setLocales(locales)
                return true
            }
        }
        val compatLocales = AppCompatDelegate.getApplicationLocales()
        if (compatLocales.isEmpty) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val locales = compatLocales.unwrap() as? android.os.LocaleList
            if (locales != null) {
                configuration.setLocales(locales)
                return true
            }
            return false
        }
        @Suppress("DEPRECATION")
        configuration.locale = compatLocales.get(0) ?: configuration.locale
        return true
    }
}
