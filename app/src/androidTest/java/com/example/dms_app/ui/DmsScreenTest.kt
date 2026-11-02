package com.example.dms_app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dms_app.audio.AlertManager
import com.example.dms_app.ui.theme.DMS_AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests verifying [DmsScreen] components, permission rationale display,
 * HUD readouts, controls interaction, and settings dialog behavior on Android devices.
 */
@RunWith(AndroidJUnit4::class)
class DmsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Verifies that when camera permission is not granted, the permission rationale is shown.
     */
    @Test
    fun testCameraPermissionRationaleDisplayedWhenPermissionDenied() {
        composeTestRule.setContent {
            DMS_AppTheme {
                DmsScreen(initialCameraPermission = false)
            }
        }

        composeTestRule
            .onNodeWithText("Camera Permission Required")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Grant Camera Permission")
            .assertIsDisplayed()
    }

    /**
     * Verifies that the dashboard HUD and controls are rendered when camera permission is granted.
     */
    @Test
    fun testDashboardDisplayedWhenPermissionGranted() {
        val viewModel = DmsViewModel(alertManager = AlertManager(null))

        composeTestRule.setContent {
            DMS_AppTheme {
                DmsScreen(
                    viewModel = viewModel,
                    initialCameraPermission = true
                )
            }
        }

        composeTestRule
            .onNodeWithText("Pause")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Settings")
            .assertIsDisplayed()
    }

    /**
     * Verifies that clicking the Pause/Start button toggles monitoring state.
     */
    @Test
    fun testToggleMonitoringButtonUpdatesState() {
        val viewModel = DmsViewModel(alertManager = AlertManager(null))

        composeTestRule.setContent {
            DMS_AppTheme {
                DmsScreen(
                    viewModel = viewModel,
                    initialCameraPermission = true
                )
            }
        }

        assertTrue(viewModel.isMonitoring.value)

        composeTestRule
            .onNodeWithText("Pause")
            .performClick()

        assertFalse(viewModel.isMonitoring.value)

        composeTestRule
            .onNodeWithText("Start")
            .assertIsDisplayed()
    }

    /**
     * Verifies that clicking the settings button opens the DMS Settings dialog.
     */
    @Test
    fun testSettingsDialogOpensAndDismisses() {
        val viewModel = DmsViewModel(alertManager = AlertManager(null))

        composeTestRule.setContent {
            DMS_AppTheme {
                DmsScreen(
                    viewModel = viewModel,
                    initialCameraPermission = true
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Settings")
            .performClick()

        composeTestRule
            .onNodeWithText("DMS Settings")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Camera Display")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        composeTestRule
            .onNodeWithText("DMS Settings")
            .assertDoesNotExist()
    }
}
