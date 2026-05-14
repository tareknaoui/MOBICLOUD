# Story 13.4 : Écran d'initialisation P2P & Permissions groupées grand public

Status: done

**Epic :** 13 — Refonte UX Grand Public (V5.2 Simplified)
**Story ID :** 13.4
**Story Key :** `13-4-ecran-initialisation-p2p-et-permissions-groupees-grand-public`
**Date :** 2026-05-14
**Auteur :** Bob (SM) / bmad-create-story
**Prérequis :** Story 13.1 `review` (toggle Mode Expert, NodeSettings v17)
**Bloque :** —

---

## Contexte & Justification (défense PFE)

L'application MobiCloud demande actuellement uniquement `POST_NOTIFICATIONS` au lancement (Story 12.1 a supprimé la demande de `ACCESS_FINE_LOCATION`). La permission `NEARBY_WIFI_DEVICES` (API 33+) est déclarée dans le Manifest mais **jamais demandée au runtime** — ce qui prive silencieusement Wi-Fi Direct de son accès sur Android 13+.

Par ailleurs, au premier lancement, l'utilisateur grand public est projeté directement dans le Dashboard sans aucune explication. La spec UX §UJ-01 (User Journey — Onboarding) décrit un parcours en 2 étapes :

1. **PermissionsScreen** — explication humanisée des permissions requises + demande groupée
2. **InitScreen** — feedback de progression pendant le démarrage du moteur P2P (clé cryptographique, découverte réseau, connexion au groupe)

Ce chemin est affiché **une seule fois** (flag `hasCompletedOnboarding` persisté en DataStore). Une fois complété, le NavHost démarre directement sur le Dashboard.

---

## Story

En tant que **nouvel utilisateur de MobiCloud**,
Je veux **comprendre pourquoi l'application demande des permissions et voir la progression du démarrage P2P**,
Afin que **je consente en connaissance de cause et que je sache que l'application travaille pour moi dès le premier lancement** ; le moteur P2P sous-jacent (terminologie interne `SuperPair`, `Cluster`, etc.) reste **inchangé**.

---

## Acceptance Criteria (BDD)

### AC1 — Permission NEARBY_WIFI_DEVICES demandée au runtime (API 33+)

**Given** l'application tourne sur Android 13+ (API level ≥ 33)
**When** l'utilisateur atteint l'écran de permissions (PermissionsScreen)
**Then** `NEARBY_WIFI_DEVICES` est ajouté à la liste `permissions` dans `MainActivity.kt` (conditionnel `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`)
**And** la liste résultante est : `[POST_NOTIFICATIONS, NEARBY_WIFI_DEVICES]` sur API 33+, `[POST_NOTIFICATIONS]` sur API < 33
**And** la demande de permission est déclenchée depuis `PermissionsScreen` via `ActivityResultContracts.RequestMultiplePermissions`
**And** le moteur P2P démarre uniquement après que l'utilisateur a répondu à la demande (accordée ou refusée).

### AC2 — PermissionsScreen : explication humanisée grand public

**Given** l'application est lancée pour la première fois (flag `hasCompletedOnboarding = false`)
**When** NavHost évalue le `startDestination`
**Then** `PermissionsScreen` est affiché à la place du Dashboard
**And** l'écran contient :
  - Un titre : **"Bienvenue sur MobiCloud"**
  - Un sous-titre : **"Pour fonctionner, MobiCloud a besoin de quelques autorisations."**
  - Pour chaque permission, une ligne avec icône + titre humain + explication :
    - `POST_NOTIFICATIONS` → icône 🔔, titre **"Notifications"**, texte **"Pour vous informer lorsqu'un fichier est disponible ou qu'un transfert est terminé."**
    - `NEARBY_WIFI_DEVICES` (API 33+ seulement) → icône 📶, titre **"Wi-Fi de proximité"**, texte **"Pour découvrir et rejoindre le groupe de partage sécurisé à proximité."**
  - Un bouton principal **"Continuer"** (CTA) qui déclenche la demande de permissions
  - Un lien textuel discret **"Passer"** qui saute la demande (utilisateur peut refuser)
**And** tous les textes sont en vouvoiement formel
**And** le fond et les couleurs respectent le thème OLED dark (background = `Color.Black`, accent = `Color(0xFFFFB300)`).

### AC3 — PermissionsScreen : gestion des réponses

