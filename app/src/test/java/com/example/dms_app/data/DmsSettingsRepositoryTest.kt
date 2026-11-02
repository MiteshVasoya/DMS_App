package com.example.dms_app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.dms_app.model.DmsSettings
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
 * Unit tests verifying [DmsSettingsRepositoryImpl] preferences serialization,
 * default fallback values, and update transformations using an isolated test DataStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DmsSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: DmsSettingsRepository

    /**
     * Set up temporary test DataStore instance before each test method execution.
     */
    @Before
    fun setUp() {
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test_dms_settings.preferences_pb") }
        )
        repository = DmsSettingsRepositoryImpl(testDataStore)
    }

    /**
     * Verifies that the initial settings flow emits standard production defaults.
     */
    @Test
    fun testInitialSettingsFlowEmitsProductionDefaults() = runTest {
        val initial = repository.settingsFlow.first()
        assertEquals(DmsSettings(), initial)
    }

    /**
     * Verifies that updating eye sensitivity threshold persists to DataStore.
     */
    @Test
    fun testUpdateEyeSensitivityThresholdPersistsValue() = runTest {
        repository.updateSettings { it.copy(eyeOpenThreshold = 0.42f) }
        val updated = repository.settingsFlow.first()
        assertEquals(0.42f, updated.eyeOpenThreshold, 0.001f)
    }

    /**
     * Verifies that updating drowsiness warning and critical durations persists.
     */
    @Test
    fun testUpdateDrowsyThresholdsPersistsValues() = runTest {
        repository.updateSettings {
            it.copy(drowsyWarningDurationMs = 1250L, drowsyCriticalDurationMs = 2100L)
        }
        val updated = repository.settingsFlow.first()
        assertEquals(1250L, updated.drowsyWarningDurationMs)
        assertEquals(2100L, updated.drowsyCriticalDurationMs)
    }

    /**
     * Verifies that updating distraction thresholds persists head yaw and pitch.
     */
    @Test
    fun testUpdateDistractionThresholdsPersistsAnglesAndDurations() = runTest {
        repository.updateSettings {
            it.copy(
                yawThresholdDegrees = 32f,
                pitchThresholdDegrees = 25f,
                distractionWarningDurationMs = 1800L,
                distractionCriticalDurationMs = 2800L
            )
        }
        val updated = repository.settingsFlow.first()
        assertEquals(32f, updated.yawThresholdDegrees, 0.001f)
        assertEquals(25f, updated.pitchThresholdDegrees, 0.001f)
        assertEquals(1800L, updated.distractionWarningDurationMs)
        assertEquals(2800L, updated.distractionCriticalDurationMs)
    }

    /**
     * Verifies that toggling feature flags (audio, vibration, preview) persists state.
     */
    @Test
    fun testToggleFeatureFlagsPersistsBooleans() = runTest {
        repository.updateSettings {
            it.copy(
                isAudioEnabled = false,
                isVibrationEnabled = false,
                isCameraPreviewVisible = false
            )
        }
        val updated = repository.settingsFlow.first()
        assertFalse(updated.isAudioEnabled)
        assertFalse(updated.isVibrationEnabled)
        assertFalse(updated.isCameraPreviewVisible)

        repository.updateSettings { it.copy(isAudioEnabled = true) }
        val reUpdated = repository.settingsFlow.first()
        assertTrue(reUpdated.isAudioEnabled)
    }
}
