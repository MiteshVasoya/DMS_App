package com.example.dms_app.model

/**
 * Severity level of driver inattention or drowsiness.
 */
enum class AlertLevel {
    NORMAL,
    WARNING_DROWSY,
    CRITICAL_DROWSY,
    WARNING_DISTRACTED,
    CRITICAL_DISTRACTED,
    FACE_NOT_DETECTED
}

/**
 * Driver alertness telemetry and analysis state.
 */
data class AlertnessState(
    val faceDetected: Boolean = false,
    val leftEyeOpenProb: Float? = null,
    val rightEyeOpenProb: Float? = null,
    val headEulerAngleY: Float? = null, // Yaw: negative is looking right, positive is looking left
    val headEulerAngleX: Float? = null, // Pitch: negative is looking down, positive is looking up
    val eyeClosedDurationMs: Long = 0L,
    val distractedDurationMs: Long = 0L,
    val alertLevel: AlertLevel = AlertLevel.NORMAL,
    val statusMessage: String = "Monitoring active",
    val fps: Int = 0
) {
    val isAlertTriggered: Boolean
        get() = alertLevel != AlertLevel.NORMAL && alertLevel != AlertLevel.FACE_NOT_DETECTED

    val isCritical: Boolean
        get() = alertLevel == AlertLevel.CRITICAL_DROWSY || alertLevel == AlertLevel.CRITICAL_DISTRACTED
}

/**
 * Configurable sensitivity thresholds for the DMS.
 */
data class DmsSettings(
    val eyeOpenThreshold: Float = 0.35f,
    val drowsyWarningDurationMs: Long = 1000L,
    val drowsyCriticalDurationMs: Long = 1800L,
    val yawThresholdDegrees: Float = 28f,
    val pitchThresholdDegrees: Float = 22f,
    val distractionWarningDurationMs: Long = 1500L,
    val distractionCriticalDurationMs: Long = 2500L,
    val isAudioEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true
)
