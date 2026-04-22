# Story 6.1: Localisation des Blocs via Requête DHT

Status: review

## Story

En tant qu'utilisateur,
Je veux appuyer sur "Télécharger" sur une entrée du catalogue dans l'Explorer et localiser automatiquement tous ses blocs dans la DHT,
Afin de savoir depuis quels nœuds je peux les récupérer (prérequis pour le téléchargement concurrent de la Story 6.2).

## Acceptance Criteria

1. **Given** l'utilisateur est sur l'onglet Explorer et le fichier apparaît dans le catalogue
   **When** l'utilisateur appuie sur "Télécharger" pour une entrée
   **Then** une requête de localisation est déclenchée : pour chaque `FragmentLocation.fragmentHash` (= blockId), le nœud hébergeur est résolu depuis `PeerRepository.peers`

2. **And** pour chaque `blockId`, la `PeerRegistry` retourne l'`ipAddress:port` du nœud qui le détient — via `FragmentLocation.nodeIds` croisés avec `PeerRepository.peers` actifs

3. **And** si un bloc est hébergé par plusieurs nœuds (`FragmentLocation.nodeIds.size > 1`), le nœud avec le meilleur `identity.reliabilityScore` est priorisé

4. **And** si aucun nœud actif n'est trouvé via `nodeIds`, le fallback `dhtRepository.findByBlockId(blockId)` est tenté

5. **And** si la DHT locale ne contient pas l'entrée, la requête est relayée au nœud suivant de l'anneau : `ConsistentHashRing.getPartition(blockId)` → TCP `DHT_LOOKUP_REQ` (discriminant `0x30`) envoyé au nœud responsable → `DHT_LOOKUP_RESP` (`0x31`) reçu

6. **And** le résultat est une `Map<String, ResolvedBlockLocation>` remontée via `Result<Map<String, ResolvedBlockLocation>>` — clé = `blockId`, valeur = meilleur pair résolu

7. **And** la logique est dans `domain/usecase/m05_dht_catalog/LocalizeFileBlocksUseCase.kt`

8. **And** le `CatalogEntryCard` affiche un bouton "Télécharger" qui déclenche `ExplorerViewModel.initiateDownload(fileHash)` — visible uniquement si `availabilityState() != DEGRADE`

## Tasks / Subtasks

