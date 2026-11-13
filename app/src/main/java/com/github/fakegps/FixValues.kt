package com.github.fakegps

/**
 * Values written into the mock [android.location.Location] payload.
 * Pure data so the field selection can be unit-tested without Android.
 */
data class FixValues(
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    val wallTimeMs: Long,
    val elapsedRealtimeNanos: Long,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val verticalAccuracyMeters: Float?,
    val bearingAccuracyDegrees: Float?,
    val speedAccuracyMetersPerSecond: Float?
) {

    companion object {
        const val DEFAULT_ALTITUDE_METERS = 50.0
        const val DEFAULT_ACCURACY_METERS = 10.0f
        const val VERTICAL_ACCURACY_METERS = 1.0f
        const val BEARING_ACCURACY_DEGREES = 5.0f
        const val SPEED_ACCURACY_METERS_PER_SECOND = 0.5f

        fun from(
            moving: Boolean,
            delta: LocationDelta?,
            wallTimeMs: Long,
            elapsedRealtimeNanos: Long,
            api26OrNewer: Boolean
        ): FixValues {
            val speed = if (moving) delta?.speedMetersPerSecond?.toFloat() else null
            val bearing = if (moving) delta?.bearingDegrees?.toFloat() else null
            return FixValues(
                altitudeMeters = DEFAULT_ALTITUDE_METERS,
                accuracyMeters = DEFAULT_ACCURACY_METERS,
                wallTimeMs = wallTimeMs,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                speedMetersPerSecond = speed,
                bearingDegrees = bearing,
                verticalAccuracyMeters = if (api26OrNewer) VERTICAL_ACCURACY_METERS else null,
                bearingAccuracyDegrees = if (api26OrNewer && moving) {
                    BEARING_ACCURACY_DEGREES
                } else {
                    null
                },
                speedAccuracyMetersPerSecond = if (api26OrNewer && moving) {
                    SPEED_ACCURACY_METERS_PER_SECOND
                } else {
                    null
                }
            )
        }
    }
}
