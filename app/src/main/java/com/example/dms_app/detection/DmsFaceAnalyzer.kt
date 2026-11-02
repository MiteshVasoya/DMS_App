package com.example.dms_app.detection

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.DmsSettings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * ImageAnalysis.Analyzer that detects driver faces using ML Kit and delegates
 * alertness state evaluation to [AlertnessEvaluator].
 *
 * @param settings Configurable sensitivity thresholds for the DMS.
 * @param onStateUpdated Callback invoked on each processed frame with the updated [AlertnessState].
 * @param evaluator State machine evaluator instance responsible for alertness detection logic.
 */
class DmsFaceAnalyzer @JvmOverloads constructor(
    private var settings: DmsSettings,
    private val onStateUpdated: (AlertnessState) -> Unit,
    private val evaluator: AlertnessEvaluator = AlertnessEvaluator(settings)
) : ImageAnalysis.Analyzer {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.18f)
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)

    // FPS calculation
    private var frameCount = 0
    private var lastFpsTimestamp = SystemClock.elapsedRealtime()
    private var currentFps = 0

    /**
     * Updates detection settings and propagates them to the underlying [evaluator].
     *
     * @param newSettings Updated DMS configuration settings.
     */
    fun updateSettings(newSettings: DmsSettings) {
        settings = newSettings
        evaluator.updateSettings(newSettings)
    }

    /**
     * Resets the underlying [evaluator] state timers.
     */
    fun reset() {
        evaluator.reset()
    }

    /**
     * Analyzes a camera frame proxy to detect faces and evaluate alertness.
     *
     * @param imageProxy The camera frame image proxy to analyze.
     */
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

    /**
     * Evaluates detected faces against the [evaluator] and notifies [onStateUpdated].
     *
     * @param faces List of faces detected by ML Kit.
     * @param now Current timestamp in milliseconds.
     */
    private fun processFaces(faces: List<Face>, now: Long) {
        if (faces.isEmpty()) {
            val state = evaluator.evaluate(
                faceDetected = false,
                leftEyeOpen = null,
                rightEyeOpen = null,
                yaw = null,
                pitch = null,
                now = now,
                currentFps = currentFps
            )
            onStateUpdated(state)
            return
        }

        // Take the primary (largest/first) detected face
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: faces[0]

        val state = evaluator.evaluate(
            faceDetected = true,
            leftEyeOpen = face.leftEyeOpenProbability,
            rightEyeOpen = face.rightEyeOpenProbability,
            yaw = face.headEulerAngleY,
            pitch = face.headEulerAngleX,
            now = now,
            currentFps = currentFps
        )
        onStateUpdated(state)
    }

    /**
     * Closes the ML Kit face detector resources.
     */
    fun close() {
        try {
            detector.close()
        } catch (_: Exception) {
        }
    }
}
