package com.github.fakegps

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.github.fakegps.ui.MainActivity
import com.github.fakegps.R
import tiger.radio.loggerlibrary.Logger

/**
 * Foreground service to keep mock location running in background on Android 8.0+.
 */
class FakeLocationService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "FakeLocationService onCreate")
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "FakeGPS::LocationService"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG, "FakeLocationService onStartCommand")
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Unable to enter foreground state", e)
            stopSelf()
            return START_NOT_STICKY
        }
        // Keep the 1 Hz injection loop running while the screen is off.
        // This is user-initiated and released when the service is stopped.
        if (SettingsStore.keepRunningWithScreenOff(this) && wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }
        // The mock loop lives in the app process, not in this service. Restarting
        // it without that state would only post a misleading "active" notification.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Logger.d(TAG, "FakeLocationService onDestroy")
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The service context keeps the system language; use the language
            // picked in the app so the notification matches its screens.
            val context = AppLocale.context(this)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = context.getString(R.string.notification_channel_desc)
            channel.setShowBadge(false)
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION") // The channel-less builder is the API 21-25 path.
    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val context = AppLocale.context(this)
        return builder
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_fakegps)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    private companion object {
        private const val TAG = "FakeLocationService"
        private const val CHANNEL_ID = "fake_gps_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
