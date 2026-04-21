# Story 5.4: ErasureProgressIndicator — Feedback UX en Temps Réel

Status: in-progress

## Story

En tant qu'utilisateur,
Je veux voir la progression du découpage et de la distribution de mes blocs Erasure en temps réel,
Afin de comprendre l'état de mon opération de stockage sans attendre la fin.

## Acceptance Criteria

1. **Given** l'utilisateur a déclenché un stockage de fichier via le FAB "Upload"
   **When** l'opération d'Erasure Coding et de distribution est en cours
   **Then** le composant `ErasureProgressIndicator` s'affiche dans l'`ExplorerScreen` avec la phase courante : "Encodage..." → "Chiffrement..." → "Distribution (X/K+N blocs)"

2. **And** chaque bloc confirmé par ACK incrémente le compteur de blocs distribués affiché en temps réel dans `ErasureProgressIndicator`

3. **And** les blocs de données (K=4) et de parité (N=2) sont visuellement distincts dans l'indicateur (couleur différente)

4. **And** en cas d'erreur sur un bloc (timeout ou NACK), ce bloc est affiché dans une couleur d'erreur avec l'index du bloc concerné

5. **And** à la fin de la distribution réussie, un toast `Snackbar` "Fichier stocké avec succès sur X nœuds" s'affiche, et `ErasureProgressIndicator` disparaît

6. **And** si la distribution échoue (< K confirmations), l'indicateur passe en état d'erreur global et affiche le message d'échec

## Tasks / Subtasks

