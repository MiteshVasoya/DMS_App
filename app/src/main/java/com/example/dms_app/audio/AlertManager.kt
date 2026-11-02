package com.example.dms_app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.dms_app.model.AlertLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles audio tones and haptic vibration feedback
 * for normal driver warnings, vehicle dynamics alerts, and critical impact/crash alerts.
 */
open class AlertManager(private val context: Context?) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var alertJob: Job? = null
    private var currentLevel: AlertLevel = AlertLevel.NORMAL
    val currentAlertLevel: AlertLevel
        get() = currentLevel
    /**
     * Indicates whether audio tone alarms are enabled.
     */
    var isAudioEnabled: Boolean = true
        private set

    /**
     * Indicates whether haptic vibration alarms are enabled.
     */
    var isVibrationEnabled: Boolean = true
        private set
    var alarmVolume: Int = 100
        private set
    var alertToneType: Int = 0
        private set

    /**
     * Internal tracking of whether audio focus has been requested from AudioManager.
     */
    var hasAudioFocus: Boolean = false
        private set

    private var toneGenerator: ToneGenerator? = null

    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioManager: AudioManager? by lazy {
        context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val vibrator: Vibrator? by lazy {
        val vibratorManager = context?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    }

    init {
        createToneGenerator()
    }

    /**
     * Recreates the tone generator with the configured hardware stream and volume.
     */
    private fun createToneGenerator() {
        try {
            toneGenerator?.release()
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, alarmVolume.coerceIn(0, 100))
        } catch (_: Throwable) {
            // ToneGenerator init fallback in unit test / headless environments
        }
    }

    /**
     * Updates alert settings including audio and vibration toggles.
     *
     * @param audioEnabled Whether audio alarms are permitted to sound.
     * @param vibrationEnabled Whether haptic vibration feedback is permitted.
     */
    open fun updateSettings(audioEnabled: Boolean, vibrationEnabled: Boolean) {
        updateSettings(audioEnabled, vibrationEnabled, alarmVolume, alertToneType)
    }

    /**
     * Updates alert settings including audio/vibration toggles, hardware volume, and tone style.
     *
     * @param audioEnabled Whether audio alarms are permitted to sound.
     * @param vibrationEnabled Whether haptic vibration feedback is permitted.
     * @param volume Hardware alarm stream volume (scaled 0 to 100).
     * @param toneType Sound effect selection index for audible alerts.
     */
    open fun updateSettings(
        audioEnabled: Boolean,
        vibrationEnabled: Boolean,
        volume: Int = alarmVolume,
        toneType: Int = alertToneType
    ) {
        val volumeChanged = volume != alarmVolume
        isAudioEnabled = audioEnabled
        isVibrationEnabled = vibrationEnabled
        alarmVolume = volume.coerceIn(0, 100)
        alertToneType = toneType

        if (volumeChanged) {
            createToneGenerator()
        }

        if (!audioEnabled && !vibrationEnabled) {
            stopAlert()
        }
    }

    open fun updateAlertLevel(level: AlertLevel) {
        if (level == currentLevel) return
        currentLevel = level

        when (level) {
            AlertLevel.NORMAL, AlertLevel.FACE_NOT_DETECTED -> {
                stopAlert()
            }
            AlertLevel.WARNING_DROWSY, AlertLevel.WARNING_DISTRACTED -> {
                startPulsingAlert(pulseIntervalMs = 500L, toneDurationMs = 250, critical = false)
            }
            AlertLevel.HARSH_ACCELERATION -> {
                startPulsingAlert(pulseIntervalMs = 400L, toneDurationMs = 200, critical = false)
            }
            AlertLevel.HARSH_BRAKING -> {
                startPulsingAlert(pulseIntervalMs = 300L, toneDurationMs = 180, critical = false)
            }
            AlertLevel.CRITICAL_DROWSY, AlertLevel.CRITICAL_DISTRACTED, AlertLevel.CRITICAL_CRASH -> {
                startPulsingAlert(pulseIntervalMs = 200L, toneDurationMs = 150, critical = true)
            }
        }
    }

    private fun startPulsingAlert(pulseIntervalMs: Long, toneDurationMs: Int, critical: Boolean) {
        requestAudioFocus(critical = critical)
        alertJob?.cancel()
        alertJob = scope.launch {
            while (isActive) {
                if (isAudioEnabled) {
                    playTone(critical = critical, durationMs = toneDurationMs)
                }
                if (isVibrationEnabled) {
                    vibrate(critical = critical, durationMs = toneDurationMs.toLong())
                }
                delay(pulseIntervalMs)
            }
        }
    }

    /**
     * Requests transient audio focus so external media (e.g. Spotify, Maps) ducks or pauses during alarms.
     *
     * @param critical If true, requests exclusive transient focus; otherwise requests transient focus with ducking.
     */
    private fun requestAudioFocus(critical: Boolean) {
        if (!isAudioEnabled) return
        val am = audioManager ?: return
        try {
            val focusGain = if (critical) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            } else {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            }

            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val request = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { /* Handle focus changes if needed */ }
                .build()

            audioFocusRequest = request
            val res = am.requestAudioFocus(request)
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } catch (_: Throwable) {
            // Audio focus fallback in unit test / headless mock environments
        }
    }

    /**
     * Abandons active audio focus to restore original volume for background media applications.
     */
    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        val request = audioFocusRequest ?: return
        try {
            am.abandonAudioFocusRequest(request)
            audioFocusRequest = null
            hasAudioFocus = false
        } catch (_: Throwable) {
        }
    }

    private fun playTone(critical: Boolean, durationMs: Int) {
        try {
            val toneType = when {
                critical -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                alertToneType == 1 -> ToneGenerator.TONE_PROP_PROMPT
                alertToneType == 2 -> ToneGenerator.TONE_DTMF_D
                else -> ToneGenerator.TONE_PROP_BEEP2
            }
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {
        }
    }

    private fun vibrate(critical: Boolean, durationMs: Long) {
        try {
            val amplitude = if (critical) 255 else 160
            val effect = VibrationEffect.createOneShot(durationMs, amplitude)
            vibrator?.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    open fun stopAlert() {
        alertJob?.cancel()
        alertJob = null
        try {
            toneGenerator?.stopTone()
            vibrator?.cancel()
        } catch (_: Exception) {
        } finally {
            abandonAudioFocus()
        }
    }

    open fun release() {
        stopAlert()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {
        }
    }
}
