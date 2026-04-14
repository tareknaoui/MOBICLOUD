# Story 1.4: Foreground Service Réseau & Permissions au Lancement

Status: ready-for-dev

## Story

As a utilisateur,
I want accorder les permissions réseau nécessaires en un seul flux au démarrage,
so that le service P2P de MobiCloud fonctionne en arrière-plan de façon continue sans être tué par l'OS.

## Acceptance Criteria

1. **Given** l'app est lancée pour la première fois
   **When** l'écran de démarrage s'affiche
   **Then** les permissions `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `INTERNET`, `ACCESS_NETWORK_STATE` sont demandées en un seul flux (via le manifeste Android — ces 4 permissions sont des **normal permissions** auto-accordées ; `POST_NOTIFICATIONS` sur Android 13+ est la seule runtime permission demandée, déjà initiée)

2. **And** si l'utilisateur accorde les permissions (callback `checkForPermissions`), un `Foreground Service` (`MobicloudP2PService`) est démarré avec une notification persistante discrète ("MobiCloud Node Active")

3. **And** le service acquiert un `MulticastLock` Wi-Fi pour empêcher l'OS de filtrer les paquets UDP multicast

4. **And** si le service est tué par l'OS, il redémarre automatiquement (`START_STICKY`)

5. **And** l'état du service est exposé via un `StateFlow<ServiceStatus>` observable depuis le Dashboard (composant `DashboardScreen`)

## Tasks / Subtasks

- [ ] Task 1: Créer le modèle `ServiceStatus` (Domain Layer) (AC: #5)
  - [ ] Créer `domain/models/ServiceStatus.kt` — sealed class ou enum : `STOPPED`, `STARTING`, `RUNNING`, `ERROR`
  - [ ] Aucun import Android dans ce fichier (POJO Kotlin pur — Clean Architecture)

- [ ] Task 2: Étendre `NetworkServiceController` avec `StateFlow<ServiceStatus>` (AC: #5)
  - [ ] Modifier `domain/repository/NetworkServiceController.kt` — ajouter `val serviceStatus: StateFlow<ServiceStatus>`
  - [ ] Respecter la Clean Architecture : `StateFlow` est Kotlin pur, acceptable dans le domain

- [ ] Task 3: Implémenter `StateFlow<ServiceStatus>` dans `NetworkServiceControllerImpl` (AC: #5)
  - [ ] Ajouter `private val _serviceStatus = MutableStateFlow(ServiceStatus.STOPPED)` dans `NetworkServiceControllerImpl`
  - [ ] Override `val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()`
  - [ ] Dans `startService()` : mettre à jour `_serviceStatus` à `STARTING` avant l'appel, puis `RUNNING` après succès, `ERROR` en cas d'exception
  - [ ] Dans `stopService()` : mettre à jour `_serviceStatus` à `STOPPED` après arrêt réussi

- [ ] Task 4: Déclencher le service depuis `MainActivity` au bon moment (AC: #2) ⚠️ CRITIQUE ANDROID 12+
  - [ ] Injecter `NetworkServiceController` dans `MainActivity` via `@Inject`
  - [ ] Dans le callback `checkForPermissions(permissions) { ... }` de `MainActivity.onCreate()`, appeler `networkServiceController.startService()`
  - [ ] ⚠️ NE PAS appeler `startService()` depuis un ViewModel ou une coroutine en arrière-plan : `ForegroundServiceStartNotAllowedException` sur Android 12+ (API 31+) — l'appel DOIT venir du contexte foreground de l'Activity
  - [ ] Logger l'échec en cas de `Result.failure` sans bloquer l'UI

- [ ] Task 5: Créer `DashboardViewModel` (AC: #5)
  - [ ] Créer `presentation/dashboard/DashboardViewModel.kt` avec `@HiltViewModel`
  - [ ] Injecter `NetworkServiceController` et exposer `val serviceStatus: StateFlow<ServiceStatus>`
  - [ ] Utiliser `stateInDelayed` ou `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceStatus.STOPPED)` pour la conversion Flow → StateFlow

- [ ] Task 6: Mettre à jour `DashboardScreen` pour observer le statut (AC: #5)
  - [ ] Modifier `presentation/dashboard/DashboardScreen.kt` pour accepter un `DashboardViewModel` (Hilt)
  - [ ] Collecter `serviceStatus` via `collectAsStateWithLifecycle()`
  - [ ] Afficher un indicateur visuel simple du statut service (ex: `Text("Service: RUNNING")` — composant minimaliste OLED)

- [ ] Task 7: Tests unitaires `NetworkServiceControllerImplTest` (AC: #2, #5)
  - [ ] Créer `data/network/service/NetworkServiceControllerImplTest.kt`
  - [ ] Tester la transition de statut `STOPPED → RUNNING` lors d'un `startService()` succès
  - [ ] Tester la transition `RUNNING → STOPPED` lors d'un `stopService()` succès
  - [ ] Tester la transition vers `ERROR` lors d'une `SecurityException`
  - [ ] Mocker `Context` avec Mockito ou MockK

## Dev Notes

> [!CAUTION] **DISASTER PREVENTION — LIRE AVANT TOUTE IMPLÉMENTATION :**
> 1. **`ForegroundServiceStartNotAllowedException` (Android 12+) :** `networkServiceController.startService()` DOIT être appelé depuis un contexte Activity foreground. Utiliser le callback `checkForPermissions { ... }` dans `MainActivity.onCreate()`. NE PAS déléguer cet appel à un ViewModel via `viewModelScope.launch {}`. [Source: deferred-work.md, F-7, `NetworkServiceControllerImpl.kt:17-19`]
> 2. **Infrastructure déjà implémentée — NE PAS réimplémenter :** `MobicloudP2PService`, `NetworkServiceController`, `NetworkServiceControllerImpl`, `NetworkModule` existent déjà. La story porte uniquement sur l'orchestration + le `StateFlow<ServiceStatus>` + le `DashboardViewModel`. Créer des doublons casserait Hilt.
> 3. **Normal Permissions (auto-accordées) :** `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `INTERNET`, `ACCESS_NETWORK_STATE` sont toutes des **normal permissions** Android. Elles sont auto-accordées à l'installation via le manifeste. Aucune `requestPermissions()` à appeler pour ces 4 permissions. Seule `POST_NOTIFICATIONS` (Android 13+) est une runtime permission — déjà gérée dans `MainActivity.permissions`.

