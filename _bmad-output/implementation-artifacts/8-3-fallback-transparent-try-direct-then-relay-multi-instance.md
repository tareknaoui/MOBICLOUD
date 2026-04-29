# Story 8.3: Fallback Transparent Try-Direct-Then-Relay (Multi-Instance)

Status: done

## Story

En tant qu'utilisateur,
Je veux que l'application choisisse automatiquement le meilleur chemin de transfert (TCP direct ou Relais HA),
Afin que l'expérience de stockage reste fluide quel que soit mon environnement réseau.

## Acceptance Criteria

1. **Given** un transfert de bloc est déclenché par `DistributeEncryptedBlocksUseCase` (Story 5.3) ou `ExecuteMigrationPlanUseCase` (Story 7.2)
   **When** le `BlockSender` (= `BlockSenderWithRelay`) tente d'envoyer le bloc
   **Then** il tente d'abord une connexion TCP directe via `BlockTransferClient` (Priorité 1)

2. **And** en cas d'échec TCP (`IOException`, `SocketTimeoutException`), il bascule automatiquement sur `RelayRepository.uploadBlock()` via la couche HA (Priorité 2)

3. **And** en cas d'échec du Relais HA primaire, le failover multi-instance (Priorité 3) est géré automatiquement par `RelayWebSocketClient` — `BlockSenderWithRelay` n'a pas à l'implémenter lui-même

4. **And** le succès ou l'échec final est remonté au UseCase via `Result<BlockAckMessage>` sans qu'il connaisse le canal utilisé

5. **And** `DistributeEncryptedBlocksUseCase` et `ExecuteMigrationPlanUseCase` ne sont PAS modifiés (substitution transparente via `BlockTransferModule` Hilt)

6. **And** l'état du canal de transfert actif est exposé via `StateFlow<TransferChannelState>` (DIRECT / RELAY_HA / OFFLINE) consommable par le Dashboard

7. **And** le composant `CloudRelayBadge` (UX-DR10) affiche cet état dans le Dashboard avec 3 icônes distinctes (✓ direct / ☁ relay / ⚠ offline)

## Tasks / Subtasks

### 📋 Task 1 — `TransferChannelState.kt` (domaine pur)

- [x] **Task 1** : Créer `domain/models/TransferChannelState.kt`
  - [x] Subtask 1.1 : Enum dans le package `com.mobicloud.domain.models` :
    ```kotlin
    package com.mobicloud.domain.models

    enum class TransferChannelState {
        DIRECT,    // TCP P2P direct réussi
        RELAY_HA,  // Fallback via Serveurs Relais HA
        OFFLINE    // Tous les canaux ont échoué
    }
    ```
  - **Contrainte** : zéro import Android ou OkHttp — cette classe vit dans `domain/`.

---

### 🔧 Task 2 — `BlockSenderWithRelay.kt` (livrable principal)

