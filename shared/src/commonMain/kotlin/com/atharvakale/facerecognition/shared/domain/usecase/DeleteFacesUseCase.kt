package com.atharvakale.facerecognition.shared.domain.usecase

import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository

class DeleteFacesUseCase(
    private val repository: FaceRepository
) {
    suspend operator fun invoke(ids: Set<String>) {
        if (ids.isEmpty()) return
        repository.delete(ids)
    }
}

