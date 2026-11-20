package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import java.security.MessageDigest
import java.util.BitSet
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Filtre de Bloom utilisé comme résumé (digest) du catalogue DHT dans le gossip.
 *
 * [salt] : graine aléatoire tirée à chaque round de gossip. Elle rend les faux positifs
 * indépendants d'un round à l'autre : un identifiant masqué par un faux positif au round r
 * a une probabilité ≈ fp d'être encore masqué au round r+1, donc ≈ fp^r après r rounds
 * (convergence à terme avec probabilité 1). Avec un hachage déterministe (salt = 0),
 * le même faux positif se répète tant que le catalogue ne change pas.
 *
 * salt = 0 conserve exactement l'ancien schéma de hachage (compatibilité avec les
 * versions de l'application antérieures au protocole v1).
 */
class BloomFilter(
    val bitArraySize: Int = 1024,
    val numHashFunctions: Int = 3,
    val salt: Long = 0L
) {

    init {
        require(bitArraySize > 0) { "bitArraySize must be > 0" }
        require(numHashFunctions > 0) { "numHashFunctions must be > 0" }
    }

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
        val input = if (salt == 0L) {
            seed.toString() + element // schéma legacy (v0)
        } else {
            "$salt|$seed|$element"
        }.toByteArray(Charsets.UTF_8)
        val hash = digests[seed].digest(input)
        val value = hash.sliceArray(0..3).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        return Math.floorMod(value, bitArraySize)
    }

    companion object {
        /** Taille minimale : identique à l'ancien filtre fixe, pour les petits catalogues. */
        const val MIN_BITS = 1024

        /** Plafond : 4 Mbit = 512 Ko, sous la limite de 1 Mo par message gossip. */
        const val MAX_BITS = 4_000_000

        const val MAX_HASH_FUNCTIONS = 10

        /** Taux de faux positifs visé par [sized]. */
        const val DEFAULT_TARGET_FP_RATE = 0.01

        /**
         * Crée un filtre dimensionné pour [expectedElements] éléments et un taux de faux
         * positifs [targetFpRate] : m = −n·ln(p)/(ln 2)², k = (m/n)·ln 2.
         *
         * L'ancien filtre fixe (1024 bits, k=3) atteignait ≈ 20 % de faux positifs
         * avec 300 entrées.
         */
        fun sized(
            expectedElements: Int,
            targetFpRate: Double = DEFAULT_TARGET_FP_RATE,
            salt: Long = 0L
        ): BloomFilter {
            require(targetFpRate > 0.0 && targetFpRate < 1.0) { "targetFpRate must be in (0, 1)" }
            if (expectedElements <= 0) return BloomFilter(MIN_BITS, 3, salt)
            val ln2 = ln(2.0)
            val rawBits = ceil(-expectedElements * ln(targetFpRate) / (ln2 * ln2))
            // Arrondi au multiple de 64 supérieur, borné dans [MIN_BITS, MAX_BITS]
            val bits = ((rawBits / 64.0).let { ceil(it) } * 64).toInt()
                .coerceIn(MIN_BITS, MAX_BITS)
            val k = (bits.toDouble() / expectedElements * ln2).roundToInt()
                .coerceIn(1, MAX_HASH_FUNCTIONS)
            return BloomFilter(bits, k, salt)
        }

        fun fromByteArray(bytes: ByteArray, size: Int = 1024, k: Int = 3, salt: Long = 0L): BloomFilter {
            require(size in 1..MAX_BITS) { "bloom size out of range: $size" }
            require(k in 1..MAX_HASH_FUNCTIONS) { "bloom hash count out of range: $k" }
            val filter = BloomFilter(size, k, salt)
            val loaded = BitSet.valueOf(bytes)
            for (i in 0 until minOf(loaded.length(), size)) {
                if (loaded.get(i)) filter.bitArray.set(i)
            }
            return filter
        }
    }
}
