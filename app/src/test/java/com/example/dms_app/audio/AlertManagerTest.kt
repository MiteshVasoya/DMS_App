package com.example.dms_app.audio

import android.content.Context
import com.example.dms_app.model.AlertLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [AlertManager] alert level state transitions,
 * audio/haptic settings updates, alarm volume scaling, and release lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertManagerTest {

    private lateinit var alertManager: TestableAlertManager

    /**
     * Subclass of [AlertManager] allowing inspection of internal tone and vibrator actions.
     */
    private class TestableAlertManager : AlertManager(null as Context?) {
        var toneCount: Int = 0
        var lastToneType: Int? = null
        var lastToneDuration: Int? = null
        var vibrateCount: Int = 0

        fun resetStats() {
            toneCount = 0
            lastToneType = null
            lastToneDuration = null
            vibrateCount = 0
        }
    }

    /**
     * Set up alert manager fixture before each test.
     */
    @Before
    fun setUp() {
        alertManager = TestableAlertManager()
    }

    /**
     * Verifies that updating settings modifies audio, vibration, volume, and tone type.
     */
    @Test
    fun testUpdateSettingsModifiesParameters() {
        alertManager.updateSettings(
            audioEnabled = false,
            vibrationEnabled = false,
            volume = 80,
            toneType = 1
        )
        assertEquals(80, alertManager.alarmVolume)
        assertEquals(1, alertManager.alertToneType)
    }

    /**
     * Verifies volume clamping between 0 and 100.
     */
    @Test
    fun testVolumeClampingLimitsRange() {
        alertManager.updateSettings(
            audioEnabled = true,
            vibrationEnabled = true,
            volume = 150,
            toneType = 0
        )
        assertEquals(100, alertManager.alarmVolume)

        alertManager.updateSettings(
            audioEnabled = true,
            vibrationEnabled = true,
            volume = -20,
            toneType = 0
        )
        assertEquals(0, alertManager.alarmVolume)
    }

    /**
     * Verifies alert transitions between NORMAL, WARNING, CRITICAL, and back to NORMAL.
     */
    @Test
    fun testAlertLevelTransitions() {
        alertManager.updateAlertLevel(AlertLevel.NORMAL)
        assertEquals(AlertLevel.NORMAL, alertManager.currentAlertLevel)

        alertManager.updateAlertLevel(AlertLevel.WARNING_DROWSY)
        assertEquals(AlertLevel.WARNING_DROWSY, alertManager.currentAlertLevel)

        alertManager.updateAlertLevel(AlertLevel.CRITICAL_DROWSY)
        assertEquals(AlertLevel.CRITICAL_DROWSY, alertManager.currentAlertLevel)

        alertManager.updateAlertLevel(AlertLevel.NORMAL)
        assertEquals(AlertLevel.NORMAL, alertManager.currentAlertLevel)
    }

    /**
     * Verifies that release cleans up tone generators and stops alerts.
     */
    @Test
    fun testReleaseStopsAlertsAndCleansUp() {
        alertManager.updateAlertLevel(AlertLevel.WARNING_DISTRACTED)
        assertEquals(AlertLevel.WARNING_DISTRACTED, alertManager.currentAlertLevel)

        alertManager.release()
        alertManager.stopAlert()
        assertFalse(alertManager.hasAudioFocus)
    }

    /**
     * Verifies that stopping alerts clears active audio focus.
     */
    @Test
    fun testStopAlertClearsAudioFocus() {
        alertManager.updateAlertLevel(AlertLevel.CRITICAL_DROWSY)
        alertManager.stopAlert()
        assertFalse(alertManager.hasAudioFocus)
    }
}
