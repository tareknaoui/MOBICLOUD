# Story 1.3: Couche Réseau (Foreground Service & MulticastLock)

**Story ID:** 1.3
**Story Key:** 1-3-couche-reseau-foreground-service-multicastlock
**Epic:** 1 (Identité & Découverte Réseau)
**Status:** done

## Story

**As a** Nœud du réseau,
**I want** d'activer un Foreground Service persistant avec verrouillage Multicast UDP,
**so that** mon téléphone reste connecté au réseau MobiCloud (Gossip/Heartbeat) même lorsque l'application passe en arrière-plan (Doze Mode).

## Acceptance Criteria

1. **Given** que l'utilisateur a autorisé les permissions réseau et batterie,
2. **When** l'application "Démarre le Noeud MobiCloud",
3. **Then** une notification persistante s'affiche et un `ForegroundService` maintient l'OS éveillé pour les sockets.
4. **And** le `WifiManager.MulticastLock` est acquis.

## Tasks / Subtasks

- [x] **Task 1 (AC: 1, 2, 3): Déclarer le Service et les Permissions Android 14+**
  - [x] Déclarer les permissions générales et Android 14+ dans `AndroidManifest.xml` (ex: `FOREGROUND_SERVICE`, et spécifiquement `FOREGROUND_SERVICE_CONNECTED_DEVICE` car c'est un réseau de type P2P).
  - [x] Ajouter la déclaration du `<service>` avec `android:foregroundServiceType="connectedDevice"` dans le manifest (Requis strict pour Android 14 API 34+).
  - [x] S'assurer que le workflow UI/Demande d'utilisation valide ou requiert ces permissions si besoin au runtime.
- [x] **Task 2 (AC: 3): Implémenter le `ForegroundService` persistant**
  - [x] Créer la classe de service P2P héritant de `Service` Android.
  - [x] Configurer un `NotificationChannel` (API 26+) et construire la notification persistante de l'état du nœud.
  - [x] Appeler `startForeground(ID, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)` afin que Android ne tue pas le service en Doze mode.
- [x] **Task 3 (AC: 4): Acquérir et gérer le `WifiManager.MulticastLock`**
  - [x] Instancier un `MulticastLock` depuis le `WifiManager` du système.
  - [x] Acquérir le lock `.acquire()` lorsque le service démarre ou est activé, ce qui permet à l'antenne radio Wi-Fi de recevoir les paquets UDP entrants (exigé pour le Heartbeat & Gossip).
  - [x] Libérer formellement le lock `.release()` au `onDestroy` du service pour éviter de causer un drain de la batterie excessif, ou via annulation coopérative.
- [x] **Task 4: Lier le Service via l'Architecture MVC/MVVM Hilt**
  - [x] Créer une interface côté module métier (`domain`) pour le démarrage et l'arrêt du service réseau (ex: `INetworkControllerService`).
  - [x] L'implémentation du service doit rester dans la couche dépendante du Framework Android (`data`, ou via Injection Hilt appropriée).
  - [x] Rédiger des Tests Unitaires (vérification du démarrage via Intent, etc.).

## Developer Context & Guardrails

### 🏗 Architecture Compliance (CRITICAL)
- **Couche Network Isolée:** Le `ForegroundService` Android est une dépendance externe liée spécifiquement au SDK Android. Ce code **DOIT** se situer dans le dossier parent "data" ou assimilé (ex: `app/src/main/kotlin/com/mobicloud/data/network/service/`).
- **Purity of Domain:** Le domain ne doit absolument pas référencer ou connaître l'existence de `.MulticastLock` ou du `ForegroundService` ou d'un `Intent`.
- **References:** [Source: `architecture.md`, Section "Continuité Physique & OS"]

### ⚙️ Technical Requirements & Guidelines
> [!IMPORTANT]
> - **Android 14+ Requirements:** Depuis Android API 34, vous DEVEZ utiliser un type explicite de service. Pour un réseau P2P, le type `connectedDevice` est requis avec la permission `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
> - **Battery Considerations:** Maintenez le MulticastLock *seulement* pendant l'existence du service P2P. N'oubliez sous aucun prétexte le `lock.release()` lors de l'arrêt, sinon cela videra la batterie de l'utilisateur (Memory/Battery Drain Alert).

### 📂 File Structure Requirements
Arborescence structurale typique demandée :
```text
app/src/main/AndroidManifest.xml (Modifications permissions + déclaration Service)
app/src/main/kotlin/com/mobicloud/
 ├── domain/
 │   └── repository/
 │       └── NetworkServiceController.kt (Interface pour Controller l'état du nœud)
 ├── data/
 │   └── network/
 │       └── service/
 │           └── MobicloudP2PService.kt (Service & MulticastLock Implementation)
 └── di/
     └── NetworkModule.kt (Pour lier ou injecter ce qui est nécessaire)
```

### 🧪 Testing Requirements
- **Unit/Integration Testing:** S'assurer que le cycle de vie du service est correct. Valider par des tests mockés (Robolectric ou autres) que `release()` est correctement appelé lors de la clôture du service. Mocker les classes du SDK (`WifiManager`).

## Previous Story Intelligence
- Story 1.1 et 1.2 ont mis en place `Hilt` et `Clean Architecture` avec l'infrastructure Junit/Coroutines.
- Attention aux exceptions ignorées (`.getOrNull()` problématiques précédemment). Toutes les erreurs de démarrage de l'Intent Service ou l'absence de Hardware doivent provoquer un ResultFailure interceptable proprement.

## Dev Agent Record

### Agent Model Used
Gemini 3.1 Pro (High) - Planner

### Debug Log References
- Extrait P2P Network Android 14 `connectedDevice` Multicast Requirements.
- sprint-status.yaml validé.
- Epics.md Story 1.3 parcouru exhaustivement.

### Completion Notes List
- Ultimate context engine analysis completed - comprehensive developer guide created.
- Implémenté Foreground Service MobicloudP2PService avec MulticastLock.
- Créé les interfaces NetworkServiceController dans le répertoire domain et son implémentation NetworkServiceControllerImpl qui gère les Intents.
- Modifié AndroidManifest.xml pour inclure toutes les permissions de type réseau et déclarer le service `connectedDevice`.
- Tests d'instrumentation complétés dans le dossier app/src/androidTest.

## File List
- app/src/main/AndroidManifest.xml
- app/src/main/kotlin/com/mobicloud/MainActivity.kt
- app/src/main/kotlin/com/mobicloud/domain/repository/NetworkServiceController.kt
- app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt
- app/src/main/kotlin/com/mobicloud/data/network/service/NetworkServiceControllerImpl.kt
- app/src/main/kotlin/com/mobicloud/di/NetworkModule.kt
- app/src/androidTest/kotlin/com/mobicloud/data/network/service/NetworkServiceControllerImplTest.kt

## Change Log
- Added network and foreground service permissions
- Configured Notification Channel and foreground state for P2P Service
- Added MulticastLock acquisition for UDP Traffic
- Added Hilt dependency binding for the domain controller

## Review Findings

- [x] [Review][Patch] `startForeground` appelé sans type sur SDK Q-S — simplifié, type `connectedDevice` passé dès API 29 [MobicloudP2PService.kt:34] — **fixé**
- [x] [Review][Decision→Patch] Interface domain retourne `Unit` — exceptions silencieuses, violation guardrail Clean Architecture — **fixé** : interface `NetworkServiceController` retourne `Result<Unit>`
- [x] [Review][Patch] NPE potentiel sur cast `WifiManager` non sécurisé [MobicloudP2PService.kt:80] — **fixé** : `getSystemService(WifiManager::class.java)` + garde null
- [x] [Review][Patch] Test ne valide pas le comportement réel — `assertNotNull(controller)` trivial [NetworkServiceControllerImplTest.kt] — **fixé** : 3 tests vérifiant le Result
- [x] [Review][Patch] `WAKE_LOCK` déclaré mais jamais utilisé [AndroidManifest.xml:27] — **fixé** : permission supprimée
- [x] [Review][Decision→Patch] `setReferenceCounted(true)` — risque drain batterie si kill OS [MobicloudP2PService.kt:82] — **fixé** : passé à `false`
- [x] [Review][Patch] Condition `Build.VERSION.SDK_INT >= Q` superflue — logique confuse [MobicloudP2PService.kt:34] — **fixé** : simplifié en une seule conditionnelle
- [x] [Review][Defer] `ForegroundServiceStartNotAllowedException` Android 12+ BG [NetworkServiceControllerImpl.kt:17] — différé, pré-existant (contrainte OS)
- [x] [Review][Defer] Tests Robolectric + mock WifiManager non implémentés [NetworkServiceControllerImplTest.kt] — différé, dépendances Robolectric non configurées
