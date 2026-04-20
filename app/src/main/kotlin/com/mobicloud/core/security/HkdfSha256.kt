package com.mobicloud.core.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal fun hkdfSha256(
    ikm: ByteArray,
    salt: ByteArray? = null,
    info: ByteArray,
    outputLen: Int = 32
): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    // Extract
    mac.init(SecretKeySpec(salt ?: ByteArray(32), "HmacSHA256"))
    val prk = mac.doFinal(ikm)
    // Expand
    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    val result = ByteArray(outputLen)
    var prev = ByteArray(0)
    var pos = 0
    var ctr = 1
    while (pos < outputLen) {
        mac.update(prev)
        mac.update(info)
        mac.update(ctr.toByte())
        prev = mac.doFinal()
        val len = minOf(prev.size, outputLen - pos)
        prev.copyInto(result, pos, 0, len)
        pos += len
        ctr++
    }
    return result
}
