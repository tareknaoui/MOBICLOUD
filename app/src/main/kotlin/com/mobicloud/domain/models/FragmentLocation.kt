package com.mobicloud.domain.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Représente un emplacement d'un fragment d'un fichier dans le réseau DHT.
 * "Zero-Knowledge" : Aucune information permettant de déduire le contenu du fragment.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FragmentLocation(
    @ProtoNumber(1) val fragmentIndex: Int,
    @ProtoNumber(2) val fragmentHash: String,
    @ProtoNumber(3) val nodeIds: List<String>
)