- [x] **Task 2** : Créer `data/p2p/BlockSenderWithRelay.kt`

  - [x] Subtask 2.1 : Déclaration de classe + constructeur :
    ```kotlin
    package com.mobicloud.data.p2p

    import com.mobicloud.core.format.MobiCloudProtoBuf
    import com.mobicloud.data.p2p.tcp.BlockTransferClient
    import com.mobicloud.domain.models.BlockAckMessage
    import com.mobicloud.domain.models.BlockTransferMessage
    import com.mobicloud.domain.models.Peer
    import com.mobicloud.domain.models.TransferChannelState
    import com.mobicloud.domain.repository.BlockSender
    import com.mobicloud.domain.repository.RelayRepository
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.serialization.ExperimentalSerializationApi
    import java.io.IOException
    import java.net.SocketTimeoutException
    import javax.inject.Inject
    import javax.inject.Singleton

    @Singleton
    class BlockSenderWithRelay @Inject constructor(
        private val tcpSender: BlockTransferClient,
        private val relayRepository: RelayRepository
    ) : BlockSender {

        private val _transferChannelState = MutableStateFlow(TransferChannelState.DIRECT)
        val transferChannelState: StateFlow<TransferChannelState> = _transferChannelState.asStateFlow()
    ```

  - [x] Subtask 2.2 : Implémentation de `sendBlock()` — logique Try-Direct-Then-Relay :
    ```kotlin
        @OptIn(ExperimentalSerializationApi::class)
        override suspend fun sendBlock(
            block: BlockTransferMessage,
            peer: Peer,
            timeoutMs: Long
        ): Result<BlockAckMessage> {
            // ---- Priorité 1 : TCP direct ----
            val tcpResult = tcpSender.sendBlock(block, peer, timeoutMs)
            if (tcpResult.isSuccess) {
                _transferChannelState.value = TransferChannelState.DIRECT
                return tcpResult
            }

            // N'essayer le relay que pour des erreurs réseau (pas sécurité / NACK hash)
            val tcpCause = tcpResult.exceptionOrNull()
            if (tcpCause != null && tcpCause !is IOException && tcpCause !is SocketTimeoutException) {
                _transferChannelState.value = TransferChannelState.OFFLINE
                return tcpResult
            }

            // ---- Priorité 2 : Relay HA (failover multi-instance géré par RelayWebSocketClient) ----
            val blockPayload = runCatching {
                MobiCloudProtoBuf.encodeToByteArray(BlockTransferMessage.serializer(), block)
            }.getOrElse {
                _transferChannelState.value = TransferChannelState.OFFLINE
                return Result.failure(it)
            }

            val relayResult = relayRepository.uploadBlock(
                destNodeId = peer.identity.nodeId,
                blockId = block.blockId,
                data = blockPayload
            )

            return if (relayResult.isSuccess) {
                _transferChannelState.value = TransferChannelState.RELAY_HA
                val syntheticAck = BlockAckMessage(
                    blockId = block.blockId,
                    blockHash = block.blockId,
                    receiverNodeId = peer.identity.nodeId,
                    signature = ByteArray(0)
                )
                Result.success(syntheticAck)
            } else {
                _transferChannelState.value = TransferChannelState.OFFLINE
                Result.failure(
                    IOException(
                        "Tous les canaux de transfert ont échoué — TCP: ${tcpCause?.message} ; Relay: ${relayResult.exceptionOrNull()?.message}"
                    )
                )
            }
        }
    } // fin class BlockSenderWithRelay
    ```

  - **Règle critique — ACK synthétique** :
    - `signature = ByteArray(0)` : le relay server ne peut pas produire une signature pair. `DistributeEncryptedBlocksUseCase` et `ExecuteMigrationPlanUseCase` n'utilisent que `receiverNodeId` du résultat — ils **ne vérifient pas** la signature côté appelant (la vérification est dans `BlockTransferClient.sendBlock()`, pas dans le UseCase).
    - `blockId = blockHash` : le `blockId` du `BlockTransferMessage` est déjà le sha256-hex du ciphertext (calculé dans `DistributeEncryptedBlocksUseCase.sha256Hex(frag.ciphertext)`) — réutiliser comme `blockHash` est cohérent.

  - **Règle critique — données relay** :
    - `data` = Protobuf sérialisé du `BlockTransferMessage` complet (ciphertext + iv + metadata). C'est opaque pour le relay (Zero-Knowledge). Le récepteur déserialise depuis `RelayEvent.BlockReceived.data` (Task 5).
    - Ne pas envoyer uniquement `block.ciphertext` : le pair destinataire a besoin du champ `iv`, `fragmentIndex`, `isParity`, etc. pour reconstituer le fichier via `DecodeErasureFragmentsUseCase`.

---

### 🔩 Task 3 — Hilt `BlockTransferModule` + exposition du StateFlow

