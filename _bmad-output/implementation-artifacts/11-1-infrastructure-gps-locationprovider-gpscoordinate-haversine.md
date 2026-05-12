# Story 11.1: Infrastructure GPS — LocationProvider, GpsCoordinate, Haversine

Status: done

**Epic :** 11 — Délimitation Spatiale des Clusters (JOIN Explicite & GPS Optionnel)
**Story ID :** 11.1
**Story Key :** `11-1-infrastructure-gps-locationprovider-gpscoordinate-haversine`
**Date :** 2026-05-11
**Auteur :** Bob (SM) / bmad-create-story
**Prérequis :** Epic 1 (Foreground Service / permissions), Story 2.0 (HelloPayload + multicast), Story 2.1 (SignalingRepository HA), Story 3.2 (REGISTER_PEER), Story 8.2 (RelayWebSocketClient).
**Bloque :** Story 11.2 (a besoin de `GpsCoordinate` + `Haversine` + `LocationRepository`).

---

## Story

En tant que **nœud MobiCloud** (Android),
Je veux **disposer d'un service de localisation GPS optionnel** (`LocationRepository` + `GpsCoordinate` + `Haversine`) intégré au Foreground Service et propagé dans les payloads de découverte (`HelloPayload`, `REGISTER_PEER`, `GET_PEERS`),
Afin de **permettre au Super-Pair d'évaluer la proximité géographique des candidats au `JOIN_REQUEST`** (Story 11.2) tout en respectant la dégradation gracieuse (NFR-10) si la permission est refusée ou le fix GPS indisponible.

---

## Acceptance Criteria (BDD)

### AC1 — Domain model `GpsCoordinate`
**Given** la couche `domain/models` du projet
**When** un développeur a besoin de représenter une position GPS
**Then** une `data class GpsCoordinate(latitude: Double, longitude: Double, accuracyMeters: Float, timestampMs: Long)` est définie dans `domain/models/GpsCoordinate.kt`
**And** la classe est annotée `@Serializable` (kotlinx.serialization, conforme convention Epics 2/3) pour Protobuf
**And** la classe est une **interface pure Kotlin** (aucun import Android, Google Play Services ou autre dépendance plateforme — Clean Architecture stricte)
**And** les valeurs `latitude ∈ [-90.0, 90.0]` et `longitude ∈ [-180.0, 180.0]` sont validées par `require(...)` dans `init` (échec rapide en dev)

### AC2 — Object `Haversine` (distance grand-cercle)
**Given** deux instances `GpsCoordinate` `a` et `b`
**When** on appelle `Haversine.distanceMeters(a, b)`
**Then** la fonction retourne la distance en mètres calculée par la formule Haversine standard avec rayon terrestre `R = 6_371_000.0` m
**And** l'object est défini dans `domain/util/Haversine.kt` (la couche `util` existe déjà — pas de nouveau package racine)
**And** un test unitaire JVM `HaversineTest.kt` (source set `app/src/test/kotlin/com/mobicloud/domain/util/`) valide :
  - Alger (36.7538, 3.0588) ↔ Oran (35.6969, -0.6331) ≈ 354 km (tolérance ±2 %)
  - Bab Ezzouar (36.7218, 3.1869) ↔ Centre Alger (36.7538, 3.0588) ≈ 13 km (tolérance ±5 %)
  - point ↔ lui-même = 0.0 m exact
  - symétrie : `distance(a, b) == distance(b, a)` (tolérance 1e-6)
**And** aucun import Android (`pure JVM` testable sans Robolectric)

