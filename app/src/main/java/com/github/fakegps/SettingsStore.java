package com.github.fakegps;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Simple persisted app settings.
 */
public final class SettingsStore {

    private static final String PREFS = "settings";
    private static final String KEY_THEME = "theme";
    private static final String KEY_KEEP_RUNNING = "keep_running";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private SettingsStore() {
    }

    public static String getTheme(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_SYSTEM);
    }

    public static void setTheme(Context context, String theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, theme).apply();
    }

    public static int nightModeFor(String theme) {
        if (THEME_LIGHT.equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (THEME_DARK.equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static boolean keepRunningWithScreenOff(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_KEEP_RUNNING, true);
    }

    public static void setKeepRunningWithScreenOff(Context context, boolean keepRunning) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_KEEP_RUNNING, keepRunning).apply();
    }
}
