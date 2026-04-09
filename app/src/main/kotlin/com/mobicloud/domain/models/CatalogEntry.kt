package com.mobicloud.domain.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Représente une fiche de catalogue distribué.
 * Conception strictement "Zero-Knowledge" :
 * - Aucun nom de fichier
 * - Aucune taille en clair
 * - Aucune extension textuelle
 * Uniquement des données cryptographiques et de routage (CRDT LWW basé sur versionClock).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CatalogEntry(
    @ProtoNumber(1) val fileHash: String,
    @ProtoNumber(2) val ownerPubKeyHash: String,
    @ProtoNumber(3) val versionClock: Long,
    @ProtoNumber(4) val fragmentLocations: List<FragmentLocation>
)
