# Story 1.5: Backoff Progressif d'Annonce (Anti-Saturation) FR-01.5

**Story ID:** 1.5
**Story Key:** 1-5-backoff-progressif-dannonce-anti-saturation-fr-01-5
**Epic:** 1 (Identité & Découverte Réseau)
**Status:** done

## Story

**As a** Système MobiCloud,
**I want** d'implémenter un mécanisme de backoff progressif exponentiel lors de l'annonce initiale de ma présence sur le maillage local,
**so that** je n'inonde pas le réseau local de requêtes simultanées causant une tempête de Broadcasts (effet Flash Crowd).

## Acceptance Criteria

1. **Given** un appareil entrant fraîchement dans une zone à forte densité (plus de 100 appareils),
2. **When** le module de découverte est activé,
3. **Then** le délai entre deux annonces successives augmente progressivement (ex: 1s, 2s, 4s...) jusqu'à réception d'un écho ou stabilisation.

## Tasks / Subtasks

- [x] **Task 1: Implémenter l'algorithme de Backoff Exponentiel**
  - [x] Définir la logique d'incrémentation du délai (ex. `initial = 1s`, `factor = 2.0`, `maxDelay = 16s ou 32s`).
  - [x] Implémenter le calcul de ce délai au sein d'une boucle ou en utilisant les opérateurs Coroutine flow/retry.
- [x] **Task 2: Intégrer le Backoff pour l'annonce P2P (`UdpHeartbeatBroadcaster` ou `MobicloudP2PService`)**
  - [x] Adapter la boucle d'émission de datagrammes Multicast pour exploiter un `delay()` variable exponentiel.
  - [x] L'état de "stabilisation" doit stopper l'augmentation du délai et le figer au seuil normal pour un Heartbeat (tel que défini précédemment) ou le réduire selon la recette.
- [x] **Task 3: Gérer la réinitialisation du Backoff**
  - [x] Lors d'une déconnexion, un changement de réseau ou de la nécessité de prospecter activement des pairs, réinitialiser le backoff à sa valeur initiale (1s).
- [x] **Task 4: Tests Unitaires**
  - [x] S'assurer que le calcul du délai ne déborde pas la capacité (Overflow memory) et respecte le maxDelay.
  - [x] Tester que le délai se resette quand l'état du réseau ou les appels de méthode l'indiquent.

### Review Findings

- [x] [Review][Decision→Dismiss] Network Monitor reset au démarrage — comportement **intentionnel** : le burst initial est vital pour construire rapidement la liste de pairs au démarrage. Pas de `.drop(1)`. [MobicloudP2PService.kt:134]
- [x] [Review][Patch] `setStable(false)` ne déclenchait pas `resetTrigger` → délai parasite 0-2s avant reprise du backoff — **fixé** [UdpHeartbeatBroadcaster.kt:setStable]
- [x] [Review][Patch] `@Volatile` insuffisant — `AtomicBoolean` + `getAndSet()` pour atomicité — **fixé** [UdpHeartbeatBroadcaster.kt:isStable]
- [x] [Review][Patch] `Result.success(Unit)` code mort — commenté et documenté — **fixé** [UdpHeartbeatBroadcaster.kt:83]
- [x] [Review][Patch] `MutableSharedFlow(extraBufferCapacity=1)` pouvait perdre des signaux — augmenté à 64 — **fixé** [UdpHeartbeatBroadcaster.kt:32]
- [x] [Review][Patch] `initialIntervalMs=0L` boucle infinie CPU — `require(initialIntervalMs > 0L)` ajouté dans `init` — **fixé** [UdpHeartbeatBroadcaster.kt:init]
- [x] [Review][Patch] Test 3 `resetBackoff` commentaire incertain "Let's assume" — test réécrit avec `runCurrent()` explicite — **fixé** [UdpHeartbeatBroadcasterTest.kt]
- [x] [Review][Patch] `System.currentTimeMillis()` violation du guardrail spec — remplacé par `SystemClock.elapsedRealtime()` — **fixé** [MobicloudP2PService.kt:106,117]
- [x] [Review][Patch] `HEARTBEAT_INTERVAL_MS` constante morte — supprimée, commentaire explicatif ajouté — **fixé** [MobicloudP2PService.kt:companion]
- [x] [Review][Patch] Test overflow manquant Task 4 — 2 nouveaux tests ajoutés (cap extrême + constructeur) — **fixé** [UdpHeartbeatBroadcasterTest.kt]
- [x] [Review][Defer] Overflow arithmétique `Double→Long` sur valeurs extrêmes de backoff [UdpHeartbeatBroadcaster.kt:79] — deferred, pré-existant, plafond maxIntervalMs=32s rend le cas impossible en production
- [x] [Review][Defer] Loop 5 crash silencieux si `networkUtils.getCurrentState()` lève une exception — SupervisorJob absorbe sans notification [MobicloudP2PService.kt:134] — deferred, pré-existant, pattern NetworkUtils à solidifier dans une future story

