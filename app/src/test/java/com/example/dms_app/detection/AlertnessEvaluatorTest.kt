package com.example.dms_app.detection

import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [AlertnessEvaluator].
 *
 * Verifies alertness state transitions across driver gaze, head orientation,
 * eye closure duration, absence of face telemetry, dynamic configuration updates,
 * and state resets.
 */
class AlertnessEvaluatorTest {

    private lateinit var evaluator: AlertnessEvaluator
    private val defaultSettings = DmsSettings(
        eyeOpenThreshold = 0.35f,
        drowsyWarningDurationMs = 1000L,
        drowsyCriticalDurationMs = 1800L,
        yawThresholdDegrees = 28f,
        pitchThresholdDegrees = 22f,
        distractionWarningDurationMs = 1500L,
        distractionCriticalDurationMs = 2500L
    )

    /**
     * Initializes a fresh [AlertnessEvaluator] before each test execution.
     */
    @Before
    fun setUp() {
        evaluator = AlertnessEvaluator(defaultSettings)
    }

    /**
     * Verifies that when a driver is attentive with open eyes and forward head pose,
     * the evaluator returns [AlertLevel.NORMAL] and zero elapsed alert durations.
     */
    @Test
    fun testNormalStateWhenDriverAlertAndAttentive() {
        val state: AlertnessState = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.90f,
            rightEyeOpen = 0.85f,
            yaw = 0.0f,
            pitch = 0.0f,
            now = 1000L,
            currentFps = 30
        )

