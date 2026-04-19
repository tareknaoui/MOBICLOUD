# Story 4.2: Protocole Gossip Épidémique avec Filtres de Bloom

Status: done

## Story

En tant que nœud MobiCloud,
Je veux synchroniser ma partition DHT avec mes voisins via des échanges Gossip légers (Filtres de Bloom),
Afin que tous les nœuds convergent vers une vue cohérente du catalogue sans échanger de catalogues bruts.

## Acceptance Criteria

1. **Given** deux nœuds voisins sont actifs dans le cluster
2. **When** le cycle Gossip s'exécute (toutes les 2 secondes)
3. **Then** chaque nœud envoie un `BloomFilterGossip` Protobuf contenant son Filtre de Bloom (représentation probabiliste de sa partition DHT)
4. **And** le nœud récepteur calcule les éléments potentiellement manquants (`diff`) en comparant les Filtres de Bloom reçus
5. **And** si un delta est détecté, une requête `DELTA_SYNC` est émise pour ne récupérer que les entrées manquantes
6. **And** la convergence est atteinte en ≤ 3 secondes après une mise à jour de bloc (NFR-01)
7. **And** le Gossip est circulaire : chaque nœud sélectionne aléatoirement 2 voisins par cycle (fan-out = 2)
8. **And** la logique est dans `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt`

---

## Dev Agent Guardrails & Context

### Technical Requirements

#### Bloom Filter Implementation

Créer `domain/usecase/m03_m04_gossip_heartbeat/BloomFilter.kt` — classe pure Kotlin, zéro import Android :

```kotlin
class BloomFilter(val bitArraySize: Int = 1024, val numHashFunctions: Int = 3) {
    private val bitArray = BitSet(bitArraySize)

    fun add(element: String) { /* k hash functions via SHA-256 seeds */ }
    fun mightContain(element: String): Boolean { /* return true si tous les bits sont à 1 */ }
    fun toByteArray(): ByteArray { /* sérialisation pour envoi Protobuf */ }

    companion object {
        fun fromByteArray(bytes: ByteArray, size: Int = 1024, k: Int = 3): BloomFilter
    }
}
```

- Utiliser `MessageDigest.getInstance("SHA-256")` pour les k fonctions de hachage (seed via `(index.toString() + element).toByteArray()`).
- Exécuter le calcul du Bloom filter sur `Dispatchers.Default` (CPU-bound).
- Taille recommandée : 1024 bits / 3 fonctions → faux-positif < 1% pour 100 entrées DHT.

#### Messages Gossip (kotlinx.serialization)

Créer `domain/models/gossip/` avec les modèles suivants :

```kotlin
@Serializable
data class BloomFilterGossip(
    val senderNodeId: String,
    val bloomFilterBytes: ByteArray,       // Bloom filter sérialisé
    val bloomFilterSize: Int = 1024,
    val numHashFunctions: Int = 3,
    val partitionIds: List<String>,        // partitions DHT annoncées par l'émetteur
    val timestamp: Long
)

@Serializable
data class DeltaSyncRequest(
    val requesterNodeId: String,
    val missingBlockIds: List<String>,     // blockIds potentiellement manquants (faux-positifs possibles)
    val timestamp: Long
)

@Serializable
data class DeltaSyncResponse(
    val responderNodeId: String,
    val entries: List<DhtEntryDto>,        // entrées DHT complètes demandées
    val timestamp: Long
)

@Serializable
data class DhtEntryDto(
    val blockId: String,
    val nodeId: String,
    val ipAddress: String,
    val port: Int,
    val timestamp: Long
)
```

- Placer ces classes dans `domain/models/gossip/` (pas dans `data/`).
- Utiliser `@Serializable` de `kotlinx.serialization` avec `ignoreUnknownKeys = true` dans le décodeur.
- `ByteArray` → sérialiser en Base64 via `@Serializable` custom ou encoder en hex string avant sérialisation.

#### Canal Réseau Gossip

Créer `data/p2p/tcp/GossipChannel.kt` — responsable de l'envoi/réception des messages Gossip via TCP :

```kotlin
class GossipChannel @Inject constructor(
    private val protoBufSerializer: ProtoBufSerializer   // existant dans core/format/
) {
    suspend fun sendBloomGossip(targetIp: String, targetPort: Int, msg: BloomFilterGossip): Result<Unit>
    suspend fun sendDeltaSyncRequest(targetIp: String, targetPort: Int, req: DeltaSyncRequest): Result<DeltaSyncResponse>
}
```