## Developer Context & Guardrails

### 🏗 Architecture Compliance (CRITICAL)
- **Couche Network Isolée:** Maintenir les détails UDP à la couche Data `data/p2p`.
- **Non-Blocking I/O:** Le backoff dynamique doit impérativement exploiter `kotlinx.coroutines.delay` depuis une Coroutine hébergée sur `Dispatchers.IO` ou gérer l'état en Flow avec debouncing/backoff operators natifs.

### ⚙️ Technical Guidelines
> [!IMPORTANT]
> - **Gestion Fiable du Temps:** Utilisez `SystemClock.elapsedRealtime()` plutôt que `System.currentTimeMillis()` si vous documentez manuellement des timestamps pour les délais réseau, afin d'ignorer les désynchronisations d'horloge de l'utilisateur.
> - **Plafonnement Strict:** Le délai de Heartbeat ne doit pas monter à l'infini (ex: 1 heure!). Le plafond (maxDelay) doit être correctement calibré (ex: 30 secondes max entre deux annonces de présence si aucun pair n'est trouvé).
> - **Zero Exception Silencieuse:** Maintenir la politique `Result<T>` pour attraper et propager les erreurs d'I/O réseau sans faire crasher l'app.

### 📂 File Structure Requirements
Localisation probable des éditions de code :
- `app/src/main/kotlin/com/mobicloud/data/p2p/UdpHeartbeatBroadcaster.kt`
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
- `app/src/test/kotlin/com/mobicloud/data/p2p/UdpHeartbeatBroadcasterTest.kt`

### 🧪 Testing Requirements
- Vérifier que `delay()` est appelé les bonnes séquences de temps avec des coroutines tests (`runTest`, `advanceTimeBy`).

## Previous Story Intelligence
- **Review de Bug Identifié:** Lors de l'implémentation du 1.4, une boucle UDP `UdpHeartbeatBroadcaster` plantait de façon indésirable à la première exception `socket.send` au lieu de retry. Assurez-vous que l'ajout du backoff soit englobant autour de `try/catch` réseau sûr.
- Des anomalies avec `System.currentTimeMillis()` ont été ignorées dans le run précédent. Il sera plus sûr d'investiguer `SystemClock.elapsedRealtime()` pour éviter d'aggraver ces soucis de désynchronisation.

## Status Note
Ultimate context engine analysis completed - comprehensive developer guide created.

## Dev Agent Record
### Debug Log
- Utilisation d'un `MutableSharedFlow<Unit>` avec `withTimeout` pour permettre l'interruption immédiate du délai du backoff lorsqu'un `resetBackoff()` est invoqué.
- Utilisation de `networkUtils` injecté dans `MobicloudP2PService` pour tracker au niveau OS les états de perte / changement de réseau afin de forcer la réinitialisation du backoff.

### Completion Notes
- Implémentation du backoff progressif d'annonce avec support de la stabilisation quand un pair est détecté (le délai repasse au heartbeat normal de 2000ms au lieu du max de 32000ms).
- Tests exécutés et validés sans regression de la couche P2P.

## File List
- `app/src/main/kotlin/com/mobicloud/data/p2p/UdpHeartbeatBroadcaster.kt` (MODIFIED)
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt` (MODIFIED)
- `app/src/test/kotlin/com/mobicloud/data/p2p/UdpHeartbeatBroadcasterTest.kt` (MODIFIED)

## Change Log
- Ajout de la logique backoff exponentielle pour l'annonce UdpHeartbeatBroadcaster.
- Ajout de l'injection NetworkUtils pour prospecter automatiquement le réseau.
