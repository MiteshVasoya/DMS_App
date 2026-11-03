package com.example.dms_app.overlay

import com.example.dms_app.R
import com.example.dms_app.model.AlertLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [DmsOverlayManager] visual styling mappings, icon resolution,
 * and critical alert level classifications for the floating overlay bubble.
 */
class DmsOverlayManagerTest {

    /**
     * Verifies visual resolution for critical drowsiness alert level.
     */
    @Test
    fun testResolveVisualsForCriticalDrowsy() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.CRITICAL_DROWSY)
        assertEquals(0xFFE53935.toInt(), visuals.backgroundColor)
        assertEquals(0xFFFFCDD2.toInt(), visuals.strokeColor)
        assertEquals(0xFFFF1744.toInt(), visuals.dotColor)
        assertEquals(android.R.drawable.stat_notify_error, visuals.iconRes)
        assertTrue(visuals.isCritical)
    }

    /**
     * Verifies visual resolution for critical distraction alert level.
     */
    @Test
    fun testResolveVisualsForCriticalDistracted() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.CRITICAL_DISTRACTED)
        assertEquals(0xFFE53935.toInt(), visuals.backgroundColor)
        assertEquals(0xFFFFCDD2.toInt(), visuals.strokeColor)
        assertEquals(0xFFFF1744.toInt(), visuals.dotColor)
        assertEquals(android.R.drawable.stat_notify_error, visuals.iconRes)
        assertTrue(visuals.isCritical)
    }

    /**
     * Verifies visual resolution for critical crash alert level.
     */
    @Test
    fun testResolveVisualsForCriticalCrash() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.CRITICAL_CRASH)
        assertEquals(0xFFE53935.toInt(), visuals.backgroundColor)
        assertEquals(0xFFFFCDD2.toInt(), visuals.strokeColor)
        assertEquals(0xFFFF1744.toInt(), visuals.dotColor)
        assertEquals(android.R.drawable.stat_notify_error, visuals.iconRes)
        assertTrue(visuals.isCritical)
    }

    /**
     * Verifies visual resolution for warning harsh braking alert level.
     */
    @Test
    fun testResolveVisualsForHarshBraking() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.HARSH_BRAKING)
        assertEquals(0xFFFB8C00.toInt(), visuals.backgroundColor)
        assertEquals(0xFFFFE0B2.toInt(), visuals.strokeColor)
        assertEquals(0xFFFF9100.toInt(), visuals.dotColor)
        assertEquals(android.R.drawable.stat_notify_sync, visuals.iconRes)
        assertFalse(visuals.isCritical)
    }

    /**
     * Verifies visual resolution for warning harsh acceleration alert level.
     */
    @Test
    fun testResolveVisualsForHarshAcceleration() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.HARSH_ACCELERATION)
        assertEquals(0xFFFB8C00.toInt(), visuals.backgroundColor)
        assertEquals(0xFFFFE0B2.toInt(), visuals.strokeColor)
        assertEquals(0xFFFF9100.toInt(), visuals.dotColor)
        assertEquals(android.R.drawable.stat_notify_sync, visuals.iconRes)
        assertFalse(visuals.isCritical)
    }
    /**
     * Verifies visual resolution for warning drowsiness alert level.
     */
    @Test
    fun testResolveVisualsForWarningDrowsy() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.WARNING_DROWSY)
        assertEquals(0xFFFB8C00.toInt(), visuals.backgroundColor)
        assertEquals(0xFFFFE0B2.toInt(), visuals.strokeColor)
        assertEquals(0xFFFF9100.toInt(), visuals.dotColor)
        assertEquals(android.R.drawable.stat_notify_sync, visuals.iconRes)
        assertFalse(visuals.isCritical)
    }

    /**
     * Verifies visual resolution for warning distraction alert level.
     */
    @Test
    fun testResolveVisualsForWarningDistracted() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.WARNING_DISTRACTED)
        assertEquals(0xFFFB8C00.toInt(), visuals.backgroundColor)
        assertEquals(0xFFFFE0B2.toInt(), visuals.strokeColor)
        assertEquals(0xFFFF9100.toInt(), visuals.dotColor)
        assertEquals(android.R.drawable.stat_notify_sync, visuals.iconRes)
        assertFalse(visuals.isCritical)
    }

    /**
     * Verifies visual resolution for normal driver status.
     */
    @Test
    fun testResolveVisualsForNormalStatus() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.NORMAL)
        assertEquals(0xFF2E7D32.toInt(), visuals.backgroundColor)
        assertEquals(0xFFA5D6A7.toInt(), visuals.strokeColor)
        assertEquals(0xFF00E676.toInt(), visuals.dotColor)
        assertEquals(R.drawable.ic_dms_eye, visuals.iconRes)
        assertFalse(visuals.isCritical)
    }

    /**
     * Verifies visual resolution when driver face is not detected.
     */
    @Test
    fun testResolveVisualsForFaceNotDetected() {
        val visuals = DmsOverlayManager.resolveVisuals(AlertLevel.FACE_NOT_DETECTED)
        assertEquals(0xFF424242.toInt(), visuals.backgroundColor)
        assertEquals(0xFF9E9E9E.toInt(), visuals.strokeColor)
        assertEquals(0xFF757575.toInt(), visuals.dotColor)
        assertEquals(R.drawable.ic_dms_eye, visuals.iconRes)
        assertFalse(visuals.isCritical)
    }
}
