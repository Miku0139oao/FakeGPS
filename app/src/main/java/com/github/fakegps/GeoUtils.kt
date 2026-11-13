package com.github.fakegps

import com.github.fakegps.model.LocPoint

/**
 * Small geodesy helpers for meter-based joystick movement.
 */
object GeoUtils {

    private const val METERS_PER_DEGREE_LATITUDE = 111320.0

    @JvmStatic
    fun latitudeDeltaForMeters(meters: Double): Double =
        meters / METERS_PER_DEGREE_LATITUDE

    @JvmStatic
    fun longitudeDeltaForMeters(meters: Double, latitude: Double): Double {
        var cosine = Math.cos(Math.toRadians(latitude))
        if (Math.abs(cosine) < 1e-6) {
            cosine = Math.copySign(1e-6, if (cosine == 0.0) 1.0 else cosine)
        }
        return meters / (METERS_PER_DEGREE_LATITUDE * cosine)
    }

    /**
     * Moves a point by the given number of meters north/east and clamps the
     * result to valid latitude/longitude ranges.
     */
    @JvmStatic
    fun offset(point: LocPoint, northMeters: Double, eastMeters: Double): LocPoint {
        var latitude = point.latitude + latitudeDeltaForMeters(northMeters)
        latitude = Math.max(-90.0, Math.min(90.0, latitude))
        val longitude = point.longitude +
            longitudeDeltaForMeters(eastMeters, point.latitude)
        return LocPoint(latitude, wrapLongitude(longitude))
    }

    @JvmStatic
    fun wrapLongitude(longitude: Double): Double {
        var wrapped = (longitude + 180.0) % 360.0
        if (wrapped < 0) {
            wrapped += 360.0
        }
        return wrapped - 180.0
    }
}
