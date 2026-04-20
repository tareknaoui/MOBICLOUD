# Story 4.3: CRDT — Résolution de Conflits de Catalogue

Status: done

## Story

En tant que nœud MobiCloud,
Je veux que les conflits d'état de la DHT soient résolus automatiquement par des règles CRDT,
Afin de garantir la convergence éventuelle sans coordination centrale ni perte de données.

## Acceptance Criteria

1. **Given** deux nœuds ont des versions différentes d'une même entrée DHT (même `blockId`, `timestamps` différents)
2. **When** une synchronisation Gossip-Delta se produit
3. **Then** la règle CRDT LWW (Last-Write-Wins sur `timestamp`) s'applique : l'entrée la plus récente écrase l'ancienne
4. **And** si les timestamps sont identiques, le `nodeId` lexicographiquement supérieur est prioritaire (déterminisme garanti)
5. **And** aucune entrée n'est supprimée sans un `TombstoneEntry` CRDT explicite (évite les résurrections)
6. **And** les `TombstoneEntry` expirées (âge > 24h) sont purgées au démarrage du service
7. **And** la logique CRDT est dans `domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCase.kt`

---

## Dev Agent Guardrails & Context

### Ce qui EXISTE DÉJÀ — NE PAS recréer

| Fichier | Description |
|---|---|
| `domain/repository/DhtRepository.kt` | Interface avec `insertEntry(blockId, nodeId, ipAddress, port)` — **sans timestamp** |
| `domain/models/DhtEntry.kt` | `data class DhtEntry(blockId, nodeId, ipAddress, port, timestamp: Long)` |
| `data/local/entity/DhtEntryEntity.kt` | Entité Room `dht_entries`, version 4 |
| `data/local/dao/DhtDao.kt` | `findByBlockId`, `insertEntry`, `deleteByNodeId`, `observeAllEntries()` |
| `data/repository/DhtRepositoryImpl.kt` | Implémente `insertEntry` en générant `System.currentTimeMillis()` comme timestamp |
| `data/local/CatalogDatabase.kt` | Version **4**, entités déclarées, `MIGRATION_3_4` existante |
| `domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt` | Délègue à `dhtRepository.insertEntry()` — timestamp ignoré |
| `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` | `handleDeltaResponse()` fait un `dhtRepository.insertEntry()` **sans CRDT** — à corriger ici |
| `domain/usecase/m05_dht_catalog/MergeCatalogEntriesUseCase.kt` | CRDT sur `CatalogEntry` (objet différent) — **NE PAS modifier**, c'est un autre domaine |
| `core/format/ProtoBufSerializer.kt` | Sérialiseur kotlinx.serialization avec `ignoreUnknownKeys=true` |
| `data/network/service/MobicloudP2PService.kt` | Foreground Service principal — à modifier pour purge tombstones au démarrage |

### Problème critique résolu par cette story

`GossipSyncUseCase.handleDeltaResponse()` (ligne 164–177) insère aveuglément les entrées DHT reçues sans vérifier si la version locale est plus récente. **Ce bug (F14 déféré en Story 4.2) est résolu ici** : l'insertion doit passer par `ResolveDhtConflictUseCase.resolve()`.

---

### Technical Requirements

#### 1. Nouveau modèle domaine : TombstoneEntry

Créer `domain/models/TombstoneEntry.kt` :

```kotlin
package com.mobicloud.domain.models

data class TombstoneEntry(
    val blockId: String,
    val deletedAt: Long
)
```

#### 2. Nouvelle interface repository : TombstoneRepository

Créer `domain/repository/TombstoneRepository.kt` :

```kotlin
package com.mobicloud.domain.repository

import com.mobicloud.domain.models.TombstoneEntry

interface TombstoneRepository {
    suspend fun insert(tombstone: TombstoneEntry): Result<Unit>
    suspend fun findByBlockId(blockId: String): Result<TombstoneEntry?>
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Result<Int>
    suspend fun existsForBlock(blockId: String): Boolean
}
```

#### 3. Entité Room : TombstoneEntryEntity

Créer `data/local/entity/TombstoneEntryEntity.kt` :

