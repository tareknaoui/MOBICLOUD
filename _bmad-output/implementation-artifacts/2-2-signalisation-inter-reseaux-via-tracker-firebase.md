# Story 2.2 : Signalisation Inter-Réseaux via Tracker Firebase

Status: in-progress

## Story

En tant que nœud MobiCloud sur réseau 4G,
Je veux m'enregistrer auprès du Tracker Firebase et découvrir les Super-Pairs d'autres clusters,
Afin de rejoindre la fédération MobiCloud même sans réseau local commun.

## Acceptance Criteria

1. **Given** le nœud est sur un réseau 4G (pas de Multicast UDP disponible)
   **When** le service démarre et détecte l'absence de pairs locaux après 10 secondes
   **Then** le nœud s'enregistre sur Firebase Realtime Database avec ses métadonnées (`nodeId`, `publicKey` en Base64, `ip`, `port`, `timestamp`)

2. **And** le nœud lit la liste des nœuds inscrits sur Firebase et les ajoute à sa `PeerRepository` locale (source `REMOTE_FIREBASE`) — les pairs non-locaux sont ignorés s'ils portent le même `nodeId` que le nœud local

3. **And** les entrées Firebase âgées de plus de 60 secondes (`System.currentTimeMillis() - timestamp > 60_000`) sont **ignorées** à la lecture (filtrage TTL côté client)

4. **And** la logique Firebase est encapsulée dans `data/repository/SignalingRepositoryImpl.kt` (interface `domain/repository/SignalingRepository.kt`) — aucun import Firebase dans le domain

5. **And** si Firebase est inaccessible, le nœud reste en mode Multicast local seul (`Result.Failure` remontée proprement via `SignalingRepository`, service P2P non interrompu)

6. **And** à la déconnexion du nœud, son entrée Firebase est supprimée automatiquement via `onDisconnect().removeValue()`

## Tasks / Subtasks

