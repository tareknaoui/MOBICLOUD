package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.EncryptionKeyBinding
import com.mobicloud.domain.models.encryptionKeyBindingSignedBytes
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signe la clé publique de chiffrement locale avec la clé d'identité Android Keystore
 * (voir [EncryptionKeyBinding]). Le lien est mis en cache : il ne change que si la clé de
 * chiffrement change (import d'un code de récupération) ou pour un autre nodeId.
 */
@Singleton
class IssueEncryptionKeyBindingUseCase @Inject constructor(
    private val securityRepository: SecurityRepository
) {
    private val mutex = Mutex()
    private var cached: EncryptionKeyBinding? = null

    /** Horloge injectable pour les tests. */
    internal var clockMs: () -> Long = System::currentTimeMillis

    suspend operator fun invoke(nodeId: ByteArray): Result<EncryptionKeyBinding> = mutex.withLock {
        runCatching {
            val encryptionPublicKey = securityRepository.getEncryptionIdentity().getOrThrow().publicKeyBytes
            cached?.takeIf {
                it.nodeId.contentEquals(nodeId) && it.encryptionPublicKey.contentEquals(encryptionPublicKey)
            }?.let { return@runCatching it }

            val issuedAtMs = clockMs()
            val signature = securityRepository
                .signData(encryptionKeyBindingSignedBytes(nodeId, encryptionPublicKey, issuedAtMs))
                .getOrThrow()
            EncryptionKeyBinding(nodeId, encryptionPublicKey, issuedAtMs, signature).also { cached = it }
        }
    }
}
