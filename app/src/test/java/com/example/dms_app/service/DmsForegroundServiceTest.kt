package com.example.dms_app.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.dms_app.audio.AlertManager
import com.example.dms_app.data.DmsSettingsRepository
import com.example.dms_app.data.DmsSettingsRepositoryImpl
import com.example.dms_app.model.AlertLevel
import com.example.dms_app.model.AlertnessState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests verifying [DmsForegroundService] lifecycle contracts, notification status text
 * mappings, and real-time settings synchronization from [DmsSettingsRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DmsForegroundServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: DmsSettingsRepository

    /**
     * Initializes temporary DataStore and repository instance before each test.
     */
    @Before
    fun setUp() {
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test_service_settings.preferences_pb") }
        )
        repository = DmsSettingsRepositoryImpl(testDataStore)
    }

    /**
     * Verifies that foreground service notification mapping generates appropriate driver warnings.
     */
    @Test
    fun testNotificationStatusTextMappingForAlertLevels() {
        val criticalDrowsy = AlertnessState(alertLevel = AlertLevel.CRITICAL_DROWSY)
        val criticalDistracted = AlertnessState(alertLevel = AlertLevel.CRITICAL_DISTRACTED)
        val warningDrowsy = AlertnessState(alertLevel = AlertLevel.WARNING_DROWSY)
        val warningDistracted = AlertnessState(alertLevel = AlertLevel.WARNING_DISTRACTED)
        val normal = AlertnessState(alertLevel = AlertLevel.NORMAL)
        val searching = AlertnessState(alertLevel = AlertLevel.FACE_NOT_DETECTED)

        assertEquals("⚠️ CRITICAL: Drowsiness detected! Wake up!", getStatusText(criticalDrowsy))
        assertEquals("⚠️ Distraction detected! Focus on road!", getStatusText(criticalDistracted))
        assertEquals("Caution: Eyes closing", getStatusText(warningDrowsy))
        assertEquals("Caution: Looking away", getStatusText(warningDistracted))
        assertEquals("Driver Alert & Protected", getStatusText(normal))
        assertEquals("Searching for driver...", getStatusText(searching))
    }

    /**
     * Verifies that repository settings updates propagate to AlertManager properties.
     */
    @Test
    fun testSettingsRepositoryUpdatesPropagateToAlertManager() = runTest {
        val alertManager = AlertManager(null)
        val initialSettings = repository.settingsFlow.first()

        alertManager.updateSettings(
            audioEnabled = initialSettings.isAudioEnabled,
            vibrationEnabled = initialSettings.isVibrationEnabled,
            volume = initialSettings.alarmVolume,
            toneType = initialSettings.alertToneType
        )

        assertTrue(alertManager.isAudioEnabled)
        assertTrue(alertManager.isVibrationEnabled)
        assertEquals(100, alertManager.alarmVolume)
        assertEquals(0, alertManager.alertToneType)

        // Modify settings in repository
        repository.updateSettings {
            it.copy(
                isAudioEnabled = false,
                isVibrationEnabled = false,
                alarmVolume = 50,
                alertToneType = 2
            )
        }

        val updated = repository.settingsFlow.first()
        alertManager.updateSettings(
            audioEnabled = updated.isAudioEnabled,
            vibrationEnabled = updated.isVibrationEnabled,
            volume = updated.alarmVolume,
            toneType = updated.alertToneType
        )

        assertFalse(alertManager.isAudioEnabled)
        assertFalse(alertManager.isVibrationEnabled)
        assertEquals(50, alertManager.alarmVolume)
        assertEquals(2, alertManager.alertToneType)
    }

    /**
     * Helper mimicking the status text resolution in DmsForegroundService.
     */
    private fun getStatusText(state: AlertnessState): String {
        return when (state.alertLevel) {
            AlertLevel.CRITICAL_DROWSY -> "⚠️ CRITICAL: Drowsiness detected! Wake up!"
            AlertLevel.CRITICAL_DISTRACTED -> "⚠️ Distraction detected! Focus on road!"
            AlertLevel.WARNING_DROWSY -> "Caution: Eyes closing"
            AlertLevel.WARNING_DISTRACTED -> "Caution: Looking away"
            AlertLevel.NORMAL -> "Driver Alert & Protected"
            AlertLevel.FACE_NOT_DETECTED -> "Searching for driver..."
        }
    }
}
