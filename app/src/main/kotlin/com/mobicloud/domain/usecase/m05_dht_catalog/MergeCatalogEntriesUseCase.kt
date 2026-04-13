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
        // Guard Zero-Knowledge : les hashes ne peuvent pas être vides (ECH-01)
        require(local.fileHash.isNotBlank()) { "local.fileHash cannot be blank" }
        require(remote.fileHash.isNotBlank()) { "remote.fileHash cannot be blank" }
        // Validation: On ne fusionne que des fiches du même fichier
        require(local.fileHash == remote.fileHash) {
            "Cannot merge entries for different files: ${local.fileHash} vs ${remote.fileHash}"
        }

        // LWW (Last-Writer-Wins): On préfère toujours la version la plus récente.
        return when {
            remote.versionClock > local.versionClock -> remote
            local.versionClock > remote.versionClock -> local
            else -> {
                // versionClock identiques : CRDT standard académique.
                // Pour chaque fragmentIndex, on élit un seul gagnant : le fragment avec le fragmentHash
                // lexicographiquement le plus grand. Garantit convergence déterministe et commutative
                // sans timestamp physique (les horloges mobiles ne sont pas fiables). (D1-B)
                val mergedFragments = (local.fragmentLocations + remote.fragmentLocations)
                    .groupBy { it.fragmentIndex }
                    .values
                    .map { fragments -> fragments.maxByOrNull { it.fragmentHash }!! }
                    .sortedBy { it.fragmentIndex }

                // Tie-breaker commutative sur ownerPubKeyHash (standard CRDT LWW)
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
