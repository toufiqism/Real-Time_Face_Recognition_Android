package com.atharvakale.facerecognition.shared

import com.atharvakale.facerecognition.shared.controller.FaceRecognitionController
import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository
import com.atharvakale.facerecognition.shared.domain.repository.RecognitionSettingsRepository
import com.atharvakale.facerecognition.shared.platform.PermissionsManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Central entry-point for wiring the shared face-recognition domain layer.
 */
object FaceRecognitionSDK {
    /**
     * Creates a [FaceRecognitionController] with Single Source of Truth state flows.
     *
     * @param faceRepository platform-specific data-source (SharedPreferences/CoreData/etc.)
     * @param settingsRepository handles threshold + developer preferences
     * @param permissionsManager abstracts camera permission orchestration
     * @param dispatcher dispatcher for shared coroutines (Default for CPU work)
     */
    fun createController(
        faceRepository: FaceRepository,
        settingsRepository: RecognitionSettingsRepository,
        permissionsManager: PermissionsManager,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): FaceRecognitionController {
        return FaceRecognitionController(
            faceRepository = faceRepository,
            settingsRepository = settingsRepository,
            permissionsManager = permissionsManager,
            dispatcher = dispatcher
        )
    }
}

