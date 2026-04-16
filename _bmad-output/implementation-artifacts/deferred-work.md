# Deferred Work Log

## Deferred from: code review of 0-1-initialisation-du-starter-clean-architecture (2026-03-27)

- **Répertoires `domain/` et `data/` vides** — Pas de `.gitkeep` ou fichier placeholder. Les répertoires sont vides car le contenu sera livré par les stories 0-2 et suivantes. Pre-existing, non bloquant pour la story 0-1.
- **Build Gradle non vérifié** — `JAVA_HOME` était absent dans l'environnement CLI de l'agent lors de l'exécution de la story. L'AC "le projet compile sans erreur" n'a pas pu être validée automatiquement. À vérifier manuellement dans Android Studio ou via CI.

## Deferred from: code review of 0-1-initialisation-du-starter-clean-architecture — Passe 2 (2026-03-27)

- **`AppModule` : CoroutineScope sur `Dispatchers.Default`** — Pour les opérations I/O P2P lourdes (network, disk), le dispatcher devra être changé en `Dispatchers.IO`. À adresser dans la story 0-2 ou lors du premier module P2P. [di/AppModule.kt:19]
- **`topLevelDestinationsWithUnreadResources` hardcodé vide** — `MutableStateFlow(setOf())` avec commentaire `TODO: Requires Implementation`. Non bloquant pour story 0-1 (couche UI), mais à implémenter avant toute feature utilisant les notifications de ressources non-lues. [ui/JetpackAppState.kt:152-154]
- **Liste de permissions vide dans MainActivity** — `checkForPermissions(permissions)` appelé avec une liste vide. Le callback est silencieux. Toute story ajoutant des permissions devra explicitement peupler cette liste et configurer le callback. [MainActivity.kt:71]
- **`di/AppModule` sans binding `@ApplicationContext`** — Le module DI ne fournit pas de binding de Context applicatif. À compléter dans toute story nécessitant un accès au Context pour les modules réseau, base de données ou sécurité. [di/AppModule.kt]

## Deferred from: code review of 0-2-configuration-protobuf-serialisation (2026-03-28)

- **`FormatModule` dans `:app` au lieu de `:core:format`** — Isolation est software (package) mais pas Gradle. Un module `:core:format` dédié serait nécessaire pour partager la configuration Protobuf entre plusieurs modules Gradle. Décision architecturale à prendre avant l'Epic 1.
- **Cas type mismatch non testé** — Si un champ change de type (ex. `Int` → `Long`), kotlinx-serialization-protobuf lève une `SerializationException`. Ce cas n'est pas couvert par `ProtobufCompatibilityTest` ni documenté hors-scope. À adresser lors de l'introduction des CRDTs (Epic 2).
- **`kotlinx-serialization-protobuf` en `implementation` dans `:app`** — Si d'autres modules (`:core:network`, features) ont besoin de sérialiser en Protobuf, ils devront déclarer leur propre dépendance ou passer par `:app`, inversant le graphe de dépendances. À résoudre lors de la mise en place de la couche réseau (Epic 1).
- **Absence de test Hilt pour `FormatModule`** — Aucun `@HiltAndroidTest` ne valide le binding DI. À ajouter lors de la mise en place de tests d'intégration.

## Deferred from: code review of 0-4-hotfix-compilation-ui-boilerplate (2026-03-28)

- **Incohérence package `JetpackAppState`** — `JetpackAppState` vit dans `com.mobicloud.ui` mais est consommé par `com.mobicloud.compose.ui` et `com.mobicloud.compose.navigation`. Couplage fragile entre modules, à refactorer lors d'une consolidation de la couche UI.
- **`ExperimentalMaterial3WindowSizeClassApi` dans `MainActivity`** — API expérimentale qui peut changer sans préavis entre versions mineures de Material3. À surveiller lors des mises à jour de `androidxComposeBom`.

## Deferred from: code review of 1-1-generation-de-la-node-identity-androidkeystore (2026-03-30)

