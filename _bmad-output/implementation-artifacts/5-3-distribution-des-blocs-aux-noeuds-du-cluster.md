# Story 5.3: Distribution des Blocs aux Nœuds du Cluster

Status: done

## Story

En tant qu'utilisateur,
Je veux stocker un fichier dans le réseau distribué depuis l'Explorer,
Afin que ses blocs chiffrés soient distribués automatiquement aux nœuds disponibles du cluster via sockets TCP directs.

## Acceptance Criteria

1. **Given** l'utilisateur sélectionne un fichier et appuie sur "Stocker" dans l'Explorer
   **When** la distribution est déclenchée
   **Then** le fichier est encodé en K+N blocs chiffrés (Stories 5.1 + 5.2)

2. **And** le nœud local assigne un nœud destination par bloc en round-robin sur les nœuds `isActive` de la `PeerRegistry` (`peerRepository.peers.value.filter { it.isActive && it.ipAddress != null && it.port != null }`)

3. **And** chaque bloc est transmis via socket TCP direct (pas de routage multi-sauts) avec le protocole `BLOCK_TRANSFER` (discriminator `0x20`)

4. **And** le nœud destinataire confirme la réception avec un `BlockAckMessage` signé contenant le hash SHA-256 du bloc

5. **And** si un nœud est indisponible (timeout ACK : base 10s, retry 30s), un nœud de remplacement est sélectionné automatiquement parmi les pairs non encore utilisés

6. **And** après distribution réussie (≥ K confirmations), une `CatalogEntry` est insérée localement puis diffusée via `GossipSyncUseCase.runGossipCycle()`

7. **And** en cas d'échec partiel (< K confirmations), l'opération retourne `Result.failure` et l'utilisateur est notifié via `Snackbar` dans l'Explorer

## Tasks / Subtasks

- [x] Task 0 : Pré-requis — Enrichir `CatalogEntry` pour stocker la `WrappedFileMasterKey` (requis par Story 6.3)
  - [x] Subtask 0.1 : Dans `domain/models/CatalogEntry.kt`, ajouter le champ optionnel :
    ```kotlin
    @ProtoNumber(5) val wrappedMasterKey: WrappedFileMasterKey? = null
    ```
    **Remarque :** `WrappedFileMasterKey` est déjà `@Serializable` (Story 5.2). Ce champ est absent lors de la réception Gossip (backward compatible via `= null`).
  - [x] Subtask 0.2 : Dans `data/local/entity/CatalogEntryEntity.kt`, ajouter :
    ```kotlin
    @ColumnInfo(name = "wrapped_master_key_json") val wrappedMasterKeyJson: String? = null
    ```
  - [x] Subtask 0.3 : Mettre à jour `CatalogRepositoryImpl` — mappers `toEntity()` / `toDomain()` pour sérialiser/désérialiser `wrappedMasterKeyJson` via `json.encodeToString(WrappedFileMasterKey.serializer(), it)`.
  - [x] Subtask 0.4 : Incrémenter la version Room DB dans `CatalogDatabase.kt` (`version = 6`) et ajouter une migration `MIGRATION_5_6` (`addColumn`). Enregistrée dans `IdentityModule`.

- [x] Task 1 : Protocole TCP Block Transfer — constantes et modèles
  - [x] Subtask 1.1 : Créer `data/p2p/tcp/BlockTransferChannel.kt` (constantes discriminators + timeouts)
  - [x] Subtask 1.2 : Créer `domain/models/BlockTransferMessage.kt` (`@Serializable` `@ProtoNumber`)
  - [x] Subtask 1.3 : Créer `domain/models/BlockAckMessage.kt` (`@Serializable` `@ProtoNumber`)