- Utiliser un socket TCP de courte durée (connect, send, receive, close) sur `Dispatchers.IO`.
- **NE PAS réutiliser** `TcpConnectionManager` — ce composant est dédié aux handshakes d'identité, pas aux échanges de données. Créer un canal dédié Gossip.
- Timeout de connexion : 3 secondes maximum (pour respecter NFR-01 convergence ≤ 3s).
- Port Gossip : utiliser le même port TCP que le serveur P2P existant, avec un byte discriminant en tête de message (ex: `GOSSIP_BLOOM = 0x01`, `GOSSIP_DELTA_REQ = 0x02`, `GOSSIP_DELTA_RESP = 0x03`).

#### GossipSyncUseCase

Créer `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` :

```kotlin
class GossipSyncUseCase @Inject constructor(
    private val dhtRepository: DhtRepository,         // existant Story 4.1
    private val peerRepository: PeerRepository,       // existant Story 2.x
    private val consistentHashRing: ConsistentHashRing, // existant Story 4.1
    private val gossipChannel: GossipChannel,         // nouveau Story 4.2
    private val networkEventRepository: NetworkEventRepository // pour logs RadarConsole
) {
    suspend fun runGossipCycle(): Result<Unit>
    private fun selectRandomNeighbors(peers: List<Peer>, count: Int = 2): List<Peer>
    private suspend fun buildLocalBloomFilter(entries: List<DhtEntry>): BloomFilter
    private suspend fun computeDelta(localEntries: List<DhtEntry>, remoteBloom: BloomFilter): List<String>
}
```

**Algorithme du cycle Gossip (à implémenter dans `runGossipCycle()`) :**

1. Charger toutes les entrées DHT locales via `dhtRepository.observeAllEntries()` (prendre `.first()`).
2. Construire le Bloom filter local : ajouter chaque `entry.blockId` au filtre.
3. Sélectionner 2 pairs aléatoires parmi `peerRepository.peers.value` dont le statut est actif.
4. **Guard ConsistentHashRing N=0 :** si `peerRepository.peers.value.isEmpty()`, retourner `Result.success(Unit)` sans erreur (cluster isolé).
5. Envoyer `BloomFilterGossip` à chaque pair sélectionné via `gossipChannel.sendBloomGossip()`.
6. À réception d'un `BloomFilterGossip` distant, appeler `handleIncomingBloom()` :
   a. Reconstruire le Bloom filter distant depuis `bloomFilterBytes`.
   b. Pour chaque entrée locale, vérifier si `remoteBloom.mightContain(entry.blockId)`.
   c. Les `blockId` **non présents** dans le filtre distant → liste `potentiallyMissing`.
   d. Si `potentiallyMissing` non vide → envoyer `DeltaSyncRequest(missingBlockIds = potentiallyMissing)`.
7. À réception d'un `DeltaSyncResponse` → insérer chaque `DhtEntryDto` via `dhtRepository.insertEntry()` (InsertDhtEntryUseCase existant).
8. Logger les événements importants dans `NetworkEventRepository` (convergence atteinte, delta reçu, erreur TCP).

#### Intégration dans le Foreground Service

Dans `MobicloudP2PService.kt` — ajouter le démarrage du cycle Gossip périodique :

```kotlin
// Dans onStartCommand() ou la coroutine principale du service :
@ApplicationScope coroutineScope.launch {
    while (isActive) {
        gossipSyncUseCase.runGossipCycle()
        delay(2000L)  // cycle toutes les 2 secondes (AC#2)
    }
}
```

- Injecter `GossipSyncUseCase` dans `MobicloudP2PService` via `@Inject`.
- Le cycle tourne sur `Dispatchers.Default` pour le calcul Bloom, `Dispatchers.IO` pour les sockets (géré intérieurement).
- Si `runGossipCycle()` retourne `Result.failure`, logger l'erreur et continuer (pas de crash du service).

#### Réception des messages Gossip entrants

Étendre le serveur TCP existant (`TcpConnectionManager` ou un nouveau `GossipServer`) pour dispatcher les messages entrants selon le byte discriminant :

- `0x01 (GOSSIP_BLOOM)` → appeler `gossipSyncUseCase.handleIncomingBloom(msg, senderIp, senderPort)`
- `0x02 (GOSSIP_DELTA_REQ)` → appeler `gossipSyncUseCase.handleDeltaRequest(req)` → retourner `DeltaSyncResponse`
- Autres bytes → ignorer ou passer au handler handshake existant.

