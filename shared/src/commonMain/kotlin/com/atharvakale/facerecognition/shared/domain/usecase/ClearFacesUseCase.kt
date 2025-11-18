package com.atharvakale.facerecognition.shared.domain.usecase

import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository

class ClearFacesUseCase(
    private val repository: FaceRepository
) {
    suspend operator fun invoke() {
        repository.deleteAll()
    }
}