- [x] **Task 3** : Modifier `di/BlockTransferModule.kt`

  - [x] Subtask 3.1 : Remplacer le binding `BlockSender` + exposer `transferChannelState` avec qualifier `@Named` :
    ```kotlin
    package com.mobicloud.di

    import com.mobicloud.data.p2p.BlockSenderWithRelay
    import com.mobicloud.data.p2p.tcp.BlockDownloadClient
    import com.mobicloud.domain.models.TransferChannelState
    import com.mobicloud.domain.repository.BlockDownloader
    import com.mobicloud.domain.repository.BlockSender
    import dagger.Module
    import dagger.Provides
    import dagger.hilt.InstallIn
    import dagger.hilt.components.SingletonComponent
    import kotlinx.coroutines.flow.StateFlow
    import javax.inject.Named
    import javax.inject.Singleton

    @Module
    @InstallIn(SingletonComponent::class)
    object BlockTransferModule {

        @Provides
        @Singleton
        fun provideBlockSender(sender: BlockSenderWithRelay): BlockSender = sender

        @Provides
        @Singleton
        @Named("transfer_channel_state")
        fun provideTransferChannelState(sender: BlockSenderWithRelay): StateFlow<TransferChannelState> =
            sender.transferChannelState

        @Provides
        @Singleton
        fun provideBlockDownloader(client: BlockDownloadClient): BlockDownloader = client
    }
    ```
  - **Attention** : `BlockTransferClient` n'est plus lié à `BlockSender` — il est injecté directement par son type concret dans le constructeur de `BlockSenderWithRelay`. Aucun conflit DI car `BlockSenderWithRelay` l'injectionne comme `BlockTransferClient` (pas via l'interface `BlockSender`).

---

### 🎨 Task 4 — `CloudRelayBadge` + intégration Dashboard (UX-DR10)

- [x] **Task 4** : Créer le composant `CloudRelayBadge` et l'intégrer dans le Dashboard

  - [x] Subtask 4.1 : Créer `presentation/dashboard/components/CloudRelayBadge.kt` :
    ```kotlin
    package com.mobicloud.presentation.dashboard.components

    import androidx.compose.foundation.layout.Row
    import androidx.compose.foundation.layout.Spacer
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.layout.size
    import androidx.compose.foundation.layout.width
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.CheckCircle
    import androidx.compose.material.icons.filled.Cloud
    import androidx.compose.material.icons.filled.Warning
    import androidx.compose.material3.Icon
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.unit.dp
    import com.mobicloud.domain.models.TransferChannelState

    @Composable
    fun CloudRelayBadge(
        state: TransferChannelState,
        modifier: Modifier = Modifier
    ) {
        val (icon, tint, label) = when (state) {
            TransferChannelState.DIRECT   -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "P2P Direct")
            TransferChannelState.RELAY_HA -> Triple(Icons.Default.Cloud,       Color(0xFF2196F3), "Relay HA")
            TransferChannelState.OFFLINE  -> Triple(Icons.Default.Warning,     Color(0xFFF44336), "Hors-ligne")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Canal de transfert : $label",
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
    ```

  - [x] Subtask 4.2 : Modifier `presentation/dashboard/DashboardViewModel.kt` — injecter et exposer le StateFlow :
    - Ajout du paramètre `@Named("transfer_channel_state") transferChannelStateFlow: StateFlow<TransferChannelState>` dans le constructeur `@HiltViewModel`.
    - Exposition : `val relayState: StateFlow<TransferChannelState> = transferChannelStateFlow`

  - [x] Subtask 4.3 : Modifier `presentation/dashboard/DashboardScreen.kt` — ajouter le badge :
    - Collecte `viewModel.relayState.collectAsStateWithLifecycle()` dans le composable.
    - Placement `CloudRelayBadge(state = relayState)` sous le badge rôle du nœud.
    - **OLED** : le fond reste `#000000` — `CloudRelayBadge` utilise des couleurs vives sur fond noir.

---

### 📥 Task 5 — Réception de blocs via Relay (complétion E2E)

- [x] **Task 5** : Étendre `RelayRepositoryImpl.kt` pour traiter les blocs entrants via `FORWARD`

  - [x] Subtask 5.1 : Injecter `ReceiveAndHostBlockUseCase` dans `RelayRepositoryImpl` :
    ```kotlin
    @Singleton
    class RelayRepositoryImpl @Inject constructor(
        private val client: RelayWebSocketClient,
        private val receiveAndHostBlockUseCase: ReceiveAndHostBlockUseCase
    ) : RelayRepository {
    ```

  - [x] Subtask 5.2 : Gérer `RelayEvent.BlockReceived` dans le flux `init {}` :
    ```kotlin
    init {
        repoScope.launch {
            client.connect(RELAY_SERVER_URLS.first()).collect { event ->
                when (event) {
                    is RelayEvent.Connected    -> _connectionState.value = RelayConnectionState.CONNECTED
                    is RelayEvent.Disconnected -> _connectionState.value = RelayConnectionState.OFFLINE
                    is RelayEvent.BlockReceived -> {
                        runCatching {
                            val blockMsg = MobiCloudProtoBuf.decodeFromByteArray(
                                BlockTransferMessage.serializer(), event.data
                            )
                            receiveAndHostBlockUseCase.receive(blockMsg)
                        }.onFailure { e ->
                            Log.w("RelayRepo", "Bloc FORWARD malformé blockId=${event.blockId.take(16)}: ${e.message}")
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
    ```

---

### 🧪 Task 6 — Tests JVM

- [x] **Task 6** : Créer `app/src/test/kotlin/com/mobicloud/data/p2p/BlockSenderWithRelayTest.kt`

  - [x] Subtask 6.1 : Test 1 — TCP réussit → état DIRECT, résultat = TCP ACK
  - [x] Subtask 6.2 : Test 2 — TCP échoue avec IOException → relay réussit → état RELAY_HA, ACK synthétique
  - [x] Subtask 6.3 : Test 3 — TCP échoue + relay échoue → état OFFLINE, Result.failure
  - [x] Subtask 6.4 : Test 4 — TCP échoue avec SecurityException (NACK signature) → relay NON essayé
  - [x] Subtask 6.5 : Test 5 — Transparence totale : appel via interface `BlockSender`, résultat identique

  - **Framework** : Kotlin `MockK` + `kotlinx-coroutines-test` (déjà dans le projet). Pas d'émulateur requis.

---

## Dev Notes

### Architecture — Substitution Transparente via Hilt

```
DistributeEncryptedBlocksUseCase ──injecte──> BlockSender (interface)
ExecuteMigrationPlanUseCase ──────injecte──> BlockSender (interface)
                                                    │
                                         Hilt binding (BlockTransferModule)
                                                    │
                                    BlockSenderWithRelay (@Singleton, data/p2p/)
                                       /                    \
                           BlockTransferClient       RelayRepository (domain interface)
                           (TCP direct, existant)    (impl: RelayRepositoryImpl)
```

**Règle d'or** : `DistributeEncryptedBlocksUseCase` et `ExecuteMigrationPlanUseCase` **NE SONT PAS MODIFIÉS**. La substitution est 100% transparente via le DI Hilt.

### Flowchart Try-Direct-Then-Relay

```
sendBlock(block, peer, timeout)
    │
    ▼
BlockTransferClient.sendBlock()   ← Priorité 1 (TCP direct)
    │
    ├── Success ──> return Result.success(tcpAck), state=DIRECT
    │
    └── Failure (IOException/SocketTimeoutException)
            │
            ▼
        RelayRepository.uploadBlock(peer.nodeId, block.blockId, protobufPayload)
            │                       ← Priorité 2+3 (Relay HA, failover géré par RelayWebSocketClient)
            ├── Success ──> return Result.success(syntheticAck), state=RELAY_HA
            │
            └── Failure ──> return Result.failure(combined), state=OFFLINE
```

### Format des données relay

```
relayRepository.uploadBlock(
    destNodeId = peer.identity.nodeId,   // 16 chars hex = nodeId pair destinataire
    blockId    = block.blockId,          // 64 chars hex = SHA-256 du ciphertext
    data       = Protobuf(block)         // BlockTransferMessage sérialisé (opaque, Zero-Knowledge)
)
```

Le relay server (Story 8.1) transmet `data` byte-à-byte sans déchiffrement (`UPLOAD → FORWARD`). Le récepteur déserialise pour récupérer `ciphertext`, `iv`, `fragmentIndex`, `isParity`, `ownerId`, `originalFileSize`.

### ACK Synthétique (relay path)

```kotlin
BlockAckMessage(
    blockId        = block.blockId,          // SHA-256 hex du ciphertext
    blockHash      = block.blockId,          // identique — blockId IS the hash
    receiverNodeId = peer.identity.nodeId,   // utilisé par UseCase pour la DHT
    signature      = ByteArray(0)            // pas de signature pair (relay ACK ≠ peer ACK)
)
```

**Compatibilité aval** : `DistributeEncryptedBlocksUseCase` utilise uniquement `receiverNodeId` pour `insertDhtEntryUseCase`. `ExecuteMigrationPlanUseCase` vérifie seulement `result.isSuccess`. Aucun des deux n'appelle `verifySignature()` sur le résultat — cette vérification est interne à `BlockTransferClient.sendBlock()` (TCP path uniquement).

### Ce qui ne doit PAS être touché

| Fichier | Raison |
|---------|--------|
| `BlockTransferClient.kt` | TCP sender existant — injecté directement dans `BlockSenderWithRelay` |
| `domain/repository/BlockSender.kt` | Interface pure — PAS de nouveau champ `transferChannelState` (exposé via `@Named` Hilt) |
| `DistributeEncryptedBlocksUseCase.kt` | AC#5 — aucune modification |
| `ExecuteMigrationPlanUseCase.kt` | AC#5 — aucune modification |
| `OrchestrateBlockMigrationUseCase.kt` | Utilise `TcpConnectionManager` directement, pas `BlockSender` |
| `relay-server/server.js` | Story 8.1 — déjà done |
| `RelayWebSocketClient.kt` | Story 8.2 — déjà done; failover multi-instance déjà implémenté |

### Conflict DI — `BlockTransferClient` vs `BlockSender`

`BlockTransferClient` continue d'implémenter `BlockSender` (interface). Cependant, après Story 8.3, **Hilt bind `BlockSender` → `BlockSenderWithRelay`**, non plus `BlockTransferClient`. `BlockSenderWithRelay` reçoit `BlockTransferClient` comme **type concret** (pas via l'interface). Dagger résout sans ambiguïté car les bindings sont distincts.

