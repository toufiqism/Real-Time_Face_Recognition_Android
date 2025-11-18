package com.atharvakale.facerecognition.shared.domain.usecase

import com.atharvakale.facerecognition.shared.core.model.FaceProfile
import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository

class ReplaceFacesUseCase(
    private val repository: FaceRepository
) {
    suspend operator fun invoke(faces: List<FaceProfile>) {
        repository.deleteAll()
        if (faces.isNotEmpty()) {
            repository.upsertAll(faces)
        }
    }
}

