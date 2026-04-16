package com.mobicloud.data.repository_impl

import com.mobicloud.core.security.KeystoreManager
import com.mobicloud.data.local.dao.IdentityDao
import com.mobicloud.data.local.entity.NodeIdentityEntity
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.IdentityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepositoryImpl @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val identityDao: IdentityDao
) : IdentityRepository {

    override suspend fun getIdentity(): Result<NodeIdentity> = withContext(Dispatchers.IO) {
        runCatching {
            // Étape 1 : Vérifier la BDD locale
            val savedEntity = identityDao.getIdentity()
            if (savedEntity != null) {
                return@runCatching NodeIdentity(
                    nodeId = savedEntity.nodeId,
                    publicKeyBytes = savedEntity.publicKeyBytes,
                    reliabilityScore = savedEntity.reliabilityScore
                )
            }

            // Étape 2 : Si non en BDD, vérifier le Keystore existant (cas rare de purge DB)
            val existingIdentity = keystoreManager.getExistingIdentity()
            if (existingIdentity != null) {
                val entity = NodeIdentityEntity(
                    nodeId = existingIdentity.nodeId,
                    publicKeyBytes = existingIdentity.publicKeyBytes,
                    reliabilityScore = existingIdentity.reliabilityScore
                )
                identityDao.insertIdentity(entity)
                return@runCatching existingIdentity
            }

            // Étape 3 : Générer une nouvelle identité
            val newIdentity = keystoreManager.generateIdentity()
            val newEntity = NodeIdentityEntity(
                nodeId = newIdentity.nodeId,
                publicKeyBytes = newIdentity.publicKeyBytes,
                reliabilityScore = newIdentity.reliabilityScore
            )
            identityDao.insertIdentity(newEntity)

            newIdentity
        }
    }

    override suspend fun updateReliabilityScore(nodeId: String, score: Float): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            identityDao.updateReliabilityScore(nodeId, score)
        }
    }
}
