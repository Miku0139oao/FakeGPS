package com.github.fakegps

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import tiger.radio.loggerlibrary.Logger

/**
 * Looks up the newest GitHub release and, when it is newer than the installed
 * build, offers to download it.
 *
 * <p>The request goes to GitHub's public Releases API with a generic
 * User-Agent. Nothing about the device or the user is sent, and checks run at
 * most once a day (the setting can turn them off). Network and JSON failures
 * are silent for automatic checks.</p>
 */
object UpdateCheck {

    private const val TAG = "UpdateCheck"
    const val API_URL = "https://api.github.com/repos/Miku0139oao/FakeGPS/releases/latest"
    const val RELEASES_URL = "https://github.com/Miku0139oao/FakeGPS/releases/latest"
    private const val USER_AGENT = "FakeGPS-Android"

    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val PROMPT_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000L
    private const val MAX_NOTES_LENGTH = 600

    // How long to wait before retrying after a failed lookup, so an app that is
    // resumed repeatedly while offline does not keep sending requests.
    private const val RETRY_INTERVAL_MS = 60 * 60 * 1000L

    // The activity whose manual check is in flight. A different activity may
    // start a new check once the owner is gone, so a cancelled request cannot
    // block "Check now" in a fresh screen.
    @Volatile
    private var checkingActivity: Activity? = null

    data class Release(
        val version: Version,
        val apkUrl: String?,
        val pageUrl: String,
        val notes: String
    )

