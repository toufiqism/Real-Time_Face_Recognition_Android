package com.atharvakale.facerecognition.shared.core.model

/**
 * Wrapper around an immutable embedding array produced by the ML model.
 */
data class FaceEmbedding(
    val values: List<Float>,
    val confidence: Float = 0f
) {
    init {
        require(values.isNotEmpty()) { "Embedding cannot be empty" }
    }

    fun asFloatArray(): FloatArray = values.toFloatArray()
}

