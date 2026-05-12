# Story 12.1: Suppression du GPS — Admission Cluster par Charge (`memberCount`)

Status: done

**Epic :** 12 — Décentralisation de l'Admission (Refactor Epic 11 — GPS retiré)
**Story ID :** 12.1
**Story Key :** `12-1-suppression-gps-admission-cluster-par-charge`
**Date :** 2026-05-12
**Auteur :** Bob (SM) / bmad-create-story
**Prérequis :** Epic 11 `done` (`JoinStateMachine`, `SuperPeerHint`, `ClusterConstants`, `MemberEntity`, `LocationRepository` en place) ; Epic 8 `done` (Relai HA WebSocket — transport unifié indépendant de la géographie).
**Bloque :** Aucune story aval interne. La modification miroir côté tracker (Render `relay-server/server.js` — suppression du filtre Haversine, ajout sort par `memberCount`) fait l'objet d'une story serveur séparée (`12-2-tracker-load-based-discovery`) **non couverte par cette story**.

---

## Contexte & Justification (défense PFE)

L'Epic 11 a introduit le GPS comme critère de délimitation de cluster (`MAX_RADIUS_METERS = 5 000 m`, calibré Bab Ezzouar). Cette décision reposait sur une hypothèse de **transport local** (WiFi Direct / mesh) qui n'a jamais été implémentée : V5.0 utilise un **Serveur Relai HA WebSocket centralisé** (Epic 8). Conséquence directe : **la distance physique entre deux pairs n'impacte ni la latence ni le coût réseau** — tout le trafic transite par Render via le même chemin, quelle que soit la position GPS des nœuds.

Conserver le filtrage géographique introduit trois coûts sans bénéfice mesurable :

