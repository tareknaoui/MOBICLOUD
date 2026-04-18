package com.mobicloud.domain.usecase.m05_dht_catalog

import java.security.MessageDigest

class ConsistentHashRing(private val qualifiedNodes: List<String>) {

    init {
        require(qualifiedNodes.isNotEmpty()) { "Qualified nodes must not be empty" }
    }

    fun getPartition(blockId: String): String {
        val hash = hashValue(blockId)
        val index = Math.floorMod(hash.toLong(), qualifiedNodes.size.toLong()).toInt()
        return qualifiedNodes[index]
    }

    private fun hashValue(input: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.sliceArray(0..3).fold(0) { acc, byte ->
            (acc shl 8) or (byte.toInt() and 0xFF)
        }
    }
}
