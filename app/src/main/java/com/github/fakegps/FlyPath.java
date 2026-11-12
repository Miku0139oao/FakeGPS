package com.github.fakegps;

import com.github.fakegps.model.LocPoint;

/**
 * Linear interpolation between two points over a duration.
 * Pure Java so the flight math can be unit-tested without Android.
 */
public final class FlyPath {

    private final double mStartLat;
    private final double mStartLon;
    private final double mEndLat;
    private final double mEndLon;
    private final long mDurationMs;

    public FlyPath(LocPoint origin, LocPoint target, long durationMs) {
        mStartLat = origin.getLatitude();
        mStartLon = origin.getLongitude();
        mEndLat = target.getLatitude();
        mEndLon = target.getLongitude();
        mDurationMs = Math.max(0, durationMs);
    }

    public double fraction(long elapsedMs) {
        if (mDurationMs <= 0) {
            return 1.0;
        }
        if (elapsedMs <= 0) {
            return 0.0;
        }
        if (elapsedMs >= mDurationMs) {
            return 1.0;
        }
        return (double) elapsedMs / (double) mDurationMs;
    }

    public boolean isComplete(long elapsedMs) {
        return mDurationMs <= 0 || elapsedMs >= mDurationMs;
    }

    public LocPoint pointAt(long elapsedMs) {
        double factor = fraction(elapsedMs);
        return new LocPoint(
                mStartLat + factor * (mEndLat - mStartLat),
                mStartLon + factor * (mEndLon - mStartLon));
    }
}
