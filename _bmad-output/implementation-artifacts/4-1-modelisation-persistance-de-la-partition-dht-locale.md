# Story 4.1: Modélisation & Persistance de la Partition DHT Locale

Status: review

## Story

En tant que nœud MobiCloud,
Je veux maintenir localement ma partition de l'anneau DHT dans Room DB,
Afin de répondre aux requêtes de localisation de blocs sans aucune infrastructure centralisée.

## Acceptance Criteria

1. **Given** le nœud a rejoint le cluster et connaît ses pairs voisins
2. **When** un bloc est stocké sur ce nœud
3. **Then** une entrée `DhtEntry(blockId, nodeId, ipAddress, port, timestamp)` est insérée dans Room DB (table `dht_entries`)
4. **And** la partition assignée est déterminée par hachage consistant : `hash(blockId) mod N` où N = nombre de nœuds qualifiés
5. **And** le nœud peut répondre à une requête `LOOKUP(blockId)` avec l'`ipAddress:port` du nœud détenteur
6. **And** l'anneau DHT est accessible via `domain/repository/DhtRepository.kt` (interface pure Kotlin)
7. **And** `data/local/DhtDao.kt` implémente les requêtes Room nécessaires (`findByBlockId`, `insertEntry`, `deleteByNodeId`)

## Dev Agent Guardrails & Context

### Technical Requirements

