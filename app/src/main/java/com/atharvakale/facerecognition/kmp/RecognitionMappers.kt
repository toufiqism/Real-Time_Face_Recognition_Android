package com.atharvakale.facerecognition.kmp

import com.atharvakale.facerecognition.SimilarityClassifier
import com.atharvakale.facerecognition.shared.core.model.FaceEmbedding
import com.atharvakale.facerecognition.shared.core.model.FaceProfile

fun Map<String, SimilarityClassifier.Recognition>.toFaceProfiles(): List<FaceProfile> {
    return entries.mapNotNull { (fallbackName, recognition) ->
        recognition.toFaceProfile(fallbackName)
    }
}

fun List<FaceProfile>.toRecognitionMap(): HashMap<String, SimilarityClassifier.Recognition> {
    val map = HashMap<String, SimilarityClassifier.Recognition>(size)
    for (profile in this) {
        map[profile.displayName] = profile.toRecognition()
    }
    return map
}

fun FaceProfile.toRecognition(): SimilarityClassifier.Recognition {
    val recognition = SimilarityClassifier.Recognition(id, displayName, null)
    recognition.extra = arrayOf(embedding.asFloatArray())
    return recognition
}

private fun SimilarityClassifier.Recognition.toFaceProfile(
    fallbackName: String
): FaceProfile? {
    val embeddingValues = extractEmbeddingValues() ?: return null
    val label = title.takeIf { it.isNotBlank() } ?: fallbackName
    val identifier = id.takeIf { it.isNotBlank() } ?: fallbackName
    return FaceProfile(
        id = identifier,
        displayName = label,
        embedding = FaceEmbedding(embeddingValues),
        notes = distance?.let { "distance=$it" }
    )
}

private fun SimilarityClassifier.Recognition.extractEmbeddingValues(): List<Float>? {
    val payload = extra ?: return null
    return when (payload) {
        is Array<*> -> payload.firstOrNull()?.toFloatList()
        is List<*> -> payload.firstOrNull()?.toFloatList()
        else -> null
    }
}

private fun Any?.toFloatList(): List<Float>? = when (this) {
    is FloatArray -> this.toList()
    is Array<*> -> this.mapNotNull { (it as? Number)?.toFloat() }
    is List<*> -> this.mapNotNull { (it as? Number)?.toFloat() }
    else -> null
}

