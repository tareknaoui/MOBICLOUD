# Story 9.2 : freeBytes & clusterId dans GET_PEERS

Status: done

## Story

En tant que Super-Pair cherchant à placer ou récupérer des blocs inter-cluster,
Je veux que la réponse `GET_PEERS` du Serveur Relais HA expose pour chaque pair son `clusterId` et sa capacité `freeBytes`,
Afin de pouvoir identifier les Super-Pairs distants (cluster ≠ moi) avec assez d'espace libre pour héberger un bloc (préparation Stories 9.3/9.4 — distribution et récupération inter-cluster).

## Acceptance Criteria

1. **Given** un Super-Pair envoie `REGISTER_PEER` au Serveur Relais HA
   **When** `RelayWebSocketClient.sendRegisterPeer()` construit le JSON payload
   **Then** le payload inclut un champ `"freeBytes"` (Long ≥ 0) en plus du champ `"clusterId"` déjà ajouté en 9.1.

2. **Given** `SignalingRepositoryImpl.registerAsSuperPeer()` est invoqué
   **When** il prépare le payload avant d'appeler `sendRegisterPeer()`
   **Then** `freeBytes` est calculé comme `max(0, allocatedStorageBytes - totalHostedBytes)` à partir de `NodeSettingsRepository.getSettings()` et `HostedBlockRepository.getTotalHostedBytes()`.

3. **Given** le Serveur Relais reçoit `REGISTER_PEER` avec un champ `freeBytes` valide
   **When** `handleRegisterPeer()` traite le message
   **Then** `signalingRegistry` stocke `freeBytes` (Number, normalisé en Number entier ≥ 0) associé au `nodeId`. Si `freeBytes` est absent, négatif, non-numérique ou non-fini → coerce en `0` (rétrocompatibilité legacy, log warning si présent mais invalide).

4. **Given** un client envoie `GET_PEERS`
   **When** `handleGetPeers()` construit la réponse `PEERS`
   **Then** chaque entrée du tableau JSON inclut les champs `clusterId` (string, `""` si legacy) et `freeBytes` (Number, `0` si legacy ou absent), en plus des champs existants (`nodeId`, `ip`, `port`, `reliabilityScore`, `lastSeen`, `isSuperPair`).

5. **Given** l'Android reçoit une réponse `PEERS`
   **When** `RelayWebSocketClient.parsePeersPayload()` décode le JSON
   **Then** chaque `RelayPeer` est instancié avec `clusterId` (default `""` si champ absent) et `freeBytes` (default `0L` si champ absent), et `processPeerList()` propage ces deux champs jusqu'au `PeerRepository`.

6. **Given** un nœud legacy envoie `REGISTER_PEER` sans `freeBytes` (compatibilité Story 9.1)
   **When** le serveur traite le message
   **Then** `freeBytes=0` est stocké, aucune erreur, le message est accepté — exactement comme `clusterId` en 9.1.

## Context / Notes développeur

### Vue d'ensemble de la story

Story B de l'Epic 9 (Stockage Inter-Cluster). Elle complète le canal d'information `Super-Pair → annuaire HA → autres Super-Pairs` ouvert en 9.1 :

- **9.1 (done)** — chaque Super-Pair publie son `clusterId` UUID v4 via `REGISTER_PEER` ; le serveur le stocke ; le serveur ne l'expose **pas encore** dans `GET_PEERS`.
- **9.2 (cette story)** — le Super-Pair publie aussi sa capacité `freeBytes` ; le serveur les expose **toutes les deux** (`clusterId` + `freeBytes`) dans la réponse `GET_PEERS`.
- **9.3** — `RequestHosting` inter-cluster : au moment de placer un bloc, choisir un Super-Pair distant (`clusterId != moi`) avec `freeBytes >= taille bloc`.
- **9.4** — `RequestBlock` inter-cluster : retrouver un bloc côté distant.

**Ce qui n'est PAS dans cette story :**
- Aucune logique de placement ou de récupération inter-cluster (9.3, 9.4).
- Aucune UI ne consomme `freeBytes` ou `clusterId` distant ici (on prépare seulement la donnée).
- Pas de TTL spécifique sur `freeBytes` au-delà du TTL annuaire 60s déjà en place.

### Concept `freeBytes`

`freeBytes = max(0, allocatedStorageBytes - totalHostedBytes)`

