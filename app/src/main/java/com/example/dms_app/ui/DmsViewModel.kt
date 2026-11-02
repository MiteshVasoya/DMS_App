package com.example.dms_app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dms_app.audio.AlertManager
import com.example.dms_app.data.DmsSettingsRepository
import com.example.dms_app.data.DmsSettingsRepositoryImpl
import com.example.dms_app.detection.DmsFaceAnalyzer
import com.example.dms_app.detection.VehicleMotionDetector
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import com.example.dms_app.model.MotionEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing driver alertness telemetry, configuration settings,
 * audio/haptic alert lifecycle, vehicle dynamics, and monitoring state.
 *
 * @param application Application context used for system services and repository instantiation.
 * @param alertManager Alert coordinator responsible for audio tones and haptic feedback.
 * @param customFaceAnalyzer Optional preconfigured face analyzer for testing or custom ML pipelines.
 * @param customMotionDetector Optional preconfigured motion detector for testing.
 * @param settingsRepository Optional repository handling persistence and streaming of [DmsSettings].
 */
class DmsViewModel @JvmOverloads constructor(
    application: Application? = null,
    private val alertManager: AlertManager = AlertManager(application?.applicationContext),
    customFaceAnalyzer: DmsFaceAnalyzer? = null,
    customMotionDetector: VehicleMotionDetector? = null,
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
                updateAlertnessFromFace(newState)
            }
        )
    } catch (_: Throwable) {
        // Fallback for headless JVM unit tests where MlKitContext is not initialized
        null
    }

    /**
     * Active vehicle motion detector evaluating accelerometer samples.
     */
    val motionDetector: VehicleMotionDetector = customMotionDetector ?: VehicleMotionDetector(
        context = application?.applicationContext,
        settings = settings.value,
        onMotionReading = { reading ->
            updateMotionReading(reading.accelerationMps2, reading.gForce, reading.motionEvent)
        }
    )

    init {
        // Start motion listening if monitoring is enabled
        if (_isMonitoring.value) {
            motionDetector.startListening()
        }

        // Observe persistent settings updates and apply to analyzer, motion detector, & alert manager
        viewModelScope.launch {
            settings.collect { updatedSettings ->
                alertManager.updateSettings(
                    audioEnabled = updatedSettings.isAudioEnabled,
                    vibrationEnabled = updatedSettings.isVibrationEnabled,
                    volume = updatedSettings.alarmVolume,
                    toneType = updatedSettings.alertToneType
                )
                faceAnalyzer?.updateSettings(updatedSettings)
                motionDetector.settings = updatedSettings
            }
        }
    }

    private fun updateAlertnessFromFace(faceState: AlertnessState) {
        if (!_isMonitoring.value) {
            _alertnessState.value = AlertnessState(statusMessage = "Monitoring paused")
            alertManager.updateAlertLevel(AlertLevel.NORMAL)
            return
        }

        // Maintain existing motion telemetry while applying vision updates
        val currentMotion = _alertnessState.value.motionEvent
        val resolvedLevel = resolveCombinedAlertLevel(faceState.alertLevel, currentMotion)
        val resolvedStatus = resolveCombinedStatusMessage(faceState.statusMessage, currentMotion)

        _alertnessState.value = faceState.copy(
            currentAccelerationMps2 = _alertnessState.value.currentAccelerationMps2,
            currentGForce = _alertnessState.value.currentGForce,
            motionEvent = currentMotion,
            alertLevel = resolvedLevel,
            statusMessage = resolvedStatus
        )
        alertManager.updateAlertLevel(resolvedLevel)
    }

    private fun updateMotionReading(accelMps2: Float, gForce: Float, event: MotionEventType) {
        if (!_isMonitoring.value) return

        val currentVisionLevel = when (_alertnessState.value.alertLevel) {
            AlertLevel.HARSH_ACCELERATION, AlertLevel.HARSH_BRAKING, AlertLevel.CRITICAL_CRASH -> AlertLevel.NORMAL
            else -> _alertnessState.value.alertLevel
        }

        val resolvedLevel = resolveCombinedAlertLevel(currentVisionLevel, event)
        val resolvedStatus = resolveCombinedStatusMessage(_alertnessState.value.statusMessage, event)

        _alertnessState.value = _alertnessState.value.copy(
            currentAccelerationMps2 = accelMps2,
            currentGForce = gForce,
            motionEvent = event,
            alertLevel = resolvedLevel,
            statusMessage = resolvedStatus
        )
        alertManager.updateAlertLevel(resolvedLevel)
    }

    private fun resolveCombinedAlertLevel(visionLevel: AlertLevel, motionEvent: MotionEventType): AlertLevel {
        return when (motionEvent) {
            MotionEventType.CRASH_DETECTED -> AlertLevel.CRITICAL_CRASH
            MotionEventType.HARSH_BRAKING -> {
                if (visionLevel == AlertLevel.CRITICAL_DROWSY || visionLevel == AlertLevel.CRITICAL_DISTRACTED) {
                    visionLevel
                } else {
                    AlertLevel.HARSH_BRAKING
                }
            }
            MotionEventType.RAPID_ACCELERATION -> {
                if (visionLevel == AlertLevel.CRITICAL_DROWSY || visionLevel == AlertLevel.CRITICAL_DISTRACTED) {
                    visionLevel
                } else {
                    AlertLevel.HARSH_ACCELERATION
                }
            }
            MotionEventType.NORMAL -> visionLevel
        }
    }

    private fun resolveCombinedStatusMessage(visionStatus: String, motionEvent: MotionEventType): String {
        return when (motionEvent) {
            MotionEventType.CRASH_DETECTED -> "⚠️ CRASH DETECTED!"
            MotionEventType.HARSH_BRAKING -> "🛑 Harsh Braking Detected!"
            MotionEventType.RAPID_ACCELERATION -> "⚡ Rapid Acceleration Detected!"
            MotionEventType.NORMAL -> visionStatus
        }
    }

    /**
     * Toggles driver alertness monitoring active state, stopping alerts when monitoring ceases.
     */
    fun toggleMonitoring() {
        val newState = !_isMonitoring.value
        _isMonitoring.value = newState
        if (!newState) {
            motionDetector.stopListening()
            alertManager.stopAlert()
            _alertnessState.value = AlertnessState(statusMessage = "Monitoring stopped")
        } else {
            motionDetector.startListening()
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
                alertManager.updateSettings(
                    audioEnabled = updated.isAudioEnabled,
                    vibrationEnabled = updated.isVibrationEnabled,
                    volume = updated.alarmVolume,
                    toneType = updated.alertToneType
                )
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
                alertManager.updateSettings(
                    audioEnabled = updated.isAudioEnabled,
                    vibrationEnabled = updated.isVibrationEnabled,
                    volume = updated.alarmVolume,
                    toneType = updated.alertToneType
                )
                faceAnalyzer?.updateSettings(updated)
            }
        }
    }

    /**
     * Updates the driver alert hardware volume level.
     *
     * @param volume Target alarm stream volume percentage (0 to 100).
     */
    fun updateAlarmVolume(volume: Int) {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings { it.copy(alarmVolume = volume.coerceIn(0, 100)) }
            } else {
                val updated = _settings.value.copy(alarmVolume = volume.coerceIn(0, 100))
                _settings.value = updated
                alertManager.updateSettings(
                    audioEnabled = updated.isAudioEnabled,
                    vibrationEnabled = updated.isVibrationEnabled,
                    volume = updated.alarmVolume,
                    toneType = updated.alertToneType
                )
            }
        }
    }

    /**
     * Updates the driver alert tone effect style.
     *
     * @param toneType Tone generator sound effect selection index.
     */
    fun updateAlertToneType(toneType: Int) {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings { it.copy(alertToneType = toneType) }
            } else {
                val updated = _settings.value.copy(alertToneType = toneType)
                _settings.value = updated
                alertManager.updateSettings(
                    audioEnabled = updated.isAudioEnabled,
                    vibrationEnabled = updated.isVibrationEnabled,
                    volume = updated.alarmVolume,
                    toneType = updated.alertToneType
                )
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
     * Toggles motion detection active state, persisting the change if repository is present.
     */
    fun toggleMotionDetection() {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings { it.copy(isMotionDetectionEnabled = !it.isMotionDetectionEnabled) }
            } else {
                val updated = _settings.value.copy(isMotionDetectionEnabled = !_settings.value.isMotionDetectionEnabled)
                _settings.value = updated
                motionDetector.settings = updated
            }
        }
    }

    /**
     * Updates vehicle motion thresholds for acceleration, braking, and crash detection.
     */
    fun updateMotionThresholds(accelThreshold: Float, brakingThreshold: Float, crashThresholdG: Float) {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings {
                    it.copy(
                        rapidAccelThresholdMps2 = accelThreshold,
                        harshBrakingThresholdMps2 = brakingThreshold,
                        crashThresholdGForce = crashThresholdG
                    )
                }
            } else {
                val updated = _settings.value.copy(
                    rapidAccelThresholdMps2 = accelThreshold,
                    harshBrakingThresholdMps2 = brakingThreshold,
                    crashThresholdGForce = crashThresholdG
                )
                _settings.value = updated
                motionDetector.settings = updated
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
     * Updates head pose distraction yaw and pitch angle sensitivity thresholds.
     *
     * @param yawThreshold Head rotation angle threshold in degrees triggering distraction alert.
     * @param pitchThreshold Head tilt angle threshold in degrees triggering distraction alert.
     */
    fun updateDistractionAngleThresholds(yawThreshold: Float, pitchThreshold: Float) {
        viewModelScope.launch {
            if (settingsRepository != null) {
                settingsRepository.updateSettings {
                    it.copy(
                        yawThresholdDegrees = yawThreshold,
                        pitchThresholdDegrees = pitchThreshold
                    )
                }
            } else {
                val updated = _settings.value.copy(
                    yawThresholdDegrees = yawThreshold,
                    pitchThresholdDegrees = pitchThreshold
                )
                _settings.value = updated
                faceAnalyzer?.updateSettings(updated)
            }
        }
    }

    /**
     * Releases active alert manager, motion detector, and face analyzer resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        motionDetector.stopListening()
        alertManager.release()
        faceAnalyzer?.close()
    }
}
