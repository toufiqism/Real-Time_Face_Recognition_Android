package com.atharvakale.facerecognition.shared.core.model

import kotlin.random.Random

data class FaceProfile(
    val id: String = Random.nextLong().toString(),
    val displayName: String,
    val embedding: FaceEmbedding,
    val notes: String? = null
) {
    init {
        require(displayName.isNotBlank()) { "displayName is required" }
    }
}