    /**
     * Parses GitHub's "latest release" JSON. Drafts and pre-releases are
     * ignored, and so is a response without a usable tag.
     */
    @JvmStatic
    fun parseRelease(json: String): Release? {
        return try {
            val obj = JSONObject(json)
            if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) {
                return null
            }
            val version = Version.parse(obj.optString("tag_name")) ?: return null
            // Android's optString turns a JSON null into the literal "null",
            // unlike the JVM org.json used by the unit tests.
            val pageUrl = stringOrEmpty(obj, "html_url").ifBlank { RELEASES_URL }
            var apkUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val url = stringOrEmpty(asset, "browser_download_url")
                    if (url.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = url
                        break
                    }
                }
            }
            Release(version, apkUrl, pageUrl, stringOrEmpty(obj, "body").trim())
        } catch (e: JSONException) {
            Logger.e(TAG, "Release JSON is not valid", e)
            null
        }
    }

    private fun stringOrEmpty(obj: JSONObject, key: String): String {
        return if (!obj.has(key) || obj.isNull(key)) "" else obj.optString(key)
    }

    /** Suspending network lookup; null when offline, rate-limited or malformed. */
    @JvmStatic
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Logger.e(TAG, "Update check HTTP ${connection.responseCode}")
                return@withContext null
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            parseRelease(json)
        } catch (e: Exception) {
            Logger.e(TAG, "Update check failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    @JvmStatic
    fun currentVersion(): Version? = Version.parse(BuildConfig.VERSION_NAME)

    @JvmStatic
    fun isNewer(release: Release): Boolean {
        val current = currentVersion() ?: return false
        return release.version > current
    }

    /**
     * Called from the main screen whenever it comes to the front. Checks at
     * most once a day after a successful lookup (and at most once an hour while
     * lookups fail), and only prompts when the newest release is newer than
     * this build, no more often than once per three days for the same version.
     */
    @JvmStatic
    fun maybeAutoCheck(activity: Activity) {
        if (!SettingsStore.autoUpdateCheck(activity)) {
            return
        }
        val now = System.currentTimeMillis()
        val last = SettingsStore.lastUpdateCheck(activity)
        if (last > 0 && now >= last && now - last < CHECK_INTERVAL_MS) {
            return
        }
        val lastAttempt = SettingsStore.lastUpdateAttempt(activity)
        if (lastAttempt > 0 && now >= lastAttempt && now - lastAttempt < RETRY_INTERVAL_MS) {
            return
        }
        val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: return
        SettingsStore.setLastUpdateAttempt(activity, now)
        scope.launch {
            val release = fetchLatest() ?: return@launch
            // Only a successful lookup counts as checked; a failure is retried
            // later so a captive portal does not hide an update for a day.
            SettingsStore.setLastUpdateCheck(activity, now)
            if (activity.isFinishing || activity.isDestroyed || !isNewer(release)) {
                return@launch
            }
            if (!shouldPrompt(activity, release.version, now)) {
                return@launch
            }
            SettingsStore.setPromptedVersion(activity, release.version.toString())
            SettingsStore.setPromptedAt(activity, now)
            showUpdateDialog(activity, release)
        }
    }

    /** Manual check; always reports the outcome and refreshes [onFinished]. */
    @JvmStatic
    fun checkNow(activity: Activity, onFinished: (() -> Unit)? = null) {
        val running = checkingActivity
        // Double tap on the same screen.
        if (running === activity) {
            return
        }
        // Another screen's check is in flight; let it finish. A cancelled check
        // may still be stuck in the blocking read, but its activity is gone.
        if (running != null && !running.isFinishing && !running.isDestroyed) {
            return
        }
        val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: return
        checkingActivity = activity
        SettingsStore.setLastUpdateAttempt(activity, System.currentTimeMillis())
        scope.launch {
            try {
                val release = fetchLatest()
                if (release != null) {
                    // A failed check must not look like a successful one.
                    SettingsStore.setLastUpdateCheck(activity, System.currentTimeMillis())
                }
                onFinished?.invoke()
                if (activity.isFinishing || activity.isDestroyed) {
                    return@launch
                }
                when {
                    release == null -> Toast.makeText(
                        activity, R.string.settings_update_failed, Toast.LENGTH_LONG
                    ).show()

                    isNewer(release) -> {
                        SettingsStore.setPromptedVersion(activity, release.version.toString())
                        SettingsStore.setPromptedAt(activity, System.currentTimeMillis())
                        showUpdateDialog(activity, release)
                    }

                    else -> Toast.makeText(
                        activity,
                        // The installed build can be ahead of the published
                        // release, so report the version actually running.
                        activity.getString(
                            R.string.settings_update_latest, BuildConfig.VERSION_NAME
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                if (checkingActivity === activity) {
                    checkingActivity = null
                }
            }
        }
    }

    private fun shouldPrompt(context: Context, version: Version, now: Long): Boolean {
        return shouldPromptFor(
            SettingsStore.promptedVersion(context),
            SettingsStore.promptedAt(context),
            version,
            now
        )
    }

    /**
     * A new version always prompts; the same version is only repeated after
     * [PROMPT_INTERVAL_MS] so a dismissed dialog stays dismissed for a while.
     */
    @JvmStatic
    fun shouldPromptFor(
        promptedVersion: String?,
        promptedAtMs: Long,
        latest: Version,
        nowMs: Long
    ): Boolean {
        if (promptedVersion != latest.toString()) {
            return true
        }
        return promptedAtMs == 0L || nowMs - promptedAtMs >= PROMPT_INTERVAL_MS
    }

    @JvmStatic
    fun showUpdateDialog(activity: Activity, release: Release) {
        val notes = excerpt(release.notes)
        val message = activity.getString(
            R.string.update_dialog_message,
            BuildConfig.VERSION_NAME,
            release.version.toString()
        ) + if (notes.isEmpty()) "" else "\n\n$notes"

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(
                activity.getString(R.string.update_dialog_title, release.version.toString())
            )
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ ->
                openRelease(activity, release)
            }
            .setNegativeButton(R.string.update_later, null)
        if (release.apkUrl != null) {
            builder.setNeutralButton(R.string.update_release_page) { _, _ ->
                openUrl(activity, release.pageUrl)
            }
        }
        builder.show()
    }

    /** Opens the release APK download; falls back to the release page. */
    private fun openRelease(activity: Activity, release: Release) {
        val url = release.apkUrl ?: release.pageUrl
        if (!openUrl(activity, url) && url != release.pageUrl) {
            openUrl(activity, release.pageUrl)
        }
    }

    private fun openUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "No app can open $url", e)
            false
        }
    }

    /**
     * Turns the Markdown that GitHub stores in the release body into plain text
     * for the dialog: headings lose their `#`, emphasis markers disappear and
     * list items use a bullet.
     */
    @JvmStatic
    fun plainNotes(notes: String): String {
        return notes.replace("\r", "").lines()
            .joinToString("\n") { raw ->
                val line = raw.trim()
                    .replace(Regex("^#{1,6}\\s*"), "")
                    .replace("**", "")
                    .replace("`", "")
                if (line.startsWith("- ") || line.startsWith("* ")) {
                    "• " + line.substring(2)
                } else {
                    line
                }
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun excerpt(notes: String): String {
        val compact = plainNotes(notes)
        if (compact.length <= MAX_NOTES_LENGTH) {
            return compact
        }
        return compact.take(MAX_NOTES_LENGTH).trimEnd() + "…"
    }
}