- [x] Task 2 : `BlockTransferClient` — client TCP sortant (AC: #3, #4, #5)
  - [x] Subtask 2.1 : Créer interface `domain/repository/BlockSender.kt` (pattern GossipOutboundPort)
  - [x] Subtask 2.2 : Créer `data/p2p/tcp/BlockTransferClient.kt` implémentant `BlockSender` (`@Singleton @Inject constructor(SecurityRepository)`) — pattern identique à `GossipChannel.kt`

- [x] Task 3 : `DistributeEncryptedBlocksUseCase` (AC: #2–#7)
  - [x] Subtask 3.1 : Créer `domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` avec injections `PeerRepository`, `BlockSender`, `CatalogRepository`, `GossipSyncUseCase`, `SecurityRepository`, `InsertDhtEntryUseCase`
  - [x] Subtask 3.2 : Signature `suspend fun distribute(encryptedBundle, fileHash, k): Result<CatalogEntry>` dans `withContext(Dispatchers.IO)`
  - [x] Subtask 3.3 : Récupérer pairs actifs (filtre `isActive && ipAddress != null && port != null`)
  - [x] Subtask 3.4 : Récupérer identité locale via `securityRepository.getIdentity()`
  - [x] Subtask 3.5 : Helper interne `sha256Hex(bytes: ByteArray): String`
  - [x] Subtask 3.6 : Round-robin assignment `i % activePeers.size`
  - [x] Subtask 3.7 : Construction des `BlockTransferMessage` pour chaque fragment
  - [x] Subtask 3.8 : Envoi avec timeout adaptatif (10s primaire → 30s fallback)
  - [x] Subtask 3.9 : Collecter les succès via `DeliveryRecord`
  - [x] Subtask 3.10 : Vérifier seuil K blocs de données confirmés
  - [x] Subtask 3.11 : Insérer entrées DHT via `InsertDhtEntryUseCase`
  - [x] Subtask 3.12 : Construire `CatalogEntry` avec `wrappedMasterKey`
  - [x] Subtask 3.13 : Insérer via `catalogRepository.insertOwnerEntry(catalogEntry)` (Option B — bypass filtre DHT, car nodeId=16 chars ≠ fileHash=64 chars)
  - [x] Subtask 3.14 : Propager via `gossipSyncUseCase.runGossipCycle()`
  - [x] Subtask 3.15 : Retourner `Result.success(catalogEntry)`

- [x] Task 4 : Intégration Hilt
  - [x] Subtask 4.1 : Créer `di/BlockTransferModule.kt` — binding `BlockSender → BlockTransferClient`
  - [x] Subtask 4.2 : Créer `core/erasure/ErasureModule.kt` — binding `ErasureCodec → ErasureCodingJni` (requis par `EncodeErasureFragmentsUseCase` injecté dans `ExplorerViewModel`)

- [x] Task 5 : Intégration UI — Explorer "Stocker" (AC: #1, #7)
  - [x] Subtask 5.1 : Créer `presentation/explorer/StoreState.kt` (sealed class : Idle, Loading, Success, Error)
  - [x] Subtask 5.2 : Dans `ExplorerViewModel.kt`, ajouter injections + `_storeState: MutableStateFlow<StoreState>`
  - [x] Subtask 5.3 : Implémenter `fun storeFile(uri: Uri)` — lit le fichier via ContentResolver → fichier temp → encode → chiffre → distribue
  - [x] Subtask 5.4 : Helper `sha256Hex(bytes: ByteArray): String` dans `ExplorerViewModel`
  - [x] Subtask 5.5 : Dans `ExplorerScreen.kt`, ajouter `storeLauncher` (GetContent ActivityResult)
  - [x] Subtask 5.6 : Envelopper dans `Scaffold` avec `FloatingActionButton` (icône Upload)
  - [x] Subtask 5.7 : Observer `storeState` via `LaunchedEffect` pour afficher `Snackbar`
  - [x] Subtask 5.8 : `SnackbarHost(snackbarHostState)` dans le `Scaffold`

- [x] Task 6 : Tests unitaires JVM (AC: #2–#7)
  - [x] Subtask 6.1 : Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCaseTest.kt`
  - [x] Subtask 6.2 : Framework : `kotlinx-coroutines-test` + `mockk:1.13.8`, `runTest { }` pour toutes les coroutines
  - [x] Subtask 6.3 : Helpers de test : `fakeEncryptedFragment`, `fakePeer`, `fakeBundle`, `fakeAck`
  - [x] Subtask 6.4 : Test 1 — Happy path K+N=6 confirmations ; vérifie `insertOwnerEntry` ×1, `runGossipCycle` ×1, `InsertDhtEntryUseCase` ×6
  - [x] Subtask 6.5 : Test 2 — Timeout + retry réussi sur pair de remplacement
  - [x] Subtask 6.6 : Test 3 — < K confirmations → `Result.failure`, `insertOwnerEntry` non appelé
  - [x] Subtask 6.7 : Test 4 — Aucun pair actif → `Result.failure(IllegalStateException)` immédiat
  - [x] Subtask 6.8 : Test 5 — `CatalogEntry` contient bien `wrappedMasterKey` du bundle

### Review Findings

- [x] [Review][Patch] File I/O (openInputStream + writeBytes) sur Dispatchers.Main dans `storeFile()` [ExplorerViewModel.kt:60,72]
- [x] [Review][Patch] Import inutilisé `BASE_ACK_TIMEOUT_MS` dans `BlockTransferClient.kt` [BlockTransferClient.kt:4]
- [x] [Review][Patch] Silent catch sur `decodeFromString` — données corrompues silencieusement nullifiées [CatalogRepositoryImpl.kt:71-76]
- [x] [Review][Patch] `!!` assertions sur `peer.ipAddress!!` et `peer.port!!` dans `DistributeEncryptedBlocksUseCase` [DistributeEncryptedBlocksUseCase.kt:114]
- [x] [Review][Patch] Aucun guard contre invocations concurrentes de `storeFile()` — double tap FAB → entrées dupliquées [ExplorerViewModel.kt:57]
- [x] [Review][Defer] Constantes timeout dupliquées entre `BlockTransferChannel` et companion object du use case — intentionnel per spec contrainte #7 (pas d'import `data/` dans le domaine) — deferred, pre-existing
- [x] [Review][Defer] Fallback peer peut être déjà assigné en primaire pour un autre bloc — spec AC#5 = retry par bloc uniquement, comportement conforme — deferred, pre-existing

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Contenu clé | Action |
|---|---|---|
| `core/security/FragmentCipherUseCase.kt` | `encrypt(fragments: List<ErasureFragment>, recipientPublicKeyBytes: ByteArray): Result<EncryptedBundle>` | **CONSOMMER** — passer `localIdentity.publicKeyBytes` comme recipientPublicKeyBytes |
| `domain/models/EncryptedBundle.kt` | `encryptedFragments: List<EncryptedFragment>`, `wrappedFileMasterKey: WrappedFileMasterKey` | **CONSOMMER tel quel** |
| `domain/models/EncryptedFragment.kt` | `index, isParity, ciphertext: ByteArray, iv: ByteArray, originalFileSize: Long` | **SOURCE de `ciphertext` et `iv`** pour `BlockTransferMessage` |
| `domain/models/WrappedFileMasterKey.kt` | `ephemeralPublicKeyBytes, iv, encryptedKey` — `@Serializable` | **STOCKER dans `CatalogEntry.wrappedMasterKey`** (Task 0) |
| `domain/models/CatalogEntry.kt` | `@Serializable`, `fileHash, ownerPubKeyHash, versionClock, fragmentLocations` | **MODIFIER** — ajouter `wrappedMasterKey: WrappedFileMasterKey? = null` |
| `domain/models/FragmentLocation.kt` | `fragmentIndex: Int, fragmentHash: String, nodeIds: List<String>` | **`fragmentHash` = blockId = SHA-256 hex du ciphertext** |
| `domain/models/Peer.kt` | `identity: NodeIdentity, ipAddress: String?, port: Int?, isActive: Boolean` | **FILTRER** sur `isActive && ipAddress != null && port != null` |
| `domain/models/NodeIdentity.kt` | `nodeId: String, publicKeyBytes: ByteArray` | **`publicKeyBytes`** = clé publique X.509 SubjectPublicKeyInfo (~65 bytes P-256) |
| `domain/repository/PeerRepository.kt` | `val peers: StateFlow<List<Peer>>` | **LIRE `.value`** pour la liste synchrone des pairs (pas de collect nécessaire) |
| `domain/repository/CatalogRepository.kt` | `insertEntry(entry, nodeId, successorId)` avec filtre DHT | **VOIR note critique ci-dessous** |
| `domain/repository/SecurityRepository.kt` | `getIdentity()`, `verifySignature(data, signature, publicKey)` | **UTILISER** pour identity locale et vérification ACK |
| `domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt` | `invoke(blockId, nodeId, ipAddress, port): Result<Unit>` | **APPELER** après chaque ACK confirmé |
| `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` | `runGossipCycle(): Result<Unit>` | **APPELER** après `insertOwnerEntry` réussie |
| `domain/usecase/m08_m09_erasure_coding/EncodeErasureFragmentsUseCase.kt` | `invoke(file: File, params: ErasureParameters): Result<List<ErasureFragment>>` | **APPELER** dans `ExplorerViewModel.storeFile()` via fichier temporaire |
| `domain/models/ErasureParameters.kt` | `K = 4, N = 2, BLOCK_SIZE_BYTES = 1 MiB` | **RÉFÉRENCER** — ne pas hardcoder d'autres valeurs |
| `data/p2p/tcp/GossipChannel.kt` | Pattern TCP client : `Socket()`, `connect()`, `DataOutputStream`, `writeByte`, `writeInt`, `write`, `flush` | **REPRODUIRE exactement** ce pattern dans `BlockTransferClient` |
| `data/p2p/tcp/TcpConnectionManager.kt` | Gère serveur + client Gossip ; discriminators : `0x01, 0x02, 0x03` | **NE PAS MODIFIER** — Story 5.3 crée `BlockTransferClient` séparé |
| `core/format/ProtoBufSerializer.kt` | `MobiCloudProtoBuf.encodeToByteArray(...)` / `decodeFromByteArray(...)` | **RÉUTILISER** pour sérialisation `BlockTransferMessage` / `BlockAckMessage` |
| `presentation/explorer/ExplorerScreen.kt` | `PullToRefreshBox` + `LazyColumn` + `CatalogEntryCard` | **MODIFIER** — ajouter `Scaffold` + FAB + `SnackbarHost` |
| `presentation/explorer/ExplorerViewModel.kt` | `catalogEntries: StateFlow`, `refreshCatalog()` | **MODIFIER** — ajouter `storeFile()` + `storeState` |
| `data/repository_impl/CatalogRepositoryImpl.kt` | Mappers entity ↔ domain | **MODIFIER** — ajouter sérialisation `wrappedMasterKeyJson` (Task 0.3) |

### ⚠️ CONTRAINTES CRITIQUES

**1. Byte discriminators TCP — ne pas confliciter :**
```
GossipChannel (EXISTANT — NE PAS RÉUTILISER) :
  GOSSIP_BLOOM      = 0x01
  GOSSIP_DELTA_REQ  = 0x02
  GOSSIP_DELTA_RESP = 0x03

BlockTransferChannel (NOUVEAU Story 5.3) :
  BLOCK_TRANSFER    = 0x20   // Client → Serveur
  BLOCK_ACK         = 0x21   // Serveur → Client (succès)
  BLOCK_NACK        = 0x22   // Serveur → Client (rejet)
```

**2. `CatalogRepository.insertEntry` — filtre DHT critique :**
`nodeId` = 16 chars (SHA-256 tronqué), `fileHash` = 64 chars → lengths différentes → `CalculateDhtRangeUseCase` lève `IllegalArgumentException`. **Option B implémentée** : ajout de `insertOwnerEntry(entry: CatalogEntry): Result<Unit>` dans `CatalogRepository` + `CatalogRepositoryImpl` (contourne le filtre DHT pour le propriétaire).

**3. `recipientPublicKeyBytes` dans `FragmentCipherUseCase.encrypt()` :**
Passer `localIdentity.publicKeyBytes` comme `recipientPublicKeyBytes`. La clé privée correspondante sera utilisée par Story 6.3.

**4. `EncodeErasureFragmentsUseCase` prend un `File`, pas un `ByteArray` :**
Dans `ExplorerViewModel.storeFile()`, les bytes du fichier sont écrits dans un fichier temporaire (`context.cacheDir`) avant d'appeler `encodeErasureFragmentsUseCase(tempFile, ErasureParameters())`. Le fichier temp est supprimé dans le bloc `finally`.

**5. `ErasureCodec` n'était pas lié dans Hilt :**
`core/erasure/ErasureModule.kt` ajouté pour lier `ErasureCodingJni` (qui est `internal object`).

**6. Côté serveur (Story 5.5) :**
Story 5.3 implémente uniquement le client TCP. Le serveur est implémenté dans Story 5.5.

**7. `DistributeEncryptedBlocksUseCase` ne doit pas importer depuis `data/` :**
Les constantes `BASE_ACK_TIMEOUT_MS` et `MAX_ACK_TIMEOUT_MS` sont définies directement dans la companion object du use case.

### 📁 Arborescence cible après implémentation

```
app/src/main/kotlin/com/mobicloud/
├── data/
│   └── p2p/
│       └── tcp/
│           ├── TcpConnectionManager.kt               ← INCHANGÉ
│           ├── GossipChannel.kt                      ← INCHANGÉ
│           ├── BlockTransferClient.kt                ← NOUVEAU (@Singleton)
│           └── BlockTransferChannel.kt               ← NOUVEAU (constantes protocole)
├── domain/
│   ├── models/
│   │   ├── CatalogEntry.kt                           ← MODIFIÉ (+ wrappedMasterKey)
│   │   ├── WrappedFileMasterKey.kt                   ← MODIFIÉ (+ @Serializable @ProtoNumber)
│   │   ├── BlockTransferMessage.kt                   ← NOUVEAU (@Serializable @ProtoNumber)
│   │   └── BlockAckMessage.kt                        ← NOUVEAU (@Serializable @ProtoNumber)
│   ├── repository/
│   │   ├── BlockSender.kt                            ← NOUVEAU (interface domaine)
│   │   └── CatalogRepository.kt                     ← MODIFIÉ (+ insertOwnerEntry)
│   └── usecase/
│       └── m08_m09_erasure_coding/
│           └── DistributeEncryptedBlocksUseCase.kt   ← NOUVEAU
├── data/
│   └── repository_impl/
│       └── CatalogRepositoryImpl.kt                  ← MODIFIÉ (mapper wrappedMasterKey + insertOwnerEntry)
│   └── local/
│       └── entity/
│           └── CatalogEntryEntity.kt                 ← MODIFIÉ (+ wrappedMasterKeyJson)
│       └── CatalogDatabase.kt                        ← MODIFIÉ (version=6 + MIGRATION_5_6)
├── core/
│   └── erasure/
│       └── ErasureModule.kt                          ← NOUVEAU (Hilt binding)
├── di/
│   └── BlockTransferModule.kt                        ← NOUVEAU (Hilt binding BlockSender)
│   └── IdentityModule.kt                             ← MODIFIÉ (MIGRATION_5_6 enregistrée)
└── presentation/
    └── explorer/
        ├── ExplorerScreen.kt                         ← MODIFIÉ (Scaffold + FAB + Snackbar)
        ├── ExplorerViewModel.kt                      ← MODIFIÉ (storeFile + storeState)
        └── StoreState.kt                             ← NOUVEAU (sealed class)

app/src/test/kotlin/com/mobicloud/
├── domain/models/
│   └── CatalogEntryTest.kt                           ← MODIFIÉ (@ProtoNumber 5→6 pour test champ futur)
├── domain/usecase/m08_m09_erasure_coding/
│   └── DistributeEncryptedBlocksUseCaseTest.kt       ← NOUVEAU (5 tests JVM)
└── presentation/explorer/
    └── ExplorerViewModelTest.kt                      ← MODIFIÉ (nouveaux mocks pour paramètres)
```

### 🎯 Contraintes Non-Négociables

- **TCP direct uniquement :** `Socket()` direct vers `peer.ipAddress:peer.port` — jamais de relais.
- **Zero-Trust :** `BlockTransferMessage` contient `ciphertext` + `iv` uniquement — jamais de `FileMasterKey` en clair.
- **ACK signé obligatoire :** Si `verifySignature()` retourne `false` → bloc non compté.
- **< K confirmations = annulation :** Si les blocs de données confirmés sont < K, retourner `Result.failure`.
- **Interdit :** Modifier `TcpConnectionManager.kt` pour Story 5.3.
- **Interdit :** Utiliser `runBlocking` dans les use cases — utiliser `withContext(Dispatchers.IO)`.

### 🔗 Intégration avec les Stories Adjacentes

- **Story 5.1 (done) → Story 5.3 :** `EncodeErasureFragmentsUseCase.invoke(file, ErasureParameters())`
- **Story 5.2 (done) → Story 5.3 :** `FragmentCipherUseCase.encrypt(fragments, localIdentity.publicKeyBytes)`
- **Story 5.3 → Story 5.5 (Réception) :** Protocole binaire défini ici (Task 1) doit être respecté exactement.
- **Story 5.3 → Story 6.1 (Lookup DHT) :** `InsertDhtEntryUseCase` peuple la DHT locale.
- **Story 5.3 → Story 6.3 (Déchiffrement) :** `CatalogEntry.wrappedMasterKey` utilisé pour unwrapper la `FileMasterKey`.

### 📚 Références

- [GossipChannel.kt](../../app/src/main/kotlin/com/mobicloud/data/p2p/tcp/GossipChannel.kt) — Pattern TCP client à reproduire exactement
- [CatalogRepositoryImpl.kt](../../app/src/main/kotlin/com/mobicloud/data/repository_impl/CatalogRepositoryImpl.kt) — Mappers entity ↔ domain étendus

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- **Subtask 3.13** : Option A (`insertEntry(nodeId, nodeId)`) invalide car `nodeId` = 16 chars ≠ `fileHash` = 64 chars → `CalculateDhtRangeUseCase` lève `require` exception. Option B implémentée : `insertOwnerEntry`.
- **Subtask 4.2** : `ErasureCodingJni` est `internal object` → module Hilt créé dans `core.erasure` package pour accéder à la visibilité interne.
- **Test CatalogEntryTest** : `@ProtoNumber(5)` maintenant occupé par `wrappedMasterKey` → test mis à jour pour utiliser `@ProtoNumber(6)`.
- **ExplorerViewModelTest** : constructor mis à jour avec les nouveaux mocks.
- **`EncodeErasureFragmentsUseCase`** : prend `File` (pas `ByteArray`) → utilisation d'un fichier temporaire dans `storeFile()`.

### Completion Notes List

- Task 0 : `WrappedFileMasterKey` annotée `@Serializable` + `@ProtoNumber`. `CatalogEntry` enrichie. `CatalogEntryEntity` enrichie. `CatalogRepositoryImpl` mappers mis à jour + `insertOwnerEntry` ajouté. Room DB version 5→6 + `MIGRATION_5_6`. Migration enregistrée dans `IdentityModule`.
- Task 1 : `BlockTransferChannel` (constantes), `BlockTransferMessage`, `BlockAckMessage` créés.
- Task 2 : Interface `BlockSender` (domain) + `BlockTransferClient` (data, pattern GossipChannel).
- Task 3 : `DistributeEncryptedBlocksUseCase` — pipeline complet encode→distribute avec retry et seuil K.
- Task 4 : `BlockTransferModule` (BlockSender binding) + `ErasureModule` (ErasureCodec binding).
- Task 5 : `StoreState`, `ExplorerViewModel` + `ExplorerScreen` — Scaffold + FAB Upload + Snackbar.
- Task 6 : 5 tests JVM — happy path, retry, < K blocs, no peers, wrappedMasterKey présent.
- Résultat tests : **136 tests, 0 failures** (incluant régression suite complète).

### File List

**Nouveaux fichiers :**
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferChannel.kt`
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferClient.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/BlockTransferMessage.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/BlockAckMessage.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/BlockSender.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/di/BlockTransferModule.kt`
- `app/src/main/kotlin/com/mobicloud/core/erasure/ErasureModule.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/StoreState.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCaseTest.kt`

**Fichiers modifiés :**
- `app/src/main/kotlin/com/mobicloud/domain/models/CatalogEntry.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/WrappedFileMasterKey.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/CatalogRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/CatalogEntryEntity.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository_impl/CatalogRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/IdentityModule.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt`
- `app/src/test/kotlin/com/mobicloud/domain/models/CatalogEntryTest.kt`
- `app/src/test/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModelTest.kt`

## Change Log

- Story 5.3 créée par bmad-create-story (Date: 2026-04-21)
- Story 5.3 implémentée par claude-sonnet-4-6 (Date: 2026-04-21) — pipeline distribution TCP complet, 5 tests JVM, 0 régression
