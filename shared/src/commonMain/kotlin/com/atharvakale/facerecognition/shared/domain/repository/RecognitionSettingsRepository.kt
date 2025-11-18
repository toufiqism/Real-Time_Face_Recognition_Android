package com.atharvakale.facerecognition.shared.domain.repository

import com.atharvakale.facerecognition.shared.core.state.RecognitionSettings

interface RecognitionSettingsRepository {
    suspend fun get(): RecognitionSettings
    suspend fun save(settings: RecognitionSettings)
}