- **F-3 — Catch trop large dans `getIdentity()` et `generateIdentity()`** [`KeystoreSecurityRepositoryImpl.kt:40,66`] — Les `catch (e: Exception)` génériques empêchent les appelants de distinguer les types d'erreur (keystore manquant, corruption, erreur réseau). Problème de granularité d'erreurs, non bloquant pour la story. À raffiner lors de l'introduction de la couche de gestion d'erreurs des features.
- **F-8 — Keystore corrompu → état non récupérable automatiquement** [`KeystoreSecurityRepositoryImpl.kt:31-32`] — Si l'entrée keystore est corrompue (cast `as? KeyStore.PrivateKeyEntry` échoue), `getIdentity()` retourne une erreur mais ne tente pas de régénérer. Auto-régénération en cas de corruption est un feature complet à concevoir séparément dans une story de résilience.
- **F-9 — Pas de test round-trip Protobuf pour `NodeIdentity`** — `NodeIdentity` est annoté `@Serializable` mais aucun test ne valide la sérialisation/désérialisation Protobuf du champ `ByteArray`. Validation end-to-end prévue lors de l'implémentation du Gossip (Epic 1.4+).

## Deferred from: code review of 1-2-preuve-de-travail-hashcash-anti-sybil-authentification-fr-09-1b (2026-04-06)

- **F-10 — Aucune `verifySignature` exposée pour validation par les pairs** — `HashcashToken.signature` est produite mais aucune méthode de vérification cryptographique n'est exposée par le domaine. Les pairs ne peuvent pas valider l'authenticité du token. Intentionnellement différé à l'Epic 1.3 (couche Gossip).
- **F-11 — Valeur de difficulté 18 non benchmarkée pour ARM ~1s** — Le paramètre `difficultyBits = 18` est une estimation empirique non documentée. Des benchmarks sur low-end (Cortex-A53) et high-end ARM sont nécessaires pour calibrer la valeur définitive. À adresser lors des tests de réseau réels (Epic 1.3+).
- **F-12 — Désynchronisation clé publique/privée AndroidKeyStore vs fallback logiciel** [`KeystoreSecurityRepositoryImpl.kt`] — Aucune vérification de cohérence entre la clé publique retournée par `getIdentity()` et la clé privée utilisée dans `signData()` fallback. Cas de migration entre modes (e.g. reset keystore puis fallback) non géré. À adresser dans une story de résilience d'identité.
- **F-13 — Test annulation coopérative potentiellement non-déterministe** [`GenerateHashcashProofUseCaseTest.kt:85-100`] — Le test à `difficulty=60` peut théoriquement trouver un nonce valide avant d'atteindre le premier `ensureActive()` à l'itération 1000, ce qui rendrait le test passant pour la mauvaise raison. Risque très faible mais non nul. À renforcer avec un mock de `ensureActive` ou un dispatcher contrôlé.

## Deferred from: code review of 1-3-couche-reseau-foreground-service-multicastlock (2026-04-06)

- **F-7 — `ForegroundServiceStartNotAllowedException` sur Android 12+ depuis l'arrière-plan** [`NetworkServiceControllerImpl.kt:17-19`] — Depuis Android 12 (API 31), lancer `startForegroundService()` depuis un contexte d'application en arrière-plan lève une `ForegroundServiceStartNotAllowedException`. La logique d'appel est actuellement dans `startService()` sans contrainte de contexte. L'appelant (ViewModel ou Activity) doit garantir d'appeler `startService()` depuis le foreground applicatif. À documenter et enforcer dans la story 1.4 lors de l'intégration UI du nœud MobiCloud.
- **F-4 (partiel) — Tests MulticastLock lifecycle via Robolectric non implémentés** [`NetworkServiceControllerImplTest.kt`] — La spec exige des tests mockés (`WifiManager`, vérification de `release()`) via Robolectric. Les dépendances Robolectric ne sont pas encore configurées dans le build Gradle. À ajouter lors de la mise en place de la suite de tests unitaires complète (Epic 1 ou Epic 2).

