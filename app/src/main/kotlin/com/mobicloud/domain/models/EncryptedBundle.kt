package com.mobicloud.domain.models

data class EncryptedBundle(
    val encryptedFragments: List<EncryptedFragment>,
    val wrappedFileMasterKey: WrappedFileMasterKey,
    /** Ordre chiffrement / encodage, voir [CipherScheme]. */
    val cipherScheme: Int = CipherScheme.ENCODE_THEN_ENCRYPT,
    /**
     * Taille du fichier en clair. En chiffrer-puis-encoder, `originalFileSize` des fragments
     * est la taille du chiffré (trim RS) ; null = ancien format, taille portée par les fragments.
     */
    val plaintextSize: Long? = null
)
