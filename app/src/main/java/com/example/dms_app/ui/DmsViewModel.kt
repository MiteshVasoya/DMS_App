package com.example.dms_app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.dms_app.audio.AlertManager
import com.example.dms_app.detection.DmsFaceAnalyzer
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel managing driver alertness telemetry, configuration settings,
 * audio/haptic alert lifecycle, and monitoring state.
 */
class DmsViewModel(application: Application) : AndroidViewModel(application) {

    private val alertManager = AlertManager(application.applicationContext)

    private val _settings = MutableStateFlow(DmsSettings())
    val settings: StateFlow<DmsSettings> = _settings.asStateFlow()

    private val _isMonitoring = MutableStateFlow(true)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _alertnessState = MutableStateFlow(AlertnessState())
    val alertnessState: StateFlow<AlertnessState> = _alertnessState.asStateFlow()

    val faceAnalyzer = DmsFaceAnalyzer(
        settings = _settings.value,
        onStateUpdated = { newState ->
            if (_isMonitoring.value) {
                _alertnessState.value = newState
                alertManager.updateAlertLevel(newState.alertLevel)
            } else {
                _alertnessState.value = AlertnessState(statusMessage = "Monitoring paused")
                alertManager.updateAlertLevel(AlertLevel.NORMAL)
            }
        }
    )

    fun toggleMonitoring() {
        val newState = !_isMonitoring.value
        _isMonitoring.value = newState
        if (!newState) {
            alertManager.stopAlert()
            _alertnessState.value = AlertnessState(statusMessage = "Monitoring stopped")
        }
    }

    fun toggleAudio() {
        _settings.update { current ->
            val updated = current.copy(isAudioEnabled = !current.isAudioEnabled)
            alertManager.updateSettings(updated.isAudioEnabled, updated.isVibrationEnabled)
            faceAnalyzer.updateSettings(updated)
            updated
        }
    }

    fun toggleVibration() {
        _settings.update { current ->
            val updated = current.copy(isVibrationEnabled = !current.isVibrationEnabled)
            alertManager.updateSettings(updated.isAudioEnabled, updated.isVibrationEnabled)
            faceAnalyzer.updateSettings(updated)
            updated
        }
    }

    fun updateEyeSensitivity(threshold: Float) {
        _settings.update { current ->
            val updated = current.copy(eyeOpenThreshold = threshold)
            faceAnalyzer.updateSettings(updated)
            updated
        }
    }

    fun updateDrowsyThresholds(warningMs: Long, criticalMs: Long) {
        _settings.update { current ->
            val updated = current.copy(
                drowsyWarningDurationMs = warningMs,
                drowsyCriticalDurationMs = criticalMs
            )
            faceAnalyzer.updateSettings(updated)
            updated
        }
    }

    override fun onCleared() {
        super.onCleared()
        alertManager.release()
        faceAnalyzer.close()
    }
}
