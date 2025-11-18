package com.atharvakale.facerecognition.shared.core.state

import com.atharvakale.facerecognition.shared.core.model.FaceProfile
import com.atharvakale.facerecognition.shared.platform.PermissionState

data class FaceRegistryState(
    val faces: List<FaceProfile> = emptyList(),
    val isLoading: Boolean = false,
    val lastOperation: RegistryOperation? = null,
    val settings: RecognitionSettings = RecognitionSettings(),
    val permissionState: PermissionState = PermissionState(),
    val errorMessage: String? = null
)

sealed interface RegistryOperation {
    data class Saved(val profile: FaceProfile) : RegistryOperation
    data class Deleted(val ids: Set<String>) : RegistryOperation
    data class SavedBulk(val ids: Set<String>) : RegistryOperation
    data object Cleared : RegistryOperation
}

