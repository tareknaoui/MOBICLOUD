package com.mobicloud.domain.util

import java.util.concurrent.atomic.AtomicLong

/**
 * Horloge logique hybride (HLC) — résiste aux dérives et retours en arrière de
 * l'horloge système Android.
 *
 * Format encodé sur Long : 48 bits physical (millis Unix) | 16 bits logical (compteur).
 * Garantit `now() < now()` strict pour deux appels successifs, même si `currentTimeMillis()`
 * recule (ajustement NTP, changement manuel d'horloge, etc.).
 *
 * Utilisé comme `versionClock` dans `CatalogEntry` pour résoudre les conflits CRDT
 * (Story 4.3) sans risque de collision millis.
 */
object HybridLogicalClock {

    private const val LOGICAL_BITS = 16
    private const val LOGICAL_MASK = (1L shl LOGICAL_BITS) - 1L

    private val state = AtomicLong(0L)

    /**
     * Retourne un timestamp HLC strictement supérieur à tous les précédents émis par ce process.
     *
     * Si l'horloge physique avance, le compteur logique est remis à 0.
     * Si l'horloge physique recule ou est égale, le compteur logique est incrémenté.
     */
    fun now(): Long {
        val physicalNow = System.currentTimeMillis()
        while (true) {
            val prev = state.get()
            val prevPhysical = prev ushr LOGICAL_BITS
            val prevLogical = prev and LOGICAL_MASK
            val (newPhysical, newLogical) = if (physicalNow > prevPhysical) {
                physicalNow to 0L
            } else {
                prevPhysical to (prevLogical + 1L).coerceAtMost(LOGICAL_MASK)
            }
            val candidate = (newPhysical shl LOGICAL_BITS) or newLogical
            if (state.compareAndSet(prev, candidate)) return candidate
        }
    }
}