**Attention :** ne pas casser le handshake TCP existant (`TcpConnectionManager.handleIncomingConnection()`). Le discriminant doit être le premier byte lu — si c'est un handshake legacy (pas de discriminant connu), fallback vers le handler actuel.

---

### Architecture Compliance

**Emplacement des fichiers :**

| Fichier | Couche |
|---|---|
| `domain/models/gossip/BloomFilterGossip.kt` | Domain |
| `domain/models/gossip/DeltaSyncRequest.kt` | Domain |
| `domain/models/gossip/DeltaSyncResponse.kt` | Domain |
| `domain/models/gossip/DhtEntryDto.kt` | Domain |
| `domain/usecase/m03_m04_gossip_heartbeat/BloomFilter.kt` | Domain |
| `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` | Domain |
| `data/p2p/tcp/GossipChannel.kt` | Data |
| `di/GossipModule.kt` | DI |

**Règles strictes :**
- ❌ Aucun import Android dans `domain/` (pure Kotlin uniquement).
- ❌ Ne pas toucher à `TcpConnectionManager.handleIncomingConnection()` (handshake identité existant).
- ✅ `GossipSyncUseCase` ne connaît que les interfaces `DhtRepository`, `PeerRepository` — jamais `DhtRepositoryImpl` directement.
- ✅ `Result<T>` pour toutes les méthodes `suspend` publiques.
- ✅ `ignoreUnknownKeys = true` dans le décodeur Protobuf/JSON pour forward-compatibility.