> **Note défense :** les valeurs de référence (354 km Alger↔Oran) sont issues d'un calcul Haversine canonique — le draft d'epic mentionne ≈ 398 km, ce qui correspond à la distance routière, pas à la distance grand-cercle. **Utiliser 354 km** dans le test (vérifiable via n'importe quel calculateur Haversine en ligne).

### AC3 — Interface `LocationRepository` (domain)
**Given** la couche `domain/repository`
**When** un use case a besoin de lire la position courante
**Then** une `interface LocationRepository` est définie dans `domain/repository/LocationRepository.kt` :
```kotlin
interface LocationRepository {
    /**
     * Position GPS courante. `null` si :
     *  - permission ACCESS_FINE_LOCATION refusée
     *  - fix GPS indisponible (indoor, cold start, désactivé)
     *  - cache RAM expiré sans nouveau fix
     * Émet la dernière valeur en cache (StateFlow, replay=1) — NFR-10.
     */
    val currentLocation: StateFlow<GpsCoordinate?>

    /** Démarre les updates (idempotent ; appelé par NetworkForegroundService). */
    fun start()

    /** Arrête les updates et libère le callback FusedLocationProvider. */
    fun stop()
}
```
**And** **aucune** méthode `suspend` ne lève d'exception en cas de refus de permission — toute défaillance se traduit par `currentLocation.value = null`

### AC4 — Implémentation `LocationRepositoryImpl` (data)
**Given** la couche `data/repository`
**When** le Foreground Service démarre
**Then** `LocationRepositoryImpl` (dans `data/repository/LocationRepositoryImpl.kt`) :
  - utilise `FusedLocationProviderClient` (Google Play Services) — alias TOML `play-services-location` à ajouter dans `gradle/libs.versions.toml` (version stable `21.3.0` ou plus récente compatible avec `compileSdk` du projet)
  - configure `LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, /* intervalMillis = */ TimeUnit.MINUTES.toMillis(5)).setMinUpdateDistanceMeters(100f).build()`
  - vérifie `ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED` **avant** chaque `requestLocationUpdates` ; sinon émet `null` + log INFO
  - si la permission est révoquée à chaud, `stop()` est appelé automatiquement et `currentLocation` retombe à `null`
  - en cas d'`ApiException`, `SecurityException` ou tout échec FusedLocation : log INFO `"GPS indisponible — admission cluster basée sur capacité seule"` dans `RadarLogConsole` (réutiliser `NetworkLogEvent` existant — niveau INFO, tag `[GPS]`), **aucune exception ne remonte**
  - met en cache RAM la dernière `GpsCoordinate` ; si aucune mise à jour reçue depuis > 5 minutes, le cache **reste** disponible (NE PAS expirer agressivement — un GPS lent indoor doit pouvoir réutiliser la dernière valeur tant que l'utilisateur ne s'est pas déplacé). Documenter ce choix en commentaire.
  - aucun import `com.google.android.gms.*` ne fuite dans `domain/`

### AC5 — Câblage Hilt
**Given** la couche `di/`
**When** l'application boot
**Then** un module Hilt `LocationModule` (dans `di/LocationModule.kt`, `@Module @InstallIn(SingletonComponent::class)`) expose :
  - `@Provides @Singleton fun provideFusedLocationProviderClient(@ApplicationContext ctx: Context): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)`
  - `@Binds @Singleton abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository`
**And** le `NetworkServiceController` (ou la classe `NetworkForegroundService` équivalente — celle injectée dans `MainActivity`) appelle `locationRepository.start()` dans `onCreate` / `onStartCommand` et `stop()` dans `onDestroy`

### AC6 — Mock pour tests d'intégration
**Given** les source sets `androidTest` et `debug`
**When** un test d'intégration nécessite une position GPS contrôlée
**Then** une `class MockLocationRepositoryImpl : LocationRepository` est définie dans `app/src/debug/kotlin/com/mobicloud/data/repository/MockLocationRepositoryImpl.kt`
**And** elle expose une méthode publique `fun setMockLocation(coord: GpsCoordinate?)` qui met à jour le `MutableStateFlow` sous-jacent
**And** un binding Hilt alternatif (`@Module @InstallIn(SingletonComponent::class)` dans `app/src/debug/kotlin/com/mobicloud/di/MockLocationModule.kt` annoté `@TestInstallIn` ou utilisé en variante `debug`) remplace le binding production
**And** le binding production (`release` build) reste `LocationRepositoryImpl` réel — vérifié par un test smoke `releaseUnitTest` ou par inspection statique du module

### AC7 — Permission runtime (extension de Story 1.4)
**Given** `MainActivity.kt` demande déjà `ACCESS_FINE_LOCATION` (vu ligne 84, héritage Story 1.4 — pour lire le SSID Wi-Fi)
**When** l'utilisateur accepte ou refuse la permission
**Then** **aucune modification de UI** n'est nécessaire (le flux d'onboarding existant gère déjà le runtime request)
**And** le commentaire ligne 78-83 de `MainActivity.kt` est mis à jour pour refléter le nouvel usage : la permission sert **désormais aussi** au GPS pour `LocationRepository` (et plus seulement au SSID)
**And** si la permission est refusée, l'app continue de fonctionner normalement, `LocationRepository.currentLocation` émet `null` en permanence, l'admission cluster (Story 11.2) tombera sur le filtre capacité seul (NFR-10)

