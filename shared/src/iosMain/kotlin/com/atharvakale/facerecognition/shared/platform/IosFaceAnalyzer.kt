package com.atharvakale.facerecognition.shared.platform

actual class PlatformContext(val reference: Any?)

actual fun provideFaceAnalyzer(context: PlatformContext): FaceAnalyzer =
    NoOpFaceAnalyzer

private object NoOpFaceAnalyzer : FaceAnalyzer {
    override suspend fun analyze(frame: CameraFrame): FaceAnalysisResult {
        return FaceAnalysisResult(emptyList(), emptyList())
    }
}

