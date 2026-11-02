package com.example.dms_app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dms_app.audio.AlertManager
import com.example.dms_app.data.DmsSettingsRepository
import com.example.dms_app.data.DmsSettingsRepositoryImpl
import com.example.dms_app.detection.DmsFaceAnalyzer
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing driver alertness telemetry, configuration settings,
 * audio/haptic alert lifecycle, and monitoring state.
 *
 * @param application Application context used for system services and repository instantiation.
 * @param alertManager Alert coordinator responsible for audio tones and haptic feedback.
 * @param customFaceAnalyzer Optional preconfigured face analyzer for testing or custom ML pipelines.
 * @param settingsRepository Optional repository handling persistence and streaming of [DmsSettings].
 */
class DmsViewModel @JvmOverloads constructor(
    application: Application? = null,
    private val alertManager: AlertManager = AlertManager(application?.applicationContext),
    customFaceAnalyzer: DmsFaceAnalyzer? = null,
    private val settingsRepository: DmsSettingsRepository? = application?.let { DmsSettingsRepositoryImpl(it) }
) : AndroidViewModel(application ?: Application()) {

    private val _settings = MutableStateFlow(DmsSettings())

    /**
     * Observable stream of active [DmsSettings] backed by persistent storage or in-memory state.
     */
    val settings: StateFlow<DmsSettings> = settingsRepository?.settingsFlow?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DmsSettings()
    ) ?: _settings.asStateFlow()

    private val _isMonitoring = MutableStateFlow(true)

    /**
     * Observable monitoring status indicating whether driver telemetry is actively evaluated.
     */
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _alertnessState = MutableStateFlow(AlertnessState())

    /**
     * Observable driver alertness state providing telemetry and alert categorization.
     */
    val alertnessState: StateFlow<AlertnessState> = _alertnessState.asStateFlow()

    /**
     * Active face analyzer processing camera frames, or null if initialization failed.
     */
    val faceAnalyzer: DmsFaceAnalyzer? = customFaceAnalyzer ?: try {
        DmsFaceAnalyzer(
            settings = settings.value,
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
    } catch (_: Throwable) {
        // Fallback for headless JVM unit tests where MlKitContext is not initialized
        null
    }

    init {
        // Observe persistent settings updates and apply to analyzer & alert manager
        viewModelScope.launch {
            settings.collect { updatedSettings ->
                alertManager.updateSettings(
                    updatedSettings.isAudioEnabled,
                    updatedSettings.isVibrationEnabled
                )
                faceAnalyzer?.updateSettings(updatedSettings)
            }
        }
    }

    /**
     * Toggles driver alertness monitoring active state, stopping alerts when monitoring ceases.
     */
    fun toggleMonitoring() {
        val newState = !_isMonitoring.value
        _isMonitoring.value = newState
        if (!newState) {
            alertManager.stopAlert()
            _alertnessState.value = AlertnessState(statusMessage = "Monitoring stopped")
        }
    }

    /**
     * Toggles audible alert notifications on or off, persisting the change if repository is present.
     */
    fun toggleAudio() {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings { it.copy(isAudioEnabled = !it.isAudioEnabled) }
            } else {
                val updated = _settings.value.copy(isAudioEnabled = !_settings.value.isAudioEnabled)
                _settings.value = updated
                alertManager.updateSettings(updated.isAudioEnabled, updated.isVibrationEnabled)
                faceAnalyzer?.updateSettings(updated)
            }
        }
    }

    /**
     * Toggles haptic vibration alert notifications on or off, persisting the change if repository is present.
     */
    fun toggleVibration() {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings { it.copy(isVibrationEnabled = !it.isVibrationEnabled) }
            } else {
                val updated = _settings.value.copy(isVibrationEnabled = !_settings.value.isVibrationEnabled)
                _settings.value = updated
                alertManager.updateSettings(updated.isAudioEnabled, updated.isVibrationEnabled)
                faceAnalyzer?.updateSettings(updated)
            }
        }
    }

    /**
     * Toggles camera preview visibility within the UI, persisting the change if repository is present.
     */
    fun toggleCameraPreview() {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings { it.copy(isCameraPreviewVisible = !it.isCameraPreviewVisible) }
            } else {
                _settings.value = _settings.value.copy(isCameraPreviewVisible = !_settings.value.isCameraPreviewVisible)
            }
        }
    }

    /**
     * Updates eye openness sensitivity threshold, persisting the change if repository is present.
     *
     * @param threshold EAR threshold below which eyes are classified as closed.
     */
    fun updateEyeSensitivity(threshold: Float) {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings { it.copy(eyeOpenThreshold = threshold) }
            } else {
                val updated = _settings.value.copy(eyeOpenThreshold = threshold)
                _settings.value = updated
                faceAnalyzer?.updateSettings(updated)
            }
        }
    }

    /**
     * Updates drowsiness warning and critical duration thresholds, persisting changes if repository is present.
     *
     * @param warningMs Continuous eye closure duration in milliseconds triggering a warning.
     * @param criticalMs Continuous eye closure duration in milliseconds triggering a critical alarm.
     */
    fun updateDrowsyThresholds(warningMs: Long, criticalMs: Long) {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings {
                    it.copy(
                        drowsyWarningDurationMs = warningMs,
                        drowsyCriticalDurationMs = criticalMs
                    )
                }
            } else {
                val updated = _settings.value.copy(
                    drowsyWarningDurationMs = warningMs,
                    drowsyCriticalDurationMs = criticalMs
                )
                _settings.value = updated
                faceAnalyzer?.updateSettings(updated)
            }
        }
    }

    /**
     * Releases active alert manager and face analyzer resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        alertManager.release()
        faceAnalyzer?.close()
    }
}
