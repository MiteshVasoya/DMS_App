package com.example.dms_app.detection

import com.example.dms_app.model.DmsSettings
import com.example.dms_app.model.MotionEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests validating vehicle motion processing, debounce filters, and crash detection.
 */
class VehicleMotionDetectorTest {

    private lateinit var detector: VehicleMotionDetector
    private var lastReading: VehicleMotionReading? = null

    @Before
    fun setUp() {
        lastReading = null
        detector = VehicleMotionDetector(
            context = null,
            settings = DmsSettings(
                rapidAccelThresholdMps2 = 3.0f,
                harshBrakingThresholdMps2 = 4.0f,
                crashThresholdGForce = 3.0f,
                isMotionDetectionEnabled = true
            ),
            onMotionReading = { reading ->
                lastReading = reading
            }
        )
    }

    @Test
    fun `processLinearAcceleration returns NORMAL for benign gentle motion`() {
        val reading = detector.processLinearAcceleration(ax = 0.2f, ay = 0.5f, az = 0.1f, timestampMs = 1000L)

        assertEquals(MotionEventType.NORMAL, reading.motionEvent)
        assertTrue(reading.accelerationMps2 < 1.0f)
        assertEquals(reading, lastReading)
    }

    @Test
    fun `processLinearAcceleration requires debounce sustain for rapid acceleration`() {
        // First instantaneous spike (e.g. at t = 1000ms)
        val initialReading = detector.processLinearAcceleration(ax = 0f, ay = 3.5f, az = 0f, timestampMs = 1000L)
        // Should be NORMAL initially to prevent single bump false positives
        assertEquals(MotionEventType.NORMAL, initialReading.motionEvent)

        // Still below MIN_SUSTAINED_EVENT_DURATION_MS (250ms)
        val midReading = detector.processLinearAcceleration(ax = 0f, ay = 3.5f, az = 0f, timestampMs = 1150L)
        assertEquals(MotionEventType.NORMAL, midReading.motionEvent)

        // Sustained at or past 250ms (t = 1260ms)
        val sustainedReading = detector.processLinearAcceleration(ax = 0f, ay = 3.5f, az = 0f, timestampMs = 1260L)
        assertEquals(MotionEventType.RAPID_ACCELERATION, sustainedReading.motionEvent)
    }

    @Test
    fun `processLinearAcceleration triggers HARSH_BRAKING when sustained negative longitudinal deceleration exceeds threshold`() {
        // Harsh braking: ay <= -4.0 m/s^2
        detector.processLinearAcceleration(ax = 0f, ay = -4.5f, az = 0f, timestampMs = 1000L)
        val sustained = detector.processLinearAcceleration(ax = 0f, ay = -4.5f, az = 0f, timestampMs = 1260L)

        assertEquals(MotionEventType.HARSH_BRAKING, sustained.motionEvent)
    }

    @Test
    fun `processLinearAcceleration triggers CRASH_DETECTED immediately on high-G impact spike`() {
        // High-G collision impact: 3.5g (~34.3 m/s^2)
        val crashAccel = 3.5f * VehicleMotionDetector.STANDARD_GRAVITY
        val reading = detector.processLinearAcceleration(ax = crashAccel, ay = 0f, az = 0f, timestampMs = 1000L)

        assertEquals(MotionEventType.CRASH_DETECTED, reading.motionEvent)
        assertTrue(reading.gForce >= 3.0f)
    }

    @Test
    fun `crash cooldown prevents immediate repeat crash alerts`() {
        val crashAccel = 3.5f * VehicleMotionDetector.STANDARD_GRAVITY
        val first = detector.processLinearAcceleration(ax = crashAccel, ay = 0f, az = 0f, timestampMs = 1000L)
        assertEquals(MotionEventType.CRASH_DETECTED, first.motionEvent)

        // Another spike within 10 seconds cooldown without sustain defaults to debounce NORMAL
        val withinCooldownInstant = detector.processLinearAcceleration(ax = crashAccel, ay = 0f, az = 0f, timestampMs = 2000L)
        assertEquals(MotionEventType.NORMAL, withinCooldownInstant.motionEvent)

        // If sustained across MIN_SUSTAINED_EVENT_DURATION_MS during cooldown, falls back to HARSH_BRAKING
        val withinCooldownSustained = detector.processLinearAcceleration(ax = crashAccel, ay = 0f, az = 0f, timestampMs = 2300L)
        assertEquals(MotionEventType.HARSH_BRAKING, withinCooldownSustained.motionEvent)
    }
}
