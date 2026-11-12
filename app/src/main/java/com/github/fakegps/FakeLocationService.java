package com.github.fakegps;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.ServiceCompat;

import com.github.fakegps.ui.MainActivity;
import com.tencent.fakegps.R;

import tiger.radio.loggerlibrary.Logger;

/**
 * Foreground service to keep mock location running in background on Android 8.0+.
 */
public class FakeLocationService extends Service {

    private static final String TAG = "FakeLocationService";
    private static final String CHANNEL_ID = "fake_gps_channel";
    private static final int NOTIFICATION_ID = 1001;

    private PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d(TAG, "FakeLocationService onCreate");
        createNotificationChannel();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "FakeGPS::LocationService");
            mWakeLock.setReferenceCounted(false);
        }
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d(TAG, "FakeLocationService onStartCommand");
        try {
            ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                            : 0);
        } catch (Exception e) {
            Logger.e(TAG, "Unable to enter foreground state", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        // Keep the 1 Hz injection loop running while the screen is off.
        // This is user-initiated and released when the service is stopped.
        if (SettingsStore.keepRunningWithScreenOff(this)
                && mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        // The mock loop lives in the app process, not in this service. Restarting
        // it without that state would only post a misleading "active" notification.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Logger.d(TAG, "FakeLocationService onDestroy");
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_stat_fakegps)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
    }
}
