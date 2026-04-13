package com.mobicloud.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * TypeConverters Room pour les types primitifs complexes stock\u00e9s en colonnes SQL.
 *
 * NOTE (BH-02) : Ce fichier ne contient plus de converter pour [FragmentLocation].
 * La s\u00e9rialisation des listes de nœuds (nodeIds) est g\u00e9r\u00e9e explicitement
 * dans [CatalogRepositoryImpl] pour garder la logique de mapping dans la couche Repository,
 * conform\u00e9ment aux principes Clean Architecture.
 *
 * Ce converter est r\u00e9serv\u00e9 pour les futurs types primitifs complexes (ex: List<String> g\u00e9n\u00e9rique).
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return "[]"
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
