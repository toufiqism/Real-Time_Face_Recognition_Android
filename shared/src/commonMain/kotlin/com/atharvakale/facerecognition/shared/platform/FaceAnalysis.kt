package com.atharvakale.facerecognition.shared.platform

import com.atharvakale.facerecognition.shared.core.model.FaceEmbedding

enum class FrameFormat { YUV_420_888, RGB }

data class CameraFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val format: FrameFormat
)

data class DetectedFace(
    val boundingBox: BoundingBox,
    val landmarks: List<Point> = emptyList()
)

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class Point(val x: Float, val y: Float)

data class FaceAnalysisResult(
    val faces: List<DetectedFace>,
    val embeddings: List<FaceEmbedding>
)

interface FaceAnalyzer {
    suspend fun analyze(frame: CameraFrame): FaceAnalysisResult
}

expect class PlatformContext

expect fun provideFaceAnalyzer(context: PlatformContext): FaceAnalyzer