- `allocatedStorageBytes` : quota choisi par l'utilisateur dans Settings ([NodeSettings](app/src/main/kotlin/com/mobicloud/domain/models/NodeSettings.kt)).
- `totalHostedBytes` : somme des blocs hébergés actuellement, exposée par [HostedBlockRepository.observeTotalHostedBytes()](app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt).
- Le `max(0, …)` se justifie parce que `usedBytes` peut transitoirement dépasser le quota si l'utilisateur abaisse le slider après avoir hébergé des blocs (cf. [SettingsViewModel.kt:40](app/src/main/kotlin/com/mobicloud/presentation/settings/SettingsViewModel.kt#L40) qui prévient ce cas mais ne l'élimine pas pour des données legacy).
- `freeBytes` est un **snapshot au moment du REGISTER_PEER**. Pas de stream, pas de subscription ; le rafraîchissement se fait au prochain REGISTER_PEER (déclenché à la (ré)connexion WebSocket via `onConnectedHook` ou par les Bully ré-élections périodiques).

### Fichiers à modifier (≈ 7 fichiers + tests)

| Fichier | Modification |
|---|---|
| `app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt` | Ajouter `suspend fun getTotalHostedBytes(): Long` (one-shot, distinct du `observe…` Flow). |
| `app/src/main/kotlin/com/mobicloud/data/repository/HostedBlockRepositoryImpl.kt` | Implémenter `getTotalHostedBytes()` via le DAO existant (probable `dao.getTotalSize()` ou aggregate sur la même requête que `observeTotalHostedBytes`). |
| `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt` | Injecter `HostedBlockRepository`, calculer `freeBytes` dans `registerAsSuperPeer()`, passer à `sendRegisterPeer()`. |
| `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt` | `sendRegisterPeer(...)` ajoute paramètre `freeBytes: Long` + champ JSON. `parsePeersPayload()` lit `clusterId` et `freeBytes` (avec defaults). |
| `app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt` | `RelayPeer` ajouter `clusterId: String = ""` et `freeBytes: Long = 0L`. |
| `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt` (processPeerList) | Vérifier la propagation jusqu'à `PeerRepository.registerOrUpdatePeer(...)` — voir Guardrail "PeerRepository extension". |
| `relay-server/server.js` | `handleRegisterPeer()` parse + valide `freeBytes` ; `handleGetPeers()` expose `clusterId` + `freeBytes` dans la réponse. |
| `relay-server/server.test.js` | Étendre les tests `handleRegisterPeer` et `handleGetPeers`. |
| `app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt` | `HostedBlockRepository` mocké, vérifier le calcul `freeBytes` et les bornes. |
| `app/src/test/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClientTest.kt` | `sendRegisterPeer` à 7 args ; `parsePeersPayload` avec/sans `clusterId`/`freeBytes`. |

### Guardrails critiques

