package com.atharvakale.facerecognition.shared.domain.repository

import android.content.Context
import com.atharvakale.facerecognition.shared.core.model.FaceEmbedding
import com.atharvakale.facerecognition.shared.core.model.FaceProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharedPreferencesFaceRepository(
    context: Context,
    private val gson: Gson = Gson()
) : FaceRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun getAll(): List<FaceProfile> = mutex.withLock {
        withContext(Dispatchers.IO) { readProfiles() }
    }

    override suspend fun upsert(profile: FaceProfile) {
        mutex.withLock {
            val current = readProfiles().associateBy { it.id }.toMutableMap()
            current[profile.id] = profile
            persistProfiles(current.values.toList())
        }
    }

    override suspend fun upsertAll(profiles: List<FaceProfile>) {
        if (profiles.isEmpty()) return
        mutex.withLock {
            val current = readProfiles().associateBy { it.id }.toMutableMap()
            profiles.forEach { current[it.id] = it }
            persistProfiles(current.values.toList())
        }
    }

    override suspend fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            val filtered = readProfiles().filterNot { ids.contains(it.id) }
            persistProfiles(filtered)
        }
    }

    override suspend fun deleteAll() {
        mutex.withLock {
            prefs.edit()
                .remove(KEY_V2)
                .remove(KEY_LEGACY)
                .apply()
        }
    }

    private fun readProfiles(): List<FaceProfile> {
        val v2Json = prefs.getString(KEY_V2, null)
        if (!v2Json.isNullOrBlank()) {
            runCatching {
                val dto = gson.fromJson(v2Json, FaceRegistryDto::class.java)
                if (dto.faces.isNotEmpty()) {
                    return dto.faces.mapNotNull { it.toDomain() }
                }
            }.onFailure {
                // Fall back to legacy parsing below
            }
        }
        val legacyJson = prefs.getString(KEY_LEGACY, null) ?: return emptyList()
        val type = object : TypeToken<Map<String, LegacyRecognitionDto>>() {}.type
        return runCatching {
            val legacyMap: Map<String, LegacyRecognitionDto> = gson.fromJson(legacyJson, type)
            legacyMap.mapNotNull { (key, value) -> value.toDomain(key) }
        }.getOrDefault(emptyList())
    }

    private fun persistProfiles(profiles: List<FaceProfile>) {
        val dto = FaceRegistryDto(
            schemaVersion = 1,
            faces = profiles.map { it.toDto() }
        )
        prefs.edit().putString(KEY_V2, gson.toJson(dto)).apply()
    }

    private data class FaceRegistryDto(
        val schemaVersion: Int = 1,
        val faces: List<FaceProfileDto>
    )

    private data class FaceProfileDto(
        val id: String,
        val displayName: String,
        val notes: String?,
        val embedding: List<Float>
    ) {
        fun toDomain(): FaceProfile? {
            if (displayName.isBlank() || embedding.isEmpty()) return null
            return FaceProfile(
                id = id,
                displayName = displayName,
                notes = notes,
                embedding = FaceEmbedding(embedding)
            )
        }
    }

    private data class LegacyRecognitionDto(
        val id: String?,
        val title: String?,
        val distance: Float?,
        val extra: Any?
    ) {
        fun toDomain(fallbackName: String): FaceProfile? {
            val values = (extra as? List<*>)?.firstOrNull() as? List<*>
            val floats = values?.mapNotNull { (it as? Number)?.toFloat() } ?: return null
            if (floats.isEmpty()) return null
            val name = title ?: id ?: fallbackName
            if (name.isBlank()) return null
            return FaceProfile(
                id = id ?: fallbackName,
                displayName = name,
                embedding = FaceEmbedding(floats),
                notes = distance?.let { "distance=$it" }
            )
        }
    }

    private fun FaceProfile.toDto(): FaceProfileDto = FaceProfileDto(
        id = id,
        displayName = displayName,
        notes = notes,
        embedding = embedding.values
    )

    companion object {
        private const val PREF_NAME = "HashMap"
        private const val KEY_V2 = "faces_v2"
        private const val KEY_LEGACY = "map"
    }
}

