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
    private const val KEY_BACKGROUND_HINT_SHOWN = "background_hint_shown"
    private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_LAST_UPDATE_ATTEMPT = "last_update_attempt"
    private const val KEY_PROMPTED_VERSION = "prompted_version"
    private const val KEY_PROMPTED_AT = "prompted_at"

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

    /** Whether the one-time "allow background execution" hint was shown. */
    @JvmStatic
    fun backgroundHintShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND_HINT_SHOWN, false)

    @JvmStatic
    fun setBackgroundHintShown(context: Context, shown: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_BACKGROUND_HINT_SHOWN, shown)
        }
    }

    /** Whether the app may ask GitHub for a newer release about once a day. */
    @JvmStatic
    fun autoUpdateCheck(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE_CHECK, true)

    @JvmStatic
    fun setAutoUpdateCheck(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_AUTO_UPDATE_CHECK, enabled)
        }
    }

    /** Epoch millis of the last successful check, or 0 when never checked. */
    @JvmStatic
    fun lastUpdateCheck(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_CHECK, 0L)

    @JvmStatic
    fun setLastUpdateCheck(context: Context, timeMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_UPDATE_CHECK, timeMs)
        }
    }

    /**
     * Epoch millis of the last attempt, successful or not. Used to keep a
     * failed check (offline, captive portal) from retrying on every resume.
     */
    @JvmStatic
    fun lastUpdateAttempt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_ATTEMPT, 0L)

    @JvmStatic
    fun setLastUpdateAttempt(context: Context, timeMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_UPDATE_ATTEMPT, timeMs)
        }
    }

    /** The version already shown in the update prompt, if any. */
    @JvmStatic
    fun promptedVersion(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROMPTED_VERSION, "") ?: ""

    @JvmStatic
    fun setPromptedVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_PROMPTED_VERSION, version)
        }
    }

    @JvmStatic
    fun promptedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PROMPTED_AT, 0L)

    @JvmStatic
    fun setPromptedAt(context: Context, timeMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_PROMPTED_AT, timeMs)
        }
    }
}
