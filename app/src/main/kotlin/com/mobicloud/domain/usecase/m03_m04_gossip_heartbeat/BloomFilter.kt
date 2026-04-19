package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import java.security.MessageDigest
import java.util.BitSet

class BloomFilter(val bitArraySize: Int = 1024, val numHashFunctions: Int = 3) {

    private val bitArray = BitSet(bitArraySize)

    // F10: instances MessageDigest pré-allouées par seed — évite 300 getInstance() par cycle sur 100 entrées
    private val digests: Array<MessageDigest> = Array(numHashFunctions) {
        MessageDigest.getInstance("SHA-256")
    }

    fun add(element: String) {
        for (i in 0 until numHashFunctions) {
            val index = hashIndex(i, element)
            bitArray.set(index)
        }
    }

    fun mightContain(element: String): Boolean {
        for (i in 0 until numHashFunctions) {
            if (!bitArray.get(hashIndex(i, element))) return false
        }
        return true
    }

    fun toByteArray(): ByteArray {
        // BitSet.toByteArray() omits trailing zero bytes, so we fix the size
        val raw = bitArray.toByteArray()
        val byteCount = (bitArraySize + 7) / 8
        return raw.copyOf(byteCount)
    }

    private fun hashIndex(seed: Int, element: String): Int {
        val input = (seed.toString() + element).toByteArray(Charsets.UTF_8)
        val hash = digests[seed].digest(input)
        val value = hash.sliceArray(0..3).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        return Math.floorMod(value, bitArraySize)
    }

    companion object {
        fun fromByteArray(bytes: ByteArray, size: Int = 1024, k: Int = 3): BloomFilter {
            val filter = BloomFilter(size, k)
            val loaded = BitSet.valueOf(bytes)
            for (i in 0 until loaded.length()) {
                if (loaded.get(i)) filter.bitArray.set(i)
            }
            return filter
        }
    }
}
