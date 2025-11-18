package com.atharvakale.facerecognition.shared.domain.usecase

import com.atharvakale.facerecognition.shared.core.model.FaceProfile
import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository

class RegisterFaceUseCase(
    private val repository: FaceRepository
) {
    suspend operator fun invoke(profile: FaceProfile) {
        repository.upsert(profile)
    }
}

