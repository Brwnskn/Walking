package com.example

import com.example.domain.LocationSample
import com.example.domain.TrackerPhase
import com.example.domain.TravelMode
import com.example.domain.TripTrackerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTrackerEngineTest {

    @Test
    fun `brief stop under 15 minutes keeps trip open and resumes seamlessly`() {
        val engine = TripTrackerEngine()
        engine.startTracking()

        val t0 = 1_700_000_000_000L
        // Start at origin
        engine.processLocation(LocationSample(37.7749, -122.4194, t0, 0f, 8f))
        // Bike ride ~500m east
        engine.processLocation(LocationSample(37.7749, -122.4137, t0 + 90_000L, 5.5f, 8f))
        assertEquals(TrackerPhase.MOVING, engine.snapshot(t0 + 90_000L).phase)

        // Stop for coffee at t0 + 120s
        engine.processLocation(LocationSample(37.7749, -122.4137, t0 + 120_000L, 0f, 8f))
        // Still stopped 50s later -> enters DWELLING
        engine.processLocation(LocationSample(37.7749, -122.4137, t0 + 170_000L, 0f, 8f))
        assertEquals(TrackerPhase.DWELLING, engine.snapshot(t0 + 170_000L).phase)

        // Check at 10 minutes of stopping -> trip must NOT finalize yet
        val earlyTick = engine.onTick(t0 + 120_000L + 10 * 60_000L)
        assertNull(earlyTick)
        assertEquals(TrackerPhase.DWELLING, engine.snapshot(t0 + 120_000L + 10 * 60_000L).phase)

        // Resume riding at 11 minutes -> back to MOVING on same trip
        val resumeTime = t0 + 120_000L + 11 * 60_000L
        val resumed = engine.processLocation(LocationSample(37.7749, -122.4060, resumeTime, 6.0f, 8f))
        assertNull(resumed)
        assertEquals(TrackerPhase.MOVING, engine.snapshot(resumeTime).phase)
        assertTrue(engine.snapshot(resumeTime).activeDistanceMeters > 1000.0)
    }

    @Test
    fun `stop longer than 15 minutes locks destination and starts next leg from there`() {
        val engine = TripTrackerEngine()
        engine.startTracking()

        val t0 = 1_700_000_000_000L
        // Origin A
        engine.processLocation(LocationSample(37.7749, -122.4194, t0, 0f, 8f))
        // Car drive ~2.2km north
        engine.processLocation(LocationSample(37.7830, -122.4194, t0 + 60_000L, 18.0f, 8f))
        engine.processLocation(LocationSample(37.7900, -122.4194, t0 + 120_000L, 18.0f, 8f))

        // Arrive & park at B at t0 + 150s
        val arriveAtB = t0 + 150_000L
        engine.processLocation(LocationSample(37.7950, -122.4194, arriveAtB, 0f, 8f))
        engine.processLocation(LocationSample(37.7950, -122.4194, arriveAtB + 50_000L, 0f, 8f))
        assertEquals(TrackerPhase.DWELLING, engine.snapshot(arriveAtB + 50_000L).phase)

        // Periodic ticker hits 15 minutes + 1s after arriveAtB
        val completed = engine.onTick(arriveAtB + 15 * 60_000L + 1_000L)
        assertNotNull(completed)
        assertEquals(TravelMode.CAR, completed!!.travelMode)
        assertEquals(150_000L, completed.durationMs)
        assertTrue(completed.distanceMeters > 2000.0)
        assertEquals(TrackerPhase.AT_DESTINATION, engine.snapshot(arriveAtB + 15 * 60_000L + 1_000L).phase)

        // Depart from B later -> begins a brand new leg
        val departB = arriveAtB + 25 * 60_000L
        engine.processLocation(LocationSample(37.7950, -122.4120, departB, 5.0f, 8f))
        assertEquals(TrackerPhase.MOVING, engine.snapshot(departB).phase)
    }

    @Test
    fun `inaccurate gps fixes are filtered out`() {
        val engine = TripTrackerEngine()
        engine.startTracking()

        val t0 = 1_700_000_000_000L
        engine.processLocation(LocationSample(37.7749, -122.4194, t0, 0f, 10f))
        // Wild 2km inaccurate jump with 120m accuracy -> ignored
        engine.processLocation(LocationSample(37.7949, -122.4194, t0 + 5_000L, 20f, 120f))

        val state = engine.snapshot(t0 + 5_000L)
        assertEquals(TrackerPhase.IDLE, state.phase)
        assertEquals(0.0, state.activeDistanceMeters, 0.01)
    }
}