```kotlin
package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobicloud.domain.models.TombstoneEntry

@Entity(tableName = "tombstone_entries")
data class TombstoneEntryEntity(
    @PrimaryKey @ColumnInfo(name = "block_id") val blockId: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long
)

fun TombstoneEntryEntity.toDomain() = TombstoneEntry(blockId = blockId, deletedAt = deletedAt)
fun TombstoneEntry.toEntity() = TombstoneEntryEntity(blockId = blockId, deletedAt = deletedAt)
```

#### 4. DAO : TombstoneDao

Créer `data/local/dao/TombstoneDao.kt` :

```kotlin
package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.TombstoneEntryEntity

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TombstoneEntryEntity)

    @Query("SELECT * FROM tombstone_entries WHERE block_id = :blockId LIMIT 1")
    suspend fun findByBlockId(blockId: String): TombstoneEntryEntity?

    @Query("DELETE FROM tombstone_entries WHERE deleted_at < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM tombstone_entries WHERE block_id = :blockId")
    suspend fun countByBlockId(blockId: String): Int
}
```

#### 5. Implémentation : TombstoneRepositoryImpl

Créer `data/repository/TombstoneRepositoryImpl.kt` :

```kotlin
package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.TombstoneDao
import com.mobicloud.data.local.entity.toEntity
import com.mobicloud.data.local.entity.toDomain
import com.mobicloud.domain.models.TombstoneEntry
import com.mobicloud.domain.repository.TombstoneRepository
import javax.inject.Inject

class TombstoneRepositoryImpl @Inject constructor(
    private val tombstoneDao: TombstoneDao
) : TombstoneRepository {

    override suspend fun insert(tombstone: TombstoneEntry): Result<Unit> =
        runCatching { tombstoneDao.insert(tombstone.toEntity()) }

    override suspend fun findByBlockId(blockId: String): Result<TombstoneEntry?> =
        runCatching { tombstoneDao.findByBlockId(blockId)?.toDomain() }

    override suspend fun deleteOlderThan(cutoffTimestamp: Long): Result<Int> =
        runCatching { tombstoneDao.deleteOlderThan(cutoffTimestamp) }

    override suspend fun existsForBlock(blockId: String): Boolean =
        tombstoneDao.countByBlockId(blockId) > 0
}
```

#### 6. Méthode manquante dans DhtRepository : insertEntryWithTimestamp

Ajouter dans `domain/repository/DhtRepository.kt` :

```kotlin
suspend fun insertEntryWithTimestamp(
    blockId: String,
    nodeId: String,
    ipAddress: String,
    port: Int,
    timestamp: Long
): Result<Unit>
```

Implémenter dans `data/repository/DhtRepositoryImpl.kt` :

```kotlin
override suspend fun insertEntryWithTimestamp(
    blockId: String, nodeId: String, ipAddress: String, port: Int, timestamp: Long
): Result<Unit> = runCatching {
    dhtDao.insert(DhtEntryEntity(blockId = blockId, nodeId = nodeId,
                                  ipAddress = ipAddress, port = port, timestamp = timestamp))
}
```

#### 7. Use Case principal : ResolveDhtConflictUseCase

Créer `domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCase.kt` :

