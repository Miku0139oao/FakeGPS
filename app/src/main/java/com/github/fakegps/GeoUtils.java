package com.github.fakegps;

import com.github.fakegps.model.LocPoint;

/**
 * Small geodesy helpers for meter-based joystick movement.
 */
public final class GeoUtils {

    private static final double METERS_PER_DEGREE_LATITUDE = 111320.0;

    private GeoUtils() {
    }

    public static double latitudeDeltaForMeters(double meters) {
        return meters / METERS_PER_DEGREE_LATITUDE;
    }

    public static double longitudeDeltaForMeters(double meters, double latitude) {
        double cosine = Math.cos(Math.toRadians(latitude));
        if (Math.abs(cosine) < 1e-6) {
            cosine = Math.copySign(1e-6, cosine == 0 ? 1.0 : cosine);
        }
        return meters / (METERS_PER_DEGREE_LATITUDE * cosine);
    }

    /**
     * Moves a point by the given number of meters north/east and clamps the
     * result to valid latitude/longitude ranges.
     */
    public static LocPoint offset(LocPoint point, double northMeters, double eastMeters) {
        double latitude = point.getLatitude() + latitudeDeltaForMeters(northMeters);
        latitude = Math.max(-90.0, Math.min(90.0, latitude));
        double longitude = point.getLongitude()
                + longitudeDeltaForMeters(eastMeters, point.getLatitude());
        return new LocPoint(latitude, wrapLongitude(longitude));
    }

    public static double wrapLongitude(double longitude) {
        double wrapped = (longitude + 180.0) % 360.0;
        if (wrapped < 0) {
            wrapped += 360.0;
        }
        return wrapped - 180.0;
    }
}