- **Compat JSON serveur** : la validation `clusterId` (regex UUID v4 + coerce, cf. [server.js:147](relay-server/server.js#L147)) reste inchangée. Pour `freeBytes`, accepter tout `Number.isFinite(x) && x >= 0` ; sinon coerce en `0` + `console.warn` (mêmes pattern que pour `clusterId` invalide).
- **Pas de surcharge réseau** : `clusterId` (36 chars) + `freeBytes` (≤ 19 chars) ≈ 70 octets supplémentaires par pair. Sur 100 pairs ≈ 7 ko — négligeable, mais documenter dans les Dev Notes.
- **PeerRepository extension** : `processPeerList()` appelle aujourd'hui `peerRepository.registerOrUpdatePeer(identity, timestampMs, source, ipAddress, port, isSuperPair)`. **Cette story ne doit PAS modifier la table `peers` ni `PeerRepository`** — `freeBytes` et `clusterId` distants sont des données volatiles d'annuaire, pas des attributs persistants du pair. Conserver la signature actuelle de `PeerRepository.registerOrUpdatePeer`. La consommation de `freeBytes`/`clusterId` distants se fera via un cache ad-hoc en mémoire dans `SignalingRepositoryImpl` ou directement dans la story 9.3 — ici on **se contente de les exposer** dans `RelayPeer` et de les logger pour vérification IRL.
- **Atomicité freeBytes** : `getSettings()` et `getTotalHostedBytes()` sont deux requêtes DB séquentielles → une concurrence ajout-bloc/REGISTER_PEER peut donner un `freeBytes` légèrement faux. Acceptable (snapshot best-effort, le bloc inter-cluster utilisera de toute façon une marge dans 9.3).
- **Ne pas modifier la signature** de `SignalingRepository.registerAsSuperPeer()` (interface domain). Le calcul `freeBytes` reste un détail data — comme pour `clusterId` en 9.1.
- **`getTotalHostedBytes()` suspend ≠ `observeTotalHostedBytes()` Flow** : ne pas faire `observeTotalHostedBytes().first()` (peut bloquer si le Flow est cold ou attend une émission). Préférer une vraie requête DAO one-shot.
- **Sérialisation JSON `Long` côté Android → Number côté serveur** : `org.json.JSONObject.put("freeBytes", longValue)` produit un Number JSON correct. Côté serveur, `typeof entry.freeBytes === 'number' && Number.isFinite(x)`. Pas de risque de précision tant que `freeBytes < 2^53` (~ 9 PB ; aucun téléphone n'atteindra ça).
- **Tests JVM Hilt** : `SignalingRepositoryImpl` a maintenant 5 dépendances (`relayClient`, `peerRepository`, `networkEventRepository`, `identityRepository`, `nodeSettingsRepository`) → ajouter `hostedBlockRepository` en 6e. Mettre à jour le constructeur et tous les `SignalingRepositoryImpl(...)` appelés dans les tests.
- **Pas de migration Room ni schema change** — c'est une story pure transport/protocole.

---

## Tasks / Subtasks

### 🗄️ Bloc Domain (Task 1) — Modèle RelayPeer + interface

- [x] **Task 1** : Étendre `RelayPeer` avec `clusterId` et `freeBytes` (AC: 4, 5)
  - [x] Subtask 1.1 : Modifier `app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt` :
    ```kotlin
    data class RelayPeer(
        val nodeId: String,
        val ip: String,
        val port: Int,
        val reliabilityScore: Float,
        val lastSeen: Long,
        val isSuperPair: Boolean = false,
        val clusterId: String = "",   // Story 9.2 — UUID v4 du cluster du Super-Pair, "" si legacy
        val freeBytes: Long = 0L      // Story 9.2 — capacité libre snapshot, 0 si legacy
    )
    ```
  - [x] Subtask 1.2 : Ajouter `suspend fun getTotalHostedBytes(): Long` à l'interface `HostedBlockRepository`. Documenter qu'il s'agit d'une lecture one-shot complémentaire à `observeTotalHostedBytes()`.
  - [x] Subtask 1.3 : Implémenter `getTotalHostedBytes()` dans `HostedBlockRepositoryImpl` via le DAO (réutiliser la requête sous-jacente d'`observeTotalHostedBytes` mais en `suspend` non-Flow).

### 🌐 Bloc Transport Android (Task 2–3) — REGISTER_PEER + parse PEERS

- [x] **Task 2** : Ajouter `freeBytes` au payload `REGISTER_PEER` (AC: 1)
  - [x] Subtask 2.1 : Dans `RelayWebSocketClient.kt`, ajouter `freeBytes: Long` à la signature `sendRegisterPeer(...)` (placer en dernier paramètre — ordre : nodeId, ip, port, reliabilityScore, electedAt, clusterId, freeBytes).
  - [x] Subtask 2.2 : Ajouter `put("freeBytes", freeBytes)` dans la construction du `JSONObject`.

- [x] **Task 3** : Parser `clusterId` et `freeBytes` dans `parsePeersPayload()` (AC: 5)
  - [x] Subtask 3.1 : Modifier `parsePeersPayload()` dans `RelayWebSocketClient.kt` pour lire `obj.optString("clusterId", "")` et `obj.optLong("freeBytes", 0L)`. Defaults garantissent la rétrocompatibilité avec d'éventuelles réponses serveur sans ces champs.

### 🏗️ Bloc Repository Android (Task 4) — Calcul freeBytes

- [x] **Task 4** : Calculer `freeBytes` et l'envoyer dans `SignalingRepositoryImpl.registerAsSuperPeer()` (AC: 2)
  - [x] Subtask 4.1 : Injecter `HostedBlockRepository` dans le constructeur `@Inject` de `SignalingRepositoryImpl`. Vérifier qu'un binding Hilt existe déjà (cf. modules `RepositoryBindingModule` ou similaire — sinon ajouter `@Binds`).
  - [x] Subtask 4.2 : Dans `registerAsSuperPeer()`, après lecture du `clusterId`, lire `allocated = nodeSettingsRepository.getSettings().allocatedStorageBytes` et `used = hostedBlockRepository.getTotalHostedBytes()`, puis `val freeBytes = (allocated - used).coerceAtLeast(0L)`.
  - [x] Subtask 4.3 : Passer `freeBytes` à `relayClient.sendRegisterPeer(...)`. Mettre à jour le log : ajouter `freeBytes=$freeBytes`.
  - [x] Subtask 4.4 : `SignalingRepository.registerAsSuperPeer()` (interface domain) reste inchangée — le calcul est un détail transport.

### 🖥️ Bloc Serveur Node.js (Tasks 5–6) — Stockage + exposition

- [x] **Task 5** : Stocker `freeBytes` validé dans `signalingRegistry` (AC: 3, 6)
  - [x] Subtask 5.1 : Dans `relay-server/server.js`, fonction `handleRegisterPeer()`, ajouter une validation/coercion pour `freeBytes` :
    ```js
    let freeBytesNum = 0;
    if (entry.freeBytes !== undefined && entry.freeBytes !== null) {
      const n = Number(entry.freeBytes);
      if (Number.isFinite(n) && n >= 0) {
        freeBytesNum = Math.floor(n);
      } else {
        console.warn(`[SIGNALING] freeBytes invalide rejeté (coerce en 0) — nodeId=${nodeId.slice(0, 8)} type=${typeof entry.freeBytes}`);
      }
    }
    ```
  - [x] Subtask 5.2 : Ajouter `freeBytes: freeBytesNum` à l'objet `signalingRegistry.set(nodeId, { ... })`.
  - [x] Subtask 5.3 : Étendre la ligne de log REGISTER_PEER avec `freeBytes=${freeBytesNum}`.

- [x] **Task 6** : Exposer `clusterId` et `freeBytes` dans la réponse `GET_PEERS` (AC: 4)
  - [x] Subtask 6.1 : Dans `handleGetPeers()`, ajouter `clusterId: entry.clusterId ?? ''` et `freeBytes: entry.freeBytes ?? 0` à chaque objet `peers.push({...})`.
  - [x] Subtask 6.2 : `JOIN` (`handleJoin`) **ne doit pas** publier `freeBytes` (les nœuds non-Super-Pair n'ont pas vocation à héberger inter-cluster). Conserver `freeBytes=0` par défaut dans l'entrée JOIN. → Vérifier qu'`handleJoin` ne touche pas `freeBytes` (sinon défaut `0` reste après les modifications).

### ✅ Bloc Tests (Tasks 7–8)

- [x] **Task 7** : Tests serveur Node.js (`relay-server/server.test.js`) (AC: 3, 4, 6)
  - [x] Subtask 7.1 : Étendre `describe('handleRegisterPeer')` :
    - REGISTER_PEER avec `freeBytes: 1234567` → `entry.freeBytes === 1234567`.
    - REGISTER_PEER avec `freeBytes` absent → `entry.freeBytes === 0`, aucune erreur (legacy).
    - REGISTER_PEER avec `freeBytes: -5` ou `"abc"` ou `NaN` → coerce en `0` + warn (test via `jest.spyOn(console, 'warn')`).
  - [x] Subtask 7.2 : Étendre `describe('handleGetPeers')` :
    - Après REGISTER_PEER avec `clusterId` UUID + `freeBytes: 1024`, le payload PEERS retourné contient bien `clusterId` et `freeBytes` corrects pour ce nœud.
    - JOIN-only (sans REGISTER_PEER) → `clusterId === ''` et `freeBytes === 0` dans la réponse.

- [x] **Task 8** : Tests Android (AC: 1, 2, 5)
  - [x] Subtask 8.1 : `RelayWebSocketClientTest.kt` :
    - `sendRegisterPeer(...)` à 7 args inclut bien `"freeBytes":<long>` dans le JSON capturé.
    - `parsePeersPayload(...)` retourne bien `clusterId` et `freeBytes` quand présents ; defaults `""` / `0L` quand absents.
  - [x] Subtask 8.2 : `SignalingRepositoryImplTest.kt` :
    - Mock `HostedBlockRepository.getTotalHostedBytes()` ; vérifier que `freeBytes` passé à `sendRegisterPeer` = `allocated - used`.
    - Cas `used > allocated` → `freeBytes` clampé à `0L` (jamais négatif).
    - Constructeur du repo : 6 dépendances. Mettre à jour TOUS les sites de construction.

### 📓 Bloc Documentation (Task 9)

- [x] **Task 9** : Documenter la décision de **ne pas** persister `clusterId`/`freeBytes` distants dans `PeerRepository`
  - [x] Subtask 9.1 : Ajouter un commentaire bloc dans `processPeerList()` expliquant que ces champs sont **volatiles** (snapshot annuaire HA, TTL 60s) et seront consommés en mémoire par les use-cases inter-cluster (Stories 9.3, 9.4) — pas de table Room.

---

## Dev Notes

### Architecture / Sources

- **Topologie inter-cluster (V5.0)** — Le Serveur Relais HA est le seul point de rendez-vous pour des Super-Pairs sur des clusters NAT-séparés. `GET_PEERS` est aujourd'hui le canal d'annuaire ; on l'enrichit ici pour rendre le placement inter-cluster décidable côté client. [Source: _bmad-output/planning-artifacts/architecture.md#Cross-Cutting Concerns L172-173]
- **TTL 60s sur l'annuaire** — `signalingRegistry` purge automatiquement chaque entry après 60s sans REGISTER_PEER ; `freeBytes` reste donc frais à ±60s. Aligné avec la philosophie "snapshot best-effort". [Source: relay-server/server.js#L21]
- **Pourquoi un cache mémoire et pas Room** — Les attributs distants `clusterId`/`freeBytes` ont une durée de vie courte et viennent toujours du même endroit (annuaire HA). Les persister dans Room créerait un risque de servir des données obsolètes après TTL serveur expiré. [Source: project memory `project_super_peer.md`]
- **Calcul `freeBytes`** — Aligné avec la formule UI dans `SettingsScreen.kt:80` (« usedBytes utilisés sur sliderValue alloués »). Le `coerceAtLeast(0)` reflète exactement la prévention `if (newBytes < usedStorageBytes.value)` du SettingsViewModel. [Source: app/src/main/kotlin/com/mobicloud/presentation/settings/SettingsViewModel.kt#L40]

### Project Structure Notes

- Pas de nouveau module ni package — modifications localisées dans `data/p2p/websocket`, `data/repository`, `domain/models`, `domain/repository` côté Android, et `relay-server/server.js` côté serveur.
- Pas de migration Room (incrément de version). Le schéma reste en v12.

### Testing Standards

- **Android** : tests JVM unitaires (JUnit + MockK), pas d'instrumented test pour cette story.
- **Serveur** : tests Jest (déjà en place via `relay-server/jest.config.js`). Utiliser `jest.spyOn(console, 'warn')` pour les cas de coercion.
- **Coverage attendu** : tous les chemins de validation `freeBytes` (valide, absent, négatif, non-numérique, NaN) — alignement strict avec le pattern `clusterId` 9.1 [Source: implementation-artifacts/9-1-clusterid-nodesettings-et-register-peer.md#Patches F6].

### References

- [Source: _bmad-output/implementation-artifacts/9-1-clusterid-nodesettings-et-register-peer.md] — Story précédente, fournit `clusterId` (réutilisé ici) + pattern de validation strict serveur (option b coerce + warn).
- [Source: relay-server/server.js#L135-182] — `handleRegisterPeer` actuelle, point d'entrée des modifications serveur.
- [Source: relay-server/server.js#L228-241] — `handleGetPeers` actuelle, à enrichir.
- [Source: app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt#L111-126] — `registerAsSuperPeer` actuel, point d'extension pour `freeBytes`.
- [Source: app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt#L221-258] — `sendRegisterPeer` + `parsePeersPayload`.
- [Source: app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt#L33] — `observeTotalHostedBytes` existant, pattern pour ajouter `getTotalHostedBytes()`.
- [Source: project memory `project_super_peer.md`] — invariant "rôle Super-Pair sacré" : ne pas court-circuiter, on enrichit son canal d'annonce.
- [Source: project memory `project_intercluster_test_result.md`] — Inter-cluster 4G↔Wi-Fi via HA Relay validé IRL ; cette story ouvre la voie au choix intelligent du destinataire.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (Amelia / bmad-dev-story)

### Debug Log References

- Premier run Jest : échec sur `freeBytes: NaN` / `freeBytes: Infinity` → cause = `JSON.stringify` sérialise ces valeurs en `null`, ce qui est traité comme legacy (pas de warn). Tests retirés (commentaire ajouté).
- Suite complète `:app:testDebugUnitTest` : 8 échecs préexistants (ErasureProgressViewModelTest, SendDepartureNoticeUseCaseTest, LocalDiscoveryRepositoryImplTest) — confirmés via `git stash` baseline avant modifications. Aucun lien avec Story 9.2.

### Completion Notes List

- AC1 ✅ — `RelayWebSocketClient.sendRegisterPeer` ajoute `freeBytes: Long` (7e param) + champ JSON `"freeBytes"`.
- AC2 ✅ — `SignalingRepositoryImpl.registerAsSuperPeer` calcule `freeBytes = (allocated - used).coerceAtLeast(0L)` à partir de `nodeSettingsRepository.getSettings()` et nouveau `hostedBlockRepository.getTotalHostedBytes()`.
- AC3 ✅ — `server.js handleRegisterPeer` valide `Number.isFinite(n) && n >= 0` ; coerce en `0` + `console.warn` sinon ; absent → `0` silencieux.
- AC4 ✅ — `server.js handleGetPeers` expose `clusterId` et `freeBytes` (defaults `''`/`0`) en plus des champs existants.
- AC5 ✅ — `RelayWebSocketClient.parsePeersPayload` lit `obj.optString("clusterId", "")` + `obj.optLong("freeBytes", 0L)`.
- AC6 ✅ — Test `REGISTER_PEER sans freeBytes (legacy) stocke 0 sans erreur` couvre la rétrocompat.
- Décision (Task 9) : `clusterId`/`freeBytes` distants ne sont PAS persistés dans `PeerRepository` ; commentaire bloc ajouté dans `processPeerList`.
- Architecture : nouveau `getTotalHostedBytes(): Long` (suspend) ajouté à l'interface `HostedBlockRepository` + DAO `getTotalSizeBytes()` one-shot (distinct du Flow `observeTotalSizeBytes`).
- Hilt : `HostedBlockRepository` injecté dans `SignalingRepositoryImpl` (binding existant dans `HostingModule`).
- Tests serveur : 37/37 ✅ (4 nouveaux cas freeBytes valide/legacy/invalide/décimal + 2 nouveaux cas GET_PEERS expose clusterId+freeBytes).
- Tests Android : SignalingRepositoryImplTest + RelayWebSocketClientTest verts (signature 7 args + 2 nouveaux cas calcul `freeBytes` + clamp).
- Limite acceptée (alignement précédent 9.1) : pas de test JVM capturant le JSON de `sendRegisterPeer` car `org.json.JSONObject` n'est pas disponible en unit test (cf. commentaire test existant).

### File List

**Modifiés :**
- [app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt](app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt) — `RelayPeer` + `clusterId`/`freeBytes`.
- [app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt](app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt) — `suspend fun getTotalHostedBytes()`.
- [app/src/main/kotlin/com/mobicloud/data/local/dao/HostedBlockDao.kt](app/src/main/kotlin/com/mobicloud/data/local/dao/HostedBlockDao.kt) — `suspend fun getTotalSizeBytes()`.
- [app/src/main/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImpl.kt](app/src/main/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImpl.kt) — implémentation `getTotalHostedBytes`.
- [app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt](app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt) — `sendRegisterPeer` 7 args + `parsePeersPayload` lit clusterId/freeBytes.
- [app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt](app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt) — injection HostedBlockRepository + calcul freeBytes + commentaire processPeerList.
- [relay-server/server.js](relay-server/server.js) — validation/coercion freeBytes + exposition GET_PEERS.
- [relay-server/server.test.js](relay-server/server.test.js) — 6 nouveaux tests (handleRegisterPeer + handleGetPeers).
- [app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt](app/src/test/kotlin/com/mobicloud/data/repository/SignalingRepositoryImplTest.kt) — nouvelle dépendance + 2 tests freeBytes.
- [app/src/test/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClientTest.kt](app/src/test/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClientTest.kt) — sendRegisterPeer signature 7 args.

### Change Log

- 2026-05-05 — Story 9.2 implémentée : `freeBytes` ajouté au protocole `REGISTER_PEER` (snapshot best-effort) + `clusterId`/`freeBytes` exposés dans la réponse `GET_PEERS` pour préparer le placement inter-cluster (Stories 9.3/9.4). Aucune migration Room ni rupture de compat (defaults 0/"" pour les nœuds legacy).

### Review Findings

- [x] [Review][Decision→Patch] **DB read failures abort Super-Pair registration** → résolu en Option 1 (fallback). `runCatching` local autour du calcul `freeBytes` ; en cas d'exception DB, fallback `0L` + `Log.w` ; `REGISTER_PEER` est toujours envoyé → le Super-Pair reste découvrable, juste écarté du placement inter-cluster pour ce cycle TTL. Test `Story 9_2 D1 ... fallback=0 si getTotalHostedBytes leve une exception` ajouté. [`SignalingRepositoryImpl.kt:131-138`]
- [x] [Review][Patch] **`handleJoin` n'efface pas `clusterId`/`freeBytes` d'un Super-Pair** → corrigé. `signalingRegistry.set` dans `handleJoin` hérite désormais `existing?.clusterId ?? ''` et `existing?.freeBytes ?? 0`. Test `Story 9.2 review (P1) — JOIN heartbeat préserve...` ajouté (vert). [`relay-server/server.js:227-241`]
- [x] [Review][Patch] **Borne supérieure absente sur `freeBytes` serveur** → corrigé. Validation étendue : `freeBytes >= 0 && freeBytes <= Number.MAX_SAFE_INTEGER`. Test `au-delà de MAX_SAFE_INTEGER` ajouté. [`relay-server/server.js:158-172`]
- [x] [Review][Patch] **Type guard `freeBytes` manquant côté serveur** → corrigé. `typeof freeBytes === 'number'` exigé en plus de `Number.isFinite`. Tests `string numérique`, `boolean true`, `boolean false` ajoutés (tous coercés en 0 + warn). [`relay-server/server.js:158-172`]
- [x] [Review][Patch] **`parsePeersPayload` mal-géré sur `clusterId: null`** → corrigé. `if (obj.isNull("clusterId")) "" else obj.optString(...)` (idem `freeBytes`). [`RelayWebSocketClient.kt:259-262`]
- [x] [Review][Patch] **Test `clampe freeBytes a 0` assertion faible** → dismissed. Combiné avec le test positif `allocated - used = 750_000L`, une inversion de formule serait détectée. Note conservée pour transparence. [`SignalingRepositoryImplTest.kt`]
- [x] [Review][Defer] **Ambiguïté sentinelle `freeBytes==0` et `clusterId==""`** — collision entre legacy / disque plein / coerce-invalide. Refonte sentinelle (`null`/`-1`) à considérer pour 9.3. — deferred, design choice
- [x] [Review][Defer] **`isSuperPair=true` même quand `clusterId` coercé à `""`** — un nœud peut s'enregistrer Super-Pair sans UUID valide ; brise l'invariant supposé par 9.3/9.4. Aligné avec pattern 9.1 (warn-only). — deferred, aligned with 9.1 pattern
- [x] [Review][Defer] **Pas de debouncing sur `freeBytes`** — oscillations entre REGISTER_PEERs rapides (bloc en vol) → décisions placement non-déterministes côté consommateurs. — deferred, consumer-side concern (9.3)
- [x] [Review][Defer] **`reliabilityScore`/`electedAt` non validés** — pré-existant, hors scope ; à durcir avec une story QA serveur dédiée. — deferred, pre-existing
- [x] [Review][Defer] **Comment "null produit no-warn" non testé** — comment dans server.test.js sans test correspondant ; ajouter cas `null` explicite. — deferred, test gap minor
- [x] [Review][Defer] **`clusterId === ''` traité silencieusement comme legacy** — un Super-Pair bug envoyant `""` est masqué (no warn). Pré-existant 9.1. — deferred, pre-existing from 9.1
- [x] [Review][Defer] **`getSettings()` lazy-créé clusterId sur 1er appel dans le chemin election critique** — pré-existant 9.1, ajoute latence + point d'échec DB. — deferred, pre-existing from 9.1
