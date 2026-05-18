# Story 7.2: Orchestration de la Migration Proactive par le Super-Pair

Status: done

## Story

En tant que Super-Pair,
Je veux orchestrer le transfert des blocs d'un nœud partant vers d'autres nœuds disponibles en < 5 secondes,
Afin de maintenir le niveau de résilience du cluster avant la déconnexion.

## Acceptance Criteria

1. **Given** le Super-Pair reçoit un `DEPARTURE_NOTICE` d'un nœud partant
   **When** l'orchestration de migration démarre
   **Then** pour chaque `blockId` du nœud partant, le Super-Pair identifie un nœud de destination disponible (`ACTIVE`, hors nœud partant)

2. **And** le nœud partant reçoit un `MIGRATE_BLOCK(blockId, destinationIp:port)` et transfère le bloc chiffré sans le déchiffrer (transfert aveugle opaque)

3. **And** le nœud de destination confirme la réception avec un `ACK` signé + hash SHA-256 du bloc

4. **And** la DHT est mise à jour immédiatement (Gossip déclenché) pour refléter le nouveau propriétaire

5. **And** toute l'opération doit être complétée en < 5 secondes (NFR-02)

6. **And** la logique est dans `domain/usecase/m06_m07_repair_migration/OrchestrateBlockMigrationUseCase.kt`

## Tasks / Subtasks

### 🔢 Bloc Données (Tasks 1–2) — modèle Protobuf & canal TCP

