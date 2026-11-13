package com.github.fakegps

import com.github.fakegps.model.LocPoint

/**
 * Pure movement state: current point, joystick step and flight interpolation.
 *
 * <p>The clock is injected so the flight state machine can be exercised in JVM
 * tests. Every method is synchronized because the engine thread reads ticks
 * while the UI thread changes the point, step or flight state.</p>
 */
class MovementState(private val nowMs: () -> Long) {

    data class Tick(val point: LocPoint, val flyCompleted: Boolean)

    private var current: LocPoint? = null
    private var stepMeters = DEFAULT_MOVE_STEP
    private var flyPath: FlyPath? = null
    private var flyStartMs = 0L
    private var flying = false

    @Synchronized
    fun start(point: LocPoint) {
        resetFly()
        current = LocPoint(point)
    }

    @Synchronized
    fun currentPoint(): LocPoint? = current?.let { LocPoint(it) }

    @Synchronized
    fun moveStep(): Double = stepMeters

    @Synchronized
    fun setMoveStep(moveStep: Double) {
        if (FakeGpsUtils.isValidMoveStep(moveStep)) {
            stepMeters = moveStep
        }
    }

    @Synchronized
    fun isFlyMode(): Boolean = flying

    @Synchronized
    fun resetFly() {
        flying = false
        flyPath = null
        flyStartMs = 0L
    }

    @Synchronized
    fun jumpTo(point: LocPoint) {
        resetFly()
        current = LocPoint(point)
    }

    @Synchronized
    fun flyTo(target: LocPoint, flyTimeSeconds: Int) {
        if (flyTimeSeconds <= 0) {
            return
        }
        val origin = current?.let { LocPoint(it) } ?: LocPoint(target)
        flyPath = FlyPath(origin, target, flyTimeSeconds * 1000L)
        flyStartMs = nowMs()
        current = LocPoint(origin)
        flying = true
    }

    @Synchronized
    fun stopFly() {
        resetFly()
    }

    /**
     * Produces the next location. While flying, the point comes from the path
     * snapshot and real elapsed time, so ticks never compound on each other.
     */
    @Synchronized
    fun tick(): Tick? {
        val point = current ?: return null
        val path = flyPath
        if (!flying || path == null) {
            return Tick(LocPoint(point), false)
        }

        val elapsedMs = nowMs() - flyStartMs
        val next = path.pointAt(elapsedMs)
        current = next
        val completed = path.isComplete(elapsedMs)
        if (completed) {
            flying = false
        }
        return Tick(LocPoint(next), completed)
    }

    @Synchronized
    fun arrowUp() {
        current = current?.let { GeoUtils.offset(it, stepMeters, 0.0) }
    }

    @Synchronized
    fun arrowDown() {
        current = current?.let { GeoUtils.offset(it, -stepMeters, 0.0) }
    }

    @Synchronized
    fun arrowLeft() {
        current = current?.let { GeoUtils.offset(it, 0.0, -stepMeters) }
    }

    @Synchronized
    fun arrowRight() {
        current = current?.let { GeoUtils.offset(it, 0.0, stepMeters) }
    }

    companion object {
        const val DEFAULT_MOVE_STEP = 2.0
    }
}
