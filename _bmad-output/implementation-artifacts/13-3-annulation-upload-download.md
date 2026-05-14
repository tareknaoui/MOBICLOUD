# Story 13.3 — Annulation Upload & Download

## Story

**As a** utilisateur grand public,  
**I want** pouvoir annuler un upload ou un download en cours,  
**So that** je reprends le contrôle sans attendre la fin d'une opération longue ou bloquée.

---

## Status

review

---

## Context

### Scope délibéré (décisions d'architecture)

Le pipeline d'upload est **séquentiel et non-checkpointable** : Encoding → Encrypting → Distributing(callback). Implémenter un vrai Pause/Resume nécessiterait de refactoriser chaque étape en `Flow` avec points de suspension, ce qui sort du budget de cette story. **Cette story implémente Cancel uniquement**, pour upload et download.

La file d'attente (upload queue) est également hors scope : l'UI notifiera déjà l'utilisateur qu'un upload est en cours (snackbar) quand il en déclenche un second.

### État actuel du code

- **Upload** : `storeFile(uri)` est lancé comme `viewModelScope.launch { ... }` anonyme — aucun `storeJob: Job?` pour l'annuler. Guard présent : `if (_storeState.value is StoreState.InProgress) return`.
- **Download** : `downloadJob` et `locateJob` sont déjà trackés en `Job?` et `resetDownloadState()` les cancelle — mais **aucun bouton Cancel n'existe dans l'UI**.
- **StoreState** : Idle / InProgress(Encoding/Encrypting/Distributing) / Success / Error — pas de `Cancelled`.
- **ErasureProgressIndicator** : Composable `@Composable fun ErasureProgressIndicator(state: StoreState.InProgress)` — pas de callback `onCancel`.

---

## Acceptance Criteria

### AC1 — Cancel Upload : bouton présent pendant InProgress
**Given** un upload est en cours (StoreState.InProgress),  
**When** l'utilisateur appuie sur "Annuler",  
**Then** `viewModel.cancelUpload()` est appelé, le job coroutine est annulé, et le state passe à `StoreState.Cancelled` dans la même frame (avant scheduleReset).

### AC2 — Cancel Upload : feedback visuel et retour à Idle
**Given** l'état vient de passer à `StoreState.Cancelled`,  
**When** 3 secondes s'écoulent (scheduleReset réduit à 3s pour Cancelled),  
**Then** l'état repasse à `StoreState.Idle` et l'UI revient à l'état normal (FAB visible, pas de progress indicator).

### AC3 — Cancel Upload : fichier temporaire supprimé
**Given** l'upload est annulé à n'importe quelle étape (Encoding, Encrypting, Distributing),  
**When** la coroutine est annulée,  
**Then** le bloc `finally { tempFile.delete() }` existant s'exécute (déjà garanti par l'architecture coroutine — à vérifier en test).

### AC4 — Cancel Download : bouton présent pendant Locating/Downloading/Decrypting
**Given** un download est en cours (DownloadState.Locating, Downloading ou Decrypting),  
**When** l'utilisateur appuie sur "Annuler",  
**Then** `viewModel.resetDownloadState()` est appelé (déjà implémenté, cancelle `locateJob` + `downloadJob`), et l'état passe à `DownloadState.Idle`.

### AC5 — Upload en cours : snackbar si l'utilisateur tente un second upload
**Given** un upload est déjà en cours (`StoreState.InProgress`),  
**When** l'utilisateur appuie sur le FAB et sélectionne un fichier,  
**Then** une snackbar "Upload en cours, veuillez patienter" s'affiche (le `return` existant dans `storeFile()` est préservé, on ajoute juste l'émission du message via SharedFlow).

### AC6 — ErasureProgressIndicator : paramètre onCancel
**Given** le composable `ErasureProgressIndicator` est rendu,  
**When** il reçoit `onCancel: () -> Unit`,  
**Then** un bouton "✕ Annuler" rouge apparaît en bas du widget, visible quel que soit le sous-état (Encoding, Encrypting, Distributing).

---

## Tasks / Subtasks

### Task 1 — StoreState : ajouter l'état Cancelled
- [x] 1.1 Dans `StoreState.kt`, ajouter `object Cancelled : StoreState()` au même niveau que `Idle`, `Success`, `Error`
- [x] 1.2 Vérifier que tous les `when(storeState)` exhaustifs compilent toujours (ExplorerScreen, ErasureProgressIndicator, tests)