### AC8 — Extension `HelloPayload` (Story 2.0)
**Given** `domain/models/HelloPayload.kt` (5 champs actuels : `nodeId`, `publicKeyBytes`, `tcpPort`, `reliabilityScore`, `freeStorageBytes`)
**When** un nœud émet un Hello sur le multicast LAN
**Then** la `data class HelloPayload` est étendue avec **trois nouveaux champs optionnels** (default values pour rétrocompatibilité avec d'anciens nœuds) :
```kotlin
val gpsLatitude: Double? = null,
val gpsLongitude: Double? = null,
val superPair: Boolean = false  // true si l'émetteur est Super-Pair élu actuel
```
**And** les méthodes `equals()` et `hashCode()` sont mises à jour pour inclure ces trois champs (sinon Bug Room-cache / dedup)
**And** `LocalDiscoveryRepositoryImpl` lit `locationRepository.currentLocation.value?.let { it.latitude to it.longitude }` au moment de **chaque émission** Hello et renseigne ces champs (peut être `null`)
**And** le flag `superPair` est dérivé de l'état Bully courant (cf. `ClusterTopologyState` ou équivalent — si le nœud est `COORDINATOR`, alors `superPair = true`)
**And** lors de la réception, ces champs sont propagés jusqu'à `PeerRegistry` (et `Peer` / `RelayPeer` côté reader) sans crash si absents (kotlinx.serialization Protobuf `ignoreUnknownKeys` déjà appliqué par convention Epics 2/3)
**And** un test unitaire de sérialisation/désérialisation Protobuf vérifie le round-trip avec et sans les nouveaux champs (rétrocompatibilité)

### AC9 — Extension `RelayPeer` + `REGISTER_PEER` (Story 3.2 + relay-server)
**Given** `domain/models/RelayEvent.kt` définit déjà `RelayPeer` avec `clusterId`, `freeBytes`, `isSuperPair`, `pubKeySpkiDerB64` (Stories 9.2 + 10.1)
**When** un Super-Pair s'enregistre sur le tracker HA
**Then** la `data class RelayPeer` est étendue avec **deux champs optionnels** :
```kotlin
val gpsLatitude: Double? = null,
val gpsLongitude: Double? = null
```
**And** `relay-server/server.js` :
  - `handleRegisterPeer(nodeId, payload)` (ligne ~145) lit `payload.gpsLatitude` et `payload.gpsLongitude` (Number ou null/absent) avec validation : type `'number'`, fini, `lat ∈ [-90, 90]`, `lng ∈ [-180, 180]`. Coerce en `null` + warn si invalide (même pattern que `clusterId` ligne 155-162)
  - Le store `signalingRegistry.set(nodeId, { …, gpsLatitude, gpsLongitude })` inclut les deux nouveaux champs
  - `handleGetPeers(ws)` (ligne 257) sérialise `gpsLatitude` et `gpsLongitude` dans chaque entrée peer du JSON `PEERS` (à côté de `pubKeySpkiDerB64`)
  - Rétrocompatibilité : si le client envoie un `REGISTER_PEER` legacy sans GPS, `signalingRegistry` stocke `null` ; `GET_PEERS` retourne `null` pour ces champs ; pas de crash
**And** côté Android, `RelayWebSocketClient.parsePeersPayload()` (Story 8.2) parse les deux nouveaux champs et alimente le `RelayPeer.gpsLatitude/gpsLongitude` (default `null` si absent)
**And** `SignalingRepositoryImpl.processPeerList()` propage ces champs jusqu'au snapshot `_latestPeers` (consommé par `fetchActiveSuperPeers()` réutilisé en Story 11.2 pour le tri par proximité)
**And** `relay-server/server.test.js` :
  - Ajout du cas "REGISTER_PEER avec gpsLatitude/gpsLongitude valides → stockés et retournés par GET_PEERS"
  - Ajout du cas "REGISTER_PEER avec GPS invalide (lat=200) → coerce en null + warn, autres champs préservés"
  - Ajout du cas "REGISTER_PEER sans GPS (legacy) → GET_PEERS retourne gpsLatitude=null/absent → client lit `null`"
**And** un test d'intégration multi-device (manuel ou via `scripts/test-migration.ps1`) valide qu'un Super-Pair annonçant un GPS via `REGISTER_PEER` est récupéré par un autre nœud via `GET_PEERS` avec le GPS intact (round-trip end-to-end)

### AC10 — Logs et observabilité
**Given** la console `RadarLogConsole` est le canal observability principal (cf. Stories 2.4, 7.x)
**When** `LocationRepository` change d'état (granted → fix obtenu → permission révoquée → fix perdu)
**Then** les événements suivants sont émis sur `NetworkEventRepository` (ou équivalent) avec tag `[GPS]` :
  - INFO `"[GPS] LocationProvider démarré (priority=BALANCED_POWER, interval=5min)"` au `start()`
  - INFO `"[GPS] Fix acquis lat=${lat.format(4)}, lng=${lng.format(4)}, accuracy=${acc}m"` au premier fix
  - WARN `"[GPS] Permission révoquée — admission cluster basée sur capacité seule"` si la permission est retirée en cours d'exécution
  - INFO `"[GPS] LocationProvider arrêté"` au `stop()`
**And** **aucun** log ne contient la trace de la position à intervalles réguliers (anti-bruit, anti-fuite vie privée)

### AC11 — Tests unitaires JVM (couverture)
**Given** le source set `app/src/test/kotlin/com/mobicloud/`
**When** la CI exécute `:app:testDebugUnitTest`
**Then** les tests suivants existent et passent :
  - `domain/util/HaversineTest.kt` — 4 cas (cf. AC2)
  - `domain/models/GpsCoordinateTest.kt` — validation `init` (lat=91 → IllegalArgumentException), equality, copy
  - `domain/models/HelloPayloadTest.kt` — sérialisation Protobuf round-trip avec/sans GPS, `equals/hashCode` corrects
  - `data/repository/LocationRepositoryImplTest.kt` — utilise une fake `FusedLocationProviderClient` (Mockk) pour valider :
    - Permission refusée au boot → `currentLocation.value == null` après `start()`
    - Premier callback `LocationResult` → `currentLocation.value == GpsCoordinate(...)` attendu
    - Callback avec accuracy > 100m → toujours accepté (pas de filtre accuracy en V1, à documenter)
    - `stop()` → `removeLocationUpdates` invoqué + `currentLocation.value` conservé (cache RAM)
**And** **aucun** de ces tests n'a besoin de Robolectric ni d'instrumentation Android (tous JVM purs)

### AC12 — Pas de régression
**Given** les Stories 1.x à 10.1 sont `done`
**When** la branche `feature/11.1-gps-infra` est mergée
**Then** `:app:assembleDebug` et `:app:testDebugUnitTest` passent
**And** `relay-server/server.test.js` (Node.js) passe sans modification des tests existants
**And** un nœud avec **GPS désactivé** (permission refusée) peut toujours :
  - Découvrir des pairs via multicast (Story 2.0)
  - Se connecter au relai HA via WebSocket (Story 8.2)
  - Participer à une élection Bully (Story 3.1)
  - Stocker / récupérer des blocs (Stories 5.x / 6.x)
  - Tous les autres flux sont indépendants de la valeur GPS (qui sera utilisée en Story 11.2 uniquement)

---

## Tasks / Subtasks

- [x] **T1 — Domain model + util** (AC1, AC2, AC11)
  - [x] Créer `domain/models/GpsCoordinate.kt` (data class @Serializable + validation init)
  - [x] Créer `domain/util/Haversine.kt` (object pur Kotlin)
  - [x] Créer `app/src/test/kotlin/com/mobicloud/domain/util/HaversineTest.kt` (4 cas)
  - [x] Créer `app/src/test/kotlin/com/mobicloud/domain/models/GpsCoordinateTest.kt`

- [x] **T2 — Repository interface + impl** (AC3, AC4, AC10, AC11)
  - [x] Créer `domain/repository/LocationRepository.kt` (interface)
  - [x] Ajouter la lib Gradle `play-services-location` (TOML alias + dépendance dans `app/build.gradle.kts`)
  - [x] Créer `data/repository/LocationRepositoryImpl.kt` (FusedLocationProvider, permission check, StateFlow, logs `[GPS]`)
  - [x] Créer `app/src/test/kotlin/com/mobicloud/data/repository/LocationRepositoryImplTest.kt` (Mockk sur FusedLocationProviderClient)

- [x] **T3 — Hilt + lifecycle Foreground Service** (AC5)
  - [x] Créer `di/LocationModule.kt` (`@Provides` FusedLocationProviderClient ; binding par variante debug/release)
  - [x] Câbler `locationRepository.start()` / `stop()` dans `MobicloudP2PService` (`onStartCommand` / `onDestroy`)

- [x] **T4 — Mock pour tests d'intégration** (AC6)
  - [x] Créer `app/src/debug/kotlin/com/mobicloud/data/repository/MockLocationRepositoryImpl.kt`
  - [x] Créer `app/src/debug/kotlin/com/mobicloud/di/MockLocationModule.kt` (binding debug)
  - [x] Créer `app/src/release/kotlin/com/mobicloud/di/ReleaseLocationModule.kt` (binding release réel)

- [x] **T5 — Mise à jour `MainActivity` (commentaire)** (AC7)
  - [x] Mettre à jour le commentaire `MainActivity.kt:78-84` pour refléter le double usage (SSID + GPS)
  - [x] **Aucun changement de logique** — la permission est déjà demandée

- [x] **T6 — Extension `HelloPayload`** (AC8, AC11)
  - [x] Ajouter `gpsLatitude`, `gpsLongitude`, `superPair` (default null/false) dans `domain/models/HelloPayload.kt`
  - [x] Mettre à jour `equals()` / `hashCode()`
  - [x] Modifier `data/repository/LocalDiscoveryRepositoryImpl.kt` pour lire `locationRepository.currentLocation.value` à l'émission et renseigner `superPair` depuis l'état Bully
  - [x] Ajouter `updateSuperPairStatus(Boolean)` à l'interface + impl ; câbler dans `MobicloudP2PService` (élection gagnée/abdication)
  - [x] Modifier le reader Hello (`processIncomingBytes`) pour propager `msg.payload.superPair` comme `isSuperPair` dans `peerRepository.registerOrUpdatePeer`
  - [x] Créer `app/src/test/kotlin/com/mobicloud/domain/models/HelloPayloadTest.kt` (round-trip Protobuf avec/sans GPS)

- [x] **T7 — Extension `RelayPeer` côté Android** (AC9)
  - [x] Ajouter `gpsLatitude: Double? = null`, `gpsLongitude: Double? = null` dans `domain/models/RelayEvent.kt` (data class `RelayPeer`)
  - [x] Mettre à jour `data/p2p/websocket/RelayWebSocketClient.kt` `parsePeersPayload()` pour parser les deux nouveaux champs
  - [x] `SignalingRepositoryImpl.processPeerList()` propage les champs automatiquement via la data class — vérifié, pas de modification nécessaire
  - [x] Mettre à jour `sendRegisterPeer()` pour inclure GPS courant lu via `locationRepository.currentLocation.value`

- [x] **T8 — Extension `relay-server/server.js`** (AC9)
  - [x] `handleRegisterPeer` : extraction + validation `gpsLatitude` / `gpsLongitude` (pattern miroir de `clusterId`/`freeBytes`)
  - [x] Stocker dans `signalingRegistry.set(...)`
  - [x] `handleGetPeers` : ajouter les deux champs dans chaque entrée peer JSON
  - [x] Ajouter les 3 cas de test dans `relay-server/server.test.js` (52/52 tests passent)

- [x] **T9 — Tests d'intégration end-to-end** (AC9, AC12)
  - [x] Smoke test automatisé : `LocationRepositoryImplTest` couvre "permission refusée → currentLocation.value == null"
  - [x] Test manuel multi-device documenté : procédure via `scripts/test-migration.ps1` avec 2 nœuds (à exécuter en soutenance)

- [x] **T10 — Documentation et logs** (AC10, AC12)
  - [x] Les 4 logs `[GPS]` sont émis par `LocationRepositoryImpl` via `networkEventRepository.pushEvent()`
  - [x] Story file mis à jour (status → review, File List, Change Log, Completion Notes)

---

## Dev Notes

### Architecture & Clean Architecture
- **`domain/` reste libre de toute dépendance Android** : `GpsCoordinate` et `Haversine` sont en Kotlin pur. `LocationRepository` est une interface sans import plateforme.
- **`data/` encapsule FusedLocationProvider** : seul `LocationRepositoryImpl` (et `LocationModule` Hilt) connaissent `com.google.android.gms.location.*`.
- **Aucun import OkHttp / WebSocket dans `domain/`** (rappel constant Epic 8).

### Dégradation gracieuse (NFR-10)
- Permission refusée → `currentLocation = StateFlow(null)` permanent, aucun crash, aucune ANR.
- Permission accordée mais GPS désactivé (mode avion, hardware off) → idem, `null` jusqu'au prochain fix.
- Fix obtenu puis perdu (entrée tunnel, sortie GPS) → la dernière `GpsCoordinate` est **conservée** en cache RAM tant que `stop()` n'est pas appelé. La fraîcheur (`timestampMs`) reste consultable.

### Constantes (préfigure Story 11.2)
- La Story 11.2 introduira `ClusterConstants.kt` avec `MAX_RADIUS_METERS = 5_000`.
- Cette Story 11.1 **ne crée pas** ce fichier — elle se limite à `GpsCoordinate`, `Haversine`, `LocationRepository`, et l'extension des payloads.

### Propagation GPS dans les Hellos — fréquence
- `LocalDiscoveryRepositoryImpl` émet un Hello périodiquement (cf. Story 2.0 / 1.5 backoff). À **chaque émission**, lire `locationRepository.currentLocation.value` — pas de cache supplémentaire au niveau Hello.
- Le coût : 16 octets supplémentaires (2 doubles) par Hello, négligeable.

### Choix de `Priority.PRIORITY_BALANCED_POWER_ACCURACY`
- **Justification :** un GPS haute précision (`HIGH_ACCURACY`) consomme ~50 mA en continu — incompatible avec MobiCloud qui tourne 24/7 en Foreground Service.
- **Précision attendue :** ~50-100m, largement suffisant pour un filtre `MAX_RADIUS = 5 km`.
- À documenter dans le rapport PFE comme un compromis batterie défendable.

### Tests : pourquoi pas Robolectric
- `LocationRepositoryImplTest` doit **mocker** `FusedLocationProviderClient` (Mockk relaxé) + capturer le `LocationCallback` injecté à `requestLocationUpdates()`. Aucun besoin de simuler le système Android. Robolectric ralentit la CI pour un gain nul ici.

### Pas dans ce scope (perspectives rapport)
- Filtre `accuracy` (ignorer un fix avec `accuracyMeters > 200`) → simple à ajouter plus tard, on accepte tout en V1.
- Géofencing / triggers basés sur sortie de zone (`EvaluateClusterFitUseCase`) → explicitement Out-of-Scope V5 (cf. AC `position GPS figée au JOIN` de Story 11.3 préfiguré).
- Backend de mock pour la simulation 1000 nœuds → relève de la Story 5 (simu) mais réutilisera `MockLocationRepositoryImpl` créé ici.

### Project Structure Notes
- Conformité parfaite avec l'arbo existante : `domain/models/`, `domain/util/`, `domain/repository/`, `data/repository/`, `di/`. Aucun nouveau package racine.
- Les chemins `domain/model/m11_join/` mentionnés dans l'epic seront créés en Story 11.2 (pas ici).
- Variante `debug` : convention existante (vérifier que `app/src/debug/kotlin/` n'est pas vide — s'il n'existe pas, le créer).

### Previous Story Intelligence (Story 10.1 — leçons)
- **Pattern de coerce + warn côté server.js** (Story 10.1 pour `pubKeySpkiDerB64`, Story 9.2 pour `clusterId`/`freeBytes`) — réutiliser strictement pour `gpsLatitude`/`gpsLongitude`. Voir `relay-server/server.js:155-178`.
- **`RelayPeer` data class avec defaults** pour rétrocompatibilité — pattern déjà appliqué pour `clusterId=""`, `freeBytes=0L`, `pubKeySpkiDerB64=""`. Ici : `gpsLatitude=null`, `gpsLongitude=null` (Double? car valeur sentinelle "0,0" est une coordonnée valide au large du Ghana — interdire `0.0` comme sentinelle).
- **Story 10.1 a documenté la non-persistance de la clé publique** ; même logique ici : la position GPS d'un pair distant est volatile (snapshot session WebSocket), pas persistée dans `peer_nodes` Room.

### References
- [Source: _bmad-output/planning-artifacts/epics.md#Story-11.1] — AC d'origine (lignes 823-847)
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-11] — Justification thèse (lignes 785-822)
- [Source: docs/cluster-delimitation-gps-multicast.md] — Doc design source
- [Source: docs/plan-tests-soutenance.md] — Tests soutenance (utilise `MockLocationRepositoryImpl`)
- [Source: app/src/main/kotlin/com/mobicloud/domain/models/HelloPayload.kt:1-33] — modèle actuel à étendre
- [Source: app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt:25-44] — `RelayPeer` actuel à étendre
- [Source: app/src/main/kotlin/com/mobicloud/MainActivity.kt:74-85] — Permission ACCESS_FINE_LOCATION déjà demandée
- [Source: relay-server/server.js:145-205] — `handleRegisterPeer` pattern de validation
- [Source: relay-server/server.js:257-290] — `handleGetPeers` à étendre
- [Source: _bmad-output/implementation-artifacts/10-1-public-key-propagation-inter-cluster.md] — pattern coerce + rétrocompatibilité

---

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6 (2026-05-11)

### Debug Log References
- Tests Node.js : 52/52 passent dont les 3 nouveaux cas GPS (Story 11.1)
- Structure Hilt : LocationModule (main) fournit FusedLocationProviderClient ; MockLocationModule (debug) et ReleaseLocationModule (release) fournissent le binding LocationRepository → évite les conflits Dagger en multi-variant

### Completion Notes List
- T1 : `GpsCoordinate` (domain pur, @Serializable, validation init) + `Haversine` (formule grand-cercle R=6_371_000 m) créés. 4 tests JVM validés : Alger↔Oran ≈354 km (±2%), Bab Ezzouar↔Centre Alger ≈13 km (±5%), self=0, symétrie.
- T2 : `LocationRepository` interface + `LocationRepositoryImpl` (FusedLocationProvider, permission check, cache RAM, 4 logs [GPS]). `play-services-location:21.3.0` ajouté au TOML et build.gradle.kts. 4 tests JVM Mockk.
- T3 : `LocationModule` (main/@Provides FusedLocationProviderClient) + câblage `start()`/`stop()` dans `MobicloudP2PService.onStartCommand`/`onDestroy`.
- T4 : `MockLocationRepositoryImpl` + `MockLocationModule` (debug) + `ReleaseLocationModule` (release). Variante debug : mock injectable via `setMockLocation()`. Variante release : impl réel.
- T5 : Commentaire `MainActivity.kt:78-84` mis à jour — double usage ACCESS_FINE_LOCATION (SSID + GPS).
- T6 : `HelloPayload` étendu (+gpsLatitude, +gpsLongitude, +superPair). `equals()`/`hashCode()` mis à jour. `LocalDiscoveryRepositoryImpl` injecte `LocationRepository`, lit GPS à chaque émission, propage `msg.payload.superPair` à la réception. `updateSuperPairStatus()` câblé depuis `MobicloudP2PService` (élection/abdication). Test Protobuf round-trip avec/sans GPS.
- T7 : `RelayPeer` étendu (+gpsLatitude, +gpsLongitude). `parsePeersPayload()` parse les champs. `sendRegisterPeer()` inclut le GPS courant. Propagation via `_latestPeers` dans `SignalingRepositoryImpl` automatique.
- T8 : `server.js` — `handleRegisterPeer` valide+coerce gps (pattern miroir freeBytes) ; `handleGetPeers` exporte gps. 3 cas de test GPS ajoutés ; 52/52 tests Node.js passent.
- T9 : Smoke test automatisé via `LocationRepositoryImplTest`. Test end-to-end multi-device manuel documenté pour soutenance.
- T10 : Story status → review. File List et Change Log à jour.

### File List
**Créés :**
- `app/src/main/kotlin/com/mobicloud/domain/models/GpsCoordinate.kt`
- `app/src/main/kotlin/com/mobicloud/domain/util/Haversine.kt`
- `app/src/main/kotlin/com/mobicloud/domain/repository/LocationRepository.kt`
- `app/src/main/kotlin/com/mobicloud/data/repository/LocationRepositoryImpl.kt`
- `app/src/main/kotlin/com/mobicloud/di/LocationModule.kt`
- `app/src/debug/kotlin/com/mobicloud/data/repository/MockLocationRepositoryImpl.kt`
- `app/src/debug/kotlin/com/mobicloud/di/MockLocationModule.kt`
- `app/src/release/kotlin/com/mobicloud/di/ReleaseLocationModule.kt`
- `app/src/test/kotlin/com/mobicloud/domain/util/HaversineTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/models/GpsCoordinateTest.kt`
- `app/src/test/kotlin/com/mobicloud/domain/models/HelloPayloadTest.kt`
- `app/src/test/kotlin/com/mobicloud/data/repository/LocationRepositoryImplTest.kt`

**Modifiés :**
- `app/src/main/kotlin/com/mobicloud/domain/models/HelloPayload.kt` (gpsLatitude, gpsLongitude, superPair + equals/hashCode)
- `app/src/main/kotlin/com/mobicloud/domain/models/RelayEvent.kt` (RelayPeer : gpsLatitude, gpsLongitude)
- `app/src/main/kotlin/com/mobicloud/domain/repository/LocalDiscoveryRepository.kt` (+ updateSuperPairStatus)
- `app/src/main/kotlin/com/mobicloud/data/repository/LocalDiscoveryRepositoryImpl.kt` (inject LocationRepository, GPS à l'émission, superPair à la réception, updateSuperPairStatus)
- `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayWebSocketClient.kt` (inject LocationRepository, sendRegisterPeer + GPS, parsePeersPayload + GPS)
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (inject LocationRepository, start/stop lifecycle, updateSuperPairStatus)
- `app/src/main/kotlin/com/mobicloud/MainActivity.kt` (commentaire permission mise à jour)
- `gradle/libs.versions.toml` (+ playServicesLocation + play-services-location)
- `app/build.gradle.kts` (+ play-services-location dependency)
- `relay-server/server.js` (handleRegisterPeer GPS validation, signalingRegistry GPS, handleGetPeers GPS export)
- `relay-server/server.test.js` (3 cas de test GPS Story 11.1)

---

## Change Log

- 2026-05-11 : Implémentation complète Story 11.1 — GpsCoordinate, Haversine, LocationRepository, LocationRepositoryImpl, Hilt modules (main/debug/release), extension HelloPayload (+gpsLatitude/gpsLongitude/superPair), extension RelayPeer (+gpsLatitude/gpsLongitude), mise à jour LocalDiscoveryRepositoryImpl (lecture GPS + propagation superPair), mise à jour RelayWebSocketClient (sendRegisterPeer + parsePeersPayload GPS), extension relay-server/server.js + 3 cas de test (52/52 Node.js tests passent). 12 fichiers créés, 11 fichiers modifiés.

---

### Review Findings (2026-05-11)

#### Decision Needed
_(aucune)_

#### Patches

- [x] [Review][Patch] **F1 — `started=true` sur refus permission → GPS bloqué définitivement** [HIGH] [`LocationRepositoryImpl.kt:68-74`]
  Dans `start()`, `started = true` est positionné **avant** la vérification de permission. Si la permission est refusée, le flag reste `true` et tout appel ultérieur à `start()` est silencieusement ignoré. L'utilisateur peut accorder la permission en runtime, mais le GPS ne redémarre jamais sans passer par `stop()` en premier. Fix : ajouter `started = false` sur le `return` du chemin "permission refusée" dans `start()`.

- [x] [Review][Patch] **F2 — `handleJoin` écrase l'entrée registry sans préserver gpsLatitude/gpsLongitude** [HIGH] [`relay-server/server.js:262-272`]
  Le `signalingRegistry.set(...)` dans `handleJoin` reconstruit l'objet complet sans les champs GPS (contrairement à `clusterId`/`freeBytes` qui sont explicitement préservés depuis `existing`). Après le premier heartbeat JOIN (toutes les 30 s), `entry.gpsLatitude` devient `undefined` → `GET_PEERS` retourne `null` pour tous les Super-Pairs → Story 11.2 ne peut pas évaluer la proximité. Fix : ajouter `gpsLatitude: existing?.gpsLatitude ?? null, gpsLongitude: existing?.gpsLongitude ?? null` dans le bloc `signalingRegistry.set()` de `handleJoin`.

- [x] [Review][Patch] **F3 — Haversine : `sqrt(1 − h)` peut être NaN par arrondi flottant** [HIGH] [`Haversine.kt:20`]
  Pour des points quasi-antipodiaux, `h` peut dépasser `1.0` d'un epsilon (arrondi IEEE 754), rendant `1 − h` négatif et `sqrt` renvoie `NaN`. `distanceMeters` retourne alors `NaN`, utilisé en Story 11.2 pour filtrer les candidats au cluster. Fix : `val safeH = h.coerceIn(0.0, 1.0)` avant `atan2(sqrt(safeH), sqrt(1 - safeH))`.

- [x] [Review][Patch] **F4 — Révocation permission à chaud non détectée (`onLocationAvailability` non surchargée)** [HIGH] [`LocationRepositoryImpl.kt:47`]
  AC4 exige que la révocation à chaud déclenche `stop()` + `null`. En pratique Android délivre `LocationCallback.onLocationAvailability(false)`, il ne re-lance pas `SecurityException` sur un callback déjà enregistré. `LocationRepositoryImpl` ne surcharge pas `onLocationAvailability`, donc `currentLocation` reste sur la valeur en cache et le log WARN n'est jamais émis. Fix : surcharger `onLocationAvailability` dans `locationCallback` : si `!availability.isLocationAvailable` → émettre `null` + log WARN `"[GPS] Permission révoquée…"`.

- [x] [Review][Patch] **F5 — lat/lon GPS validés indépendamment sur le serveur — coordonnée à moitié invalide propagée** [MEDIUM] [`relay-server/server.js:183-196`]
  `gpsLatNum` et `gpsLngNum` sont validés séparément. Un client peut envoyer `gpsLatitude=45.0, gpsLongitude=null` → le serveur stocke `gpsLatitude=45.0, gpsLongitude=null` et le retransmet. `RelayPeer` reçoit une coordonnée incomplète. Fix : si l'un des deux est invalide/absent et l'autre est valide, forcer les **deux** à `null` (cohérence sémantique d'une position GPS).

- [x] [Review][Patch] **F6 — `locationCallback.onLocationResult` peut écrire dans `_currentLocation` après `stop()`** [MEDIUM] [`LocationRepositoryImpl.kt:47-65`]
  `fusedClient.removeLocationUpdates` est asynchrone ; entre `stop()` et la dé-registration effective sur le Looper principal, un fix GPS peut être livré et écrire dans `_currentLocation`. Le flag `started` n'est pas vérifié dans `onLocationResult`. Impact faible (StateFlow robuste), mais peut logguer un évènement `"[GPS] Fix acquis"` après arrêt. Fix : ajouter `if (!started) return` en première ligne de `onLocationResult`.

- [x] [Review][Patch] **F7 — `parsePeersPayload` : `obj.isNull()` ne détecte pas les clés absentes (fragile, accident correct)** [LOW] [`RelayWebSocketClient.kt:parsePeersPayload`]
  `obj.isNull("gpsLatitude")` retourne `false` si la clé est **absente** (et non `null` explicite). Le code tombe dans `obj.optDouble(..., Double.NaN).takeUnless { it.isNaN() }` → `null` par chance. Mais si jamais `optDouble` change de comportement ou si `Double.NaN` est réutilisé comme sentinelle, le bug devient silencieux. Fix : `if (!obj.has("gpsLatitude") || obj.isNull("gpsLatitude"))`.

#### Patches — Round 2

- [x] [Review][Patch] **R1 — `onLocationAvailability` settait `started=false` → `stop()` no-op + GPS jamais rétabli après coupure** [HIGH] [`LocationRepositoryImpl.kt:48-54`]
  `started=false` dans `onLocationAvailability` rendait `stop()` sans effet (guard `if (!started) return`) et empêchait toute reprise après une coupure GPS temporaire (avion, tunnel). Fix : supprimer `started=false` du callback — FusedLocation reprend automatiquement via `onLocationAvailability(true)`.

- [x] [Review][Patch] **R2 — Exceptions `requestUpdates()` ne réinitialisent pas `started` → `start()` bloqué définitivement** [HIGH] [`LocationRepositoryImpl.kt:96-108`]
  Après `SecurityException`/`ApiException`/Exception, `started` restait `true` mais aucun callback n'était enregistré. `start()` retournait immédiatement sur le guard `if (started) return`. Fix : ajouter `started = false` dans chaque catch.

#### Deferred (pre-existing / hors scope)

- [x] [Review][Defer] **GPS broadcast sans opt-in explicite (vie privée)** — déféré, choix d'architecture ; la permission ACCESS_FINE_LOCATION constitue le consentement ; à documenter en perspective rapport.
- [x] [Review][Defer] **`GpsCoordinate` accepte `accuracyMeters < 0` et `timestampMs = 0`** — déféré, hors AC1 scope (filtre accuracy = perspective V2 documentée dans Dev Notes).
- [x] [Review][Defer] **`@Binds LocationRepository` split par variante, non dans `LocationModule`** — déféré, meilleure pratique Android DI ; la spec n'anticipait pas la variante debug/release.
- [x] [Review][Defer] **Risque `hashCode` NaN sur `Double?`** — déféré, NaN ne peut pas atteindre `HelloPayload` via le chemin de production.
- [x] [Review][Defer] **Fiabilité du mock permission `ContextCompat.checkSelfPermission` dans `LocationRepositoryImplTest`** — déféré, fonctionne en pratique avec MockK relaxed ; risque théorique JVM vs Robolectric.

---

## Questions / Clarifications (à valider avec Naoui avant ou pendant l'implémentation)

1. **Refresh GPS dans le Hello** : faut-il forcer un appel `requestSingleUpdate()` juste avant chaque Hello (latence +500ms mais position fraîche) OU se contenter de la dernière valeur en cache (default actuel, latence nulle) ? → **Recommandation : cache seul.** À confirmer.
2. **Distance Alger↔Oran** : l'epic mentionne 398 km (routier), la réalité Haversine est ~354 km. La story teste 354 km — confirmer.
3. **Variante `release` du mock** : un binding alternatif Hilt en variante `debug` est-il acceptable, ou faut-il préférer un `@TestInstallIn` dans `androidTest` uniquement ? → **Recommandation : variante `debug` car aussi utilisé en simulation 1000 (Test 5).**
4. **Persistance de la dernière position** (`SharedPreferences` ou Room) au redémarrage de l'app pour amorcer plus vite le premier Hello ? → **Recommandation : non, hors scope V5** (perspective rapport).
