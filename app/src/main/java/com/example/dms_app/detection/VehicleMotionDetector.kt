package com.example.dms_app.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.dms_app.model.DmsSettings
import com.example.dms_app.model.MotionEventType
import kotlin.math.sqrt

/**
 * Data structure representing a processed vehicle motion sample.
 *
 * @property accelerationMps2 Magnitude of net linear acceleration in m/s^2 (excluding gravity).
 * @property gForce Acceleration expressed in G-forces (1g = 9.80665 m/s^2).
 * @property motionEvent The detected physical event (normal, rapid acceleration, harsh braking, crash).
 */
data class VehicleMotionReading(
    val accelerationMps2: Float = 0f,
    val gForce: Float = 0f,
    val motionEvent: MotionEventType = MotionEventType.NORMAL
)

/**
 * Real-time vehicle dynamics and impact detector using the device's inertial sensors.
 *
 * Uses [Sensor.TYPE_LINEAR_ACCELERATION] (gravity isolated via sensor fusion), falling back
 * to high-pass filtered [Sensor.TYPE_ACCELEROMETER] when linear acceleration is unavailable.
 *
 * Includes noise debouncing and thresholding to reliably differentiate potholes/bumps
 * from prolonged harsh acceleration, severe braking, or collision impacts.
 *
 * @param context Optional Android context for acquiring the [SensorManager].
 * @param settings Initial sensitivity thresholds.
 * @param onMotionReading Callback invoked when new motion readings are processed.
 */
class VehicleMotionDetector(
    context: Context? = null,
    var settings: DmsSettings = DmsSettings(),
    private val onMotionReading: (VehicleMotionReading) -> Unit = {}
) : SensorEventListener {

    private val sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var isListening = false

    // Gravity filter for fallback accelerometer
    private val gravity = FloatArray(3)
    private var hasGravity = false
    private val alpha = 0.8f // Low-pass filter coefficient for gravity estimation

    // Debounce & sustain tracking
    private var eventStartTime = 0L
    private var activeEvent = MotionEventType.NORMAL
    private var lastCrashTriggerTime = -CRASH_COOLDOWN_MS

    companion object {
        const val STANDARD_GRAVITY = 9.80665f

        /** Minimum sustained duration of acceleration/braking before triggering an alert (filtering brief potholes/road bumps). */
        const val MIN_SUSTAINED_EVENT_DURATION_MS = 250L

        /** Cooldown duration after crash alert to prevent repeated spamming. */
        const val CRASH_COOLDOWN_MS = 10000L
    }

    /**
     * Starts monitoring motion sensors at UI rate (~50-60Hz).
     */
    fun startListening() {
        if (isListening || sensorManager == null) return

        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearSensor != null) {
            sensorManager.registerListener(this, linearSensor, SensorManager.SENSOR_DELAY_UI)
            isListening = true
        } else {
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelSensor != null) {
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
                isListening = true
            }
        }
    }

    /**
     * Stops sensor listening and clears state.
     */
    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        hasGravity = false
        activeEvent = MotionEventType.NORMAL
        eventStartTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !settings.isMotionDetectionEnabled) return

        val now = System.currentTimeMillis()
        val linearAccel = FloatArray(3)

        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            linearAccel[0] = event.values[0]
            linearAccel[1] = event.values[1]
            linearAccel[2] = event.values[2]
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Apply high-pass filter: linearAccel = raw - gravity
            if (!hasGravity) {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
                hasGravity = true
            } else {
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
            }
            linearAccel[0] = event.values[0] - gravity[0]
            linearAccel[1] = event.values[1] - gravity[1]
            linearAccel[2] = event.values[2] - gravity[2]
        } else {
            return
        }

        processLinearAcceleration(linearAccel[0], linearAccel[1], linearAccel[2], now)
    }

    /**
     * Evaluates 3D linear acceleration components against motion thresholds and duration gates.
     * Pure testable logic that can be invoked directly in unit tests without hardware mocks.
     *
     * @param ax Linear acceleration X component in m/s^2.
     * @param ay Linear acceleration Y component in m/s^2.
     * @param az Linear acceleration Z component in m/s^2.
     * @param timestampMs Monotonic or wall-clock timestamp in milliseconds.
     * @return Resulting [VehicleMotionReading].
     */
    fun processLinearAcceleration(
        ax: Float,
        ay: Float,
        az: Float,
        timestampMs: Long = System.currentTimeMillis()
    ): VehicleMotionReading {
        val magnitudeMps2 = sqrt(ax * ax + ay * ay + az * az)
        val gForce = magnitudeMps2 / STANDARD_GRAVITY

        // 1. Crash Detection: High-G instantaneous spike
        if (gForce >= settings.crashThresholdGForce &&
            (timestampMs - lastCrashTriggerTime > CRASH_COOLDOWN_MS)
        ) {
            lastCrashTriggerTime = timestampMs
            activeEvent = MotionEventType.CRASH_DETECTED
            val reading = VehicleMotionReading(
                accelerationMps2 = magnitudeMps2,
                gForce = gForce,
                motionEvent = MotionEventType.CRASH_DETECTED
            )
            onMotionReading(reading)
            return reading
        }

        // 2. Harsh Braking vs Rapid Acceleration
        // If Y is primary vehicle axis (standard portrait dashboard mount), ay < 0 indicates deceleration (braking).
        // If phone orientation is arbitrary, net magnitude above braking threshold is classified as harsh braking,
        // and between accel and braking threshold as rapid acceleration.
        val potentialEvent = when {
            ay <= -settings.harshBrakingThresholdMps2 || magnitudeMps2 >= settings.harshBrakingThresholdMps2 -> {
                MotionEventType.HARSH_BRAKING
            }
            ay >= settings.rapidAccelThresholdMps2 || magnitudeMps2 >= settings.rapidAccelThresholdMps2 -> {
                MotionEventType.RAPID_ACCELERATION
            }
            else -> MotionEventType.NORMAL
        }

        // Debounce: verify potential event is sustained for at least MIN_SUSTAINED_EVENT_DURATION_MS
        val resolvedEvent: MotionEventType = if (potentialEvent != MotionEventType.NORMAL) {
            if (activeEvent != potentialEvent) {
                activeEvent = potentialEvent
                eventStartTime = timestampMs
                MotionEventType.NORMAL
            } else if (timestampMs - eventStartTime >= MIN_SUSTAINED_EVENT_DURATION_MS) {
                potentialEvent
            } else {
                MotionEventType.NORMAL
            }
        } else {
            activeEvent = MotionEventType.NORMAL
            eventStartTime = 0L
            MotionEventType.NORMAL
        }

        val reading = VehicleMotionReading(
            accelerationMps2 = magnitudeMps2,
            gForce = gForce,
            motionEvent = resolvedEvent
        )
        onMotionReading(reading)
        return reading
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
