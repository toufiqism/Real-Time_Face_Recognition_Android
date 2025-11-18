package com.atharvakale.facerecognition.shared.domain.usecase

import com.atharvakale.facerecognition.shared.core.model.FaceProfile
import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository

class LoadFacesUseCase(
    private val repository: FaceRepository
) {
    suspend operator fun invoke(): List<FaceProfile> = repository.getAll()
}

