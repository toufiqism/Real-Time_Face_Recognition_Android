package com.atharvakale.facerecognition.shared.domain.usecase

import com.atharvakale.facerecognition.shared.core.state.RecognitionSettings
import com.atharvakale.facerecognition.shared.domain.repository.RecognitionSettingsRepository

class UpdateSettingsUseCase(
    private val repository: RecognitionSettingsRepository
) {
    suspend operator fun invoke(transform: (RecognitionSettings) -> RecognitionSettings): RecognitionSettings {
        val current = repository.get()
        val updated = transform(current)
        if (updated != current) {
            repository.save(updated)
        }
        return updated
    }
}

