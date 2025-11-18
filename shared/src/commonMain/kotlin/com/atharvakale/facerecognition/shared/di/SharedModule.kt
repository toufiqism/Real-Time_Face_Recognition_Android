package com.atharvakale.facerecognition.shared.di

import com.atharvakale.facerecognition.shared.controller.FaceRecognitionController
import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository
import com.atharvakale.facerecognition.shared.domain.repository.RecognitionSettingsRepository
import com.atharvakale.facerecognition.shared.platform.PermissionsManager

object SharedModule {
    fun provideFaceRecognitionController(
        faceRepository: FaceRepository,
        settingsRepository: RecognitionSettingsRepository,
        permissionsManager: PermissionsManager
    ): FaceRecognitionController = FaceRecognitionController(
        faceRepository = faceRepository,
        settingsRepository = settingsRepository,
        permissionsManager = permissionsManager
    )
}

