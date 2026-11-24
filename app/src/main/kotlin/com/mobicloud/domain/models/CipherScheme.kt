package com.mobicloud.domain.models

/**
 * Ordre chiffrement / encodage Reed-Solomon d'un fichier, enregistré dans son entrée de catalogue.
 */
object CipherScheme {
    /**
     * Ancien format : encodage RS du clair, puis chiffrement AES-GCM de chaque fragment avec une
     * clé dérivée par fragment. Régénérer un fragment perdu exige K_F (propriétaire seul).
     */
    const val ENCODE_THEN_ENCRYPT = 0

    /**
     * Chiffrement AES-GCM du fichier entier, puis encodage RS du chiffré. Les fragments sont des
     * morceaux de chiffré : un nœud détenant k fragments peut régénérer les autres sans K_F.
     */
    const val ENCRYPT_THEN_ENCODE = 1
}