- [x] Task 1 : Étendre `StoreState` — granularité des phases (AC: #1, #2, #4, #6)
  - [x] Subtask 1.1 : Modifier `presentation/explorer/StoreState.kt` — remplacer `object Loading` par une hiérarchie `sealed class InProgress`:
    ```kotlin
    sealed class StoreState {
        object Idle : StoreState()
        sealed class InProgress : StoreState() {
            object Encoding : InProgress()
            object Encrypting : InProgress()
            data class Distributing(
                val confirmed: Int,
                val total: Int,
                val dataBlockCount: Int,      // K = 4, blocs de données
                val failedIndices: List<Int> = emptyList()
            ) : InProgress()
        }
        data class Success(val entry: CatalogEntry, val nodeCount: Int) : StoreState()
        data class Error(val message: String) : StoreState()
    }
    ```
  - [x] Subtask 1.2 : Mettre à jour `ExplorerScreen.kt` — le `LaunchedEffect(storeState)` utilise `is StoreState.InProgress` pour ne pas déclencher de Snackbar pendant la progression
  - [x] Subtask 1.3 : Mettre à jour le test `ExplorerViewModelTest.kt` — adapter les assertions sur l'ancien `StoreState.Loading` vers les nouveaux états `InProgress.*`

- [x] Task 2 : Ajouter callback de progression dans `DistributeEncryptedBlocksUseCase` (AC: #2, #4)
  - [x] Subtask 2.1 : Modifier la signature de `distribute()` dans `domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` :
    ```kotlin
    suspend fun distribute(
        encryptedBundle: EncryptedBundle,
        fileHash: String,
        k: Int,
        onBlockResult: ((blockIndex: Int, success: Boolean) -> Unit)? = null
    ): Result<CatalogEntry>
    ```
  - [x] Subtask 2.2 : Dans la boucle d'envoi TCP existante (après chaque `blockSender.sendBlock()`), appeler `onBlockResult?.invoke(blockIndex, success)` immédiatement après réception de l'ACK ou du timeout — **avant** de collecter le `DeliveryRecord`
  - [x] Subtask 2.3 : Le callback est optionnel (`= null`) — compatibilité ascendante garantie avec les tests existants (5 tests dans `DistributeEncryptedBlocksUseCaseTest.kt` restent non-modifiés)

- [x] Task 3 : Émettre la progression depuis `ExplorerViewModel` (AC: #1, #2, #4, #5, #6)
  - [x] Subtask 3.1 : Dans `ExplorerViewModel.storeFile()`, émettre les phases dans l'ordre :
    ```kotlin
    _storeState.value = StoreState.InProgress.Encoding
    // ... encode ...
    _storeState.value = StoreState.InProgress.Encrypting
    // ... encrypt ...
    val total = bundle.encryptedFragments.size  // K+N = 6
    val dataBlockCount = params.k               // K = 4
    _storeState.value = StoreState.InProgress.Distributing(0, total, dataBlockCount)
    ```
  - [x] Subtask 3.2 : Passer le callback à `distributeEncryptedBlocksUseCase.distribute()` :
    ```kotlin
    distributeEncryptedBlocksUseCase.distribute(bundle, fileHash, k = params.k) { blockIndex, success ->
        val current = _storeState.value as? StoreState.InProgress.Distributing ?: return@distribute
        _storeState.value = current.copy(
            confirmed = if (success) current.confirmed + 1 else current.confirmed,
            failedIndices = if (!success) current.failedIndices + blockIndex else current.failedIndices
        )
    }
    ```
  - [x] Subtask 3.3 : Après `onSuccess { entry -> _storeState.value = StoreState.Success(entry, entry.fragmentLocations.size) }` — passer `nodeCount` depuis le nombre de `fragmentLocations`
  - [x] Subtask 3.4 : Guard anti-concurrence existant (`if (_storeState.value is StoreState.InProgress) return`) — adapter pour `is StoreState.InProgress` (remplacer l'ancien `is StoreState.Loading`)

- [x] Task 4 : Composant `ErasureProgressIndicator` (AC: #1, #2, #3, #4)
  - [x] Subtask 4.1 : Créer `presentation/explorer/components/ErasureProgressIndicator.kt`
  - [x] Subtask 4.2 : Signature :
    ```kotlin
    @Composable
    fun ErasureProgressIndicator(
        state: StoreState.InProgress,
        modifier: Modifier = Modifier
    )
    ```
  - [x] Subtask 4.3 : Affichage selon la phase :
    - `Encoding` → label "⚙ Encodage Erasure..." + LinearProgressIndicator indéterminé
    - `Encrypting` → label "🔒 Chiffrement AES-256..." + LinearProgressIndicator indéterminé
    - `Distributing` → label "⬆ Distribution (X/K+N blocs)" + rangée de blocs visuels
  - [x] Subtask 4.4 : Rendu des blocs pour `Distributing` — rangée horizontale de `total` carrés 20×20dp :
    - Index `0..(dataBlockCount-1)` = blocs de données : vert `Color(0xFF00FF41)` si confirmé, gris foncé `Color(0xFF1A1A1A)` si en attente
    - Index `dataBlockCount..(total-1)` = blocs de parité : amber `Color(0xFFFFB300)` si confirmé, gris foncé `Color(0xFF1A1A1A)` si en attente
    - Si index dans `failedIndices` : rouge `Color(0xFFFF3333)` + texte d'erreur sous le bloc
  - [x] Subtask 4.5 : Texte du compteur `"$confirmed / $total blocs"` en `FontFamily.Monospace`, `Color(0xFFE0E0E0)`, 13.sp — sous la rangée de blocs
  - [x] Subtask 4.6 : Respecter le thème OLED pur — fond `Color(0xFF000000)`, aucune élévation/ombre, bordure `Color(0xFF333333)` 1dp via `Modifier.border()`
  - [x] Subtask 4.7 : Pas d'animation de fondu ou d'ondes (anti-pattern batterie selon UX spec) — transitions d'état via recomposition Compose uniquement

- [x] Task 5 : Intégrer `ErasureProgressIndicator` dans `ExplorerScreen` (AC: #1, #5, #6)
  - [x] Subtask 5.1 : Dans `ExplorerScreen.kt`, sous le `Scaffold` → `PullToRefreshBox`, ajouter une section conditionnelle :
    ```kotlin
    val inProgressState = storeState as? StoreState.InProgress
    if (inProgressState != null) {
        ErasureProgressIndicator(
            state = inProgressState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    ```
  - [x] Subtask 5.2 : Positionner `ErasureProgressIndicator` **au-dessus** du `PullToRefreshBox` contenant la `LazyColumn`, pour qu'il reste visible pendant le scroll
  - [x] Subtask 5.3 : Mettre à jour `LaunchedEffect(storeState)` pour la gestion des toasts :
    ```kotlin
    is StoreState.Success -> snackbarHostState.showSnackbar(
        "Fichier stocké avec succès sur ${state.nodeCount} nœuds"
    )
    is StoreState.Error -> snackbarHostState.showSnackbar("Erreur : ${state.message}")
    else -> Unit  // InProgress et Idle ne déclenchent pas de Snackbar
    ```
  - [x] Subtask 5.4 : Après un `Success` ou `Error` — réinitialiser `_storeState` à `Idle` dans le `ViewModel` après un délai de 3 secondes (via `viewModelScope.launch { delay(3000); _storeState.value = StoreState.Idle }`) pour permettre la lecture du Snackbar avant reset

- [x] Task 6 : Tests unitaires JVM (AC: #1, #2, #4, #6)
  - [x] Subtask 6.1 : Créer `app/src/test/kotlin/com/mobicloud/presentation/explorer/ErasureProgressViewModelTest.kt`
  - [x] Subtask 6.2 : Test 1 — La séquence d'états émis lors de `storeFile()` est : `InProgress.Encoding → InProgress.Encrypting → InProgress.Distributing(0,6,4) → InProgress.Distributing(1,6,4) → ... → Success`
  - [x] Subtask 6.3 : Test 2 — Callback `onBlockResult(index=2, success=false)` provoque `Distributing.failedIndices = [2]` et `confirmed` inchangé
  - [x] Subtask 6.4 : Test 3 — Guard anti-concurrence : appel de `storeFile()` pendant `InProgress` est ignoré
  - [x] Subtask 6.5 : Framework : `kotlinx-coroutines-test` + `mockk:1.13.8`, `runTest { }`, `turbine` ou `toList()` pour collecter les émissions `StateFlow`

## Dev Notes

### 🔴 CE QUI EXISTE DÉJÀ — NE PAS RECRÉER

| Fichier | Contenu clé | Action |
|---|---|---|
| `presentation/explorer/StoreState.kt` | `sealed class StoreState { Idle, Loading, Success(entry), Error(message) }` | **MODIFIER** — remplacer `Loading` par `InProgress` hiérarchie |
| `presentation/explorer/ExplorerViewModel.kt` | `_storeState: MutableStateFlow<StoreState>`, `storeFile(uri)` avec pipeline complet | **MODIFIER** — émettre phases granulaires |
| `presentation/explorer/ExplorerScreen.kt` | `Scaffold` + FAB + `SnackbarHost` + `PullToRefreshBox` | **MODIFIER** — ajouter zone `ErasureProgressIndicator` |
| `presentation/explorer/components/CatalogEntryCard.kt` | Pattern composant OLED : `Surface(color=Black)`, bordure `#333333`, `FontFamily.Monospace` | **REPRODUIRE exactement** ce style dans `ErasureProgressIndicator` |
| `domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` | `suspend fun distribute(bundle, fileHash, k): Result<CatalogEntry>` | **MODIFIER** — ajouter paramètre optionnel `onBlockResult` |
| `domain/models/ErasureParameters.kt` | `k = 4, n = 2` (K=4 données, N=2 parité, total=6 blocs) | **RÉFÉRENCER** — `params.k` et `bundle.encryptedFragments.size` pour `dataBlockCount` et `total` |
| `domain/models/CatalogEntry.kt` | `fragmentLocations: List<FragmentLocation>` — taille = K+N nœuds réels confirmés | **UTILISER `.size`** pour `nodeCount` dans `StoreState.Success` |
| `app/src/test/.../DistributeEncryptedBlocksUseCaseTest.kt` | 5 tests JVM existants (passent `null` implicitement au callback) | **NE PAS MODIFIER** — signature compatible car `onBlockResult = null` par défaut |
| `app/src/test/.../ExplorerViewModelTest.kt` | Tests existants sur `storeState` | **ADAPTER** — changer assertions `StoreState.Loading` → `StoreState.InProgress` |

### ⚠️ CONTRAINTES CRITIQUES

**1. Architecture Compose — pas d'animation intensive (contrainte UX spec) :**
```
// INTERDIT — consume batterie GPU
AnimatedVisibility(...) { ... }
animateColorAsState(...)

// AUTORISÉ — recomposition native Compose uniquement
if (isConfirmed) Color(0xFF00FF41) else Color(0xFF1A1A1A)
```

**2. Callback dans `DistributeEncryptedBlocksUseCase` — thread-safety :**
```kotlin
// Le callback est appelé depuis Dispatchers.IO dans withContext(Dispatchers.IO)
// MutableStateFlow.value = ... est thread-safe — pas besoin de mutex
// Le viewModelScope collecte le StateFlow sur le thread UI automatiquement
```

**3. Guard anti-concurrence — adapter la condition :**
```kotlin
// AVANT (Story 5.3) :
if (_storeState.value is StoreState.Loading) return

// APRÈS (Story 5.4) :
if (_storeState.value is StoreState.InProgress) return
```

**4. Reset après `Success`/`Error` — obligatoire :**
Le `LaunchedEffect(storeState)` dans `ExplorerScreen` se déclenche à chaque changement de `storeState`. Si `storeState` reste `Success`, le Snackbar ne se re-déclenche pas au prochain fichier. Implémenter le reset `delay(3000) → Idle`.

**5. `StoreState.Success` — ajouter `nodeCount` :**
```kotlin
// AVANT :
data class Success(val entry: CatalogEntry) : StoreState()

// APRÈS :
data class Success(val entry: CatalogEntry, val nodeCount: Int) : StoreState()
```
Le `nodeCount` = `entry.fragmentLocations.size` (nœuds effectivement confirmés) — utilisé dans le toast.

**6. Visuel blocs — largeur adaptative :**
```kotlin
// K+N = 6 blocs → 6 × 20dp + 5 × 4dp gap = 140dp total → tient en portrait sur tous les écrans Android
// Ne pas hardcoder la taille — si `total > 10` (extensions futures), utiliser `FlowRow` ou wrap
// Pour la story actuelle : total = 6, Row simple suffisant
```

### 📁 Arborescence cible après implémentation

```
app/src/main/kotlin/com/mobicloud/
└── presentation/
    └── explorer/
        ├── StoreState.kt                          ← MODIFIÉ (InProgress hierarchy)
        ├── ExplorerViewModel.kt                   ← MODIFIÉ (phases granulaires + reset)
        ├── ExplorerScreen.kt                      ← MODIFIÉ (ErasureProgressIndicator intégré)
        └── components/
            ├── CatalogEntryCard.kt                ← INCHANGÉ
            └── ErasureProgressIndicator.kt        ← NOUVEAU (composant UX-DR4)

app/src/main/kotlin/com/mobicloud/
└── domain/
    └── usecase/
        └── m08_m09_erasure_coding/
            └── DistributeEncryptedBlocksUseCase.kt  ← MODIFIÉ (+ onBlockResult callback)

app/src/test/kotlin/com/mobicloud/
└── presentation/
    └── explorer/
        └── ErasureProgressViewModelTest.kt        ← NOUVEAU (3 tests JVM)
```

### 🎯 Contraintes Non-Négociables

- **Pas d'animation GPU** : aucun `AnimatedVisibility`, `animateColorAsState`, ni Canvas animé — recomposition Compose pure uniquement (UX spec : anti-pattern batterie).
- **Thème OLED pur** : fond `#000000`, bordures `#333333`, monospace, aucune élévation/ombre — respecter exactement `CatalogEntryCard` comme référence visuelle.
- **Callback optionnel** : `onBlockResult = null` par défaut — les 5 tests existants de `DistributeEncryptedBlocksUseCaseTest` doivent passer sans modification.
- **Thread-safe** : `_storeState.value = ...` depuis `Dispatchers.IO` est autorisé — `MutableStateFlow` est thread-safe.
- **K=4 données, N=2 parité** : toujours lire depuis `params.k` et `bundle.encryptedFragments.size` — ne pas hardcoder 4 et 2.
- **Interdit** : créer un nouveau `UseCase` ou `Repository` pour la progression — la progression est gérée par callback inline.

### 🔗 Intégration avec les Stories Adjacentes

- **Story 5.3 (done) → Story 5.4 :** `DistributeEncryptedBlocksUseCase.distribute()` étendu avec `onBlockResult`
- **Story 5.1 (done) → Story 5.4 :** `ErasureParameters.k` = 4 pour `dataBlockCount`
- **Story 5.2 (done) → Story 5.4 :** `bundle.encryptedFragments.size` = K+N = 6 pour `total`
- **Story 5.4 → Story 5.5 (Réception) :** Aucune dépendance — stories parallèles

### 📚 Références patterns

- [CatalogEntryCard.kt](../../app/src/main/kotlin/com/mobicloud/presentation/explorer/components/CatalogEntryCard.kt) — Référence style OLED (Surface + border + Monospace)
- [ExplorerViewModel.kt](../../app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt) — Pipeline `storeFile()` à étendre
- [ExplorerScreen.kt](../../app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt) — Structure `Scaffold` à étendre
- [DistributeEncryptedBlocksUseCase.kt](../../app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt) — Signature à étendre

### Review Findings

- [x] [Review][Decision] D1 — LinearProgressIndicator indéterminé : violation de la contrainte GPU ? — Accepté : non listé explicitement dans la contrainte. [ErasureProgressIndicator.kt]
- [x] [Review][Decision] D2 — Labels de phase : déviation du spec AC1 — Accepté : labels enrichis conservés. [ErasureProgressIndicator.kt]
- [x] [Review][Decision] D3 — Format affichage index bloc en erreur : "!$index" vs index nu — Accepté : "!$index" conservé, cohérent avec le thème terminal. [ErasureProgressIndicator.kt]
- [x] [Review][Patch] P1 — `isConfirmed = index < confirmed` : coloring incorrecte quand un bloc au milieu échoue [ErasureProgressIndicator.kt ~L83] — CORRIGÉ : `StoreState.InProgress.Distributing` remplace `confirmed: Int` par `confirmedIndices: Set<Int>` (avec propriété calculée `confirmed`). Coloring utilise `index in state.confirmedIndices`.
- [x] [Review][Patch] P2 — `scheduleReset()` non contrôlé : annule le Snackbar + écrase le 2e `storeFile()` [ExplorerViewModel.kt ~L136] — CORRIGÉ : `private var resetJob: Job?` stocke le job ; `resetJob?.cancel()` au début de `storeFile()` ; délai porté à 5000ms (> Snackbar Short ~4s).
- [x] [Review][Patch] P3 — AC3 : blocs data/parité visuellement identiques à l'état "en attente" [ErasureProgressIndicator.kt ~L87] — CORRIGÉ : blocs data en attente → `Color(0xFF0D2B0D)` (teinte verte sombre) ; blocs parité en attente → `Color(0xFF2B2000)` (teinte amber sombre).
- [x] [Review][Patch] P4 — Copy-paste : message d'erreur identity failure [ExplorerViewModel.kt ~L93] — Non applicable : le code actuel avait déjà "Identité locale indisponible" correct. Null-safety ajoutée sur tous les messages.
- [x] [Review][Patch] P5 — `"Échec distribution: null"` si `e.message` est null [ExplorerViewModel.kt ~L126] — CORRIGÉ : tous les messages d'erreur utilisent `${e.message ?: "erreur inconnue"}`.
- [x] [Review][Patch] P6 — `readBytes()` sans garde de taille : OOM sur gros fichiers [ExplorerViewModel.kt ~L68] — CORRIGÉ : `ContentResolver.query(OpenableColumns.SIZE)` avant `readBytes()` ; rejet avec Error si > 100 Mo.
- [x] [Review][Patch] P7 — TOCTOU guard : état `InProgress` posé dans la coroutine [ExplorerViewModel.kt ~L58] — CORRIGÉ : `_storeState.value = StoreState.InProgress.Encoding` déplacé avant `viewModelScope.launch`.
- [x] [Review][Defer] W1 — Pas de feedback UI pour le tap ignoré pendant InProgress [ExplorerViewModel.kt:58] — deferred, pre-existing UX gap
- [x] [Review][Defer] W2 — Hash SHA-256 du plaintext exposé dans le catalogue : metadata leak [ExplorerViewModel.kt ~L73] — deferred, pre-existing design decision
- [x] [Review][Defer] W3 — `tempFile` créé avant le bloc `try` : narrow gap si annulation coroutine [ExplorerViewModel.kt ~L77] — deferred, pre-existing
- [x] [Review][Defer] W4 — Read-modify-write non-atomique dans le callback onBlockResult : latent si distribution parallélisée [ExplorerViewModel.kt ~L114] — deferred, sequential today, use `.update{}` in future refactor

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Race condition `withContext(Dispatchers.IO)` + `advanceUntilIdle()` dans les tests coroutines : résolu avec `advanceWithIoFlush()` (advanceUntilIdle + Thread.sleep(100) + advanceUntilIdle) pour laisser les threads IO réels poster leurs continuations avant de vérifier les assertions.
- StateFlow conflation dans les tests de séquence : résolu avec `yield()` dans les mocks `coAnswers` pour créer des points de suspension entre les changements d'état, permettant au collector de capturer chaque état intermédiaire.
- Gradle configuration cache avec ancien bytecode : utiliser `--no-configuration-cache` en cas de doute.

### Completion Notes List

- `StoreState` étendu avec hiérarchie `InProgress` (Encoding, Encrypting, Distributing) + `Success(nodeCount)` ajouté
- `DistributeEncryptedBlocksUseCase.distribute()` étendu avec `onBlockResult` optionnel — 5 tests existants inchangés
- `ExplorerViewModel.storeFile()` émet les 3 phases granulaires + callback inline + `scheduleReset()` après Success/Error
- Guard anti-concurrence mis à jour : `is StoreState.Loading` → `is StoreState.InProgress`
- `ErasureProgressIndicator` créé (nouveau composant) — thème OLED pur, pas d'animation GPU, blocs visuels couleur-codés
- `ExplorerScreen` restructuré : `Column` contenant `ErasureProgressIndicator` (conditionnel) + `PullToRefreshBox`
- Snackbar mis à jour : "Fichier stocké avec succès sur X nœuds" avec `nodeCount`
- 3 nouveaux tests JVM : séquence de phases, failedIndices, guard anti-concurrence — 138 tests passent (0 régressions)

### File List

- `app/src/main/kotlin/com/mobicloud/presentation/explorer/StoreState.kt` — MODIFIÉ
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt` — MODIFIÉ
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt` — MODIFIÉ
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/ErasureProgressIndicator.kt` — NOUVEAU
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m08_m09_erasure_coding/DistributeEncryptedBlocksUseCase.kt` — MODIFIÉ
- `app/src/test/kotlin/com/mobicloud/presentation/explorer/ErasureProgressViewModelTest.kt` — NOUVEAU
- `_bmad-output/implementation-artifacts/5-4-erasureprogressindicator-feedback-ux-en-temps-reel.md` — MODIFIÉ
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — MODIFIÉ

## Change Log

- Story 5.4 créée par bmad-create-story (Date: 2026-04-21)
- Story 5.4 implémentée par claude-sonnet-4-6 (Date: 2026-04-21) — 6 tâches, 138 tests passent