**Module Hilt `GossipModule.kt` :**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object GossipModule {
    @Provides @Singleton
    fun provideGossipChannel(serializer: ProtoBufSerializer): GossipChannel = GossipChannel(serializer)

    @Provides @Singleton
    fun provideBloomFilter(): BloomFilter = BloomFilter(bitArraySize = 1024, numHashFunctions = 3)
}
```

---

### Previous Story Intelligence (Story 4.1)

**Ce qui existe DÉJÀ — NE PAS recréer :**
- `domain/repository/DhtRepository.kt` — interface avec `insertEntry`, `findByBlockId`, `findByNodeId`, `deleteByNodeId`, `observeAllEntries()`.
- `domain/usecase/m05_dht_catalog/ConsistentHashRing.kt` — hachage consistant SHA-256, méthode `getPartition(blockId, numNodes)`.
- `domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt` — use case pour insérer les entrées reçues.
- `data/local/dao/DhtDao.kt` + `data/local/entity/DhtEntryEntity.kt` — persistance Room.
- `data/repository/DhtRepositoryImpl.kt` — implémentation avec mapping Entity ↔ Domain.
- `di/DhtModule.kt` — binding Hilt pour DhtRepository.
- `core/format/ProtoBufSerializer.kt` — sérialiseur Protobuf existant (`ignoreUnknownKeys=true`).
- `data/p2p/tcp/TcpConnectionManager.kt` — handshakes TCP (ne PAS modifier son comportement actuel).

**Learnings critiques de 4.1 :**
- **P6 (BUG OUVERT) — ConsistentHashRing N=0 :** Division par zéro si aucun pair connu. **AJOUTER un guard `if (numNodes == 0) return Result.success(Unit)` au début de `runGossipCycle()`** avant d'utiliser `ConsistentHashRing`. Ce bug est connu et non corrigé dans 4.1.
- **P7 (Race Condition) :** Firebase annonce le port TCP avant que le serveur soit prêt. Pour le Gossip, vérifier que `TcpConnectionManager` est démarré avant de lancer le cycle Gossip (utiliser un `StateFlow<ServiceStatus>` ou attendre le port disponible).
- **Patterns à répliquer :** `Result<T>` pour tous les retours, `StateFlow` pour l'état observable, logging via `NetworkEventRepository`.

**Fichiers modifiés par 4.1 (vérifier avant de modifier) :**
- `data/local/CatalogDatabase.kt` — version 4, entité `DhtEntryEntity` déjà déclarée. **NE PAS incrémenter la version DB si aucun schéma n'est ajouté dans cette story.**
- `di/P2PModule.kt` — `@ApplicationScope` ajouté pour `CoroutineScope`.

---

### Testing Requirements

**Tests unitaires requis (JVM, sans émulateur) :**

**`BloomFilterTest.kt`** dans `test/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/` :
- Test 1 : `add(blockId)` puis `mightContain(blockId)` → `true` (pas de faux-négatifs).
- Test 2 : `mightContain("element-jamais-ajoute")` → `false` (ou rarement `true` = faux-positif acceptable).
- Test 3 : `toByteArray()` puis `fromByteArray()` → filtre reconstruit identique (déterminisme de sérialisation).
- Test 4 : 100 éléments ajoutés → taux faux-positifs < 1% sur 1000 éléments aléatoires.

**`GossipSyncUseCaseTest.kt`** :
- Test 5 : `runGossipCycle()` avec 0 pairs → `Result.success(Unit)` (guard N=0).
- Test 6 : `runGossipCycle()` avec 3 pairs → exactement 2 pairs sélectionnés (fan-out = 2).
- Test 7 : `handleIncomingBloom()` avec Bloom distant vide → `potentiallyMissing` = toutes les entrées locales.
- Test 8 : `handleIncomingBloom()` avec Bloom distant contenant toutes les entrées locales → aucun `DELTA_SYNC` envoyé.
- Test 9 : `handleDeltaRequest()` → `DeltaSyncResponse` contient les entrées DHT demandées.
- Test 10 : `runGossipCycle()` → résultat `Result.failure` si `gossipChannel.sendBloomGossip()` échoue (test que le cycle continue sans crash).

**Mocking :** Utiliser MockK pour mocker `DhtRepository`, `PeerRepository`, `GossipChannel`. Pas d'émulateur nécessaire.

---

### NFR Compliance

**NFR-01 (Convergence ≤ 3 secondes) :**
- Cycle 2s + timeout TCP 3s = convergence typique < 3s pour 1 delta.
- **NE PAS mettre de delay artificiel > 500ms** dans les chemins de traitement Bloom.
- Le test de convergence peut être validé manuellement avec 2 appareils ou 2 instances.

**NFR-03 (Overhead CPU ≤ 5%) :**
- Bloom filter sur 100 entrées DHT = calcul négligeable.
- **NE PAS appeler `observeAllEntries()` en continu** — utiliser `.first()` par cycle pour éviter les collect permanents.
- `delay(2000L)` entre les cycles assure un overhead CPU < 1%.

---

## Tasks / Subtasks

- [x] Task 1: Créer les modèles de messages Gossip (AC: #3, #5)
  - [x] Subtask 1.1: Créer `domain/models/gossip/BloomFilterGossip.kt`
  - [x] Subtask 1.2: Créer `domain/models/gossip/DeltaSyncRequest.kt`
  - [x] Subtask 1.3: Créer `domain/models/gossip/DeltaSyncResponse.kt`
  - [x] Subtask 1.4: Créer `domain/models/gossip/DhtEntryDto.kt`

- [x] Task 2: Implémenter le Bloom Filter (AC: #3, #4)
  - [x] Subtask 2.1: Créer `domain/usecase/m03_m04_gossip_heartbeat/BloomFilter.kt` (pure Kotlin, SHA-256)
  - [x] Subtask 2.2: Créer `BloomFilterTest.kt` (4 tests : no false-negatives, serialization, false-positive rate)

- [x] Task 3: Créer le canal réseau Gossip (AC: #3, #5)
  - [x] Subtask 3.1: Créer `data/p2p/tcp/GossipChannel.kt` (socket TCP courte durée, timeout 3s)
  - [x] Subtask 3.2: Définir le protocole message (byte discriminant en tête)
  - [x] Subtask 3.3: Étendre le serveur TCP entrant pour dispatcher vers GossipChannel (sans casser le handshake existant)

- [x] Task 4: Implémenter GossipSyncUseCase (AC: #2, #4, #5, #6, #7, #8)
  - [x] Subtask 4.1: Créer `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt`
  - [x] Subtask 4.2: Implémenter `runGossipCycle()` avec fan-out=2, guard N=0, cycle 2s
  - [x] Subtask 4.3: Implémenter `handleIncomingBloom()` (calcul delta, déclenchement DELTA_SYNC)
  - [x] Subtask 4.4: Implémenter `handleDeltaRequest()` (retour des entrées manquantes)
  - [x] Subtask 4.5: Implémenter `handleDeltaResponse()` (insertion via InsertDhtEntryUseCase)

- [x] Task 5: Configurer l'injection Hilt (AC: #8)
  - [x] Subtask 5.1: Créer `di/GossipModule.kt`
  - [x] Subtask 5.2: Injecter `GossipSyncUseCase` dans `MobicloudP2PService`

- [x] Task 6: Intégrer dans le Foreground Service (AC: #2, #6)
  - [x] Subtask 6.1: Ajouter la coroutine de cycle Gossip (delay 2000ms) dans `MobicloudP2PService`
  - [x] Subtask 6.2: S'assurer que le cycle démarre APRÈS que le serveur TCP soit prêt (guard P7)

- [x] Task 7: Écrire les tests unitaires (All ACs)
  - [x] Subtask 7.1: Créer `GossipSyncUseCaseTest.kt` (6 tests : fan-out, guard N=0, delta calcul, no-delta, delta-request, failure propagation)

### Review Findings

- [x] [Review][Patch] F1 [CRITICAL] `runBlocking` dans `onBloomGossipReceived`/`onDeltaSyncRequestReceived` bloque le thread accept TCP — risque de DoS et deadlock `[GossipSyncUseCase.kt:70-79]`
- [x] [Review][Patch] F2 [CRITICAL] Allocation `ByteArray(len)` non bornée — OOM/crash via pair malveillant envoyant un `len=2GB` `[TcpConnectionManager.kt:handleIncomingBloomGossip/handleIncomingDeltaRequest]`
- [x] [Review][Patch] F3 [CRITICAL] `return@withContext Result.failure(e)` dans la boucle fan-out — le cycle Gossip s'arrête au premier pair défaillant (fan-out effectif = 1) `[GossipSyncUseCase.kt:58-61]`
- [x] [Review][Patch] F4 [CRITICAL] Violation d'architecture : `GossipSyncUseCase` (domain) importe `GossipChannel` et `TcpConnectionManager` (data) et implémente `TcpConnectionManager.GossipIncomingHandler` `[GossipSyncUseCase.kt:imports+class header]`
- [x] [Review][Patch] F5 [CRITICAL] `requesterNodeId` dans `DeltaSyncRequest` est mis à `msg.senderNodeId` (l'ID du pair distant) au lieu de l'ID du nœud local `[GossipSyncUseCase.kt:handleIncomingBloom:99-103]`
- [x] [Review][Patch] F6 [HIGH] `senderPort` passé à `handleIncomingBloom` est le port éphémère client (ex: 54321) et non le port serveur du pair — `sendDeltaSyncRequest` échoue systématiquement `[TcpConnectionManager.kt:91 + GossipSyncUseCase.kt:104]`
- [x] [Review][Patch] F7 [HIGH] `gossipHandler` est un `var` sans `@Volatile` — race condition JVM entre le thread service (write) et le thread TCP accept (read) `[TcpConnectionManager.kt:37]`
- [x] [Review][Patch] F8 [HIGH] Le singleton `BloomFilter` fourni par `GossipModule` n'est pas injecté dans `GossipSyncUseCase` — dead code trompeur (une nouvelle instance est créée par cycle) `[GossipModule.kt:18 + GossipSyncUseCase.kt:164]`
- [x] [Review][Patch] F9 [HIGH] `localNodeId` résolu depuis `peerRepository.peers.value.firstOrNull()` (liste des pairs distants) — retourne l'ID d'un pair aléatoire, pas l'ID du nœud local `[GossipSyncUseCase.kt:43,566]`
- [x] [Review][Patch] F10 [MEDIUM] `MessageDigest.getInstance("SHA-256")` instancié à chaque appel de `hashIndex` — overhead CPU inutile sur le hot-path 2s `[BloomFilter.kt:hashIndex]`
- [x] [Review][Patch] F11 [MEDIUM] Aucun read timeout sur les sockets TCP — un pair lent peut bloquer le thread indéfiniment `[GossipChannel.kt + TcpConnectionManager.kt]`
- [x] [Review][Patch] F12 [LOW] Paramètre `socket: Socket` inutilisé dans `handleIncomingBloomGossip` — signature trompeuse `[TcpConnectionManager.kt:handleIncomingBloomGossip]`
- [x] [Review][Defer] F13 [LOW] `observeAllEntries().first()` sans timeout peut se bloquer sous charge d'écriture intense `[GossipSyncUseCase.kt:38,92]` — deferred, sera résolu avec F1
- [x] [Review][Defer] F14 [LOW] Timestamp des entrées DHT reçues (`DhtEntryDto.timestamp`) silencieusement ignoré lors de l'insertion — `DhtRepository.insertEntry` ne prend pas de timestamp `[GossipSyncUseCase.kt:handleDeltaResponse]` — deferred, nécessite changement d'interface pré-existant
- [x] [Review][Defer] F15 [LOW] Entrées DHT insérées sans validation d'authenticité — risque de DHT poisoning par tout participant `[GossipSyncUseCase.kt:handleDeltaResponse]` — deferred, hors scope story (durcissement sécurité systémique)

## Change Log

- Story 4.2 implémentée (2026-04-19) : Protocole Gossip épidémique complet avec Filtres de Bloom, canal TCP dédié, GossipSyncUseCase, et 10 tests unitaires passants.
- Correction de 4 tests pré-existants en erreur de compilation (RunBullyElectionUseCaseTest, DashboardViewModelTest, CircuitBreakerUseCaseTest, LocalRepairBufferTest).

## Dev Notes

- **ByteArray dans Protobuf** : `@Serializable data class BloomFilterGossip(val bloomFilterBytes: ByteArray, ...)` — ByteArray est sérialisé nativement par kotlinx-serialization-protobuf, pas besoin de custom serializer.
- **Protocole discriminant** : premier byte = `0x01` (BLOOM), `0x02` (DELTA_REQ), `0x03` (DELTA_RESP). Le handshake legacy (ObjectInputStream) démarre par `0xAC 0xED` (magic Java sérialisation) — pas de conflit.
- **Guard P7 (Race Condition)** : `tcpConnectionManager.gossipHandler = gossipSyncUseCase` est assigné APRÈS `tcpConnectionManager.startServer()` pour éviter les appels Gossip sur un serveur non prêt.
- **Faux-positifs** : avec 1024 bits / 3 fonctions / 100 éléments, le taux théorique est ~1.6%. Le test de performance utilise 2048 bits pour fiabilité < 1% (spec story mathématiquement incorrecte pour 1024/3/100).
- **12 tests pré-existants en échec** (stories 3.x/2.x, non liées à cette story) : PeerRepositoryImplTest (2), CircuitBreakerUseCaseTest (5), LocalRepairBufferTest (5) — problèmes de coroutines non annulées dans le setup.

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Completion Notes

- **BloomFilter.kt** : classe pure Kotlin, SHA-256, BitSet 1024 bits, 3 fonctions de hachage. Sérialisation deterministe via `toByteArray()` / `fromByteArray()`. Zéro import Android.
- **Modèles Gossip** : 4 data classes `@Serializable` dans `domain/models/gossip/` — BloomFilterGossip (avec equals/hashCode custom pour ByteArray), DeltaSyncRequest, DeltaSyncResponse, DhtEntryDto.
- **GossipChannel** : socket TCP courte durée (connect/send/receive/close), timeout 3s, `Dispatchers.IO`. Envoie BLOOM puis reçoit DELTA_RESP en une seule connexion pour `sendDeltaSyncRequest`.
- **TcpConnectionManager** : étendu avec `GossipIncomingHandler` interface + `PushbackInputStream` pour dispatcher sans casser le handshake legacy. `gossipHandler` est une propriété nullable définie depuis `MobicloudP2PService`.
- **GossipSyncUseCase** : implémente `GossipIncomingHandler`, fan-out=2, guard N=0, toutes les méthodes retournent `Result<T>`, zéro import Android.
- **MobicloudP2PService** : cycle Gossip lancé sur `serviceScope.launch` après `startServer()`, `delay(2000L)`, erreurs logguées sans crash.
- **Tests** : BloomFilterTest (4/4 ✅), GossipSyncUseCaseTest (6/6 ✅). 102 tests compilés et exécutés, 90 passent, 12 échecs pré-existants (stories 3.x/2.x non liées à cette story).

## File List

**NEW FILES (à créer) :**
- `app/src/main/kotlin/com/mobicloud/domain/models/gossip/BloomFilterGossip.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/gossip/DeltaSyncRequest.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/gossip/DeltaSyncResponse.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/gossip/DhtEntryDto.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/BloomFilter.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/GossipChannel.kt`
- `app/src/main/kotlin/com/mobicloud/di/GossipModule.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/BloomFilterTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCaseTest.kt`

**MODIFIED FILES (à modifier avec précaution) :**
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (ajout cycle Gossip — NE PAS modifier les logiques Firebase/TCP existantes)
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt` (dispatcher byte discriminant — NE PAS modifier `handleIncomingConnection()` legacy)
