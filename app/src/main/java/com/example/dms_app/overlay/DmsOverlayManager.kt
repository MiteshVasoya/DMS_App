package com.example.dms_app.overlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.dms_app.MainActivity
import com.example.dms_app.R
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import com.example.dms_app.model.MotionEventType
import kotlin.math.abs

/**
 * Manages the draggable floating bubble that stays on top of Google Maps / Waze.
 * Visually displays the driver's alertness status (Normal, Warning, Critical)
 * and animates pulse effects when an alert is triggered.
 */
class DmsOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var bubbleContainer: FrameLayout? = null
    private var statusIcon: ImageView? = null
    private var textDmsTitle: TextView? = null
    private var textEyeLeft: TextView? = null
    private var textEyeRight: TextView? = null
    private var badgeDot: View? = null

    private var pulseAnimator: ValueAnimator? = null
    private var currentAlertLevel: AlertLevel = AlertLevel.NORMAL

    fun showOverlay() {
        if (!Settings.canDrawOverlays(context) || overlayView != null) return

        mainHandler.post {
            createAndAddOverlay()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndAddOverlay() {
        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_dms_overlay_bubble, null)
        overlayView = view

        bubbleContainer = view.findViewById(R.id.bubble_container)
        statusIcon = view.findViewById(R.id.img_status_icon)
        textDmsTitle = view.findViewById(R.id.txt_dms_title)
        textEyeLeft = view.findViewById(R.id.txt_eye_left)
        textEyeRight = view.findViewById(R.id.txt_eye_right)
        badgeDot = view.findViewById(R.id.view_badge_dot)

        setupDragAndClick(view)

        try {
            windowManager.addView(view, layoutParams)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndClick(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        view.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isClick = false
                    }

                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    try {
                        windowManager.updateViewLayout(overlayView, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        openMainActivity()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    fun updateState(state: AlertnessState) {
        if (overlayView == null) return

        mainHandler.post {
            val leftProb = state.leftEyeOpenProb
            val rightProb = state.rightEyeOpenProb

            textEyeLeft?.text = if (leftProb != null) "L:${(leftProb * 100).toInt()}%" else "L:--"
            textEyeRight?.text = if (rightProb != null) "R:${(rightProb * 100).toInt()}%" else "R:--"

            // Display dynamic title if motion event active
            textDmsTitle?.text = when (state.motionEvent) {
                MotionEventType.CRASH_DETECTED -> "CRASH!"
                MotionEventType.HARSH_BRAKING -> "BRAKE!"
                MotionEventType.RAPID_ACCELERATION -> "ACCEL!"
                MotionEventType.NORMAL -> "DMS Active"
            }

            val (bgColor, strokeColor, dotColor) = when (state.alertLevel) {
                AlertLevel.CRITICAL_DROWSY, AlertLevel.CRITICAL_DISTRACTED, AlertLevel.CRITICAL_CRASH -> {
                    Triple("#E53935".toColorInt(), "#FFCDD2".toColorInt(), "#FF1744".toColorInt())
                }
                AlertLevel.WARNING_DROWSY, AlertLevel.WARNING_DISTRACTED,
                AlertLevel.HARSH_BRAKING, AlertLevel.HARSH_ACCELERATION -> {
                    Triple("#FB8C00".toColorInt(), "#FFE0B2".toColorInt(), "#FF9100".toColorInt())
                }
                AlertLevel.NORMAL -> {
                    Triple("#2E7D32".toColorInt(), "#A5D6A7".toColorInt(), "#00E676".toColorInt())
                }
                AlertLevel.FACE_NOT_DETECTED -> {
                    Triple("#424242".toColorInt(), "#9E9E9E".toColorInt(), "#757575".toColorInt())
                }
            }

            badgeDot?.background = createCircleDrawable(dotColor)

            if (state.alertLevel != currentAlertLevel) {
                currentAlertLevel = state.alertLevel
                handleAlertAnimation(state.alertLevel, bgColor, strokeColor)
            }
        }
    }

    private fun handleAlertAnimation(level: AlertLevel, baseColor: Int, strokeColor: Int) {
        pulseAnimator?.cancel()
        pulseAnimator = null

        when (level) {
            AlertLevel.CRITICAL_DROWSY, AlertLevel.CRITICAL_DISTRACTED, AlertLevel.CRITICAL_CRASH -> {
                // Urgent flashing pulse
                val flashColor = "#B71C1C".toColorInt()
                pulseAnimator = ValueAnimator.ofObject(ArgbEvaluator(), baseColor, flashColor).apply {
                    duration = 200
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { anim ->
                        val color = anim.animatedValue as Int
                        bubbleContainer?.background = createRoundedRectDrawable(color, strokeColor, 8)
                    }
                    start()
                }
            }
            AlertLevel.WARNING_DROWSY, AlertLevel.WARNING_DISTRACTED,
            AlertLevel.HARSH_BRAKING, AlertLevel.HARSH_ACCELERATION -> {
                // Caution slow pulse
                val flashColor = "#E65100".toColorInt()
                pulseAnimator = ValueAnimator.ofObject(ArgbEvaluator(), baseColor, flashColor).apply {
                    duration = 450
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { anim ->
                        val color = anim.animatedValue as Int
                        bubbleContainer?.background = createRoundedRectDrawable(color, strokeColor, 6)
                    }
                    start()
                }
            }
            else -> {
                bubbleContainer?.background = createRoundedRectDrawable(baseColor, strokeColor, 4)
            }
        }
    }

    private fun createRoundedRectDrawable(bgColor: Int, strokeColor: Int, strokeWidthDp: Int): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(bgColor)
            setStroke((strokeWidthDp * density).toInt(), strokeColor)
        }
    }

    private fun createCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    fun removeOverlay() {
        mainHandler.post {
            pulseAnimator?.cancel()
            pulseAnimator = null

            overlayView?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {
                }
            }
            overlayView = null
        }
    }
}