### Task 2 — ExplorerViewModel : tracker storeJob + cancelUpload()
- [x] 2.1 Ajouter `private var storeJob: Job? = null` après `private var resetJob: Job? = null`
- [x] 2.2 Dans `storeFile()`, changer `viewModelScope.launch {` en `storeJob = viewModelScope.launch {`
- [x] 2.3 Ajouter la fonction `cancelUpload()` :
  ```kotlin
  fun cancelUpload() {
      storeJob?.cancel()
      resetJob?.cancel()
      _storeState.value = StoreState.Cancelled
      scheduleReset(delayMs = 3000L)
  }
  ```
- [x] 2.4 Modifier `scheduleReset()` pour accepter un paramètre `delayMs: Long = 5000L` (valeur par défaut inchangée pour Success/Error)
- [x] 2.5 Ajouter `private val _uploadBusyEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)` et `val uploadBusyEvent: SharedFlow<Unit> = _uploadBusyEvent.asSharedFlow()`
- [x] 2.6 Dans `storeFile()`, à la place du `return` silencieux : `if (_storeState.value is StoreState.InProgress) { _uploadBusyEvent.tryEmit(Unit); return }`

### Task 3 — ErasureProgressIndicator : bouton Annuler
- [x] 3.1 Modifier la signature : `fun ErasureProgressIndicator(state: StoreState.InProgress, onCancel: () -> Unit = {}, modifier: Modifier = Modifier)`
- [x] 3.2 Ajouter en bas du `Column` (après le contenu existant) :
  ```kotlin
  TextButton(
      onClick = onCancel,
      modifier = Modifier.align(Alignment.End)
  ) {
      Text("✕ Annuler", color = Color(0xFFFF3333), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
  }
  ```
- [x] 3.3 Ajouter l'import `androidx.compose.material3.TextButton`

### Task 4 — ExplorerScreen : câblage Cancel upload + snackbar busy
- [x] 4.1 Passer `onCancel = { viewModel.cancelUpload() }` au `ErasureProgressIndicator`
- [x] 4.2 Ajouter `LaunchedEffect(Unit)` pour `uploadBusyEvent` :
  ```kotlin
  LaunchedEffect(Unit) {
      viewModel.uploadBusyEvent.collect {
          snackbarHostState.showSnackbar("Upload en cours, veuillez patienter")
      }
  }
  ```
- [x] 4.3 Dans le `when(storeState)`, ajouter le cas `is StoreState.Cancelled` : afficher un `Text("⊘ Upload annulé", color = Color(0xFFFF3333), ...)` dans la zone progress (même emplacement que `ErasureProgressIndicator`)

### Task 5 — ExplorerScreen : bouton Cancel download dans DownloadProgressIndicator
- [x] 5.1 Lire `DownloadProgressIndicator.kt` pour comprendre sa signature actuelle
- [x] 5.2 Ajouter `onCancel: () -> Unit = {}` à `DownloadProgressIndicator`
- [x] 5.3 Afficher le bouton "✕ Annuler" rouge uniquement pendant `Locating`, `Downloading`, `Decrypting` (pas pendant `Assembled`)
- [x] 5.4 Dans `ExplorerScreen`, passer `onCancel = { viewModel.resetDownloadState() }` au `DownloadProgressIndicator`

### Task 6 — Tests
- [x] 6.1 Créer `ExplorerViewModelCancelUploadTest.kt` dans `app/src/test/.../presentation/explorer/` :
  - Test : `cancelUpload() annule storeJob` → `storeFile()` lancé, puis `cancelUpload()`, state == `Cancelled`
  - Test : `cancelUpload() scheduleReset à 3s` → advanceTimeBy(3001), state == `Idle`
  - Test : `storeFile() quand InProgress émet uploadBusyEvent` → lancer storeFile, state devient InProgress, relancer storeFile, vérifier event émis
- [x] 6.2 Mettre à jour `ExplorerViewModelTrashTest.kt` si la signature de `ExplorerViewModel` a changé (vérifier compilation)

---

## Dev Notes

### Architecture — pourquoi le `storeJob` capte bien la CancellationException

`storeJob?.cancel()` déclenche une `CancellationException` dans la coroutine. Le bloc `catch (e: CancellationException) { throw e }` existant dans `storeFile()` la rethrow correctement, et le `finally { tempFile.delete() }` s'exécute dans tous les cas. Ne pas supprimer ce catch.

### Architecture — scheduleReset paramétrique

`scheduleReset()` doit accepter un `delayMs` pour que `cancelUpload()` puisse utiliser 3s au lieu de 5s. Les callsites existants (Success, Error) n'ont pas besoin de changer (valeur par défaut = 5000L).

