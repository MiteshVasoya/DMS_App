package com.example.dms_app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying default construction, alert predicates, telemetry mutations,
 * and data class behavior for [AlertnessState].
 */
class AlertnessStateTest {

    /**
     * Verifies that the default constructor of [AlertnessState] initializes all telemetry
     * properties, durations, alert levels, and derived flags to their intended defaults.
     */
    @Test
    fun testDefaultConstructorValues() {
        val state = AlertnessState()

        assertFalse(state.faceDetected)
        assertNull(state.leftEyeOpenProb)
        assertNull(state.rightEyeOpenProb)
        assertNull(state.headEulerAngleY)
        assertNull(state.headEulerAngleX)
        assertEquals(0L, state.eyeClosedDurationMs)
        assertEquals(0L, state.distractedDurationMs)
        assertEquals(AlertLevel.NORMAL, state.alertLevel)
        assertEquals("Monitoring active", state.statusMessage)
        assertEquals(0, state.fps)
        assertFalse(state.isAlertTriggered)
        assertFalse(state.isCritical)
    }

    /**
     * Verifies that [AlertnessState.isAlertTriggered] returns false when [AlertLevel.NORMAL] is set.
     */
    @Test
    fun testIsAlertTriggeredReturnsFalseForNormal() {
        val state = AlertnessState(alertLevel = AlertLevel.NORMAL)
        assertFalse(state.isAlertTriggered)
    }

    /**
     * Verifies that [AlertnessState.isAlertTriggered] returns false when [AlertLevel.FACE_NOT_DETECTED] is set.
     */
    @Test
    fun testIsAlertTriggeredReturnsFalseForFaceNotDetected() {
        val state = AlertnessState(alertLevel = AlertLevel.FACE_NOT_DETECTED)
        assertFalse(state.isAlertTriggered)
    }

    /**
     * Verifies that [AlertnessState.isAlertTriggered] returns true when [AlertLevel.WARNING_DROWSY] is set.
     */
    @Test
    fun testIsAlertTriggeredReturnsTrueForWarningDrowsy() {
        val state = AlertnessState(alertLevel = AlertLevel.WARNING_DROWSY)
        assertTrue(state.isAlertTriggered)
    }

    /**
     * Verifies that [AlertnessState.isAlertTriggered] returns true when [AlertLevel.CRITICAL_DROWSY] is set.
     */
    @Test
    fun testIsAlertTriggeredReturnsTrueForCriticalDrowsy() {
        val state = AlertnessState(alertLevel = AlertLevel.CRITICAL_DROWSY)
        assertTrue(state.isAlertTriggered)
    }

    /**
     * Verifies that [AlertnessState.isAlertTriggered] returns true when [AlertLevel.WARNING_DISTRACTED] is set.
     */
    @Test
    fun testIsAlertTriggeredReturnsTrueForWarningDistracted() {
        val state = AlertnessState(alertLevel = AlertLevel.WARNING_DISTRACTED)
        assertTrue(state.isAlertTriggered)
    }

    /**
     * Verifies that [AlertnessState.isAlertTriggered] returns true when [AlertLevel.CRITICAL_DISTRACTED] is set.
     */
    @Test
    fun testIsAlertTriggeredReturnsTrueForCriticalDistracted() {
        val state = AlertnessState(alertLevel = AlertLevel.CRITICAL_DISTRACTED)
        assertTrue(state.isAlertTriggered)
    }

    /**
     * Verifies that [AlertnessState.isCritical] returns false when [AlertLevel.NORMAL] is set.
     */
    @Test
    fun testIsCriticalReturnsFalseForNormal() {
        val state = AlertnessState(alertLevel = AlertLevel.NORMAL)
        assertFalse(state.isCritical)
    }

    /**
     * Verifies that [AlertnessState.isCritical] returns false when [AlertLevel.FACE_NOT_DETECTED] is set.
     */
    @Test
    fun testIsCriticalReturnsFalseForFaceNotDetected() {
        val state = AlertnessState(alertLevel = AlertLevel.FACE_NOT_DETECTED)
        assertFalse(state.isCritical)
    }

    /**
     * Verifies that [AlertnessState.isCritical] returns false when [AlertLevel.WARNING_DROWSY] is set.
     */
    @Test
    fun testIsCriticalReturnsFalseForWarningDrowsy() {
        val state = AlertnessState(alertLevel = AlertLevel.WARNING_DROWSY)
        assertFalse(state.isCritical)
    }

    /**
     * Verifies that [AlertnessState.isCritical] returns true when [AlertLevel.CRITICAL_DROWSY] is set.
     */
    @Test
    fun testIsCriticalReturnsTrueForCriticalDrowsy() {
        val state = AlertnessState(alertLevel = AlertLevel.CRITICAL_DROWSY)
        assertTrue(state.isCritical)
    }