- [x] Task 1 : Créer `domain/repository/SignalingRepository.kt` (AC: #4, #5)
  - [x] Définir `interface SignalingRepository` avec `suspend fun registerNode(ip: String, port: Int): Result<Unit>` et `fun observeRemoteNodes(): Flow<List<Peer>>`
  - [x] Aucun import Firebase — interface pure Kotlin

- [x] Task 2 : Créer `data/repository/SignalingRepositoryImpl.kt` (AC: #1, #2, #3, #4, #5, #6)
  - [x] Implémenter `registerNode()` : écriture sous `nodes/{nodeId}` avec `nodeId`, `publicKeyBase64`, `ip`, `port`, `timestamp = System.currentTimeMillis()`
  - [x] Ajouter `onDisconnect().removeValue()` juste après le `setValue()` pour cleanup automatique
  - [x] Implémenter `observeRemoteNodes()` : `callbackFlow` + `ValueEventListener` sur `nodes/`
  - [x] Dans `onDataChange()` : filtrer les entrées avec `timestamp` absent ou `System.currentTimeMillis() - timestamp > 60_000L`
  - [x] Exclure le nœud local de la liste retournée (comparer `nodeId`)
  - [x] Construire `Peer(identity, timestampMs, source = REMOTE_FIREBASE, ipAddress, port)` par entrée valide
  - [x] Wrapper `registerNode()` dans `runCatching { }`, retourner `Result.failure(e)` si erreur Firebase

- [x] Task 3 : Créer `di/SignalingModule.kt` (AC: #4)
  - [x] Module Hilt `@InstallIn(SingletonComponent::class)` avec `@Binds @Singleton` qui lie `SignalingRepository` → `SignalingRepositoryImpl`
  - [x] `SignalingRepositoryImpl` doit recevoir `FirebaseDatabase` (déjà fourni par `FirebaseModule.kt`) et `SecurityRepository` via `@Inject` constructor

- [x] Task 4 : Mettre à jour `MobicloudP2PService` — remplacer `BootstrapRepository` par `SignalingRepository` (AC: #1, #2, #5)
  - [x] Remplacer `@Inject lateinit var bootstrapRepository: BootstrapRepository` par `@Inject lateinit var signalingRepository: SignalingRepository`
  - [x] Firebase announce : attendre 10s (`delay(10_000L)`) puis appeler `signalingRepository.registerNode()`
  - [x] Firebase discovery : remplacer `bootstrapRepository.observeActivePeers()` par `signalingRepository.observeRemoteNodes()`
  - [x] Conserver la logique existante de handshake TCP sur les pairs Firebase découverts
  - [x] Conserver `withTimeout(FIREBASE_ANNOUNCE_TIMEOUT_MS)` autour de `registerNode()`

- [x] Task 5 : Tests unitaires (AC: #2, #3, #5)
  - [x] Créer `data/repository/SignalingRepositoryImplTest.kt` (JVM unit test, MockK)
  - [x] Tester : `observeRemoteNodes()` filtre les entrées avec timestamp > 60s
  - [x] Tester : `observeRemoteNodes()` exclut le nœud local
  - [x] Tester : `registerNode()` retourne `Result.failure(e)` si Firebase lève une exception
  - [x] Tester : `observeRemoteNodes()` construit correctement un `Peer` avec `source = REMOTE_FIREBASE`

## Dev Notes

> [!CAUTION] **DISASTER PREVENTION — LIRE AVANT TOUTE IMPLÉMENTATION :**
> 1. **Infrastructure existante — NE PAS réimplémenter de zéro** : `FirebaseBootstrapRepositoryImpl` dans `data/repository_impl/` et `BootstrapRepository` dans `domain/repository/` existent et fonctionnent. La story crée `SignalingRepository` comme interface canonique de remplacement — **NE PAS toucher** `FirebaseBootstrapRepositoryImpl` ni `BootstrapRepository` (pas de suppression, risque de régression).
> 2. **Chemin canonique `data/repository/`** : Le nouveau `SignalingRepositoryImpl.kt` va dans `data/repository/` (même dossier que `PeerRepositoryImpl.kt`). Pas dans `data/repository_impl/` (dette technique pré-existante).
> 3. **TTL côté client** : Firebase Realtime Database ne supporte pas les TTL natifs (pas de Cloud Functions dans le scope PFE). Le filtrage des 60s se fait **dans `onDataChange()`** du `ValueEventListener` en comparant `System.currentTimeMillis()`.
> 4. **`onDisconnect()` AVANT `setValue()`** : L'ordre des opérations est critique. Appeler `dbRef.onDisconnect().removeValue().await()` **avant** `dbRef.setValue(nodeData).await()`. Si Firebase est déconnecté avant le `setValue`, le cleanup est déjà programmé.
> 5. **`callbackFlow` + `awaitClose`** : Le `observeRemoteNodes()` DOIT utiliser `callbackFlow { ... awaitClose { reference.removeEventListener(listener) } }` pour éviter les fuites de listeners Firebase.
> 6. **Result<T> obligatoire** : Toute exception Firebase dans `registerNode()` doit être capturée via `runCatching` et retournée comme `Result.failure(e)`. Ne jamais laisser une exception Firebase remonter non gérée.
> 7. **Pas de `FirebaseDatabase` dans le domain** : `SignalingRepository.kt` (domain) ne doit contenir AUCUN import Firebase. Frontière architecturale stricte — le domain ne connaît que des interfaces pures.
> 8. **`FirebaseDatabase` déjà provisionné** : `FirebaseModule.kt` (`di/FirebaseModule.kt`) fournit déjà `@Provides @Singleton fun provideFirebaseDatabase(): FirebaseDatabase`. `SignalingRepositoryImpl` peut injecter `FirebaseDatabase` directement sans créer un nouveau module pour ce provider.
> 9. **identité locale dans `SignalingRepositoryImpl`** : `registerNode()` a besoin de `nodeId` et `publicKeyBytes`. Ces données sont dans `SecurityRepository.getIdentity()`. Injecter `SecurityRepository` dans `SignalingRepositoryImpl` (pas `IdentityRepository` — `SecurityRepository` est l'interface qui expose `generateIdentity()`/`getIdentity()`).
> 10. **Délai 10s avant Firebase** : Le Service DOIT attendre 10 secondes avant d'appeler `signalingRepository.registerNode()` ET vérifier que `peerRepository.peers.value.none { it.isActive }`. Si des pairs locaux sont déjà détectés, l'annonce Firebase est optionnelle mais ne nuit pas (double annonce OK pour la fédération).

### Infrastructure Existante (Inventaire Précis)

| Fichier | Statut | Action Requise |
|---|---|---|
| `domain/repository/BootstrapRepository.kt` | ✅ Ne pas toucher | Conserver tel quel (legacy) |
| `data/repository_impl/FirebaseBootstrapRepositoryImpl.kt` | ✅ Ne pas toucher | Conserver tel quel (legacy) |
| `di/FirebaseModule.kt` | ✅ Fournit `FirebaseDatabase` | Ajouter binding `SignalingRepository` OU créer `di/SignalingModule.kt` séparé |
| `data/network/service/MobicloudP2PService.kt` | ⚠️ Injecte `BootstrapRepository` | Remplacer par `SignalingRepository` |
| `domain/repository/SecurityRepository.kt` | ✅ Expose `getIdentity()` | Injecter dans `SignalingRepositoryImpl` |
| `domain/models/Peer.kt` | ✅ Champ `isActive`, `source: DiscoverySource` | Réutiliser tel quel |
| `domain/models/DiscoverySource.kt` | ✅ `LOCAL_UDP`, `REMOTE_FIREBASE` | Réutiliser `REMOTE_FIREBASE` |

### Fichiers à Créer / Modifier

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   └── repository/
│       └── SignalingRepository.kt           ← NOUVEAU (interface pure Kotlin)
├── data/
│   └── repository/
│       └── SignalingRepositoryImpl.kt       ← NOUVEAU (Firebase bridge, chemin canonique)
└── di/
    ├── SignalingModule.kt                   ← NOUVEAU (Hilt binding)
    └── (FirebaseModule.kt déjà existant — NE PAS modifier si SignalingModule créé séparé)

app/src/main/kotlin/com/mobicloud/data/network/service/
└── MobicloudP2PService.kt                  ← MODIFIER (BootstrapRepository → SignalingRepository)

app/src/test/kotlin/com/mobicloud/data/repository/
└── SignalingRepositoryImplTest.kt          ← NOUVEAU (JVM unit test, MockK)
```

### Détail : `SignalingRepository.kt`

```kotlin
// domain/repository/SignalingRepository.kt
package com.mobicloud.domain.repository

import com.mobicloud.domain.models.Peer
import kotlinx.coroutines.flow.Flow

interface SignalingRepository {
    /** Enregistre le nœud local sur Firebase. Retourne Result.failure si inaccessible. */
    suspend fun registerNode(ip: String, port: Int): Result<Unit>

    /** Observe les nœuds distants sur Firebase (TTL 60s filtré, nœud local exclu). */
    fun observeRemoteNodes(): Flow<List<Peer>>
}
```

### Détail : `SignalingRepositoryImpl.kt`

```kotlin
// data/repository/SignalingRepositoryImpl.kt
package com.mobicloud.data.repository

import android.util.Base64
import com.google.firebase.database.*
import com.mobicloud.domain.models.*
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.SignalingRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val NODES_PATH = "nodes"
private const val TTL_MS = 60_000L

class SignalingRepositoryImpl @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val firebaseDatabase: FirebaseDatabase
) : SignalingRepository {

    override suspend fun registerNode(ip: String, port: Int): Result<Unit> = runCatching {
        val identity = securityRepository.getIdentity().getOrThrow()
        val ref = firebaseDatabase.reference.child(NODES_PATH).child(identity.nodeId)

        val nodeData = mapOf(
            "nodeId"        to identity.nodeId,
            "publicKeyBase64" to Base64.encodeToString(identity.publicKeyBytes, Base64.NO_WRAP),
            "ip"            to ip,
            "port"          to port,
            "timestamp"     to System.currentTimeMillis()
        )

        // onDisconnect() AVANT setValue() — cleanup automatique à la déconnexion
        ref.onDisconnect().removeValue().await()
        ref.setValue(nodeData).await()
    }

    override fun observeRemoteNodes(): Flow<List<Peer>> = callbackFlow {
        val localNodeId = securityRepository.getIdentity().getOrNull()?.nodeId
        val reference = firebaseDatabase.reference.child(NODES_PATH)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val peers = snapshot.children.mapNotNull { child ->
                    try {
                        val nodeId    = child.child("nodeId").getValue(String::class.java) ?: return@mapNotNull null
                        if (nodeId == localNodeId) return@mapNotNull null
                        val pubKeyB64 = child.child("publicKeyBase64").getValue(String::class.java) ?: return@mapNotNull null
                        val ip        = child.child("ip").getValue(String::class.java) ?: return@mapNotNull null
                        val port      = child.child("port").getValue(Int::class.java) ?: return@mapNotNull null
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: return@mapNotNull null

                        if (now - timestamp > TTL_MS) return@mapNotNull null  // TTL 60s

                        val publicKeyBytes = Base64.decode(pubKeyB64, Base64.NO_WRAP)
                        Peer(
                            identity = NodeIdentity(nodeId, publicKeyBytes),
                            lastSeenTimestampMs = timestamp,
                            source = DiscoverySource.REMOTE_FIREBASE,
                            ipAddress = ip,
                            port = port
                        )
                    } catch (e: Exception) { null }
                }
                trySend(peers)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        reference.addValueEventListener(listener)
        awaitClose { reference.removeEventListener(listener) }
    }
}
```

### Détail : `SignalingModule.kt`

```kotlin
// di/SignalingModule.kt
package com.mobicloud.di

import com.mobicloud.data.repository.SignalingRepositoryImpl
import com.mobicloud.domain.repository.SignalingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SignalingModule {
    @Binds
    @Singleton
    abstract fun bindSignalingRepository(
        impl: SignalingRepositoryImpl
    ): SignalingRepository
}
```

### Détail : Modifications `MobicloudP2PService`

Remplacer l'injection de `BootstrapRepository` par `SignalingRepository` et ajouter le délai 10s :

```kotlin
// AVANT :
@Inject lateinit var bootstrapRepository: BootstrapRepository

// APRÈS :
@Inject lateinit var signalingRepository: SignalingRepository
```

Remplacer le bloc Firebase announce (dans `startP2PNetworkLoops()`) :

```kotlin
// AVANT :
launch {
    val ipToAnnounce = publicIpFetcher.fetchPublicIp().getOrElse { "127.0.0.1" }
    try {
        withTimeout(FIREBASE_ANNOUNCE_TIMEOUT_MS) {
            bootstrapRepository.announcePresence(ipToAnnounce, tcpPort)
        }
    } catch (e: Exception) { ... }
}

// APRÈS :
launch {
    delay(10_000L)  // Laisser la découverte locale 10s avant Firebase
    val ipToAnnounce = publicIpFetcher.fetchPublicIp().getOrElse { "127.0.0.1" }
    try {
        withTimeout(FIREBASE_ANNOUNCE_TIMEOUT_MS) {
            signalingRepository.registerNode(ipToAnnounce, tcpPort)
                .onFailure { Log.w(TAG, "Firebase registerNode échec — mode local seul", it) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Firebase announce timeout — mode local seul", e)
    }
}
```

Remplacer le bloc Firebase discovery :

```kotlin
// AVANT :
bootstrapRepository.observeActivePeers().collectLatest { ... }

// APRÈS :
signalingRepository.observeRemoteNodes().collectLatest { peers ->
    for (peer in peers) {
        peerRepository.registerOrUpdatePeer(
            peer.identity,
            SystemClock.elapsedRealtime(),
            peer.source,
            peer.ipAddress,
            peer.port
        ).onFailure { Log.e(TAG, "Failed to register Firebase peer", it) }
        if (!tcpConnectionManager.isConnected(peer.identity.nodeId)) {
            launch { tcpConnectionManager.connectToPeer(peer) }
        }
    }
}
```

### Détail : Tests unitaires `SignalingRepositoryImplTest.kt`

```kotlin
// Test 1 : TTL filtering
@Test
fun `observeRemoteNodes filtre les entrées plus vieilles que 60s`() {
    // Mock DataSnapshot avec timestamp = System.currentTimeMillis() - 70_000L
    // Vérifier que la liste retournée est vide
}

// Test 2 : Exclusion nœud local
@Test
fun `observeRemoteNodes exclut le nœud local`() {
    // Mock DataSnapshot avec nodeId == localNodeId
    // Vérifier que la liste retournée est vide
}

// Test 3 : registerNode retourne failure sur exception Firebase
@Test
fun `registerNode retourne Result_failure si Firebase lance une exception`() {
    // Mock firebaseDatabase.reference pour lancer une exception
    // Vérifier que registerNode() retourne Result.failure
}

// Test 4 : Construction correcte d'un Peer
@Test
fun `observeRemoteNodes construit Peer avec source REMOTE_FIREBASE`() {
    // Mock DataSnapshot valide
    // Vérifier source == DiscoverySource.REMOTE_FIREBASE
}
```

### Firebase Security Rules (à vérifier)

Les règles Firebase doivent autoriser :
- Lecture de `nodes/*` : publique
- Écriture sur `nodes/{nodeId}` : restreinte au nœud propriétaire de ce `nodeId`

Si les règles Firebase actuelles sont configurées pour `active_nodes/*` (ancien chemin de `FirebaseBootstrapRepositoryImpl`), elles **ne s'appliquent pas** au nouveau chemin `nodes/*`. Vérifier / créer les règles pour le chemin `nodes/`.

### Références

- [Source: docs/_bmad-output/planning-artifacts/architecture.md#Tracker Firebase — Signalisation Minimaliste]
- [Source: docs/_bmad-output/planning-artifacts/architecture.md#Firebase Boundary]
- [Source: docs/_bmad-output/planning-artifacts/epics.md#Story 2.2]
- [Source: app/src/main/kotlin/com/mobicloud/data/repository_impl/FirebaseBootstrapRepositoryImpl.kt] — code de référence
- [Source: app/src/main/kotlin/com/mobicloud/di/FirebaseModule.kt] — provider `FirebaseDatabase` existant
- [Source: app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt] — service à modifier

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Créé `SignalingRepository.kt` (interface pure Kotlin, domain layer — zéro import Firebase)
- Créé `SignalingRepositoryImpl.kt` : `registerNode()` avec `onDisconnect().removeValue()` AVANT `setValue()` (ordre critique), `observeRemoteNodes()` avec `callbackFlow` + `awaitClose` pour éviter les fuites, filtrage TTL 60s côté client, exclusion du nœud local par nodeId
- Créé `SignalingModule.kt` : binding Hilt `@Binds @Singleton`, respecte la frontière architecturale (FirebaseModule.kt inchangé)
- Modifié `MobicloudP2PService.kt` : `BootstrapRepository` remplacé par `SignalingRepository`, ajout `delay(10_000L)` avant l'announce Firebase, `observeRemoteNodes()` remplace `observeActivePeers()`, la vérification du nœud local est désormais dans `SignalingRepositoryImpl` (supprimée du service)
- Créé `SignalingRepositoryImplTest.kt` : 4 tests JVM MockK couvrant TTL filtering, exclusion nœud local, Result.failure sur exception Firebase, et construction correcte de Peer avec `source = REMOTE_FIREBASE`

### Review Findings

- [x] [Review][Decision] AC1 — Délai 10s inconditionnel sans vérification des pairs locaux actifs → Option A appliquée : `peerRepository.peers.value.any { it.isActive }` ajouté [`MobicloudP2PService.kt:125`]
- [x] [Review][Patch] F01 — `localNodeId` null : filtre d'auto-exclusion silencieusement désactivé → fail-fast si `getIdentity()` échoue [`SignalingRepositoryImpl.kt:45-50`]
- [x] [Review][Patch] F02 — `collectLatest` + `launch{}` interne : prolifération non bornée de coroutines TCP → `connectionJobs: Map<nodeId, Job>` ajouté [`MobicloudP2PService.kt:145-157`]
- [x] [Review][Patch] F03 — `onCancelled` ferme le flow avec exception sans catch dans le service → `try/catch` autour de `collectLatest` [`MobicloudP2PService.kt:141,160`]
- [x] [Review][Patch] F06 — `Base64.decode` sans log : clé malformée ignorée silencieusement → `Log.w` ajouté dans le `catch` [`SignalingRepositoryImpl.kt:82`]
- [x] [Review][Patch] F11 — IP fallback `127.0.0.1` publiée sur Firebase → guard `ipToAnnounce == null || == "127.0.0.1"` [`MobicloudP2PService.kt:131`]
- [x] [Review][Patch] F12 — `port` Firebase : `getValue(Int::class.java)` → corrigé en `getValue(Long::class.java)?.toInt()` [`SignalingRepositoryImpl.kt:66`]
- [x] [Review][Defer] F07 — TTL dérive d'horloge : `System.currentTimeMillis()` inter-appareils non synchronisés [`SignalingRepositoryImpl.kt:50,66`] — deferred, pre-existing (limitation Firebase sans Cloud Functions, noté dans Dev Notes)
- [x] [Review][Defer] F08 — Règles de sécurité Firebase (`nodes/*`) absentes du diff — deferred, pre-existing (noté dans spec "Firebase Security Rules à vérifier")
- [x] [Review][Defer] F09 — Nœuds morts non purgés côté serveur Firebase en cas de crash — deferred, pre-existing (limitation Firebase sans Cloud Functions, scope PFE)
- [x] [Review][Defer] F10 — `await()` Firebase SDK non coopératif avec l'annulation du CoroutineScope [`MobicloudP2PService.kt:124-135`] — deferred, limitation SDK Firebase
- [x] [Review][Defer] F16 — `onDisconnect()` fenêtre nœud fantôme si `setValue()` échoue sur session précédente [`SignalingRepositoryImpl.kt:40-41`] — deferred, limitation inhérente au pattern onDisconnect Firebase
- [x] [Review][Defer] F18 — Couverture tests insuffisante : cas `localNodeId == null`, déduplication TCP, décodage clé invalide non testés — deferred, à compléter lors de la prochaine itération

### File List

**Créés :**
- `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/SignalingModule.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt`

**Modifiés :**
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