**Data Model & Entities:**
- Créer l'entité Room `DhtEntryEntity` dans `data/local/entity/DhtEntryEntity.kt` avec les colonnes :
  - `blockId: String` (primary key / indexed pour LOOKUP)
  - `nodeId: String` (clé publique du détenteur, indexée)
  - `ipAddress: String` (adresse IP publique/LAN du nœud détenteur)
  - `port: Int` (numéro de port TCP du détenteur)
  - `timestamp: Long` (timestamp d'insertion Unix, pour TTL/expiration future)
- Utiliser `@PrimaryKey` sur `blockId` (pas d'ID synthétique) — chaque bloc a une seule entrée DHT primaire par partition.

**Consistent Hashing Algorithm:**
- Implémenter `ConsistentHashRing` dans `domain/usecase/m05_dht_catalog/` comme une classe pure Kotlin (sans dépendance Android).
- La partition assignée au bloc `blockId` = `hash(blockId) mod N` où N = nombre de nœuds qualifiés (`ACTIVE` + `INACTIVE` récent).
- Utiliser `MessageDigest.getInstance("SHA-256")` pour le hachage (stable, déterministe).
- **Critique :** La fonction de hachage DOIT être identique sur tous les nœuds du cluster (sinon désynchronisation DHT).

**Repository & DAO:**
- Interface `DhtRepository.kt` dans `domain/repository/` avec méthodes pures :
  - `suspend fun insertEntry(blockId: String, nodeId: String, ipAddress: String, port: Int): Result<Unit>`
  - `suspend fun findByBlockId(blockId: String): Result<DhtEntry?>`
  - `suspend fun findByNodeId(nodeId: String): Result<List<DhtEntry>>`
  - `suspend fun deleteByNodeId(nodeId: String): Result<Unit>`
  - `fun observeAllEntries(): Flow<List<DhtEntry>>` (pour observer les changements DHT)
- DAO `DhtDao.kt` dans `data/local/` implémente les opérations Room :
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entry: DhtEntryEntity)`
  - `@Query("SELECT * FROM dht_entries WHERE block_id = :blockId LIMIT 1") suspend fun findByBlockId(blockId: String): DhtEntryEntity?`
  - `@Query("SELECT * FROM dht_entries WHERE node_id = :nodeId") suspend fun findByNodeId(nodeId: String): List<DhtEntryEntity>`
  - `@Query("DELETE FROM dht_entries WHERE node_id = :nodeId") suspend fun deleteByNodeId(nodeId: String)`
  - `@Query("SELECT * FROM dht_entries ORDER BY timestamp DESC") fun observeAllEntries(): Flow<List<DhtEntryEntity>>`
- Implémentation concrète `DhtRepositoryImpl.kt` dans `data/repository/` qui mappe `DhtEntryEntity` ↔ `DhtEntry` domain model.

**Domain Models:**
- Créer le modèle `DhtEntry` dans `domain/models/` :
  ```kotlin
  data class DhtEntry(
      val blockId: String,
      val nodeId: String,
      val ipAddress: String,
      val port: Int,
      val timestamp: Long
  )
  ```

**Error Handling:**
- Respecter le pattern `Result<T>` obligatoire de l'architecture. Les méthodes `insert()`, `findByBlockId()` etc. doivent retourner `Result<T>` pour capturer toute erreur Room/DB.
- Exemples d'erreurs possibles : corruption base de données (rare), espace disque insuffisant, entité mal formée.

### Architecture Compliance

**Code Organization:**
- **Domain layer :** `domain/models/DhtEntry.kt`, `domain/repository/DhtRepository.kt`, `domain/usecase/m05_dht_catalog/ConsistentHashRing.kt`
- **Data layer :** `data/local/entity/DhtEntryEntity.kt`, `data/local/DhtDao.kt`, `data/repository/DhtRepositoryImpl.kt`
- **Database layer :** Configuration Room dans `core/database/` ou directement dans le Module Hilt correspondant.

**Injection & DI (Hilt):**
- Créer un module Hilt `DhtModule.kt` dans `di/` si inexistant, pour :
  - Exposer `DhtDao` depuis la base de données
  - Bind `DhtRepository` → `DhtRepositoryImpl` avec `@Provides`
- Injecter `DhtRepository` dans les UseCases appelants (Epic 4 Stories suivantes comme 4.2 Gossip, 4.3 CRDT).

**Database Configuration:**
- La table `dht_entries` doit être déclarée dans `AppDatabase.kt` (ou équivalent) via `@Entity`.
- Version de base de données : à incrémenter si modification du schéma (Room FORCE migrations).
- Migrations Room : créer une migration vide si c'est la première version (aucune migration antérieure).

**Dispatcher & Coroutines:**
- Les opérations DAO s'exécutent sur `Dispatchers.IO` (fourni automatiquement par Room Coroutines).
- Les opérations métier (hachage, partition) sur `Dispatchers.Default` si lourdes (peu probable ici).

**Protobuf & Serialization:**
- Si `DhtEntry` doit être sérialisée en messages Protobuf P2P (pour Gossip, Story 4.2), créer un fichier `.proto` avec les mappages.
- Exemple : `DhtEntryProto { blockId, nodeId, ipAddress, port, timestamp }` dans `core/format/`.
- Utiliser `kotlinx.serialization` avec `ignoreUnknownKeys=true` pour forward-compatibility.

### Testing Requirements

**Unit Tests (JVM, sans émulateur):**
- Créer `DhtRepositoryImplTest.kt` utilisant Robolectric ou une implémentation en mémoire de Room pour les tests JVM purs.
- **Test 1 :** `insertEntry()` insère correctement une entrée et `findByBlockId()` la retrouve.
- **Test 2 :** `findByNodeId()` retourne toutes les entrées d'un nœud donné.
- **Test 3 :** `deleteByNodeId()` supprime toutes les entrées d'un nœud.
- **Test 4 :** `observeAllEntries()` émet les changements via Flow lorsque des insertions se produisent.
- **Test 5 :** `Result<T>` type error handling — une insertion avec données invalides retourne `Result.Failure`.

**Consistent Hash Ring Tests:**
- Créer `ConsistentHashRingTest.kt` :
- **Test 1 :** Deux appels avec le même `blockId` produisent le même hash (déterminisme).
- **Test 2 :** Le hash est dans l'intervalle `[0, N-1]` pour N nœuds.
- **Test 3 :** Changement du nombre de nœuds (N) → recalcul de partition correct.
- **Test 4 :** Comparaison cross-node : deux instances `ConsistentHashRing` avec mêmes nœuds calculent mêmes hashes.

**Integration Test (optionnel, post-dev):**
- Tester l'intégration Room en environnement d'émulateur Android (test Robolectric ou `@RunWith(RobolectricTestRunner.class)`).
- Valider que la création de table `dht_entries` réussit à l'initialisation de l'app.

### Previous Story Intelligence (Epic 3, Story 3.4)

**Learnings du Circuit-Breaker (3.4):**
- **Thread-Safety via Mutex :** Le Circuit-Breaker a adopté un `Mutex` pour les opérations concurrentes sur `churnHistory`. La DHT peut recevoir des insertions parallèles depuis plusieurs sources (Gossip, réception de fragments). **VOUS DEVEZ AJOUTER** un mécanisme de synchronisation ou une approche immutable pour protéger les opérations DAO Room concurrentes. Room fournit nativement la thread-safety pour les DAO, mais les opérations métier autour (hachage, partition) pourraient nécessiter une protection.
- **Time-based Cleanup :** Story 3.4 a implanté des timers d'expiration (5 min pour churn, 24h pour tombstones). Pour Story 4.1, **prévoir un cleanup des entrées DHT expirées** (par exemple, âge > 24h) au démarrage du service ou en tâche de fond.

**Review Outcomes de 3.4 (Applicable ici):**
- Pattern `@ApplicationScope` : Si vous devez un scheduler de nettoyage DHT, envisager d'injecter `@ApplicationScope` pour la durée de vie de l'app.
- Atomicité : Comme avec les timers de Circuit-Breaker, les opérations DHT (insertion + Gossip trigger) doivent être atomiques ou au moins ordonnées proprement.

**Patterns Adoptés (À RÉUTILISER) :**
- Pas d'exception silencieuse : Story 3.4 a mis en place `Result<T>` systemically. **Respectez ce pattern identique** dans cette story (insertEntry retourne `Result<Unit>`, etc.).
- StateFlow pour exposition : Story 3.4 expose l'état (circuit ouvert/fermé) via `StateFlow`. Pour la DHT, exposer `observeAllEntries(): Flow<List<DhtEntry>>` permet à Story 4.2 (Gossip) d'écouter les changements.
- Logging via `NetworkEventRepository` : Story 3.4 loggait les événements critiques (activation Circuit-Breaker) via `NetworkEventRepository`. **Loggez les insertions/suppressions DHT importantes** (bloc orphelin supprimé, nœud entièrement nettoyé) de manière similaire pour la visibilité.

### Project Context Reference

**Architecture Decision: DHT + Gossip (Source: architecture.md §2, p.49-50):**
> "Le protocole Gossip épidémique est circumscrit aux *Replica Sets* (nœuds gérant la même partition DHT) pour les métadonnées de catalogue. Un Gossip ultra-léger (Heartbeat) persiste pour la topologie vivant/mort. Firebase n'est jamais impliqué dans la synchronisation du catalogue — c'est une responsabilité DHT/CRDT exclusive."

**Implication:** La partition DHT locale (Story 4.1) est le **fondation de toute la distribution catalogue.** Chaque nœud ne gère que sa propre partition (via hachage consistant). La synchronisation avec les replicas se fera en Story 4.2 (Gossip).

**Architecture Decision: Consistent Hashing (Source: architecture.md §3, p.175):**
> "Cross-Component Dependencies: ...Protobuf est omniprésent : Les sockets réseau le consomment, le Gossip le génère, et Room DB le persiste parfois..."

**Implication:** Le `blockId` doit être une clé stable et sérialisable. Utiliser le hash SHA-256 du fichier ou un UUID stable (NOT random per fetch). **Validez que blockId est toujours identique sur tous les nœuds pour un même bloc.**

**Architecture Decision: Room as Single Source of Truth (Source: architecture.md §3, p.147):**
> "Stockage Local Catalogue : Jetpack Room (SQLite) pour la persistance locale de la partition DHT assignée au nœud, permettant des requêtes 'Zéro-Latence' locales (< 100ms)."

**Implication:** Room n'est pas un cache optionnel — c'est la source principale. Les réponses aux requêtes `LOOKUP` doivent être servies directement depuis la base (pas de requête réseau intermédiaire).

**Architecture Decision: Stateless API Layer (Source: architecture.md, pattern enforcement, p.224):**
> "Les revues de code (`bmad-code-review`) rejetteront toute implémentation qui mute un état global contournant la Clean Architecture ou qui jette une exception non gérée."

**Implication:** Aucune variable statique globale pour la DHT (ex: `companion object`). Utiliser l'injection Hilt et le Repository pattern.

### Patterns & Conventions to Follow

**Naming Conventions (Source: architecture.md §4, p.186-198):**
- Tables Room : `snake_case` (`dht_entries`)
- Classes Kotlin : `PascalCase` (`DhtEntry`, `DhtEntryEntity`, `DhtRepository`)
- DAO methods : Verbes clairs (`findByBlockId`, `insertEntry`, `deleteByNodeId`)

**Error Handling (Source: architecture.md, Error Handling, p.214):**
> "Utilisation OBLIGATOIRE du validateur natif `Result<T>` ou d'une `sealed class` (ex: `Resource<T>`) pour chaque retour de couche Data ou Usecase."

**Implication:** **Chaque DAO method retourne `Result<T>`** (excepté les Flow qui gèrent les erreurs différemment). Exemples :
```kotlin
suspend fun insertEntry(...): Result<Unit>
suspend fun findByBlockId(...): Result<DhtEntry?>
suspend fun deleteByNodeId(...): Result<Unit>
```

## Tasks / Subtasks

- [x] Task 1: Créer le modèle domaine DHT (AC: #3, #6)
  - [x] Subtask 1.1: Créer `domain/models/DhtEntry.kt`
  - [x] Subtask 1.2: Créer l'interface `domain/repository/DhtRepository.kt`

- [x] Task 2: Implémenter l'algorithme de hachage consistant (AC: #4)
  - [x] Subtask 2.1: Créer `domain/usecase/m05_dht_catalog/ConsistentHashRing.kt` (logique pure)
  - [x] Subtask 2.2: Créer tests `ConsistentHashRingTest.kt` (déterminisme, cross-node validation)

- [x] Task 3: Configurer Room Database et entités (AC: #3, #7)
  - [x] Subtask 3.1: Créer `data/local/entity/DhtEntryEntity.kt`
  - [x] Subtask 3.2: Mettre à jour `core/database/AppDatabase.kt` (ou créer) pour ajouter la table
  - [x] Subtask 3.3: Créer `data/local/DhtDao.kt` avec opérations CRUD + Flow

- [x] Task 4: Implémenter le repository DHT (AC: #6, #7)
  - [x] Subtask 4.1: Créer `data/repository/DhtRepositoryImpl.kt`
  - [x] Subtask 4.2: Mapper `DhtEntryEntity` ↔ `DhtEntry` domain model

- [x] Task 5: Configurer l'injection Hilt (AC: #6)
  - [x] Subtask 5.1: Créer ou mettre à jour `di/DhtModule.kt`
  - [x] Subtask 5.2: Binder `DhtRepository` → `DhtRepositoryImpl`

- [x] Task 6: Écrire les tests unitaires (All ACs)
  - [x] Subtask 6.1: Créer `DhtRepositoryImplTest.kt` (9 tests: insert, find, delete, observeAll, error handling)
  - [x] Subtask 6.2: Valider déterminisme du hachage cross-node

- [x] Task 7: Intégration préalable (AC: #5, #6)
  - [x] Subtask 7.1: Préparer une UseCase `InsertDhtEntryUseCase` qui appelle `DhtRepository` (pour réutilisation par Story 5.5 Receive Block)
  - [x] Subtask 7.2: Préparer une UseCase `LookupBlockLocationUseCase` qui répond aux requêtes `LOOKUP` (pour réutilisation par Story 6.1)

## Change Log

- **2026-04-18** : Story 4.1 Complètement implémentée
  - Architecture DHT locale avec Room persistence
  - Algorithme consistent hash ring (SHA-256)
  - Requêtes CRUD + Flow observability
  - Tests unitaires complets (16 tests)
  - Use cases préparés pour Stories futures (4.2, 5.5, 6.1)
  - Corrections dépendances Hilt (@ApplicationScope)

## Dev Notes

### Architecture Points

**Clean Architecture Separation:**
- `domain/` : Modèles purs Kotlin (`DhtEntry`, `DhtRepository`), algorithme de hachage consistant.
- `data/` : Implémentation Room, mappage entités, injection Hilt.
- `core/database/` : Configuration App Database.

**Key Dependencies (Future Stories):**
- **Story 4.2 (Gossip):** Consomme `DhtRepository.observeAllEntries()` pour détecter changements locaux et déclencher synchronisation.
- **Story 4.3 (CRDT):** Utilise `DhtRepository` pour appliquer les règles de résolution de conflits (LWW, tombstones).
- **Story 5.5 (Receive Block):** Appelle `InsertDhtEntryUseCase` après réception d'un bloc pour l'enregistrer dans la DHT.
- **Story 6.1 (Download):** Appelle `LookupBlockLocationUseCase` pour retrouver les nœuds détenteurs des blocs.

**Consistency Guarantees:**
- **Déterminisme du hachage :** Tous les nœuds doivent calculer la même partition pour un même `blockId`. Utiliser une fonction de hachage identique et vérifier en tests unitaires.
- **Partition invariant :** Une entrée DHT est **définitive** une fois écrite (sauf suppression explicite par `deleteByNodeId`). Pas de modification, pas de versioning.
- **Replica placement :** Un bloc est répliqué sur K nœuds, mais cette story gère UNIQUEMENT la partition locale. Les replicas seront synchronisés en Story 4.2 (Gossip).

### Project Structure Notes

**No conflicts with existing project structure.** Ajouts nouveaux :
- `data/local/entity/DhtEntryEntity.kt` (nouvelle entité Room)
- `domain/models/DhtEntry.kt` (nouveau modèle domaine)
- `domain/usecase/m05_dht_catalog/ConsistentHashRing.kt` (nouvelle logique métier pure)
- `data/repository/DhtRepositoryImpl.kt` (implémentation repository)
- `di/DhtModule.kt` (module Hilt)

**Alignment with Epic 1 foundation:** Epic 1 a établi Clean Architecture (Hilt, Room, Compose) comme socle. Cette story réutilise ces patterns identiquement.

### Code Patterns & Antipatterns

**Patterns to REPLICATE (from Story 3.4):**
- ✅ `Result<T>` pour tous les retours métier
- ✅ `StateFlow<T>` pour exposition d'état observable
- ✅ Logging via `NetworkEventRepository` pour trace DHT importante
- ✅ Tests avec `runTest` et `TestCoroutineDispatcher` (si applicable pour timers cleanup)

**Antipatterns to AVOID:**
- ❌ Statiques globaux `companion object` pour l'état DHT
- ❌ Exception levées non gérées (toujours `Result.Failure`)
- ❌ Opérations bloquantes hors `Dispatchers.IO`
- ❌ Hardcoding des paramètres de partitionnement (utiliser config injectable si besoin)

### Testing Strategy

**Unit Tests (JVM, sans émulateur):**
- 8+ tests couvrant insertions, lookups, deletions, observabilité, erreurs.
- Robolectric ou in-memory Room pour tests JVM purs.
- ✅ MUST PASS avant dev-story completion.

**Integration Tests (optionnel, post-dev):**
- Émulateur Android pour valider création table Room.

## Dev Agent Record

### Agent Model Used

Claude Haiku 4.5-20251001

### Debug Log References

*(Agent logs will be populated during dev-story execution)*

### Completion Notes

✅ **Implémentation complète de la persistance DHT locale**

**Modèles & Architecture :**
- Créé `DhtEntry` domain model avec serialization support
- Créé `DhtRepository` interface avec méthodes pures (Result<T>)
- Créé `DhtEntryEntity` Room entity avec indexation sur blockId et nodeId
- Ajouté `DhtEntryEntity` à `CatalogDatabase` v4 avec migration

**Hachage Consistant :**
- Implémenté `ConsistentHashRing` classe pure Kotlin
- Utilise SHA-256 pour déterminisme cross-node
- Partition = hash(blockId) mod N (N = nombre de nœuds qualifiés)
- Testé : déterminisme, distribution uniforme, cross-node validation

**Persistance & Requêtes :**
- Créé `DhtDao` avec insert, find, delete, observeAllEntries
- Implémenté `DhtRepositoryImpl` avec mappage Entity ↔ Domain
- Wrapping Result<T> pour gestion d'erreurs cohérente

**Injection & Module :**
- Créé `DhtModule.kt` pour DhtDao et DhtRepository binding
- Correctif : ajouté @ColumnInfo(name = "node_id") à PeerNodeEntity
- Correctif : ajouté @ApplicationScope aux dépendances CoroutineScope

**Tests :**
- 7 tests `ConsistentHashRingTest` : déterminisme, range, distribution, cross-node
- 9 tests `DhtRepositoryImplTest` : insert, find, delete, flow, error handling
- Tests complets couvrant tous les ACs

**Use Cases de réutilisation :**
- `InsertDhtEntryUseCase` → pour Story 5.5 (Receive Block)
- `LookupBlockLocationUseCase` → pour Story 6.1 (Download)

**Fichiers modifiés/créés :**
- 9 nouveaux fichiers créés
- 2 fichiers existants corrigés (PeerNodeEntity, DiagnosticsRepositoryImpl, P2PModule)
- Database version incrémentée 3 → 4

## File List

**NEW FILES:**
- `app/src/main/kotlin/com/mobicloud/domain/models/DhtEntry.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/DhtRepository.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/ConsistentHashRing.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/DhtEntryEntity.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/dao/DhtDao.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/DhtRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/DhtModule.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/LookupBlockLocationUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/ConsistentHashRingTest.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/DhtRepositoryImplTest.kt`

**MODIFIED FILES:**
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt` (ajouté DhtEntryEntity, v3→v4, MIGRATION_3_4)
- `app/src/main/kotlin/com/mobicloud/data/local/entity/PeerNodeEntity.kt` (ajouté @ColumnInfo pour nodeId)
- `app/src/main/kotlin/com/mobicloud/data/repository/DiagnosticsRepositoryImpl.kt` (ajouté @ApplicationScope)
- `app/src/main/kotlin/com/mobicloud/di/P2PModule.kt` (ajouté @ApplicationScope à CoroutineScope)
