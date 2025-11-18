package com.atharvakale.facerecognition.shared.core.state

data class RecognitionSettings(
    val distanceThreshold: Float = 1.0f,
    val developerMode: Boolean = false,
    val flipCameraHorizontally: Boolean = false
) {
    init {
        require(distanceThreshold in 0.1f..2.0f) { "distanceThreshold out of safe bounds" }
    }
}

