package com.example.dms_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.dms_app.MainActivity
import com.example.dms_app.R
import com.example.dms_app.audio.AlertManager
import com.example.dms_app.data.DmsSettingsRepository
import com.example.dms_app.data.DmsSettingsRepositoryImpl
import com.example.dms_app.detection.DmsFaceAnalyzer
import com.example.dms_app.detection.VehicleMotionDetector
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import com.example.dms_app.model.MotionEventType
import com.example.dms_app.overlay.DmsOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Foreground Service that keeps CameraX ImageAnalysis, ML Kit Face Detection, and
 * Vehicle Motion Detection running even when the driver navigates with Google Maps or Waze.
 * Controls the floating overlay bubble and plays audio/vibration alarms.
 */
class DmsForegroundService : LifecycleService() {

    private lateinit var alertManager: AlertManager
    private lateinit var overlayManager: DmsOverlayManager
    private lateinit var motionDetector: VehicleMotionDetector
    private var faceAnalyzer: DmsFaceAnalyzer? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Optional settings repository instance for testing and dynamic preference synchronization.
     */
    var settingsRepository: DmsSettingsRepository? = null

    /**
     * Internal getter for [AlertManager] to support testing and state verification.
     */
    val currentAlertManager: AlertManager?
        get() = if (::alertManager.isInitialized) alertManager else null

    /**
     * Internal getter for [DmsFaceAnalyzer] to support testing and state verification.
     */
    val currentFaceAnalyzer: DmsFaceAnalyzer?
        get() = faceAnalyzer

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isRunning.value = true

        alertManager = AlertManager(applicationContext)
        overlayManager = DmsOverlayManager(applicationContext)

        val repo = settingsRepository ?: DmsSettingsRepositoryImpl(applicationContext)
        settingsRepository = repo
        faceAnalyzer = DmsFaceAnalyzer(
            settings = DmsSettings(),
            onStateUpdated = { state ->
                val currentMotion = _alertnessState.value.motionEvent
                val resolvedLevel = resolveCombinedAlertLevel(state.alertLevel, currentMotion)
                val resolvedStatus = resolveCombinedStatusMessage(state.statusMessage, currentMotion)
                val combined = state.copy(
                    currentAccelerationMps2 = _alertnessState.value.currentAccelerationMps2,
                    currentGForce = _alertnessState.value.currentGForce,
                    motionEvent = currentMotion,
                    alertLevel = resolvedLevel,
                    statusMessage = resolvedStatus
                )
                _alertnessState.value = combined
                alertManager.updateAlertLevel(resolvedLevel)
                overlayManager.updateState(combined)
                updateNotification(combined)
            }
        )

        motionDetector = VehicleMotionDetector(
            context = applicationContext,
            settings = DmsSettings(),
            onMotionReading = { reading ->
                val currentVisionLevel = when (_alertnessState.value.alertLevel) {
                    AlertLevel.HARSH_ACCELERATION, AlertLevel.HARSH_BRAKING, AlertLevel.CRITICAL_CRASH -> AlertLevel.NORMAL
                    else -> _alertnessState.value.alertLevel
                }
                val resolvedLevel = resolveCombinedAlertLevel(currentVisionLevel, reading.motionEvent)
                val resolvedStatus = resolveCombinedStatusMessage(_alertnessState.value.statusMessage, reading.motionEvent)
                val combined = _alertnessState.value.copy(
                    currentAccelerationMps2 = reading.accelerationMps2,
                    currentGForce = reading.gForce,
                    motionEvent = reading.motionEvent,
                    alertLevel = resolvedLevel,
                    statusMessage = resolvedStatus
                )
                _alertnessState.value = combined
                alertManager.updateAlertLevel(resolvedLevel)
                overlayManager.updateState(combined)
                updateNotification(combined)
            }
        )

        // Observe DataStore persistent settings updates in real time
        serviceScope.launch {
            repo.settingsFlow.collect { updatedSettings ->
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

        createNotificationChannel()
        startInForeground()

        // Show floating bubble if permission is granted
        overlayManager.showOverlay()

        // Initialize CameraX in Service lifecycle
        startCameraMonitoring()

        // Start motion listening
        motionDetector.startListening()
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

    private fun startInForeground() {
        val notification = buildNotification(AlertnessState())
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        )
    }

    private fun startCameraMonitoring() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                faceAnalyzer?.let { analyzer ->
                    imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (_: Exception) {
                // Fallback to back camera if front camera is unavailable
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                    faceAnalyzer?.let { analyzer ->
                        imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageAnalysis
                    )
                } catch (_: Exception) {
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Monitoring Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors driver alertness and drowsiness in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: AlertnessState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DmsForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (state.alertLevel) {
            AlertLevel.CRITICAL_CRASH -> "⚠️ EMERGENCY: Collision / Crash detected!"
            AlertLevel.CRITICAL_DROWSY -> "⚠️ CRITICAL: Drowsiness detected! Wake up!"
            AlertLevel.CRITICAL_DISTRACTED -> "⚠️ Distraction detected! Focus on road!"
            AlertLevel.HARSH_BRAKING -> "🛑 Harsh braking detected"
            AlertLevel.HARSH_ACCELERATION -> "⚡ Rapid acceleration detected"
            AlertLevel.WARNING_DROWSY -> "Caution: Eyes closing"
            AlertLevel.WARNING_DISTRACTED -> "Caution: Looking away"
            AlertLevel.NORMAL -> "Driver Alert & Protected"
            AlertLevel.FACE_NOT_DETECTED -> "Searching for driver..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DMS Active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Monitoring", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(state: AlertnessState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        instance = null
        serviceScope.cancel()

        motionDetector.stopListening()
        overlayManager.removeOverlay()
        alertManager.release()
        faceAnalyzer?.close()
        analysisExecutor.shutdown()
    }

    companion object {
        const val CHANNEL_ID = "dms_foreground_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.example.dms_app.ACTION_STOP_SERVICE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _alertnessState = MutableStateFlow(AlertnessState())
        val alertnessState: StateFlow<AlertnessState> = _alertnessState.asStateFlow()

        var instance: DmsForegroundService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, DmsForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DmsForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
