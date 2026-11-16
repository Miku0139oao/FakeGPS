package com.github.fakegps

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * Simple persisted app settings.
 */
object SettingsStore {

    private const val PREFS = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_KEEP_RUNNING = "keep_running"
    private const val KEY_SHOW_JOYSTICK = "show_joystick"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    @JvmStatic
    fun getTheme(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    @JvmStatic
    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_THEME, theme)
        }
    }

    @JvmStatic
    fun nightModeFor(theme: String?): Int = when (theme) {
        THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    @JvmStatic
    fun keepRunningWithScreenOff(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_RUNNING, true)

    @JvmStatic
    fun setKeepRunningWithScreenOff(context: Context, keepRunning: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_KEEP_RUNNING, keepRunning)
        }
    }

    @JvmStatic
    fun showJoystick(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_JOYSTICK, true)

    @JvmStatic
    fun setShowJoystick(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SHOW_JOYSTICK, show)
        }
    }
}