## Deferred from: code review of 1-4-udp-heartbeat-et-registre-des-pairs (2026-04-07)

- **System clock jumps affect `PeerRegistryImpl` eviction** [`PeerRegistryImpl.kt:33`] — L'utilisation absolue de `System.currentTimeMillis()` rend l'éviction sensible aux sauts d'horloge du système (backward/forward jump). Problème minime sur réseaux synchronisés NTP, mais l'utilisation de `SystemClock.elapsedRealtime()` est recommandée plus tard.

## Deferred from: code review of 1-5-backoff-progressif-dannonce-anti-saturation-fr-01-5 (2026-04-08)

- **Overflow arithmétique `Double→Long` sur valeurs extrêmes de backoff** [`UdpHeartbeatBroadcaster.kt:79`] — `(currentIntervalMs * backoffFactor).roundToLong()` peut lever une `ArithmeticException` si `currentIntervalMs` est extrêmement grand et `backoffFactor` très élevé. Cas impossible en production avec `maxIntervalMs=32000L`, mais non couvert par un guard arithmétique explicite. À solidifier si les paramètres de backoff deviennent configurables dynamiquement.
- **Loop 5 crash silencieux sur exception `networkUtils.getCurrentState()`** [`MobicloudP2PService.kt:134`] — Si `NetworkUtils.getCurrentState()` lève une exception non capturée, `SupervisorJob` absorbe le crash de la coroutine Loop 5 sans notification. L'app perd silencieusement le monitoring réseau. À adresser lors d'une story de robustesse/observabilité du service P2P.

## Deferred from: code review of 2-1-modele-dht-partitionne-crdt-jetpack-room-fr-05-1 (2026-04-11)

- **BH-03 — `exportSchema = false` bloque les migrations Room futures** [`CatalogDatabase.kt:10`] — Décision technique consciente pour la phase de développement. À activer (`exportSchema = true`) et à configurer un dossier de schéma avant la première release publique pour permettre les migrations automatiques.
- **AA-05 — Absence de validation de format des hashes cryptographiques** [`CatalogEntryEntity.kt`, `CatalogEntry.kt`] — `fileHash` et `ownerPubKeyHash` sont des `String` sans contrainte de format. Rien n'empêche de stocker une valeur lisible (non cryptographique). À adresser dans une story de hardening sécurité (validation longueur fixe + caractères hex).
- **AA-06 — Aucune requête DAO de sélection par plage DHT** [`CatalogDao.kt`] — Aucune méthode `getEntriesInRange(start, end)` n'est disponible dans le DAO. Sera requis pour le Gossip épidémique de la Story 2.3. À planifier dans le sprint de l'Epic 2.

## Deferred from: code review de 1-1-initialisation-du-projet-fondation-clean-architecture (2026-04-13)

- **F-06 — Alias `firebase-database-ktx` trompeur dans `libs.versions.toml`** [`gradle/libs.versions.toml:189`] — L'alias conserve le suffixe `-ktx` mais l'artefact cible est maintenant `firebase-database` (non-KTX). Risque de confusion pour les stories 2.x utilisant Firebase. Renommer l'alias en `firebase-database` lors de la prochaine refacto du catalog.
- **F-08 — `MobicloudApplication.kt` hors File List du Dev Agent Record** — AC#3 (`@HiltAndroidApp`) assumé satisfait sans preuve dans les fichiers modifiés. À valider manuellement via Android Studio ou CI lors d'un prochain build check.
- **F-09 — `TimeoutCancellationException` pourrait masquer annulation coroutine** [`UdpHeartbeatBroadcaster.kt:87`] — `TimeoutCancellationException` extends `CancellationException`. Le catch ligne 87 cible `TimeoutCancellationException` spécifiquement mais si Kotlin change la hiérarchie, l'annulation pourrait être absorbée. À vérifier lors des tests de robustesse de l'Epic 2.
- **F-10 — `FirebaseDatabase.getInstance()` sans URL explicite** [`FirebaseModule.kt:19`] — Dépend entièrement de `google-services.json`. Aucun guard ni log d'initialisation. En cas de fichier absent (CI, test), les échecs seront silencieux. À adresser avec un check d'initialisation dans la story 2.2.

