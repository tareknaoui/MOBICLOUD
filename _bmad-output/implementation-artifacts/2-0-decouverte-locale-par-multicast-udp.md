# Story 2.0 : Découverte Locale par Multicast UDP

**Status:** done  
**Epic:** 2 — Découverte Inter-Réseaux & Dashboard Tactique  
**Story ID:** 2.0  
**Story Key:** 2-0-decouverte-locale-par-multicast-udp  
**Date:** 2026-04-29

---

## Story

En tant que nœud MobiCloud,  
Je veux découvrir mes pairs au sein du même sous-réseau Wi-Fi via Multicast UDP signé EC P-256,  
Afin de rester P2P pur en LAN sans dépendre des Serveurs Relais HA quand ce n'est pas nécessaire.

---

## Acceptance Criteria

**Given** le Foreground Service est actif et l'appareil est connecté à un Wi-Fi  
**When** le module de découverte locale démarre  
**Then** un `MulticastLock` est acquis (`WifiManager.createMulticastLock`) et conservé tant que le service tourne  
**And** le nœud émet périodiquement (toutes les 5 s) un datagramme `HELLO` Protobuf signé EC P-256 sur l'adresse multicast `239.255.42.99:48999` (TTL 1 — local link)  
**And** le nœud écoute en parallèle les `HELLO` reçus sur le même groupe et insère chaque pair valide dans `PeerRepository` (avec source `LAN_MULTICAST`)  
**And** la signature EC P-256 de chaque `HELLO` est vérifiée avant insertion dans `PeerRepository`  
**And** la logique est encapsulée dans `data/repository/LocalDiscoveryRepositoryImpl.kt` (interface `domain/repository/LocalDiscoveryRepository.kt`)  
**And** si le réseau ne supporte pas le multicast (filtrage hotspot), un log INFO `"Multicast indisponible — fallback Relais HA seul"` est émis dans `RadarLogConsole` via `NetworkEventRepository.pushEvent()` après 30 s sans `HELLO` entrant  
**And** la découverte LAN est **prioritaire** : un pair présent dans les deux sources (`LAN_MULTICAST` et `RELAY_HA`) est conservé avec source `LAN_MULTICAST`

---

## Tasks / Subtasks

