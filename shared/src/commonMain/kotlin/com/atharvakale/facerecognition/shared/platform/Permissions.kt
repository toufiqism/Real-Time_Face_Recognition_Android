package com.atharvakale.facerecognition.shared.platform

enum class AppPermission {
    CAMERA
}

enum class PermissionStatus {
    UNKNOWN,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED
}

data class PermissionState(
    val permission: AppPermission = AppPermission.CAMERA,
    val status: PermissionStatus = PermissionStatus.UNKNOWN,
    val shouldShowRationale: Boolean = false
)

interface PermissionsManager {
    suspend fun getStatus(permission: AppPermission): PermissionStatus
    suspend fun request(permission: AppPermission): PermissionStatus
    suspend fun shouldShowRationale(permission: AppPermission): Boolean
}

