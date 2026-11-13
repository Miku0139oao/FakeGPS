package com.github.fakegps

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.litesuits.orm.LiteOrm
import com.tencent.fakegps.BuildConfig
import java.io.File
import org.osmdroid.config.Configuration
import tiger.radio.loggerlibrary.Logger

/**
 * Application entry point: theme, logger, osmdroid and the lite-orm singleton.
 */
class FakeGpsApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        sAppContext = base
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            SettingsStore.nightModeFor(SettingsStore.getTheme(this))
        )
        if (BuildConfig.DEBUG) {
            initLogger()
        }
        initOsmdroid()
        JoyStickManager.get().init(this)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                JoyStickManager.get().onAppActivityStarted()
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                JoyStickManager.get().onAppActivityStopped()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun initLogger() {
        // Keep diagnostic logs in internal storage; release builds do not log at all.
        val outputDir = File(filesDir, "logs")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        Logger.configure(outputDir, "logs")
    }

    /**
     * Must run before the first MapView is created. Mapnik rejects the library's
     * default user agent and would otherwise serve a blank map.
     */
    private fun initOsmdroid() {
        val configuration = Configuration.getInstance()
        configuration.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        configuration.osmdroidBasePath = File(filesDir, "osmdroid")
        configuration.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        configuration.userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
        // Keep the tile cache small on low-end devices.
        configuration.tileFileSystemCacheMaxBytes = 50L * 1024 * 1024
        configuration.tileFileSystemCacheTrimBytes = 40L * 1024 * 1024
    }

    companion object {
        @Volatile
        private var sAppContext: Context? = null

        @Volatile
        private var sLiteOrm: LiteOrm? = null

        @JvmStatic
        fun get(): Context? = sAppContext

        @JvmStatic
        fun getLiteOrm(): LiteOrm {
            val existing = sLiteOrm
            if (existing != null) {
                return existing
            }
            synchronized(FakeGpsApp::class.java) {
                val current = sLiteOrm
                if (current != null) {
                    return current
                }
                val created = LiteOrm.newSingleInstance(
                    requireNotNull(sAppContext) { "FakeGpsApp context is not ready" },
                    "fake_gps.db"
                )
                created.setDebugged(BuildConfig.DEBUG)
                sLiteOrm = created
                return created
            }
        }
    }
}
