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
 *
 * Story 6.3 — ajout de [originalFileSize] (octets, requis par le décodeur Erasure pour
 * trimer le padding au moment du réassemblage). Valeur par défaut [0L] pour compat
 * Protobuf et CRDT (entrées pré-6.3 → le pipeline lèvera MasterKeyTransportGap).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CatalogEntry(
    @ProtoNumber(1) val fileHash: String,
    @ProtoNumber(2) val ownerPubKeyHash: String,
    @ProtoNumber(3) val versionClock: Long,
    @ProtoNumber(4) val fragmentLocations: List<FragmentLocation>,
    @ProtoNumber(5) val wrappedMasterKey: WrappedFileMasterKey? = null,
    @ProtoNumber(6) val originalFileSize: Long = 0L,
    @ProtoNumber(7) val originalFileName: String = ""
)