```kotlin
// ✅ Correct : injection par type concret
class BlockSenderWithRelay @Inject constructor(
    private val tcpSender: BlockTransferClient,   // type concret direct
    private val relayRepository: RelayRepository  // interface domain
)
```

### RelayConnectionState vs TransferChannelState

| Type | Où | Signification |
|------|----|---------------|
| `RelayConnectionState` | `domain/repository/RelayRepository.kt` | État de la **connexion WebSocket** au relay server |
| `TransferChannelState` | `domain/models/TransferChannelState.kt` | Canal utilisé pour le **dernier transfert de bloc** |

Ces deux états sont distincts et non-interchangeables. Le Dashboard peut afficher les deux (connexion relay = UX connexion réseau ; canal transfert = UX dernier envoi de bloc).

### Tests existants à ne pas casser

- `RelayFramingTest.kt` et `RelayWebSocketClientTest.kt` — Story 8.2, doivent rester verts
- `DistributeEncryptedBlocksUseCaseTest.kt` — Story 5.3, doit rester vert
- `ExecuteMigrationPlanUseCaseTest.kt` — Story 7.2, doit rester vert

### Imports clés à utiliser

```kotlin
// Protobuf sérialisation (déjà dans le projet depuis Story 0-2)
import com.mobicloud.core.format.MobiCloudProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi

// Relay domain interface (Story 8.2)
import com.mobicloud.domain.repository.RelayRepository

// TCP sender existant (Story 5.3)
import com.mobicloud.data.p2p.tcp.BlockTransferClient

// BlockSender interface (Story 5.3)
import com.mobicloud.domain.repository.BlockSender

// ReceiveAndHostBlockUseCase (Story 5.5) — pour Task 5
import com.mobicloud.domain.usecase.m08_hosting.ReceiveAndHostBlockUseCase
```