- [x] Task 1 : Créer le modèle domaine `ResolvedBlockLocation` (AC: #6)
  - [x] Subtask 1.1 : Créer `domain/models/ResolvedBlockLocation.kt` :
    ```kotlin
    data class ResolvedBlockLocation(
        val blockId: String,
        val fragmentIndex: Int,
        val nodeId: String,
        val ipAddress: String,
        val port: Int,
        val reliabilityScore: Float
    )
    ```

- [x] Task 2 : Créer les messages Protobuf pour le relay DHT ring (AC: #5)
  - [x] Subtask 2.1 : Créer `domain/models/DhtLookupRequestMessage.kt` :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class DhtLookupRequestMessage(
        @ProtoNumber(1) val blockId: String = ""
    )
    ```
  - [x] Subtask 2.2 : Créer `domain/models/DhtLookupResponseMessage.kt` :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class DhtLookupResponseMessage(
        @ProtoNumber(1) val blockId: String = "",
        @ProtoNumber(2) val nodeId: String = "",
        @ProtoNumber(3) val ipAddress: String = "",
        @ProtoNumber(4) val port: Int = 0,
        @ProtoNumber(5) val found: Boolean = false,
        @ProtoNumber(6) val timestamp: Long = 0L
    )
    ```

- [x] Task 3 : Ajouter constantes de relay DHT dans `BlockTransferChannel` (AC: #5)
  - [x] Subtask 3.1 : Ajouter dans `data/p2p/tcp/BlockTransferChannel.kt` :
    ```kotlin
    const val DHT_LOOKUP_REQ: Byte = 0x30
    const val DHT_LOOKUP_RESP: Byte = 0x31
    ```

- [x] Task 4 : Étendre `DhtRepository` interface + `DhtRepositoryImpl` pour le relay (AC: #5)
  - [x] Subtask 4.1 : Ajouter dans `domain/repository/DhtRepository.kt` :
    ```kotlin
    suspend fun remoteLookup(blockId: String, peerIp: String, peerPort: Int): Result<DhtEntry?>
    ```
  - [x] Subtask 4.2 : Implémenter dans `data/repository/DhtRepositoryImpl.kt` :
    ```kotlin
    override suspend fun remoteLookup(blockId: String, peerIp: String, peerPort: Int): Result<DhtEntry?> =
        runCatching {
            withContext(Dispatchers.IO) {
                Socket().use { socket ->
                    socket.soTimeout = 3_000
                    socket.connect(InetSocketAddress(peerIp, peerPort), 3_000)
                    val out = DataOutputStream(socket.getOutputStream())
                    val requestBytes = ProtoBuf.encodeToByteArray(DhtLookupRequestMessage(blockId))
                    out.writeByte(BlockTransferChannel.DHT_LOOKUP_REQ.toInt())
                    out.writeInt(requestBytes.size)
                    out.write(requestBytes)
                    out.flush()
                    val inp = DataInputStream(socket.getInputStream())
                    val disc = inp.readByte()
                    if (disc != BlockTransferChannel.DHT_LOOKUP_RESP) return@use null
                    val len = inp.readInt()
                    if (len <= 0 || len > 1024) return@use null
                    val respBytes = ByteArray(len).also { inp.readFully(it) }
                    val resp = ProtoBuf.decodeFromByteArray<DhtLookupResponseMessage>(respBytes)
                    if (!resp.found) null
                    else DhtEntry(resp.blockId, resp.nodeId, resp.ipAddress, resp.port, resp.timestamp)
                }
            }
        }
    ```
    Imports nécessaires : `java.net.InetSocketAddress`, `java.net.Socket`, `java.io.DataInputStream/DataOutputStream`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`.

- [x] Task 5 : Ajouter handler `DHT_LOOKUP_REQ` dans `TcpConnectionManager` (AC: #5)
  - [x] Subtask 5.1 : Ajouter champ dans `TcpConnectionManager` :
    ```kotlin
    var dhtRelayHandler: DhtRepository? = null
    ```
  - [x] Subtask 5.2 : Dans `handleIncomingConnection()` ajouter le case après `BLOCK_TRANSFER` :
    ```kotlin
    BlockTransferChannel.DHT_LOOKUP_REQ -> handleDhtLookupRelay(input, socket)
    ```
  - [x] Subtask 5.3 : Implémenter `handleDhtLookupRelay(input, socket)` dans `TcpConnectionManager` :
    ```kotlin
    private suspend fun handleDhtLookupRelay(input: InputStream, socket: Socket) {
        val len = DataInputStream(input).readInt()
        if (len <= 0 || len > 1024) { socket.close(); return }
        val reqBytes = ByteArray(len).also { DataInputStream(input).readFully(it) }
        val req = ProtoBuf.decodeFromByteArray<DhtLookupRequestMessage>(reqBytes)
        val entry = dhtRelayHandler?.findByBlockId(req.blockId)?.getOrNull()
        val resp = if (entry != null) {
            DhtLookupResponseMessage(entry.blockId, entry.nodeId, entry.ipAddress, entry.port, found = true, entry.timestamp)
        } else {
            DhtLookupResponseMessage(blockId = req.blockId, found = false)
        }
        val respBytes = ProtoBuf.encodeToByteArray(resp)
        val out = DataOutputStream(socket.getOutputStream())
        out.writeByte(BlockTransferChannel.DHT_LOOKUP_RESP.toInt())
        out.writeInt(respBytes.size)
        out.write(respBytes)
        out.flush()
    }
    ```

- [x] Task 6 : Câbler `dhtRelayHandler` dans `MobicloudP2PService` (AC: #5)
  - [x] Subtask 6.1 : Injecter `DhtRepository` dans `MobicloudP2PService` via `@Inject`
  - [x] Subtask 6.2 : Assigner **avant** `startServer()` :
    ```kotlin
    tcpConnectionManager.dhtRelayHandler = dhtRepository
    ```

- [x] Task 7 : Créer `LocalizeFileBlocksUseCase` (AC: #1–#7)
  - [x] Subtask 7.1 : Créer `domain/usecase/m05_dht_catalog/LocalizeFileBlocksUseCase.kt` :
    ```kotlin
    @Singleton
    class LocalizeFileBlocksUseCase @Inject constructor(
        private val catalogRepository: CatalogRepository,
        private val dhtRepository: DhtRepository,
        private val peerRepository: PeerRepository
    ) {
        suspend fun invoke(fileHash: String): Result<Map<String, ResolvedBlockLocation>>
    }
    ```
  - [x] Subtask 7.2 : Algorithme dans `invoke()` (exécuté sur `Dispatchers.IO` via `withContext`) :
    ```
    1. catalogRepository.getEntry(fileHash) → null → Result.failure(FileNotFoundException("Fichier introuvable dans le catalogue : $fileHash"))
    2. val activePeers = peerRepository.peers.value.filter { it.isActive && it.ipAddress != null && it.port != null }
    3. Pour chaque fragmentLocation dans entry.fragmentLocations :
       val blockId = fragmentLocation.fragmentHash
       a. PRIMARY : activePeers.filter { it.identity.nodeId in fragmentLocation.nodeIds }
                              .maxByOrNull { it.identity.reliabilityScore }
          → ResolvedBlockLocation(blockId, fragmentLocation.fragmentIndex, peer.identity.nodeId, peer.ipAddress!!, peer.port!!, peer.identity.reliabilityScore)
       b. DHT FALLBACK (si primary null) : dhtRepository.findByBlockId(blockId).getOrNull()
          → cross-check : activePeers.find { it.identity.nodeId == entry.nodeId }
          → ResolvedBlockLocation(...)
       c. RING RELAY (si b null et activePeers non vide) :
          val ring = ConsistentHashRing(activePeers.map { it.identity.nodeId })
          val responsibleNodeId = ring.getPartition(blockId)
          val relayPeer = activePeers.find { it.identity.nodeId == responsibleNodeId } ?: null
          → dhtRepository.remoteLookup(blockId, relayPeer.ipAddress!!, relayPeer.port!!).getOrNull()
          → si non null → ResolvedBlockLocation(...)
    4. Construire Map<String, ResolvedBlockLocation> (ignorer les nulls)
    5. Result.success(map)
    ```
  - [x] Subtask 7.3 : Exception interne :
    ```kotlin
    class FileNotFoundException(message: String) : Exception(message)
    ```
    Déclarer dans le même fichier ou dans `domain/models/`.

- [x] Task 8 : Créer `DownloadState` (AC: #8)
  - [x] Subtask 8.1 : Créer `presentation/explorer/DownloadState.kt` :
    ```kotlin
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Locating(val fileHash: String) : DownloadState()
        data class Located(
            val fileHash: String,
            val blockMap: Map<String, ResolvedBlockLocation>
        ) : DownloadState()
        data class Error(val fileHash: String, val message: String) : DownloadState()
    }
    ```

- [x] Task 9 : Modifier `ExplorerViewModel` (AC: #1, #8)
  - [x] Subtask 9.1 : Injecter `LocalizeFileBlocksUseCase` dans le constructeur `ExplorerViewModel`
  - [x] Subtask 9.2 : Ajouter :
    ```kotlin
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun initiateDownload(fileHash: String) {
        if (_downloadState.value is DownloadState.Locating) return
        _downloadState.value = DownloadState.Locating(fileHash)
        viewModelScope.launch {
            localizeFileBlocksUseCase.invoke(fileHash)
                .onSuccess { map ->
                    _downloadState.value = DownloadState.Located(fileHash, map)
                }
                .onFailure { e ->
                    _downloadState.value = DownloadState.Error(fileHash, e.message ?: "Localisation échouée")
                }
        }
    }
    ```

- [x] Task 10 : Modifier `CatalogEntryCard` (AC: #8)
  - [x] Subtask 10.1 : Ajouter paramètre `onDownload: ((String) -> Unit)? = null` à `CatalogEntryCard`
  - [x] Subtask 10.2 : Conditionner le bouton : afficher uniquement si `onDownload != null && entry.availabilityState() != AvailabilityState.DEGRADE`
  - [x] Subtask 10.3 : Ajouter un `IconButton` ou `TextButton` avec label "↓" ou "Télécharger" (style cohérent OLED : couleur `Color(0xFF00FF41)` si COMPLET, `Color(0xFFFFB300)` si PARTIEL) :
    ```kotlin
    onDownload?.let { callback ->
        if (entry.availabilityState() != AvailabilityState.DEGRADE) {
            TextButton(onClick = { callback(entry.fileHash) }) {
                Text("↓", color = badgeColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
    ```

- [x] Task 11 : Modifier `ExplorerScreen` (AC: #8)
  - [x] Subtask 11.1 : Collecter `downloadState` :
    ```kotlin
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    ```
  - [x] Subtask 11.2 : Dans `LaunchedEffect` ou séparé, afficher un Snackbar en cas d'`Error` ou de `Located` :
    ```kotlin
    val terminalDownloadState = remember(downloadState) {
        downloadState.takeIf { it is DownloadState.Located || it is DownloadState.Error }
    }
    LaunchedEffect(terminalDownloadState) {
        when (val s = terminalDownloadState) {
            is DownloadState.Located -> snackbarHostState.showSnackbar(
                "${s.blockMap.size} blocs localisés pour ${s.fileHash.take(8)}..."
            )
            is DownloadState.Error -> snackbarHostState.showSnackbar("Erreur : ${s.message}")
            else -> Unit
        }
    }
    ```
  - [x] Subtask 11.3 : Passer le callback dans `CatalogEntryCard` :
    ```kotlin
    CatalogEntryCard(
        entry = entry,
        onDownload = { fileHash -> viewModel.initiateDownload(fileHash) }
    )
    ```

- [x] Task 12 : Tests JVM pour `LocalizeFileBlocksUseCase` (AC: #1–#6)
  - [x] Subtask 12.1 : Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/LocalizeFileBlocksUseCaseTest.kt`
  - [x] Subtask 12.2 : Test 1 — Primary path : `fragmentLocation.nodeIds` correspond à un `Peer` actif → `ResolvedBlockLocation` avec le meilleur `reliabilityScore`
  - [x] Subtask 12.3 : Test 2 — Tie-breaking : 2 nœuds actifs pour même bloc → celui avec le `reliabilityScore` supérieur est retenu
  - [x] Subtask 12.4 : Test 3 — DHT fallback : `nodeIds` ne matchent aucun pair actif → `dhtRepository.findByBlockId()` appelé → `ResolvedBlockLocation` depuis DHT
  - [x] Subtask 12.5 : Test 4 — Fichier introuvable : `catalogRepository.getEntry(fileHash)` retourne `null` → `Result.failure(FileNotFoundException)`
  - [x] Subtask 12.6 : Test 5 — Résultat partiel : 1 bloc sur 6 non résolvable (aucun pair, DHT vide, relay null) → map retournée avec 5 entrées (pas de failure)
  - [x] Subtask 12.7 : Framework : `mockk`, `kotlinx-coroutines-test` (`runTest`), `UnconfinedTestDispatcher`

---

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Description | Action |
|---|---|---|
| `domain/usecase/m05_dht_catalog/LookupBlockLocationUseCase.kt` | Lookup **single** blockId via `dhtRepository.findByBlockId()` — retourne `Result<DhtEntry?>` | **NE PAS MODIFIER** — Story 6.1 crée `LocalizeFileBlocksUseCase` au-dessus (fichier-level, multi-blocs) |
| `domain/repository/DhtRepository.kt` | Interface avec `findByBlockId`, `findByNodeId`, `insertEntry`, `observeAllEntries` — **MODIFIER** uniquement pour ajouter `remoteLookup()` | **MODIFIER** — ajout minimal |
| `data/repository/DhtRepositoryImpl.kt` | Impl Room existante — **MODIFIER** uniquement pour `remoteLookup()` TCP | **MODIFIER** |
| `domain/usecase/m05_dht_catalog/ConsistentHashRing.kt` | `getPartition(blockId: String): String` — prend `List<String>` (nodeIds) → renvoie nodeId responsable | **RÉUTILISER** tel quel |
| `domain/models/DhtEntry.kt` | `blockId, nodeId, ipAddress, port, timestamp` | **RÉUTILISER** tel quel |
| `domain/models/CatalogEntry.kt` | `fileHash, ownerPubKeyHash, versionClock, fragmentLocations, wrappedMasterKey` | **RÉUTILISER** tel quel |
| `domain/models/FragmentLocation.kt` | `fragmentIndex, fragmentHash, nodeIds: List<String>` — `fragmentHash` est le `blockId` | **RÉUTILISER** tel quel |
| `domain/models/Peer.kt` | `identity: NodeIdentity, ipAddress: String?, port: Int?, isActive: Boolean` — `reliabilityScore` via `identity.reliabilityScore` | **RÉUTILISER** tel quel |
| `domain/repository/PeerRepository.kt` | `peers: StateFlow<List<Peer>>` — lire `.peers.value` (snapshot synchrone) | **RÉUTILISER** |
| `domain/repository/CatalogRepository.kt` | `getEntry(hash: String): Result<CatalogEntry?>` | **RÉUTILISER** |
| `data/p2p/tcp/BlockTransferChannel.kt` | Constantes 0x20/21/22 — **MODIFIER** pour ajouter 0x30/31 | **MODIFIER** |
| `data/p2p/tcp/TcpConnectionManager.kt` | Pattern `blockReceiverHandler` + `handleIncomingConnection()` `when` | **MODIFIER** — ajouter `dhtRelayHandler` + case `0x30` |
| `data/network/service/MobicloudP2PService.kt` | Pattern `blockReceiverHandler = receiveAndHostBlockUseCase` à reproduire | **MODIFIER** |
| `presentation/explorer/components/CatalogEntryCard.kt` | Affiche entrée catalogue sans action download | **MODIFIER** — ajouter `onDownload` param |
| `presentation/explorer/ExplorerViewModel.kt` | Gère `storeFile()` + `refreshCatalog()` — **MODIFIER** pour `initiateDownload()` | **MODIFIER** |
| `presentation/explorer/ExplorerScreen.kt` | Affiche le `LazyColumn` des entrées — **MODIFIER** pour collecter `downloadState` et passer callback | **MODIFIER** |

### ⚠️ CONTRAINTES CRITIQUES

**1. `fragmentHash` = `blockId` — ne pas confondre avec `fileHash` :**
`CatalogEntry.fileHash` identifie le **fichier**. `FragmentLocation.fragmentHash` identifie le **bloc**. La clé de la map résultat est `fragmentHash` (= blockId). Ne jamais substituer l'un à l'autre.

**2. `reliabilityScore` est sur `Peer.identity.reliabilityScore`, PAS `Peer.reliabilityScore` :**
`Peer` ne possède pas de champ `reliabilityScore` direct — il est sur `identity: NodeIdentity`. Accès : `peer.identity.reliabilityScore`.

**3. `peerRepository.peers.value` — snapshot synchrone dans coroutine :**
`PeerRepository.peers` est un `StateFlow<List<Peer>>`. Utiliser `.value` depuis une coroutine suspendue sur `Dispatchers.IO` est thread-safe (StateFlow est thread-safe). Ne pas utiliser `.collect {}` — juste `.value`.

**4. `LookupBlockLocationUseCase` n'est pas `LocalizeFileBlocksUseCase` :**
Le `LookupBlockLocationUseCase` existant fait un lookup **simple** d'un bloc via DHT local uniquement. `LocalizeFileBlocksUseCase` est au-dessus : file-level, multi-blocs, primary path via `FragmentLocation.nodeIds`, DHT fallback, ring relay. Ne pas renommer/modifier l'ancien.

**5. Ring relay — timeout 3 secondes par nœud :**
`socket.soTimeout = 3_000` et `socket.connect(..., 3_000)`. Un nœud lent ne doit pas bloquer toute la localisation. Le relay est best-effort : si `remoteLookup()` retourne `Result.failure`, ignorer et passer au bloc suivant (résultat partiel acceptable).

**6. `handleDhtLookupRelay` — ne pas bloquer le thread accept :**
Suivre le même pattern que `handleIncomingBlockTransfer` : si `TcpConnectionManager` utilise un `connectionScope.launch(Dispatchers.IO)`, le relay se lance déjà en dehors du thread accept. Pas de `runBlocking` supplémentaire.

**7. DB version 7 — aucune migration nécessaire :**
Story 6.1 ne crée aucune nouvelle table. DB reste à version 7 (Story 5.5). Ne pas toucher à `CatalogDatabase`.

**8. `DownloadState.Locating` comme garde anti-concurrence :**
Dans `ExplorerViewModel.initiateDownload()`, vérifier `if (_downloadState.value is DownloadState.Locating) return` **avant** de lancer le job. Pattern identique à `if (_storeState.value is StoreState.InProgress) return`.

**9. Protobuf — valeurs par défaut obligatoires :**
`DhtLookupRequestMessage` et `DhtLookupResponseMessage` doivent avoir des valeurs par défaut sur tous les champs (`= ""`/ `= 0` / `= false`) — requis par `kotlinx.serialization.protobuf` pour `ignoreUnknownKeys=true` (convention MobiCloud établie dès Story 1.1).

**10. `onDownload` nullable dans `CatalogEntryCard` :**
Garder `onDownload: ((String) -> Unit)? = null` nullable pour préserver la compatibilité des previews Compose existants qui appellent `CatalogEntryCard` sans ce paramètre.

### 📁 Arborescence cible après implémentation

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   ├── models/
│   │   ├── ResolvedBlockLocation.kt                     ← NOUVEAU
│   │   ├── DhtLookupRequestMessage.kt                   ← NOUVEAU
│   │   └── DhtLookupResponseMessage.kt                  ← NOUVEAU
│   ├── repository/
│   │   └── DhtRepository.kt                             ← MODIFIÉ (+remoteLookup)
│   └── usecase/
│       └── m05_dht_catalog/
│           └── LocalizeFileBlocksUseCase.kt             ← NOUVEAU
├── data/
│   ├── repository/
│   │   └── DhtRepositoryImpl.kt                         ← MODIFIÉ (+remoteLookup TCP)
│   └── p2p/tcp/
│       ├── BlockTransferChannel.kt                      ← MODIFIÉ (+0x30/0x31)
│       └── TcpConnectionManager.kt                      ← MODIFIÉ (+dhtRelayHandler, +handleDhtLookupRelay)
├── data/network/service/
│   └── MobicloudP2PService.kt                           ← MODIFIÉ (+dhtRepository injecté, +dhtRelayHandler)
└── presentation/explorer/
    ├── DownloadState.kt                                  ← NOUVEAU
    ├── ExplorerViewModel.kt                             ← MODIFIÉ (+localizeFileBlocksUseCase, +initiateDownload, +downloadState)
    ├── ExplorerScreen.kt                                ← MODIFIÉ (+downloadState collector, +onDownload callback)
    └── components/
        └── CatalogEntryCard.kt                          ← MODIFIÉ (+onDownload param, +bouton Télécharger)

app/src/test/kotlin/com/mobicloud/
└── domain/usecase/m05_dht_catalog/
    └── LocalizeFileBlocksUseCaseTest.kt                 ← NOUVEAU (5 tests JVM)
```

### 🔗 Dépendances inter-stories

- **Story 4.1 (done) → Story 6.1 :** `DhtRepository.findByBlockId()`, `DhtRepositoryImpl`, `DhtDao`, `DhtEntry` — fondations de la DHT locale.
- **Story 4.4 (done) → Story 6.1 :** `CatalogRepository`, `CatalogEntry`, `FragmentLocation` avec `nodeIds` populés par Story 5.3.
- **Story 5.3 (done) → Story 6.1 :** La distribution (5.3) a peuplé `FragmentLocation.nodeIds` et inséré les entrées DHT. Story 6.1 requiert que les blocs soient bien distribués pour que les paths PRIMARY et DHT FALLBACK fonctionnent.
- **Story 5.5 (done) → Story 6.1 :** `BlockTransferChannel` avec 0x20/21/22, pattern `handler` dans `TcpConnectionManager`, pattern wiring dans `MobicloudP2PService`. DB à version 7.
- **Story 6.1 → Story 6.2 :** `DownloadState.Located.blockMap: Map<String, ResolvedBlockLocation>` est exactement ce dont Story 6.2 a besoin pour démarrer les K+2 téléchargements TCP parallèles.
- **Story 6.1 → Story 6.3 :** `CatalogEntry.wrappedMasterKey` (déjà dans le modèle) sera nécessaire pour le déchiffrement AES-256 GCM en Story 6.3. Story 6.1 ne le consomme pas mais doit le transmettre (passer `entry.wrappedMasterKey` dans le `DownloadState.Located` si besoin — à envisager en Story 6.2 review).

### 🧪 Testing Requirements

**5 tests JVM purs** — pas de Robolectric, pas d'émulateur Android.

Mocks necessaires :
- `mockk<CatalogRepository>()` — `coEvery { getEntry(fileHash) } returns Result.success(catalogEntry)` / `returns Result.success(null)`
- `mockk<DhtRepository>()` — `coEvery { findByBlockId(any()) } returns Result.success(null)` / `returns Result.success(dhtEntry)`; `coEvery { remoteLookup(any(), any(), any()) } returns Result.success(null)`
- `mockk<PeerRepository>()` — `every { peers } returns MutableStateFlow(listOf(peer1, peer2))`

Builder helper pour les tests :
```kotlin
fun buildPeer(nodeId: String, score: Float, ip: String = "192.168.1.1", port: Int = 9000, active: Boolean = true) =
    Peer(identity = NodeIdentity(nodeId, ByteArray(0), score), lastSeenTimestampMs = 0L, ipAddress = ip, port = port, isActive = active)

fun buildCatalogEntry(fileHash: String, fragments: List<FragmentLocation>) =
    CatalogEntry(fileHash, "ownerHash", System.currentTimeMillis(), fragments)

fun buildFragmentLocation(index: Int, fragmentHash: String, nodeIds: List<String>) =
    FragmentLocation(index, fragmentHash, nodeIds)
```

### 📚 Références patterns

- [BlockTransferChannel.kt](../../app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferChannel.kt) — constantes 0x20/21/22 à étendre avec 0x30/31
- [TcpConnectionManager.kt](../../app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt) — pattern `blockReceiverHandler` + `handleIncomingConnection()` `when` à reproduire pour `dhtRelayHandler`
- [MobicloudP2PService.kt](../../app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt) — assignation handler avant `startServer()` (pattern story 5.5)
- [DhtRepositoryImpl.kt](../../app/src/main/kotlin/com/mobicloud/data/repository/DhtRepositoryImpl.kt) — `runCatching { withContext(Dispatchers.IO) { ... } }` pattern
- [ConsistentHashRing.kt](../../app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/ConsistentHashRing.kt) — `ConsistentHashRing(nodeIds).getPartition(blockId)` → String (nodeId responsable)
- [ExplorerViewModel.kt](../../app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt) — pattern `storeFile()` guard + `MutableStateFlow` + `viewModelScope.launch` à reproduire pour `initiateDownload()`
- [CatalogEntryCard.kt](../../app/src/main/kotlin/com/mobicloud/presentation/explorer/components/CatalogEntryCard.kt) — UI existante à étendre (ne pas casser `availabilityState()` ni les styles)
- [Source: epics.md#Story 6.1] — AC et user story

## Previous Story Intelligence

**Learnings critiques de Story 5.5 (Réception & Hébergement) :**

- **DB version 7** : `CatalogDatabase` est à version 7 depuis Story 5.5. Ne pas réincrémenter, aucune nouvelle table.
- **`blockReceiverHandler` pattern** : `TcpConnectionManager` utilise des champs optionnels (handler? = null) assignés avant `startServer()`. Reproduire exactement pour `dhtRelayHandler`.
- **`runBlocking` dans handler TCP** : déféré en story 5.5 (cohérent avec handlers Gossip). Story 6.1 doit utiliser `connectionScope.launch(Dispatchers.IO)` comme pattern standard, pas `runBlocking`.
- **`connectionScope.launch` anti-DoS** : depuis story 5.5, chaque connexion entrante est traitée dans `connectionScope.launch(Dispatchers.IO)` pour éviter de bloquer le thread accept. Le handler `handleDhtLookupRelay` suivra ce patron.
- **Écriture atomique `.tmp` + rename** : pattern de story 5.5 pour les fichiers — non applicable ici (pas d'écriture disque), mais le principe "operation atomique" s'applique à la réponse TCP.
- **Regex validation `blockId`** : story 5.5 a ajouté `^[0-9a-f]{64}$` sur les blockId entrants. Story 6.1 reçoit des `blockId` via réseau dans `DhtLookupRequestMessage` — valider avec la même regex avant de faire la lookup DHT.
- **Domain separation sur signatures** : story 5.5 a ajouté `MOBICLOUD_BLOCK_ACK_v1|...` prefix. Story 6.1 n'a pas de signature dans le DHT lookup, mais si une future Story ajoute une auth sur le relay, suivre ce pattern.

**Learnings de Stories 4.x (DHT/Gossip) :**

- **`DhtEntry` vs `FragmentLocation`** : Deux sources différentes pour la même information. `FragmentLocation.nodeIds` est la liste "plan de distribution" peuplée à la création. `DhtEntry` est la vue "temps réel" via Gossip. En cas de divergence, préférer le pair qui répond le plus vite (Story 6.2) — pour Story 6.1, préférer `FragmentLocation.nodeIds` + PeerRegistry (PRIMARY) comme source la plus à jour.
- **Convergence CRDT ≤ 3s** : La DHT est supposée converger en ≤ 3s (NFR-01). Si un bloc est récemment distribué (< 3s), la DHT locale peut ne pas encore avoir l'entrée. Le PRIMARY path via `FragmentLocation.nodeIds` évite ce délai.
- **`ConsistentHashRing` — nœuds qualifiés uniquement** : Passer uniquement les nodeIds des peers `isActive=true` au constructeur. Ne pas inclure les peers `isActive=false` dans l'anneau — ils ne répondront pas au relay.

## NFR Compliance

**NFR-03 (CPU ≤ 5%) :** Localisation = lookups Room + PeerRegistry snapshot. Pas de calcul lourd. Ring relay = 1 socket TCP par bloc manquant (3s timeout max). Overhead négligeable.

**NFR-01 (Convergence CRDT ≤ 3s) :** Le PRIMARY path via `FragmentLocation.nodeIds` + PeerRegistry est indépendant de la convergence DHT — il utilise l'information de distribution d'origine, pas la DHT propagée. Pour les blocs migrés (Epic 7), le DHT fallback + ring relay couvre la période post-migration.

**Sécurité** : `DhtLookupRequestMessage` ne contient que `blockId` (hash SHA-256 du ciphertext). Zéro information sur le contenu ou le propriétaire. Aucune clé transmise. Conforme Zero-Trust.

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Aucun blocage majeur. Correction mineure : `handleIncomingConnection` rendue `suspend` pour permettre l'appel de `handleDhtLookupRelay` (suspend) sans `runBlocking` supplémentaire — cohérent avec le pattern `connectionScope.launch` existant. Correction logique DHT fallback : `DhtEntry` a son propre ip/port, le cross-check peer n'est que pour le reliabilityScore.

### Completion Notes List

- Créé 3 nouveaux modèles domaine : `ResolvedBlockLocation`, `DhtLookupRequestMessage`, `DhtLookupResponseMessage`
- Étendu `DhtRepository` + `DhtRepositoryImpl` avec `remoteLookup()` TCP (socket 3s timeout, protobuf)
- Ajouté constantes 0x30/0x31 dans `BlockTransferChannel`
- Ajouté `dhtRelayHandler` + `handleDhtLookupRelay()` dans `TcpConnectionManager` (suspend, pattern identique `blockReceiverHandler`)
- Câblé `dhtRelayHandler = dhtRepository` dans `MobicloudP2PService` avant `startServer()`
- Créé `LocalizeFileBlocksUseCase` : PRIMARY (PeerRegistry) → DHT FALLBACK (Room) → RING RELAY (TCP 0x30/0x31)
- Créé `DownloadState` sealed class (Idle/Locating/Located/Error)
- Modifié `ExplorerViewModel` : injection `LocalizeFileBlocksUseCase`, `initiateDownload()` avec guard anti-concurrence
- Modifié `CatalogEntryCard` : param `onDownload` nullable, bouton "↓" conditionnel (non-DEGRADE), couleur badgeColor
- Modifié `ExplorerScreen` : collecte `downloadState`, Snackbar Located/Error, callback `onDownload`
- 5 tests JVM `LocalizeFileBlocksUseCaseTest` : 5/5 ✅ (primary, tie-breaking, dht-fallback, file-not-found, partial-result)
- Mis à jour `ErasureProgressViewModelTest` + `ExplorerViewModelTest` pour passer le nouveau paramètre `localizeFileBlocksUseCase`

### File List

app/src/main/kotlin/com/mobicloud/domain/models/ResolvedBlockLocation.kt (NOUVEAU)
app/src/main/kotlin/com/mobicloud/domain/models/DhtLookupRequestMessage.kt (NOUVEAU)
app/src/main/kotlin/com/mobicloud/domain/models/DhtLookupResponseMessage.kt (NOUVEAU)
app/src/main/kotlin/com/mobicloud/domain/repository/DhtRepository.kt (MODIFIÉ)
app/src/main/kotlin/com/mobicloud/data/repository/DhtRepositoryImpl.kt (MODIFIÉ)
app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferChannel.kt (MODIFIÉ)
app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt (MODIFIÉ)
app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt (MODIFIÉ)
app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/LocalizeFileBlocksUseCase.kt (NOUVEAU)
app/src/main/kotlin/com/mobicloud/presentation/explorer/DownloadState.kt (NOUVEAU)
app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt (MODIFIÉ)
app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt (MODIFIÉ)
app/src/main/kotlin/com/mobicloud/presentation/explorer/components/CatalogEntryCard.kt (MODIFIÉ)
app/src/test/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/LocalizeFileBlocksUseCaseTest.kt (NOUVEAU)
app/src/test/kotlin/com/mobicloud/presentation/explorer/ErasureProgressViewModelTest.kt (MODIFIÉ)
app/src/test/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModelTest.kt (MODIFIÉ)

## Change Log

- 2026-04-22 : Implémentation complète Story 6.1 — LocalizeFileBlocksUseCase (PRIMARY/DHT FALLBACK/RING RELAY), messages Protobuf DHT (0x30/0x31), handler relay TCP, bouton Télécharger UI, 5 tests JVM (5/5 ✅)
