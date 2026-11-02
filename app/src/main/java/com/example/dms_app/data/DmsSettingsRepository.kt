package com.example.dms_app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dms_app.model.DmsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dms_settings")

/**
 * Repository interface defining persistence operations for driver monitoring settings.
 */
interface DmsSettingsRepository {
    /**
     * Flow emitting updated [DmsSettings] whenever underlying storage changes.
     */
    val settingsFlow: Flow<DmsSettings>

    /**
     * Updates stored settings using the specified transform lambda.
     *
     * @param transform Lambda receiving current [DmsSettings] and returning modified settings.
     */
    suspend fun updateSettings(transform: (DmsSettings) -> DmsSettings)
}

/**
 * Production implementation of [DmsSettingsRepository] backed by AndroidX Jetpack DataStore Preferences.
 *
 * @property dataStore Underlying [DataStore] instance holding preferences.
 */
class DmsSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : DmsSettingsRepository {

    /**
     * Secondary constructor creating a repository backed by the context default [dataStore].
     *
     * @param context Application context used to access the preferences DataStore instance.
     */
    constructor(context: Context) : this(context.dataStore)

    /**
     * Preference keys for persisted driver monitoring settings.
     */
    companion object {
        /** Preference key for eye open sensitivity threshold. */
        val KEY_EYE_OPEN_THRESHOLD = floatPreferencesKey("eye_open_threshold")
        /** Preference key for drowsiness warning duration in milliseconds. */
        val KEY_DROWSY_WARN_MS = longPreferencesKey("drowsy_warning_duration_ms")
        /** Preference key for drowsiness critical duration in milliseconds. */
        val KEY_DROWSY_CRIT_MS = longPreferencesKey("drowsy_critical_duration_ms")
        /** Preference key for distraction warning duration in milliseconds. */
        val KEY_DISTRACTION_WARN_MS = longPreferencesKey("distraction_warning_duration_ms")
        /** Preference key for distraction critical duration in milliseconds. */
        val KEY_DISTRACTION_CRIT_MS = longPreferencesKey("distraction_critical_duration_ms")
        /** Preference key for head yaw threshold in degrees. */
        val KEY_YAW_THRESHOLD = floatPreferencesKey("head_yaw_threshold_degrees")
        /** Preference key for head pitch threshold in degrees. */
        val KEY_PITCH_THRESHOLD = floatPreferencesKey("head_pitch_threshold_degrees")
        /** Preference key for audio alert enable flag. */
        val KEY_AUDIO_ENABLED = booleanPreferencesKey("is_audio_enabled")
        /** Preference key for vibration alert enable flag. */
        val KEY_VIBRATION_ENABLED = booleanPreferencesKey("is_vibration_enabled")
        /** Preference key for camera preview visibility flag. */
        val KEY_PREVIEW_VISIBLE = booleanPreferencesKey("is_camera_preview_visible")
    }

    override val settingsFlow: Flow<DmsSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapPreferencesToSettings(preferences)
        }

    override suspend fun updateSettings(transform: (DmsSettings) -> DmsSettings) {
        dataStore.edit { preferences ->
            val currentSettings = mapPreferencesToSettings(preferences)
            val newSettings = transform(currentSettings)

            preferences[KEY_EYE_OPEN_THRESHOLD] = newSettings.eyeOpenThreshold
            preferences[KEY_DROWSY_WARN_MS] = newSettings.drowsyWarningDurationMs
            preferences[KEY_DROWSY_CRIT_MS] = newSettings.drowsyCriticalDurationMs
            preferences[KEY_DISTRACTION_WARN_MS] = newSettings.distractionWarningDurationMs
            preferences[KEY_DISTRACTION_CRIT_MS] = newSettings.distractionCriticalDurationMs
            preferences[KEY_YAW_THRESHOLD] = newSettings.yawThresholdDegrees
            preferences[KEY_PITCH_THRESHOLD] = newSettings.pitchThresholdDegrees
            preferences[KEY_AUDIO_ENABLED] = newSettings.isAudioEnabled
            preferences[KEY_VIBRATION_ENABLED] = newSettings.isVibrationEnabled
            preferences[KEY_PREVIEW_VISIBLE] = newSettings.isCameraPreviewVisible
        }
    }

    /**
     * Maps stored DataStore [Preferences] to a strongly-typed [DmsSettings] domain model,
     * falling back to default values for missing keys.
     *
     * @param preferences The raw DataStore preferences to map.
     * @return Resolved [DmsSettings] with persisted values or defaults.
     */
    private fun mapPreferencesToSettings(preferences: Preferences): DmsSettings {
        val default = DmsSettings()
        return DmsSettings(
            eyeOpenThreshold = preferences[KEY_EYE_OPEN_THRESHOLD] ?: default.eyeOpenThreshold,
            drowsyWarningDurationMs = preferences[KEY_DROWSY_WARN_MS] ?: default.drowsyWarningDurationMs,
            drowsyCriticalDurationMs = preferences[KEY_DROWSY_CRIT_MS] ?: default.drowsyCriticalDurationMs,
            distractionWarningDurationMs = preferences[KEY_DISTRACTION_WARN_MS] ?: default.distractionWarningDurationMs,
            distractionCriticalDurationMs = preferences[KEY_DISTRACTION_CRIT_MS] ?: default.distractionCriticalDurationMs,
            yawThresholdDegrees = preferences[KEY_YAW_THRESHOLD] ?: default.yawThresholdDegrees,
            pitchThresholdDegrees = preferences[KEY_PITCH_THRESHOLD] ?: default.pitchThresholdDegrees,
            isAudioEnabled = preferences[KEY_AUDIO_ENABLED] ?: default.isAudioEnabled,
            isVibrationEnabled = preferences[KEY_VIBRATION_ENABLED] ?: default.isVibrationEnabled,
            isCameraPreviewVisible = preferences[KEY_PREVIEW_VISIBLE] ?: default.isCameraPreviewVisible
        )
    }
}
