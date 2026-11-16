package com.github.fakegps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.github.fakegps.model.LocPoint
import com.github.fakegps.BuildConfig
import tiger.radio.loggerlibrary.Logger

/**
 * Owns the mock-location engine for the process lifetime.
 *
 * <p>One HandlerThread serializes provider installation, the 1 Hz tick and
 * teardown. It is started once and never quit: a HandlerThread cannot be
 * restarted, and the old create/stop races came from quitting it. Start and stop
 * are explicit commands queued on the looper, so rapid stop→start always ends in
 * a running engine with a scheduled tick.</p>
 *
 * <p>Uses the standard Mock Location API (addTestProvider +
 * setTestProviderLocation); no root is required. The user must select this app
 * as the mock location app in Developer Options.</p>
 */
class LocationThread(
    context: Context,
    private val listener: Listener
) : HandlerThread("LocationThread") {

    fun interface Listener {
        /** Called on the engine thread when no test provider could be installed. */
        fun onMockLocationUnavailable()
    }

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var handler: Handler? = null
    private var threadStarted = false

    // Suppresses the "mock app unavailable" callback after an explicit stop.
    @Volatile
    private var stopRequested = false

    // All of the following fields are confined to the engine looper.
    private var engineRunning = false
    private var tickScheduled = false
    private var providersAdded = false

    private var lastLocPoint = LocPoint(0.0, 0.0)
    private var hasLastLocPoint = false
    private var lastUpdateElapsedMs = 0L

    fun startThread() {
        if (threadStarted) {
            return
        }
        threadStarted = true
        super.start()
        handler = Handler(looper)
    }

    fun requestStart() {
        stopRequested = false
        handler?.post(startRunnable)
    }

    fun requestStop() {
        stopRequested = true
        handler?.post(stopRunnable)
    }

    private val startRunnable = Runnable { startEngine() }

    private val stopRunnable = Runnable { stopEngine() }

    private fun startEngine() {
        if (!engineRunning) {
            resetSession()
            if (!addTestProviders()) {
                failAndNotify()
                return
            }
            engineRunning = true
        }
        scheduleTick()
    }

    private fun stopEngine() {
        tickScheduled = false
        handler?.removeCallbacks(tickRunnable)
        removeTestProviders()
        engineRunning = false
    }

    private fun failAndNotify() {
        tickScheduled = false
        handler?.removeCallbacks(tickRunnable)
        removeTestProviders()
        engineRunning = false
        if (!stopRequested) {
            listener.onMockLocationUnavailable()
        }
    }

    private fun scheduleTick() {
        if (!tickScheduled) {
            tickScheduled = true
            handler?.post(tickRunnable)
        }
    }

    private fun scheduleNextTick() {
        if (!tickScheduled && engineRunning) {
            tickScheduled = true
            handler?.postDelayed(tickRunnable, UPDATE_INTERVAL_MS)
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            tickScheduled = false
            if (!engineRunning) {
                return
            }

            val locPoint = JoyStickManager.get().getUpdateLocPoint()
            if (locPoint == null) {
                scheduleNextTick()
                return
            }
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "UpdateLocation, $locPoint")
            }

            val now = SystemClock.elapsedRealtime()
            val delta = if (hasLastLocPoint && lastUpdateElapsedMs > 0) {
                LocationDelta.between(lastLocPoint, locPoint, now - lastUpdateElapsedMs)
            } else {
                null
            }
            val moving = delta != null && delta.isMoving
            val values = FixValues.from(
                moving = moving,
                delta = delta,
                wallTimeMs = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                api26OrNewer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            )

            var successCount = 0
            for (provider in PROVIDERS) {
                try {
                    val location = Location(provider)
                    location.latitude = locPoint.latitude
                    location.longitude = locPoint.longitude
                    location.altitude = values.altitudeMeters
                    location.accuracy = values.accuracyMeters
                    location.time = values.wallTimeMs
                    location.elapsedRealtimeNanos = values.elapsedRealtimeNanos
                    values.speedMetersPerSecond?.let { location.speed = it }
                    values.bearingDegrees?.let { location.bearing = it }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        values.verticalAccuracyMeters?.let { location.verticalAccuracyMeters = it }
                        values.bearingAccuracyDegrees?.let { location.bearingAccuracyDegrees = it }
                        values.speedAccuracyMetersPerSecond?.let {
                            location.speedAccuracyMetersPerSecond = it
                        }
                    }
                    locationManager.setTestProviderLocation(provider, location)
                    successCount++
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to set location for provider: $provider", e)
                }
            }

            if (successCount == 0) {
                // Mock permission was probably revoked while running. Stop instead
                // of retrying forever and reporting a healthy state.
                failAndNotify()
                return
            }

            lastLocPoint.latitude = locPoint.latitude
            lastLocPoint.longitude = locPoint.longitude
            hasLastLocPoint = true
            lastUpdateElapsedMs = now

            scheduleNextTick()
        }
    }

    private fun resetSession() {
        hasLastLocPoint = false
        lastUpdateElapsedMs = 0L
        lastLocPoint = LocPoint(0.0, 0.0)
    }

    /**
     * @return true if at least one provider was installed.
     */
    @SuppressLint("WrongConstant")
    @Suppress("DEPRECATION") // Criteria is the only provider-properties API below Android 12.
    private fun addTestProviders(): Boolean {
        if (providersAdded) {
            return true
        }
        if (!FakeGpsUtils.isMockLocationAppSelected(appContext)) {
            Logger.e(TAG, "App is not selected as the mock location app", null)
            return false
        }

        var added = 0
        for (provider in PROVIDERS) {
            try {
                // Remove an existing test provider if any.
                try {
                    locationManager.removeTestProvider(provider)
                } catch (ignored: Exception) {
                    // Not installed yet.
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ uses ProviderProperties.
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, true, true, true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, true, true, true,
                        android.location.Criteria.POWER_LOW,
                        android.location.Criteria.ACCURACY_FINE
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
                added++
                Logger.d(TAG, "Test provider added: $provider")
            } catch (e: SecurityException) {
                Logger.e(
                    TAG,
                    "Cannot add test provider '$provider'. Make sure this app is selected " +
                        "as mock location app in Developer Options.",
                    e
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Error adding test provider: $provider", e)
            }
        }
        providersAdded = added > 0
        return providersAdded
    }

    private fun removeTestProviders() {
        if (!providersAdded) {
            return
        }
        for (provider in PROVIDERS) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
                Logger.d(TAG, "Test provider removed: $provider")
            } catch (e: Exception) {
                Logger.e(TAG, "Error removing test provider: $provider", e)
            }
        }
        providersAdded = false
    }

    private companion object {
        private const val TAG = "LocationThread"

        /**
         * Browsers (Chrome, WebView) and Google Play Services ask the system's
         * fused provider for a location, not GPS/network directly. Without a
         * fused override they keep returning the real position, so the mock
         * must cover it as well. The extra entry is best-effort: on devices
         * without a fused provider the add simply throws and is logged.
         */
        private const val FUSED_PROVIDER = "fused"
        private val PROVIDERS = arrayOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            FUSED_PROVIDER
        )
        private const val UPDATE_INTERVAL_MS = 1000L
    }
}
