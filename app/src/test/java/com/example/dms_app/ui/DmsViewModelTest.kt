package com.example.dms_app.ui

import android.content.Context
import com.example.dms_app.audio.AlertManager
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [DmsViewModel] business logic, state mutations,
 * and audio/vibration coordinator interactions without Android runtime dependencies.
 */
class DmsViewModelTest {

    private lateinit var fakeAlertManager: FakeAlertManager
    private lateinit var viewModel: DmsViewModel

    /**
     * Fake test double for [AlertManager] to capture invocations and avoid Android audio hardware.
     */
    private class FakeAlertManager : AlertManager(null as Context?) {
        var lastLevel: AlertLevel? = null
        var stopAlertCalled: Boolean = false
        var lastAudioEnabled: Boolean? = null
        var lastVibrationEnabled: Boolean? = null

        override fun updateAlertLevel(level: AlertLevel) {
            lastLevel = level
        }

        override fun stopAlert() {
            stopAlertCalled = true
        }

        override fun updateSettings(audioEnabled: Boolean, vibrationEnabled: Boolean) {
            lastAudioEnabled = audioEnabled
            lastVibrationEnabled = vibrationEnabled
        }

        override fun release() {
            stopAlertCalled = true
        }
    }

    /**
     * Set up test fixtures before each test method execution.
     */
    @Before
    fun setUp() {
        fakeAlertManager = FakeAlertManager()
        viewModel = DmsViewModel(alertManager = fakeAlertManager)
    }

    /**
     * Verifies that the ViewModel initializes with default settings and active monitoring.
     */
    @Test
    fun testInitialStateHasMonitoringEnabledAndDefaultSettings() {
        assertTrue(viewModel.isMonitoring.value)
        assertEquals(DmsSettings(), viewModel.settings.value)
        assertEquals(AlertnessState(), viewModel.alertnessState.value)
    }

    /**
     * Verifies that toggling monitoring pauses the state and stops active alarms.
     */
    @Test
    fun testToggleMonitoringPausesAndResumesState() {
        // Toggle OFF
        viewModel.toggleMonitoring()
        assertFalse(viewModel.isMonitoring.value)
        assertTrue(fakeAlertManager.stopAlertCalled)
        assertEquals("Monitoring stopped", viewModel.alertnessState.value.statusMessage)

        // Toggle back ON
        viewModel.toggleMonitoring()
        assertTrue(viewModel.isMonitoring.value)
    }

    /**
     * Verifies that toggling audio updates the settings flow and notifies AlertManager.
     */
    @Test
    fun testToggleAudioUpdatesSettingsAndNotifiesAlertManager() {
        val initialAudio = viewModel.settings.value.isAudioEnabled
        viewModel.toggleAudio()

        assertEquals(!initialAudio, viewModel.settings.value.isAudioEnabled)
        assertEquals(!initialAudio, fakeAlertManager.lastAudioEnabled)
    }

    /**
     * Verifies that toggling vibration updates the settings flow and notifies AlertManager.
     */
    @Test
    fun testToggleVibrationUpdatesSettingsAndNotifiesAlertManager() {
        val initialVibration = viewModel.settings.value.isVibrationEnabled
        viewModel.toggleVibration()

        assertEquals(!initialVibration, viewModel.settings.value.isVibrationEnabled)
        assertEquals(!initialVibration, fakeAlertManager.lastVibrationEnabled)
    }

    /**
     * Verifies that toggling camera preview toggles the preview visibility state.
     */
    @Test
    fun testToggleCameraPreviewTogglesVisibility() {
        val initialPreview = viewModel.settings.value.isCameraPreviewVisible
        viewModel.toggleCameraPreview()

        assertEquals(!initialPreview, viewModel.settings.value.isCameraPreviewVisible)
    }

    /**
     * Verifies that updating eye sensitivity threshold updates settings.
     */
    @Test
    fun testUpdateEyeSensitivityModifiesThreshold() {
        viewModel.updateEyeSensitivity(0.45f)
        assertEquals(0.45f, viewModel.settings.value.eyeOpenThreshold, 0.001f)
    }

    /**
     * Verifies that updating drowsiness warning and critical durations modifies settings.
     */
    @Test
    fun testUpdateDrowsyThresholdsModifiesDurations() {
        viewModel.updateDrowsyThresholds(warningMs = 1200L, criticalMs = 2200L)

        assertEquals(1200L, viewModel.settings.value.drowsyWarningDurationMs)
        assertEquals(2200L, viewModel.settings.value.drowsyCriticalDurationMs)
    }
}
