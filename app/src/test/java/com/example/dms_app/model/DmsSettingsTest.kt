package com.example.dms_app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying default configuration thresholds, custom threshold mutations,
 * boundary conditions, and data class behavior for [DmsSettings].
 */
class DmsSettingsTest {

    /**
     * Verifies that the default constructor of [DmsSettings] initializes all sensitivity
     * thresholds, duration limits, and toggle flags to production defaults.
     */
    @Test
    fun testDefaultConfigurationThresholds() {
        val settings = DmsSettings()

        assertEquals(0.35f, settings.eyeOpenThreshold, 0.001f)
        assertEquals(1000L, settings.drowsyWarningDurationMs)
        assertEquals(1800L, settings.drowsyCriticalDurationMs)
        assertEquals(28f, settings.yawThresholdDegrees, 0.001f)
        assertEquals(22f, settings.pitchThresholdDegrees, 0.001f)
        assertEquals(1500L, settings.distractionWarningDurationMs)
        assertEquals(2500L, settings.distractionCriticalDurationMs)
        assertTrue(settings.isAudioEnabled)
        assertTrue(settings.isVibrationEnabled)
        assertTrue(settings.isCameraPreviewVisible)
    }

    /**
     * Verifies that [DmsSettings] correctly stores and reflects explicit custom values
     * passed for every parameter.
     */
    @Test
    fun testCustomConfigurationValues() {
        val settings = DmsSettings(
            eyeOpenThreshold = 0.45f,
            drowsyWarningDurationMs = 800L,
            drowsyCriticalDurationMs = 1500L,
            yawThresholdDegrees = 35f,
            pitchThresholdDegrees = 25f,
            distractionWarningDurationMs = 2000L,
            distractionCriticalDurationMs = 3000L,
            isAudioEnabled = false,
            isVibrationEnabled = false,
            isCameraPreviewVisible = false
        )

        assertEquals(0.45f, settings.eyeOpenThreshold, 0.001f)
        assertEquals(800L, settings.drowsyWarningDurationMs)
        assertEquals(1500L, settings.drowsyCriticalDurationMs)
        assertEquals(35f, settings.yawThresholdDegrees, 0.001f)
        assertEquals(25f, settings.pitchThresholdDegrees, 0.001f)
        assertEquals(2000L, settings.distractionWarningDurationMs)
        assertEquals(3000L, settings.distractionCriticalDurationMs)
        assertFalse(settings.isAudioEnabled)
        assertFalse(settings.isVibrationEnabled)
        assertFalse(settings.isCameraPreviewVisible)
    }

    /**
     * Verifies that calling [.copy] updates selected settings while preserving unmodified
     * default settings.
     */
    @Test
    fun testCopyModifiesSpecifiedSettingsWhilePreservingOthers() {
        val defaults = DmsSettings()
        val modified = defaults.copy(
            eyeOpenThreshold = 0.50f,
            isAudioEnabled = false
        )

        assertEquals(0.50f, modified.eyeOpenThreshold, 0.001f)
        assertFalse(modified.isAudioEnabled)

        // Preserved defaults
        assertEquals(defaults.drowsyWarningDurationMs, modified.drowsyWarningDurationMs)
        assertEquals(defaults.drowsyCriticalDurationMs, modified.drowsyCriticalDurationMs)
        assertEquals(defaults.yawThresholdDegrees, modified.yawThresholdDegrees, 0.001f)
        assertEquals(defaults.pitchThresholdDegrees, modified.pitchThresholdDegrees, 0.001f)
        assertEquals(defaults.distractionWarningDurationMs, modified.distractionWarningDurationMs)
        assertEquals(defaults.distractionCriticalDurationMs, modified.distractionCriticalDurationMs)
        assertEquals(defaults.isVibrationEnabled, modified.isVibrationEnabled)
        assertEquals(defaults.isCameraPreviewVisible, modified.isCameraPreviewVisible)
    }

    /**
     * Verifies default ordering constraints between warning and critical duration thresholds,
     * ensuring that warning durations are strictly shorter than critical durations.
     */
    @Test
    fun testThresholdBoundaryDefaultOrderingConstraints() {
        val settings = DmsSettings()

        assertTrue(
            "Drowsy warning duration must be less than critical duration",
            settings.drowsyWarningDurationMs < settings.drowsyCriticalDurationMs
        )
        assertTrue(
            "Distraction warning duration must be less than critical duration",
            settings.distractionWarningDurationMs < settings.distractionCriticalDurationMs
        )
    }

    /**
     * Verifies threshold boundary checks for eye open probability thresholds at lower
     * (0.0f) and upper (1.0f) limits.
     */
    @Test
    fun testProbabilityThresholdBoundaries() {
        val minProbSettings = DmsSettings(eyeOpenThreshold = 0.0f)
        val maxProbSettings = DmsSettings(eyeOpenThreshold = 1.0f)

        assertEquals(0.0f, minProbSettings.eyeOpenThreshold, 0.001f)
        assertEquals(1.0f, maxProbSettings.eyeOpenThreshold, 0.001f)
    }

    /**
     * Verifies threshold boundary checks for angle thresholds including zero and large angles.
     */
    @Test
    fun testAngleThresholdBoundaries() {
        val zeroAngleSettings = DmsSettings(yawThresholdDegrees = 0f, pitchThresholdDegrees = 0f)
        val wideAngleSettings = DmsSettings(yawThresholdDegrees = 90f, pitchThresholdDegrees = 90f)

        assertEquals(0f, zeroAngleSettings.yawThresholdDegrees, 0.001f)
        assertEquals(0f, zeroAngleSettings.pitchThresholdDegrees, 0.001f)
        assertEquals(90f, wideAngleSettings.yawThresholdDegrees, 0.001f)
        assertEquals(90f, wideAngleSettings.pitchThresholdDegrees, 0.001f)
    }

    /**
     * Verifies that audio, vibration, and camera preview toggle flags operate independently.
     */
    @Test
    fun testToggleFlagsIndependentConfiguration() {
        val audioOnly = DmsSettings(isAudioEnabled = true, isVibrationEnabled = false, isCameraPreviewVisible = false)
        assertTrue(audioOnly.isAudioEnabled)
        assertFalse(audioOnly.isVibrationEnabled)
        assertFalse(audioOnly.isCameraPreviewVisible)

        val vibrationOnly = DmsSettings(isAudioEnabled = false, isVibrationEnabled = true, isCameraPreviewVisible = false)
        assertFalse(vibrationOnly.isAudioEnabled)
        assertTrue(vibrationOnly.isVibrationEnabled)
        assertFalse(vibrationOnly.isCameraPreviewVisible)

        val previewOnly = DmsSettings(isAudioEnabled = false, isVibrationEnabled = false, isCameraPreviewVisible = true)
        assertFalse(previewOnly.isAudioEnabled)
        assertFalse(previewOnly.isVibrationEnabled)
        assertTrue(previewOnly.isCameraPreviewVisible)
    }

    /**
     * Verifies the data class equals and hashCode contract for identical and differing
     * [DmsSettings] instances.
     */
    @Test
    fun testEqualityAndHashCodeContract() {
        val settingsA = DmsSettings()
        val settingsB = DmsSettings()
        val settingsC = DmsSettings(eyeOpenThreshold = 0.40f)

        assertEquals(settingsA, settingsB)
        assertEquals(settingsA.hashCode(), settingsB.hashCode())
        assertNotEquals(settingsA, settingsC)
        assertNotEquals(settingsA.hashCode(), settingsC.hashCode())
    }
}
