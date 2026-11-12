package com.github.fakegps;

import com.github.fakegps.model.LocPoint;

/**
 * Distance, bearing, and speed between two consecutive fixes. Pure Java so it
 * can be unit-tested without the Android framework.
 */
public final class LocationDelta {

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double MIN_MOVEMENT_METERS = 0.5;

    private final double mDistanceMeters;
    private final double mBearingDegrees;
    private final double mSpeedMetersPerSecond;
    private final boolean mMoving;

    private LocationDelta(double distanceMeters, double bearingDegrees,
                          double speedMetersPerSecond, boolean moving) {
        mDistanceMeters = distanceMeters;
        mBearingDegrees = bearingDegrees;
        mSpeedMetersPerSecond = speedMetersPerSecond;
        mMoving = moving;
    }

    public static LocationDelta between(LocPoint from, LocPoint to, long elapsedMs) {
        double distance = distanceMeters(from, to);
        boolean moving = distance >= MIN_MOVEMENT_METERS && elapsedMs > 0;
        double speed = moving ? distance / (elapsedMs / 1000.0) : 0.0;
        double bearing = moving ? bearingDegrees(from, to) : 0.0;
        return new LocationDelta(distance, bearing, speed, moving);
    }

    public double getDistanceMeters() {
        return mDistanceMeters;
    }

    public double getBearingDegrees() {
        return mBearingDegrees;
    }

    public double getSpeedMetersPerSecond() {
        return mSpeedMetersPerSecond;
    }

    public boolean isMoving() {
        return mMoving;
    }

    private static double distanceMeters(LocPoint from, LocPoint to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(to.getLongitude() - from.getLongitude());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static double bearingDegrees(LocPoint from, LocPoint to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double deltaLon = Math.toRadians(to.getLongitude() - from.getLongitude());

        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return (degrees + 360.0) % 360.0;
    }
}
