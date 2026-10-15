package com.example.dms_app.detection

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

/**
 * ImageAnalysis.Analyzer that detects driver faces using ML Kit,
 * calculating eye closure time and head orientation to detect drowsiness and distraction.
 */
class DmsFaceAnalyzer(
    private var settings: DmsSettings,
    private val onStateUpdated: (AlertnessState) -> Unit
) : ImageAnalysis.Analyzer {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.18f)
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)

    // Tracking timestamps
    private var eyeClosedStartTime: Long? = null
    private var distractionStartTime: Long? = null
    private var noFaceStartTime: Long? = null

    // FPS calculation
    private var frameCount = 0
    private var lastFpsTimestamp = SystemClock.elapsedRealtime()
    private var currentFps = 0

    fun updateSettings(newSettings: DmsSettings) {
        settings = newSettings
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Calculate FPS
        frameCount++
        val now = SystemClock.elapsedRealtime()
        if (now - lastFpsTimestamp >= 1000L) {
            currentFps = frameCount
            frameCount = 0
            lastFpsTimestamp = now
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                processFaces(faces, now)
            }
            .addOnFailureListener {
                // If detection fails, maintain safe fallback
                onStateUpdated(
                    AlertnessState(
                        faceDetected = false,
                        statusMessage = "Detection error",
                        fps = currentFps
                    )
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processFaces(faces: List<Face>, now: Long) {
        if (faces.isEmpty()) {
            if (noFaceStartTime == null) {
                noFaceStartTime = now
            }
            eyeClosedStartTime = null
            distractionStartTime = null

            val noFaceDuration = now - (noFaceStartTime ?: now)
            val alertLevel = if (noFaceDuration > settings.distractionWarningDurationMs) {
                AlertLevel.WARNING_DISTRACTED
            } else {
                AlertLevel.FACE_NOT_DETECTED
            }

            onStateUpdated(
                AlertnessState(
                    faceDetected = false,
                    alertLevel = alertLevel,
                    statusMessage = if (noFaceDuration > settings.distractionWarningDurationMs) {
                        "Driver not detected! Watch road!"
                    } else {
                        "Searching for driver's face..."
                    },
                    fps = currentFps
                )
            )
            return
        }

        noFaceStartTime = null
        // Take the primary (largest/first) detected face
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: faces[0]

        val leftEyeOpen = face.leftEyeOpenProbability
        val rightEyeOpen = face.rightEyeOpenProbability
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX

        // 1. Drowsiness Check: Eyes closed threshold
        val areEyesClosed = if (leftEyeOpen != null && rightEyeOpen != null) {
            leftEyeOpen < settings.eyeOpenThreshold && rightEyeOpen < settings.eyeOpenThreshold
        } else if (leftEyeOpen != null) {
            leftEyeOpen < settings.eyeOpenThreshold
        } else if (rightEyeOpen != null) {
            rightEyeOpen < settings.eyeOpenThreshold
        } else {
            false
        }

        var eyeClosedDuration = 0L
        if (areEyesClosed) {
            if (eyeClosedStartTime == null) {
                eyeClosedStartTime = now
            }
            eyeClosedDuration = now - (eyeClosedStartTime ?: now)
        } else {
            eyeClosedStartTime = null
        }

        // 2. Distraction Check: Head yaw or pitch excessive
        val isHeadTurned = (yaw != null && abs(yaw) > settings.yawThresholdDegrees) ||
                (pitch != null && abs(pitch) > settings.pitchThresholdDegrees)

        var distractionDuration = 0L
        if (isHeadTurned) {
            if (distractionStartTime == null) {
                distractionStartTime = now
            }
            distractionDuration = now - (distractionStartTime ?: now)
        } else {
            distractionStartTime = null
        }

        // Determine alertness level & status message
        val (level, message) = when {
            eyeClosedDuration >= settings.drowsyCriticalDurationMs -> {
                AlertLevel.CRITICAL_DROWSY to "WAKE UP! EYES CLOSED!"
            }
            eyeClosedDuration >= settings.drowsyWarningDurationMs -> {
                AlertLevel.WARNING_DROWSY to "Warning: Eyes closed!"
            }
            distractionDuration >= settings.distractionCriticalDurationMs -> {
                AlertLevel.CRITICAL_DISTRACTED to "DANGER: LOOK AT ROAD!"
            }
            distractionDuration >= settings.distractionWarningDurationMs -> {
                AlertLevel.WARNING_DISTRACTED to "Please focus on the road!"
            }
            else -> {
                AlertLevel.NORMAL to "Driver Alert"
            }
        }

        onStateUpdated(
            AlertnessState(
                faceDetected = true,
                leftEyeOpenProb = leftEyeOpen,
                rightEyeOpenProb = rightEyeOpen,
                headEulerAngleY = yaw,
                headEulerAngleX = pitch,
                eyeClosedDurationMs = eyeClosedDuration,
                distractedDurationMs = distractionDuration,
                alertLevel = level,
                statusMessage = message,
                fps = currentFps
            )
        )
    }

    fun close() {
        try {
            detector.close()
        } catch (_: Exception) {
        }
    }
}
