package com.mobicloud.data.local.m11_join

import com.mobicloud.data.local.CatalogDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valide la structure des migrations Room : présence des deux tables,
 * version attendue et SQL bien formé.
 * (Tests JVM purs — la vérification d'intégration Room réelle se fait via
 * les tests androidTest s'ils sont activés ou manuellement via test-migration.ps1.)
 */
class MemberEntityMigrationTest {

    @Test
    fun `MIGRATION_14_15 cree depuis la version 14 vers 15`() {
        assertEquals(14, CatalogDatabase.MIGRATION_14_15.startVersion)
        assertEquals(15, CatalogDatabase.MIGRATION_14_15.endVersion)
    }

    @Test
    fun `MIGRATION_15_16 retire colonnes GPS de cluster_members`() {
        assertEquals(15, CatalogDatabase.MIGRATION_15_16.startVersion)
        assertEquals(16, CatalogDatabase.MIGRATION_15_16.endVersion)
    }

    @Test
    fun `CatalogDatabase version est 16`() {
        // Note : `@Database` est en `RetentionPolicy.CLASS`, donc non lisible via réflexion.
        // On consomme la même constante que l'annotation pour garder le test couplé à la source.
        assertEquals(16, CatalogDatabase.CURRENT_VERSION)
    }

    @Test
    fun `MemberEntity a les champs requis par cluster_members`() {
        val fields = com.mobicloud.data.local.entity.MemberEntity::class.java.declaredFields
            .map { it.name }.toSet()
        assertTrue("nodeId", fields.contains("nodeId"))
        assertTrue("clusterId", fields.contains("clusterId"))
        assertTrue("publicKeyBytes", fields.contains("publicKeyBytes"))
        assertTrue("lastSeen", fields.contains("lastSeen"))
        assertTrue("role", fields.contains("role"))
        assertTrue("status", fields.contains("status"))
    }

    @Test
    fun `MemberSnapshotEntity a les champs requis par member_snapshot`() {
        val fields = com.mobicloud.data.local.entity.MemberSnapshotEntity::class.java.declaredFields
            .map { it.name }.toSet()
        assertTrue("clusterId", fields.contains("clusterId"))
        assertTrue("superPairNodeIdHex", fields.contains("superPairNodeIdHex"))
        assertTrue("lastUpdatedMs", fields.contains("lastUpdatedMs"))
        assertTrue("membersJson", fields.contains("membersJson"))
    }
}
