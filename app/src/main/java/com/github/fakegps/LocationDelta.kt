package com.github.fakegps

import com.github.fakegps.model.LocPoint

/**
 * Distance, bearing and speed between two consecutive fixes. Pure Kotlin so it
 * can be unit-tested without the Android framework.
 */
class LocationDelta private constructor(
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val speedMetersPerSecond: Double,
    val isMoving: Boolean
) {

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0
        private const val MIN_MOVEMENT_METERS = 0.5

        @JvmStatic
        fun between(from: LocPoint, to: LocPoint, elapsedMs: Long): LocationDelta {
            val distance = distanceMeters(from, to)
            val moving = distance >= MIN_MOVEMENT_METERS && elapsedMs > 0
            val speed = if (moving) distance / (elapsedMs / 1000.0) else 0.0
            val bearing = if (moving) bearingDegrees(from, to) else 0.0
            return LocationDelta(distance, bearing, speed, moving)
        }

        private fun distanceMeters(from: LocPoint, to: LocPoint): Double {
            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)
            val deltaLat = lat2 - lat1
            val deltaLon = Math.toRadians(to.longitude - from.longitude)

            val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }

        private fun bearingDegrees(from: LocPoint, to: LocPoint): Double {
            val lat1 = Math.toRadians(from.latitude)
            val lat2 = Math.toRadians(to.latitude)
            val deltaLon = Math.toRadians(to.longitude - from.longitude)

            val y = Math.sin(deltaLon) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon)
            val degrees = Math.toDegrees(Math.atan2(y, x))
            return (degrees + 360.0) % 360.0
        }
    }
}
