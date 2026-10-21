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
import com.example.dms_app.detection.DmsFaceAnalyzer
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import com.example.dms_app.overlay.DmsOverlayManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Foreground Service that keeps CameraX ImageAnalysis and ML Kit Face Detection running
 * even when the driver navigates with Google Maps or Waze.
 * Controls the floating overlay bubble and plays audio/vibration alarms.
 */
class DmsForegroundService : LifecycleService() {

    private lateinit var alertManager: AlertManager
    private lateinit var overlayManager: DmsOverlayManager
    private var faceAnalyzer: DmsFaceAnalyzer? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isRunning.value = true

        alertManager = AlertManager(applicationContext)
        overlayManager = DmsOverlayManager(applicationContext)

        val settings = DmsSettings()
        faceAnalyzer = DmsFaceAnalyzer(
            settings = settings,
            onStateUpdated = { state ->
                _alertnessState.value = state
                alertManager.updateAlertLevel(state.alertLevel)
                overlayManager.updateState(state)
                updateNotification(state)
            }
        )

        createNotificationChannel()
        startInForeground()

        // Show floating bubble if permission is granted
        overlayManager.showOverlay()

        // Initialize CameraX in Service lifecycle
        startCameraMonitoring()
    }

    private fun startInForeground() {
        val notification = buildNotification(AlertnessState())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
            } catch (e: Exception) {
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
            AlertLevel.CRITICAL_DROWSY -> "⚠️ CRITICAL: Drowsiness detected! Wake up!"
            AlertLevel.CRITICAL_DISTRACTED -> "⚠️ Distraction detected! Focus on road!"
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