### UX Dark OLED — CloudRelayBadge

- Fond du badge : transparent (hérite du fond `#000000` du Dashboard)
- Couleur DIRECT : `Color(0xFF4CAF50)` — vert vif visible sur noir
- Couleur RELAY_HA : `Color(0xFF2196F3)` — bleu vif visible sur noir
- Couleur OFFLINE : `Color(0xFFF44336)` — rouge vif visible sur noir
- Taille icône : 16.dp (compact, non-intrusif dans le Dashboard)

### Project Structure Notes

**Arborescence cible après implémentation :**

```
app/src/main/kotlin/com/mobicloud/
├── domain/models/
│   └── TransferChannelState.kt          ← NOUVEAU — enum DIRECT/RELAY_HA/OFFLINE
├── data/p2p/
│   └── BlockSenderWithRelay.kt          ← NOUVEAU — @Singleton, implements BlockSender
├── di/
│   └── BlockTransferModule.kt           ← MODIFIÉ — binding BlockSender → BlockSenderWithRelay
│                                                    + @Named("transfer_channel_state")
├── data/repository/
│   └── RelayRepositoryImpl.kt           ← MODIFIÉ — gestion RelayEvent.BlockReceived (Task 5)
└── presentation/dashboard/
    ├── DashboardViewModel.kt            ← MODIFIÉ — inject transferChannelStateFlow
    ├── DashboardScreen.kt               ← MODIFIÉ — CloudRelayBadge ajouté
    └── components/
        └── CloudRelayBadge.kt           ← NOUVEAU — Composable UX-DR10

app/src/test/kotlin/com/mobicloud/
└── data/p2p/
    └── BlockSenderWithRelayTest.kt      ← NOUVEAU — 5 tests JVM MockK
```

