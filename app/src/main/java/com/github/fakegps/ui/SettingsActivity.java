package com.github.fakegps.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.os.LocaleListCompat;

import com.github.fakegps.SettingsStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tencent.fakegps.BuildConfig;
import com.tencent.fakegps.R;

import java.util.Locale;

/**
 * Material settings screen: language, theme, wake lock, and links.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String[] LANGUAGE_TAGS = {
            "", "en", "zh-TW", "zh-CN", "ja", "ko", "es", "fr", "de", "pt-BR"
    };

    private TextView mLanguageSummary;
    private TextView mThemeSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        UiUtils.enableEdgeToEdge(this, R.id.root);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        mLanguageSummary = findViewById(R.id.tv_language_summary);
        mThemeSummary = findViewById(R.id.tv_theme_summary);
        SwitchCompat keepRunning = findViewById(R.id.switch_keep_running);

        findViewById(R.id.row_language).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLanguageDialog();
            }
        });
        findViewById(R.id.row_theme).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showThemeDialog();
            }
        });
        findViewById(R.id.row_about).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAboutDialog();
            }
        });
        findViewById(R.id.row_source).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openUrl("https://github.com/Miku0139oao/FakeGPS");
            }
        });
        findViewById(R.id.row_help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openUrl("https://github.com/Miku0139oao/FakeGPS#troubleshooting");
            }
        });

        keepRunning.setChecked(SettingsStore.keepRunningWithScreenOff(this));
        keepRunning.setOnCheckedChangeListener((button, checked) ->
                SettingsStore.setKeepRunningWithScreenOff(this, checked));

        updateSummaries();
    }

    private void updateSummaries() {
        String currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        String[] names = languageNames();
        int index = 0;
        for (int i = 0; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equalsIgnoreCase(currentTag)) {
                index = i;
                break;
            }
        }
        mLanguageSummary.setText(names[index]);

        String theme = SettingsStore.getTheme(this);
        if (SettingsStore.THEME_LIGHT.equals(theme)) {
            mThemeSummary.setText(R.string.settings_theme_light);
        } else if (SettingsStore.THEME_DARK.equals(theme)) {
            mThemeSummary.setText(R.string.settings_theme_dark);
        } else {
            mThemeSummary.setText(R.string.settings_theme_system);
        }
    }

    private String[] languageNames() {
        return new String[]{
                getString(R.string.settings_language_system),
                "English", "繁體中文", "简体中文", "日本語", "한국어",
                "Español", "Français", "Deutsch", "Português (Brasil)"
        };
    }

    private void showLanguageDialog() {
        final String[] names = languageNames();
        String currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        int checked = 0;
        for (int i = 0; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equalsIgnoreCase(currentTag)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    LocaleListCompat locales = LANGUAGE_TAGS[which].isEmpty()
                            ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(LANGUAGE_TAGS[which]);
                    AppCompatDelegate.setApplicationLocales(locales);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showThemeDialog() {
        final String[] values = {
                SettingsStore.THEME_SYSTEM,
                SettingsStore.THEME_LIGHT,
                SettingsStore.THEME_DARK
        };
        final String[] labels = {
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
        };
        String current = SettingsStore.getTheme(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    SettingsStore.setTheme(this, values[which]);
                    AppCompatDelegate.setDefaultNightMode(
                            SettingsStore.nightModeFor(values[which]));
                    dialog.dismiss();
                    updateSummaries();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_about)
                .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.dialog_close, null)
                .setNeutralButton(R.string.settings_source,
                        (dialog, which) -> openUrl("https://github.com/Miku0139oao/FakeGPS"))
                .show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            // No browser installed.
        }
    }

    @SuppressWarnings("unused")
    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
