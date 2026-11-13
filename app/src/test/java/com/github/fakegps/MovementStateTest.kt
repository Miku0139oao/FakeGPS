package com.github.fakegps

import com.github.fakegps.model.LocPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementStateTest {

    private var now = 0L
    private val state = MovementState { now }

    @Test
    fun updateBeforeStartReturnsNull() {
        assertNull(state.tick())
        assertNull(state.currentPoint())
    }

    @Test
    fun startSetsPointAndClearsFly() {
        state.start(LocPoint(1.0, 2.0))
        state.flyTo(LocPoint(3.0, 4.0), 10)

        state.start(LocPoint(5.0, 6.0))

        val point = requireNotNull(state.currentPoint())
        assertEquals(5.0, point.latitude, 0.0)
        assertEquals(6.0, point.longitude, 0.0)
        assertFalse(state.isFlyMode())
    }

    @Test
    fun flyInterpolatesFromSnapshotOrigin() {
        state.start(LocPoint(0.0, 0.0))
        now = 0L
        state.flyTo(LocPoint(10.0, 20.0), 10)

        now = 2_500
        assertEquals(2.5, requireNotNull(state.tick()).point.latitude, 1e-9)
        now = 5_000
        assertEquals(5.0, requireNotNull(state.tick()).point.latitude, 1e-9)
        now = 7_500
        assertEquals(7.5, requireNotNull(state.tick()).point.latitude, 1e-9)
        now = 10_000
        val completeTick = requireNotNull(state.tick())
        assertEquals(10.0, completeTick.point.latitude, 1e-9)
        assertTrue(completeTick.flyCompleted)
        assertFalse(state.isFlyMode())
    }

    @Test
    fun tickDoesNotCompoundInterpolation() {
        state.start(LocPoint(0.0, 0.0))
        now = 0L
        state.flyTo(LocPoint(10.0, 20.0), 10)

        now = 2_500
        assertEquals(2.5, requireNotNull(state.tick()).point.latitude, 1e-9)
        now = 5_000
        assertEquals(5.0, requireNotNull(state.tick()).point.latitude, 1e-9)
    }

    @Test
    fun flyCompletionClearsFlyModeOnlyOnce() {
        state.start(LocPoint(0.0, 0.0))
        now = 0L
        state.flyTo(LocPoint(10.0, 0.0), 1)

        now = 1_000
        assertTrue(requireNotNull(state.tick()).flyCompleted)
        now = 2_000
        val laterTick = requireNotNull(state.tick())
        assertFalse(laterTick.flyCompleted)
        assertEquals(10.0, laterTick.point.latitude, 1e-9)
        assertFalse(state.isFlyMode())
    }

    @Test
    fun jumpCancelsFly() {
        state.start(LocPoint(0.0, 0.0))
        state.flyTo(LocPoint(10.0, 0.0), 10)
        state.jumpTo(LocPoint(30.0, 40.0))

        val point = requireNotNull(state.tick()).point
        assertEquals(30.0, point.latitude, 0.0)
        assertEquals(40.0, point.longitude, 0.0)
        assertFalse(state.isFlyMode())
    }

    @Test
    fun stopKeepsCurrentPointButClearsFly() {
        state.start(LocPoint(0.0, 0.0))
        now = 0L
        state.flyTo(LocPoint(10.0, 0.0), 10)
        now = 2_500
        requireNotNull(state.tick())

        state.stopFly()

        assertFalse(state.isFlyMode())
        now = 5_000
        assertEquals(2.5, requireNotNull(state.currentPoint()).latitude, 1e-9)
        assertEquals(2.5, requireNotNull(state.tick()).point.latitude, 1e-9)
    }

    @Test
    fun arrowsMoveByMeters() {
        state.start(LocPoint(0.0, 0.0))
        state.setMoveStep(2.0)

        state.arrowUp()
        val north = requireNotNull(state.currentPoint())
        assertTrue(north.latitude > 0.0)
        state.arrowDown()
        assertEquals(0.0, requireNotNull(state.currentPoint()).latitude, 1e-9)

        state.arrowRight()
        val east = requireNotNull(state.currentPoint())
        assertTrue(east.longitude > 0.0)
        state.arrowLeft()
        assertEquals(0.0, requireNotNull(state.currentPoint()).longitude, 1e-9)
    }

    @Test
    fun invalidMoveStepIgnored() {
        state.start(LocPoint(0.0, 0.0))
        state.setMoveStep(0.0)
        state.setMoveStep(-1.0)
        state.setMoveStep(Double.NaN)

        assertEquals(MovementState.DEFAULT_MOVE_STEP, state.moveStep(), 0.0)
    }
}
