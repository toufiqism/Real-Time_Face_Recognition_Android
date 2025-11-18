package com.atharvakale.facerecognition.shared.controller

import com.atharvakale.facerecognition.shared.core.model.FaceProfile
import com.atharvakale.facerecognition.shared.core.state.FaceRegistryState
import com.atharvakale.facerecognition.shared.core.state.RegistryOperation
import com.atharvakale.facerecognition.shared.core.state.RecognitionSettings
import com.atharvakale.facerecognition.shared.domain.repository.FaceRepository
import com.atharvakale.facerecognition.shared.domain.repository.RecognitionSettingsRepository
import com.atharvakale.facerecognition.shared.domain.usecase.ClearFacesUseCase
import com.atharvakale.facerecognition.shared.domain.usecase.DeleteFacesUseCase
import com.atharvakale.facerecognition.shared.domain.usecase.LoadFacesUseCase
import com.atharvakale.facerecognition.shared.domain.usecase.RegisterFaceUseCase
import com.atharvakale.facerecognition.shared.domain.usecase.ReplaceFacesUseCase
import com.atharvakale.facerecognition.shared.domain.usecase.UpdateSettingsUseCase
import com.atharvakale.facerecognition.shared.platform.AppPermission
import com.atharvakale.facerecognition.shared.platform.PermissionState
import com.atharvakale.facerecognition.shared.platform.PermissionStatus
import com.atharvakale.facerecognition.shared.platform.PermissionsManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FaceRecognitionController(
    private val faceRepository: FaceRepository,
    settingsRepository: RecognitionSettingsRepository,
    private val permissionsManager: PermissionsManager,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val loadFacesUseCase = LoadFacesUseCase(faceRepository)
    private val registerFaceUseCase = RegisterFaceUseCase(faceRepository)
    private val deleteFacesUseCase = DeleteFacesUseCase(faceRepository)
    private val clearFacesUseCase = ClearFacesUseCase(faceRepository)
    private val replaceFacesUseCase = ReplaceFacesUseCase(faceRepository)
    private val updateSettingsUseCase = UpdateSettingsUseCase(settingsRepository)

    private val _state = MutableStateFlow(FaceRegistryState())
    val state: StateFlow<FaceRegistryState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FaceRegistryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FaceRegistryEvent> = _events.asSharedFlow()

    init {
        refreshFaces()
        refreshSettings()
    }

    fun refreshFaces() {
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { loadFacesUseCase() }
                .onSuccess { faces ->
                    _state.update { it.copy(faces = faces, isLoading = false) }
                }
                .onFailure { throwable -> handleError(throwable) }
        }
    }

    fun registerFace(profile: FaceProfile) {
        scope.launch {
            runCatching { registerFaceUseCase(profile) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            faces = it.faces.filterNot { f -> f.id == profile.id } + profile,
                            lastOperation = RegistryOperation.Saved(profile),
                            errorMessage = null
                        )
                    }
                    _events.emit(FaceRegistryEvent.FaceSaved(profile))
                }
                .onFailure { throwable -> handleError(throwable) }
        }
    }

    fun deleteFaces(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            runCatching { deleteFacesUseCase(ids) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            faces = it.faces.filterNot { profile -> ids.contains(profile.id) },
                            lastOperation = RegistryOperation.Deleted(ids),
                            errorMessage = null
                        )
                    }
                    _events.emit(FaceRegistryEvent.FacesDeleted(ids))
                }
                .onFailure { throwable -> handleError(throwable) }
        }
    }

    fun clearFaces() {
        scope.launch {
            runCatching { clearFacesUseCase() }
                .onSuccess {
                    _state.update {
                        it.copy(
                            faces = emptyList(),
                            lastOperation = RegistryOperation.Cleared,
                            errorMessage = null
                        )
                    }
                    _events.emit(FaceRegistryEvent.FacesCleared)
                }
                .onFailure { throwable -> handleError(throwable) }
        }
    }

    fun replaceFaces(faces: List<FaceProfile>) {
        scope.launch {
            runCatching { replaceFacesUseCase(faces) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            faces = faces,
                            lastOperation = RegistryOperation.SavedBulk(faces.map(FaceProfile::id).toSet()),
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable -> handleError(throwable) }
        }
    }

    fun updateSettings(transform: (RecognitionSettings) -> RecognitionSettings) {
        scope.launch {
            runCatching { updateSettingsUseCase(transform) }
                .onSuccess { updated ->
                    _state.update { it.copy(settings = updated, errorMessage = null) }
                    _events.emit(FaceRegistryEvent.SettingsChanged(updated))
                }
                .onFailure { throwable -> handleError(throwable) }
        }
    }

    fun refreshSettings() {
        scope.launch {
            runCatching {
                updateSettingsUseCase { it } // no-op to fetch latest persisted settings
            }.onSuccess { settings ->
                _state.update { it.copy(settings = settings, errorMessage = null) }
            }.onFailure { throwable -> handleError(throwable) }
        }
    }

    fun syncCameraPermission() {
        scope.launch {
            val permission = AppPermission.CAMERA
            val status = permissionsManager.getStatus(permission)
            val rationale = permissionsManager.shouldShowRationale(permission)
            _state.update {
                it.copy(permissionState = PermissionState(permission, status, rationale))
            }
        }
    }

    fun requestCameraPermission() {
        scope.launch {
            val permission = AppPermission.CAMERA
            val status = permissionsManager.request(permission)
            val rationale = permissionsManager.shouldShowRationale(permission)
            _state.update {
                it.copy(permissionState = PermissionState(permission, status, rationale))
            }
            when (status) {
                PermissionStatus.GRANTED -> _events.emit(FaceRegistryEvent.PermissionGranted)
                PermissionStatus.DENIED -> _events.emit(FaceRegistryEvent.PermissionDenied(false))
                PermissionStatus.PERMANENTLY_DENIED -> _events.emit(FaceRegistryEvent.PermissionDenied(true))
                PermissionStatus.UNKNOWN -> Unit
            }
        }
    }

    private suspend fun handleError(throwable: Throwable) {
        _state.update { it.copy(errorMessage = throwable.message, isLoading = false) }
        _events.emit(FaceRegistryEvent.Error(throwable))
    }
}

sealed interface FaceRegistryEvent {
    data class FaceSaved(val profile: FaceProfile) : FaceRegistryEvent
    data class FacesDeleted(val ids: Set<String>) : FaceRegistryEvent
    data object FacesCleared : FaceRegistryEvent
    data class SettingsChanged(val settings: RecognitionSettings) : FaceRegistryEvent
    data object PermissionGranted : FaceRegistryEvent
    data class PermissionDenied(val permanently: Boolean) : FaceRegistryEvent
    data class Error(val throwable: Throwable) : FaceRegistryEvent
}