    /**
     * Verifies that [AlertnessState.isCritical] returns false when [AlertLevel.WARNING_DISTRACTED] is set.
     */
    @Test
    fun testIsCriticalReturnsFalseForWarningDistracted() {
        val state = AlertnessState(alertLevel = AlertLevel.WARNING_DISTRACTED)
        assertFalse(state.isCritical)
    }

    /**
     * Verifies that [AlertnessState.isCritical] returns true when [AlertLevel.CRITICAL_DISTRACTED] is set.
     */
    @Test
    fun testIsCriticalReturnsTrueForCriticalDistracted() {
        val state = AlertnessState(alertLevel = AlertLevel.CRITICAL_DISTRACTED)
        assertTrue(state.isCritical)
    }

    /**
     * Verifies that calling [.copy] updates specified telemetry fields while preserving
     * all untouched fields in the returned [AlertnessState] instance.
     */
    @Test
    fun testCopyUpdatesTelemetryFieldsCorrectly() {
        val initial = AlertnessState(
            faceDetected = true,
            leftEyeOpenProb = 0.85f,
            rightEyeOpenProb = 0.90f,
            headEulerAngleY = -5.0f,
            headEulerAngleX = 2.0f,
            eyeClosedDurationMs = 100L,
            distractedDurationMs = 200L,
            alertLevel = AlertLevel.NORMAL,
            statusMessage = "Driver alert",
            fps = 30
        )

        val updated = initial.copy(
            leftEyeOpenProb = 0.10f,
            rightEyeOpenProb = 0.12f,
            eyeClosedDurationMs = 1200L,
            alertLevel = AlertLevel.WARNING_DROWSY,
            statusMessage = "Drowsiness detected"
        )

        assertTrue(updated.faceDetected)
        assertEquals(0.10f, updated.leftEyeOpenProb ?: 0f, 0.001f)
        assertEquals(0.12f, updated.rightEyeOpenProb ?: 0f, 0.001f)
        assertEquals(-5.0f, updated.headEulerAngleY ?: 0f, 0.001f)
        assertEquals(2.0f, updated.headEulerAngleX ?: 0f, 0.001f)
        assertEquals(1200L, updated.eyeClosedDurationMs)
        assertEquals(200L, updated.distractedDurationMs)
        assertEquals(AlertLevel.WARNING_DROWSY, updated.alertLevel)
        assertEquals("Drowsiness detected", updated.statusMessage)
        assertEquals(30, updated.fps)
        assertTrue(updated.isAlertTriggered)
        assertFalse(updated.isCritical)
    }

    /**
     * Verifies that invoking [.copy] without arguments creates an instance equivalent
     * to the original [AlertnessState].
     */
    @Test
    fun testCopyPreservesUnmodifiedFields() {
        val original = AlertnessState(
            faceDetected = true,
            leftEyeOpenProb = 0.65f,
            rightEyeOpenProb = 0.70f,
            headEulerAngleY = 15.0f,
            headEulerAngleX = -10.0f,
            eyeClosedDurationMs = 500L,
            distractedDurationMs = 600L,
            alertLevel = AlertLevel.WARNING_DISTRACTED,
            statusMessage = "Distraction warning",
            fps = 28
        )

        val clone = original.copy()

        assertEquals(original, clone)
        assertEquals(original.hashCode(), clone.hashCode())
    }

    /**
     * Verifies the data class equals and hashCode contract for identical and differing
     * [AlertnessState] instances.
     */
    @Test
    fun testEqualityAndHashCodeContract() {
        val stateA = AlertnessState(
            faceDetected = true,
            leftEyeOpenProb = 0.8f,
            rightEyeOpenProb = 0.8f,
            headEulerAngleY = 0.0f,
            headEulerAngleX = 0.0f,
            eyeClosedDurationMs = 0L,
            distractedDurationMs = 0L,
            alertLevel = AlertLevel.NORMAL,
            statusMessage = "OK",
            fps = 30
        )
        val stateB = AlertnessState(
            faceDetected = true,
            leftEyeOpenProb = 0.8f,
            rightEyeOpenProb = 0.8f,
            headEulerAngleY = 0.0f,
            headEulerAngleX = 0.0f,
            eyeClosedDurationMs = 0L,
            distractedDurationMs = 0L,
            alertLevel = AlertLevel.NORMAL,
            statusMessage = "OK",
            fps = 30
        )
        val stateC = stateA.copy(alertLevel = AlertLevel.CRITICAL_DROWSY)

        assertEquals(stateA, stateB)
        assertEquals(stateA.hashCode(), stateB.hashCode())
        assertNotEquals(stateA, stateC)
        assertNotEquals(stateA.hashCode(), stateC.hashCode())
    }
}