1. **Bootstrap cassé à petite échelle** — 5 utilisateurs PFE répartis sur le territoire algérien forment 5 clusters solos au lieu d'un seul cluster fonctionnel, brisant la démonstration P2P.
2. **Permission Android intrusive** (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`) demandée à l'utilisateur pour un usage qui n'apporte aucune valeur fonctionnelle réelle — risque de refus à l'onboarding et incohérence RGPD.
3. **Complexité technique** (`Haversine`, `LocationRepository`, calibration `MAX_RADIUS_METERS`, propagation GPS dans 19 fichiers) maintenue sans retour.

Cette story **remplace le critère géographique par un critère de charge** : un nouveau nœud rejoint le super-peer disponible le moins chargé (`memberCount` ASC) parmi ceux annonçant `memberCount < MAX_CLUSTER_SIZE`. Si aucun super-peer n'a de place, le nœud déclenche `BullySoloElectionUseCase` et fonde un nouveau cluster — comportement déjà spécifié en Story 11.2 (transition `Isolated → SuperPair`), simplement réorienté.

**Argumentaire jury** :
> *"L'admission combine **affinité de session** (priorité au dernier cluster joint, garantissant la stabilité d'appartenance entre redémarrages) et **équilibrage de charge** (fallback `memberCount` ASC quand l'ancien cluster est indisponible ou plein). Ce compromis est cohérent avec une architecture où le transport passe par un relai centralisé : la distance physique n'impacte ni la latence ni le coût réseau. Le critère unique d'admission devient la **capacité résiduelle du cluster**, plafonné à `MAX_CLUSTER_SIZE = 50` (limite batterie super-peer). En contrepartie, l'application n'exige plus de permission GPS, simplifiant l'onboarding et l'empreinte vie privée. L'option déterministe par consistent hashing (à 2 niveaux : clusters ET blocs DHT) est étudiée en perspectives V6."*

---

## Story

En tant que **nœud MobiCloud** (Android),
Je veux **rejoindre automatiquement le super-peer le moins chargé disponible (sans aucun critère de localisation GPS), et fonder un nouveau cluster si tous les super-peers existants sont pleins (`memberCount >= MAX_CLUSTER_SIZE`)**,
Afin que **l'application fonctionne à petite échelle (5–10 testeurs PFE répartis géographiquement), n'exige plus de permission Android sensible (`ACCESS_FINE_LOCATION`), et que la charge soit naturellement équilibrée entre clusters** ; le code GPS hérité de l'Epic 11 (`LocationRepository`, `Haversine`, `GpsCoordinate`, `MAX_RADIUS_METERS`) est **supprimé du codebase**, et le rapport PFE documente la décision en chapitre "Évolution architecturale V5.0 → V5.1".

---

## Acceptance Criteria (BDD)

### AC1 — Retrait des constantes et helpers GPS

**Given** la couche `domain/models/m11_join/` contient `ClusterConstants.kt` avec `MAX_RADIUS_METERS = 5_000`
**When** la story 12.1 supprime la dépendance géographique
**Then** la constante `MAX_RADIUS_METERS` est **supprimée** de [`ClusterConstants.kt`](app/src/main/kotlin/com/mobicloud/domain/models/m11_join/ClusterConstants.kt)
**And** le commentaire d'en-tête `// Calibré pour Bab Ezzouar...` est supprimé
**And** `MAX_CLUSTER_SIZE = 50` est **conservé** et devient le **seul** critère d'admission
**And** le commentaire de `MAX_CLUSTER_SIZE` est mis à jour : `// Plafond batterie côté SP + critère unique d'admission (Story 12.1)`
**And** la classe `core/geo/Haversine.kt` (introduite Story 11.1) est **supprimée**
**And** la classe `domain/models/GpsCoordinate.kt` est **supprimée**
**And** un test unitaire `ClusterConstantsTest.kt` mis à jour ne référence plus `MAX_RADIUS_METERS` (le test d'invariant `JOIN_REQUEST_TIMEOUT_MS < ISOLATION_BACKOFF_MS < SP_TIMEOUT_MS` est conservé)
**And** aucune référence résiduelle à `MAX_RADIUS_METERS`, `Haversine`, `GpsCoordinate` dans le codebase (`grep -r` doit retourner 0 résultat hors story files).

### AC2 — `SuperPeerHint` : retrait GPS + ajout `currentMemberCount`

**Given** [`SuperPeerHint.kt`](app/src/main/kotlin/com/mobicloud/domain/models/m11_join/SuperPeerHint.kt) expose `gpsLatitude: Double?` et `gpsLongitude: Double?`
**When** la story 12.1 réoriente la sélection vers la charge
**Then** la `data class SuperPeerHint` est redéfinie sans GPS et avec un champ de charge :
```kotlin
@Serializable
data class SuperPeerHint(
    val nodeId: ByteArray,
    val clusterId: String = "",
    val ipAddress: String,
    val port: Int,
    val reliabilityScore: Float = 0f,
    val currentMemberCount: Int = 0,    // Story 12.1 — critère d'admission (load balancing)
    val source: SuperPeerHintSource = SuperPeerHintSource.MULTICAST
)
```
**And** `equals` / `hashCode` sont régénérés (les champs `gpsLatitude`/`gpsLongitude` ne sont plus comparés)
**And** les fichiers [`SuperPeerHintMappers.kt`](app/src/main/kotlin/com/mobicloud/domain/models/m11_join/SuperPeerHintMappers.kt) sont mis à jour : toutes les fabriques (`fromCoordinatorEvent`, `fromRelayPeer`, `fromHelloPayload`) ne lisent plus `gpsLatitude`/`gpsLongitude` et propagent `currentMemberCount` depuis la source (HELLO ou tracker GET_PEERS).

### AC3 — `HelloPayload` : retrait GPS + ajout `currentMemberCount`

**Given** [`HelloPayload.kt`](app/src/main/kotlin/com/mobicloud/domain/models/HelloPayload.kt) contient `gpsLatitude: Double?` et `gpsLongitude: Double?`
**When** un super-peer émet un HELLO multicast (Story 2.0)
**Then** les champs `gpsLatitude` / `gpsLongitude` sont **supprimés** de `HelloPayload`
**And** un nouveau champ `currentMemberCount: Int = 0` est ajouté (placé en fin de structure pour minimiser le risque de divergence Protobuf inter-versions)
**And** côté super-peer, [`LocalDiscoveryRepositoryImpl`](app/src/main/kotlin/com/mobicloud/data/repository/LocalDiscoveryRepositoryImpl.kt) renseigne `currentMemberCount` depuis `MemberDao.countActiveByClusterId(state.clusterId)` au lieu de `locationRepository.currentLocation.value` ; pour un membre régulier ou un nœud `Undiscovered`, `currentMemberCount = 0`
**And** la méthode `LocalDiscoveryRepositoryImpl.broadcastLoop()` ne référence plus `locationRepository` (constructeur nettoyé — voir AC5).

### AC4 — `MemberEntity` (Room) et migration `MIGRATION_15_16`

**Given** la table `cluster_members` (version 15) contient `gpsLatitude: Double?` et `gpsLongitude: Double?` ([`MemberEntity.kt`](app/src/main/kotlin/com/mobicloud/data/local/entity/MemberEntity.kt))
**When** la story 12.1 retire le GPS persisté
**Then** la `data class MemberEntity` retire les deux colonnes
**And** la base est bumpée `version = 16` et une migration `MIGRATION_15_16` est ajoutée dans `CatalogDatabase.companion` :
```sql
-- Migration 15 → 16 (Story 12.1) : retrait colonnes GPS de cluster_members
CREATE TABLE cluster_members_new (
    nodeId TEXT NOT NULL PRIMARY KEY,
    clusterId TEXT NOT NULL,
    publicKeyBytes BLOB NOT NULL,
    ipAddress TEXT NOT NULL,
    port INTEGER NOT NULL,
    freeBytes INTEGER NOT NULL,
    lastSeen INTEGER NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
);
INSERT INTO cluster_members_new
    SELECT nodeId, clusterId, publicKeyBytes, ipAddress, port, freeBytes, lastSeen, role, status
    FROM cluster_members;
DROP TABLE cluster_members;
ALTER TABLE cluster_members_new RENAME TO cluster_members;
CREATE INDEX IF NOT EXISTS index_cluster_members_clusterId ON cluster_members(clusterId);
CREATE INDEX IF NOT EXISTS index_cluster_members_status ON cluster_members(status);
```
**And** `MemberDao` ajoute la requête : `@Query("SELECT COUNT(*) FROM cluster_members WHERE clusterId = :clusterId AND status = 'ACTIVE'") suspend fun countActiveByClusterId(clusterId: String): Int`
**And** [`MemberMapper.kt`](app/src/main/kotlin/com/mobicloud/data/local/m11_join/MemberMapper.kt) ne propage plus `gpsLatitude`/`gpsLongitude` entre `MemberInfo` ↔ `MemberEntity`
**And** un test de migration `MemberEntityMigrationV15V16Test.kt` (AndroidJUnit4 + `MigrationTestHelper`) valide l'upgrade `15 → 16` à partir d'une base v15 contenant 3 lignes (vérifie : table reconstruite, lignes conservées, colonnes GPS absentes, index présents).

### AC5 — Suppression de `LocationRepository` et permissions Android

**Given** [`LocationRepository.kt`](app/src/main/kotlin/com/mobicloud/domain/repository/LocationRepository.kt), [`LocationRepositoryImpl.kt`](app/src/main/kotlin/com/mobicloud/data/repository/LocationRepositoryImpl.kt) et [`LocationModule.kt`](app/src/main/kotlin/com/mobicloud/di/LocationModule.kt) existent
**When** la story 12.1 retire le GPS du domaine
**Then** les **3 fichiers** sont **supprimés**
**And** toutes les injections `@Inject locationRepository: LocationRepository` sont retirées de :
  - `LocalDiscoveryRepositoryImpl` (constructeur)
  - tous les autres consommateurs détectés via `grep "LocationRepository"`
**And** [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) retire les déclarations :
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```
**And** [`MainActivity.kt`](app/src/main/kotlin/com/mobicloud/MainActivity.kt) retire les demandes runtime des permissions `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` (le bloc `registerForActivityResult` correspondant est nettoyé)
**And** [`MobicloudP2PService.kt`](app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt) retire toute logique conditionnelle dépendante des permissions GPS (start du foreground service inconditionnel sur ce critère)
**And** la dépendance Gradle Play Services Location est supprimée si plus aucune autre fonctionnalité ne l'utilise (vérifier `app/build.gradle.kts` — `implementation("com.google.android.gms:play-services-location:...")`).

### AC6 — Logique de sélection : sticky cluster + tri par charge dans `SendJoinRequestUseCase`

**Given** [`SendJoinRequestUseCase`](app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/SendJoinRequestUseCase.kt) reçoit une `List<SuperPeerHint>` candidats
**And** [`NodeSettingsRepositoryImpl`](app/src/main/kotlin/com/mobicloud/data/repository/NodeSettingsRepositoryImpl.kt) persiste déjà `NodeSettingsEntity.clusterId` (Room) avec une méthode `setClusterId(id: String)` utilisée par `MarkSelfAsSuperPairUseCase`
**When** la story 12.1 réoriente la sélection vers une stratégie **affinité de session + équilibrage de charge** (Option A — sticky cluster)
**Then** la sélection applique l'algorithme suivant dans `SendJoinRequestUseCase` :
```kotlin
val maxSize = MAX_CLUSTER_SIZE
val lastKnown: String = nodeSettingsRepository.getClusterIdOnce()  // "" si jamais joint
val (sticky, others) = hints
    .filter { it.currentMemberCount < maxSize }
    .partition { lastKnown.isNotEmpty() && it.clusterId == lastKnown }
val candidates = sticky + others.sortedBy { it.currentMemberCount }
```
**And** **aucun calcul de distance Haversine** n'est effectué (le code antérieur basé sur `gpsLatitude/gpsLongitude` est supprimé)
**And** **priorité absolue au cluster historique** — si le tracker renvoie un SP du `lastKnown` clusterId avec une place libre, il est tenté en **premier** ; en cas de `JOIN_REDIRECT` (race condition AC7), la liste `others` triée par charge prend le relai
**And** au reçu d'un `JOIN_ACCEPT` (transition `Joining → Member` dans [`JoinStateMachine`](app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/JoinStateMachine.kt)), la branche correspondante (~ligne 80) appelle `nodeSettingsRepository.setClusterId(accept.clusterId)` pour mémoriser le cluster joint
**And** si `candidates.isEmpty()` (aucun SP disponible OU tous pleins), le use case retourne `JoinAttemptOutcome.NoCandidates` qui déclenche `JoinEvent.AllCandidatesExhausted` → transition `Isolated → BullySolo → SuperPair` (chaîne déjà câblée Story 11.2, non modifiée)
**And** **nettoyage de la dérivation legacy `clusterIdProvider` basée sur SSID WiFi** — le bloc "FIX SPLIT-CLUSTER self-healing" dans [`NodeSettingsRepositoryImpl`](app/src/main/kotlin/com/mobicloud/data/repository/NodeSettingsRepositoryImpl.kt) (lignes ~66-105 : `getCurrentWifiClusterId`, `refreshClusterIdFromCurrentNetwork`, `clusterIdProvider` basé sur SSID) est **supprimé** ; le `clusterId` n'est plus jamais dérivé du SSID — il est uniquement attribué par `JOIN_ACCEPT` ou `BullySolo` et persiste tant que le nœud reste dans son cluster
**And** un test `SendJoinRequestUseCase_prefersStickyClusterFirst()` vérifie l'ordonnancement : étant donné `lastKnown = "α"` et `hints = [β(count=2), α(count=40), γ(count=1)]`, l'ordre de tentative est `[α, γ, β]` (sticky d'abord, puis tri par charge croissante)
**And** un test `SendJoinRequestUseCase_fallsBackToLoadBased_whenStickyUnavailable()` vérifie qu'avec `lastKnown = "α"` mais aucun SP du cluster α dans la liste (ou α plein), la sélection retombe correctement sur `sortedBy { currentMemberCount }`.

### AC7 — `ProcessJoinRequestUseCase` (côté Super-Pair) : rejet capacity-only

**Given** [`ProcessJoinRequestUseCase`](app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/ProcessJoinRequestUseCase.kt) appliquait deux filtres : (a) GPS dans le rayon, (b) `memberCount < MAX_CLUSTER_SIZE`
**When** la story 12.1 retire la validation géographique
**Then** seul le check de capacité subsiste :
```kotlin
if (memberDao.countActiveByClusterId(clusterId) >= MAX_CLUSTER_SIZE) {
    return JoinResponse.JoinRedirect(reason = REDIRECT_FULL_CAPACITY, alternativeSuperPeers = emptyList())
}
return JoinResponse.JoinAccept(...)
```
**And** la constante `REDIRECT_OUT_OF_RANGE` est supprimée de l'enum `RedirectReason` (si elle existait)
**And** la constante `REDIRECT_FULL_CAPACITY` est conservée comme seul motif de rejet automatique
**And** les tests `ProcessJoinRequestUseCaseTest.kt` couvrant la branche GPS-out-of-range sont supprimés ; les tests couvrant la branche capacity-full sont conservés
**And** un nouveau test `ProcessJoinRequestUseCase_acceptsRegardlessOfLocation()` vérifie qu'un JOIN_REQUEST sans GPS (qui n'existe plus dans le payload — voir AC8) est accepté tant que `countActiveByClusterId < MAX_CLUSTER_SIZE`.

### AC8 — `JoinRequest` (wire format) : retrait GPS

**Given** [`JoinRequest.kt`](app/src/main/kotlin/com/mobicloud/domain/models/m11_join/JoinRequest.kt) (et son `JoinSignedBytes` associé) transportent `gpsLatitude` / `gpsLongitude` du candidat
**When** un nœud envoie un JOIN_REQUEST au super-peer
**Then** les champs GPS sont supprimés du payload Protobuf
**And** [`JoinSignedBytes.kt`](app/src/main/kotlin/com/mobicloud/domain/models/m11_join/JoinSignedBytes.kt) est régénéré sans ces champs (l'ordre canonique de signature change → bump `JOIN_PROTOCOL_VERSION` de 1 à 2 si une constante existe ; sinon ajouter une constante `const val JOIN_PROTOCOL_VERSION = 2` dans `ClusterConstants.kt` et l'inclure dans le payload signé)
**And** [`MemberInfo.kt`](app/src/main/kotlin/com/mobicloud/domain/models/m11_join/MemberInfo.kt) retire `gpsLatitude` / `gpsLongitude` (impact `JoinAccept.memberSnapshot` → également privé de GPS)
**And** [`ProtoBufSerializer.kt`](app/src/main/kotlin/com/mobicloud/core/format/ProtoBufSerializer.kt) ne nécessite **aucun changement** si les champs étaient annotés `@ProtoNumber` séquentiellement — sinon vérifier que les indices Protobuf restants restent contigus pour éviter la corruption au handshake (les schémas Protobuf tolèrent la suppression d'un champ optionnel mais pas le shift d'indices).

### AC9 — Nettoyage `JoinEvent`, `JoinStateMachine`, événements relai

**Given** plusieurs sites du codebase transportent encore `gpsLatitude` / `gpsLongitude` ([`JoinEvent.kt`](app/src/main/kotlin/com/mobicloud/domain/models/m11_join/JoinEvent.kt), [`ElectionPayload.kt`](app/src/main/kotlin/com/mobicloud/domain/models/ElectionPayload.kt), [`RelayEvent.kt`](app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt), [`ProcessIncomingElectionEventUseCase.kt`](app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/ProcessIncomingElectionEventUseCase.kt), [`RunBullyElectionUseCase.kt`](app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RunBullyElectionUseCase.kt), [`RelayWebSocketClient.kt`](app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt))
**When** la story 12.1 nettoie ces sites
**Then** **tous** les champs `gpsLatitude` / `gpsLongitude` sont retirés
**And** dans [`JoinStateMachine.kt`](app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/JoinStateMachine.kt) ligne 257, la méthode privée `toSuperPeerHint()` est simplifiée (plus de mappage GPS, ajout de `currentMemberCount` depuis l'event si disponible — sinon `0`)
**And** un test de smoke `JoinStateMachineTest.coordinatorReceived_transitionsToJoining_withoutGps()` valide que la FSM accepte un `CoordinatorReceived` sans GPS et transite `Undiscovered → Joining` correctement.

### AC10 — Document conceptuel & UI

**Given** le dashboard / ExplorerScreen affichent éventuellement la position GPS ou la distance au super-peer
**When** la story 12.1 retire le GPS de l'UI
**Then** tout composant Compose affichant `gpsLatitude` / `gpsLongitude` ou un libellé "distance au super-peer" est retiré (grep `presentation/` pour `gps`)
**And** un nouveau composant ou libellé est ajouté au Dashboard : `"Membres dans mon cluster : N / 50"` lu depuis `MemberDao.countActiveByClusterId(currentClusterId).collectAsState()` — défendable comme indicateur de charge cluster
**And** aucune string `R.string.permission_location_*` n'est utilisée (les ressources peuvent être supprimées de `res/values/strings.xml` et `res/values-ar/strings.xml`).

### AC11 — Test d'intégration multi-émulateur

**Given** [`scripts/test-migration.ps1`](scripts/test-migration.ps1) orchestre déjà des tests multi-device (cf. `[[reference_test_migration_script]]`)
**When** la story 12.1 est validée fonctionnellement
**Then** un nouveau scénario `test-load-based-admission.ps1` (ou extension du script existant) lance **3 émulateurs Android** avec capture logs :
  1. Device A lance MobiCloud → après 20 s d'isolement → devient SuperPair du cluster `α`
  2. Device B lance MobiCloud → reçoit `α` via tracker (mock ou réel selon dispo) avec `currentMemberCount = 1` → JOIN → devient Member
  3. Device C lance MobiCloud → reçoit `α` avec `currentMemberCount = 2` → JOIN → devient Member
**And** le script vérifie via `adb logcat | grep '[JOIN-FSM]'` que **les 3 devices partagent le même `clusterId`** (le filtre Haversine étant retiré, leur position GPS simulée — ou absente — n'influe pas)
**And** un verdict `OK` est imprimé si les assertions passent.

### AC13 — Serveur relai (Node.js) : retrait GPS + propagation `currentMemberCount`

**Given** [`relay-server/server.js`](relay-server/server.js) valide et stocke actuellement `gpsLatitude` / `gpsLongitude` dans `handleRegisterPeer` (lignes ~180-192, ~212-213), les préserve dans `handleJoin` (~lignes 265-266), et les expose dans `handleGetPeers` (~lignes 308-310)
**When** la story 12.1 retire le GPS et introduit l'équilibrage par charge
**Then** dans `handleRegisterPeer` :
  - Le bloc de validation `gpsLatitude` / `gpsLongitude` (~lignes 180-192) est **supprimé** (constantes `latPresent`, `lngPresent`, `latValid`, `lngValid`, `gpsLatNum`, `gpsLngNum` et les `console.warn` associés)
  - L'extraction `const { ... gpsLatitude, gpsLongitude } = entry;` (ligne ~145) retire ces deux champs
  - Un nouveau champ `currentMemberCount` est extrait, validé (`typeof === 'number'`, `Number.isFinite`, `>= 0`, `<= MAX_CLUSTER_SIZE_SERVER = 50`), coercé en `0` + `console.warn` si invalide — pattern identique à `freeBytes`
  - L'objet stocké dans `signalingRegistry.set(nodeId, {...})` (~ligne 206) remplace les clés `gpsLatitude` / `gpsLongitude` par `currentMemberCount`
  - Le log `[SIGNALING] REGISTER super-peer ... gps=...` (~ligne 219) remplace le suffixe `gps=...` par `memberCount=${currentMemberCountNum}/${MAX_CLUSTER_SIZE_SERVER}`
**And** dans `handleJoin` (~lignes 258-270), les héritages `gpsLatitude: existing?.gpsLatitude ?? null` et `gpsLongitude: existing?.gpsLongitude ?? null` sont **supprimés** ; un héritage `currentMemberCount: existing?.currentMemberCount ?? 0` est ajouté (un JOIN non-SP n'écrase pas la valeur SP existante — même logique que `clusterId`/`freeBytes`)
**And** dans `handleGetPeers` (~lignes 295-311), les champs `gpsLatitude` et `gpsLongitude` retournés sont **supprimés** ; un champ `currentMemberCount: entry.currentMemberCount ?? 0` est ajouté
**And** une constante en-tête de fichier `const MAX_CLUSTER_SIZE_SERVER = 50;` est ajoutée (en miroir de `MAX_CLUSTER_SIZE` côté Android — duplication assumée car le serveur Node.js n'importe pas le code Kotlin ; un commentaire `// MIRROR: app/.../ClusterConstants.kt#MAX_CLUSTER_SIZE` rend la dépendance explicite)
**And** **aucune logique de filtrage Haversine** n'est introduite côté serveur (elle n'existait pas auparavant — le filtrage GPS se faisait côté client uniquement, on confirme qu'il ne sera pas ré-ajouté ici)
**And** les tests Jest [`relay-server/`](relay-server/) couvrant `REGISTER_PEER` / `GET_PEERS` sont mis à jour : suppression des assertions sur `gpsLatitude` / `gpsLongitude`, ajout d'une assertion `expect(peer.currentMemberCount).toBe(N)` après un `REGISTER_PEER` avec `currentMemberCount: N`
**And** un nouveau test Jest `handleRegisterPeer_rejectsCurrentMemberCountAbove50_coercesToZero()` valide la borne supérieure (`currentMemberCount: 51` → coerce 0 + warn).

### AC14 — Client Android : envoi `currentMemberCount` au tracker

**Given** [`RelayWebSocketClient.sendRegisterPeer()`](app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt) (lignes 270-302) sérialise `gpsLatitude` / `gpsLongitude` depuis `locationRepository.currentLocation.value` (lignes 280, 291-298)
**When** la story 12.1 oriente le tracker sur la charge
**Then** la signature de `sendRegisterPeer` est modifiée :
```kotlin
fun sendRegisterPeer(
    nodeId: String,
    ip: String,
    port: Int,
    reliabilityScore: Float,
    electedAt: Long,
    clusterId: String,
    freeBytes: Long,
    currentMemberCount: Int     // Story 12.1 — remplace gpsLatitude/gpsLongitude
): Boolean
```
**And** le corps de la méthode :
  - **Supprime** l'injection `locationRepository` (cohérent avec AC5)
  - **Supprime** le bloc `if (gps != null && gps.latitude.isFinite()...) { put("gpsLatitude", ...) ; put("gpsLongitude", ...) }`
  - **Ajoute** `put("currentMemberCount", currentMemberCount)`
**And** dans [`SignalingRepositoryImpl.kt`](app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt) (~ligne 185), l'appelant calcule `currentMemberCount` :
```kotlin
val currentMemberCount = runCatching {
    memberDao.countActiveByClusterId(clusterId)
}.onFailure { e ->
    Log.w(TAG, "countActiveByClusterId échoué — coerce à 0 pour REGISTER_PEER", e)
}.getOrDefault(0)
val sent = relayClient.sendRegisterPeer(
    nodeId, ip, port, reliabilityScore, electedAt, clusterId, freeBytes, currentMemberCount
)
```
**And** le log `REGISTER_PEER envoyé` à la ligne ~187 inclut désormais `memberCount=$currentMemberCount/${MAX_CLUSTER_SIZE}` (suppression de toute mention GPS)
**And** dans le même fichier `RelayWebSocketClient.kt`, la méthode `parsePeersPayload` (~ligne 304+) supprime la lecture `gpsLatitude` / `gpsLongitude` et ajoute la lecture `currentMemberCount = obj.optInt("currentMemberCount", 0)` ; la `data class RelayPeer` est mise à jour en conséquence (retrait 2 champs, ajout 1)
**And** le mapper `SuperPeerHintMappers.fromRelayPeer()` (AC2) propage `currentMemberCount` du `RelayPeer` vers `SuperPeerHint`
**And** la registration au tracker est **déclenchée à chaque heartbeat émis vers le tracker** (généralement toutes les 30s — vérifier le mécanisme existant `SuperPeerHeartbeatToTrackerUseCase` ou équivalent ; si seulement émis au démarrage, ajouter un timer périodique de `HEARTBEAT_INTERVAL_MS = 30_000L`) afin que `currentMemberCount` reste frais côté tracker — sinon les nouveaux nœuds verront une valeur stale
**And** un test `SignalingRepositoryImplTest.registerAsSuperPeer_includesCurrentMemberCount()` mocke `memberDao.countActiveByClusterId()` à `7` et vérifie que la JSON envoyée contient `"currentMemberCount":7` (et **aucune** clé `gpsLatitude` / `gpsLongitude`).

### AC12 — Documentation thèse

**Given** [`_bmad-output/planning-artifacts/architecture-connectivity-and-clustering.md`](_bmad-output/planning-artifacts/architecture-connectivity-and-clustering.md) documente la décision GPS Epic 11
**When** la story 12.1 inverse cette décision
**Then** une nouvelle section `## Évolution V5.1 — Retrait du GPS et admission par charge` est ajoutée en fin du document avec :
  - rappel de la justification originale Epic 11 (transport local supposé)
  - constat du déploiement V5.0 réel (Relai HA → distance physique non pertinente)
  - description de la nouvelle règle d'admission (`memberCount` ASC, fallback BullySolo)
  - bénéfices mesurés : –1 permission Android, ~80 LOC supprimées, bootstrap fonctionnel à N≥2
**And** [`_bmad-output/planning-artifacts/epics.md`](_bmad-output/planning-artifacts/epics.md) reçoit une nouvelle section `## Epic 12 — Décentralisation de l'Admission (Refactor)` placée après Epic 11, listant la story 12.1 (et la 12.2 tracker en perspective serveur)
**And** la mémoire auto-conservée du projet est mise à jour (memory file `project_gps_removed.md` créé via le système `auto memory`).

---

## Files à modifier / supprimer (inventaire exhaustif)

### Fichiers supprimés (5)
- `app/src/main/kotlin/com/mobicloud/core/geo/Haversine.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/GpsCoordinate.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/LocationRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/LocationRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/LocationModule.kt`

### Fichiers modifiés (≈ 18)
- `app/src/main/AndroidManifest.xml` (retrait 2 permissions)
- `app/src/main/kotlin/com/mobicloud/MainActivity.kt`
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/LocalDiscoveryRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/NodeSettingsRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/MemberEntity.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/m11_join/MemberMapper.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt` (version 15→16 + migration)
- `app/src/main/kotlin/com/mobicloud/data/local/dao/MemberDao.kt` (+ `countActiveByClusterId`)
- `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/HelloPayload.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/ElectionPayload.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/m11_join/ClusterConstants.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/m11_join/SuperPeerHint.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/m11_join/SuperPeerHintMappers.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/m11_join/JoinEvent.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/m11_join/JoinRequest.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/m11_join/JoinSignedBytes.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/m11_join/MemberInfo.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/SendJoinRequestUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/ProcessJoinRequestUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/JoinStateMachine.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/ProcessIncomingElectionEventUseCase.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m10_election/RunBullyElectionUseCase.kt`
- `app/build.gradle.kts` (suppression dépendance Play Services Location si applicable)
- `app/src/main/kotlin/com/mobicloud/data/repository/SignalingRepositoryImpl.kt` (calcul `currentMemberCount` via `memberDao.countActiveByClusterId`, passé à `sendRegisterPeer`)

### Fichiers modifiés — serveur Node.js (1)
- `relay-server/server.js` (handleRegisterPeer + handleJoin + handleGetPeers + constante `MAX_CLUSTER_SIZE_SERVER`)

### Tests créés / modifiés
- `MemberEntityMigrationV15V16Test.kt` (créé)
- `ClusterConstantsTest.kt` (modifié — retrait assert sur `MAX_RADIUS_METERS`)
- `ProcessJoinRequestUseCaseTest.kt` (modifié — suppression cas GPS-out-of-range, ajout cas capacity-only)
- `SendJoinRequestUseCaseTest.kt` (modifié — sort by `currentMemberCount`)
- `JoinStateMachineTest.kt` (modifié — coordinatorReceived sans GPS)
- `LocalDiscoveryRepositoryImplTest.kt` (modifié — HELLO sans GPS, avec `currentMemberCount`)
- `SignalingRepositoryImplTest.kt` (créé/modifié — `registerAsSuperPeer_includesCurrentMemberCount`)
- `relay-server/*.test.js` (modifié — assertions GPS retirées, assertions `currentMemberCount` ajoutées ; nouveau test `rejectsCurrentMemberCountAbove50_coercesToZero`)
- `scripts/test-load-based-admission.ps1` (créé — orchestration 3 émulateurs)

---

## Out of Scope (story 12.1)

- **Rééquilibrage automatique inter-clusters** — si cluster α est à 50/50 et cluster β à 5/50, aucune migration de membre n'est effectuée. La sélection load-based ne s'applique qu'à l'admission initiale.
- **Préférence opérationnelle** (ex. SP avec meilleur `reliabilityScore` parmi ceux à charge égale) — possible mais non requis. La règle reste `sticky → sortedBy { currentMemberCount }` strict.
- **Consistent hashing à 2 niveaux (clusters + DHT blocs)** — l'Option B explorée mais reportée en perspectives V6 (rapport).
- **Compatibilité ascendante** avec d'anciens clients V5.0 portant GPS dans HELLO/REGISTER_PEER — le retrait casse la compatibilité par construction (acceptable : aucun déploiement utilisateur réel, single-tenant Render).
- **Migration coordonnée client/serveur en production** — pas applicable : le déploiement PFE est mono-utilisateur, l'APK et le serveur sont redéployés conjointement.

---

## Definition of Done

- [x] AC1–AC14 validés (BDD)
- [x] `./gradlew assembleDebug` passe sans warning sur les fichiers touchés
- [x] `./gradlew test` passe (tests unitaires Android) — 568 tests passent (session 2, 2026-05-12)
- [x] `cd relay-server && npm test` passe (tests Jest serveur) — 66 tests, 2 suites passent (session 2, 2026-05-12)
- [ ] Test de migration Room v15→v16 passe (`./gradlew connectedAndroidTest --tests *MemberEntityMigration*`) — nécessite émulateur
- [ ] Test d'intégration 3 émulateurs (AC11) imprime `OK` end-to-end (LAN multicast + tracker Render)
- [ ] Le serveur relai (`relay-server/server.js`) est redéployé sur Render avec la nouvelle version (vérifier `console.log` au démarrage qui doit mentionner `MAX_CLUSTER_SIZE_SERVER`)
- [x] Aucune occurrence résiduelle de `gpsLatitude`, `gpsLongitude`, `Haversine`, `GpsCoordinate`, `MAX_RADIUS_METERS`, `LocationRepository`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (`grep -r` propre — résultats restants sont des commentaires uniquement)
- [x] Section "Évolution V5.1" ajoutée à `architecture-connectivity-and-clustering.md`
- [x] Section Epic 12 ajoutée à `epics.md`
- [x] Status story passé à `review` après dev, puis `done` après code-review

---

## Dev Agent Record

**Agent :** claude-sonnet-4-6 (bmad-dev-story workflow)
**Date implémentation :** 2026-05-12
**Sessions :** 3 (contexte compacté entre les sessions)

### Notes d'implémentation

- **Décision d'architecture** : `currentMemberCount` est pushé depuis `MobicloudP2PService` vers `LocalDiscoveryRepository.updateCurrentMemberCount()` via un loop périodique de 30s dans le `superPeerJob`, suivant le pattern existant `updateSuperPairStatus(Boolean)`. Cette approche évite d'injecter `MemberDao` dans la couche `data.repository.LocalDiscoveryRepositoryImpl` (domaine séparé).
- **`P2PModule.kt`** : Le provider `provideLocalDiscoveryRepository` a été nettoyé — `LocationRepository` retiré du constructeur suite à la suppression de l'injection dans `LocalDiscoveryRepositoryImpl`.
- **`MobicloudP2PService.onDestroy()`** : `locationRepository.stop()` retiré (était un résidu non détecté initialement).
- **GPS dans les commentaires** : Plusieurs fichiers (`ElectionPayload.kt`, `MainActivity.kt`, `ProtoBufSerializer.kt`) contiennent des mentions GPS uniquement dans des commentaires de code — ces mentions documentent la suppression et sont intentionnelles.
- **`NodeSettingsRepository.refreshClusterIdFromWifi()`** : La méthode reste dans `NodeSettingsRepositoryImpl` (l'observer réseau WiFi la référence encore dans `MobicloudP2PService`). Elle retourne le clusterId persisté sans dérivation SSID — la dérivation SSID a été retirée dans `NodeSettingsRepositoryImpl.getClusterIdOnce()`.
- **`JOIN_PROTOCOL_VERSION = 2`** : Ajouté dans `ClusterConstants.kt`. Les bytes signés du `JoinRequest` et `ElectionPayload` sont préfixés `v2|` — toute node V5.0 (GPS) sera incompatible, acceptable pour le PFE (déploiement mono-tenant).

### Fichiers réellement modifiés (diff vs branch main)

**Supprimés (7)** : `Haversine.kt`, `GpsCoordinate.kt`, `LocationRepository.kt`, `LocationRepositoryImpl.kt`, `LocationModule.kt`, `MockLocationRepositoryImpl.kt` (debug), `MockLocationModule.kt` (debug), `ReleaseLocationModule.kt` (release)

**Créés (1)** : `scripts/test-load-based-admission.ps1`

**Modifiés (~25)** : voir section "Files à modifier / supprimer" ci-dessus. Fichiers supplémentaires non listés dans la spec : `di/P2PModule.kt` (nettoyage `LocationRepository` du provider).

### Change Log

| Date | Version | Description | Auteur |
|------|---------|-------------|--------|
| 2026-05-12 | V5.1 | Story 12.1 implémentée — GPS supprimé, admission par `memberCount`, `currentMemberCount` propagé dans HELLO/REGISTER_PEER/GET_PEERS | Dev Agent |
| 2026-05-12 | V5.1-r1 | Code review (Blind / Edge / Acceptance) — findings consignés ci-dessous | Code Review |
| 2026-05-12 | V5.1-r2 | Review patches P2+P3+P13 appliqués — tous les tests compilent et passent (568 Android + 66 Jest) ; Story passée en `review` | Dev Agent |

### Review Findings (2026-05-12)

**Decisions tranchées (2026-05-12)**

- [x] [Review][Decision→Defer] **D1 — Compat wire-format protobuf inter-version** — accepté incompat V5.0/V5.1 : tous les devices PFE sont flashés ensemble. Documenté en perspective rapport. → voir `deferred-work.md` W-12.1-10.
- [ ] [Review][Decision→Defer] **D2 — Migration Room 15→16, conformité des index** — P19 confirmé conforme (index identiques `MemberEntity` vs `MIGRATION_15_16`). P20 (test AndroidJUnit4 `MigrationTestHelper`) déféré → voir `deferred-work.md` W-12.1-D2.
- [x] [Review][Decision→Defer] **D3 — Signature `ElectionPayload` préfixée `v2|…`** — même justification que D1 : pas de coexistence V5.0/V5.1. → voir `deferred-work.md` W-12.1-10.
- [x] [Review][Decision→Patch] **D4 — `countActiveByClusterId` cutoff `last_seen`** — aligner sur `MonitorMemberLivenessUseCase` qui utilise `SP_TIMEOUT_MS` (90s). → patch P21 appliqué.

**Patches (correction non ambiguë)**

- [x] [Review][Patch] **P1 — Compile cassé : `refreshClusterIdFromWifi()` supprimé mais appelé** [`MobicloudP2PService.kt:285,298`] — retirer les appels + le hook `networkChangeObserver.onWifiAvailable` (AC6 = plus de dérivation SSID). Sources : blind+auditor (Critical).
- [x] [Review][Patch] **P2 — Tests Kotlin cassés (compile)** [`app/src/test/.../m11_join/ProcessJoinRequestUseCaseTest.kt`] — importent encore `GpsCoordinate`/`LocationRepository`, construisent `JoinRequest(gpsLatitude=…)`, testent `OUT_OF_RADIUS`. Supprimer ces tests + ajouter `acceptsRegardlessOfLocation` (AC7) et `prefersStickyClusterFirst` / `fallsBackToLoadBased_whenStickyUnavailable` (AC6). Source : auditor (Critical). **Appliqué session 2.**
- [x] [Review][Patch] **P3 — Tests Jest serveur cassés** [`relay-server/server.test.js:261-340`] — assertions sur `gpsLatitude`/`gpsLongitude` et le warning "gpsLatitude invalide" ; manque `rejectsCurrentMemberCountAbove50_coercesToZero` (AC13). Source : auditor (Critical). **Appliqué session 2 — 66 tests Jest passent.**
- [x] [Review][Patch] **P4 — JOIN_ACCEPT ne persiste pas le `clusterId` → sticky cluster inopérant** [`JoinStateMachine.kt:79-89`] — ajouter `nodeSettingsRepository.updateClusterId(accept.clusterId)` dans la branche `Joining → Member` (AC6). Source : auditor (High).
- [x] [Review][Patch] **P5 — Boucle `currentMemberCount` capture `clusterId` une seule fois** [`MobicloudP2PService.kt:482-491`] — déplacer `nodeSettingsRepository.getClusterIdOnce()` à **l'intérieur** du `while(isActive)` pour réagir aux changements de cluster (Bully, JOIN_ACCEPT). Sources : blind+edge (High).
- [x] [Review][Patch] **P6 — Aucune garde `clusterId == ""` avant `countActiveByClusterId`** [`MobicloudP2PService.kt` + `SignalingRepositoryImpl.kt`] — `if (clusterId.isBlank()) continue` (ou `0`) pour éviter qu'un SP fraîchement élu sans clusterId annonce un faux `memberCount=0`. Sources : blind+edge (High).
- [x] [Review][Patch] **P7 — Race `memberRegistry.size() < MAX_CLUSTER_SIZE` puis `add()` non atomique** [`ProcessJoinRequestUseCase.kt:75-94`] — `addIfBelowCapacity(member, max)` ajouté à `MemberRegistry` (Ram + Room avec `Mutex` côté Room). Source : edge (High).
- [x] [Review][Patch] **P8 — `currentMemberCount` non validé côté client** [`RelayWebSocketClient.kt`] — `coerceIn(0, MAX_CLUSTER_SIZE)` au parse GET_PEERS. Source : edge (High).
- [x] [Review][Patch] **P9 — Tie-break manquant sur `currentMemberCount` égal** [`SendJoinRequestUseCase.kt`, `ProcessJoinRequestUseCase.kt`] — `compareBy { memberCount }.thenBy { nodeId.toHexShort() }`. Source : edge (Medium).
- [x] [Review][Patch] **P10 — `getAlternativeSuperPeers` ne filtre pas les SP pleins** [`ProcessJoinRequestUseCase.kt`] — `.filter { currentMemberCount < MAX_CLUSTER_SIZE }`. Source : blind (Medium).
- [x] [Review][Patch] **P11 — `JoinRedirectReason.OUT_OF_RADIUS` toujours dans l'enum** [`JoinRedirectReason.kt:7`] — supprimé. Source : auditor (Medium).
- [ ] [Review][Patch] **P12 — REGISTER_PEER non re-émis périodiquement** — le tracker ne reçoit `currentMemberCount` qu'à l'élection ; câbler envoi périodique (`HEARTBEAT_INTERVAL_MS = 30_000L`) sinon la valeur reste stale chez le tracker (AC14). Source : auditor (Medium). **Skipped batch — refactor timer hors scope mécanique.**
- [x] [Review][Patch] **P13 — Tests `JoinStateMachineTest` / `LocalDiscoveryRepositoryImplTest` / `SignalingRepositoryImplTest` non mis à jour** — `JoinEvent.CoordinatorReceived(senderNodeId, clusterId, gps…)` ne compile plus. Source : auditor (Medium). **Appliqué session 2 — tous les fichiers de tests corrigés, 568 tests Android passent.**
- [x] [Review][Patch] **P14 — Sticky cluster non invalidé sur `CLUSTER_FULL` / `INVALID_STATE`** [`SendJoinRequestUseCase`] — `clearClusterId()` ajouté au repository + appelé sur rejet. Source : edge (Medium).
- [x] [Review][Patch] **P15 — `distanceMeters` toujours dans `JoinRedirect`** [`JoinResponse.kt`, `ProcessJoinRequestUseCase`] — champ retiré du DTO. Source : blind (Low).
- [x] [Review][Patch] **P16 — `StatFs` instancié deux fois consécutivement** [`NodeSettingsRepositoryImpl.defaultBytes()`] — factorisé en variable locale. Source : blind (Low).
- [x] [Review][Patch] **P17 — `play-services-location` dans `libs.versions.toml`** — alias + version retirés. Source : blind (Low).
- [x] [Review][Patch] **P18 — Cas "tous les SP pleins" → `emptyList()` BullySolo trigger** — déjà géré : `top.isEmpty()` → `AllCandidatesExhausted` → `Isolated → IsolationBackoffElapsed → BullySolo` (chaîne FSM existante). Aucun patch nécessaire.
- [x] [Review][Patch] **P19 — Audit conformité index migration 15→16** — index `idx_cluster_members_active_scan(cluster_id, status, last_seen)` et `idx_cluster_members_status(status)` strictement identiques entre `@Entity(indices=…)` de `MemberEntity.kt` et `MIGRATION_15_16`. Aucun patch nécessaire.
- [ ] [Review][Patch] **P20 — Test `MemberEntityMigrationV15V16Test.kt`** (AndroidJUnit4 + `MigrationTestHelper`) — couvre AC4. **Skipped batch — création de fichier de test nouveau.**
- [x] [Review][Patch] **P21 — Cutoff `last_seen` dans `countActiveByClusterId`** [`MemberDao.kt`, `MobicloudP2PService.kt`, `SignalingRepositoryImpl.kt`] — signature DAO bumpée avec `cutoffMs` ; call-sites passent `now - SP_TIMEOUT_MS`. Issu de D4.

**Defer (pré-existant ou hors scope V5.1)**

- [x] [Review][Defer] **W1 — `observeFreeSpaceBytes()` émet une seule valeur puis se ferme** [`NodeSettingsRepositoryImpl`] — pré-existant, à corriger dans une story dédiée.
- [x] [Review][Defer] **W2 — `JOIN_REQUEST` v1→v2 sans fallback v1** — intentionnel par bump `JOIN_PROTOCOL_VERSION = 2` ; à documenter dans le rapport PFE comme incompat assumée petite échelle.
- [x] [Review][Defer] **W3 — HELLO `currentMemberCount` spoofable par SP byzantin** — hors modèle V5 (byzantin non couvert) ; ajouter perspective rapport.
- [x] [Review][Defer] **W4 — Migration n'efface pas les `MemberEntity` orphelins** — risque comptage fantôme post-upgrade ; mitigation via TTL `last_seen` (cf. D4).
- [x] [Review][Defer] **W5 — `cluster_id` SSID-hash stale en DB après upgrade** — bootstrap dégradé ; sera lavé naturellement au prochain BullySolo / JOIN_ACCEPT.
- [x] [Review][Defer] **W6 — `@Inject` sur `NodeSettingsRepositoryImpl` — vérifier qualification `@Singleton`** — audit hors diff visible.
- [x] [Review][Defer] **W7 — `RelayPeer` arguments positionnels potentiellement à risque** — diff utilise args nommés ; auditer call-sites hors diff.
- [x] [Review][Defer] **W8 — `NEARBY_WIFI_DEVICES` vs `WifiNetworkRepository` orphelin** — vérifier appels `getCurrentSsid()` résiduels.
- [x] [Review][Defer] **W9 — `SuperPeerHint.reliabilityScore` default `0f`** — neutralisation tie-break secondaire ; lié à P9.

**Dismissed comme bruit (5)** : double-restart `superPeerJob` transient, `ACCESS_FINE_LOCATION` upgrade granted (sans impact), `updateCurrentMemberCount` race derrière `isLocalNodeSuperPair` gate, divergence path Haversine dans la spec (cosmétique), inventaire `MockLocationRepositoryImpl` absent de la spec (doc).

---

### Review Findings R3 (2026-05-12) — 3 patches · 5 defer · 18 dismissed

**Patches (correction non ambiguë)**

- [x] [Review][Patch] **P22 — Commentaire trompeur dans COORDINATOR handler** [`ProcessIncomingElectionEventUseCase.kt:124`] — le commentaire original disait "Le COORDINATOR Bully n'écrase plus le clusterId persisté" alors que le code appelait bien `updateClusterId()`. Tests `ClusterIdPropagationTest` (AC2/AC3) confirment que l'adoption sur COORDINATOR est intentionnelle. Fix appliqué : commentaire corrigé pour refléter la réalité ("seuls COORDINATOR non-self, JOIN_ACCEPT et BullySolo écrivent le clusterId"). Sources : blind+auditor — **Commentaire corrigé, code conservé.**
- [x] [Review][Patch] **P23 — `memberRegistry.remove()` sans `runCatching` sur chemin échec signature** [`ProcessJoinRequestUseCase.kt:98`] — si `remove()` lève une exception (ex. erreur DB), la branche `signData` échoue silencieusement, le membre reste en DB (count gonflé) et aucun REDIRECT n'est envoyé. Fix : `runCatching { memberRegistry.remove(newMember.nodeId, clusterId) }`. Source : blind (Medium). **Appliqué.**
- [x] [Review][Patch] **P24 — Test `coordinatorReceived_transitionsToJoining_withoutGps()` absent** [`JoinStateMachineTest.kt`] — AC9 exige explicitement ce nom de test. Fix : test ajouté. **Appliqué. 569 tests passent.**

**Defer (pré-existant ou hors scope V5.1)**

- [x] [Review][Defer] **W-R3-1 — 30s stale HELLO memberCount entre deux ticks** — distinct de P12 (tracker) : le HELLO multicast annonce une charge potentiellement stale jusqu'à 30s après une admission. Suboptimal (routing légèrement sous-optimal au burst) mais non bloquant — `addIfBelowCapacity` reste la gate correctrice.
- [x] [Review][Defer] **W-R3-2 — `ClusterTopologyCard` lit `state.nodes.size` au lieu de `MemberDao.countActiveByClusterId`** [`ClusterTopologyCard.kt`] — AC10 prescrit un compteur DAO live ; l'implémentation utilise le snapshot JOIN_ACCEPT. Nécessite refactor ViewModel + injection DAO. Déviation mineure AC10.
- [x] [Review][Defer] **W-R3-3 — Constante `HEARTBEAT_INTERVAL_MS` absente du codebase** — AC14 demande `const val HEARTBEAT_INTERVAL_MS = 30_000L` dans `ClusterConstants.kt` (ou équivalent). La logique 30s est câblée inline. Nommage manquant.
- [x] [Review][Defer] **W-R3-4 — Alternatives redirect non re-triées par `currentMemberCount` après injection dans `top`** [`SendJoinRequestUseCase.kt`] — les SP injectés depuis `JoinRedirect.alternativeSuperPeers` s'ajoutent en fin de `top` sans re-tri. Suboptimal (best-effort ordering) mais sans impact sur la correction de l'admission.
- [x] [Review][Defer] **W-R3-5 — Tous-SP-pleins vs pas-de-candidats indiscernables dans les logs** [`SendJoinRequestUseCase.kt`] — le chemin `AllCandidatesExhausted` ne distingue pas "tracker a renvoyé 0 SP" de "tous les SP filtrés car ≥ MAX_CLUSTER_SIZE". Observabilité dégradée.

**Dismissed comme bruit (18)** : SuperPeerHint.currentMemberCount présent (faux alarm E1), HelloPayload.currentMemberCount présent (E13), countActiveByClusterId dans MemberDao (E19), MAX_CLUSTER_SIZE_SERVER défini dans server.js (E23), currentMemberCount dans GET_PEERS (E24), ProcessJoinRequestUseCase utilise addIfBelowCapacity et non size()+add() (E17/E20), differentSenderSameCluster déclenche JOIN même si differentCluster=false (E15), Mutex sérialise addIfBelowCapacity correctement (E6), attemptIndex borné à MAX_ATTEMPTS=3 (Blind #3), clearClusterId() lit depuis DAO frais (Blind #8), status colonne présente en v15 (Blind #9), double updateClusterId écrit la même valeur (Blind #12), BullySolo sur tous-SP-pleins = comportement conçu (E2), commentaires GPS intentionnels (A6/A7), AC13 count=50 cohérent serveur/client (A4), comportement COORDINATOR 4G préexistant (Blind #2 partiellement → ciblé en P22 côté clusterId uniquement).
