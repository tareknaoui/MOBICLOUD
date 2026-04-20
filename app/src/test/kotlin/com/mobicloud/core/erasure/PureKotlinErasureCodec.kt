package com.mobicloud.core.erasure

/**
 * Pure-Kotlin Reed-Solomon Erasure Coding over GF(256).
 *
 * Test-only implementation that mirrors the algorithm of the native C++ kernel
 * (`erasure_jni.cpp`) so that the UseCases can be exercised on the host JVM without loading the
 * `.so` library. Uses the same primitive polynomial (0x11D) and the same systematic Vandermonde
 * construction, so the parity bytes it produces are bit-exact with the native implementation.
 */
class PureKotlinErasureCodec : ErasureCodec {

    override fun encode(data: List<ByteArray>, k: Int, n: Int): List<ByteArray> {
        require(data.size == k)
        val blockSize = data.first().size
        require(data.all { it.size == blockSize })

        val p = buildParityMatrix(k, n)
        return List(n) { i ->
            val row = ByteArray(blockSize)
            for (j in 0 until k) {
                gfXorScaled(row, data[j], blockSize, p[i * k + j])
            }
            row
        }
    }

    override fun decode(
        survivors: List<ByteArray>,
        survivorIndices: IntArray,
        k: Int,
        n: Int,
    ): List<ByteArray> {
        require(survivors.size == k)
        require(survivorIndices.size == k)
        val blockSize = survivors.first().size
        require(survivors.all { it.size == blockSize })

        val p = buildParityMatrix(k, n)
        val m = IntArray(k * k)
        for (r in 0 until k) {
            val idx = survivorIndices[r]
            require(idx in 0 until (k + n)) { "Invalid survivor index: $idx" }
            if (idx < k) {
                m[r * k + idx] = 1
            } else {
                for (j in 0 until k) {
                    m[r * k + j] = p[(idx - k) * k + j].toInt() and 0xff
                }
            }
        }
        check(invertMatrix(m, k)) { "Cannot decode: survivor matrix is singular" }

        return List(k) { i ->
            val row = ByteArray(blockSize)
            for (j in 0 until k) {
                gfXorScaled(row, survivors[j], blockSize, m[i * k + j].toByte())
            }
            row
        }
    }

    // ------------------------------------------------------------------------------------------
    // GF(256) tables — initialized once per instance.
    // ------------------------------------------------------------------------------------------

    private val gfLog = IntArray(256)
    private val gfExp = IntArray(512)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11d
        }
        for (i in 255 until 512) gfExp[i] = gfExp[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        val ua = a and 0xff
        val ub = b and 0xff
        if (ua == 0 || ub == 0) return 0
        return gfExp[gfLog[ua] + gfLog[ub]]
    }

    private fun gfInv(a: Int): Int {
        val ua = a and 0xff
        return gfExp[255 - gfLog[ua]]
    }

    private fun gfPow(base: Int, exponent: Int): Int {
        val ub = base and 0xff
        if (ub == 0) return 0
        var product = (gfLog[ub] * exponent) % 255
        if (product < 0) product += 255
        return gfExp[product]
    }

    private fun gfXorScaled(dst: ByteArray, src: ByteArray, blockSize: Int, coeff: Byte) {
        val uc = coeff.toInt() and 0xff
        if (uc == 0) return
        if (uc == 1) {
            for (c in 0 until blockSize) dst[c] = (dst[c].toInt() xor src[c].toInt()).toByte()
            return
        }
        val logCoeff = gfLog[uc]
        for (c in 0 until blockSize) {
            val s = src[c].toInt() and 0xff
            if (s != 0) {
                val prod = gfExp[gfLog[s] + logCoeff]
                dst[c] = (dst[c].toInt() xor prod).toByte()
            }
        }
    }

    private fun buildParityMatrix(k: Int, n: Int): ByteArray {
        val rows = k + n
        val v = IntArray(rows * k)
        for (i in 0 until rows) {
            for (j in 0 until k) {
                v[i * k + j] = gfPow(i + 1, j)
            }
        }
        val vtopInv = IntArray(k * k)
        for (i in 0 until k * k) vtopInv[i] = v[i]
        check(invertMatrix(vtopInv, k))

        val p = ByteArray(n * k)
        for (i in 0 until n) {
            for (j in 0 until k) {
                var sum = 0
                for (l in 0 until k) {
                    sum = sum xor gfMul(v[(k + i) * k + l], vtopInv[l * k + j])
                }
                p[i * k + j] = sum.toByte()
            }
        }
        return p
    }

    private fun invertMatrix(mat: IntArray, k: Int): Boolean {
        val inv = IntArray(k * k)
        for (i in 0 until k) inv[i * k + i] = 1

        for (col in 0 until k) {
            var pivot = -1
            for (row in col until k) {
                if (mat[row * k + col] != 0) { pivot = row; break }
            }
            if (pivot < 0) return false
            if (pivot != col) {
                for (j in 0 until k) {
                    val t1 = mat[col * k + j]; mat[col * k + j] = mat[pivot * k + j]; mat[pivot * k + j] = t1
                    val t2 = inv[col * k + j]; inv[col * k + j] = inv[pivot * k + j]; inv[pivot * k + j] = t2
                }
            }
            val pvInv = gfInv(mat[col * k + col])
            for (j in 0 until k) {
                mat[col * k + j] = gfMul(mat[col * k + j], pvInv)
                inv[col * k + j] = gfMul(inv[col * k + j], pvInv)
            }
            for (row in 0 until k) {
                if (row == col) continue
                val factor = mat[row * k + col]
                if (factor == 0) continue
                for (j in 0 until k) {
                    mat[row * k + j] = mat[row * k + j] xor gfMul(factor, mat[col * k + j])
                    inv[row * k + j] = inv[row * k + j] xor gfMul(factor, inv[col * k + j])
                }
            }
        }
        for (i in 0 until k * k) mat[i] = inv[i]
        return true
    }
}
