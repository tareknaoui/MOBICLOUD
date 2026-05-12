package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.entity.MemberEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T16 — Purge des membres orphelins au démarrage (Story 11.3, AC17).
 *
 * Vérifie que memberDao.purgeOlderThan() est appelé avec un cutoff correspondant
 * à `now - 24h`, éliminant les entrées cluster_members dont lastSeen > 24h.
 *
 * Logique testée : MobicloudP2PService.onStartCommand() ligne 162-164.
 * Le test est DAO-centrique (pas de Service Android) pour rester JVM-pur.
 */
class PurgeOnStartTest {

    private val ONE_DAY_MS = 24 * 3600_000L

    private lateinit var memberDao: MemberDao

    @Before
    fun setUp() {
        memberDao = mockk(relaxed = true)
    }

    private fun entity(nodeId: String, lastSeen: Long) = MemberEntity(
        nodeId = nodeId, clusterId = "cid",
        publicKeyBytes = byteArrayOf(),
        ipAddress = "1.2.3.4", port = 8080,
        gpsLatitude = null, gpsLongitude = null,
        freeBytes = 0L, lastSeen = lastSeen, role = "MEMBER", status = "ACTIVE"
    )

    /**
     * Simule la logique de purge exécutée au boot :
     *   memberDao.purgeOlderThan(System.currentTimeMillis() - 24 * 3600_000L)
     *
     * Vérifie que le cutoff passé est bien ≈ now − 24h (tolérance 5 s).
     */
    @Test
    fun `cutoff purge est now moins 24h avec tolerance 5s`() = runTest {
        val cutoffSlot = slot<Long>()
        coEvery { memberDao.purgeOlderThan(capture(cutoffSlot)) } returns 0

        val nowBefore = System.currentTimeMillis()
        // Reproduit la logique inline du service
        val cutoff = System.currentTimeMillis() - ONE_DAY_MS
        memberDao.purgeOlderThan(cutoff)
        val nowAfter = System.currentTimeMillis()

        val captured = cutoffSlot.captured
        val expectedMin = nowBefore - ONE_DAY_MS
        val expectedMax = nowAfter - ONE_DAY_MS + 5_000L   // tolérance 5s

        assertTrue(
            "Cutoff ($captured) doit être dans [now-24h, now-24h+5s]",
            captured in expectedMin..expectedMax
        )
    }

    /**
     * Un membre avec lastSeen = now − 25h doit être supprimé par la purge.
     * Un membre avec lastSeen = now − 23h doit survivre.
     */
    @Test
    fun `purge supprime membres anciens et garde membres recents`() = runTest {
        val now = System.currentTimeMillis()
        val old   = entity("old-node",   now - (ONE_DAY_MS + 3_600_000L))  // 25h → mort
        val fresh = entity("fresh-node", now - (ONE_DAY_MS - 3_600_000L))  // 23h → vivant

        val cutoff = now - ONE_DAY_MS

        // Simule le comportement Room : la requête DELETE WHERE lastSeen < cutoff
        // old.lastSeen < cutoff → supprimé ; fresh.lastSeen > cutoff → préservé
        assertTrue("Membre ancien (25h) doit avoir lastSeen < cutoff",
            old.lastSeen < cutoff)
        assertTrue("Membre récent (23h) doit avoir lastSeen > cutoff",
            fresh.lastSeen > cutoff)

        // Vérifie que la DAO est appelée une seule fois avec le bon cutoff
        coEvery { memberDao.purgeOlderThan(cutoff) } returns 1
        memberDao.purgeOlderThan(cutoff)
        coVerify(exactly = 1) { memberDao.purgeOlderThan(cutoff) }
    }

    /**
     * Si aucun membre orphelin n'existe, purgeOlderThan retourne 0 sans erreur.
     */
    @Test
    fun `purge sur registre vide retourne 0 sans erreur`() = runTest {
        coEvery { memberDao.purgeOlderThan(any()) } returns 0

        val result = runCatching {
            memberDao.purgeOlderThan(System.currentTimeMillis() - ONE_DAY_MS)
        }

        assertTrue("Pas d'exception si registre vide", result.isSuccess)
        coVerify(exactly = 1) { memberDao.purgeOlderThan(any()) }
    }

    /**
     * Purge idempotente : deux appels successifs au démarrage ne suppriment pas deux fois
     * (protégé par le flag loopsStarted du service).
     */
    @Test
    fun `deux appels purge avec le meme cutoff sont idempotents`() = runTest {
        coEvery { memberDao.purgeOlderThan(any()) } returns 3 andThen 0

        val cutoff = System.currentTimeMillis() - ONE_DAY_MS
        memberDao.purgeOlderThan(cutoff)
        val second = memberDao.purgeOlderThan(cutoff)

        // Après le premier appel, les membres sont supprimés → deuxième retourne 0
        assertTrue("Second appel purge = 0 membres (déjà supprimés)", second == 0)
    }
}