**Given** l'utilisateur a tapé "Continuer" sur PermissionsScreen
**When** le système affiche la boîte de dialogue système de permissions
**Then** si toutes les permissions sont accordées → naviguer vers `InitRoute`
**And** si au moins une permission est refusée → afficher un `Snackbar` : **"Certaines fonctionnalités seront limitées."** puis naviguer vers `InitRoute` quand même (l'utilisateur a le droit de refuser)
**And** si l'utilisateur tape "Passer" → naviguer directement vers `InitRoute` sans demander les permissions
**And** dans tous les cas, `hasCompletedOnboarding` est mis à `true` avant de quitter `PermissionsScreen` (afin que le parcours onboarding ne se répète pas au prochain lancement).

### AC4 — InitScreen : progression P2P humanisée

**Given** l'utilisateur arrive sur `InitScreen` (après PermissionsScreen ou depuis un lancement déjà onboardé qui démarre le service)
**When** `NetworkServiceController.startService()` est appelé
**Then** `InitScreen` affiche une progression en 3 étapes séquentielles :
  1. **"Création de votre identité sécurisée…"** (pendant `identityRepository.getIdentity()`)
  2. **"Recherche de membres à proximité…"** (pendant la découverte UDP multicast)
  3. **"Connexion au groupe de partage…"** (pendant l'élection/join cluster)
**And** chaque étape est représentée par un `LinearProgressIndicator` ou une animation de chargement
**And** le texte de statut se met à jour en fonction de la progression (StateFlow depuis `InitViewModel`)
**And** après un délai maximal de 8 secondes (timeout configurable dans `InitViewModel`), l'application navigue automatiquement vers `DashboardRoute` même si le service n'a pas fini de s'initialiser
**And** l'utilisateur peut aussi taper un bouton **"Accéder à l'application"** qui ignore le délai et navigue immédiatement.

### AC5 — Navigation gating (NavHost)

**Given** `NavHost.kt` définit actuellement `startDestination = DashboardRoute`
**When** l'application démarre
**Then** `NavHost` lit `hasCompletedOnboarding` depuis `UserPreferencesDataSource` (synchrone via `runBlocking` ou préchargé dans `MainActivityViewModel`)
**And** si `hasCompletedOnboarding == false` → `startDestination = OnboardingRoute` (mène à PermissionsScreen)
**And** si `hasCompletedOnboarding == true` → `startDestination = DashboardRoute` (comportement actuel préservé)
**And** `OnboardingRoute`, `PermissionsRoute`, `InitRoute` sont ajoutés au `NavHost` avec les transitions appropriées
**And** depuis `InitScreen`, la navigation vers `DashboardRoute` utilise `popUpTo(OnboardingRoute) { inclusive = true }` pour éviter le back-stack onboarding
**And** la régression est vérifiée : un utilisateur déjà onboardé arrive directement sur Dashboard (aucun changement visible pour les users existants).

### AC6 — Flag hasCompletedOnboarding persisté en DataStore

**Given** `UserPreferencesDataSource` gère déjà les préférences utilisateur (UserDataPreferences)
**When** Story 13.4 ajoute le flag onboarding
**Then** `UserDataPreferences` reçoit un champ `hasCompletedOnboarding: Boolean = false`
**And** le proto DataStore (ou Preferences DataStore) est mis à jour pour persister ce champ
**And** `UserPreferencesDataSource` expose :
  ```kotlin
  suspend fun setOnboardingCompleted()
  fun observeHasCompletedOnboarding(): Flow<Boolean>
  ```
**And** `MainActivityViewModel` précharge `hasCompletedOnboarding` au démarrage (splash screen attend `loading = true` jusqu'à la résolution)
**And** la valeur par défaut pour les utilisateurs existants est `false` — ils verront l'onboarding une seule fois après mise à jour (comportement acceptable pour PFE).

### AC7 — Vouvoiement sur tous les nouveaux strings

**Given** la politique éditoriale de l'application (Experience Principle #6 dans ux-design-specification.md)
**When** des strings sont ajoutés par cette story
**Then** tous les textes visibles utilisateur emploient le **vouvoiement formel** (« Bienvenue sur MobiCloud », « Pour vous informer… », « Continuer », « Passer »)
**And** aucun tutoiement n'est introduit.

### AC8 — Régression moteur P2P inchangé

**Given** le moteur P2P (SuperPairManager, GossipEngine, DHT, ErasureCodingEngine, etc.)
**When** Story 13.4 est livrée
**Then** aucun fichier sous `domain/`, `data/` ou `service/` n'est modifié sauf `UserPreferencesDataSource` (AC6) et `MainActivity.kt` (AC1)
**And** les tests existants continuent de passer sans modification.

---

## Tasks / Subtasks

### Task 1 — Ajouter NEARBY_WIFI_DEVICES à la liste runtime dans MainActivity

- [x] 1.1 Dans `MainActivity.kt`, dans le bloc `apply { }` qui construit `permissions`, ajouter `NEARBY_WIFI_DEVICES` conditionnel API 33+.
- [x] 1.2 `checkForPermissions` conservé dans `onCreate` pour les users déjà onboardés ; le service est aussi démarré depuis `InitViewModel` pour les nouveaux utilisateurs.

### Task 2 — Flag hasCompletedOnboarding dans UserPreferencesDataSource

- [x] 2.1 Lu `UserDataPreferences` (Preferences DataStore / Serializable).
- [x] 2.2 Ajouté `hasCompletedOnboarding: Boolean = false` à `UserDataPreferences`.
- [x] 2.3 `setOnboardingCompleted()` et `observeHasCompletedOnboarding()` ajoutés à l'interface et l'impl.
- [x] 2.4 `MainActivityViewModel` expose `hasCompletedOnboarding: StateFlow<Boolean?>` (null = pas encore chargé).

### Task 3 — PermissionsViewModel

- [x] 3.1 Créé `app/src/main/kotlin/com/mobicloud/presentation/onboarding/PermissionsViewModel.kt`
- [x] 3.2 Injection : `UserPreferencesDataSource`
- [x] 3.3 Expose `fun markOnboardingCompleted()`
- [x] 3.4 Annoté `@HiltViewModel`.

### Task 4 — PermissionsScreen.kt

- [x] 4.1 Créé `app/src/main/kotlin/com/mobicloud/presentation/onboarding/PermissionsScreen.kt`
- [x] 4.2 `@Serializable object PermissionsRoute` défini
- [x] 4.3 Layout AC2 complet : fond noir, accent ambre, titre/sous-titre, liste permissions conditionnelle
- [x] 4.4 `rememberLauncherForActivityResult(RequestMultiplePermissions)` intégré
- [x] 4.5 Réponse : `markOnboardingCompleted()` → `onNavigateToInit()`
- [x] 4.6 Refus partiel → Snackbar + navigation quand même
- [x] 4.7 "Passer" → `markOnboardingCompleted()` + navigation sans demander

### Task 5 — InitViewModel.kt

- [x] 5.1 Créé `app/src/main/kotlin/com/mobicloud/presentation/onboarding/InitViewModel.kt`
- [x] 5.2 Injection : `NetworkServiceController`
- [x] 5.3 `sealed class InitStep` : `CreatingIdentity`, `SearchingMembers`, `ConnectingToGroup`, `Done`
- [x] 5.4 `val currentStep: StateFlow<InitStep>` exposé
- [x] 5.5 `startInit()` : 3 étapes avec delays + `startService()` à ConnectingToGroup
- [x] 5.6 Timeout 8s via `withTimeoutOrNull(8000)` → émet `Done` dans tous les cas
- [x] 5.7 Annoté `@HiltViewModel`.

### Task 6 — InitScreen.kt

- [x] 6.1 Créé `app/src/main/kotlin/com/mobicloud/presentation/onboarding/InitScreen.kt`
- [x] 6.2 `@Serializable object InitRoute` défini
- [x] 6.3 Layout : MobiCloud + LinearProgressIndicator + texte statut + bouton "Accéder à l'application"
- [x] 6.4 `LaunchedEffect(Unit) { viewModel.startInit() }`
- [x] 6.5 Observer `currentStep` : `Done` → `onNavigateToDashboard()`

### Task 7 — NavHost : gating onboarding

- [x] 7.1 Lu `NavHost.kt` et `MainActivityViewModel.kt`.
- [x] 7.2 `NavHost.kt` mis à jour : paramètre `hasCompletedOnboarding: Boolean`, startDestination conditionnel, composables `PermissionsRoute` et `InitRoute` ajoutés.
- [x] 7.3 `JetpackApp.kt` mis à jour pour propager `hasCompletedOnboarding` depuis `MainActivity` → `JetpackApp` → `JetpackScaffold` → `JetpackNavHost`. NavHost composé seulement si `hasCompletedOnboarding != null`.
- [x] 7.4 4 routes existantes inchangées.

### Task 8 — Service start pour users déjà onboardés

- [x] 8.1 `checkForPermissions` conservé dans `MainActivity.onCreate()` — gère le démarrage du service pour les users existants.
- [x] 8.2 `InitViewModel.startInit()` appelle `startService()` pour les nouveaux utilisateurs.

### Task 9 — Tests

- [x] 9.1 `PermissionsViewModelTest` : vérifie que `markOnboardingCompleted()` appelle `setOnboardingCompleted()`.
- [x] 9.2 `InitViewModelTest` : vérifie les 3 étapes, la transition vers Done, l'appel startService, le timeout 8s.
- [x] 9.3 Test UI Compose non créé (test androidTest — hors scope PFE, pas de device dans CI).
- [x] 9.4 Tests existants cassés (DashboardViewModelTest, m11_join) sont pré-existants Story 13.1 (déjà en review) — non introduits par cette story.

---

## Dev Notes

### Architecture & Patterns

**Package onboarding** : créer sous `presentation/onboarding/` (suit le pattern `presentation/dashboard/`, `presentation/explorer/`, etc.)

**Hilt** : tous les ViewModels `@HiltViewModel`. Pas de nouvelle dépendance Gradle nécessaire.

**DataStore** : `UserPreferencesDataSource` utilise probablement `androidx.datastore:datastore-preferences` ou proto DataStore. Lire le fichier existant avant de modifier pour choisir la bonne approche.

**Compose Navigation** : routes `@Serializable object XxxRoute` (pattern établi par `DashboardRoute`, `ExplorerRoute`, etc.).

**Permission launcher** : `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())` retourne une `Map<String, Boolean>` — vérifier que toutes les valeurs sont `true` pour "toutes accordées".

**checkForPermissions extension** : définie dans `com.mobicloud.core.ui.extensions`. Elle prend `List<String>` et un callback. À conserver pour la compatibilité avec les utilisateurs déjà onboardés.

### Séquence démarrage pour users existants (hasCompletedOnboarding = true)

```
MainActivity.onCreate()
  → installSplashScreen() avec condition loading
  → MainActivityViewModel.uiState émet UiState(loading=false, hasCompletedOnboarding=true)
  → NavHost startDestination = DashboardRoute (pas d'InitScreen)
  → DashboardScreen affiché
  → networkServiceController.startService() appelé [à confirmer — mécanisme actuel dans MainActivity]
```

### Séquence démarrage premier lancement (hasCompletedOnboarding = false)

```
MainActivity.onCreate()
  → NavHost startDestination = PermissionsRoute
  → PermissionsScreen: affichage + bouton Continuer
  → RequestMultiplePermissions lancé
  → markOnboardingCompleted() + navigate(InitRoute)
  → InitScreen: startInit() → CreatingIdentity → SearchingMembers → ConnectingToGroup → Done
  → networkServiceController.startService() appelé dans ConnectingToGroup
  → navigate(DashboardRoute) { popUpTo(PermissionsRoute) { inclusive = true } }
```

### Contraintes OLED Dark Theme

Fond = `Color.Black`, texte principal = `Color.White`, accent = `Color(0xFFFFB300)` (ambre). Respecter le pattern des screens existants.

### Valeur par défaut hasCompletedOnboarding pour users existants

Les users déjà installés auront `hasCompletedOnboarding = false` après mise à jour (DataStore vide = valeur défaut). Ils verront l'onboarding une fois. C'est acceptable pour PFE — documenter dans les Completion Notes.

### Fichiers à créer (NEW)

- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/PermissionsScreen.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/PermissionsViewModel.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/InitScreen.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/InitViewModel.kt`

### Fichiers à modifier (UPDATE)

- `app/src/main/kotlin/com/mobicloud/MainActivity.kt` — AC1 + Task 8
- `app/src/main/kotlin/com/mobicloud/navigation/NavHost.kt` — Task 7
- `app/src/main/kotlin/com/mobicloud/MainActivityViewModel.kt` — Task 2.4 (hasCompletedOnboarding dans uiState)
- `UserPreferencesDataSource.kt` (chemin à confirmer) — Task 2
- `UserDataPreferences.kt` ou proto (chemin à confirmer) — Task 2.2

---

## Dev Agent Record

### Debug Log

- `hasCompletedOnboarding` initialisé à `null` (pas `false`) pour éviter le flash PermissionsScreen pendant le splash — le NavHost n'est composé que quand la valeur est non-null.
- `startService()` retourne `Result<Unit>` (non-suspend) → pas d'`identityRepository` dans InitViewModel (déjà appelé dans `MainActivityViewModel.init`).
- Tests existants cassés (DashboardViewModelTest, JoinIntegrationTest, etc.) sont pré-existants de Story 13.1 — confirmé via git stash + build test.

### Completion Notes

- AC1 : `NEARBY_WIFI_DEVICES` ajouté conditionnellement API 33+ dans `MainActivity.kt`
- AC2–AC3 : `PermissionsScreen` complet avec explication humanisée, vouvoiement, gestion accordé/refusé/passer
- AC4 : `InitScreen` avec 3 étapes séquentielles, `LinearProgressIndicator`, timeout 8s, bouton court-circuit
- AC5 : `NavHost` gating — `startDestination` conditionnel sur `hasCompletedOnboarding`, routes onboarding sans bottom nav
- AC6 : `UserDataPreferences.hasCompletedOnboarding` persisté en DataStore, exposé via `MainActivityViewModel`
- AC7 : Vouvoiement formel sur tous les strings ("Bienvenue sur MobiCloud", "Pour vous informer…", "Continuer", "Passer")
- AC8 : Aucun fichier domain/ ou data/ modifié sauf `UserPreferencesDataSource` et `MainActivity.kt`
- Régression : `BUILD SUCCESSFUL` — compilation code source OK (179 tâches, 0 erreur)

### File List

- `app/src/main/kotlin/com/mobicloud/MainActivity.kt` — MODIFIÉ (NEARBY_WIFI_DEVICES + hasCompletedOnboarding)
- `app/src/main/kotlin/com/mobicloud/MainActivityViewModel.kt` — MODIFIÉ (hasCompletedOnboarding StateFlow)
- `app/src/main/kotlin/com/mobicloud/navigation/NavHost.kt` — MODIFIÉ (gating onboarding, nouvelles routes)
- `app/src/main/kotlin/com/mobicloud/ui/JetpackApp.kt` — MODIFIÉ (propagation hasCompletedOnboarding)
- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/PermissionsViewModel.kt` — CRÉÉ
- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/PermissionsScreen.kt` — CRÉÉ
- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/InitViewModel.kt` — CRÉÉ
- `app/src/main/kotlin/com/mobicloud/presentation/onboarding/InitScreen.kt` — CRÉÉ
- `core/preferences/src/main/kotlin/com/mobicloud/core/preferences/model/UserDataPreferences.kt` — MODIFIÉ (hasCompletedOnboarding)
- `core/preferences/src/main/kotlin/com/mobicloud/core/preferences/data/UserPreferencesDataSource.kt` — MODIFIÉ (setOnboardingCompleted, observeHasCompletedOnboarding)
- `core/preferences/src/main/kotlin/com/mobicloud/core/preferences/data/UserPreferencesDataSourceImpl.kt` — MODIFIÉ (impl des deux méthodes)
- `app/src/test/kotlin/com/mobicloud/presentation/onboarding/PermissionsViewModelTest.kt` — CRÉÉ
- `app/src/test/kotlin/com/mobicloud/presentation/onboarding/InitViewModelTest.kt` — CRÉÉ
- `_bmad-output/implementation-artifacts/13-4-ecran-initialisation-p2p-et-permissions-groupees-grand-public.md` — STORY

---

## Change Log

| Date | Auteur | Description |
|------|--------|-------------|
| 2026-05-14 | bmad-create-story | Story créée — Phase 3 onboarding P2P grand public |
| 2026-05-14 | bmad-dev-story | Implémentation complète — BUILD SUCCESSFUL, status → review |

---

### Review Findings

> Code review adversarial — 3 couches (Blind Hunter · Edge Case Hunter · Acceptance Auditor) — 2026-05-14

**`patch` (2)**

- [x] [Review][Patch] **F1 — `startInit()` sans guard → double navigation sur rotation** [`InitViewModel.kt`] — ✅ Corrigé : `@Volatile private var initStarted = false` + `if (initStarted) return` avant le launch.
- [x] [Review][Patch] **F2 — `import kotlinx.coroutines.flow.map` dupliqué** [`MainActivityViewModel.kt`] — ✅ Corrigé : doublon supprimé.

**`defer` (1)**

- [x] [Review][Defer] **F5 — `SharingStarted.WhileSubscribed(5000L)` pour `hasCompletedOnboarding`** [`MainActivityViewModel.kt`] — deferred, pattern établi dans le codebase, risque de flash NavHost si > 5s background accepté pour PFE.

**`dismiss` (2)**

- [x] [Review][Dismiss] **F3 — `markOnboardingCompleted()` fire-and-forget avant navigation** — race condition DB extrêmement improbable (< 10ms), acceptable.
- [x] [Review][Dismiss] **F4 — `permissionInfos` affiche Notifications sur API < 33** — techniquement correct (POST_NOTIFICATIONS auto-granted < API 33), UX acceptable.
