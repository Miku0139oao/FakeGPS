package com.github.fakegps;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.fakegps.model.LocPoint;
import com.github.fakegps.ui.BookmarkActivity;
import com.github.fakegps.ui.FlyToActivity;
import com.github.fakegps.ui.JoyStickView;
import com.github.fakegps.ui.MainActivity;
import com.tencent.fakegps.R;

import tiger.radio.loggerlibrary.Logger;

/**
 * Created by tiger on 7/22/16.
 */
public class JoyStickManager implements IJoyStickPresenter {

    private static final String TAG = "JoyStickManager";

    // Default joystick step in meters.
    public static double STEP_DEFAULT = 2.0;

    private static JoyStickManager INSTANCE = new JoyStickManager();

    private Context mContext;
    private LocationThread mLocationThread;
    private volatile boolean mIsStarted = false;
    private double mMoveStep = STEP_DEFAULT;

    private LocPoint mCurrentLocPoint;

    // Linear interpolation state. The path snapshots the origin when the flight
    // starts, so a tick never compounds on the previous interpolated position.
    private FlyPath mFlyPath;
    private long mFlyStartElapsedMs;
    private volatile boolean mIsFlyMode = false;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private JoyStickView mJoyStickView;

    private JoyStickManager() {
    }


    public void init(Context context) {
        mContext = context;
    }

    public static JoyStickManager get() {
        return INSTANCE;
    }

    public void start(@NonNull LocPoint locPoint) {
        synchronized (this) {
            resetFlyState();
            mCurrentLocPoint = new LocPoint(locPoint);
        }
        if (mLocationThread == null || !mLocationThread.isAlive()) {
            mLocationThread = new LocationThread(
                    mContext.getApplicationContext(), this, mLocationListener);
            mLocationThread.startThread();
        }

        // Start foreground service for Android 8.0+
        startForegroundService();

        showJoyStick();
        mIsStarted = true;
    }

    public void stop() {
        if (mLocationThread != null) {
            mLocationThread.stopThread();
            mLocationThread = null;
        }

        // Stop foreground service
        stopForegroundService();

        hideJoyStick();
        synchronized (this) {
            resetFlyState();
        }
        mIsStarted = false;
    }

