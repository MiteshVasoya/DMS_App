package com.example.dms_app.detection

import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import kotlin.math.abs

/**
 * Pure Kotlin state machine for evaluating driver alertness telemetry.
 *
 * Tracks cumulative eye closure and head rotation durations across frames to detect
 * drowsiness and distraction according to configured [DmsSettings] thresholds.
 *
 * @param settings Configurable sensitivity thresholds for alertness detection.
 */
class AlertnessEvaluator(
    private var settings: DmsSettings = DmsSettings()
) {

    // Tracking timestamps
    private var eyeClosedStartTime: Long? = null
    private var distractionStartTime: Long? = null
    private var noFaceStartTime: Long? = null

    /**
     * Evaluates face telemetry for a single frame and returns the updated [AlertnessState].
     *
     * @param faceDetected Whether a driver face was detected in the current frame.
     * @param leftEyeOpen Driver's left eye open probability (0.0 to 1.0), or null if not available.
     * @param rightEyeOpen Driver's right eye open probability (0.0 to 1.0), or null if not available.
     * @param yaw Driver's head Euler Y angle (yaw) in degrees, or null if not available.
     * @param pitch Driver's head Euler X angle (pitch) in degrees, or null if not available.
     * @param now Current timestamp in milliseconds (typically elapsed realtime).
     * @param currentFps Current frame processing rate in frames per second.
     * @return The resulting [AlertnessState] representing the driver's alertness level.
     */
    fun evaluate(
        faceDetected: Boolean,
        leftEyeOpen: Float?,
        rightEyeOpen: Float?,
        yaw: Float?,
        pitch: Float?,
        now: Long,
        currentFps: Int = 0
    ): AlertnessState {
        if (!faceDetected) {
            if (noFaceStartTime == null) {
                noFaceStartTime = now
            }
            eyeClosedStartTime = null
            distractionStartTime = null

            val noFaceDuration = now - (noFaceStartTime ?: now)
            val alertLevel = if (noFaceDuration > settings.distractionWarningDurationMs) {
                AlertLevel.WARNING_DISTRACTED
            } else {
                AlertLevel.FACE_NOT_DETECTED
            }

            return AlertnessState(
                faceDetected = false,
                alertLevel = alertLevel,
                statusMessage = if (noFaceDuration > settings.distractionWarningDurationMs) {
                    "Driver not detected! Watch road!"
                } else {
                    "Searching for driver's face..."
                },
                fps = currentFps
            )
        }

        noFaceStartTime = null

        // 1. Drowsiness Check: Eyes closed threshold
        val areEyesClosed = if (leftEyeOpen != null && rightEyeOpen != null) {
            leftEyeOpen < settings.eyeOpenThreshold && rightEyeOpen < settings.eyeOpenThreshold
        } else if (leftEyeOpen != null) {
            leftEyeOpen < settings.eyeOpenThreshold
        } else if (rightEyeOpen != null) {
            rightEyeOpen < settings.eyeOpenThreshold
        } else {
            false
        }

        var eyeClosedDuration = 0L
        if (areEyesClosed) {
            if (eyeClosedStartTime == null) {
                eyeClosedStartTime = now
            }
            eyeClosedDuration = now - (eyeClosedStartTime ?: now)
        } else {
            eyeClosedStartTime = null
        }

        // 2. Distraction Check: Head yaw or pitch excessive
        val isHeadTurned = (yaw != null && abs(yaw) > settings.yawThresholdDegrees) ||
                (pitch != null && abs(pitch) > settings.pitchThresholdDegrees)

        var distractionDuration = 0L
        if (isHeadTurned) {
            if (distractionStartTime == null) {
                distractionStartTime = now
            }
            distractionDuration = now - (distractionStartTime ?: now)
        } else {
            distractionStartTime = null
        }

        // Determine alertness level & status message
        val (level, message) = when {
            eyeClosedDuration >= settings.drowsyCriticalDurationMs -> {
                AlertLevel.CRITICAL_DROWSY to "WAKE UP! EYES CLOSED!"
            }
            eyeClosedDuration >= settings.drowsyWarningDurationMs -> {
                AlertLevel.WARNING_DROWSY to "Warning: Eyes closed!"
            }
            distractionDuration >= settings.distractionCriticalDurationMs -> {
                AlertLevel.CRITICAL_DISTRACTED to "DANGER: LOOK AT ROAD!"
            }
            distractionDuration >= settings.distractionWarningDurationMs -> {
                AlertLevel.WARNING_DISTRACTED to "Please focus on the road!"
            }
            else -> {
                AlertLevel.NORMAL to "Driver Alert"
            }
        }

        return AlertnessState(
            faceDetected = true,
            leftEyeOpenProb = leftEyeOpen,
            rightEyeOpenProb = rightEyeOpen,
            headEulerAngleY = yaw,
            headEulerAngleX = pitch,
            eyeClosedDurationMs = eyeClosedDuration,
            distractedDurationMs = distractionDuration,
            alertLevel = level,
            statusMessage = message,
            fps = currentFps
        )
    }

    /**
     * Updates the detection thresholds with [newSettings].
     *
     * @param newSettings Updated DMS configuration settings.
     */
    fun updateSettings(newSettings: DmsSettings) {
        settings = newSettings
    }

    /**
     * Resets all internal timer states and tracking counters.
     */
    fun reset() {
        eyeClosedStartTime = null
        distractionStartTime = null
        noFaceStartTime = null
    }
}