```kotlin
package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.TombstoneEntry
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.TombstoneRepository
import javax.inject.Inject

class ResolveDhtConflictUseCase @Inject constructor(
    private val dhtRepository: DhtRepository,
    private val tombstoneRepository: TombstoneRepository
) {

    companion object {
        const val TOMBSTONE_MAX_AGE_MS = 24 * 60 * 60 * 1000L  // 24 heures
    }

    /**
     * Résout un conflit CRDT LWW lors d'une réception Gossip-Delta.
     * 1. Si un tombstone existe pour ce blockId → ignorer (anti-résurrection)
     * 2. Si aucune entrée locale → insérer directement
     * 3. LWW : timestamp distant > local → remplacer
     * 4. LWW tie-break : timestamps égaux → nodeId lexicographiquement supérieur gagne
     */
    suspend fun resolve(remote: DhtEntry): Result<Unit> {
        // AC#5 : tombstone check — ne jamais ressusciter une entrée supprimée
        if (tombstoneRepository.existsForBlock(remote.blockId)) {
            return Result.success(Unit)
        }

        val localResult = dhtRepository.findByBlockId(remote.blockId)
        val local = localResult.getOrNull()

        return when {
            local == null -> {
                // Aucune entrée locale — insérer directement
                dhtRepository.insertEntryWithTimestamp(
                    remote.blockId, remote.nodeId, remote.ipAddress, remote.port, remote.timestamp
                )
            }
            remote.timestamp > local.timestamp -> {
                // AC#3 : LWW — entrée distante plus récente → remplacer
                dhtRepository.insertEntryWithTimestamp(
                    remote.blockId, remote.nodeId, remote.ipAddress, remote.port, remote.timestamp
                )
            }
            remote.timestamp < local.timestamp -> {
                // Entrée locale plus récente → conserver, rien à faire
                Result.success(Unit)
            }
            else -> {
                // AC#4 : tie-break déterministe sur nodeId lexicographique
                if (remote.nodeId > local.nodeId) {
                    dhtRepository.insertEntryWithTimestamp(
                        remote.blockId, remote.nodeId, remote.ipAddress, remote.port, remote.timestamp
                    )
                } else {
                    Result.success(Unit)
                }
            }
        }
    }

    /**
     * Marque un bloc comme supprimé via un TombstoneEntry CRDT.
     * AC#5 : obligation d'utiliser cette méthode pour toute suppression DHT.
     */
    suspend fun tombstone(blockId: String): Result<Unit> {
        return tombstoneRepository.insert(TombstoneEntry(blockId, System.currentTimeMillis()))
    }

    /**
     * AC#6 : purge des tombstones expirés (âge > 24h).
     * Appelé au démarrage du Foreground Service.
     */
    suspend fun purgeExpiredTombstones(): Result<Int> {
        val cutoff = System.currentTimeMillis() - TOMBSTONE_MAX_AGE_MS
        return tombstoneRepository.deleteOlderThan(cutoff)
    }
}
```

**Règles strictes pour `ResolveDhtConflictUseCase` :**
- ❌ Zéro import Android (`android.*`) — pure Kotlin uniquement
- ❌ Ne pas appeler `dhtRepository.insertEntry()` (sans timestamp) — toujours `insertEntryWithTimestamp()`
- ✅ `Result<T>` pour toutes les méthodes `suspend` publiques

#### 8. Migration Room : version 4 → 5

Dans `data/local/CatalogDatabase.kt` :