        assertTrue(state.faceDetected)
        assertEquals(AlertLevel.NORMAL, state.alertLevel)
        assertEquals("Driver Alert", state.statusMessage)
        assertEquals(0L, state.eyeClosedDurationMs)
        assertEquals(0L, state.distractedDurationMs)
        assertEquals(30, state.fps)
        assertFalse(state.isAlertTriggered)
        assertFalse(state.isCritical)
    }

    /**
     * Verifies that short eye closures under 300ms do not trigger drowsy warnings
     * and reset the eye closure timer upon reopening.
     */
    @Test
    fun testShortBlinkResetsTimerWithoutTriggeringWarning() {
        // Driver eyes open initially
        val stateOpen = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = 0f,
            now = 1000L
        )
        assertEquals(AlertLevel.NORMAL, stateOpen.alertLevel)
        assertEquals(0L, stateOpen.eyeClosedDurationMs)

        // Eyes close for a blink at t=1100L
        val stateBlinkStart = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = 1100L
        )
        assertEquals(AlertLevel.NORMAL, stateBlinkStart.alertLevel)
        assertEquals(0L, stateBlinkStart.eyeClosedDurationMs)

        // 200ms elapsed during blink (less than 300ms)
        val stateMidBlink = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = 1300L
        )
        assertEquals(AlertLevel.NORMAL, stateMidBlink.alertLevel)
        assertEquals(200L, stateMidBlink.eyeClosedDurationMs)

        // Eyes reopen at t=1350L (blink finished)
        val stateReopened = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.85f,
            rightEyeOpen = 0.85f,
            yaw = 0f,
            pitch = 0f,
            now = 1350L
        )
        assertEquals(AlertLevel.NORMAL, stateReopened.alertLevel)
        assertEquals(0L, stateReopened.eyeClosedDurationMs)

        // New closure at t=2000L should start afresh from 0ms, not accumulate prior blink
        val stateSubsequentClosure = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = 2000L
        )
        assertEquals(AlertLevel.NORMAL, stateSubsequentClosure.alertLevel)
        assertEquals(0L, stateSubsequentClosure.eyeClosedDurationMs)
    }

    /**
     * Verifies that prolonged eye closure progresses through WARNING_DROWSY (> 1000ms)
     * and CRITICAL_DROWSY (> 1800ms) thresholds.
     */
    @Test
    fun testProlongedEyeClosureTriggersWarningAndCriticalDrowsy() {
        val start = 1000L

        // Eyes closed at t=1000L
        val state0 = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start
        )
        assertEquals(AlertLevel.NORMAL, state0.alertLevel)
        assertEquals(0L, state0.eyeClosedDurationMs)

        // At 999ms elapsed, still normal
        val stateJustBeforeWarning = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 999L
        )
        assertEquals(AlertLevel.NORMAL, stateJustBeforeWarning.alertLevel)
        assertEquals(999L, stateJustBeforeWarning.eyeClosedDurationMs)

        // At 1000ms elapsed, triggers WARNING_DROWSY
        val stateWarning = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 1000L
        )
        assertEquals(AlertLevel.WARNING_DROWSY, stateWarning.alertLevel)
        assertEquals("Warning: Eyes closed!", stateWarning.statusMessage)
        assertEquals(1000L, stateWarning.eyeClosedDurationMs)
        assertTrue(stateWarning.isAlertTriggered)
        assertFalse(stateWarning.isCritical)

        // At 1799ms elapsed, still WARNING_DROWSY
        val stateJustBeforeCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 1799L
        )
        assertEquals(AlertLevel.WARNING_DROWSY, stateJustBeforeCritical.alertLevel)

        // At 1800ms elapsed, triggers CRITICAL_DROWSY
        val stateCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 1800L
        )
        assertEquals(AlertLevel.CRITICAL_DROWSY, stateCritical.alertLevel)
        assertEquals("WAKE UP! EYES CLOSED!", stateCritical.statusMessage)
        assertEquals(1800L, stateCritical.eyeClosedDurationMs)
        assertTrue(stateCritical.isAlertTriggered)
        assertTrue(stateCritical.isCritical)

        // Reopening eyes recovers to NORMAL
        val stateRecovered = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.8f,
            rightEyeOpen = 0.8f,
            yaw = 0f,
            pitch = 0f,
            now = start + 2000L
        )
        assertEquals(AlertLevel.NORMAL, stateRecovered.alertLevel)
        assertEquals("Driver Alert", stateRecovered.statusMessage)
        assertEquals(0L, stateRecovered.eyeClosedDurationMs)
    }

    /**
     * Verifies that closure of a single eye while the other remains open does not trigger
     * drowsiness alerts when both eye metrics are available.
     */
    @Test
    fun testSingleEyeClosureBehaviorWhenBothEyesReported() {
        val start = 1000L

        // Left eye closed (0.1f), right eye open (0.8f) - e.g. winking or asymmetrical expression
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.8f,
            yaw = 0f,
            pitch = 0f,
            now = start
        )
        val stateLeftClosed = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.8f,
            yaw = 0f,
            pitch = 0f,
            now = start + 2000L
        )
        assertEquals(AlertLevel.NORMAL, stateLeftClosed.alertLevel)
        assertEquals(0L, stateLeftClosed.eyeClosedDurationMs)

        // Right eye closed (0.1f), left eye open (0.8f)
        val stateRightClosed = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.8f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 4000L
        )
        assertEquals(AlertLevel.NORMAL, stateRightClosed.alertLevel)
        assertEquals(0L, stateRightClosed.eyeClosedDurationMs)
    }

    /**
     * Verifies single eye closure behavior when the opposite eye is occluded (null probability).
     */
    @Test
    fun testSingleEyeClosureBehaviorWhenOneEyeOccluded() {
        val start = 1000L

        // Left eye closed, right eye null (occluded)
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = null,
            yaw = 0f,
            pitch = 0f,
            now = start
        )
        val stateLeftOnly = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = null,
            yaw = 0f,
            pitch = 0f,
            now = start + 1000L
        )
        assertEquals(AlertLevel.WARNING_DROWSY, stateLeftOnly.alertLevel)

        evaluator.reset()

        // Right eye closed, left eye null (occluded)
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = null,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start
        )
        val stateRightOnly = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = null,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 1800L
        )
        assertEquals(AlertLevel.CRITICAL_DROWSY, stateRightOnly.alertLevel)

        // Both eyes null should not register as closed
        evaluator.reset()
        val stateBothNull = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = 0f,
            pitch = 0f,
            now = start + 2000L
        )
        assertEquals(AlertLevel.NORMAL, stateBothNull.alertLevel)
        assertEquals(0L, stateBothNull.eyeClosedDurationMs)
    }

    /**
     * Verifies that head yaw exceeding thresholds in either direction (looking right or left)
     * triggers WARNING_DISTRACTED (> 1500ms) and CRITICAL_DISTRACTED (> 2500ms).
     */
    @Test
    fun testHeadYawLeftAndRightExceedingThresholdTriggersDistraction() {
        val start = 1000L

        // Looking right (negative yaw, e.g. -35 degrees > threshold 28 degrees)
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = -35f,
            pitch = 0f,
            now = start
        )
        val stateYawRightWarning = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = -35f,
            pitch = 0f,
            now = start + 1500L
        )
        assertEquals(AlertLevel.WARNING_DISTRACTED, stateYawRightWarning.alertLevel)
        assertEquals("Please focus on the road!", stateYawRightWarning.statusMessage)
        assertEquals(1500L, stateYawRightWarning.distractedDurationMs)

        val stateYawRightCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = -35f,
            pitch = 0f,
            now = start + 2500L
        )
        assertEquals(AlertLevel.CRITICAL_DISTRACTED, stateYawRightCritical.alertLevel)
        assertEquals("DANGER: LOOK AT ROAD!", stateYawRightCritical.statusMessage)
        assertEquals(2500L, stateYawRightCritical.distractedDurationMs)

        // Center head resets distraction
        val stateYawCentered = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 5f,
            pitch = 0f,
            now = start + 2600L
        )
        assertEquals(AlertLevel.NORMAL, stateYawCentered.alertLevel)
        assertEquals(0L, stateYawCentered.distractedDurationMs)

        // Looking left (positive yaw, e.g. +35 degrees > threshold 28 degrees)
        val leftStart = 5000L
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 35f,
            pitch = 0f,
            now = leftStart
        )
        val stateYawLeftWarning = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 35f,
            pitch = 0f,
            now = leftStart + 1500L
        )
        assertEquals(AlertLevel.WARNING_DISTRACTED, stateYawLeftWarning.alertLevel)

        val stateYawLeftCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 35f,
            pitch = 0f,
            now = leftStart + 2500L
        )
        assertEquals(AlertLevel.CRITICAL_DISTRACTED, stateYawLeftCritical.alertLevel)
    }

    /**
     * Verifies that head pitch exceeding thresholds (looking down or looking up)
     * triggers WARNING_DISTRACTED (> 1500ms) and CRITICAL_DISTRACTED (> 2500ms).
     */
    @Test
    fun testHeadPitchUpAndDownExceedingThresholdTriggersDistraction() {
        val start = 1000L

        // Looking down (negative pitch, e.g. -25 degrees > threshold 22 degrees)
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = -25f,
            now = start
        )
        val statePitchDownWarning = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = -25f,
            now = start + 1500L
        )
        assertEquals(AlertLevel.WARNING_DISTRACTED, statePitchDownWarning.alertLevel)

        val statePitchDownCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = -25f,
            now = start + 2500L
        )
        assertEquals(AlertLevel.CRITICAL_DISTRACTED, statePitchDownCritical.alertLevel)

        // Return pitch to level (0 degrees)
        val statePitchLevel = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = 0f,
            now = start + 2600L
        )
        assertEquals(AlertLevel.NORMAL, statePitchLevel.alertLevel)
        assertEquals(0L, statePitchLevel.distractedDurationMs)

        // Looking up (positive pitch, e.g. +26 degrees > threshold 22 degrees)
        val upStart = 5000L
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = 26f,
            now = upStart
        )
        val statePitchUpWarning = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = 26f,
            now = upStart + 1500L
        )
        assertEquals(AlertLevel.WARNING_DISTRACTED, statePitchUpWarning.alertLevel)

        val statePitchUpCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = 26f,
            now = upStart + 2500L
        )
        assertEquals(AlertLevel.CRITICAL_DISTRACTED, statePitchUpCritical.alertLevel)
    }

    /**
     * Verifies that subtle head orientation within tolerance does not trigger distraction.
     */
    @Test
    fun testHeadAnglesWithinThresholdRemainNormal() {
        val start = 1000L
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 15f,
            pitch = 10f,
            now = start
        )
        val stateWithinTolerance = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 20f,
            pitch = 15f,
            now = start + 3000L
        )
        assertEquals(AlertLevel.NORMAL, stateWithinTolerance.alertLevel)
        assertEquals(0L, stateWithinTolerance.distractedDurationMs)
    }

    /**
     * Verifies that absence of a detected face transitions from FACE_NOT_DETECTED initially
     * to WARNING_DISTRACTED once the duration exceeds distractionWarningDurationMs (> 1500ms).
     */
    @Test
    fun testNoFaceTimeoutTransitionsFromNotDetectedToDistracted() {
        val start = 1000L

        // Initial missing face frame
        val stateInitial = evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start
        )
        assertFalse(stateInitial.faceDetected)
        assertEquals(AlertLevel.FACE_NOT_DETECTED, stateInitial.alertLevel)
        assertEquals("Searching for driver's face...", stateInitial.statusMessage)

        // Missing face at 1500ms elapsed (threshold is > 1500ms)
        val stateAtThreshold = evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start + 1500L
        )
        assertEquals(AlertLevel.FACE_NOT_DETECTED, stateAtThreshold.alertLevel)
        assertEquals("Searching for driver's face...", stateAtThreshold.statusMessage)

        // Missing face exceeding threshold at 1501ms elapsed
        val stateAfterThreshold = evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start + 1501L
        )
        assertFalse(stateAfterThreshold.faceDetected)
        assertEquals(AlertLevel.WARNING_DISTRACTED, stateAfterThreshold.alertLevel)
        assertEquals("Driver not detected! Watch road!", stateAfterThreshold.statusMessage)
    }

    /**
     * Verifies that when a face reappears after being absent or distracted,
     * the state immediately resets timers and returns to NORMAL.
     */
    @Test
    fun testFaceReappearanceResetsTimers() {
        val start = 1000L

        // Face missing until it triggers WARNING_DISTRACTED
        evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start
        )
        val missingWarning = evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start + 2000L
        )
        assertEquals(AlertLevel.WARNING_DISTRACTED, missingWarning.alertLevel)

        // Face reappears alert and attentive
        val reappeared = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 0f,
            pitch = 0f,
            now = start + 2500L
        )
        assertTrue(reappeared.faceDetected)
        assertEquals(AlertLevel.NORMAL, reappeared.alertLevel)
        assertEquals("Driver Alert", reappeared.statusMessage)

        // If face drops out again later, timer starts fresh from new drop timestamp
        val newDrop = evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start + 5000L
        )
        assertEquals(AlertLevel.FACE_NOT_DETECTED, newDrop.alertLevel)
    }

    /**
     * Verifies priority resolution when both drowsiness and distraction conditions occur concurrently.
     */
    @Test
    fun testPriorityResolutionWhenBothDrowsyAndDistracted() {
        val start = 1000L

        // Scenario 1: Both reach critical duration
        // Eyes closed for 2500ms (>= 1800ms) and yaw excessive for 2500ms (>= 2500ms)
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 35f,
            pitch = 0f,
            now = start
        )
        val bothCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 35f,
            pitch = 0f,
            now = start + 2500L
        )
        // Drowsy critical has priority over distraction critical
        assertEquals(AlertLevel.CRITICAL_DROWSY, bothCritical.alertLevel)
        assertEquals("WAKE UP! EYES CLOSED!", bothCritical.statusMessage)

        evaluator.reset()

        // Scenario 2: Both reach warning duration (1500ms elapsed)
        // eyeClosedDuration = 1500ms (>= 1000ms), distractionDuration = 1500ms (>= 1500ms)
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 35f,
            pitch = 0f,
            now = start
        )
        val bothWarning = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 35f,
            pitch = 0f,
            now = start + 1500L
        )
        // Drowsy warning is evaluated ahead of distraction warning
        assertEquals(AlertLevel.WARNING_DROWSY, bothWarning.alertLevel)
        assertEquals("Warning: Eyes closed!", bothWarning.statusMessage)

        evaluator.reset()

        // Scenario 3: Drowsy warning (1000ms) active while distraction is critical (2500ms)
        // Head turned at t=1000L
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 35f,
            pitch = 0f,
            now = start
        )
        // Eyes closed later at t=2500L
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 35f,
            pitch = 0f,
            now = start + 1500L
        )
        // At t=3500L: distractionDuration = 2500ms (critical), eyeClosedDuration = 1000ms (warning)
        val drowsyWarningDistractionCritical = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 35f,
            pitch = 0f,
            now = start + 2500L
        )
        assertEquals(AlertLevel.WARNING_DROWSY, drowsyWarningDistractionCritical.alertLevel)
    }

    /**
     * Verifies that updating settings via [AlertnessEvaluator.updateSettings] dynamically
     * modifies threshold evaluation.
     */
    @Test
    fun testSettingsUpdatesModifyingThresholdsDynamically() {
        val start = 1000L

        // With default eyeOpenThreshold = 0.35f, value 0.40f is considered open
        val defaultState = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.40f,
            rightEyeOpen = 0.40f,
            yaw = 0f,
            pitch = 0f,
            now = start
        )
        assertEquals(AlertLevel.NORMAL, defaultState.alertLevel)
        assertEquals(0L, defaultState.eyeClosedDurationMs)

        // Update settings to stricter threshold 0.50f and shorter warning duration 500ms
        val customSettings = defaultSettings.copy(
            eyeOpenThreshold = 0.50f,
            drowsyWarningDurationMs = 500L
        )
        evaluator.updateSettings(customSettings)

        // With new settings, 0.40f is considered closed (< 0.50f)
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.40f,
            rightEyeOpen = 0.40f,
            yaw = 0f,
            pitch = 0f,
            now = start + 100L
        )
        val updatedWarningState = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.40f,
            rightEyeOpen = 0.40f,
            yaw = 0f,
            pitch = 0f,
            now = start + 650L
        )
        // 550ms elapsed >= new 500ms threshold
        assertEquals(AlertLevel.WARNING_DROWSY, updatedWarningState.alertLevel)
        assertEquals(550L, updatedWarningState.eyeClosedDurationMs)
    }

    /**
     * Verifies that calling [AlertnessEvaluator.reset] clears all tracked state timers.
     */
    @Test
    fun testResetClearingAllStateTimers() {
        val start = 1000L

        // Trigger drowsiness warning
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start
        )
        val preResetState = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 1200L
        )
        assertEquals(AlertLevel.WARNING_DROWSY, preResetState.alertLevel)

        // Reset the evaluator
        evaluator.reset()

        // Immediate next frame with closed eyes restarts timer from 0
        val postResetState = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.1f,
            rightEyeOpen = 0.1f,
            yaw = 0f,
            pitch = 0f,
            now = start + 1300L
        )
        assertEquals(AlertLevel.NORMAL, postResetState.alertLevel)
        assertEquals(0L, postResetState.eyeClosedDurationMs)

        // Distraction timer reset
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 35f,
            pitch = 0f,
            now = start + 2000L
        )
        evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 35f,
            pitch = 0f,
            now = start + 3600L
        )
        evaluator.reset()
        val postResetDistraction = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f,
            yaw = 35f,
            pitch = 0f,
            now = start + 3700L
        )
        assertEquals(AlertLevel.NORMAL, postResetDistraction.alertLevel)
        assertEquals(0L, postResetDistraction.distractedDurationMs)

        // No-face timer reset
        evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start + 4000L
        )
        evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start + 5600L
        )
        evaluator.reset()
        val postResetNoFace = evaluator.evaluate(
            faceDetected = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            yaw = null,
            pitch = null,
            now = start + 5700L
        )
        assertEquals(AlertLevel.FACE_NOT_DETECTED, postResetNoFace.alertLevel)
    }
}
