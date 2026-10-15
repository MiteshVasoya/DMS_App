package com.example.dms_app.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
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
 * for normal driver warnings and critical alerts.
 */
class AlertManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var alertJob: Job? = null
    private var currentLevel: AlertLevel = AlertLevel.NORMAL
    private var isAudioEnabled: Boolean = true
    private var isVibrationEnabled: Boolean = true

    private var toneGenerator: ToneGenerator? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (_: Exception) {
            // ToneGenerator init fallback
        }
    }

    fun updateSettings(audioEnabled: Boolean, vibrationEnabled: Boolean) {
        isAudioEnabled = audioEnabled
        isVibrationEnabled = vibrationEnabled
        if (!audioEnabled && !vibrationEnabled) {
            stopAlert()
        }
    }

    fun updateAlertLevel(level: AlertLevel) {
        if (level == currentLevel) return
        currentLevel = level

        when (level) {
            AlertLevel.NORMAL, AlertLevel.FACE_NOT_DETECTED -> {
                stopAlert()
            }
            AlertLevel.WARNING_DROWSY, AlertLevel.WARNING_DISTRACTED -> {
                startPulsingAlert(pulseIntervalMs = 500L, toneDurationMs = 250, critical = false)
            }
            AlertLevel.CRITICAL_DROWSY, AlertLevel.CRITICAL_DISTRACTED -> {
                startPulsingAlert(pulseIntervalMs = 200L, toneDurationMs = 150, critical = true)
            }
        }
    }

    private fun startPulsingAlert(pulseIntervalMs: Long, toneDurationMs: Int, critical: Boolean) {
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

    private fun playTone(critical: Boolean, durationMs: Int) {
        try {
            val toneType = if (critical) {
                ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
            } else {
                ToneGenerator.TONE_PROP_BEEP2
            }
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {
        }
    }

    private fun vibrate(critical: Boolean, durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = if (critical) 255 else 160
                val effect = VibrationEffect.createOneShot(durationMs, amplitude)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    fun stopAlert() {
        alertJob?.cancel()
        alertJob = null
        try {
            toneGenerator?.stopTone()
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    fun release() {
        stopAlert()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {
        }
    }
}