- Incrémenter `version = 4` → `version = 5`
- Ajouter `TombstoneEntryEntity::class` dans le tableau `entities`
- Ajouter `abstract fun tombstoneDao(): TombstoneDao`
- Ajouter la constante de migration :

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tombstone_entries (
                block_id TEXT NOT NULL PRIMARY KEY,
                deleted_at INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
```

- Ajouter `MIGRATION_4_5` dans le builder Room (dans le module Hilt `DatabaseModule.kt` ou `P2PModule.kt` — chercher `addMigrations`).

#### 9. Intégration dans GossipSyncUseCase

Modifier `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` — méthode `handleDeltaResponse()` :

**AVANT (comportement actuel — bug F14 déféré) :**
```kotlin
for (dto in response.entries) {
    dhtRepository.insertEntry(dto.blockId, dto.nodeId, dto.ipAddress, dto.port)
}
```

**APRÈS (CRDT intégré) :**
```kotlin
for (dto in response.entries) {
    val entry = DhtEntry(
        blockId = dto.blockId,
        nodeId = dto.nodeId,
        ipAddress = dto.ipAddress,
        port = dto.port,
        timestamp = dto.timestamp
    )
    resolveDhtConflictUseCase.resolve(entry)
        .onFailure { networkEventRepository.pushEvent("[CRDT] Résolution échouée pour ${dto.blockId.take(8)}") }
}
```

Ajouter `resolveDhtConflictUseCase: ResolveDhtConflictUseCase` dans le constructeur de `GossipSyncUseCase` via `@Inject`.

**⚠️ ATTENTION :** Ne modifier QUE `handleDeltaResponse()`. Ne pas toucher à `runGossipCycle()`, `handleIncomingBloom()`, `handleDeltaRequest()`, ni au cycle périodique du service.

#### 10. Purge tombstones au démarrage du service

Dans `data/network/service/MobicloudP2PService.kt` — au démarrage de la coroutine principale (après `startServer()`), ajouter :

```kotlin
serviceScope.launch {
    resolveDhtConflictUseCase.purgeExpiredTombstones()
        .onSuccess { count -> if (count > 0) log("[CRDT] $count tombstones expirés purgés") }
        .onFailure { log("[CRDT] Purge tombstones échouée : ${it.message}") }
}
```

Injecter `ResolveDhtConflictUseCase` dans `MobicloudP2PService` via `@Inject`. Logger via la méthode de log existante du service (ne pas créer de nouvelle méthode de log).

#### 11. Module Hilt : TombstoneModule

Créer `di/TombstoneModule.kt` :

```kotlin
package com.mobicloud.di

import com.mobicloud.data.local.dao.TombstoneDao
import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.repository.TombstoneRepositoryImpl
import com.mobicloud.domain.repository.TombstoneRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TombstoneModule {

    @Provides
    @Singleton
    fun provideTombstoneDao(database: CatalogDatabase): TombstoneDao =
        database.tombstoneDao()

    @Provides
    @Singleton
    fun provideTombstoneRepository(impl: TombstoneRepositoryImpl): TombstoneRepository = impl
}
```

---

### Architecture Compliance

**Emplacement des fichiers :**

| Fichier | Couche | Action |
|---|---|---|
| `domain/models/TombstoneEntry.kt` | Domain | NOUVEAU |
| `domain/repository/TombstoneRepository.kt` | Domain | NOUVEAU |
| `domain/repository/DhtRepository.kt` | Domain | MODIFIER (ajouter `insertEntryWithTimestamp`) |
| `domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCase.kt` | Domain | NOUVEAU |
| `data/local/entity/TombstoneEntryEntity.kt` | Data | NOUVEAU |
| `data/local/dao/TombstoneDao.kt` | Data | NOUVEAU |
| `data/repository/TombstoneRepositoryImpl.kt` | Data | NOUVEAU |
| `data/repository/DhtRepositoryImpl.kt` | Data | MODIFIER (implémenter `insertEntryWithTimestamp`) |
| `data/local/CatalogDatabase.kt` | Data | MODIFIER (v4→v5, tombstones) |
| `di/TombstoneModule.kt` | DI | NOUVEAU |
| `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` | Domain | MODIFIER (handleDeltaResponse uniquement) |
| `data/network/service/MobicloudP2PService.kt` | Data | MODIFIER (purge tombstones au démarrage) |

**Règles d'architecture :**
- ❌ Aucun import Android dans `domain/` (aucune dépendance `android.*`)
- ❌ Ne pas toucher à `MergeCatalogEntriesUseCase` (opère sur `CatalogEntry`, pas `DhtEntry`)
- ❌ Ne pas supprimer `dhtRepository.insertEntry()` de l'interface — c'est utilisé par `InsertDhtEntryUseCase` (Story 5.5)
- ✅ `Result<T>` pour toutes les méthodes `suspend` publiques
- ✅ `TombstoneRepository` injecté dans `ResolveDhtConflictUseCase` uniquement via interface (jamais `TombstoneRepositoryImpl` directement)
- ✅ `ignoreUnknownKeys = true` dans le décodeur Protobuf — conservé

---

### Previous Story Intelligence

**Learnings critiques de Story 4.2 (Gossip) :**

- **F14 (résolu ici) :** `DhtEntryDto.timestamp` était ignoré lors de l'insertion dans `handleDeltaResponse`. `ResolveDhtConflictUseCase.resolve(DhtEntry(..., timestamp = dto.timestamp))` corrige ce déférement.
- **F4 (architecture) :** `GossipSyncUseCase` est dans `domain/` — toute dépendance injectée DOIT être une interface domain (pas une classe Data). `ResolveDhtConflictUseCase` est dans `domain/` ✅, `TombstoneRepository` est une interface domain ✅.
- **F9 :** L'identité locale vient de `securityRepository.getIdentity()` — ne pas utiliser `peerRepository.peers` pour obtenir l'identité locale.
- **BloomFilter singleton :** Ne pas créer un singleton BloomFilter dans le module Hilt pour cette story — ce pattern a créé du dead code en 4.2.
- **Version DB :** `CatalogDatabase` est à version **4**. Cette story l'incrémente à **5**. Vérifier que le builder Room dans le module Hilt inclut **toutes** les migrations (1→2, 2→3, 3→4, 4→5).

**Learnings de Story 4.1 (DHT) :**

- **P6 (guard N=0) :** Déjà corrigé en 4.2 dans `GossipSyncUseCase`. `ResolveDhtConflictUseCase` n'a pas ce problème — il opère sur une entrée individuelle.
- **Room thread-safety :** Les DAO Room sont thread-safe nativement. Pas de `Mutex` nécessaire pour `TombstoneDao`.
- **`@ApplicationScope` :** Si `ResolveDhtConflictUseCase` a besoin d'un scope coroutine (il n'en a pas — toutes les méthodes sont `suspend`), utiliser `@ApplicationScope` injecté, pas `GlobalScope`.

**Git — commits récents :**
- `b0e87f6 bug fix` et `ce18fc5 update` (après 4.2) — probablement correctifs de Review 4.2. Vérifier que les patches F1–F9 sont appliqués avant de modifier `GossipSyncUseCase`.

---

### Testing Requirements

Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCaseTest.kt` :

**Dépendances de test :** MockK pour mocker `DhtRepository` et `TombstoneRepository`. `runTest` de `kotlinx-coroutines-test`.

**Tests obligatoires :**

```
Test 1 — LWW : entrée distante plus récente remplace locale
  - local.timestamp = 1000, remote.timestamp = 2000
  → insertEntryWithTimestamp(remote) appelé

Test 2 — LWW : entrée locale plus récente conservée
  - local.timestamp = 2000, remote.timestamp = 1000
  → insertEntryWithTimestamp NON appelé, Result.success(Unit)

Test 3 — Tie-break : timestamps identiques, remote.nodeId > local.nodeId
  - local.nodeId = "aaa", remote.nodeId = "zzz", timestamps = 1000
  → insertEntryWithTimestamp(remote) appelé

Test 4 — Tie-break : timestamps identiques, local.nodeId > remote.nodeId
  - local.nodeId = "zzz", remote.nodeId = "aaa", timestamps = 1000
  → insertEntryWithTimestamp NON appelé

Test 5 — Anti-résurrection : tombstone existe pour blockId
  - tombstoneRepository.existsForBlock(blockId) = true
  → insertEntryWithTimestamp NON appelé, Result.success(Unit)

Test 6 — Nouvelle entrée (pas de local)
  - dhtRepository.findByBlockId = null
  → insertEntryWithTimestamp(remote) appelé directement

Test 7 — tombstone() insère un TombstoneEntry
  - résultat : tombstoneRepository.insert() appelé avec blockId correct

Test 8 — purgeExpiredTombstones() délègue à tombstoneRepository.deleteOlderThan(cutoff)
  - résultat : cutoff = System.currentTimeMillis() - 86400000 (approx)
```

**Mocking :** `mockk<DhtRepository>()` et `mockk<TombstoneRepository>()`. Ne pas utiliser Robolectric — tests JVM purs.

---

### NFR Compliance

**NFR-01 (Convergence ≤ 3 secondes) :**
- `resolve()` est synchrone (Room query + optional insert) : < 10ms par entrée. Pas de risque sur NFR-01.
- Ne pas ajouter de `delay()` ou d'opérations réseaux dans `ResolveDhtConflictUseCase`.

**NFR-03 (Overhead CPU ≤ 5%) :**
- La résolution CRDT s'exécute dans `handleDeltaResponse()` sur `Dispatchers.Default`.
- L'overhead additionnel (1 Room query par entrée reçue) est négligeable vs le cycle Gossip 2s.
- `purgeExpiredTombstones()` s'exécute une seule fois au démarrage — pas d'overhead récurrent.

---

## Tasks / Subtasks

- [x] Task 1: Créer les modèles domaine CRDT (AC: #5)
  - [x] Subtask 1.1: Créer `domain/models/TombstoneEntry.kt`
  - [x] Subtask 1.2: Créer `domain/repository/TombstoneRepository.kt`

- [x] Task 2: Étendre DhtRepository avec timestamp explicite (AC: #3, #4)
  - [x] Subtask 2.1: Ajouter `insertEntryWithTimestamp()` dans `domain/repository/DhtRepository.kt`
  - [x] Subtask 2.2: Implémenter dans `data/repository/DhtRepositoryImpl.kt`

- [x] Task 3: Implémenter la couche persistance Tombstone (AC: #5, #6)
  - [x] Subtask 3.1: Créer `data/local/entity/TombstoneEntryEntity.kt`
  - [x] Subtask 3.2: Créer `data/local/dao/TombstoneDao.kt`
  - [x] Subtask 3.3: Créer `data/repository/TombstoneRepositoryImpl.kt`
  - [x] Subtask 3.4: Mettre à jour `data/local/CatalogDatabase.kt` (version 4→5, tombstoneDao(), MIGRATION_4_5)
  - [x] Subtask 3.5: Ajouter `MIGRATION_4_5` dans le builder Room (module Hilt)

- [x] Task 4: Implémenter ResolveDhtConflictUseCase (AC: #3, #4, #5, #6, #7)
  - [x] Subtask 4.1: Créer `domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCase.kt`
  - [x] Subtask 4.2: Implémenter `resolve()` (LWW + tie-break + tombstone guard)
  - [x] Subtask 4.3: Implémenter `tombstone()` et `purgeExpiredTombstones()`

- [x] Task 5: Configurer l'injection Hilt (AC: #7)
  - [x] Subtask 5.1: Créer `di/TombstoneModule.kt`

- [x] Task 6: Intégrer le CRDT dans le pipeline Gossip (AC: #3, #4, #5)
  - [x] Subtask 6.1: Injecter `ResolveDhtConflictUseCase` dans `GossipSyncUseCase`
  - [x] Subtask 6.2: Modifier `handleDeltaResponse()` pour appeler `resolve()` au lieu de `insertEntry()`

- [x] Task 7: Purge tombstones au démarrage du service (AC: #6)
  - [x] Subtask 7.1: Injecter `ResolveDhtConflictUseCase` dans `MobicloudP2PService`
  - [x] Subtask 7.2: Appeler `purgeExpiredTombstones()` au démarrage de la coroutine principale

- [x] Task 8: Écrire les tests unitaires (All ACs)
  - [x] Subtask 8.1: Créer `ResolveDhtConflictUseCaseTest.kt` (8 tests)

### Review Findings

- [x] [Review][Defer] `tombstone()` n'a aucun appelant en production — AC#5 inerte jusqu'à câblage [`ResolveDhtConflictUseCase.kt:53`] — deferred, aucun flux de suppression DHT existant ; à câbler dans la première story introduisant une suppression d'entrée DHT (Epic 7 ou story dédiée)

- [x] [Review][Patch] `existsForBlock` non-wrappé : exception DB s'échappe du contrat `Result<Unit>` de `resolve()` [`TombstoneRepositoryImpl.kt:23`]
- [x] [Review][Patch] `findByBlockId` `Result.failure` silencieusement traité comme `null` → insertion inconditionnelle en cas d'erreur DB [`ResolveDhtConflictUseCase.kt:30`]
- [x] [Review][Patch] `TombstoneModule` utilise `@Provides` au lieu de `@Binds` pour le binding `TombstoneRepositoryImpl → TombstoneRepository` [`TombstoneModule.kt:22`]
- [x] [Review][Patch] Message d'échec CRDT dans `handleDeltaResponse` perd l'exception réelle (l'`it` est capturé mais non inclus) [`GossipSyncUseCase.kt:178`]
- [x] [Review][Patch] `ResolveDhtConflictUseCase` n'a pas d'annotation `@Singleton` — instances multiples créées si injectée en plusieurs sites [`ResolveDhtConflictUseCase.kt:9`]

- [x] [Review][Defer] Race TOCTOU entre vérification tombstone et insert (cross-DAO, pas de transaction Room atomique) [`ResolveDhtConflictUseCase.kt:26-49`] — deferred, correction nécessite architecture Room @Transaction multi-DAO ou Mutex
- [x] [Review][Defer] `MIGRATION_1_2` absent de `addMigrations` — pré-existant, données détruites sur appareils à DB v1 [`IdentityModule.kt:42`] — deferred, pre-existing
- [x] [Review][Defer] `fallbackToDestructiveMigration()` retenu aux côtés des migrations explicites [`IdentityModule.kt:45`] — deferred, pre-existing
- [x] [Review][Defer] Validation `dto.timestamp` absente (0L, négatif, futur) — périmètre plus large que cette story [`GossipSyncUseCase.kt:170`] — deferred, scope plus large
- [x] [Review][Defer] `InsertDhtEntryUseCase` contourne le CRDT — spec demande de conserver `insertEntry()`, correction en story future [`InsertDhtEntryUseCase.kt:16`] — deferred, future story
- [x] [Review][Defer] `LookupBlockLocationUseCase` retourne des entrées tombstonées sans vérification tombstone [`LookupBlockLocationUseCase.kt:12`] — deferred, future story
- [x] [Review][Defer] `handleDeltaRequest` envoie des blocs tombstonés aux pairs distants — propagation tombstone gossip hors scope [`GossipSyncUseCase.kt:140`] — deferred, future story
- [x] [Review][Defer] AC#5 tombstone guard local uniquement — résurrection possible depuis pairs n'ayant pas le tombstone — propagation non prévue dans cette story [`ResolveDhtConflictUseCase.kt:26`] — deferred, future story
- [x] [Review][Defer] `runBlocking` dans `onDeltaSyncRequestReceived` — pré-existant Story 4.2 [`GossipSyncUseCase.kt:88`] — deferred, pre-existing (F13 story 4.2)
- [x] [Review][Defer] `exportSchema = false` — pré-existant, bloque la vérification des migrations Room [`CatalogDatabase.kt:10`] — deferred, pre-existing

## Change Log

- Story 4.3 implémentée : CRDT LWW + tombstones + purge au démarrage (Date: 2026-04-20)
- Bug F14 corrigé : `handleDeltaResponse()` passe par `ResolveDhtConflictUseCase.resolve()` au lieu de `insertEntry()` aveugle
- Migration Room v4→v5 ajoutée avec table `tombstone_entries`
- Migrations MIGRATION_3_4 et MIGRATION_4_5 enregistrées dans `IdentityModule`

## Dev Notes

Implémentation CRDT LWW complète. `ResolveDhtConflictUseCase` est pur Kotlin sans dépendance Android.
Ordre de priorité de résolution : tombstone guard > no-local insert > LWW timestamp > tie-break nodeId lexicographique.

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Completion Notes

- Créé `TombstoneEntry` (domain model) et `TombstoneRepository` (interface domain)
- Créé `TombstoneEntryEntity`, `TombstoneDao`, `TombstoneRepositoryImpl` (couche data)
- Ajouté `insertEntryWithTimestamp()` dans `DhtRepository` et `DhtRepositoryImpl`
- `CatalogDatabase` mis à jour v4→v5 avec entité `TombstoneEntryEntity`, DAO `tombstoneDao()`, et `MIGRATION_4_5`
- `IdentityModule` mis à jour : toutes les migrations 2→3, 3→4, 4→5 désormais enregistrées
- `ResolveDhtConflictUseCase` implémente LWW + tie-break nodeId + tombstone anti-résurrection + purge 24h
- `TombstoneModule` Hilt créé pour l'injection
- `GossipSyncUseCase.handleDeltaResponse()` corrigé : utilise `resolve()` au lieu de `insertEntry()` (fix F14)
- `MobicloudP2PService` : purge des tombstones expirés au démarrage, après `startServer()`
- `ResolveDhtConflictUseCaseTest` : 8 tests couvrant tous les ACs (LWW, tie-break, tombstone, purge)
- `GossipSyncUseCaseTest` mis à jour pour inclure le nouveau paramètre `resolveDhtConflictUseCase`
- Suite complète : BUILD SUCCESSFUL, aucune régression

## File List

**NOUVEAUX FICHIERS :**
- `app/src/main/kotlin/com/mobicloud/domain/models/TombstoneEntry.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/TombstoneRepository.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/TombstoneEntryEntity.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/dao/TombstoneDao.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/TombstoneRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/TombstoneModule.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCaseTest.kt`

**FICHIERS MODIFIÉS (avec précaution) :**
- `app/src/main/kotlin/com/mobicloud/domain/repository/DhtRepository.kt` (ajouter `insertEntryWithTimestamp`)
- `app/src/main/kotlin/com/mobicloud/data/repository/DhtRepositoryImpl.kt` (implémenter `insertEntryWithTimestamp`)
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt` (v4→v5, `TombstoneEntryEntity`, `tombstoneDao()`, `MIGRATION_4_5`)
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` (constructeur + `handleDeltaResponse()` uniquement)
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (purge tombstones au démarrage)
