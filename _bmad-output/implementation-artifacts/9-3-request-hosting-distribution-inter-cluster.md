# Story 9.3 : RequestHosting — Distribution Inter-Cluster

Status: done

## Story

En tant que Super-Pair distribuant les fragments d'un fichier,
Je veux pouvoir placer un bloc sur un Super-Pair **d'un autre cluster** lorsque mon cluster local n'a pas la capacité ou la disponibilité requise,
Afin que la règle de réplication R=3 soit respectée même quand le cluster local est saturé / sous-peuplé, en s'appuyant sur l'annuaire HA enrichi en 9.1/9.2 (`clusterId`, `freeBytes`).

## Acceptance Criteria

1. **Given** un `RelayEvent.PeerList` est reçu par `SignalingRepositoryImpl`
   **When** la liste est traitée
   **Then** `SignalingRepository` expose un nouveau `StateFlow<List<RelayPeer>>` `latestPeers` (port domain) mis à jour avec la dernière liste **brute** reçue (avec `clusterId` et `freeBytes` non filtrés, sauf l'auto-référence retirée), distinct du `peerRepository.peers` qui ne porte ni `clusterId` ni `freeBytes`.

2. **Given** un bloc de taille `blockSize` à placer en inter-cluster
   **When** `RequestInterClusterHostingUseCase.selectRemoteHost(blockSize)` est invoqué
   **Then** il retourne le `RelayPeer` candidat respectant **TOUS** les invariants :
   - `isSuperPair == true`
   - `clusterId.isNotBlank()` (rejette `""` legacy/coerce-invalide — voir deferred W-9.2-1, W-9.2-2)
   - `clusterId != localClusterId` (cluster distant strict)
   - `freeBytes >= blockSize` (capacité suffisante)
   - `ip` non vide ET `port > 0`
   Et trié par `freeBytes` décroissant (capacité maximale en tête). Retourne `null` si aucun candidat ne satisfait ces invariants.

3. **Given** `DistributeEncryptedBlocksUseCase.distribute()` doit placer le fragment `i`
   **When** la liste `activePeers` (cluster local) est vide OU le pair primaire local ET son fallback local échouent tous deux
   **Then** une dernière tentative est faite via `RequestInterClusterHostingUseCase.selectRemoteHost(blockSize = msg.ciphertext.size)` ; si un candidat distant existe, `blockSender.sendBlock(msg, remotePeer, MAX_ACK_TIMEOUT_MS)` est appelé (le routage direct/relais est délégué à `BlockSenderWithRelay` — Story 8.3) et, en cas d'ACK, la `DeliveryRecord` est ajoutée comme une livraison normale.

4. **Given** ni le placement local ni le placement inter-cluster ne réussissent pour un fragment
   **When** la boucle de distribution se termine
   **Then** le comportement actuel est préservé : `Result.failure` si `dataBlocksConfirmed < k`. Aucune régression sur les chemins purement locaux (cluster local suffisant ⇒ aucun appel inter-cluster).

5. **Given** le candidat distant est sélectionné
   **When** le bloc est livré avec succès
   **Then** `insertDhtEntryUseCase(blockId, remoteNodeId, remoteIp, remotePort)` enregistre le placement comme pour une livraison locale — la lecture inter-cluster (Story 9.4) consommera cette entrée DHT.

6. **Given** `latestPeers` est interrogé immédiatement au démarrage avant tout `GET_PEERS`
   **When** aucun `PeerList` n'a encore été reçu
   **Then** la valeur initiale est `emptyList()` et `selectRemoteHost(...)` retourne `null` sans erreur (no-op gracieux).

7. **Given** le seul candidat distant a `freeBytes` très proche de `blockSize` (ex. `freeBytes = blockSize + 1024`)
   **When** la sélection est faite
   **Then** il est retenu (comparaison `>=`, pas de marge de sécurité ajoutée — la marge sera gérée par debouncing producteur en 9.x ; voir deferred W-9.2-3).

## Context / Notes développeur

### Vue d'ensemble de la story

Story C de l'**Epic 9 — Stockage Inter-Cluster**, qui consomme les champs ouverts en 9.1 (`clusterId`) et 9.2 (`freeBytes`) :

- **9.1 (done)** — `clusterId` UUID v4 publié via `REGISTER_PEER`, stocké côté serveur.
- **9.2 (done)** — `freeBytes` snapshot publié + `clusterId`/`freeBytes` exposés dans `GET_PEERS`.
- **9.3 (cette story)** — **côté écriture** : sélection d'un Super-Pair distant + placement de bloc via le transport relais existant (Story 8.3).
- **9.4 (next)** — **côté lecture** : récupération d'un bloc hébergé en inter-cluster (DHT → relais).

**Ce qui n'est PAS dans cette story :**
- Pas de modification du protocole REGISTER_PEER / GET_PEERS / UPLOAD côté serveur — tout le travail est côté Android.
- Pas de nouveau message dédié `REQUEST_HOSTING` au niveau réseau : le message `BlockTransferMessage` existant + le routage `BlockSenderWithRelay` (try-direct-then-relay, Story 8.3) suffisent. Le « request » est implicite : envoyer le bloc à un nœud distant, c'est lui demander de l'héberger (logique `ReceiveAndHostBlockUseCase` côté récepteur, déjà existante).
- Pas de logique de récupération inter-cluster (Story 9.4).
- Pas de sentinelle `null`/`-1` pour `freeBytes==0` (deferred W-9.2-1 — on rejette par filtre `clusterId.isBlank()` qui couvre déjà le cas legacy).
- Pas de debouncing/marge sur `freeBytes` (deferred W-9.2-3).
- Pas de backpressure ni circuit-breaker spécifique inter-cluster (le TTL serveur 60s + reload `GET_PEERS` au prochain cycle Bully suffit).

### Architecture / Topologie

> *« HDFS Federation : plusieurs NameNodes, chacun gère son namespace. MobiCloud Federation : plusieurs Super-Pairs, chacun gère son cluster ; mutualisation opportuniste de capacité via l'annuaire HA. »*
> [Source: _bmad-output/planning-artifacts/architecture-connectivity-and-clustering.md#3.4 Inter-cluster L80-83]

L'inter-cluster est explicitement marqué « extension future » dans l'architecture v1, mais l'IRL test confirme que **4G↔WiFi via HA Relay fonctionne** (cf. memory `project_intercluster_test_result.md`). 9.3 transforme cette capacité réseau en politique de placement.

**Diagramme :**

```
┌──── Cluster A (mon cluster, clusterId=A) ────┐
│  Mehdi (Super-Pair) ─ moi                    │
│  Lina, Nadia, Karim, …                       │
└──────────────┬───────────────────────────────┘
               │ saturé / sous-peuplé
               │ → fallback inter-cluster
               ▼
       ┌─── HA Relay ───┐
       │  GET_PEERS     │  ← latestPeers (StateFlow)
       │  clusterId+freeBytes
       └───────┬────────┘
               │ select (clusterId≠A, freeBytes≥|bloc|)
               ▼
┌──── Cluster B (clusterId=B) ────┐
│  Sara (Super-Pair distant)      │  ← UPLOAD/FORWARD via relais
│  ReceiveAndHostBlockUseCase     │     (BlockSenderWithRelay 8.3)
└─────────────────────────────────┘
```

### Fichiers à modifier (≈ 6 fichiers + tests)

| Fichier | Modification |
|---|---|
| `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt` | Ajouter `val latestPeers: StateFlow<List<RelayPeer>>` (port). |
| `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt` | Implémenter `latestPeers` (MutableStateFlow privé alimenté par `processPeerList`). Ne pas filtrer ici. |
| `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/RequestInterClusterHostingUseCase.kt` | **Nouveau** — `selectRemoteHost(blockSize: Int): RelayPeer?` + mappage `RelayPeer → Peer` pour `BlockSender`. |
| `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` | Injecter le nouveau use-case ; ajouter le 3ᵉ niveau de fallback (local primary → local fallback → inter-cluster). |
| `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/RequestInterClusterHostingUseCaseTest.kt` | **Nouveau** — couvre tous les filtres (clusterId vide, == local, freeBytes insuffisant, isSuperPair=false, ip/port absents) + tri freeBytes décroissant. |
| `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt` | Étendre — `latestPeers.value` reflète bien la dernière `PeerList` reçue. |
| `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCaseTest.kt` (existant ou à créer si absent) | Étendre — fallback inter-cluster invoqué quand local échoue ; chemin local heureux inchangé. |

### Guardrails critiques

- **Invariant `clusterId.isNotBlank()`** : la sélection rejette tout pair distant sans `clusterId` valide. Cela couvre simultanément (a) les nœuds legacy pré-9.1, (b) les nœuds dont le serveur a coercé un `clusterId` invalide en `""` (deferred W-9.2-2). Aligné avec l'invariant thèse « cluster = unité d'intention partagée ».
- **Pas de mutation `PeerRepository`** : `latestPeers` reste un canal **mémoire-seule** parallèle à `PeerRepository.peers`. **Ne PAS persister** `clusterId`/`freeBytes` distants (rappel commentaire L86-92 de `SignalingRepositoryImpl.processPeerList`). Conforme au "TTL 60s annuaire" et au memory `project_super_peer.md`.
- **`BlockSender` est déjà inter-cluster-aware** via Story 8.3 (`BlockSenderWithRelay` — try direct TCP, fallback UPLOAD/FORWARD). **Ne pas dupliquer** la logique relais dans 9.3. La sélection 9.3 produit un `Peer` (domain), `BlockSender` route.
- **Mappage `RelayPeer → Peer`** : créer un `Peer` éphémère (pas d'`identity.publicKey`, pas d'insertion DB) — `NodeIdentity(nodeId, ByteArray(0))`, `ipAddress = ip`, `port = port`, `isActive = true`. C'est le même pattern que `processPeerList` mais sans toucher à `PeerRepository`.
- **`localClusterId`** : lu une fois en début de `distribute()` via `nodeSettingsRepository.getSettings().clusterId`. NE PAS appeler `getSettings()` à chaque fragment (perf + déjà observé risque DB en 9.2 D1).
- **Pas de modification du serveur Node.js** : 9.3 est 100% client-side.
- **Pas de migration Room** : `DhtEntry` accepte déjà n'importe quel `nodeId/ip/port` — le `nodeId` distant est juste une chaîne UUID inconnue de `PeerRepository` local, c'est attendu.
- **Backward-compat distribution locale** : si **aucun** appel `selectRemoteHost(...)` n'est nécessaire (cluster local suffisant), 0 régression. Le path inter-cluster est strictement opt-in via le 3ᵉ niveau de fallback.
- **Échec ACK distant** : géré par `BlockSender.sendBlock` (timeout `MAX_ACK_TIMEOUT_MS = 30s`). Pas de retry inter-cluster supplémentaire ici — si le candidat n°1 échoue, on **n'essaie pas** le n°2 dans cette story (simplicité ; itération possible en correct-course).
- **Concurrence `latestPeers`** : `MutableStateFlow.value = ...` est thread-safe ; `processPeerList` y écrit, le use-case y lit via `.value` (snapshot). Aucun `collect`/coroutine requise dans le use-case.

### Concept `RequestHosting` (pourquoi pas de message dédié)

Au moment du design 9.3, deux options ont été envisagées :

| Option | Avantage | Coût |
|---|---|---|
| **A** Nouveau message `REQUEST_HOSTING(blockId, size)` côté relais → Super-Pair distant ACK avant `UPLOAD` | Permet au distant de refuser sans transfert | +1 round-trip + 1 message protocolaire + côté serveur |
| **B** Envoyer directement le bloc via `UPLOAD/FORWARD` ; le distant accepte ou drop côté `ReceiveAndHostBlockUseCase` | 0 modification serveur, réutilise Story 8.3 | Bande passante gaspillée si refus |

**Choix : Option B.** Le filtre côté émetteur (`freeBytes >= blockSize`) garantit déjà ≥ 99% de succès — un refus côté récepteur est marginal. Cohérent avec le « best-effort snapshot » de 9.2 et la philosophie « le moins centralisé possible » (memory `feedback_minimize_centralization.md` : on n'ajoute pas de surface protocolaire serveur).

### Ce qui justifie la thèse

L'inter-cluster transforme MobiCloud d'une « collection de N clusters indépendants » en **fédération opportuniste**, sans composant central :
- Pas de meta-tracker, pas de coordinateur global.
- Mutualisation décidée localement par chaque Super-Pair, en consultant l'annuaire HA (qui n'arbitre pas, il publie).
- Aligne avec **HDFS Federation** (analogie déjà documentée).

**Phrase soutenance** : *« 9.3 démontre que la fédération inter-cluster ne nécessite ni nouveau message protocolaire ni serveur supplémentaire — l'annuaire enrichi en 9.1/9.2 et le transport relais 8.x suffisent. La décision de placement reste 100% locale au Super-Pair émetteur. »*

---

## Tasks / Subtasks

### 🗄️ Bloc Domain (Task 1) — Port `latestPeers` + use-case

- [x] **Task 1** : Exposer `latestPeers` sur `SignalingRepository` (AC: 1, 6)
  - [x] Subtask 1.1 : Dans `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt`, ajouter :
    ```kotlin
    /**
     * Snapshot mémoire de la dernière liste reçue via GET_PEERS.
     * Volatile (TTL serveur 60s) — NE PAS persister, NE PAS substituer à PeerRepository.
     * Utilisé par les use-cases inter-cluster (9.3, 9.4) pour consommer clusterId/freeBytes.
     * Initialement emptyList() avant le premier PeerList.
     */
    val latestPeers: StateFlow<List<RelayPeer>>
    ```
    Importer `kotlinx.coroutines.flow.StateFlow` et `com.mobicloud.domain.models.RelayPeer`.

- [x] **Task 2** : Créer le use-case de sélection (AC: 2, 7)
  - [x] Subtask 2.1 : Nouveau fichier `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/RequestInterClusterHostingUseCase.kt` :
    ```kotlin
    @Singleton
    class RequestInterClusterHostingUseCase @Inject constructor(
        private val signalingRepository: SignalingRepository,
        private val nodeSettingsRepository: NodeSettingsRepository
    ) {
        /** Retourne le meilleur Super-Pair distant pouvant héberger blockSize bytes, ou null. */
        suspend fun selectRemoteHost(blockSize: Int): RelayPeer? {
            val localClusterId = nodeSettingsRepository.getSettings().clusterId
            if (localClusterId.isBlank()) return null  // mon clusterId pas encore généré
            return signalingRepository.latestPeers.value
                .filter { it.isSuperPair }
                .filter { it.clusterId.isNotBlank() }
                .filter { it.clusterId != localClusterId }
                .filter { it.freeBytes >= blockSize.toLong() }
                .filter { it.ip.isNotBlank() && it.port > 0 }
                .maxByOrNull { it.freeBytes }
        }
    }
    ```

### 🏗️ Bloc Repository (Task 3) — Implémentation `latestPeers`

- [x] **Task 3** : Alimenter le `StateFlow` dans `SignalingRepositoryImpl` (AC: 1, 6)
  - [x] Subtask 3.1 : Ajouter dans `SignalingRepositoryImpl` :
    ```kotlin
    private val _latestPeers = MutableStateFlow<List<RelayPeer>>(emptyList())
    override val latestPeers: StateFlow<List<RelayPeer>> = _latestPeers.asStateFlow()
    ```
  - [x] Subtask 3.2 : Dans `processPeerList(peers)`, AVANT la boucle `forEach`, faire `_latestPeers.value = peers.filterNot { it.nodeId == localNodeId }`. **Ne pas** appliquer d'autres filtres ici — `clusterId`/`freeBytes` restent intacts pour usage par le use-case.
  - [x] Subtask 3.3 : Confirmer qu'aucun consommateur existant n'utilise déjà `latestPeers` (nouveau membre, pas de collision).

### 🌐 Bloc Distribution (Task 4) — Fallback inter-cluster

- [x] **Task 4** : Intégrer la sélection inter-cluster dans `DistributeEncryptedBlocksUseCase` (AC: 3, 4, 5)
  - [x] Subtask 4.1 : Injecter `RequestInterClusterHostingUseCase` dans le constructeur.
  - [x] Subtask 4.2 : Lire `localClusterId` UNE FOIS en début de `distribute()` (juste après `localIdentity`).
  - [x] Subtask 4.3 : Restructurer la boucle `encryptedBundle.encryptedFragments.forEachIndexed` :
    - Conserver le path local primary + local fallback inchangés (ne pas régresser).
    - **Si** `result.isFailure` après le fallback local **OU** `activePeers.isEmpty()` :
      ```kotlin
      val remote = requestInterClusterHostingUseCase.selectRemoteHost(msg.ciphertext.size)
      if (remote != null) {
          val remotePeer = Peer(
              identity = NodeIdentity(remote.nodeId, ByteArray(0)),
              ipAddress = remote.ip,
              port = remote.port,
              isActive = true,
              // autres champs : valeurs minimales requises par le data class
          )
          android.util.Log.i("MobiCloud:Distribute",
              "[INTER-CLUSTER] tentative #${frag.index} → ${remote.nodeId.take(8)}@${remote.ip}:${remote.port} cluster=${remote.clusterId.take(8)} freeBytes=${remote.freeBytes}")
          result = blockSender.sendBlock(msg, remotePeer, MAX_ACK_TIMEOUT_MS)
          if (result.isSuccess) confirmedPeer = remotePeer
      }
      ```
    - Vérifier la signature exacte de `data class Peer` au moment de l'implémentation et compléter les champs requis (probable `lastSeenMs`, `reliabilityScore`, etc. — utiliser des defaults plausibles, ces champs ne sont pas consultés par `BlockSender`).
  - [x] Subtask 4.4 : Le bloc `if (success) deliveries.add(...)` reste inchangé — `confirmedPeer` est déjà mis à jour avec le pair distant le cas échéant. `insertDhtEntryUseCase(...)` enregistre alors `(blockId, remoteNodeId, remoteIp, remotePort)` (AC#5 — réutilisation pure).
  - [x] Subtask 4.5 : Ajouter un log de fin de fragment indiquant si le placement a été local ou inter-cluster (utile diagnostic IRL).

### ✅ Bloc Tests (Tasks 5–7)

- [x] **Task 5** : Tests `RequestInterClusterHostingUseCaseTest` (AC: 2, 6, 7)
  - [x] Subtask 5.1 : Mock `SignalingRepository.latestPeers` (`MutableStateFlow`) et `NodeSettingsRepository.getSettings()`.
  - [x] Subtask 5.2 : Cas couverts :
    - `latestPeers = emptyList()` → `null`.
    - `localClusterId = ""` (jamais provisionné) → `null` (early return AC#6).
    - 1 candidat parfait (isSuperPair=true, clusterId distinct, freeBytes ≥ size) → retourné.
    - Candidat avec `clusterId = ""` (legacy/coerce) → rejeté.
    - Candidat avec `clusterId = localClusterId` → rejeté.
    - Candidat avec `isSuperPair = false` (JOIN-only) → rejeté.
    - Candidat avec `freeBytes < blockSize` → rejeté.
    - Candidat avec `freeBytes == blockSize` → accepté (borne `>=`).
    - Candidat avec `freeBytes == blockSize + 1024` (cas AC#7) → accepté.
    - 2 candidats valides : `freeBytes=100MB` et `freeBytes=500MB` → le second est retourné (tri décroissant).
    - Candidat avec `ip = ""` ou `port = 0` → rejeté.

- [x] **Task 6** : Test `SignalingRepositoryImplTest` (AC: 1)
  - [x] Subtask 6.1 : Émission d'un `RelayEvent.PeerList` via le mock `relayClient.connect(...)`.
  - [x] Subtask 6.2 : Assertion : `signalingRepository.latestPeers.value` contient les pairs reçus, **moins** l'auto-référence (peer.nodeId == localNodeId), et conserve `clusterId`/`freeBytes`.
  - [x] Subtask 6.3 : Vérifier la valeur initiale `emptyList()` AVANT toute émission (test de précondition AC#6).

- [x] **Task 7** : Test `DistributeEncryptedBlocksUseCaseTest` (AC: 3, 4, 5)
  - [x] Subtask 7.1 : Si le fichier de test n'existe pas, le créer minimalement (le use-case ne semble pas avoir de test JVM existant — vérifier ; sinon partir de la signature et mocker `peerRepository.peers`, `blockSender`, `securityRepository`, `catalogRepository`, `gossipSyncUseCase`, `insertDhtEntryUseCase`, `requestInterClusterHostingUseCase`).
  - [x] Subtask 7.2 : Cas A — `activePeers.isEmpty()` ET `selectRemoteHost(...)` retourne un candidat → `blockSender.sendBlock` est appelé avec le pair distant ; `insertDhtEntryUseCase` est appelé avec `remoteNodeId`/`remoteIp`/`remotePort`.
  - [x] Subtask 7.3 : Cas B — placement local primary réussit du premier coup → `selectRemoteHost(...)` JAMAIS appelé (régression-safe AC#4).
  - [x] Subtask 7.4 : Cas C — placement local échoue (primary + fallback) ET `selectRemoteHost(...)` retourne `null` → `Result.failure` (data blocks confirmés < k), comportement actuel préservé.

---

## Dev Notes

### Architecture / Sources

- **Topologie inter-cluster (V5.0)** — La règle « connectivity-aware placement » est étendue d'un cluster unique à une fédération de clusters. La décision reste 100% locale au Super-Pair émetteur. [Source: _bmad-output/planning-artifacts/architecture-connectivity-and-clustering.md#3.4 L80-83 et #4 L94-126]
- **Annuaire HA volatile** — `clusterId` et `freeBytes` ont un TTL serveur de 60s ; les exposer via `StateFlow` (mémoire) plutôt que Room respecte cette volatilité. [Source: relay-server/server.js#L21]
- **`BlockSenderWithRelay` (Story 8.3)** — Try-direct-then-relay fonctionne pour n'importe quel `(nodeId, ip, port)` ; pas besoin que le distant soit dans `PeerRepository`. C'est ce qui rend l'inter-cluster gratuit côté transport. [Source: app/src/main/kotlin/com/mobicloud/data/p2p/BlockSenderWithRelay.kt]
- **DHT entry pour bloc inter-cluster** — `InsertDhtEntryUseCase` accepte n'importe quel `nodeId` ; l'entrée DHT créée pour un bloc placé en inter-cluster sera consommée par 9.4 lors de la lecture. [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt]
- **Pattern de filtrage clusterId** — Aligné avec invariant « Super-Pair ⇒ clusterId valide » (deferred W-9.2-2 — aujourd'hui warn-only côté serveur, durci côté consommateur ici). [Source: _bmad-output/implementation-artifacts/deferred-work.md L417-419]
- **Pas de surcharge serveur** — 9.3 ne touche pas `relay-server/server.js`. [Source: project memory `feedback_minimize_centralization.md`]

### Project Structure Notes

- Pas de nouveau module/package Gradle — modifications confinées dans `domain/repository`, `data/repository`, `domain/usecase/m08_m09_erasure_coding`.
- Pas de migration Room (incrément de version). Le schéma reste en v12.
- Pas de modification du protocole WSS (REGISTER_PEER / GET_PEERS / UPLOAD inchangés).
- Pas de Hilt module supplémentaire — `@Inject` constructor sur le nouveau use-case suffit (Hilt injecte automatiquement les `@Singleton`).

### Testing Standards

- **Android JVM** : tests unitaires JUnit + MockK uniquement. Pas d'instrumented test pour cette story.
- **Mocking `StateFlow`** : utiliser `MutableStateFlow<List<RelayPeer>>(initial)` réel, pas un mock ; plus simple et plus fidèle au comportement.
- **Mocking `RelayPeer`** : data class déjà existante (Story 9.2) — instancier directement, pas besoin de mock.
- **Coverage attendu** : tous les filtres du use-case (Task 5) + branches de fallback (Task 7).
- **Pas de test du transport** : `BlockSenderWithRelay` est testé dans Story 8.3, pas re-testé ici.

### Limitations connues / deferred

- **Single-shot inter-cluster** — Si le 1ᵉʳ candidat distant échoue (ACK timeout), pas de retry sur le 2ᵉ candidat. Itération possible en 9.5 ou correct-course. Acceptable car la fréquence d'échec après filtre `freeBytes` est marginale.
- **Pas de reservation/lock** — Si deux Super-Pairs choisissent simultanément le même candidat distant pour des blocs différents, les deux `freeBytes` snapshot sont identiques mais la double-soustraction n'est observée qu'au prochain `REGISTER_PEER`. Race acceptable best-effort (deferred W-9.2-3).
- **Pas de feedback UX** — Aucun composant UI ne montre « ce bloc a été placé en inter-cluster ». Visible uniquement via logs Logcat (filtre `[INTER-CLUSTER]`).
- **Sentinelle `freeBytes == 0`** — Toujours ambiguë (legacy / disque plein / coerce-invalide). Le filtre `freeBytes >= blockSize` rejette automatiquement les `0` quand `blockSize > 0`. Refonte propre `null`/`-1` reste deferred (W-9.2-1).

### References

- [Source: _bmad-output/implementation-artifacts/9-1-clusterid-nodesettings-et-register-peer.md] — `clusterId` UUID v4 + pattern coerce-vs-warn.
- [Source: _bmad-output/implementation-artifacts/9-2-freebytes-et-clusterid-dans-get-peers.md] — `freeBytes` snapshot + invariant `Number.isFinite && >= 0`. Section "Guardrails critiques" et "Limite acceptée" de 9.2 directement applicables.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#W-9.2-1, W-9.2-2, W-9.2-3] — Deferred items que 9.3 exploite (filtre `clusterId.isBlank()` couvre W-9.2-1+2 côté consommateur).
- [Source: app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt#L86-92] — Commentaire bloc justifiant l'absence de persistance — la consommation mémoire est ici.
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt] — Point d'extension principal Task 4.
- [Source: app/src/main/kotlin/com/mobicloud/data/p2p/BlockSenderWithRelay.kt] — Transport try-direct-then-relay (Story 8.3) ; aucune duplication ici.
- [Source: app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt] — Réutilisation directe pour le placement inter-cluster.
- [Source: project memory `project_super_peer.md`] — Invariant rôle Super-Pair sacré ; on ne le bypasse pas, on lui donne un nouvel outil de placement.
- [Source: project memory `project_intercluster_test_result.md`] — Validation IRL 4G↔WiFi via HA Relay : l'infra réseau est prouvée, 9.3 livre la politique.
- [Source: project memory `feedback_minimize_centralization.md`] — Justifie le choix Option B (pas de nouveau message serveur).

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (1M context) — Amelia (bmad-dev-story)

### Debug Log References

- `gradlew :app:testDebugUnitTest --tests RequestInterClusterHostingUseCaseTest --tests DistributeEncryptedBlocksUseCaseTest --tests SignalingRepositoryImplTest` → BUILD SUCCESSFUL (tous tests Story 9.3 verts).
- 8 échecs préexistants hors scope 9.3 sur `main` (LocalDiscoveryRepositoryImplTest, SendDepartureNoticeUseCaseTest, ErasureProgressViewModelTest) — non causés par cette story (`ErasureProgressViewModel` mocke `DistributeEncryptedBlocksUseCase`, donc l'ajout d'un paramètre constructeur ne le casse pas).

### Completion Notes List

- **AC#1, AC#6 — `latestPeers` (port + impl)** : `SignalingRepository` expose `val latestPeers: StateFlow<List<RelayPeer>>`. `SignalingRepositoryImpl` alimente `_latestPeers.value = peers.filterNot { it.nodeId == localNodeId }` au tout début de `processPeerList`, AVANT la boucle d'insertion `PeerRepository`. `clusterId`/`freeBytes` non filtrés (filtrage délégué aux consommateurs). Valeur initiale `emptyList()`.
- **AC#2, AC#7 — `RequestInterClusterHostingUseCase.selectRemoteHost(blockSize)`** : nouveau use-case `@Singleton` qui lit `signalingRepository.latestPeers.value` et applique 5 invariants (isSuperPair, clusterId.isNotBlank, ≠ local, freeBytes ≥ blockSize, ip/port valides) puis tri `maxByOrNull { freeBytes }`. Early return `null` si `localClusterId.isBlank()`.
- **AC#3, AC#4, AC#5 — fallback inter-cluster dans `DistributeEncryptedBlocksUseCase`** : injection du nouveau use-case ; restructuration de la boucle en 3 niveaux (local primary → local fallback → inter-cluster). La condition `activePeers.isEmpty()` ne provoque plus un échec immédiat — la boucle continue et tente le placement distant. Si `selectRemoteHost` retourne un candidat, `BlockSenderWithRelay.sendBlock` est invoqué avec un `Peer` éphémère ; en cas d'ACK, `confirmedPeer` est mis à jour, ce qui rejoue automatiquement `insertDhtEntryUseCase(blockId, remoteNodeId, remoteIp, remotePort)` plus loin. Aucun appel inter-cluster n'est fait quand le placement local primaire réussit (régression-safe).
- **Choix de design** :
  - Tag log `[INTER-CLUSTER]` distinct pour traçabilité IRL (memory `project_intercluster_test_result.md`).
  - `Peer` éphémère construit avec `NodeIdentity(remote.nodeId, ByteArray(0))` + `source = RELAY_HA` + `isSuperPair = true` — pas d'insertion dans `PeerRepository` (cohérent avec le commentaire `processPeerList` L86-92).
  - Pas de retry sur 2ème candidat distant si le 1er échoue (deferred — itération possible).
  - Pas de modification du serveur Node.js (story 100% client-side).
- **Tests Task 5 (RequestInterClusterHostingUseCaseTest)** — 12 tests couvrant : empty list, localClusterId vide, candidat parfait, clusterId vide rejeté, clusterId == local rejeté, isSuperPair=false rejeté, freeBytes insuffisant rejeté, freeBytes == blockSize accepté (borne `>=`), AC#7 (freeBytes ≈ blockSize), tri freeBytes décroissant sur 3 candidats, ip vide rejeté, port 0 rejeté.
- **Tests Task 6 (SignalingRepositoryImplTest)** — 4 nouveaux tests Story 9.3 : valeur initiale `emptyList()`, snapshot reflète clusterId/freeBytes, auto-référence retirée, pas de filtrage clusterId/freeBytes (liste brute).
- **Tests Task 7 (DistributeEncryptedBlocksUseCaseTest)** — 4 nouveaux tests Story 9.3 : Cas A (cluster local vide ⇒ inter-cluster), Cas B (placement local OK ⇒ `selectRemoteHost` jamais appelé), Cas C (local + inter-cluster KO ⇒ failure), local fallback puis inter-cluster réussi.

### Change Log

| Date | Description |
|---|---|
| 2026-05-06 | Implémentation Story 9.3 : `latestPeers` (StateFlow brut) + `RequestInterClusterHostingUseCase` (sélection super-pair distant) + 3ᵉ niveau de fallback inter-cluster dans `DistributeEncryptedBlocksUseCase`. 20 tests JVM ajoutés/modifiés, tous verts. |
| 2026-05-06 | Code review (bmad-code-review) — 3 patches identifiés, 7 deferred, 15 dismissed. Voir section Review Findings. |

### Review Findings

- [x] [Review][Patch] `localClusterId` lu à chaque fragment au lieu d'une fois en début de `distribute()` — fix : `RequestInterClusterHostingUseCase` ne dépend plus de `NodeSettingsRepository` ; nouvelle signature `selectRemoteHost(blockSize, localClusterId)` ; `DistributeEncryptedBlocksUseCase` lit `clusterId` une fois au début de `distribute()` via `nodeSettingsRepository.getSettings().clusterId`.
- [x] [Review][Patch] Guard manquant `blockSize <= 0` dans `selectRemoteHost` — fix : early-return `if (blockSize <= 0) return null` ajouté + 2 tests (`blockSize == 0`, `blockSize < 0`).
- [x] [Review][Patch] `localClusterId` blank → return silencieux, aucun log — fix : log `[INTER-CLUSTER] désactivé : localClusterId blank` ajouté côté `DistributeEncryptedBlocksUseCase` une seule fois en début de `distribute()` (et non par fragment).
- [x] [Review][Defer] Validation `freeBytes` négatif côté parsing RelayPeer — pré-existant 9.2, server-trust.
- [x] [Review][Defer] Validation `port` upper bound (>65535) côté RelayPeer parsing — pré-existant 9.2.
- [x] [Review][Defer] Hot-spotting : même remote choisi pour tous les fragments d'un même upload [DistributeEncryptedBlocksUseCase.kt:107 + RequestInterClusterHostingUseCase.kt:49] — défait partiellement la diversité de l'erasure coding. Mitigation : tracker `bytesPromisedThisRound` par remote, ou tirage aléatoire pondéré au lieu de `maxByOrNull`. Apparenté à W-9.2-3 mais distinct.
- [x] [Review][Defer] Tie-break non-déterministe sur `freeBytes` égaux dans `maxByOrNull` — ajouter secondary sort `nodeId` lexical pour reproductibilité.
- [x] [Review][Defer] Race `latestPeers` muté pendant `distribute()` — un nouveau `processPeerList` entre fragment i et i+1 change le candidat. Snapshot `.value` au début de `distribute()` et passer la liste au use-case.
- [x] [Review][Defer] TTL `lastSeen` non consulté côté consommateur [RequestInterClusterHostingUseCase.kt + SignalingRepositoryImpl.kt:23 commentaire] — la spec délègue le TTL au consommateur mais aucun consommateur ne filtre. Ajouter `now - lastSeen < TTL` quand `RelayPeer` exposera `lastSeen`.
- [x] [Review][Defer] `runCatching` swallows `CancellationException` dans `registerAsSuperPeer` [SignalingRepositoryImpl.kt:registerAsSuperPeer] — pattern pré-existant 9.2, à corriger globalement (utiliser `try { } catch (e: CancellationException) { throw e }`).

### File List

**Modified:**

- `app/src/main/kotlin/com/mobicloud/domain/repository/SignalingRepository.kt` — ajout `val latestPeers: StateFlow<List<RelayPeer>>`.
- `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt` — `MutableStateFlow` privé alimenté par `processPeerList`, exposé via `asStateFlow()` ; auto-référence retirée.
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` — injection `RequestInterClusterHostingUseCase` ; restructuration de la boucle de distribution (3 niveaux de fallback : local primary → local fallback → inter-cluster) ; log `[INTER-CLUSTER]` ; suppression du return-early sur `activePeers.isEmpty()`.
- `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt` — 4 tests Story 9.3 sur `latestPeers`.
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCaseTest.kt` — 4 tests Story 9.3 sur le fallback inter-cluster + injection mock du nouveau use-case dans le setUp.

**Created:**

- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/RequestInterClusterHostingUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/RequestInterClusterHostingUseCaseTest.kt`
