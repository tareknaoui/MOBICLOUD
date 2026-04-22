package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Story 6.2 — requête de bloc envoyée par un client souhaitant télécharger un fragment.
 *
 * Le seul payload utile est le [blockId] (SHA-256 hex du ciphertext). Aucune information
 * de propriétaire/contenu n'est transmise — Zero-Trust préservé.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BlockRequestMessage(
    @ProtoNumber(1) val blockId: String = ""
)