## Deferred from: code review de 1-2-theme-oled-dark-navigation-3-onglets (2026-04-14)

- **F-03 — `navigateToItemScreen()` dead code dans `JetpackAppState`** [`JetpackAppState.kt`] — Méthode vide conservée avec commentaire "Obsolete". La fonction FAB la référence toujours. À supprimer proprement lors d'une refacto de la couche UI ou avant la story 1.3.
- **F-04 — `LightDefaultColorScheme` code mort** [`Theme.kt`] — Le schéma de couleurs Light est maintenu mais le thème ne peut plus jamais l'activer (hardcodé sur Dark). À supprimer ou annoter `@Deprecated` lors d'un refactoring du module `core:ui`.
- **F-09 — `topLevelDestinationsWithUnreadResources` non connecté aux nouvelles destinations** [`JetpackAppState.kt`] — Flow hardcodé vide depuis la story 0-1. Les notification dots ne fonctionneront jamais pour DASHBOARD/EXPLORER/SETTINGS. À implémenter lors de l'Epic 2 (features réseau avec états observables).
- **F-10 — Pas de sauvegarde de backstack navigation** [`NavHost.kt`] — Le `NavHost` ne configure pas de restauration d'état entre recreations d'activité. Non critique pour des placeholders, mais à adresser avant l'Epic 2 quand l'état des écrans deviendra significatif.

## Deferred from: code review de 1-3-generation-persistance-de-lidentite-cryptographique-keystore (2026-04-14)

- **F-08 — Race condition sur `getIdentity()`** [`IdentityRepositoryImpl.kt:19`] — Double appel simultané au premier démarrage peut déclencher deux générations d'identité en parallèle. `OnConflictStrategy.REPLACE` garantit la cohérence finale mais la clé "perdante" est abandonnée. À mitiger avec un `Mutex` dans `IdentityRepositoryImpl` lors d'une story de robustesse ou d'un refactoring du cycle de vie.
- **F-09 — `NodeIdentity @Serializable` avec `ByteArray`** [`NodeIdentity.kt`] — `kotlinx.serialization` sérialise `ByteArray` en array d'entiers (et non en base64) en JSON, ce qui est non-standard pour l'interopérabilité P2P. À adresser lors de la story de sérialisation réseau (Epic 2.x) en définissant un serializer custom.
- **F-10 — `KeystoreManager` méthodes non-`suspend` sur I/O Keystore** [`KeystoreManager.kt`] — Les méthodes `getExistingIdentity()` et `generateIdentity()` sont actuellement bloquantes (non-suspend) alors qu'elles effectuent des opérations I/O sur le Keystore Android. La sécurité actuelle repose sur `withContext(Dispatchers.IO)` dans le repository. Refactoring vers `suspend fun` recommandé lors d'une époque de refactoring de la couche `core/security`.

## Deferred from: code review de 2-1-decouverte-locale-via-udp-multicast-heartbeat (2026-04-14)

