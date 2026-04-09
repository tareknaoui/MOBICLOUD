package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Représente la preuve de travail Hashcash (FR-09.1b).
 *
 * @property resource L'identifiant public du nœud pour lequel la preuve est générée.
 * @property timestamp Le marqueur de temps de la génération en millisecondes.
 * @property nonce Le nonce ayant permis de résoudre le puzzle.
 * @property hash Le résultat du puzzle sous forme hexadécimale, validant la difficulté.
 * @property signature La signature des données [resource:timestamp:nonce] via la clé privée du nœud.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HashcashToken(
    @ProtoNumber(1) val resource: String,
    @ProtoNumber(2) val timestamp: Long,
    @ProtoNumber(3) val nonce: Long,
    @ProtoNumber(4) val hash: String,
    @ProtoNumber(5) val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HashcashToken

        if (resource != other.resource) return false
        if (timestamp != other.timestamp) return false
        if (nonce != other.nonce) return false
        if (hash != other.hash) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resource.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + hash.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
