package com.atharvakale.facerecognition.shared.platform

import android.content.Context

actual class PlatformContext(val context: Context)

actual fun provideFaceAnalyzer(context: PlatformContext): FaceAnalyzer =
    NoOpFaceAnalyzer

private object NoOpFaceAnalyzer : FaceAnalyzer {
    override suspend fun analyze(frame: CameraFrame): FaceAnalysisResult {
        return FaceAnalysisResult(emptyList(), emptyList())
    }
}

