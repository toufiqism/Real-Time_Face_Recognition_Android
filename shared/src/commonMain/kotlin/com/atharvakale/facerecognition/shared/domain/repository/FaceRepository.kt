package com.atharvakale.facerecognition.shared.domain.repository

import com.atharvakale.facerecognition.shared.core.model.FaceProfile

interface FaceRepository {
    suspend fun getAll(): List<FaceProfile>
    suspend fun upsert(profile: FaceProfile)
    suspend fun upsertAll(profiles: List<FaceProfile>)
    suspend fun delete(ids: Set<String>)
    suspend fun deleteAll()
}

