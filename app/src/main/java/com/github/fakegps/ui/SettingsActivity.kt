package com.github.fakegps.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.net.toUri
import com.github.fakegps.BackgroundExecution
import com.github.fakegps.SettingsStore
import com.github.fakegps.JoyStickManager
import com.github.fakegps.UpdateCheck
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.fakegps.BuildConfig
import com.github.fakegps.R
import com.github.fakegps.databinding.ActivitySettingsBinding

/**
 * Material settings screen: language, theme, wake lock, and links.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiUtils.enableEdgeToEdge(this, R.id.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowLanguage.setOnClickListener { showLanguageDialog() }
        binding.rowTheme.setOnClickListener { showThemeDialog() }
        binding.rowAbout.setOnClickListener { showAboutDialog() }
        binding.rowSource.setOnClickListener {
            openUrl("https://github.com/Miku0139oao/FakeGPS")
        }
        binding.rowHelp.setOnClickListener {
            openUrl("https://github.com/Miku0139oao/FakeGPS#troubleshooting")
        }

        binding.switchKeepRunning.isChecked = SettingsStore.keepRunningWithScreenOff(this)
        binding.switchKeepRunning.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setKeepRunningWithScreenOff(this, checked)
        }
        binding.rowKeepRunning.setOnClickListener {
            binding.switchKeepRunning.toggle()
        }

        binding.switchShowJoystick.isChecked = SettingsStore.showJoystick(this)
        binding.switchShowJoystick.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setShowJoystick(this, checked)
            // Apply immediately instead of waiting for the next start/stop.
            JoyStickManager.get().refreshOverlay()
        }
        binding.rowShowJoystick.setOnClickListener {
            binding.switchShowJoystick.toggle()
        }

        binding.rowBackgroundExecution.setOnClickListener { showBackgroundDialog() }

        binding.switchAutoUpdate.isChecked = SettingsStore.autoUpdateCheck(this)
        binding.switchAutoUpdate.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setAutoUpdateCheck(this, checked)
        }
        binding.rowAutoUpdate.setOnClickListener { binding.switchAutoUpdate.toggle() }
        binding.rowCheckUpdate.setOnClickListener { checkForUpdates() }

        binding.tvAboutSummary.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        updateSummaries()
    }

    private fun updateSummaries() {
        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val names = languageNames()
        var index = 0
        for (i in LANGUAGE_TAGS.indices) {
            if (LANGUAGE_TAGS[i].equals(currentTag, ignoreCase = true)) {
                index = i
                break
            }
        }
        binding.tvLanguageSummary.text = names[index]

        val theme = SettingsStore.getTheme(this)
        binding.tvThemeSummary.setText(
            when (theme) {
                SettingsStore.THEME_LIGHT -> R.string.settings_theme_light
                SettingsStore.THEME_DARK -> R.string.settings_theme_dark
                else -> R.string.settings_theme_system
            }
        )

        binding.tvBackgroundSummary.setText(
            when {
                // Vendor allowlists are separate from the AOSP battery list, so
                // a positive status there would be misleading.
                BackgroundExecution.needsBackgroundGuidance() ->
                    R.string.settings_background_check_action
                BackgroundExecution.isIgnoringBatteryOptimizations(this) ->
                    R.string.settings_background_allowed
                else -> R.string.settings_background_restricted
            }
        )

        val lastCheck = SettingsStore.lastUpdateCheck(this)
        binding.tvUpdateSummary.text = if (lastCheck == 0L) {
            getString(R.string.settings_update_never)
        } else {
            getString(
                R.string.settings_update_last,
                DateUtils.getRelativeTimeSpanString(
                    lastCheck, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
            )
        }
    }

    private fun checkForUpdates() {
        Toast.makeText(this, R.string.settings_checking_update, Toast.LENGTH_SHORT).show()
        UpdateCheck.checkNow(this) { updateSummaries() }
    }

    private fun showBackgroundDialog() {
        val guided = BackgroundExecution.needsBackgroundGuidance()
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.background_dialog_title)
            .setMessage(R.string.background_dialog_message)
            .setPositiveButton(R.string.background_open_settings) { _, _ ->
                BackgroundExecution.openBackgroundSettings(this)
            }
            .setNegativeButton(R.string.background_later, null)
        if (guided) {
            builder.setNeutralButton(R.string.background_open_battery) { _, _ ->
                BackgroundExecution.requestIgnoreBatteryOptimizations(this)
            }
        }
        builder.show()
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }

    private fun languageNames(): Array<String> = arrayOf(
        getString(R.string.settings_language_system),
        "English", "繁體中文", "简体中文", "日本語", "한국어",
        "Español", "Français", "Deutsch", "Português (Brasil)"
    )

    private fun showLanguageDialog() {
        val names = languageNames()
        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        var checked = 0
        for (i in LANGUAGE_TAGS.indices) {
            if (LANGUAGE_TAGS[i].equals(currentTag, ignoreCase = true)) {
                checked = i
                break
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val locales = if (LANGUAGE_TAGS[which].isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(LANGUAGE_TAGS[which])
                }
                AppCompatDelegate.setApplicationLocales(locales)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val values = arrayOf(
            SettingsStore.THEME_SYSTEM,
            SettingsStore.THEME_LIGHT,
            SettingsStore.THEME_DARK
        )
        val labels = arrayOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark)
        )
        val current = SettingsStore.getTheme(this)
        var checked = 0
        for (i in values.indices) {
            if (values[i] == current) {
                checked = i
                break
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                SettingsStore.setTheme(this, values[which])
                AppCompatDelegate.setDefaultNightMode(
                    SettingsStore.nightModeFor(values[which])
                )
                dialog.dismiss()
                updateSummaries()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_about)
            .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.dialog_close, null)
            .setNeutralButton(R.string.settings_source) { _, _ ->
                openUrl("https://github.com/Miku0139oao/FakeGPS")
            }
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (ignored: Exception) {
            // No browser installed.
        }
    }

    companion object {
        private val LANGUAGE_TAGS = arrayOf(
            "", "en", "zh-TW", "zh-CN", "ja", "ko", "es", "fr", "de", "pt-BR"
        )
    }
}
