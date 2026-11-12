package com.github.fakegps;

import static org.junit.Assert.assertEquals;

import com.github.fakegps.model.LocPoint;

import org.junit.Test;

public class FlyPathTest {

    private static final double EPSILON = 1e-9;

    @Test
    public void pointAt_isLinear() {
        FlyPath path = new FlyPath(new LocPoint(0.0, 0.0), new LocPoint(10.0, 20.0), 10_000L);

        assertEquals(0.0, path.pointAt(0).getLatitude(), EPSILON);
        assertEquals(2.5, path.pointAt(2_500).getLatitude(), EPSILON);
        assertEquals(5.0, path.pointAt(5_000).getLatitude(), EPSILON);
        assertEquals(7.5, path.pointAt(7_500).getLatitude(), EPSILON);
        assertEquals(10.0, path.pointAt(10_000).getLatitude(), EPSILON);
        assertEquals(10.0, path.pointAt(5_000).getLongitude(), EPSILON);
    }

    @Test
    public void pointAt_clampsAfterCompletion() {
        FlyPath path = new FlyPath(new LocPoint(1.0, 2.0), new LocPoint(3.0, 4.0), 1_000L);

        assertEquals(3.0, path.pointAt(5_000).getLatitude(), EPSILON);
        assertEquals(4.0, path.pointAt(5_000).getLongitude(), EPSILON);
    }

    @Test
    public void pointAt_handlesNegativeElapsed() {
        FlyPath path = new FlyPath(new LocPoint(1.0, 2.0), new LocPoint(3.0, 4.0), 1_000L);

        assertEquals(1.0, path.pointAt(-100).getLatitude(), EPSILON);
    }

    @Test
    public void zeroDuration_finishesImmediately() {
        FlyPath path = new FlyPath(new LocPoint(1.0, 2.0), new LocPoint(3.0, 4.0), 0L);

        assertEquals(3.0, path.pointAt(0).getLatitude(), EPSILON);
        assertEquals(true, path.isComplete(0));
    }

    @Test
    public void isComplete_onlyAfterDuration() {
        FlyPath path = new FlyPath(new LocPoint(0.0, 0.0), new LocPoint(1.0, 1.0), 2_000L);

        assertEquals(false, path.isComplete(1_999));
        assertEquals(true, path.isComplete(2_000));
    }
}