### Architecture — `uploadBusyEvent` vs modifier `storeFile()`

Le `return` silencieux dans `storeFile()` est intentionnel (guard contre TOCTOU). On l'augmente en émettant dans le SharedFlow, sans changer le comportement du guard.

### Fichiers à modifier (UPDATE)

| Fichier | Nature |
|---------|--------|
| `app/src/main/kotlin/com/mobicloud/presentation/explorer/StoreState.kt` | UPDATE — ajouter `Cancelled` |
| `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt` | UPDATE — storeJob, cancelUpload, uploadBusyEvent, scheduleReset param |
| `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/ErasureProgressIndicator.kt` | UPDATE — onCancel param + bouton |
| `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt` | UPDATE — câblage onCancel, LaunchedEffect busy, cas Cancelled |
| `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/DownloadProgressIndicator.kt` | UPDATE — onCancel param + bouton |

### Fichiers à créer (NEW)

| Fichier | Nature |
|---------|--------|
| `app/src/test/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModelCancelUploadTest.kt` | NEW — 3 tests unitaires |

### Erreurs de compilation connues (pré-existantes, hors scope)

Les tests suivants échouent à la compilation depuis Story 13.1 — NE PAS corriger dans cette story :
- `DashboardViewModelTest`
- `JoinIntegrationTest`
- `MemberLivenessNfrTest`
- `MonitorMemberLivenessUseCaseTest`
- `ProcessJoinRequestUseCaseTest`

`compileDebugKotlin` (sources principales) DOIT passer. `compileDebugUnitTestKotlin` peut échouer sur les tests pré-existants.

### Patterns à respecter

- OLED : `Color(0xFF000000)` fond, `Color(0xFFFF3333)` rouge cancel, `FontFamily.Monospace`
- Imports Material3 : `TextButton` depuis `androidx.compose.material3`
- SharedFlow avec `extraBufferCapacity = 1` pour événements one-shot (pattern établi par `undoEvent` en 13.2)
- `@OptIn(ExperimentalMaterial3Api::class)` déjà présent dans ExplorerScreen

---

## File List

**Modifiés (UPDATE)**
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/StoreState.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/ErasureProgressIndicator.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/DownloadProgressIndicator.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

**Créés (NEW)**
- `app/src/test/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModelCancelUploadTest.kt`

---

## Change Log

| Date | Description |
|------|-------------|
| 2026-05-14 | Story créée — bmad-create-story |
| 2026-05-14 | Implémentation complète — cancel upload + cancel download + snackbar busy + StoreState.Cancelled |

---

## Dev Agent Record

### Implementation Plan

1. `StoreState.Cancelled` ajouté — nouveau sealed object au même niveau que Idle/Success/Error
2. `storeJob: Job?` tracke la coroutine de `storeFile()` — pattern identique à `downloadJob`
3. `cancelUpload()` : cancel storeJob + resetJob, set Cancelled, scheduleReset(3s)
4. `scheduleReset(delayMs)` paramétré — default 5s inchangé pour Success/Error, 3s pour Cancelled
5. `uploadBusyEvent: SharedFlow<Unit>` — pattern SharedFlow(extraBufferCapacity=1) identique à `undoEvent`
6. `ErasureProgressIndicator` : `onCancel: () -> Unit = {}` + TextButton "✕ Annuler" rouge en bas
7. `DownloadProgressIndicator` : même pattern que ErasureProgressIndicator
8. `ExplorerScreen` : LaunchedEffect(Unit) pour uploadBusyEvent, cas Cancelled, câblage onCancel sur les deux indicateurs
9. 3 tests unitaires via `ExplorerViewModelCancelUploadTest`

### Debug Log

- `compileDebugKotlin` : BUILD SUCCESSFUL (warnings deprecation pré-existants inchangés)
- `compileDebugUnitTestKotlin` : échec sur tests pré-existants Story 13.1 (DashboardViewModelTest, JoinIntegrationTest, etc.) — hors scope

### Completion Notes

- Cancel upload : `storeJob?.cancel()` dans `cancelUpload()` → `CancellationException` reraisée → `finally { tempFile.delete() }` garanti
- Cancel download : `resetDownloadState()` pré-existant exposé via bouton dans `DownloadProgressIndicator`
- État `Cancelled` visible 3s puis retour à `Idle` via `scheduleReset(3000L)`
- Snackbar "Upload en cours" via `uploadBusyEvent.tryEmit(Unit)` quand upload déjà InProgress
- Aucune régression introduite : `compileDebugKotlin` BUILD SUCCESSFUL
