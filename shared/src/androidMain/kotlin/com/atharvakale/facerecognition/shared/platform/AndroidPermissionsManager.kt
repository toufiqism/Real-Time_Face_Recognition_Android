package com.atharvakale.facerecognition.shared.platform

import android.Manifest
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class AndroidPermissionsManager(
    private val activity: Activity,
    private var requestDelegate: suspend (AppPermission) -> PermissionStatus = { PermissionStatus.UNKNOWN }
) : PermissionsManager {

    override suspend fun getStatus(permission: AppPermission): PermissionStatus {
        val manifestPermission = permission.toManifestPermission()
        val granted = ContextCompat.checkSelfPermission(activity, manifestPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    override suspend fun request(permission: AppPermission): PermissionStatus {
        return requestDelegate(permission)
    }

    override suspend fun shouldShowRationale(permission: AppPermission): Boolean {
        val manifestPermission = permission.toManifestPermission()
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, manifestPermission)
    }

    fun updateRequestDelegate(delegate: suspend (AppPermission) -> PermissionStatus) {
        requestDelegate = delegate
    }

    private fun AppPermission.toManifestPermission(): String = when (this) {
        AppPermission.CAMERA -> Manifest.permission.CAMERA
    }
}