### Infrastructure Existante (NE PAS Recréer)

| Fichier | Statut | Notes |
|---|---|---|
| `data/network/service/MobicloudP2PService.kt` | ✅ Existant complet | `START_STICKY`, `MulticastLock`, notification, tous les loops réseau |
| `domain/repository/NetworkServiceController.kt` | ✅ Existant — à modifier | Ajouter `val serviceStatus: StateFlow<ServiceStatus>` |
| `data/network/service/NetworkServiceControllerImpl.kt` | ✅ Existant — à modifier | Ajouter `MutableStateFlow` + transitions |
| `di/NetworkModule.kt` | ✅ Existant | `@Binds NetworkServiceController → NetworkServiceControllerImpl` — ne pas toucher |
| `AndroidManifest.xml` | ✅ Existant complet | Toutes permissions + service `foregroundServiceType="connectedDevice"` |
| `presentation/dashboard/DashboardScreen.kt` | ⚠️ Placeholder | À enrichir avec ViewModel + affichage statut |

### Fichiers Clés à Créer / Modifier

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   ├── models/
│   │   └── ServiceStatus.kt          ← NOUVEAU (sealed class / enum)
│   └── repository/
│       └── NetworkServiceController.kt  ← MODIFIER (ajouter StateFlow)
├── data/network/service/
│   └── NetworkServiceControllerImpl.kt  ← MODIFIER (MutableStateFlow + transitions)
├── presentation/dashboard/
│   ├── DashboardViewModel.kt           ← NOUVEAU (HiltViewModel)
│   └── DashboardScreen.kt              ← MODIFIER (collectAsStateWithLifecycle)
└── MainActivity.kt                      ← MODIFIER (injection + startService dans callback)

app/src/test/kotlin/com/mobicloud/
└── data/network/service/
    └── NetworkServiceControllerImplTest.kt  ← NOUVEAU (tests transitions StateFlow)
```

### Détail de l'Implémentation `ServiceStatus`

```kotlin
// domain/models/ServiceStatus.kt
enum class ServiceStatus {
    STOPPED,    // Service non démarré (état initial)
    STARTING,   // Appel startForegroundService() en cours
    RUNNING,    // Service actif, MulticastLock acquis
    ERROR       // Échec démarrage (ex: ForegroundServiceStartNotAllowedException)
}
```

### Détail de l'Implémentation `NetworkServiceControllerImpl`

```kotlin
class NetworkServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkServiceController {
    
    private val _serviceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    override val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()
    
    override fun startService(): Result<Unit> {
        _serviceStatus.value = ServiceStatus.STARTING
        val intent = Intent(context, MobicloudP2PService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _serviceStatus.value = ServiceStatus.RUNNING
            Result.success(Unit)
        } catch (e: Exception) {
            _serviceStatus.value = ServiceStatus.ERROR
            Result.failure(e)
        }
    }
    
    override fun stopService(): Result<Unit> { ... /* STOPPED */ }
}
```

### Détail de l'Appel depuis `MainActivity`

```kotlin
// Dans MainActivity — APRES injection Hilt :
@Inject lateinit var networkServiceController: NetworkServiceController

