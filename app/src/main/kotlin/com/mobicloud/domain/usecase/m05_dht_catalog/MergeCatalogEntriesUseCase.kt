package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.FragmentLocation

/**
 * Use Case responsable de la fusion des entrées de catalogue.
 * Utilise un algorithme CRDT LWW (Last-Writer-Wins) basé sur l'horloge logique `versionClock`.
 * Propriété garantie: A ∪ B == B ∪ A (Commutativité).
 */
class MergeCatalogEntriesUseCase {
    operator fun invoke(local: CatalogEntry, remote: CatalogEntry): CatalogEntry {
        // Validation: On ne fusionne que des fiches du même fichier
        require(local.fileHash == remote.fileHash) {
            "Cannot merge entries for different files: ${local.fileHash} vs ${remote.fileHash}"
        }

        // LWW (Last-Writer-Wins): On préfère toujours la version la plus récente.
        return when {
            remote.versionClock > local.versionClock -> remote
            local.versionClock > remote.versionClock -> local
            else -> {
                // versionClock identiques: on privilégie l'union des fragments de manière déterministe
                val mergedFragments = (local.fragmentLocations + remote.fragmentLocations)
                    .distinctBy { it.fragmentIndex to it.fragmentHash }
                    .sortedBy { it.fragmentIndex }

                // Pour briser l'égalité de manière commutative, on prend le ownerPubKeyHash le plus grand lexicographiquement (si différent)
                val chosenOwner = if (remote.ownerPubKeyHash > local.ownerPubKeyHash) remote.ownerPubKeyHash else local.ownerPubKeyHash

                CatalogEntry(
                    fileHash = local.fileHash,
                    ownerPubKeyHash = chosenOwner,
                    versionClock = local.versionClock,
                    fragmentLocations = mergedFragments
                )
            }
        }
    }
}