- [x] **Task 1** : Créer `MigrationPlanMessage` (AC: #1, #2)
  - [x] Subtask 1.1 : Créer `app/src/main/kotlin/com/mobicloud/domain/models/MigrationPlanMessage.kt` — batch de directives envoyé par le Super-Pair au nœud partant :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class MigrateBlockDirective(
        @ProtoNumber(1) val blockId: String = "",
        @ProtoNumber(2) val destinationNodeId: String = "",
        @ProtoNumber(3) val destinationIp: String = "",
        @ProtoNumber(4) val destinationPort: Int = 0,
        @ProtoNumber(5) val destinationPublicKeyBytes: ByteArray = byteArrayOf()
    ) {
        override fun equals(other: Any?): Boolean { /* couvrir destinationPublicKeyBytes */ }
        override fun hashCode(): Int { /* ... */ }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class MigrationPlanMessage(
        @ProtoNumber(1) val superPeerNodeId: String = "",
        @ProtoNumber(2) val directives: List<MigrateBlockDirective> = emptyList(),
        @ProtoNumber(3) val signatureBytes: ByteArray = byteArrayOf()
    ) {
        override fun equals(other: Any?): Boolean { /* couvrir signatureBytes */ }
        override fun hashCode(): Int { /* ... */ }
    }
    ```
    Pattern identique à `DepartureNoticeMessage.kt` / `ElectionPayload.kt` (valeurs par défaut explicites pour tolérance Protobuf versioning).

- [x] **Task 2** : Étendre `DepartureChannel` avec le byte `MIGRATION_PLAN` (AC: #2)
  - [x] Subtask 2.1 : Dans `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/DepartureChannel.kt`, ajouter :
    ```kotlin
    // Story 7.2 — canal du Super-Pair vers le nœud partant (plan de migration)
    const val MIGRATION_PLAN: Byte = 0x09
    const val MAX_MIGRATION_PLAN_BYTES = 64_000  // ~250 directives × 250 bytes
    ```
    Le byte `0x09` est déjà réservé pour Story 7.2 dans le commentaire de `DepartureChannel.kt:9`. Vérifier qu'aucun autre canal ne l'a repris depuis.

### 🌐 Bloc Réseau (Task 3) — TcpConnectionManager entrant + sortant

- [x] **Task 3** : Extension `TcpConnectionManager` pour `MIGRATION_PLAN` (AC: #2)
  - [x] Subtask 3.1 : Ajouter un champ handler dans `TcpConnectionManager` :
    ```kotlin
    // Story 7.2 — handler du nœud partant qui exécute le plan de migration reçu du Super-Pair
    @Volatile
    var migrationPlanHandler: MigrationPlanHandler? = null
    ```
  - [x] Subtask 3.2 : Dans `handleIncomingConnection()` du `when`, ajouter la branche :
    ```kotlin
    DepartureChannel.MIGRATION_PLAN -> handleIncomingMigrationPlan(pushback)
    ```
  - [x] Subtask 3.3 : Implémenter `handleIncomingMigrationPlan` (symétrique à `handleIncomingDepartureNotice`) :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun handleIncomingMigrationPlan(inp: InputStream) {
        try {
            val data = DataInputStream(inp)
            val len = data.readInt()
            if (len <= 0 || len > DepartureChannel.MAX_MIGRATION_PLAN_BYTES) {
                Log.w("MobiCloud:TCP", "MIGRATION_PLAN taille invalide: $len — ignoré")
                return
            }
            val bytes = ByteArray(len).also { data.readFully(it) }
            val plan = MobiCloudProtoBuf.decodeFromByteArray(MigrationPlanMessage.serializer(), bytes)
            migrationPlanHandler?.onMigrationPlanReceived(plan)
                ?: Log.w("MobiCloud:TCP", "MIGRATION_PLAN reçu mais aucun handler")
        } catch (e: Exception) {
            Log.e("MobiCloud:TCP", "Erreur lecture MIGRATION_PLAN", e)
        }
    }
    ```
  - [x] Subtask 3.4 : Ajouter la méthode sortante `sendMigrationPlan` (pattern identique à `sendDepartureNotice` aux l.447–466) :
    ```kotlin
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendMigrationPlan(
        plan: MigrationPlanMessage,
        ip: String,
        port: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 3_000)
                socket.soTimeout = 3_000
                val out = DataOutputStream(socket.getOutputStream())
                val bytes = MobiCloudProtoBuf.encodeToByteArray(MigrationPlanMessage.serializer(), plan)
                out.writeByte(DepartureChannel.MIGRATION_PLAN.toInt())
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
        }
    }
    ```

### ⚙️ Bloc UseCase côté Super-Pair (Task 4) — orchestrateur

- [x] **Task 4** : Créer `OrchestrateBlockMigrationUseCase` implémentant `DepartureNoticeHandler` (AC: #1, #2, #4, #5, #6)
  - [x] Subtask 4.1 : Créer `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/OrchestrateBlockMigrationUseCase.kt` :
    ```kotlin
    @Singleton
    class OrchestrateBlockMigrationUseCase @Inject constructor(
        private val peerRepository: PeerRepository,
        private val dhtRepository: DhtRepository,
        private val securityRepository: SecurityRepository,
        private val tcpConnectionManager: TcpConnectionManager,
        private val gossipSyncUseCase: GossipSyncUseCase,
        private val networkEventRepository: NetworkEventRepository
    ) : DepartureNoticeHandler {

        companion object {
            const val NFR02_BUDGET_MS = 5_000L
        }

        override suspend fun onDepartureNoticeReceived(notice: DepartureNoticeMessage) {
            val identity = securityRepository.getIdentity().getOrElse {
                networkEventRepository.pushEvent("[MIGRATION] identité locale indisponible — plan annulé")
                return
            }

            // 1) Le nœud courant DOIT être Super-Pair pour orchestrer (sinon le NOTICE a été mal-routé)
            val selfIsSuperPair = peerRepository.peers.value
                .any { it.identity.nodeId == identity.nodeId && it.isSuperPair && it.isActive }
            if (!selfIsSuperPair) {
                networkEventRepository.pushEvent("[MIGRATION] DEPARTURE_NOTICE ignoré — nœud local non Super-Pair")
                return
            }

            // 2) Vérification signature du NOTICE (le nœud partant signe "$nodeId:$blockIdsJoined")
            val departingPeer = peerRepository.peers.value
                .firstOrNull { it.identity.nodeId == notice.senderNodeId }
            if (departingPeer == null) {
                networkEventRepository.pushEvent("[MIGRATION] Émetteur ${notice.senderNodeId.take(8)} inconnu — plan annulé")
                return
            }
            val signedPayload = "${notice.senderNodeId}:${notice.hostedBlockIds.joinToString(",")}".toByteArray()
            val valid = securityRepository.verifySignature(
                data = signedPayload,
                signature = notice.signatureBytes,
                publicKey = departingPeer.identity.publicKeyBytes
            ).getOrDefault(false)
            if (!valid) {
                networkEventRepository.pushEvent("[MIGRATION] Signature DEPARTURE_NOTICE invalide — plan annulé")
                return
            }

            if (notice.hostedBlockIds.isEmpty()) {
                networkEventRepository.pushEvent("[MIGRATION] ${notice.senderNodeId.take(8)} — aucun bloc à migrer")
                return
            }

            // 3) Candidats destination : actifs, hors émetteur, avec ip/port connus, hors soi-même
            val candidates = peerRepository.peers.value.filter { p ->
                p.isActive &&
                p.ipAddress != null && p.port != null &&
                p.identity.nodeId != notice.senderNodeId &&
                p.identity.nodeId != identity.nodeId
            }
            if (candidates.isEmpty()) {
                networkEventRepository.pushEvent("[MIGRATION] Aucun nœud de destination disponible — plan annulé")
                return
            }

            // 4) Round-robin sur blockIds triés (ordre stable = même que DAO.getAllBlockIds)
            val directives = notice.hostedBlockIds.mapIndexed { i, blockId ->
                val dest = candidates[i % candidates.size]
                MigrateBlockDirective(
                    blockId = blockId,
                    destinationNodeId = dest.identity.nodeId,
                    destinationIp = dest.ipAddress!!,
                    destinationPort = dest.port!!,
                    destinationPublicKeyBytes = dest.identity.publicKeyBytes
                )
            }

            // 5) Signature du plan : "$superPeerNodeId|${directives.joinToString("|") { "${it.blockId}:${it.destinationNodeId}" }}"
            val planSigPayload = "${identity.nodeId}|${directives.joinToString("|") { "${it.blockId}:${it.destinationNodeId}" }}"
                .toByteArray()
            val planSignature = securityRepository.signData(planSigPayload).getOrElse {
                networkEventRepository.pushEvent("[MIGRATION] Signature du plan échouée — plan annulé")
                return
            }

            val plan = MigrationPlanMessage(
                superPeerNodeId = identity.nodeId,
                directives = directives,
                signatureBytes = planSignature
            )

            // 6) Transmission du plan au nœud partant — AC#5 budget NFR-02 5s global
            val departingIp = departingPeer.ipAddress
            val departingPort = departingPeer.port
            if (departingIp == null || departingPort == null) {
                networkEventRepository.pushEvent("[MIGRATION] Adresse partant ${notice.senderNodeId.take(8)} inconnue — plan annulé")
                return
            }
            withTimeoutOrNull(NFR02_BUDGET_MS) {
                tcpConnectionManager.sendMigrationPlan(plan, departingIp, departingPort)
                    .onSuccess {
                        networkEventRepository.pushEvent(
                            "[MIGRATION] Plan envoyé à ${notice.senderNodeId.take(8)} — ${directives.size} directive(s)"
                        )
                    }
                    .onFailure {
                        networkEventRepository.pushEvent("[MIGRATION] Envoi du plan échoué : ${it.message}")
                    }
            } ?: networkEventRepository.pushEvent("[MIGRATION] Timeout envoi du plan (> 5s)")

            // 7) AC#4 — MàJ DHT optimiste : delete entries du nœud partant, insert pour destinations
            dhtRepository.deleteByNodeId(notice.senderNodeId)
                .onFailure { networkEventRepository.pushEvent("[MIGRATION] Suppression DHT partant échouée : ${it.message}") }
            directives.forEach { d ->
                dhtRepository.insertEntry(d.blockId, d.destinationNodeId, d.destinationIp, d.destinationPort)
                    .onFailure { networkEventRepository.pushEvent("[MIGRATION] Insert DHT ${d.blockId.take(16)}→${d.destinationNodeId.take(8)} échoué : ${it.message}") }
            }

            // 8) AC#4 — Gossip immédiat pour propagation nouveau propriétaire
            gossipSyncUseCase.runGossipCycle()
                .onFailure { networkEventRepository.pushEvent("[MIGRATION] Gossip post-migration échoué : ${it.message}") }
        }
    }
    ```

### ⚙️ Bloc UseCase côté nœud partant (Task 5) — exécuteur du plan

- [x] **Task 5** : Créer `MigrationPlanHandler` + `ExecuteMigrationPlanUseCase` (AC: #2, #3, #5)
  - [x] Subtask 5.1 : Créer l'interface `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/MigrationPlanHandler.kt` :
    ```kotlin
    interface MigrationPlanHandler {
        suspend fun onMigrationPlanReceived(plan: MigrationPlanMessage)
    }
    ```
  - [x] Subtask 5.2 : Créer `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/ExecuteMigrationPlanUseCase.kt` :
    ```kotlin
    @Singleton
    class ExecuteMigrationPlanUseCase @Inject constructor(
        private val hostedBlockRepository: HostedBlockRepository,
        private val peerRepository: PeerRepository,
        private val securityRepository: SecurityRepository,
        private val blockSender: BlockSender,
        private val networkEventRepository: NetworkEventRepository
    ) : MigrationPlanHandler {

        companion object {
            const val PER_BLOCK_TIMEOUT_MS = 4_000L  // < 5s NFR-02, laisse marge pour MàJ DHT côté SP
        }

        override suspend fun onMigrationPlanReceived(plan: MigrationPlanMessage) = coroutineScope {
            // 1) Vérification signature du plan avec la clé publique du Super-Pair annoncé
            val superPeer = peerRepository.peers.value
                .firstOrNull { it.identity.nodeId == plan.superPeerNodeId && it.isSuperPair }
            if (superPeer == null) {
                networkEventRepository.pushEvent("[MIGRATION] Plan reçu d'un nœud non Super-Pair ${plan.superPeerNodeId.take(8)} — ignoré")
                return@coroutineScope
            }
            val planSigPayload = "${plan.superPeerNodeId}|${plan.directives.joinToString("|") { "${it.blockId}:${it.destinationNodeId}" }}"
                .toByteArray()
            val valid = securityRepository.verifySignature(
                data = planSigPayload,
                signature = plan.signatureBytes,
                publicKey = superPeer.identity.publicKeyBytes
            ).getOrDefault(false)
            if (!valid) {
                networkEventRepository.pushEvent("[MIGRATION] Signature plan invalide — ignoré")
                return@coroutineScope
            }

            val localId = securityRepository.getIdentity().getOrElse {
                networkEventRepository.pushEvent("[MIGRATION] identité locale indisponible — plan ignoré")
                return@coroutineScope
            }.nodeId

            // 2) Exécution parallèle des directives — AC#2 transfert aveugle, AC#3 ACK signé
            plan.directives.map { directive ->
                async {
                    executeDirective(directive, localId)
                }
            }.awaitAll()
        }

        private suspend fun executeDirective(directive: MigrateBlockDirective, localNodeId: String) {
            // AC#2 : on lit le bloc déjà chiffré — pas de déchiffrement, transfert opaque
            val payload = hostedBlockRepository.getBlock(directive.blockId).getOrNull()
            if (payload == null) {
                networkEventRepository.pushEvent("[MIGRATION] Bloc ${directive.blockId.take(16)} absent localement — ignoré")
                return
            }
            val destPeer = Peer(
                identity = NodeIdentity(
                    nodeId = directive.destinationNodeId,
                    publicKeyBytes = directive.destinationPublicKeyBytes
                ),
                lastSeenTimestampMs = System.currentTimeMillis(),
                ipAddress = directive.destinationIp,
                port = directive.destinationPort
            )
            val msg = BlockTransferMessage(
                blockId = payload.blockId,
                ownerId = localNodeId,  // owner conservé = nœud partant (propriétaire d'origine)
                fragmentIndex = payload.fragmentIndex,
                isParity = payload.isParity,
                ciphertext = payload.ciphertext,
                iv = payload.iv,
                originalFileSize = 0L  // inconnu côté hébergeur, non-bloquant pour réception
            )
            // AC#3 : BlockSender.sendBlock vérifie la signature de l'ACK (domain separation ACK_DOMAIN_PREFIX)
            blockSender.sendBlock(msg, destPeer, PER_BLOCK_TIMEOUT_MS)
                .onSuccess { ack ->
                    networkEventRepository.pushEvent(
                        "[MIGRATION] ${payload.blockId.take(16)} → ${ack.receiverNodeId.take(8)} confirmé"
                    )
                }
                .onFailure {
                    networkEventRepository.pushEvent(
                        "[MIGRATION] ${payload.blockId.take(16)} → ${directive.destinationNodeId.take(8)} échec : ${it.message}"
                    )
                }
        }
    }
    ```

### 🏗️ Bloc DI & Service (Task 6) — câblage Hilt et handlers

- [x] **Task 6** : Câbler les handlers dans `MobicloudP2PService` (AC: #1, #2)
  - [x] Subtask 6.1 : Dans `MobicloudP2PService`, ajouter les injections :
    ```kotlin
    @Inject lateinit var orchestrateBlockMigrationUseCase: OrchestrateBlockMigrationUseCase
    @Inject lateinit var executeMigrationPlanUseCase: ExecuteMigrationPlanUseCase
    ```
  - [x] Subtask 6.2 : Dans `startP2PNetworkLoops()`, AVANT `tcpConnectionManager.startServer()` (à la même section que les handlers Gossip/Block/DHT existants l.132–135), ajouter :
    ```kotlin
    tcpConnectionManager.departureHandler = orchestrateBlockMigrationUseCase
    tcpConnectionManager.migrationPlanHandler = executeMigrationPlanUseCase
    ```
    `departureHandler` est déclaré en Story 7.1 mais laissé à `null` — Story 7.2 le câble enfin à `OrchestrateBlockMigrationUseCase`.
  - [x] Subtask 6.3 : `RepairMigrationModule` reste vide — les deux use cases sont `@Singleton @Inject constructor` et résolus automatiquement par Hilt.

### 🧪 Bloc Tests (Task 7)

- [x] **Task 7** : Tests JVM pour `OrchestrateBlockMigrationUseCase` et `ExecuteMigrationPlanUseCase` (AC: #1, #2, #3, #4)
  - [x] Subtask 7.1 : Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/OrchestrateBlockMigrationUseCaseTest.kt` :
    - **Test 1 — non-super-pair court-circuite** : `peerRepository.peers.value = [selfPeer(isSuperPair=false)]`, appeler handler, vérifier qu'AUCUN `sendMigrationPlan`, `dhtRepository.*`, ou `gossipSyncUseCase` n'est appelé.
    - **Test 2 — signature DEPARTURE_NOTICE invalide** : `verifySignature` retourne `false` → handler retourne sans envoyer de plan.
    - **Test 3 — aucun candidat destination** : 1 pair actif = l'émetteur uniquement → aucun plan émis, log `"Aucun nœud de destination"`.
    - **Test 4 — plan round-robin** : 3 blockIds, 2 candidats destination, vérifier `plan.directives[0].destinationNodeId == candidates[0].nodeId`, `[1]==candidates[1]`, `[2]==candidates[0]`.
    - **Test 5 — DHT mise à jour post-envoi** : vérifier `dhtRepository.deleteByNodeId(senderNodeId)` appelé 1×, `dhtRepository.insertEntry` appelé N fois (1 par directive), puis `gossipSyncUseCase.runGossipCycle()` appelé 1×.
    - **Test 6 — budget 5s (withTimeoutOrNull)** : mocker `sendMigrationPlan` pour bloquer > 5s via `delay(10_000)`, utiliser `TestCoroutineScheduler`, vérifier log `"Timeout envoi du plan"`. La MàJ DHT doit quand même être tentée.
  - [x] Subtask 7.2 : Créer `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/ExecuteMigrationPlanUseCaseTest.kt` :
    - **Test 1 — signature plan invalide** : `verifySignature` → `false` → aucun `blockSender.sendBlock` appelé.
    - **Test 2 — bloc absent localement** : `hostedBlockRepository.getBlock(blockId)` → `Success(null)` → aucun `sendBlock`, log `"absent localement"`.
    - **Test 3 — transfert aveugle opaque** : vérifier que le `BlockTransferMessage` envoyé contient `ciphertext == payload.ciphertext` (byte-à-byte), `iv == payload.iv` (aucun déchiffrement).
    - **Test 4 — exécution parallèle** : 3 directives, chaque `sendBlock` bloque 2s, utiliser `TestCoroutineScheduler`, vérifier que le handler revient en < 4s (et non 6s séquentiel).
    - **Test 5 — propagation nodeId owner** : `BlockTransferMessage.ownerId == localNodeId` (nœud partant reste propriétaire — pas destinationNodeId).

---

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Description | Action |
|---|---|---|
| `domain/models/DepartureNoticeMessage.kt` | Story 7.1 — message Protobuf signé (senderNodeId, hostedBlockIds, signatureBytes) | **LECTURE SEULE** (entrée du handler) |
| `domain/models/BlockTransferMessage.kt` | Payload Protobuf d'un bloc chiffré (blockId, ownerId, fragmentIndex, isParity, ciphertext, iv, originalFileSize) | **RÉUTILISER tel quel** pour Task 5 |
| `domain/models/BlockAckMessage.kt` | ACK signé (blockId, blockHash, receiverNodeId, signature) | **CONSOMMÉ** via `BlockSender.sendBlock` |
| `domain/repository/BlockSender.kt` | Interface `sendBlock(msg, peer, timeoutMs): Result<BlockAckMessage>` | **RÉUTILISER** dans `ExecuteMigrationPlanUseCase` |
| `data/p2p/tcp/BlockTransferClient.kt` | Impl qui envoie BLOCK_TRANSFER + vérifie signature ACK (ACK_DOMAIN_PREFIX) | **RÉUTILISER** — Hilt le bind sur `BlockSender` |
| `domain/repository/HostedBlockRepository.kt` | `getBlock(blockId): Result<HostedBlockPayload?>` | **RÉUTILISER** côté nœud partant |
| `domain/models/HostedBlockPayload.kt` | Bloc hébergé lu depuis disque (ciphertext + iv 12 bytes) | **RÉUTILISER** — fournit ciphertext pour transfert aveugle |
| `domain/repository/DhtRepository.kt` | `deleteByNodeId`, `insertEntry` — opérations locales DHT | **RÉUTILISER** pour AC#4 |
| `domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt` | Wrapper minimal — `insertEntry` direct sur repo OK aussi | **OPTIONNEL** — appel direct à `dhtRepository.insertEntry` accepté |
| `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt` | `runGossipCycle()` pour propager les changements DHT | **RÉUTILISER** pour AC#4 |
| `domain/repository/PeerRepository.kt` | `peers: StateFlow<List<Peer>>` (champs: isActive, isSuperPair, ipAddress, port, identity.nodeId, identity.publicKeyBytes) | **LECTURE** — pas de mutation ici |
| `domain/repository/SecurityRepository.kt` | `getIdentity`, `signData`, `verifySignature` (EC P-256 via Keystore) | **RÉUTILISER** pour signatures plan et vérif NOTICE |
| `domain/repository/NetworkEventRepository.kt` | `pushEvent(msg)` → logs dans `RadarLogConsole` | **RÉUTILISER** pour traçabilité (motif `[MIGRATION]`) |
| `domain/usecase/m06_m07_repair_migration/DepartureNoticeHandler.kt` | Interface déclarée en Story 7.1, laissée vide | **IMPLÉMENTER** (Task 4) |
| `data/p2p/tcp/TcpConnectionManager.kt` | Dispatch par premier byte, `sendDepartureNotice` / `handleIncomingDepartureNotice` déjà en place | **ÉTENDRE** (Task 3) |
| `data/p2p/tcp/DepartureChannel.kt` | Bytes 0x08 (DEPARTURE_NOTICE). Le commentaire l.9 réserve 0x09 pour Story 7.2 | **ÉTENDRE** (Task 2) |
| `domain/usecase/m06_m07_repair_migration/SendDepartureNoticeUseCase.kt` | Story 7.1 — envoie NOTICE puis `delay(5_000)` puis `stopServer()` | **NE PAS MODIFIER** — la fenêtre 5s permet au Super-Pair de joindre via `sendMigrationPlan` |
| `di/RepairMigrationModule.kt` | Module vide — Hilt résout les `@Singleton @Inject` | **NE PAS MODIFIER** |
| `data/network/service/MobicloudP2PService.kt` | Service démarre les loops et câble les handlers (gossip, blockReceiver, dhtRelay, hostedBlockProvider, departure) | **MODIFIER** (Task 6 — câbler `departureHandler` + `migrationPlanHandler`) |

### ⚠️ CONTRAINTES CRITIQUES

**1. Transfert aveugle opaque (AC#2, Architecture Module 7)**
- L'évacuation est strictement sur le **ciphertext** lu depuis `HostedBlockEntity.filePath` + `HostedBlockEntity.iv`. Aucune clé AES-256-GCM n'est présente sur le nœud partant (Zero-Trust, cf. Story 5.2/5.5).
- `HostedBlockRepository.getBlock` renvoie déjà `HostedBlockPayload(ciphertext, iv)` — pas d'appel à `FragmentCipherUseCase` ni à la `FileMasterKey`.
- **NE PAS** tenter de déchiffrer / ré-emballer le bloc — c'est conceptuellement et opérationnellement impossible (clé absente) et violerait l'architecture.

**2. NFR-02 — Budget 5 secondes total (AC#5)**
- La fenêtre est serrée : `SendDepartureNoticeUseCase.MIGRATION_WINDOW_MS = 5_000L` (Story 7.1) définit le temps après DEPARTURE_NOTICE avant que le nœud partant ferme son `ServerSocket`.
- Le Super-Pair doit envoyer le plan dans cette fenêtre → `withTimeoutOrNull(NFR02_BUDGET_MS)` autour de `sendMigrationPlan`.
- Côté nœud partant, `ExecuteMigrationPlanUseCase` lance les transferts **en parallèle** (`async { ... }.awaitAll()`) — séquentiel violerait NFR-02.
- `PER_BLOCK_TIMEOUT_MS = 4_000L` laisse une marge avant le `stopServer()` global.
- **Mise à jour DHT optimiste** : le Super-Pair ne peut pas attendre confirmation individuelle (coût réseau × N blocs > 5s). Il met à jour la DHT dès l'envoi du plan. Story 7.3 (auto-réparation) rattrapera les migrations échouées en détectant les blocs sous-répliqués.

**3. Byte 0x09 — MIGRATION_PLAN**
- Déjà réservé dans `DepartureChannel.kt:9` (commentaire `// Fix P10 : DEPARTURE_ACK supprimé […] Le byte 0x09 est réservé pour Story 7.2`).
- **Vérifier avant l'assignation** qu'aucun autre canal ne l'a repris entre-temps (`grep -r "0x09" app/src/main/kotlin`). Bytes occupés actuels : 0x01-0x03 (Gossip), 0x08 (DEPARTURE_NOTICE), 0x20-0x22 (BlockTransfer), 0x30-0x31 (DhtLookup), 0x40-0x42 (BlockRequest).

**4. Vérification que le nœud local EST Super-Pair (AC#1)**
- `RunBullyElectionUseCase` (Story 3.1, l.118–122) appelle `peerRepository.registerOrUpdatePeer(isSuperPair = true)` sur l'identité locale lorsqu'il gagne l'élection.
- Donc pour déterminer si ON est Super-Pair : `peerRepository.peers.value.any { it.identity.nodeId == selfNodeId && it.isSuperPair && it.isActive }`.
- **NE PAS** se fier à un flag global mutable — lire l'état depuis `peerRepository` (source de vérité unique).
- Si le nœud perd son statut entre la réception du NOTICE et l'envoi du plan (rare mais possible avec abdication Story 3.3), le plan ne sera pas émis → comportement correct.

**5. Ordre stable des blockIds**
- `HostedBlockDao.getAllBlockIds()` retourne les IDs triés `ORDER BY block_id ASC` (Fix P9 Story 7.1).
- La signature `DEPARTURE_NOTICE` est donc reproductible, et le round-robin côté Super-Pair est déterministe.
- **Préserver l'ordre reçu** dans `plan.directives` — NE PAS trier/filtrer côté Super-Pair.

**6. Signature du plan — domain separation**
- Format imposé (pour que le nœud partant puisse vérifier sans ambiguïté) :
  ```
  "${superPeerNodeId}|${directives.joinToString("|") { "${it.blockId}:${it.destinationNodeId}" }}".toByteArray()
  ```
- Pattern similaire à `ReceiveAndHostBlockUseCase.ACK_DOMAIN_PREFIX` : séparateurs clairs, pas de collision possible avec d'autres payloads signés.
- **NE PAS** signer l'encodage Protobuf du plan (pas déterministe entre versions).

**7. `BlockSender` réutilise la signature ACK du destinataire (AC#3)**
- `BlockTransferClient` (impl par défaut de `BlockSender`) vérifie déjà la signature de l'ACK via `securityRepository.verifySignature` avec le `peer.identity.publicKeyBytes`.
- Le `MigrateBlockDirective.destinationPublicKeyBytes` transporté dans le plan permet au nœud partant d'instancier le `Peer` de destination avec la bonne clé publique → la vérification ACK fonctionne sans round-trip supplémentaire.
- Le `BlockAckMessage.blockHash` est bien SHA-256 du ciphertext (cf. `ReceiveAndHostBlockUseCase` l.55 `val computedHash = sha256hex(message.ciphertext)`), satisfaisant littéralement AC#3.

**8. Réutilisation de `ReceiveAndHostBlockUseCase` côté destination**
- Le nœud de destination voit arriver un `BLOCK_TRANSFER` classique (byte 0x20) via son `TcpConnectionManager.blockReceiverHandler` — **aucune modification requise** côté destination.
- Les validations existantes (taille max 2 MiB, hash SHA-256, IV 12 bytes, quota disque) s'appliquent automatiquement.
- Si la destination rejette (STORAGE_FULL, HASH_MISMATCH, IO_ERROR), l'erreur remonte au nœud partant qui logue — Story 7.3 ré-essaiera plus tard.

**9. Parallélisme via `coroutineScope` / `async`**
- Utiliser `coroutineScope { ... }` pour que l'échec d'une directive n'annule pas les autres (c'est `SupervisorJob` qu'on voudrait, mais `coroutineScope` convient car `sendBlock` retourne `Result<>` — pas d'exception propagée).
- **NE PAS** utiliser `GlobalScope` ni un scope non tracé — le handler TCP est exécuté dans `connectionScope` de `TcpConnectionManager` qui est déjà `SupervisorJob() + Dispatchers.IO`.

**10. `deleteByNodeId` avant `insertEntry` (AC#4)**
- L'ordre est important : supprimer toutes les entrées DHT du nœud partant PUIS insérer les nouvelles destinations. Ça évite un état transitoire où un `blockId` pointe à la fois sur l'ancien et le nouveau propriétaire (conflit CRDT possible).
- Si le bloc était répliqué ailleurs (K+1 copies), `deleteByNodeId` n'affecte que les entrées du nœud partant — les autres copies restent listées dans la DHT.

**11. `runGossipCycle()` est fire-and-forget synchrone**
- `GossipSyncUseCase.runGossipCycle()` retourne `Result<Unit>` rapidement (un cycle dure < 2s, fan-out=2, pas bloquant sur ACK).
- Logger l'échec mais ne **pas bloquer** l'achèvement du handler — la propagation continue via les cycles périodiques du service (boucle 2s dans `MobicloudP2PService`).

**12. Cas limite — nœud partant sans blocs hébergés**
- `notice.hostedBlockIds.isEmpty()` → aucun plan à émettre, aucune MàJ DHT requise. Log informatif puis return.
- C'est un cas attendu (nœud qui n'a jamais hébergé) — **pas un crash**.

**13. Cas limite — nœud partant unique du cluster**
- Si `candidates` est vide (aucun autre pair actif avec ip/port), pas de migration possible. Log puis return **sans** MàJ DHT (les entrées restent, Story 7.3 auto-réparation les traitera à terme).

### 📁 Arborescence cible après implémentation

```
app/src/main/kotlin/com/mobicloud/
├── data/p2p/tcp/
│   ├── DepartureChannel.kt                         ← MODIFIÉ (+ MIGRATION_PLAN byte 0x09 + MAX_MIGRATION_PLAN_BYTES)
│   └── TcpConnectionManager.kt                     ← MODIFIÉ (+ migrationPlanHandler + handleIncomingMigrationPlan + sendMigrationPlan)
├── data/network/service/
│   └── MobicloudP2PService.kt                      ← MODIFIÉ (+ câblage departureHandler et migrationPlanHandler)
├── domain/models/
│   └── MigrationPlanMessage.kt                     ← NOUVEAU (MigrateBlockDirective + MigrationPlanMessage)
└── domain/usecase/m06_m07_repair_migration/
    ├── MigrationPlanHandler.kt                     ← NOUVEAU (interface)
    ├── OrchestrateBlockMigrationUseCase.kt         ← NOUVEAU (implements DepartureNoticeHandler)
    └── ExecuteMigrationPlanUseCase.kt              ← NOUVEAU (implements MigrationPlanHandler)

app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/
├── OrchestrateBlockMigrationUseCaseTest.kt         ← NOUVEAU
└── ExecuteMigrationPlanUseCaseTest.kt              ← NOUVEAU
```

### Project Structure Notes

- **Alignement Clean Architecture (Architecture.md §Project Organization)** : `MigrationPlanHandler` et les use cases résident dans `domain/usecase/m06_m07_repair_migration/` (zéro import Android). `MigrationPlanMessage` dans `domain/models/`. Le transport TCP (`DepartureChannel`, `TcpConnectionManager`) reste strictement dans `data/p2p/tcp/`. Le service Foreground (`MobicloudP2PService`) câble les interfaces via Hilt.
- **Conformité Protobuf Forward-Compatibility (Architecture §4)** : `MigrationPlanMessage` et `MigrateBlockDirective` ont des valeurs par défaut pour chaque `@ProtoNumber` — tolérance aux versions mixtes.
- **Conformité Result<T> (Architecture §Error Handling)** : toutes les signatures des use cases retournent `Unit` côté handler (interface `DepartureNoticeHandler` / `MigrationPlanHandler` — fire-and-forget), MAIS chaque appel interne à `Repository` / `SecurityRepository` utilise `getOrElse` ou `.onFailure` pour ne jamais swallower silencieusement une exception.
- **Dispatcher** : les handlers TCP sont déjà exécutés dans `TcpConnectionManager.connectionScope = SupervisorJob() + Dispatchers.IO` — **pas de `withContext(Dispatchers.IO)` redondant** dans les use cases. Le `async` interne hérite du dispatcher parent.
- **Aucun conflit détecté** : la structure cible réutilise les chemins déjà établis par Story 7.1. Aucun nouveau module Hilt, aucune nouvelle table Room, aucun champ ajouté aux entités existantes.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 7.2] Acceptance Criteria littéraux
- [Source: _bmad-output/planning-artifacts/architecture.md#Expert Protocol Validations Module 7] Transfert aveugle opaque (bloc chiffré, jamais déchiffré)
- [Source: _bmad-output/planning-artifacts/architecture.md#Non-Functional Requirements Coverage] NFR-02 : Migration < 5s
- [Source: _bmad-output/implementation-artifacts/7-1-detection-du-depart-imminent-dun-noeud.md#Dev Notes] Story 7.1 — `DepartureNoticeHandler` laissé à null, câblage différé Story 7.2
- [Source: app/src/main/kotlin/com/mobicloud/data/p2p/tcp/DepartureChannel.kt:9] `0x09` réservé pour Story 7.2
- [Source: app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt:429–466] Pattern `handleIncomingDepartureNotice` + `sendDepartureNotice` à dupliquer symétriquement pour MIGRATION_PLAN
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m08_hosting/ReceiveAndHostBlockUseCase.kt:110–117] `MAX_BLOCK_PAYLOAD_BYTES` + `ACK_DOMAIN_PREFIX` réutilisés par `BlockSender` (AC#3)
- [Source: app/src/main/kotlin/com/mobicloud/data/p2p/tcp/BlockTransferClient.kt:81–94] Vérification signature ACK via `ACK_DOMAIN_PREFIX|receiverNodeId|blockHash`
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RunBullyElectionUseCase.kt:118–122] Source unique du flag `isSuperPair` dans `PeerRepository`
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/SendDepartureNoticeUseCase.kt:23,60–66] `MIGRATION_WINDOW_MS = 5_000L` — fenêtre pendant laquelle `ServerSocket` du nœud partant reste ouvert
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt:40–50] `runGossipCycle()` pour propagation post-MàJ DHT
- [Source: app/src/main/kotlin/com/mobicloud/data/local/dao/HostedBlockDao.kt:25] `ORDER BY block_id ASC` — ordre stable préservé dans le plan

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m]

### Debug Log References

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL (avertissement deprecation `fallbackToDestructiveMigration`, import manquant `kotlinx.coroutines.cancel` ajouté dans `NetworkChangeObserver.kt` — bug pré-existant du commit `fdda938` qui bloquait toute compilation).
- `:app:testDebugUnitTest --tests OrchestrateBlockMigrationUseCaseTest --tests ExecuteMigrationPlanUseCaseTest` — 11/11 tests passés (6 + 5, failures=0).
- Suite complète `:app:testDebugUnitTest` — 197/204 passés. Les 7 échecs sont pré-existants et non liés à Story 7.2 : `SendDepartureNoticeUseCaseTest` (3) et `ErasureProgressViewModelTest` (4), introduits dans le commit `fdda938 big update` qui n'avait jamais compilé avant cette story.

### Completion Notes List

- **AC#1** ✅ — `OrchestrateBlockMigrationUseCase` vérifie que le nœud local est Super-Pair (source unique `PeerRepository.peers`), filtre les candidats actifs hors partant/self, et attribue round-robin sur `notice.hostedBlockIds` (ordre stable préservé).
- **AC#2** ✅ — `ExecuteMigrationPlanUseCase.executeDirective` lit `HostedBlockPayload` (déjà chiffré) et émet `BlockTransferMessage` avec `ciphertext` + `iv` inchangés — aucun déchiffrement (transfert aveugle opaque). `BlockSender.sendBlock` réutilise `BlockTransferClient` existant (canal 0x20).
- **AC#3** ✅ — `BlockSender.sendBlock` (impl `BlockTransferClient`) vérifie déjà la signature ACK via `ACK_DOMAIN_PREFIX|receiverNodeId|blockHash` + `blockHash = SHA-256(ciphertext)`. La clé publique du destinataire est transportée dans `MigrateBlockDirective.destinationPublicKeyBytes` — vérification ACK sans round-trip supplémentaire.
- **AC#4** ✅ — MàJ DHT optimiste ordonnée : `dhtRepository.deleteByNodeId(senderNodeId)` AVANT `insertEntry` par directive, puis `gossipSyncUseCase.runGossipCycle()` immédiat. La MàJ DHT est tentée même si l'envoi du plan a timeout (test 6).
- **AC#5** ✅ — `withTimeoutOrNull(NFR02_BUDGET_MS = 5_000L)` autour de `sendMigrationPlan` côté Super-Pair. Côté partant, `PER_BLOCK_TIMEOUT_MS = 4_000L` + exécution `async { ... }.awaitAll()` parallèle (test 4 vérifie < 4s pour 3 blocs bloqués 2s chacun).
- **AC#6** ✅ — `OrchestrateBlockMigrationUseCase.kt` résidant dans `domain/usecase/m06_m07_repair_migration/` conformément à la contrainte.
- **Câblage DI** : `MobicloudP2PService` câble `departureHandler = orchestrateBlockMigrationUseCase` ET `migrationPlanHandler = executeMigrationPlanUseCase` avant `tcpConnectionManager.startServer()` (même pattern que les autres handlers Gossip/Block/DHT, évite la race window).
- **Hors-scope** : fix d'import manquant `kotlinx.coroutines.cancel` dans `NetworkChangeObserver.kt:13` — bug pré-existant qui bloquait la compilation de tout le module `:app` depuis le commit `fdda938` (empêchait tous les tests de tourner). Fix minimal (1 ligne) indispensable pour valider Story 7.2.

### File List

**Nouveau (main)** :
- `app/src/main/kotlin/com/mobicloud/domain/models/MigrationPlanMessage.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/MigrationPlanHandler.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/OrchestrateBlockMigrationUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/ExecuteMigrationPlanUseCase.kt`

**Modifié (main)** :
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/DepartureChannel.kt` (+ `MIGRATION_PLAN = 0x09`, `MAX_MIGRATION_PLAN_BYTES = 64_000`)
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt` (+ `migrationPlanHandler`, `handleIncomingMigrationPlan`, `sendMigrationPlan`, branche `MIGRATION_PLAN` dans `when`)
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (+ injections `orchestrateBlockMigrationUseCase` & `executeMigrationPlanUseCase`, câblage handlers avant `startServer()`)
- `app/src/main/kotlin/com/mobicloud/core/network/NetworkChangeObserver.kt` (+ import `kotlinx.coroutines.cancel` — fix hors-scope bloquant)

**Nouveau (test)** :
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/OrchestrateBlockMigrationUseCaseTest.kt` (6 tests : non-SP, signature invalide, 0 candidat, round-robin, MàJ DHT+Gossip, timeout NFR-02)
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m06_m07_repair_migration/ExecuteMigrationPlanUseCaseTest.kt` (5 tests : signature plan invalide, bloc absent, transfert aveugle, parallélisme, ownerId préservé)

## Change Log

- 2026-04-23 — Story 7.2 créée (ready-for-dev) : Orchestration migration proactive par le Super-Pair — `MigrationPlanMessage` Protobuf + canal 0x09 + `OrchestrateBlockMigrationUseCase` (DepartureNoticeHandler) + `ExecuteMigrationPlanUseCase` (MigrationPlanHandler) + MàJ DHT optimiste + Gossip — réutilise `BlockSender` existant pour transfert aveugle (AC#2) et vérification ACK signé (AC#3)
- 2026-04-23 — Story 7.2 implémentée et marquée `review` : 6 AC satisfaits, 11 tests JVM passants (6 + 5), câblage handlers TCP dans `MobicloudP2PService`. Fix hors-scope : import `kotlinx.coroutines.cancel` dans `NetworkChangeObserver.kt` (bug pré-existant bloquant la compilation depuis commit `fdda938`).
- 2026-04-23 — Story 7.2 re-review round 2 (post-hardening) : 8 patches appliqués (garde anti-self exécuteur, filtre candidats orchestrateur renforcé, `toSigHex` sign-extend fix + consolidation dans `domain/util/HexEncoding.kt`, port upper-bound, distinction `Result.failure` vs null payload, log plan vide, test parallélisme avec destinations distinctes). 9 findings différés (versioning protocole, `withTimeout` per-directive, validation adresse, NOTICE domain-prefix, etc.). Compilation + tests 7.2 : SUCCESS.
- 2026-05-18 — Story 7.2 multi-layer review round 3 (Blind Hunter + Edge Case Hunter + Acceptance Auditor) : AC#1–#6 tous vérifiés conformes. 8 patches appliqués (fix `ownerId = payload.ownerId` propage l'uploader original ; `Semaphore(8)` borne parallélisme ; `runCatching` wrap async ; guards `signatureBytes.isEmpty()` ; `isRoutableIpLiteral` validation IP ; gossip sur `applicationScope` ; bump `MAX_MIGRATION_PLAN_BYTES` 64K→128K ; 3 tests manquants). 15 findings différés (canonical encoding payload signé, EC-aware round-robin, NIO socket refactor, etc.). Tests : 15/15 migration. Suite complète 594/595 (failure isolée hors scope).

### Review Findings

- [x] [Review][Patch] **Durcir signature du plan : inclure destinationIp/port/publicKeyBytes** [OrchestrateBlockMigrationUseCase.kt, ExecuteMigrationPlanUseCase.kt] — appliqué. Payload signé étendu à `blockId:destNodeId:destIp:destPort:destPubKeyHex`. Helper `toSigHex()` déclaré en bas de chaque fichier.
- [x] [Review][Defer] **Nonce/timestamp + lien au DEPARTURE_NOTICE** — résolu D2=2, déféré (cohérent Story 7.1). Voir deferred-work.md.
- [x] [Review][Defer] **NFR-02 non-enforce sur écriture TCP bloquée** — résolu D3=3, déféré. Refactor NIO hors scope. Voir deferred-work.md.
- [x] [Review][Patch] **Dédupliquer `hostedBlockIds` avec `.distinct()`** [OrchestrateBlockMigrationUseCase.kt] — appliqué. `uniqueBlockIds` préserve l'ordre de première apparition, log `Doublons détectés` si dédup effective. Round-robin et signature basés sur `uniqueBlockIds`.
- [x] [Review][Patch] **`coroutineScope` → `supervisorScope`** [ExecuteMigrationPlanUseCase.kt] — appliqué. Import + body + `return@supervisorScope` labels.
- [x] [Review][Patch] **Snapshot unique de `peerRepository.peers.value`** [OrchestrateBlockMigrationUseCase.kt] — appliqué. `val peersSnapshot = peerRepository.peers.value` dérive les 3 vues (self-check, lookup partant, candidats).
- [x] [Review][Patch] **Validation défensive `destinationIp`/`destinationPort`** [ExecuteMigrationPlanUseCase.kt:executeDirective] — appliqué. Guard `if (ip.isBlank() || port <= 0) { log + return }` en tête de `executeDirective`.
- [x] [Review][Patch] **Test 6 — `runCurrent()` + marge 1000ms** [OrchestrateBlockMigrationUseCaseTest.kt] — appliqué. `runCurrent()` avant et après `advanceTimeBy(4_500L)` ; advance final de `1_000L` dépasse nettement `NFR02_BUDGET_MS=5_000L`.
- [x] [Review][Patch] **Imports inutilisés dans `ExecuteMigrationPlanUseCaseTest`** — appliqué. Supprimés `kotlinx.coroutines.launch` et `kotlinx.coroutines.test.advanceTimeBy`. (OrchestrateTest conserve ces imports : utilisés par test 6.)
- [x] [Review][Defer] **`handleIncomingMigrationPlan` catch(Exception) large** [TcpConnectionManager.kt:handleIncomingMigrationPlan] — deferred, pre-existing. Pattern identique au `handleIncomingDepartureNotice` existant (Story 7.1) ; refactoring logging broader out of scope pour 7.2.
- [x] [Review][Defer] **Boucle séquentielle `directives.forEach { dhtRepository.insertEntry(...) }`** [OrchestrateBlockMigrationUseCase.kt:~110] — deferred, pre-existing. Pour N élevé (~250 directives, plafond du spec), N écritures Room séquentielles pourraient impacter NFR-02 hors fenêtre `withTimeoutOrNull`. À profiler ; batch insert ou parallélisation si mesure le justifie.
- [x] [Review][Defer] **Couverture tests manquante : handler nullability, mutation concurrente peers, throw dans coroutineScope** [OrchestrateBlockMigrationUseCaseTest.kt, ExecuteMigrationPlanUseCaseTest.kt] — deferred, pre-existing. Ne sont pas des bugs de code ; extension de test pouvant attendre si la logique ne change pas.

### Review Findings — Round 2 (re-review post-hardening, 2026-04-23)

- [x] [Review][Patch] **Garde anti-self manquante côté exécuteur** [ExecuteMigrationPlanUseCase.kt:executeDirective] — appliqué. Ajout de `if (directive.destinationNodeId == localNodeId) { log + return }` après la garde ip/port.
- [x] [Review][Patch] **Garde défensive asymétrique sur `ipAddress`/`port` côté orchestrateur** [OrchestrateBlockMigrationUseCase.kt:80-85] — appliqué. Filtre candidats renforcé : `p.ipAddress?.isNotBlank() == true && (p.port ?: 0) in 1..65535`.
- [x] [Review][Patch] **`toSigHex()` sign-extension Byte→Int** [ExecuteMigrationPlanUseCase.kt, OrchestrateBlockMigrationUseCase.kt] — appliqué. Fix `"%02x".format(it.toInt() and 0xff)` dans le nouveau helper partagé.
- [x] [Review][Patch] **Port upper-bound non validé dans la garde défensive** [ExecuteMigrationPlanUseCase.kt:executeDirective] — appliqué. `destinationPort <= 0` renforcé en `destinationPort !in 1..65535`.
- [x] [Review][Patch] **`toSigHex()` duplicé en `private` dans 2 fichiers** [ExecuteMigrationPlanUseCase.kt, OrchestrateBlockMigrationUseCase.kt] — appliqué. Helper consolidé dans `domain/util/HexEncoding.kt` (`internal fun ByteArray.toSigHex()`). Les 2 définitions file-private supprimées, import partagé ajouté.
- [x] [Review][Patch] **Log "absent localement" collapse `Result.failure` et payload null** [ExecuteMigrationPlanUseCase.kt:81-87] — appliqué. `result.fold(onSuccess, onFailure)` distingue : failure → log "Lecture bloc échouée: <msg>" ; success(null) → log "absent localement". Pas de double log.
- [x] [Review][Patch] **Test parallélisme utilise le même `destinationNodeId` pour 3 directives** [ExecuteMigrationPlanUseCaseTest.kt:165-169] — appliqué. 3 `destinationNodeId`/`ip`/`port` distincts (`d1`/`d2`/`d3`, `10.0.0.1-3`, ports `6001-3`) + 3 `coVerify(exactly=1)` par destination pour détecter un flatten.
- [x] [Review][Patch] **Plan signé vide accepté silencieusement côté exécuteur** [ExecuteMigrationPlanUseCase.kt:57-69] — appliqué. Early-return avec log `"Plan vide reçu de <sp8> — aucune action"` après vérif identité.
- [x] [Review][Defer] **Versioning du protocole de signature (rollout mixte)** [MigrationPlanMessage.kt] — deferred, closed cluster. Tout SP old-format produit une signature que les exécuteurs nouveau-format rejettent silencieusement. Cluster PFE restart complet = pas de risque immédiat. À adresser si déploiement en vague.
- [x] [Review][Defer] **Round-robin divergence entre SPs pre/post-dedup** [OrchestrateBlockMigrationUseCase.kt:135-137] — deferred, même motif que #9 (rollout mixte).
- [x] [Review][Defer] **`PER_BLOCK_TIMEOUT_MS` non-enforcé via `withTimeout` côté exécuteur** [ExecuteMigrationPlanUseCase.kt:executeDirective] — deferred. Le timeout est passé à `BlockSender.sendBlock` qui ne l'applique qu'au `soTimeout` (lectures) ; `connect()` + write bloquant peuvent dépasser. Wrapper avec `withTimeout(PER_BLOCK_TIMEOUT_MS)` possible mais interagit avec la décision NIO globale (cf. entrée existante deferred-work.md).
- [x] [Review][Defer] **Collision payload sur `:` / `|` si nodeId/IP contient ces chars** [OrchestrateBlockMigrationUseCase.kt:149-151, ExecuteMigrationPlanUseCase.kt:33-35] — deferred. nodeId est hex SHA256 (pas de collision), IPv4 non plus. IPv6 literal (`fe80::1`) casserait la séparation. Fix durable : valider charset en entrée ou encoder via URL-encoding avant signature.
- [x] [Review][Defer] **Validation adresse destination (0.0.0.0, loopback, link-local)** [ExecuteMigrationPlanUseCase.kt:executeDirective, OrchestrateBlockMigrationUseCase.kt:80-85] — deferred. Ni l'orchestrateur ni l'exécuteur ne filtrent ces adresses. Requiert une utility `InetAddresses.isRoutable()` partagée ; hors scope 7.2.
- [x] [Review][Defer] **DHT polluée par `insertEntry` vers destination devenue offline entre étape 6 et 7** [OrchestrateBlockMigrationUseCase.kt:139-148] — deferred. Le snapshot couvre les étapes 1→3 mais l'étape 7 ne revérifie pas `dest.isActive`. Story 7.3 (auto-réparation) est le recovery path documenté.
- [x] [Review][Defer] **Test #4 assertion `elapsed < 4_000L` fragile** [ExecuteMigrationPlanUseCaseTest.kt:184] — deferred, minor. Toute introduction d'un `withTimeout(PER_BLOCK_TIMEOUT_MS)` ferait passer le test à exactement 4_000ms → flip false. Laisser une marge (`elapsed < 4_500L`).
- [x] [Review][Defer] **Test #6 n'assert pas la cancellation effective du send** [OrchestrateBlockMigrationUseCaseTest.kt:213-240] — deferred. Le test vérifie le log `"Timeout envoi"` mais pas que `sendMigrationPlan` est réellement annulé. Une mauvaise configuration `withTimeoutOrNull` où le send termine post-timeout ne serait pas détectée.
- [x] [Review][Defer] **`DEPARTURE_NOTICE` signed payload sans domain-prefix tag** [OrchestrateBlockMigrationUseCase.kt:54] — deferred, pré-existant Story 7.1. Le payload `"${senderNodeId}:${blockIdsJoined}"` n'a pas de tag `"DEPARTURE_NOTICE_V1|"` — pas de collision inter-protocole aujourd'hui mais brittle aux futurs ajouts.

### Review Findings — Round 3 (multi-layer adversarial, 2026-05-18)

Review parallèle 3 couches (Blind Hunter + Edge Case Hunter + Acceptance Auditor) sur le diff `51d5e5a^..400ddc9`. AC#1–#6 tous vérifiés conformes par l'Acceptance Auditor — aucune violation critique. Findings additionnels :

- [x] [Review][Dismiss] **`originalFileSize = 0L` perdu sur bloc migré** [ExecuteMigrationPlanUseCase.kt:228] — **faux positif** : le downloader lit `originalFileSize` depuis `catalog.originalFileSize` (CatalogEntry DHT) via `AssembleDownloadedFileUseCase.kt:84,162`, pas depuis `BlockTransferMessage`. Convention identique sur `RespondToBlockRequestUseCase.kt:46` (`0L` côté répondeur — non-bloquant). La source autoritative est la DHT propagée par CRDT merge (`MergeCatalogEntriesUseCase.kt:41`). Commentaire dev correct.

- [x] [Review][Defer] **MIGRATION_PLAN accepté de n'importe quelle socket (pas de binding IP)** [TcpConnectionManager.kt:handleIncomingMigrationPlan + ExecuteMigrationPlanUseCase.kt:151] — décision : signature EC-P256 suffit comme barrière (défense en profondeur cryptographique, pas réseau). Un check IP source casserait l'inter-cluster via relais HA WebSocket (l'IP observée serait celle du relais, pas du SP émetteur).

- [x] [Review][Defer] **Race cancellation : `stopServer()` annule les `sendBlock` en cours** [SendDepartureNoticeUseCase.kt:63-68 + ExecuteMigrationPlanUseCase.kt:supervisorScope] — décision : s'appuyer sur Story 7.3 (auto-réparation détection blocs sous-répliqués). Cohérent avec la conception "MAJ DHT optimiste + convergence eventuelle" déjà documentée (Constraint #2). Les blocs perdus en bordure de fenêtre seront re-distribués automatiquement.

- [x] [Review][Patch] **`ownerId = localNodeId` perd l'identité de l'uploader original** [ExecuteMigrationPlanUseCase.kt:137] — bug réel, pas décision. Vérifié : `HostedBlockPayload.ownerId` stocke l'uploader d'origine (via `ReceiveAndHostBlockUseCase.kt:77`). `RespondToBlockRequestUseCase.kt:41` utilise déjà la convention correcte `ownerId = payload.ownerId`. Le commentaire ligne 137 est faux : le nœud partant n'est pas l'uploader, c'est juste l'hébergeur courant. Fix : `localNodeId` → `payload.ownerId` (1 ligne, aligne sur la convention `RespondToBlockRequestUseCase`).

- [x] [Review][Patch] **Parallélisme non-borné côté exécuteur (~250 sockets concurrents)** [ExecuteMigrationPlanUseCase.kt:async+awaitAll] — un plan plein (~250 directives) lance 250 `async` simultanés → saturation FD/radio sur 4G, NFR-02 violé sous contention. Ajouter `Semaphore(8)` ou chunked dispatch.

- [x] [Review][Patch] **`MAX_MIGRATION_PLAN_BYTES = 64_000` trop serré vs NOTICE 16KB** [DepartureChannel.kt:19] — un NOTICE de 250 blocIds + directive ~250 bytes/dir + pubkey hex ~64 bytes peut dépasser 64KB silencieusement. Bumper à ≥128KB ou cap explicite sur `directives.size`.

- [x] [Review][Patch] **NFR-02 budget non-enforce sur la phase DHT** [OrchestrateBlockMigrationUseCase.kt:391-417] — `withTimeoutOrNull(5s)` n'enveloppe que `sendMigrationPlan`. Les N `dhtRepository.insertEntry` séquentiels + `gossipSyncUseCase.runGossipCycle()` peuvent dépasser largement 5s. Wrap full body dans `withTimeout` ou batch DHT.

- [x] [Review][Patch] **`ipAddress` non-validée (0.0.0.0, loopback, link-local passent)** [OrchestrateBlockMigrationUseCase.kt:80-85, ExecuteMigrationPlanUseCase.kt:executeDirective] — filtres actuels acceptent `127.0.0.1`, `0.0.0.0`, `255.255.255.255`. Ajouter check `InetAddresses.isRoutableUnicast()`.

- [x] [Review][Patch] **`awaitAll()` rethrow malgré `supervisorScope`** [ExecuteMigrationPlanUseCase.kt:async+awaitAll] — si un `async` lance un Throwable non-géré (NPE, OOM), `awaitAll` re-throw et coupe les frères. Wrapper chaque `async` dans `runCatching {}` pour garantir que toutes les directives s'exécutent.

- [x] [Review][Patch] **`runGossipCycle()` bloque le `connectionScope`** [OrchestrateBlockMigrationUseCase.kt:416] — un cycle gossip prend secondes et bloque le worker TCP. Dispatcher sur `applicationScope.launch { ... }` ou scope indépendant.

- [x] [Review][Patch] **`signatureBytes.isEmpty()` non-rejetée explicitement** [OrchestrateBlockMigrationUseCase.kt + ExecuteMigrationPlanUseCase.kt:vérif signature] — `verifySignature(data, byteArrayOf(), pk)` dépend de l'impl JCA. Ajouter early-reject si `signatureBytes.isEmpty()`.

- [x] [Review][Patch] **`soTimeout` absent côté serveur entrant MIGRATION_PLAN (slowloris)** [TcpConnectionManager.kt:handleIncomingMigrationPlan] — `sendMigrationPlan` set `soTimeout=3000` côté client mais le handler entrant n'a pas de read deadline. Pair lent tient le worker indéfiniment. Set `socket.soTimeout` côté serveur avant `readFully`.

- [x] [Review][Patch] **3 tests manquants (isActive guard, plan vide receiver, anti-self receiver)** [OrchestrateBlockMigrationUseCaseTest.kt + ExecuteMigrationPlanUseCaseTest.kt] — coverage gaps acknowledged par Acceptance Auditor : (i) Test super-peer avec `isActive=false` doit court-circuiter, (ii) Test plan reçu avec `directives=emptyList()` doit log + return, (iii) Test `destinationNodeId == localNodeId` doit ignorer la directive.

- [x] [Review][Defer] **Collision payload signature via `:` / `|` (IPv6 literal `fe80::1`)** — deferred, déjà acté Round 2. Pas de risque IPv4-only ; fix durable = canonical encoding (CBOR ou Protobuf signed bytes).
- [x] [Review][Defer] **Replay sans nonce/timestamp** — **résolu post-7.2** dans commit `b1de98a` (anti-replay sur DepartureNotice/MigrationPlan/ReplicationPlan via `timestampMs` + skew window 30s). Hors scope review du diff `400ddc9` mais déjà adressé sur HEAD.
- [x] [Review][Defer] **Round-robin peut concentrer plusieurs fragments d'un même fichier EC sur 1 hôte** — deferred. Spec accepte round-robin déterministe ; Story 7.3 (auto-réparation) rattrape les blocs sous-répliqués. Fix durable = group-by-fileHash + spread.
- [x] [Review][Defer] **Test 4 parallélisme repose sur `TestDispatcher` virtual time** — deferred, faux sentiment de couverture mais bug réel improbable. Remplacer par real dispatcher + CountDownLatch si refactor.
- [x] [Review][Defer] **`Socket().connect(3000)` bloquant, non-coopératif avec coroutine cancellation** — deferred, lié à la décision NIO globale (cf. entry existante deferred-work.md round 1).
- [x] [Review][Defer] **Test 6 timeout n'assert pas la cancellation effective de `sendMigrationPlan`** — déjà deferred Round 2.
- [x] [Review][Defer] **Test 2 signature invalide ne stub pas `signData` (fragile sur réordonnancement)** — deferred, test brittleness mineure.
- [x] [Review][Defer] **`@Volatile var migrationPlanHandler` réassignable / re-wiring à chaque `onStartCommand`** — deferred. Pattern identique aux autres handlers Story 7.1+ ; refactor cross-cutting.
- [x] [Review][Defer] **`destinationPort = 0` filtré silencieusement côté orchestrateur** — deferred. Pas de log d'attrition ; ajouter diag log si bug de port-discovery suspecté.
- [x] [Review][Defer] **`sendMigrationPlan` n'a pas de cleanup explicite sur cancel post-`withTimeoutOrNull`** — deferred, lié à la décision NIO (cf. `Socket().connect` bloquant).
- [x] [Review][Defer] **`handleIncomingMigrationPlan` pas de skip(len) ni rate-limit per-IP sur length invalide** — deferred. Pré-existant sur tous les handlers TCP ; hardening transversal hors scope 7.2.
- [x] [Review][Defer] **`PER_BLOCK_TIMEOUT_MS` non-enforce via `withTimeout` côté exécuteur** — déjà deferred Round 2.
- [x] [Review][Defer] **`Peer` construit côté exécuteur sans `isActive`/`isSuperPair`** — deferred, dépendance silencieuse aux defaults du data class.
- [x] [Review][Defer] **Round-robin déterministe = prédictible pour Sybil attaque** — deferred. Acceptable en cluster fermé PFE ; randomisation crypto si déploiement adversarial.

**Round 3 — Patches appliqués (2026-05-18)** :
- `ExecuteMigrationPlanUseCase.kt` : `ownerId = payload.ownerId` (était `localNodeId`) ; `Semaphore(MAX_CONCURRENT_DIRECTIVES = 8)` autour des `async` ; `runCatching` wrap chaque directive (empêche `awaitAll` rethrow) ; guard `signatureBytes.isEmpty()` early-reject ; `isRoutableIpLiteral(ip)` remplace `isBlank()` (rejette loopback/any-local/link-local/multicast).
- `OrchestrateBlockMigrationUseCase.kt` : `@ApplicationScope CoroutineScope` injecté ; gossip cycle déplacé dans `applicationScope.launch` (ne bloque plus le worker `connectionScope`) ; guard `notice.signatureBytes.isEmpty()` early-reject ; filtre candidats utilise `isRoutableIpLiteral` au lieu de `isNotBlank()`.
- `DepartureChannel.kt` : `MAX_MIGRATION_PLAN_BYTES` 64K → 128K.
- `NetworkAddressUtil.kt` (nouveau) : helper `isRoutableIpLiteral` partagé orchestrateur/exécuteur.
- Tests : +3 tests (super-peer inactif court-circuite ; plan signé vide ; directive destination == self) ; test 5 mis à jour pour vérifier `ownerId = payload.ownerId` (au lieu de `localNodeId`).
- Faux positifs Round 3 reclassés dismiss : `soTimeout` côté serveur (déjà en place ligne 169 via `INCOMING_READ_TIMEOUT_MS=3000`) ; NFR-02 wrapper sur full body (casserait Constraint #2 + Test 6 explicite).

Build : `:app:compileDebugKotlin` SUCCESS. Tests migration : **15/15 passés** (Orchestrate 7 + Execute 7 + PlansAntiReplay 1). Suite complète `:app:testDebugUnitTest` : 594/595 (la seule failure `RunBullyElectionUseCaseTest` est pré-existante, hors scope 7.2 — confirmé via `git stash` + re-run).

**Dismissed (12)** — non-issues / intent documenté :
- DHT update optimiste après timeout (Constraint #2 explicite, Story 7.3 = recovery)
- selfIsSuperPair via PeerRepository (contrat vérifié : `RunBullyElectionUseCase.kt:143` enregistre self avec `isSuperPair=true`)
- `MigrationPlanHandler?` null log-only (Constraint #11 fire-and-forget)
- `runGossipCycle()` log-only failures (Constraint #11)
- `peer.port!!` après filtre (filter est correct)
- Trailing `Unit` après `awaitAll()` (cosmétique)
- Commentaire `// 0x08/0x09 libres` stale (cosmétique)
- `HexEncoding.internal` cross-module (projet mono-module)
- Payload signé étendu IP/port/pubkey vs spec (durcissement Round 1 acté, plus fort que la spec)
- `coroutineScope` → `supervisorScope` (durcissement Round 1 acté)
- Comment trompeur `awaitAll` cancellation (subsumé par finding patch sur `runCatching`)