// Dans onCreate() :
checkForPermissions(permissions) {
    // ← Ce callback s'exécute depuis le foreground de l'Activity — safe Android 12+
    val result = networkServiceController.startService()
    result.onFailure { e ->
        Log.e("MainActivity", "[P2P-SERVICE] Échec démarrage service: ${e.message}", e)
    }
}
```

### Détail du `DashboardViewModel`

```kotlin
// presentation/dashboard/DashboardViewModel.kt
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val networkServiceController: NetworkServiceController
) : ViewModel() {
    
    val serviceStatus: StateFlow<ServiceStatus> = networkServiceController.serviceStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ServiceStatus.STOPPED)
}
```

### Règle `MobicloudP2PService` — Ne Pas Toucher

`MobicloudP2PService` est **complet et opérationnel**. Il gère déjà :
- `START_STICKY` dans `onStartCommand()` (AC4 ✅)
- `MulticastLock` acquisition dans `acquireMulticastLock()` (AC3 ✅)
- Notification persistante "MobiCloud Node Active" (AC2 ✅)
- Tous les loops P2P (broadcaster, receiver, eviction, stability, network monitoring)

**Aucune modification de `MobicloudP2PService` n'est requise pour cette story.**

### Patterns Architecture à Respecter

- **Clean Architecture :** `ServiceStatus` est un POJO Kotlin — zéro import Android dans le domaine. Le `StateFlow` Kotlin est acceptable dans le domain.
- **Error Handling :** Toutes les méthodes retournent `Result<T>`. Jamais d'exception non gérée. Logger les échecs avec `Log.e`.
- **Hilt DI :** Utiliser `@Inject constructor` pour tous les nouveaux composants. `@HiltViewModel` pour `DashboardViewModel`. Aucune instanciation manuelle.
- **OLED Dark :** `DashboardScreen` utilise `MaterialTheme.colorScheme` — fond `#000000` respecté automatiquement via le thème existant.
- **Thread Safety :** `MutableStateFlow` est thread-safe par design. Pas de `synchronized` supplémentaire requis.

### Contexte Intelligence — Stories Précédentes

- **Story 1.1 :** Infrastructure Clean Architecture Compose+Hilt+Room. `MainActivity` utilise `checkForPermissions(permissions)` (actuellement avec liste vide + callback vide). C'est ici que s'insère l'appel `startService()`.
- **Story 1.2 :** Navigation 3 onglets (Dashboard/Explorer/Settings). `DashboardScreen` est un placeholder `Box { Text("dashboard") }`. À enrichir avec ViewModel.
- **Story 1.3 :** `IdentityRepository.getIdentity()` appelé dans `MainActivityViewModel.init {}` — pattern à reproduire pour le service, mais dans l'Activity (foreground context obligatoire).
- **Commit cc92c98 (pivot Firebase) :** `MobicloudP2PService`, `NetworkServiceController`, `NetworkServiceControllerImpl` ont été créés dans ce commit. Cette implémentation est complète et ne doit pas être réécrite.

### Tests Unitaires — Guide

```kotlin
// NetworkServiceControllerImplTest.kt (JVM unit test, pas Robolectric)
// Mocker Context avec MockK :
val mockContext = mockk<Context>(relaxed = true)
val controller = NetworkServiceControllerImpl(mockContext)

// Test 1 : état initial STOPPED
assertEquals(ServiceStatus.STOPPED, controller.serviceStatus.value)

// Test 2 : startService() → RUNNING si pas d'exception
every { mockContext.startForegroundService(any()) } returns mockk()
controller.startService()
assertEquals(ServiceStatus.RUNNING, controller.serviceStatus.value)

// Test 3 : startService() → ERROR si SecurityException
every { mockContext.startForegroundService(any()) } throws SecurityException()
controller.startService()
assertEquals(ServiceStatus.ERROR, controller.serviceStatus.value)

// Test 4 : stopService() → STOPPED
controller.stopService()
assertEquals(ServiceStatus.STOPPED, controller.serviceStatus.value)
```

> **Note :** Tests Robolectric pour le lifecycle complet de `MulticastLock` restent différés (dépendances Robolectric non configurées dans le Gradle — voir deferred-work.md F-4).

### Références

- [Source: epics.md#Story 1.4] (Acceptance Criteria de référence)
- [Source: architecture.md#Directives Android, point 2] (Foreground Service + MulticastLock obligatoire pour `core/network`)
- [Source: architecture.md#Error Handling Patterns] (Result<T>, zéro exception silencieuse)
- [Source: deferred-work.md#F-7] (`ForegroundServiceStartNotAllowedException` Android 12+ — appel depuis foreground Activity uniquement)
- [Source: deferred-work.md#F-4 partiel] (Tests Robolectric différés)
- [Source: deferred-work.md, story 0-1] (liste permissions vide dans MainActivity à peupler)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
