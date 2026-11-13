package com.github.fakegps

import com.github.fakegps.model.LocPoint

/**
 * Linear interpolation between two points over a duration.
 * Pure Kotlin so the flight math can be unit-tested without Android.
 */
class FlyPath(origin: LocPoint, target: LocPoint, durationMs: Long) {

    private val startLat: Double = origin.latitude
    private val startLon: Double = origin.longitude
    private val endLat: Double = target.latitude
    private val endLon: Double = target.longitude
    private val durationMs: Long = Math.max(0, durationMs)

    fun fraction(elapsedMs: Long): Double {
        if (durationMs <= 0) {
            return 1.0
        }
        if (elapsedMs <= 0) {
            return 0.0
        }
        if (elapsedMs >= durationMs) {
            return 1.0
        }
        return elapsedMs.toDouble() / durationMs.toDouble()
    }

    fun isComplete(elapsedMs: Long): Boolean =
        durationMs <= 0 || elapsedMs >= durationMs

    fun pointAt(elapsedMs: Long): LocPoint {
        val factor = fraction(elapsedMs)
        return LocPoint(
            startLat + factor * (endLat - startLat),
            startLon + factor * (endLon - startLon)
        )
    }
}
