package com.atharvakale.facerecognition.shared.domain.repository

import android.content.Context
import com.atharvakale.facerecognition.shared.core.state.RecognitionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharedPreferencesRecognitionSettingsRepository(
    context: Context
) : RecognitionSettingsRepository {

    private val distancePrefs = context.applicationContext.getSharedPreferences(DISTANCE_PREFS, Context.MODE_PRIVATE)
    private val settingsPrefs = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun get(): RecognitionSettings = mutex.withLock {
        withContext(Dispatchers.IO) {
            RecognitionSettings(
                distanceThreshold = distancePrefs.getFloat(KEY_DISTANCE, 1.0f),
                developerMode = settingsPrefs.getBoolean(KEY_DEV_MODE, false),
                flipCameraHorizontally = settingsPrefs.getBoolean(KEY_FLIP, false)
            )
        }
    }

    override suspend fun save(settings: RecognitionSettings) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                distancePrefs.edit().putFloat(KEY_DISTANCE, settings.distanceThreshold).apply()
                settingsPrefs.edit()
                    .putBoolean(KEY_DEV_MODE, settings.developerMode)
                    .putBoolean(KEY_FLIP, settings.flipCameraHorizontally)
                    .apply()
            }
        }
    }

    companion object {
        private const val DISTANCE_PREFS = "Distance"
        private const val SETTINGS_PREFS = "RecognitionSettings"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_DEV_MODE = "developerMode"
        private const val KEY_FLIP = "flipCameraHorizontally"
    }
}

