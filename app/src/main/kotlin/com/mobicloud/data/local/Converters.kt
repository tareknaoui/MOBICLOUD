package com.mobicloud.data.local

import androidx.room.TypeConverter
import com.mobicloud.domain.models.FragmentLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromFragmentLocationList(value: List<FragmentLocation>?): String {
        if (value == null) return "[]"
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toFragmentLocationList(value: String?): List<FragmentLocation> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