**Fichiers existants référencés (lecture seule, ne pas modifier) :**

- `data/p2p/tcp/BlockTransferClient.kt` — `@Inject constructor(securityRepository)`, implémente `BlockSender`
- `domain/repository/BlockSender.kt` — interface `sendBlock(block, peer, timeoutMs): Result<BlockAckMessage>`
- `domain/repository/RelayRepository.kt` — `uploadBlock(destNodeId, blockId, data): Result<Unit>` + `connectionState`
- `data/repository/RelayRepositoryImpl.kt` — `@Singleton`, `init { client.connect().collect {...} }`
- `data/p2p/websocket/RelayWebSocketClient.kt` — `uploadBlock()` + failover séquentiel 5 tentatives par instance
- `domain/models/BlockAckMessage.kt` — `blockId, blockHash, receiverNodeId, signature: ByteArray`
- `domain/models/BlockTransferMessage.kt` — `blockId, ownerId, fragmentIndex, isParity, ciphertext, iv, originalFileSize`
- `core/format/MobiCloudProtoBuf.kt` — `encodeToByteArray` / `decodeFromByteArray`

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.3] AC littéraux (FR-08.2 : Try-Direct-Then-Relay transparent)
- [Source: _bmad-output/planning-artifacts/architecture.md#§Server Boundary V5.0] `BlockSenderWithRelay` dans `data/p2p/`, interfaces pures dans `domain/`
- [Source: _bmad-output/planning-artifacts/architecture.md#§API & Communication Patterns] "BlockSenderWithRelay wrapper qui implémente try-direct-then-relay-with-failover"
- [Source: _bmad-output/implementation-artifacts/8-2-client-android-relaywebsocketclient-unifie.md#Task 5] `RelayRepositoryImpl` — `repoScope.launch { client.connect().collect {} }`
- [Source: _bmad-output/implementation-artifacts/8-2-client-android-relaywebsocketclient-unifie.md#Deferred] `RELAY_SERVER_URLS` hardcodé — Story 8.3 rend configurable via `RelayWebSocketClient.connect()`
- [Source: _bmad-output/implementation-artifacts/8-1-serveur-relais-ha-nodejs-signaling-transport-unifies.md#§Protocole UPLOAD] `data` bytes 80+ = bloc chiffré AES-256 GCM opaque — Zero-Knowledge garanti
- [Source: app/src/main/kotlin/com/mobicloud/di/BlockTransferModule.kt] Pattern `@Provides @Singleton fun provideBlockSender(client: BlockTransferClient): BlockSender = client` — à remplacer
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt:96] `receiverNodeId` utilisé pour DHT — seul champ du ACK vraiment consommé
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/ExecuteMigrationPlanUseCase.kt:25] Inject `blockSender: BlockSender` — bénéficiera automatiquement du fallback relay
- [Source: _bmad-output/planning-artifacts/epics.md#UX-DR10] CloudRelayBadge : 3 icônes distinctes (✓ direct / ☁ relay / ⚠ offline)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 — 2026-04-29

### Debug Log References

Aucun blocage rencontré. `ReceiveAndHostBlockUseCase.receive()` (méthode réelle) utilisé à la place de `.invoke()` mentionné dans le spec (méthode inexistante).

### Completion Notes List

- Task 1 ✅ : `TransferChannelState.kt` créé — enum pur sans import Android/OkHttp
- Task 2 ✅ : `BlockSenderWithRelay.kt` créé — logique Try-Direct-Then-Relay, ACK synthétique, StateFlow exposé
- Task 3 ✅ : `BlockTransferModule.kt` modifié — binding `BlockSender → BlockSenderWithRelay` + `@Named("transfer_channel_state")`
- Task 4 ✅ : `CloudRelayBadge.kt` créé, `DashboardViewModel` et `DashboardScreen` modifiés — badge UX-DR10 intégré
- Task 5 ✅ : `RelayRepositoryImpl.kt` étendu — traitement `RelayEvent.BlockReceived` via `receiveAndHostBlockUseCase.receive()`
- Task 6 ✅ : `BlockSenderWithRelayTest.kt` créé — 5 tests JVM (MockK + coroutines-test) couvrant tous les chemins

### File List

- `app/src/main/kotlin/com/mobicloud/domain/models/TransferChannelState.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/data/p2p/BlockSenderWithRelay.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/di/BlockTransferModule.kt` (modifié)
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/components/CloudRelayBadge.kt` (nouveau)
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardViewModel.kt` (modifié)
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt` (modifié)
- `app/src/main/kotlin/com/mobicloud/data/repository/RelayRepositoryImpl.kt` (modifié)
- `app/src/test/kotlin/com/mobicloud/data/p2p/BlockSenderWithRelayTest.kt` (nouveau)

### Review Findings

#### Decision Needed

- [x] [Review][Decision] F1 — ACK synthétique `signature = ByteArray(0)` accepté sans garantie cryptographique — Sur le chemin relay, le `BlockAckMessage` retourné aux UseCases a une signature vide. La spec documente que les UseCases actuels n'utilisent que `receiverNodeId` et ne vérifient pas la signature — mais tout futur audit sécurité ou nouveau consommateur de l'ACK recevra une signature vide sans avertissement. Décision explicite requise : (a) confirmer que les UseCases ne vérifieront jamais la signature du relay ACK, ou (b) ajouter un flag `isRelayAck: Boolean` dans `BlockAckMessage` pour distinguer les deux chemins. [BlockSenderWithRelay.kt:68]

- [x] [Review][Decision] F2 — relay-server/server.js et server.test.js modifiés hors scope story 8.3 → **Décision : commit séparé tagué 8.1 à créer** — Ces fichiers apparaissent comme modifiés dans le working tree (`git status: M`) alors que la spec 8.3 exclut explicitement `server.js` ("Story 8.1 — déjà done"). Ces modifications doivent être (a) justifiées et rattachées à la story 8.1 via un commit séparé, ou (b) revertées si elles sont accidentelles.

- [x] [Review][Decision] F3 — Réception inbound relay via première instance uniquement — split-brain HA sur le chemin de réception → **Décision : limitation documentée HA V1** — réception single-instance intentionnelle pour MVP PFE. [RelayRepositoryImpl.kt:34]

#### Patches

- [x] [Review][Patch] F4 — `tcpCause !is SocketTimeoutException` dead code supprimé ; import `SocketTimeoutException` retiré [BlockSenderWithRelay.kt:44] ✅

- [x] [Review][Patch] F5 — Message d'erreur sérialisation enrichi avec contexte TCP : `"Relay inaccessible — TCP: … ; Sérialisation: …"` [BlockSenderWithRelay.kt:51-56] ✅

- [x] [Review][Patch] F6 — Handler BlockReceived restructuré : deux `runCatching` distincts, messages d'erreur séparés pour désérialisation vs hébergement [RelayRepositoryImpl.kt:38-50] ✅

- [x] [Review][Patch] F7 — `blockId.take(16)` → `take(32)` dans les deux messages de log [RelayRepositoryImpl.kt:38-50] ✅

#### Deferred (pré-existants)

- [x] [Review][Defer] F8 — Race condition retry même blockId dans RelayWebSocketClient.uploadBlock() — pré-existant 8.2, code non modifié par 8.3 [RelayWebSocketClient.kt:166-187]
- [x] [Review][Defer] F9 — repoScope CoroutineScope jamais annulé — tech debt documenté depuis 8.2 [RelayRepositoryImpl.kt:29]
- [x] [Review][Defer] F10 — Race condition _connectionState entre init coroutine et uploadBlock() — pré-existant 8.2 [RelayRepositoryImpl.kt:57]
- [x] [Review][Defer] F11 — activeWebSocket null en fenêtre de démarrage (race init) — pré-existant 8.2 [RelayWebSocketClient.kt:167]
- [x] [Review][Defer] F12 — TCP connect timeout non borné par timeoutMs — BlockTransferClient pré-existant
- [x] [Review][Defer] F13 — timeoutMs non transmis à relayRepository.uploadBlock() — relay fixe 30s, comportement architecturalement voulu [BlockSenderWithRelay.kt:58]
- [x] [Review][Defer] F14 — DiscoverySource.REMOTE_FIREBASE dans les tests — sémantiquement dépassé post-V5 Zero-Firebase [BlockSenderWithRelayTest.kt:49]
- [x] [Review][Defer] F15 — @OptIn(ExperimentalSerializationApi::class) dispersé dans plusieurs classes — pré-existant

---

## Change Log

- 2026-04-29 : Implémentation complète Story 8.3 — fallback transparent Try-Direct-Then-Relay avec StateFlow `TransferChannelState` et badge Dashboard UX-DR10 `CloudRelayBadge` (claude-sonnet-4-6)
- 2026-04-29 : Code review — 3 décisions requises, 4 patches, 8 déférés, 8 dismissed (claude-sonnet-4-6)
