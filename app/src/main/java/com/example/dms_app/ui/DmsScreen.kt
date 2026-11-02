package com.example.dms_app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import com.example.dms_app.service.DmsForegroundService
import com.example.dms_app.ui.components.DmsCameraPreview
import androidx.compose.ui.tooling.preview.Preview
import com.example.dms_app.ui.theme.DMS_AppTheme
import java.util.Locale

/**
 * Main screen composable for the Driver Monitoring System.
 * Handles permission checking, camera initialization, HUD rendering, and settings dialog.
 *
 * @param modifier Optional [Modifier] for layout adjustments.
 * @param viewModel Primary [DmsViewModel] instance managing alertness state and settings.
 * @param initialCameraPermission Optional pre-determined camera permission flag for UI testing.
 */
@Composable
fun DmsScreen(
    modifier: Modifier = Modifier,
    viewModel: DmsViewModel = viewModel(),
    initialCameraPermission: Boolean? = null
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            initialCameraPermission ?: (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && initialCameraPermission == null) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionRationale(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = modifier
        )
    } else {
        DmsDashboard(viewModel = viewModel, modifier = modifier)
    }
}

@Composable
private fun DmsDashboard(
    viewModel: DmsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alertnessState by viewModel.alertnessState.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    fun launchBackgroundBubble() {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )
            context.startActivity(intent)
        } else {
            DmsForegroundService.start(context)
            // Move app to home screen so Google Maps or launcher is displayed with bubble
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }
    }

    // Pulsing color transition for warning and critical alarms
    val infiniteTransition = rememberInfiniteTransition(label = "AlertPulse")
    val pulsingAlpha by infiniteTransition.animateColor(
        initialValue = when (alertnessState.alertLevel) {
            AlertLevel.CRITICAL_DROWSY, AlertLevel.CRITICAL_DISTRACTED, AlertLevel.CRITICAL_CRASH -> Color.Red.copy(alpha = 0.75f)
            AlertLevel.WARNING_DROWSY, AlertLevel.WARNING_DISTRACTED,
            AlertLevel.HARSH_BRAKING, AlertLevel.HARSH_ACCELERATION -> Color(0xFFFFA000).copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        targetValue = when (alertnessState.alertLevel) {
            AlertLevel.CRITICAL_DROWSY, AlertLevel.CRITICAL_DISTRACTED, AlertLevel.CRITICAL_CRASH -> Color.Red.copy(alpha = 0.15f)
            AlertLevel.WARNING_DROWSY, AlertLevel.WARNING_DISTRACTED,
            AlertLevel.HARSH_BRAKING, AlertLevel.HARSH_ACCELERATION -> Color(0xFFFFA000).copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = infiniteRepeatable(
            animation = tween(if (alertnessState.isCritical) 200 else 450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Live Camera Preview or Black Screen (to avoid distraction and battery drain)
        if (isMonitoring && viewModel.faceAnalyzer != null) {
            DmsCameraPreview(
                analyzer = viewModel.faceAnalyzer!!,
                isPreviewVisible = settings.isCameraPreviewVisible,
                modifier = Modifier.fillMaxSize()
            )

            // When camera display is turned off to save battery, show pure black with subtle text
            if (!settings.isCameraPreviewVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Camera Display Off",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Display Off • Battery Saver",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Monitoring Paused",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2. Full-Screen Flashing Alert Overlay (When alert is triggered)
        if (alertnessState.isAlertTriggered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pulsingAlpha)
                    .border(
                        BorderStroke(
                            width = if (alertnessState.isCritical) 12.dp else 6.dp,
                            color = if (alertnessState.isCritical) Color.Red else Color(0xFFFFA000)
                        )
                    )
            )
        }

        // 3. Top Alert Banner & HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopStatusBanner(alertnessState = alertnessState, isMonitoring = isMonitoring)
            TelemetryHud(alertnessState = alertnessState, isMonitoring = isMonitoring)
        }

        // 4. Bottom Controls Bar
        BottomControlsBar(
            isMonitoring = isMonitoring,
            isAudioEnabled = settings.isAudioEnabled,
            isVibrationEnabled = settings.isVibrationEnabled,
            isCameraPreviewVisible = settings.isCameraPreviewVisible,
            onToggleMonitoring = { viewModel.toggleMonitoring() },
            onToggleCameraPreview = { viewModel.toggleCameraPreview() },
            onLaunchFloatingBubble = { launchBackgroundBubble() },
            onToggleAudio = { viewModel.toggleAudio() },
            onToggleVibration = { viewModel.toggleVibration() },
            onOpenSettings = { showSettingsDialog = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        // Settings Dialog
        if (showSettingsDialog) {
            DmsSettingsDialog(
                settings = settings,
                isCameraPreviewVisible = settings.isCameraPreviewVisible,
                onDismiss = { showSettingsDialog = false },
                onToggleCameraPreview = { viewModel.toggleCameraPreview() },
                onEyeSensitivityChanged = { viewModel.updateEyeSensitivity(it) },
                onDrowsyThresholdsChanged = { warn, crit -> viewModel.updateDrowsyThresholds(warn, crit) },
                onAlarmVolumeChanged = { viewModel.updateAlarmVolume(it) },
                onAlertToneTypeChanged = { viewModel.updateAlertToneType(it) },
                onDistractionAnglesChanged = { yaw, pitch -> viewModel.updateDistractionAngleThresholds(yaw, pitch) },
                onToggleMotionDetection = { viewModel.toggleMotionDetection() },
                onMotionThresholdsChanged = { accel, brake, crash -> viewModel.updateMotionThresholds(accel, brake, crash) }
            )
        }
    }
}

@Composable
private fun TopStatusBanner(
    alertnessState: AlertnessState,
    isMonitoring: Boolean
) {
    val bannerColor = when {
        !isMonitoring -> Color(0xFF424242)
        alertnessState.isCritical -> Color(0xFFD32F2F)
        alertnessState.alertLevel == AlertLevel.WARNING_DROWSY ||
                alertnessState.alertLevel == AlertLevel.WARNING_DISTRACTED ||
                alertnessState.alertLevel == AlertLevel.HARSH_BRAKING ||
                alertnessState.alertLevel == AlertLevel.HARSH_ACCELERATION -> Color(0xFFF57C00)
        !alertnessState.faceDetected -> Color(0xFF616161)
        else -> Color(0xFF2E7D32)
    }

    Surface(
        color = bannerColor.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (alertnessState.isAlertTriggered) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert Warning",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (alertnessState.faceDetected && isMonitoring) Color(0xFF69F0AE) else Color.LightGray)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMonitoring) alertnessState.statusMessage else "Monitoring is stopped",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isMonitoring && alertnessState.eyeClosedDurationMs > 0) {
                    Text(
                        text = "Eyes closed: ${(alertnessState.eyeClosedDurationMs / 1000.0).format(1)}s",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }

            if (isMonitoring) {
                Text(
                    text = "${alertnessState.fps} FPS",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TelemetryHud(
    alertnessState: AlertnessState,
    isMonitoring: Boolean
) {
    if (!isMonitoring || !alertnessState.faceDetected) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Eye
            EyeMetricColumn(
                label = "Left Eye",
                prob = alertnessState.leftEyeOpenProb
            )

            // Right Eye
            EyeMetricColumn(
                label = "Right Eye",
                prob = alertnessState.rightEyeOpenProb
            )

            // Head Yaw (Rotation)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Head Pose",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                val yaw = alertnessState.headEulerAngleY
                val yawText = if (yaw != null) "${yaw.toInt()}°" else "--"
                Text(
                    text = yawText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // G-Force / Linear Acceleration
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "G-Force",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                val gText = "${alertnessState.currentGForce.format(2)}g"
                Text(
                    text = gText,
                    color = if (alertnessState.currentGForce > 0.4f) Color(0xFFFF9100) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EyeMetricColumn(
    label: String,
    prob: Float?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = prob?.let { "${(it * 100).toInt()}%" } ?: "--",
            color = if ((prob ?: 1f) < 0.35f) Color(0xFFFF5252) else Color(0xFF69F0AE),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { prob ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if ((prob ?: 1f) < 0.35f) Color(0xFFFF5252) else Color(0xFF69F0AE),
            trackColor = Color.White.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun BottomControlsBar(
    isMonitoring: Boolean,
    isAudioEnabled: Boolean,
    isVibrationEnabled: Boolean,
    isCameraPreviewVisible: Boolean,
    onToggleMonitoring: () -> Unit,
    onToggleCameraPreview: () -> Unit,
    onLaunchFloatingBubble: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleVibration: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Start / Stop Main Toggle Button
            Button(
                onClick = onToggleMonitoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isMonitoring) "Stop" else "Start"
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (isMonitoring) "Pause" else "Start")
            }

            // Floating Bubble Mode (Background Overlay over Maps)
            FilledTonalIconButton(
                onClick = onLaunchFloatingBubble,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF6A1B9A)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Run in Background Floating Bubble",
                    tint = Color.White
                )
            }

            // Camera Display Toggle (Battery Saver)
            FilledTonalIconButton(
                onClick = onToggleCameraPreview,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isCameraPreviewVisible) Color(0xFF1E88E5) else Color.DarkGray
                )
            ) {
                Icon(
                    imageVector = if (isCameraPreviewVisible) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = if (isCameraPreviewVisible) "Turn Off Camera View" else "Turn On Camera View",
                    tint = Color.White
                )
            }

            // Audio Toggle
            FilledTonalIconButton(
                onClick = onToggleAudio,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isAudioEnabled) Color(0xFF1E88E5) else Color.DarkGray
                )
            ) {
                Icon(
                    imageVector = if (isAudioEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = "Toggle Audio",
                    tint = Color.White
                )
            }

            // Vibration Toggle
            FilledTonalIconButton(
                onClick = onToggleVibration,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isVibrationEnabled) Color(0xFF1E88E5) else Color.DarkGray
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = "Toggle Vibration",
                    tint = Color.White
                )
            }

            // Sensitivity Settings
            FilledTonalIconButton(
                onClick = onOpenSettings,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.DarkGray)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Dialog displaying configurable driver settings including thresholds, camera preview,
 * alarm volume, and alert sound tone styles.
 *
 * @param settings Current active driver monitoring settings.
 * @param isCameraPreviewVisible Whether camera preview is rendered.
 * @param onDismiss Callback invoked when dialog is dismissed.
 * @param onToggleCameraPreview Callback invoked to toggle camera preview state.
 * @param onEyeSensitivityChanged Callback invoked with updated eye closure sensitivity.
 * @param onDrowsyThresholdsChanged Callback invoked with updated warning and critical durations.
 * @param onAlarmVolumeChanged Callback invoked when hardware alarm stream volume is adjusted.
 * @param onAlertToneTypeChanged Callback invoked when driver selects an alternate alert sound effect.
 * @param onDistractionAnglesChanged Callback invoked with updated head pose yaw and pitch angle thresholds.
 */
@Composable
private fun DmsSettingsDialog(
    settings: DmsSettings,
    isCameraPreviewVisible: Boolean,
    onDismiss: () -> Unit,
    onToggleCameraPreview: () -> Unit,
    onEyeSensitivityChanged: (Float) -> Unit,
    onDrowsyThresholdsChanged: (Long, Long) -> Unit,
    onAlarmVolumeChanged: (Int) -> Unit = {},
    onAlertToneTypeChanged: (Int) -> Unit = {},
    onDistractionAnglesChanged: (Float, Float) -> Unit = { _, _ -> },
    onToggleMotionDetection: () -> Unit = {},
    onMotionThresholdsChanged: (Float, Float, Float) -> Unit = { _, _, _ -> }
) {
    var eyeThreshold by remember { mutableFloatStateOf(settings.eyeOpenThreshold) }
    var warnDurationSec by remember { mutableFloatStateOf(settings.drowsyWarningDurationMs / 1000f) }
    var alarmVolume by remember { mutableFloatStateOf(settings.alarmVolume.toFloat()) }
    var alertToneType by remember { mutableIntStateOf(settings.alertToneType) }
    var yawThreshold by remember { mutableFloatStateOf(settings.yawThresholdDegrees) }
    var pitchThreshold by remember { mutableFloatStateOf(settings.pitchThresholdDegrees) }
    var isMotionEnabled by remember { mutableStateOf(settings.isMotionDetectionEnabled) }
    var accelThreshold by remember { mutableFloatStateOf(settings.rapidAccelThresholdMps2) }
    var brakeThreshold by remember { mutableFloatStateOf(settings.harshBrakingThresholdMps2) }
    var crashThresholdG by remember { mutableFloatStateOf(settings.crashThresholdGForce) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DMS Settings") },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Battery saver switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Camera Display", fontWeight = FontWeight.Medium)
                        Text(
                            text = "Turn off video preview to save battery and reduce heat while still monitoring.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onToggleCameraPreview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCameraPreviewVisible) Color(0xFF1E88E5) else Color.DarkGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isCameraPreviewVisible) "On" else "Off")
                    }
                }

                // Alarm Volume Slider
                Column {
                    Text(
                        text = "Alarm Volume: ${alarmVolume.toInt()}%",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Hardware stream volume for driver alerts.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = alarmVolume,
                        onValueChange = { alarmVolume = it },
                        valueRange = 0f..100f
                    )
                }

                // Alert Tone Selection
                Column {
                    Text(text = "Alert Tone Style", fontWeight = FontWeight.Medium)
                    Text(
                        text = "Select warning and alarm sound effect.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tones = listOf("Standard", "Prompt", "Key DTMF")
                        tones.forEachIndexed { index, name ->
                            Button(
                                onClick = { alertToneType = index },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (alertToneType == index) Color(0xFF1E88E5) else Color.DarkGray
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(name, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                // Eye threshold slider
                Column {
                    Text(
                        text = "Eye Closed Sensitivity: ${(eyeThreshold * 100).toInt()}%",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Values below this are treated as closed eyes.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = eyeThreshold,
                        onValueChange = { eyeThreshold = it },
                        valueRange = 0.15f..0.60f
                    )
                }

                // Eye closure duration slider
                Column {
                    Text(
                        text = "Warning Trigger Time: ${warnDurationSec.format(1)}s",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "How long eyes can be closed before warning.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = warnDurationSec,
                        onValueChange = { warnDurationSec = it },
                        valueRange = 0.6f..2.5f
                    )
                }

                // Head Yaw (Looking Away) slider
                Column {
                    Text(
                        text = "Looking Away Sensitivity: ${yawThreshold.toInt()}°",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Head rotation angle before distraction alert triggers.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = yawThreshold,
                        onValueChange = { yawThreshold = it },
                        valueRange = 15f..45f
                    )
                }

                // Head Pitch (Looking Down/Phone) slider
                Column {
                    Text(
                        text = "Looking Down Sensitivity: ${pitchThreshold.toInt()}°",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Head downward tilt angle (e.g. looking at phone).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = pitchThreshold,
                        onValueChange = { pitchThreshold = it },
                        valueRange = 10f..35f
                    )
                }

                // Vehicle Motion & Crash Detection Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Motion & Crash Detection", fontWeight = FontWeight.Medium)
                        Text(
                            text = "Track harsh braking, rapid acceleration, and severe collision impacts via accelerometer.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            isMotionEnabled = !isMotionEnabled
                            onToggleMotionDetection()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMotionEnabled) Color(0xFF2E7D32) else Color.DarkGray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isMotionEnabled) "On" else "Off")
                    }
                }

                if (isMotionEnabled) {
                    // Rapid Acceleration Threshold
                    Column {
                        Text(
                            text = "Rapid Accel Threshold: ${accelThreshold.format(1)} m/s² (~${(accelThreshold / 9.8f).format(2)}g)",
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = accelThreshold,
                            onValueChange = { accelThreshold = it },
                            valueRange = 1.5f..6.0f
                        )
                    }

                    // Harsh Braking Threshold
                    Column {
                        Text(
                            text = "Harsh Braking Threshold: ${brakeThreshold.format(1)} m/s² (~${(brakeThreshold / 9.8f).format(2)}g)",
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = brakeThreshold,
                            onValueChange = { brakeThreshold = it },
                            valueRange = 2.0f..8.0f
                        )
                    }

                    // Crash Threshold
                    Column {
                        Text(
                            text = "Crash Impact Threshold: ${crashThresholdG.format(1)}g",
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = crashThresholdG,
                            onValueChange = { crashThresholdG = it },
                            valueRange = 1.5f..6.0f
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onEyeSensitivityChanged(eyeThreshold)
                    val warnMs = (warnDurationSec * 1000).toLong()
                    val critMs = warnMs + 800L
                    onDrowsyThresholdsChanged(warnMs, critMs)
                    onAlarmVolumeChanged(alarmVolume.toInt())
                    onAlertToneTypeChanged(alertToneType)
                    onDistractionAnglesChanged(yawThreshold, pitchThreshold)
                    onMotionThresholdsChanged(accelThreshold, brakeThreshold, crashThresholdG)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The Driver Monitoring System needs front camera access to track eye closure and alertness while driving. All image processing runs strictly on your device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Camera Permission")
            }
        }
    }
}

private fun Double.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)
private fun Float.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)

/**
 * Compose visual preview for the normal state alertness banner.
 */
@Preview(showBackground = true, name = "Normal Alertness HUD")
@Composable
private fun PreviewTopStatusBannerNormal() {
    DMS_AppTheme {
        TopStatusBanner(
            alertnessState = AlertnessState(
                faceDetected = true,
                alertLevel = AlertLevel.NORMAL,
                statusMessage = "Driver Alert & Protected",
                fps = 30
            ),
            isMonitoring = true
        )
    }
}

/**
 * Compose visual preview for the warning level drowsiness alert banner.
 */
@Preview(showBackground = true, name = "Drowsy Warning Alert HUD")
@Composable
private fun PreviewTopStatusBannerWarning() {
    DMS_AppTheme {
        TopStatusBanner(
            alertnessState = AlertnessState(
                faceDetected = true,
                alertLevel = AlertLevel.WARNING_DROWSY,
                statusMessage = "Caution: Eyes closing",
                eyeClosedDurationMs = 1200L,
                fps = 28
            ),
            isMonitoring = true
        )
    }
}

/**
 * Compose visual preview for the real-time driver telemetry HUD.
 */
@Preview(showBackground = true, name = "Telemetry HUD Preview")
@Composable
private fun PreviewTelemetryHud() {
    DMS_AppTheme {
        TelemetryHud(
            alertnessState = AlertnessState(
                faceDetected = true,
                leftEyeOpenProb = 0.92f,
                rightEyeOpenProb = 0.88f,
                headEulerAngleY = -5.0f
            ),
            isMonitoring = true
        )
    }
}

/**
 * Compose visual preview for the bottom floating controls bar.
 */
@Preview(showBackground = true, name = "Bottom Controls Bar Preview")
@Composable
private fun PreviewBottomControlsBar() {
    DMS_AppTheme {
        BottomControlsBar(
            isMonitoring = true,
            isAudioEnabled = true,
            isVibrationEnabled = true,
            isCameraPreviewVisible = true,
            onToggleMonitoring = {},
            onToggleCameraPreview = {},
            onLaunchFloatingBubble = {},
            onToggleAudio = {},
            onToggleVibration = {},
            onOpenSettings = {}
        )
    }
}

/**
 * Compose visual preview for the camera permission rationale screen.
 */
@Preview(showBackground = true, name = "Camera Permission Rationale Preview")
@Composable
private fun PreviewCameraPermissionRationale() {
    DMS_AppTheme {
        CameraPermissionRationale(onRequestPermission = {})
    }
}
