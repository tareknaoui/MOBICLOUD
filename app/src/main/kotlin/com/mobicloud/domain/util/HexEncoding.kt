package com.mobicloud.domain.util

// Encodage déterministe `ByteArray` -> hex lower-case pour payloads signés en clair.
// `it.toInt() and 0xff` évite le sign-extend de Byte -> Int qui ferait `%02x` = "ffffffff"
// au lieu de "ff" sur les octets >= 0x80. Les deux côtés d'une signature DOIVENT utiliser
// ce helper pour garantir la comparaison byte-à-byte.
internal fun ByteArray.toSigHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