- **Migration Room destructive version 1→2** — Intentionnel per spec Dev Notes (`fallbackToDestructiveMigration` déjà configuré). En production, toutes les données seront détruites à la mise à jour. À résoudre avant la première release en ajoutant une vraie migration.
- **`@ProtoNumber` absent sur `HeartbeatPayload`** — kotlinx.serialization Protobuf utilise l'ordre de déclaration des champs comme numéros. Stable dans le même codebase, mais risqué si le format évolue (ajout/suppression de champs). À documenter et à fixer si le protocole est amené à évoluer.
- **`publicKeyBytes` en clair dans SQLite** — La clé publique de chaque pair est stockée non chiffrée dans `peer_nodes`. Accessible en debug (ADB), risque de corrélation d'identités sur appareil rooté. Décision à prendre selon le modèle de menace de l'app.
- **Changements hors-scope (ServiceStatus, DashboardViewModel) sans tests** — `ServiceStatus`, `NetworkServiceControllerImpl.serviceStatus`, `DashboardViewModel` ajoutés dans cette story sans AC ni tests associés dans la story 2-1. À couvrir dans la story 2-4 (Dashboard UX).
- **Protocol multicast sans versioning** — Changement `224.0.0.1:50000 → 239.255.255.250:7777` sans version de protocole dans `HeartbeatPayload`. Incompatibilité silencieuse entre appareils sur différentes versions. À adresser avant déploiement multi-version.
- **Tests Broadcaster utilisent port 4545 ≠ 7777 production** — Les tests unitaires configurent le broadcaster avec le port 4545 (port de test), non 7777. Ne capture pas une régression de config DI. Hors scope mais à noter.
- **Race condition TCP readiness avant broadcast** — Il n'existe pas de mécanisme "ready" entre `tcpConnectionManager.startServer()` et le début du broadcast UDP. Un pair peut tenter un handshake TCP avant que le serveur accepte. Acceptable en phase dev, à adresser lors des tests réseau réels.
- **`DashboardScreen` affiche `ServiceStatus.name` brut** — `"Service: $serviceStatus"` utilise le nom Kotlin de l'enum directement dans l'UI, non localisé. À migrer vers une ressource `strings.xml` dans la story 2-4.
- **`CoroutineScope` non qualifié dans `providePeerRepository`** — Si AppModule fournit plusieurs `CoroutineScope`, Hilt choisit arbitrairement. Probablement correct si un seul scope existe. À vérifier lors d'un audit DI et ajouter un qualifier `@Named` si nécessaire.

## Deferred from: code review de 1-4-foreground-service-reseau-permissions-au-lancement (2026-04-14)

- **F-11 — RUNNING émis avant initialisation réelle du service** [`NetworkServiceControllerImpl.kt:30`] — `startForegroundService()` est asynchrone ; `_serviceStatus = RUNNING` est défini dès le retour de l'appel OS, pas après que le service ait terminé son `onCreate()`/`onStartCommand()`. Limitation architecturale d'Android (pas de callback de démarrage sans binding). Pattern conforme au spec. À réévaluer si un binding au service est introduit dans une story future.
- **F-12 — Tests JVM : branche API≥O (`startForegroundService`) jamais exercée** [`NetworkServiceControllerImplTest.kt`] — `Build.VERSION.SDK_INT = 0` en JVM test runner ; seule la branche `startService()` (API<O) est testée. Tests Robolectric différés (voir F-4). À couvrir quand Robolectric sera configuré.
- **F-13 — Pas d'état STOPPING dans l'enum ServiceStatus** [`ServiceStatus.kt`] — La machine d'état est asymétrique : STARTING existe pour le démarrage mais aucun STOPPING pour l'arrêt. Nécessite un `stopService()` asynchrone pour être utile. Hors scope story 1.4.
- **F-14 — Pas de re-sync d'état quand START_STICKY redémarre le service** [`NetworkServiceControllerImpl.kt`] — Si l'OS tue le service (le process App survit), `START_STICKY` le redémarre mais `_serviceStatus` reste à la valeur précédente (ex: RUNNING→STOPPED par OS→RUNNING par START_STICKY sans mise à jour). Nécessite un binding au service pour écouter les callbacks lifecycle. Hors scope story 1.4.

## Deferred from: code review de 2-2-signalisation-inter-reseaux-via-tracker-firebase (2026-04-15)