    private final LocationThread.Listener mLocationListener = new LocationThread.Listener() {
        @Override
        public void onMockLocationUnavailable() {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    stop();
                    Toast.makeText(mContext,
                            R.string.mock_app_not_selected,
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private void startForegroundService() {
        try {
            Intent serviceIntent = new Intent(mContext, FakeLocationService.class);
            androidx.core.content.ContextCompat.startForegroundService(mContext, serviceIntent);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start foreground service", e);
        }
    }

    private void stopForegroundService() {
        try {
            Intent serviceIntent = new Intent(mContext, FakeLocationService.class);
            mContext.stopService(serviceIntent);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to stop foreground service", e);
        }
    }

    public boolean isStarted() {
        return mIsStarted;
    }

    public void showJoyStick() {
        if (mJoyStickView == null) {
            mJoyStickView = new JoyStickView(mContext);
            mJoyStickView.setJoyStickPresenter(this);
        }

        if (!mJoyStickView.isShowing()) {
            mJoyStickView.addToWindow();
        }
    }

    public void hideJoyStick() {
        if (mJoyStickView != null && mJoyStickView.isShowing()) {
            mJoyStickView.removeFromWindow();
        }
    }

    public synchronized LocPoint getCurrentLocPoint() {
        return mCurrentLocPoint == null ? null : new LocPoint(mCurrentLocPoint);
    }

    /**
     * Returns a new point every call. When a flight is active the point is the
     * linear interpolation between the snapshot origin and the target, based on
     * real elapsed time rather than the number of ticks.
     */
    public synchronized LocPoint getUpdateLocPoint() {
        if (mCurrentLocPoint == null) {
            return null;
        }
        if (!mIsFlyMode || mFlyPath == null) {
            return new LocPoint(mCurrentLocPoint);
        }

        long elapsedMs = SystemClock.elapsedRealtime() - mFlyStartElapsedMs;
        LocPoint point = mFlyPath.pointAt(elapsedMs);
        mCurrentLocPoint = point;
        if (mFlyPath.isComplete(elapsedMs)) {
            mIsFlyMode = false;
        }
        return new LocPoint(mCurrentLocPoint);
    }

    public synchronized void jumpToLocation(@NonNull LocPoint location) {
        resetFlyState();
        mCurrentLocPoint = new LocPoint(location);
    }

    public synchronized void flyToLocation(@NonNull LocPoint location, int flyTimeSeconds) {
        if (flyTimeSeconds <= 0) {
            return;
        }
        LocPoint origin = mCurrentLocPoint == null
                ? new LocPoint(location)
                : new LocPoint(mCurrentLocPoint);
        mFlyPath = new FlyPath(origin, location, flyTimeSeconds * 1000L);
        mFlyStartElapsedMs = SystemClock.elapsedRealtime();
        mCurrentLocPoint = new LocPoint(origin);
        mIsFlyMode = true;
    }

    public boolean isFlyMode() {
        return mIsFlyMode;
    }

    public synchronized void stopFlyMode() {
        resetFlyState();
    }

    private void resetFlyState() {
        mIsFlyMode = false;
        mFlyPath = null;
        mFlyStartElapsedMs = 0;
    }

    public synchronized void setMoveStep(double moveStep) {
        if (FakeGpsUtils.isValidMoveStep(moveStep)) {
            mMoveStep = moveStep;
        }
    }

    public synchronized double getMoveStep() {
        return mMoveStep;
    }


    @Override
    public void onSetLocationClick() {
        Logger.d(TAG, "onSetLocationClick");
        MainActivity.startPage(mContext);
    }

    @Override
    public void onFlyClick() {
        Logger.d(TAG, "onFlyClick");
        if (mIsFlyMode) {
            stopFlyMode();
            Toast.makeText(mContext, R.string.stop_fly, Toast.LENGTH_SHORT).show();
        } else {
            FlyToActivity.startPage(mContext);
        }

    }

    @Override
    public void onBookmarkLocationClick() {
        Logger.d(TAG, "onBookmarkLocationClick");
        LocPoint locPoint = getCurrentLocPoint();
        if (locPoint != null) {
            BookmarkActivity.startPage(mContext, "Bookmark", locPoint);
            Toast.makeText(mContext, mContext.getString(R.string.location_copied) + "\n" + locPoint, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(mContext, R.string.service_not_started, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCopyLocationClick() {
        Logger.d(TAG, "onCopyLocationClick");
        LocPoint locPoint = getCurrentLocPoint();
        if (locPoint != null) {
            FakeGpsUtils.copyToClipboard(mContext, locPoint.toString());
            Toast.makeText(mContext, mContext.getString(R.string.location_copied) + "\n" + locPoint, Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public synchronized void onArrowUpClick() {
        Logger.d(TAG, "onArrowUpClick");
        if (mCurrentLocPoint == null) return;
        mCurrentLocPoint = GeoUtils.offset(mCurrentLocPoint, mMoveStep, 0);
    }

    @Override
    public synchronized void onArrowDownClick() {
        Logger.d(TAG, "onArrowDownClick");
        if (mCurrentLocPoint == null) return;
        mCurrentLocPoint = GeoUtils.offset(mCurrentLocPoint, -mMoveStep, 0);
    }

    @Override
    public synchronized void onArrowLeftClick() {
        Logger.d(TAG, "onArrowLeftClick");
        if (mCurrentLocPoint == null) return;
        mCurrentLocPoint = GeoUtils.offset(mCurrentLocPoint, 0, -mMoveStep);
    }

    @Override
    public synchronized void onArrowRightClick() {
        Logger.d(TAG, "onArrowRightClick");
        if (mCurrentLocPoint == null) return;
        mCurrentLocPoint = GeoUtils.offset(mCurrentLocPoint, 0, mMoveStep);
    }

}
