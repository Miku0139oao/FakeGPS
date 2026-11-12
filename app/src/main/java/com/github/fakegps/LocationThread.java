package com.github.fakegps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.github.fakegps.model.LocPoint;
import com.tencent.fakegps.BuildConfig;

import tiger.radio.loggerlibrary.Logger;

/**
 * LocationThread - uses standard Mock Location API (addTestProvider + setTestProviderLocation)
 * instead of hidden ILocationManager. No root required.
 * User must select this app as "Mock location app" in Developer Options.
 */
public class LocationThread extends HandlerThread {

    private static final String TAG = "LocationThread";
    private static final String[] PROVIDERS = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};

    public interface Listener {
        /** Called on the worker thread when no test provider could be installed. */
        void onMockLocationUnavailable();
    }

    private Context mContext;
    private volatile JoyStickManager mJoyStickManager;
    private LocationManager mLocationManager;
    private final Listener mListener;

    private Handler mHandler;
    private LocPoint mLastLocPoint = new LocPoint(0, 0);
    private volatile boolean mProvidersAdded = false;
    private volatile boolean mStopped = false;
    private boolean mHasLastLocPoint = false;
    private long mLastUpdateElapsedMs = 0;

    public LocationThread(Context context, JoyStickManager joyStickManager, Listener listener) {
        super("LocationThread");
        mContext = context;
        mJoyStickManager = joyStickManager;
        mListener = listener;
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public synchronized void start() {
        super.start();
        mHandler = new Handler(getLooper());
        mHandler.post(mSetupAndRun);
    }

    public void startThread() {
        start();
    }

    public void stopThread() {
        mStopped = true;
        mJoyStickManager = null;
        final Handler handler = mHandler;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            boolean posted = handler.post(new Runnable() {
                @Override
                public void run() {
                    removeTestProviders();
                    quit();
                }
            });
            if (!posted) {
                removeTestProviders();
                quit();
            }
        } else {
            removeTestProviders();
            quit();
        }
        try {
            interrupt();
        } catch (Exception e) {
            Logger.e(TAG, "stopThread fail!", e);
        }
    }

    /**
     * @return the number of providers that were successfully installed.
     */
    @SuppressLint("WrongConstant")
    private int addTestProviders() {
        if (mProvidersAdded) return PROVIDERS.length;
        if (!FakeGpsUtils.isMockLocationAppSelected(mContext)) {
            Logger.e(TAG, "App is not selected as the mock location app", null);
            return 0;
        }

        int added = 0;
        for (String provider : PROVIDERS) {
            try {
                // Remove existing test provider if any
                try {
                    mLocationManager.removeTestProvider(provider);
                } catch (Exception ignored) {
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ uses ProviderProperties
                    mLocationManager.addTestProvider(provider,
                            false, false, false, false, true,
                            true, true,
                            ProviderProperties.POWER_USAGE_LOW,
                            ProviderProperties.ACCURACY_FINE);
                } else {
                    mLocationManager.addTestProvider(provider,
                            false, false, false, false, true,
                            true, true,
                            android.location.Criteria.POWER_LOW,
                            android.location.Criteria.ACCURACY_FINE);
                }
                mLocationManager.setTestProviderEnabled(provider, true);
                added++;
                Logger.d(TAG, "Test provider added: " + provider);
            } catch (SecurityException e) {
                Logger.e(TAG, "Cannot add test provider '" + provider + "'. " +
                        "Make sure this app is selected as mock location app in Developer Options.", e);
            } catch (Exception e) {
                Logger.e(TAG, "Error adding test provider: " + provider, e);
            }
        }
        mProvidersAdded = added > 0;
        return added;
    }

    private void removeTestProviders() {
        if (!mProvidersAdded) return;
        for (String provider : PROVIDERS) {
            try {
                mLocationManager.setTestProviderEnabled(provider, false);
                mLocationManager.removeTestProvider(provider);
                Logger.d(TAG, "Test provider removed: " + provider);
            } catch (Exception e) {
                Logger.e(TAG, "Error removing test provider: " + provider, e);
            }
        }
        mProvidersAdded = false;
    }

    private void notifyMockLocationUnavailable() {
        if (!mStopped && mListener != null) {
            mListener.onMockLocationUnavailable();
        }
    }

    private final Runnable mSetupAndRun = new Runnable() {
        @Override
        public void run() {
            int added = addTestProviders();
            if (added == 0) {
                notifyMockLocationUnavailable();
                return;
            }
            mHandler.post(mUpdateLocation);
        }
    };

    private final Runnable mUpdateLocation = new Runnable() {
        @Override
        public void run() {
            JoyStickManager manager = mJoyStickManager;
            if (manager == null) return;

            LocPoint locPoint = manager.getUpdateLocPoint();
            if (locPoint == null) {
                mHandler.postDelayed(this, 1000);
                return;
            }
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "UpdateLocation, " + locPoint);
            }

            int successCount = 0;
            long now = SystemClock.elapsedRealtime();
            LocationDelta delta = null;
            if (mHasLastLocPoint && mLastUpdateElapsedMs > 0) {
                delta = LocationDelta.between(
                        mLastLocPoint, locPoint, now - mLastUpdateElapsedMs);
            }
            boolean moving = delta != null && delta.isMoving();

            for (String provider : PROVIDERS) {
                try {
                    Location location = new Location(provider);
                    location.setLatitude(locPoint.getLatitude());
                    location.setLongitude(locPoint.getLongitude());
                    location.setAltitude(50);
                    location.setAccuracy(10);
                    location.setTime(System.currentTimeMillis());
                    location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

                    if (moving) {
                        location.setSpeed((float) delta.getSpeedMetersPerSecond());
                        location.setBearing((float) delta.getBearingDegrees());
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        location.setVerticalAccuracyMeters(1.0f);
                        if (moving) {
                            location.setBearingAccuracyDegrees(5.0f);
                            location.setSpeedAccuracyMetersPerSecond(0.5f);
                        }
                    }

                    mLocationManager.setTestProviderLocation(provider, location);
                    successCount++;
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to set location for provider: " + provider, e);
                }
            }

            if (successCount == 0) {
                // Mock permission was probably revoked while running. Stop the loop
                // instead of retrying forever and reporting a healthy state.
                removeTestProviders();
                notifyMockLocationUnavailable();
                return;
            }

            mLastLocPoint.setLatitude(locPoint.getLatitude());
            mLastLocPoint.setLongitude(locPoint.getLongitude());
            mHasLastLocPoint = true;
            mLastUpdateElapsedMs = now;

            mHandler.postDelayed(mUpdateLocation, 1000);
        }
    };

    public Handler getHandler() {
        return mHandler;
    }
}
