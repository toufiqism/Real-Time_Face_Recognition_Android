package com.atharvakale.facerecognition.shared.di

import android.app.Activity
import com.atharvakale.facerecognition.shared.FaceRecognitionSDK
import com.atharvakale.facerecognition.shared.controller.FaceRecognitionController
import com.atharvakale.facerecognition.shared.domain.repository.SharedPreferencesFaceRepository
import com.atharvakale.facerecognition.shared.domain.repository.SharedPreferencesRecognitionSettingsRepository
import com.atharvakale.facerecognition.shared.platform.AndroidPermissionsManager
import com.atharvakale.facerecognition.shared.platform.AppPermission
import com.atharvakale.facerecognition.shared.platform.PermissionStatus

object AndroidSharedModule {
    fun controller(
        activity: Activity,
        permissionRequestDelegate: (suspend (AppPermission) -> PermissionStatus)? = null
    ): FaceRecognitionController {
        val faceRepository = SharedPreferencesFaceRepository(activity)
        val settingsRepository = SharedPreferencesRecognitionSettingsRepository(activity)
        val permissionsManager = AndroidPermissionsManager(activity).apply {
            val fallbackDelegate: suspend (AppPermission) -> PermissionStatus = { permission ->
                getStatus(permission)
            }
            val delegate = permissionRequestDelegate ?: fallbackDelegate
            updateRequestDelegate(delegate)
        }
        return FaceRecognitionSDK.createController(
            faceRepository = faceRepository,
            settingsRepository = settingsRepository,
            permissionsManager = permissionsManager
        )
    }
}