- **F07 — TTL dérive d'horloge inter-appareils** [`SignalingRepositoryImpl.kt:50,66`] — `System.currentTimeMillis()` utilisé côté client pour filtrer les entrées TTL 60s. Sur appareils sans NTP synchronisé, des nœuds actifs peuvent être filtrés prématurément ou des nœuds morts rester visibles. Limitation Firebase sans Cloud Functions, noté dans Dev Notes. À réévaluer si Firebase Functions est ajouté au scope.
- **F08 — Règles de sécurité Firebase pour le chemin `nodes/*` non vérifiées** — Le diff ne contient aucune règle `.read`/`.write` pour le nouveau chemin `nodes/`. Les règles existantes couvrent `active_nodes/*` (ancien chemin). À vérifier et créer dans la console Firebase avant toute mise en production.
- **F09 — Nœuds morts non purgés côté serveur Firebase en cas de crash** — `onDisconnect().removeValue()` couvre la déconnexion propre, mais un crash process ou perte réseau prolongée laisse les entrées indéfiniment dans Firebase (TTL client seul ne nettoie pas Firebase). Firebase Cloud Functions hors scope PFE.
- **F10 — `await()` Firebase SDK non coopératif avec l'annulation du CoroutineScope** [`MobicloudP2PService.kt:124-135`] — Les tâches Firebase `.await()` peuvent s'exécuter après `serviceScope.cancel()` car le SDK Firebase ne respecte pas le `CoroutineContext`. Impact limité (nœud enregistré puis nettoyé par TTL), mais annulation non garantie. Limitation inhérente au SDK Firebase Android.
- **F16 — `onDisconnect()` fenêtre nœud fantôme si `setValue()` échoue sur session précédente** [`SignalingRepositoryImpl.kt:40-41`] — Si `setValue()` échoue alors qu'un nœud stale d'une session précédente existe, `onDisconnect` courant ne couvre pas cet ancien nœud. Impact limité par le TTL 60s. Limitation inhérente au pattern onDisconnect Firebase.
- **F18 — Couverture tests insuffisante : cas `localNodeId == null`, déduplication TCP, décodage clé invalide non testés** — 4 tests écrits couvrent les cas nominaux spec. Les cas limites F01 (localNodeId null), F02 (race TCP), F06 (Base64 invalide) ne sont pas encore couverts par des tests unitaires. À compléter lors de la prochaine itération ou d'une story de hardening tests.

## Deferred from: code review de 2-3-calcul-du-score-de-fiabilite-local (2026-04-16)

- **Fallback `0.5f` non loggé si `registerReceiver` retourne null** [`ReliabilityScoreProviderImpl.kt`] — Si `registerReceiver(null, batteryFilter)` retourne null (certains ROMs OEM), la batterie est assumée à 50% sans log ni métrique. Amélioration d'observabilité mineure, non bloquant.
- **Sémantique uptime : `SystemClock.elapsedRealtime()` mesure l'uptime appareil, pas du service** [`ReliabilityScoreProviderImpl.kt`] — Comportement prescrit par la spec (normalisation 24h). Après reboot, le score uptime repart à 0, ce qui peut sembler contre-intuitif (nœud redémarré = peu fiable). À réévaluer si la sémantique du score évolue.
- **Précision Float insuffisante pour `elapsedRealtime()` au-delà de ~16 jours** [`ReliabilityScoreProviderImpl.kt`] — `uptimeMs.toFloat()` perd en précision (discretisation 8ms) mais `coerceIn` contient le dépassement à 1.0. Négligeable en pratique dans l'intervalle 0–24h.
- **Race TOCTOU sur `getIdentity()` dans `IdentityRepositoryImpl`** [`IdentityRepositoryImpl.kt`] — Pré-existant (déjà loggé en F-08 de la story 1-3). Deux appels simultanés peuvent déclencher deux insertions. Non introduit par cette story.
- **Loop 6 sans backoff sur échecs DB répétés** [`MobicloudP2PService.kt`] — En cas de panne Room persistante, Loop 6 retente toutes les 30s avec un `Log.w` à chaque fois. Pas de circuit-breaker. Amélioration future de résilience.
- **`StaticMockTrustScore(fixedScore)` accepte des valeurs hors `[0.0, 1.0]`** [`StaticMockTrustScore.kt`] — Pas de validation sur le constructeur. Le `CalculateReliabilityScoreUseCase` corrige via `coerceIn`, mais un appelant direct de `getScore()` recevrait une valeur hors contrat. Mock uniquement, impact nul en production.
