package com.atharvakale.facerecognition.shared.core.model

enum class RecognitionStatus {
    MATCHED,
    UNKNOWN,
    MULTIPLE,
    ERROR
}

data class RecognitionResult(
    val status: RecognitionStatus,
    val bestMatch: FaceProfile? = null,
    val distance: Float? = null,
    val errorMessage: String? = null
) {
    val isSuccessful: Boolean get() = status == RecognitionStatus.MATCHED
}

