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
