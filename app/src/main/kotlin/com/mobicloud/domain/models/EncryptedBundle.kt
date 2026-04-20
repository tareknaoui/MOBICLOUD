package com.mobicloud.domain.models

data class EncryptedBundle(
    val encryptedFragments: List<EncryptedFragment>,
    val wrappedFileMasterKey: WrappedFileMasterKey
)