- [x] Task 1 : Créer les modèles domaine `HelloPayload` et `HelloMessage` (AC: #2, #3, #4)
  - [x] Créer `domain/models/HelloPayload.kt` — `@Serializable data class HelloPayload(nodeId, publicKeyBytes, tcpPort, reliabilityScore)`
  - [x] Créer `domain/models/HelloMessage.kt` — `@Serializable data class HelloMessage(payload: HelloPayload, signature: ByteArray)`

- [x] Task 2 : Créer l'interface `LocalDiscoveryRepository` (AC: #5)
  - [x] Créer `domain/repository/LocalDiscoveryRepository.kt` — interface avec `fun start()`, `fun stop()`

- [x] Task 3 : Implémenter `LocalDiscoveryRepositoryImpl` — émission HELLO signée (AC: #2)
  - [x] Créer `data/repository/LocalDiscoveryRepositoryImpl.kt`
  - [x] Acquérir le `MulticastLock` via `WifiManager.createMulticastLock("mobicloud_discovery")`
  - [x] Boucle d'émission : toutes les 5 s, signer `MobiCloudProtoBuf.encodeToByteArray(payload)` via Keystore EC P-256 (`SHA256withECDSA`), encoder le `HelloMessage` complet, émettre en UDP sur `239.255.42.99:48999` (TTL 1)
  - [x] Séparer le payload signable (`HelloPayload`) du conteneur réseau (`HelloMessage`) pour éviter les problèmes de sérialisation circulaire

- [x] Task 4 : Implémenter la réception et la vérification de signature (AC: #3, #4, #6, #7)
  - [x] Boucle de réception dans `LocalDiscoveryRepositoryImpl` : décoder `HelloMessage` depuis le datagramme UDP
  - [x] Vérifier la signature : reconstruire la clé publique depuis `msg.payload.publicKeyBytes` via `KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))`, puis vérifier avec `Signature.getInstance("SHA256withECDSA")`
  - [x] Appeler `peerRepository.registerOrUpdatePeer(identity, timestampMs, source = DiscoverySource.LAN_MULTICAST, ipAddress = packet.address.hostAddress, port = msg.payload.tcpPort)`
  - [x] Après 30 s sans HELLO valide reçu, appeler `networkEventRepository.pushEvent("Multicast indisponible — fallback Relais HA seul")`

- [x] Task 5 : Câblage DI dans `P2PModule` (AC: #5)
  - [x] Ajouter `@Provides @Singleton fun provideLocalDiscoveryRepository(...): LocalDiscoveryRepository = LocalDiscoveryRepositoryImpl(...)`
  - [x] Injecter `WifiManager`, `PeerRepository`, `NetworkEventRepository`, `IdentityRepository` dans `LocalDiscoveryRepositoryImpl`

- [x] Task 6 : Intégration dans `MobicloudP2PService` — démarrage/arrêt du `LocalDiscoveryRepository` (AC: #1)
  - [x] Dans `startP2PNetworkLoops()` : appeler `localDiscoveryRepository.start()` dans un `launch {}`
  - [x] Dans `onDestroy()` : appeler `localDiscoveryRepository.stop()` (libérer `MulticastLock`)

- [x] Task 7 : Tests unitaires (AC: #3, #4)
  - [x] Créer `data/repository/LocalDiscoveryRepositoryImplTest.kt` (JVM unit test, MockK)
  - [x] Test : émission → `signPayload` appelé avec les bons bytes du payload
  - [x] Test : réception valide → `peerRepository.registerOrUpdatePeer()` appelé avec `LAN_MULTICAST`
  - [x] Test : réception avec signature invalide → `registerOrUpdatePeer` NON appelé, log erreur
  - [x] Test : 30 s sans HELLO → `networkEventRepository.pushEvent("Multicast indisponible — fallback Relais HA seul")`

---

## Dev Notes

> [!CAUTION] **DISASTER PREVENTION — LIRE AVANT TOUTE IMPLÉMENTATION**
>
> 1. **NE PAS réimplémenter la couche de sérialisation Protobuf** — utiliser UNIQUEMENT `MobiCloudProtoBuf` (singleton dans `core/format/ProtoBufSerializer.kt`). Ne jamais instancier `ProtoBuf { }` directement.
> 2. **NE PAS recréer de socket multicast ad hoc dans `MobicloudP2PService`** — toute la logique UDP doit être dans `LocalDiscoveryRepositoryImpl`. Le service orchestre uniquement via `start()`/`stop()`.
> 3. **La clé privée EC P-256 est dans l'Android Keystore (non exportable)** — le pattern de signature exact est dans `RelayAuthSigner.kt` (voir ci-dessous). Ne jamais appeler `KeyPairGenerator` dans cette story.
> 4. **Conflit d'adresse multicast** : architecture.md dit `239.255.255.250:7777` mais epics.md Story 2.0 dit `239.255.42.99:48999`. **Utiliser `239.255.42.99:48999`** (epics.md est plus récent). Extraire en constante nommée dans `LocalDiscoveryRepositoryImpl`.
> 5. **`LocalDiscoveryRepository` ≠ `LocalDiscoveryRepositoryImpl`** : les tests unitaires mockent l'interface, pas l'implémentation.
> 6. **Result<T> obligatoire** : toutes les méthodes suspending de l'implémentation utilisent `runCatching {}`. Logger les échecs avec `Log.e(TAG, ...)`.
> 7. **`MobicloudP2PService` ne doit PAS recevoir le `WifiManager` directement** — l'injecter dans `LocalDiscoveryRepositoryImpl` via Hilt.

---

### Infrastructure Existante (NE PAS Réimplémenter)

| Fichier | Rôle | Action |
|---|---|---|
| `core/format/ProtoBufSerializer.kt` | `MobiCloudProtoBuf` singleton | UTILISER pour encode/decode |
| `data/p2p/websocket/RelayAuthSigner.kt` | Pattern signing Keystore EC P-256 | COPIER le pattern `Signature.getInstance("SHA256withECDSA")` |
| `domain/repository/PeerRepository.kt` | Stockage pairs | APPELER `registerOrUpdatePeer(..., source = LAN_MULTICAST)` |
| `domain/repository/NetworkEventRepository.kt` | RadarLogConsole log | APPELER `pushEvent(message)` pour le fallback 30s |
| `domain/repository/IdentityRepository.kt` | Identité locale | APPELER `getIdentity()` pour obtenir `nodeId`, `publicKeyBytes`, `reliabilityScore` |
| `core/security/KeystoreManager.kt` | `KEY_ALIAS = "mobicloud_node_identity_key"` | RÉFÉRENCER l'alias pour accès Keystore |
| `domain/models/Peer.kt` | `DiscoverySource.LAN_MULTICAST` | UTILISER ce source enum |
| `data/local/dao/PeerDao.kt` + `PeerNodeEntity.kt` | Persistence Room | Géré automatiquement via `PeerRepository` |

---

### Modèles Domaine à Créer

```kotlin
// domain/models/HelloPayload.kt — payload signable (sans signature)
@Serializable
data class HelloPayload(
    val nodeId: String,
    val publicKeyBytes: ByteArray,  // X.509 DER encoded, pour reconstruction clé publique
    val tcpPort: Int,
    val reliabilityScore: Float
)

// domain/models/HelloMessage.kt — conteneur réseau complet
@Serializable
data class HelloMessage(
    val payload: HelloPayload,
    val signature: ByteArray   // SHA256withECDSA sur MobiCloudProtoBuf.encodeToByteArray(payload)
)
```

> [!NOTE] **Séparer payload et signature** : ne pas mettre `signature` dans `HelloPayload` — sinon les bytes signés incluraient le champ signature lui-même (valeur vide lors de la signature, non-vide lors de la vérification → corruption silencieuse).

---

### Pattern de Signature (à reproduire dans `LocalDiscoveryRepositoryImpl`)

```kotlin
// Inspiré de RelayAuthSigner.kt — pattern exact du projet
private suspend fun signPayload(payloadBytes: ByteArray): ByteArray {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val entry = ks.getEntry(KeystoreManager.KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
    return Signature.getInstance("SHA256withECDSA").apply {
        initSign(entry.privateKey)
        update(payloadBytes)
    }.sign()
}
```

---

### Pattern de Vérification de Signature

```kotlin
// publicKeyBytes = X.509 DER (identique à KeyPair.public.encoded dans KeystoreManager)
private fun verifySignature(payloadBytes: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
    return try {
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update(payloadBytes)
        }.verify(signature)
    } catch (e: Exception) {
        Log.w(TAG, "Signature invalide depuis pair", e)
        false
    }
}
```

---

### Interface `LocalDiscoveryRepository`

```kotlin
// domain/repository/LocalDiscoveryRepository.kt
interface LocalDiscoveryRepository {
    fun start()
    fun stop()
}
```

---

### Structure de `LocalDiscoveryRepositoryImpl`

```kotlin
// data/repository/LocalDiscoveryRepositoryImpl.kt
class LocalDiscoveryRepositoryImpl @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val peerRepository: PeerRepository,
    private val networkEventRepository: NetworkEventRepository,
    @ApplicationContext private val context: Context,
    private val externalScope: CoroutineScope  // injecté via AppModule
) : LocalDiscoveryRepository {

    companion object {
        private const val TAG = "LocalDiscoveryRepo"
        private const val MULTICAST_GROUP = "239.255.42.99"
        private const val MULTICAST_PORT = 48999
        private const val HELLO_INTERVAL_MS = 5_000L
        private const val MULTICAST_TIMEOUT_MS = 30_000L
        private const val BUFFER_SIZE = 2048
    }

    private var multicastLock: WifiManager.MulticastLock? = null
    private var job: Job? = null

    override fun start() {
        acquireMulticastLock()
        job = externalScope.launch {
            launch { broadcastLoop() }
            launch { receiveLoop() }
        }
    }

    override fun stop() {
        job?.cancel()
        multicastLock?.release()
        multicastLock = null
    }

    private fun acquireMulticastLock() {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("mobicloud_discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private suspend fun broadcastLoop() { /* emit HelloMessage toutes les 5s */ }
    private suspend fun receiveLoop()   { /* recevoir + vérifier + insérer pairs */ }
}
```

> [!IMPORTANT] **`WifiManager` sans `applicationContext`** → fuite mémoire. Toujours utiliser `@ApplicationContext context: Context` pour `getSystemService()`. Hilt fournit `@ApplicationContext` automatiquement.

---

### Logique de Priorité LAN sur RELAY_HA

```kotlin
// Dans receiveLoop(), après vérification signature :
val identity = NodeIdentity(
    nodeId = msg.payload.nodeId,
    publicKeyBytes = msg.payload.publicKeyBytes,
    reliabilityScore = msg.payload.reliabilityScore
)
// registerOrUpdatePeer avec LAN_MULTICAST : si le pair existe déjà en RELAY_HA,
// PeerRepositoryImpl.upsert() remplace la source par LAN_MULTICAST (plus récent).
peerRepository.registerOrUpdatePeer(
    identity = identity,
    timestampMs = SystemClock.elapsedRealtime(),
    source = DiscoverySource.LAN_MULTICAST,
    ipAddress = packet.address.hostAddress,
    port = msg.payload.tcpPort
).onFailure { Log.e(TAG, "Échec insertion pair LAN", it) }
```

> [!NOTE] `PeerRepositoryImpl` utilise `@Upsert` (Room 2.8.4, déjà vérifié en story 2-1) — l'appel avec `LAN_MULTICAST` écrase automatiquement la source `RELAY_HA` si le `nodeId` est identique.

---

### Logique Fallback RadarLogConsole (30 s sans HELLO)

```kotlin
private suspend fun receiveLoop() {
    var lastValidHelloMs = SystemClock.elapsedRealtime()
    var fallbackLogged = false

    // ... loop de réception ...
    // Sur réception valide :
    lastValidHelloMs = SystemClock.elapsedRealtime()
    fallbackLogged = false

    // Périodiquement (ex. dans un watchdog lancé en parallèle) :
    while (isActive) {
        delay(5_000L)
        if (!fallbackLogged && SystemClock.elapsedRealtime() - lastValidHelloMs > MULTICAST_TIMEOUT_MS) {
            networkEventRepository.pushEvent("Multicast indisponible — fallback Relais HA seul")
            fallbackLogged = true
        }
    }
}
```

---

### Câblage DI — `P2PModule.kt` (extraits à ajouter)

```kotlin
@Provides @Singleton
fun provideLocalDiscoveryRepository(
    identityRepository: IdentityRepository,
    peerRepository: PeerRepository,
    networkEventRepository: NetworkEventRepository,
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
): LocalDiscoveryRepository = LocalDiscoveryRepositoryImpl(
    identityRepository, peerRepository, networkEventRepository, context, scope
)
```

> [!NOTE] `@ApplicationScope` est l'annotation Hilt qualifier définie dans `di/ApplicationScope.kt`. Vérifier qu'elle est déjà déclarée (elle l'est — utilisée dans `PeerRepositoryImpl`).

---

### Intégration `MobicloudP2PService`

```kotlin
// Injecter LocalDiscoveryRepository via @Inject
@Inject lateinit var localDiscoveryRepository: LocalDiscoveryRepository

// Dans startP2PNetworkLoops() :
launch { localDiscoveryRepository.start() }

// Dans onDestroy() :
localDiscoveryRepository.stop()
```

---

### Fichiers à Créer / Modifier

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   ├── models/
│   │   ├── HelloPayload.kt                   ← NOUVEAU (@Serializable)
│   │   └── HelloMessage.kt                   ← NOUVEAU (@Serializable)
│   └── repository/
│       └── LocalDiscoveryRepository.kt        ← NOUVEAU (interface)
├── data/
│   └── repository/
│       └── LocalDiscoveryRepositoryImpl.kt    ← NOUVEAU (implémentation complète)
├── di/
│   └── P2PModule.kt                           ← MODIFIER (+provideLocalDiscoveryRepository)
└── data/network/service/
    └── MobicloudP2PService.kt                 ← MODIFIER (+inject + start/stop)

app/src/test/kotlin/com/mobicloud/
└── data/repository/
    └── LocalDiscoveryRepositoryImplTest.kt    ← NOUVEAU (MockK JVM tests)
```

---

### Patterns Architecture à Respecter

- **Clean Architecture** : `HelloPayload`, `HelloMessage`, `LocalDiscoveryRepository` dans `domain/`. Zéro import `android.*` dans les interfaces. `LocalDiscoveryRepositoryImpl` dans `data/repository/`.
- **`MobiCloudProtoBuf`** : seul serializer autorisé. Import : `import com.mobicloud.core.format.MobiCloudProtoBuf`.
- **Result<T>** : toutes les operations suspending utilisent `runCatching {}`. Pas d'exception silencieuse.
- **`@ApplicationContext`** : utiliser pour `WifiManager`. Jamais `context.applicationContext` redondant — Hilt le gère.
- **`@ApplicationScope`** : qualifier Hilt déclaré dans `di/ApplicationScope.kt` — NE PAS créer un nouveau scope.
- **Tests JVM** : MockK uniquement (pas de Robolectric — Deferred F-4 du projet). Mocker `PeerRepository`, `NetworkEventRepository`, `IdentityRepository`.

---

### Contexte Intelligence — Stories Précédentes

- **Story 2-1 (ancien)** : Implémentait `UdpHeartbeatBroadcaster`/`UdpHeartbeatReceiver` directement dans `MobicloudP2PService` — **ces classes n'existent plus dans le codebase** (refactoring V5.0). Ne pas tenter de les recréer ou les importer.
- **Story 2-1 signalisation (nouvelle)** : `SignalingRepositoryImpl` délègue entièrement au `RelayWebSocketClient` pour la signalisation HA — pattern similaire d'injection à reproduire.
- **Story 1.3 (identité)** : `publicKeyBytes` = X.509 DER (format `keyPair.public.encoded`). Reconstruire la clé publique via `X509EncodedKeySpec` — **jamais via `ECPublicKeySpec`** (format différent).
- **Story 1.4 (foreground service)** : `MobicloudP2PService` a déjà un guard `@Volatile loopsStarted` — ne pas appeler `localDiscoveryRepository.start()` deux fois.
- **Story 8.2 (RelayWebSocketClient)** : Le pattern signing EC P-256 de `RelayAuthSigner.kt` est le référentiel du projet — reproduire EXACTEMENT ce pattern dans `signPayload()`.

---

### Informations Techniques Critiques

- **Room version** : 2.8.4 (vérifié en story 2-1) — `@Upsert` disponible et déjà utilisé dans `PeerDao`.
- **kotlinx-serialization-protobuf** : `@OptIn(ExperimentalSerializationApi::class)` requis pour `encodeToByteArray`/`decodeFromByteArray` protobuf.
- **`MulticastSocket` vs `DatagramSocket`** : Utiliser `MulticastSocket(MULTICAST_PORT)` et appeler `joinGroup(InetAddress.getByName(MULTICAST_GROUP))` dans le constructeur/start. Pour l'émission, utiliser un `DatagramSocket` séparé (le `MulticastSocket` lie le port, et l'émission depuis le même socket est acceptable).
- **TTL 1** : `(socket as MulticastSocket).timeToLive = 1` — critique pour ne pas dépasser le sous-réseau local.
- **Signature de soi-même** : le récepteur peut recevoir ses propres datagrams. Filtrer avec `if (msg.payload.nodeId == identity.nodeId) continue`.

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (2026-04-29)

### Completion Notes List

- Modèles `HelloPayload` et `HelloMessage` créés avec `@Serializable` et `equals`/`hashCode` corrects pour `ByteArray`.
- Interface `LocalDiscoveryRepository` (domaine) : `start()` / `stop()`.
- `LocalDiscoveryRepositoryImpl` : classe `open` pour testabilité. `MulticastLock`, `broadcastLoop` (5s), `receiveLoop` avec `MulticastSocket(48999)`, TTL=1, `soTimeout=2s`. Filtrage des propres datagrams par `nodeId`. Watchdog fallback 30s sans HELLO valide.
- `signPayload` et `verifySignature` marqués `internal open` pour permettre la sous-classe de test JVM (contournement AndroidKeystore non disponible en tests JVM).
- `processIncomingBytes` extrait en `internal suspend fun` pour tester la logique métier directement.
- `P2PModule` : ajout de `provideLocalDiscoveryRepository` via `@ApplicationScope CoroutineScope`.
- `MobicloudP2PService` : `@Inject localDiscoveryRepository`, `start()` dans `startP2PNetworkLoops()`, `stop()` dans `onDestroy()` avant `serviceScope.cancel()`.
- Tests JVM (8 tests) : `mockkStatic(Log::class)` + `mockkStatic(SystemClock::class)` pour éviter les exceptions Android non mockées. Sous-classe `TestableLocalDiscoveryRepositoryImpl` override `signPayload` avec JVM EC P-256. 0 régression sur la suite pré-existante.

### File List

- `app/src/main/kotlin/com/mobicloud/domain/models/HelloPayload.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/domain/models/HelloMessage.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/domain/repository/LocalDiscoveryRepository.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/data/repository/LocalDiscoveryRepositoryImpl.kt` (NOUVEAU)
- `app/src/main/kotlin/com/mobicloud/di/P2PModule.kt` (MODIFIÉ — ajout provideLocalDiscoveryRepository)
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (MODIFIÉ — inject + start/stop)
- `app/src/test/kotlin/com/mobicloud/data/repository/LocalDiscoveryRepositoryImplTest.kt` (NOUVEAU)

### Review Findings

#### Decision Needed

- [x] [Review][Decision] **DN1 — `tcpPort = 0` hardcodé dans `broadcastLoop`** — Résolu : `start(tcpPort: Int)` reçoit le port du service après `startServer()` ; interface mise à jour et appel déplacé dans `MobicloudP2PService`.
- [x] [Review][Decision] **DN2 — AC7 : priorité LAN non garantie** — Résolu : SQL `insertOrUpdatePreservingRole` mis à jour avec un `CASE WHEN source = 'LAN_MULTICAST' AND :source != 'LAN_MULTICAST' THEN 'LAN_MULTICAST'` dans `PeerDao.kt`.
- [x] [Review][Decision] **DN3 — `verifySignature` accepte tout `publicKeyBytes` fourni par le réseau** — Résolu : vérification `nodeId == SHA-256(publicKeyBytes).take(8)` ajoutée dans `processIncomingBytes` ; cohérent avec `KeystoreManager.generateNodeId`.

#### Patches

- [x] [Review][Patch] **P1 — `job` non `@Volatile` + double-start non gardé** — Appliqué : `@Volatile` sur `job`, guard `if (job?.isActive == true) return` dans `start()`.
- [x] [Review][Patch] **P2 — `isSuperPair` manquant dans `registerOrUpdatePeer`** — Appliqué : `isSuperPair = false` explicite dans l'appel ; la SQL `MAX()` préserve déjà le statut super-pair existant.
- [x] [Review][Patch] **P3 — Test fallback tautologique (test 6)** — Appliqué : `logFallbackIfNeeded` extrait en `internal open suspend fun`, 3 tests directs ajoutés (timeout déclenché, timeout non atteint, déjà loggé).
- [x] [Review][Patch] **P4 — TTL non configuré côté émission** — Appliqué : `DatagramSocket` remplacé par `MulticastSocket().apply { timeToLive = 1 }` dans `broadcastLoop`.
- [x] [Review][Patch] **P5 — `while (job?.isActive == true)` non idiomatique** — Appliqué : `while (isActive)` dans les deux loops.
- [x] [Review][Patch] **P6 — `leaveGroup()` jamais exécuté à l'annulation** — Appliqué : socket construit manuellement, `leaveGroup()` dans bloc `finally`.
- [x] [Review][Patch] **P7 — `MulticastSocket` sans `reuseAddress = true`** — Appliqué : `MulticastSocket(null)` + `reuseAddress = true` + `bind(InetSocketAddress(MULTICAST_PORT))`.
- [x] [Review][Patch] **P8 — `signPayload` ouvre le KeyStore à chaque HELLO (5 s)** — Appliqué : `cachedPrivateKeyEntry` via `lazy { }`.
- [x] [Review][Patch] **P9 — Aucune limite de taille sur le datagramme entrant** — Appliqué : guard `bytes.isEmpty() || bytes.size > BUFFER_SIZE` avant désérialisation.
- [x] [Review][Patch] **P10 — `packet.address.hostAddress` null ou zone IPv6** — Appliqué : `(hostAddress ?: "").substringBefore('%')`.
- [x] [Review][Patch] **P11 — `scope` non annulé dans `@After` des tests** — Appliqué : `@After fun tearDown() { scope.cancel() }`.
- [x] [Review][Patch] **P12 — Clés EC de test générées à l'instance, pas en `companion`** — Appliqué : `testKeyPair`, `peerKeyPair`, `generateNodeId` déplacés dans `companion object`.

#### Deferred

- [x] [Review][Defer] **W1 — `DatagramSocket` non lié à l'interface WiFi spécifique (multi-home)** [`LocalDiscoveryRepositoryImpl.kt:90`] — deferred, pre-existing ; concerne le routage multi-interface (WiFi + Hotspot), préoccupation platform-level hors scope story.
- [x] [Review][Defer] **W2 — `fallbackLogged` trop facilement réinitialisé par un seul HELLO** [`LocalDiscoveryRepositoryImpl.kt:148`] — deferred, pre-existing ; amélioration UX/debouncing à évaluer ultérieurement.
- [x] [Review][Defer] **W3 — `getIdentity()` appelé deux fois (broadcastLoop + receiveLoop)** [`LocalDiscoveryRepositoryImpl.kt:81,114`] — deferred, pre-existing ; micro-optimisation sans impact fonctionnel.
- [x] [Review][Defer] **W4 — `reliabilityScore` exposé en clair dans les broadcasts réseau** [`HelloPayload.kt:9`] — deferred, pre-existing ; décision d'architecture P2P (fingerprinting potentiel), hors scope.
- [x] [Review][Defer] **W5 — `BindException` Android 12+ non gérée avec retry** [`LocalDiscoveryRepositoryImpl.kt:125`] — deferred, pre-existing ; restriction foreground service type, nécessite investigation permissions manifest séparée.

## Change Log

- 2026-04-29 : Story 2.0 créée — Découverte Locale par Multicast UDP signé EC P-256, `LocalDiscoveryRepositoryImpl`, priorité LAN sur Relay HA.
- 2026-04-29 : Implémentation complète — 7 tâches, 8 tests JVM, 0 régression. Statut → review.
- 2026-04-29 : Code review — 3 DN + 12 patches appliqués. Statut → done.
