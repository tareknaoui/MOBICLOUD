# Story 13.2 — Corbeille : Suppression et restauration de fichiers

## Status: done

## Story

**En tant qu'**utilisateur grand public,  
**je veux** pouvoir supprimer un fichier de mon explorateur, le retrouver dans une corbeille, le restaurer ou le supprimer définitivement,  
**afin de** gérer mon espace de stockage de façon sûre, avec une période de grâce avant suppression irréversible.

---

## Acceptance Criteria

### AC-1 : Déplacement vers la corbeille (soft-delete)
- **Étant donné** que l'utilisateur est sur l'écran Explorateur,
- **Quand** il effectue un balayage (swipe) vers la gauche sur une fiche de catalogue,
- **Alors** un fond rouge avec une icône de poubelle apparaît derrière la carte, et au relâchement la fiche disparaît de la liste principale et est marquée `isInTrash = true` avec `deletedAt = now()` dans la base de données locale.

### AC-2 : Snackbar "Annuler"
- **Étant donné** qu'un fichier vient d'être déplacé en corbeille,
- **Quand** la snackbar "Déplacé vers la corbeille" s'affiche,
- **Alors** l'utilisateur peut appuyer sur "Annuler" dans les 5 secondes pour restaurer le fichier immédiatement dans l'explorateur.

### AC-3 : Accès à la corbeille depuis l'explorateur
- **Étant donné** que des fichiers sont en corbeille,
- **Quand** l'utilisateur appuie sur l'icône corbeille dans la barre supérieure de l'Explorateur,
- **Alors** l'écran Corbeille s'ouvre avec la liste des fichiers supprimés.

### AC-4 : Affichage de la liste de la corbeille
- **Étant donné** que l'utilisateur est sur l'écran Corbeille,
- **Alors** chaque ligne affiche : nom du fichier (`originalFileName`), date de suppression au format "dd/MM/yyyy", jours restants avant expiration (30 jours - (now - deletedAt)).

### AC-5 : Restauration individuelle
- **Étant donné** que l'utilisateur est sur l'écran Corbeille,
- **Quand** il appuie sur l'icône "Restaurer" d'un fichier,
- **Alors** le fichier est retiré de la corbeille (`isInTrash = false`, `deletedAt = null`) et réapparaît dans l'explorateur.

### AC-6 : Suppression définitive individuelle
- **Étant donné** que l'utilisateur est sur l'écran Corbeille,
- **Quand** il appuie sur l'icône "Supprimer définitivement" d'un fichier puis confirme dans le dialogue de confirmation,
- **Alors** la ligne est supprimée de `catalog_entry` (ainsi que ses fragments associés dans `fragment_location`).

### AC-7 : Vider la corbeille
- **Étant donné** que l'utilisateur est sur l'écran Corbeille,
- **Quand** il appuie sur "Vider la corbeille" et confirme,
- **Alors** toutes les entrées en corbeille sont supprimées définitivement de la base de données (catalog_entry + fragment_location).

### AC-8 : Expiration automatique (TTL 30 jours)
- **Étant donné** qu'un fichier est en corbeille depuis plus de 30 jours,
- **Quand** l'écran Corbeille s'ouvre (ou l'explorateur se lance),
- **Alors** les entrées expirées sont automatiquement supprimées définitivement sans intervention de l'utilisateur.

### AC-9 : Corbeille vide — état vide
- **Étant donné** qu'aucun fichier n'est en corbeille,
- **Alors** l'écran Corbeille affiche "Votre corbeille est vide" centré avec une icône poubelle.

### AC-10 : Explorateur — liste filtrée
- **Étant donné** que des fichiers sont en corbeille (`isInTrash = true`),
- **Alors** ils ne s'affichent PAS dans la liste principale de l'Explorateur.

---

## Tasks / Subtasks

### T1 — Migration Room v17 → v18 : colonnes corbeille sur `catalog_entry`
- [x] T1.1 — Ajouter `is_in_trash INTEGER NOT NULL DEFAULT 0` et `deleted_at INTEGER` (nullable) à `CatalogEntryEntity` avec `@ColumnInfo(defaultValue = "0")` et `@ColumnInfo(defaultValue = "0")` respectivement.
- [x] T1.2 — Incrémenter `CatalogDatabase.version` de 17 à 18 et ajouter la migration :
  ```kotlin
  val MIGRATION_17_18 = Migration(17, 18) { db ->
      db.execSQL("ALTER TABLE catalog_entry ADD COLUMN is_in_trash INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE catalog_entry ADD COLUMN deleted_at INTEGER DEFAULT NULL")
  }
  ```
- [x] T1.3 — Enregistrer `MIGRATION_17_18` dans le builder de `CatalogDatabase`.

### T2 — DAO : nouvelles requêtes corbeille
- [x] T2.1 — Ajouter `@Query("SELECT * FROM catalog_entry WHERE is_in_trash = 0") fun getAllActiveEntriesFlow(): Flow<List<CatalogEntryWithFragments>>` dans `CatalogDao`.
- [x] T2.2 — Ajouter `@Query("SELECT * FROM catalog_entry WHERE is_in_trash = 1") fun getDeletedEntriesFlow(): Flow<List<CatalogEntryWithFragments>>`.
- [x] T2.3 — Ajouter `@Query("UPDATE catalog_entry SET is_in_trash = 1, deleted_at = :ts WHERE file_hash = :hash") suspend fun softDeleteEntry(hash: String, ts: Long)`.
- [x] T2.4 — Ajouter `@Query("UPDATE catalog_entry SET is_in_trash = 0, deleted_at = NULL WHERE file_hash = :hash") suspend fun restoreEntry(hash: String)`.
- [x] T2.5 — Ajouter `@Transaction suspend fun permanentlyDeleteEntry(hash: String)` qui appelle `deleteFragmentLocations(hash)` puis `deleteCatalogEntry(hash)` (ajouter `@Query("DELETE FROM catalog_entry WHERE file_hash = :hash") suspend fun deleteCatalogEntry(hash: String)`).
- [x] T2.6 — Ajouter `@Query("DELETE FROM catalog_entry WHERE is_in_trash = 1 AND deleted_at < :expiryTs") suspend fun deleteExpiredEntries(expiryTs: Long)` et la purge des fragments correspondants (Transaction).
- [x] T2.7 — Ajouter `@Query("SELECT * FROM catalog_entry WHERE is_in_trash = 1 AND deleted_at < :expiryTs") suspend fun getExpiredEntries(expiryTs: Long): List<CatalogEntryWithFragments>`.

### T3 — Domain : extension du modèle et dépôt
- [x] T3.1 — Ajouter `isInTrash: Boolean = false` et `deletedAt: Long? = null` à `CatalogEntry` (sans `@ProtoNumber` — non distribué dans le DHT, local uniquement).
- [x] T3.2 — Mettre à jour `CatalogEntryEntity.toDomain()` et `CatalogEntry.toEntity()` pour mapper `isInTrash` / `deletedAt`.
- [x] T3.3 — Ajouter à l'interface `CatalogRepository` :
  - `fun getActiveEntriesFlow(): Flow<List<CatalogEntry>>`
  - `fun getDeletedEntriesFlow(): Flow<List<CatalogEntry>>`
  - `suspend fun moveToTrash(fileHash: String): Result<Unit>`
  - `suspend fun restoreFromTrash(fileHash: String): Result<Unit>`
  - `suspend fun permanentlyDelete(fileHash: String): Result<Unit>`
  - `suspend fun emptyTrash(): Result<Unit>`
  - `suspend fun purgeExpired(): Result<Unit>`
- [x] T3.4 — Implémenter toutes ces méthodes dans `CatalogRepositoryImpl` en délégant au DAO avec `Dispatchers.IO`.

### T4 — ExplorerViewModel + ExplorerScreen : swipe-to-delete
- [x] T4.1 — Remplacer `catalogRepository.getAllEntriesFlow()` par `catalogRepository.getActiveEntriesFlow()` dans `ExplorerViewModel` (AC-10).
- [x] T4.2 — Ajouter `fun moveToTrash(fileHash: String)` dans `ExplorerViewModel` (appelle `catalogRepository.moveToTrash()`, émet un `UndoEvent`).
- [x] T4.3 — Ajouter `fun undoMoveToTrash(fileHash: String)` dans `ExplorerViewModel`.
- [x] T4.4 — Ajouter `val undoEvent: SharedFlow<String>` dans `ExplorerViewModel` (fileHash à annuler).
- [x] T4.5 — Dans `ExplorerScreen`, ajouter un `IconButton` corbeille dans la `TopAppBar` qui navigue vers `TrashRoute`.
- [x] T4.6 — Wrapper chaque `CatalogEntryCard` avec `SwipeToDismissBox` (Material3) : direction `EndToStart`, fond rouge avec `Icons.Default.Delete`, dismiss threshold 40 %. Au dismiss, appeler `viewModel.moveToTrash(entry.fileHash)`.
- [x] T4.7 — Collecter `undoEvent` dans `ExplorerScreen` avec `LaunchedEffect` et afficher une snackbar "Déplacé vers la corbeille" avec action "Annuler" (durée 5 s) qui appelle `viewModel.undoMoveToTrash(fileHash)`.

### T5 — Purge TTL au démarrage de l'Explorateur
- [x] T5.1 — Dans `ExplorerViewModel.init {}`, appeler `catalogRepository.purgeExpired()` (TTL = 30 jours = `System.currentTimeMillis() - 30L * 24 * 3600 * 1000`).

### T6 — TrashViewModel
- [x] T6.1 — Créer `app/src/main/kotlin/com/mobicloud/presentation/trash/TrashViewModel.kt` avec `@HiltViewModel`.
- [x] T6.2 — Exposer `deletedEntries: StateFlow<List<CatalogEntry>>` via `catalogRepository.getDeletedEntriesFlow().stateIn(...)`.
- [x] T6.3 — Implémenter `fun restoreEntry(fileHash: String)` → `catalogRepository.restoreFromTrash()`.
- [x] T6.4 — Implémenter `fun permanentlyDelete(fileHash: String)` → `catalogRepository.permanentlyDelete()`.
- [x] T6.5 — Implémenter `fun emptyTrash()` → `catalogRepository.emptyTrash()`.

### T7 — TrashScreen
- [x] T7.1 — Créer `app/src/main/kotlin/com/mobicloud/presentation/trash/TrashScreen.kt`.
- [x] T7.2 — Définir `@Serializable object TrashRoute`.
- [x] T7.3 — `Scaffold` OLED (`#000000`) avec `TopAppBar` "Corbeille" + bouton retour + action "Vider" (rouge, `Icons.Default.DeleteForever`, uniquement visible si liste non vide).
- [x] T7.4 — État vide : icône `Icons.Default.Delete` + texte "Votre corbeille est vide" centrés.
- [x] T7.5 — Liste non vide : `LazyColumn` de `TrashEntryRow` affichant nom, date (`dd/MM/yyyy`), jours restants.
- [x] T7.6 — Chaque `TrashEntryRow` a un bouton "Restaurer" (icône `Icons.Default.Restore`, amber) et un bouton "Supprimer" (icône `Icons.Default.DeleteForever`, rouge).
- [x] T7.7 — Dialogue de confirmation pour "Supprimer définitivement" et "Vider la corbeille" (`AlertDialog` avec bouton destructif rouge).
- [x] T7.8 — Snackbar de confirmation après restauration : "Fichier restauré".

### T8 — Navigation
- [x] T8.1 — Enregistrer `composable<TrashRoute>` dans `NavHost.kt` (lancement depuis l'Explorateur, pas de bottom nav).
- [x] T8.2 — Passer un lambda `onNavigateToTrash: () -> Unit` à `ExplorerScreen` et câbler dans le NavHost.

### T9 — Tests
- [x] T9.1 — `TrashViewModelTest` : `restoreEntry()` appelle `restoreFromTrash()`, `permanentlyDelete()` appelle `permanentlyDelete()`, `emptyTrash()` appelle `emptyTrash()`.
- [x] T9.2 — `ExplorerViewModelTrashTest` : `moveToTrash()` appelle `moveToTrash()` + émet `undoEvent`, `undoMoveToTrash()` appelle `restoreFromTrash()`.
- [x] T9.3 — Tests écrits (compilation bloquée par erreurs pré-existantes de la Story 13.1 — identique à la Story 13.4, non introduit par cette story).

---

## Dev Notes

### Architecture & conventions obligatoires
- **Package** : les nouveaux fichiers de présentation vont dans `app/src/main/kotlin/com/mobicloud/presentation/trash/`.
- **Repository pattern** : toute logique de persistence dans `CatalogRepositoryImpl` (module `:app` → `data/repository/`). Aucun appel DAO direct dans les ViewModels.
- **Dispatchers.IO** : toutes les suspending functions du Repository sont exécutées avec `withContext(ioDispatcher)`.
- **StateFlow pattern** : `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())`.
- **Result<Unit>** : les mutations retournent `Result<Unit>`, les Flow ne retournent pas Result.

### Fichiers à modifier (UPDATE)
| Fichier | Changement |
|---|---|
| `CatalogEntryEntity.kt` | +2 colonnes Room |
| `CatalogDatabase.kt` | version 17→18 + Migration |
| `CatalogDao.kt` | +7 nouvelles requêtes |
| `CatalogEntry.kt` (domain) | +2 champs locaux uniquement |
| `CatalogRepository.kt` (interface) | +7 méthodes |
| `CatalogRepositoryImpl.kt` | +7 implémentations |
| `ExplorerViewModel.kt` | getAllEntries → getActive + moveToTrash + undoMoveToTrash |
| `ExplorerScreen.kt` | SwipeToDismissBox + TopAppBar icône corbeille |
| `NavHost.kt` | +composable<TrashRoute> |

### Fichiers à créer (NEW)
| Fichier | Description |
|---|---|
| `presentation/trash/TrashViewModel.kt` | HiltViewModel corbeille |
| `presentation/trash/TrashScreen.kt` | UI corbeille + TrashRoute |

### Champs locaux uniquement — ne pas propager dans le DHT
`isInTrash` et `deletedAt` sont des métadonnées locales. `CatalogEntry` est sérialisé en Protobuf pour le réseau DHT (`@ProtoNumber`). Ces 2 champs NE doivent PAS avoir de `@ProtoNumber` — ils existent uniquement pour l'affichage local et ne partent jamais dans le Gossip.

### Migration Room : ordre impératif
```kotlin
// Dans CatalogDatabase, ajouter avant .build() :
.addMigrations(MIGRATION_17_18)
```
Companion object dans `CatalogDatabase` :
```kotlin
val MIGRATION_17_18 = Migration(17, 18) { db ->
    db.execSQL("ALTER TABLE catalog_entry ADD COLUMN is_in_trash INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE catalog_entry ADD COLUMN deleted_at INTEGER DEFAULT NULL")
}
```

### SwipeToDismissBox (Material3 ≥ 1.2)
```kotlin
val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            viewModel.moveToTrash(entry.fileHash)
            true
        } else false
    }
)
SwipeToDismissBox(
    state = dismissState,
    backgroundContent = {
        Box(Modifier.fillMaxSize().background(Color(0xFFFF3333)).padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
        }
    }
) {
    CatalogEntryCard(entry = entry, onDownload = { ... })
}
```
**Attention** : utiliser `key = entry.fileHash` dans `LazyColumn` pour éviter la récomposition des états Dismiss.

### UX — Thème OLED
- Fond : `Color.Black` (`#000000`)
- Accent restauration : `Color(0xFFFFB300)` (amber)
- Accent suppression : `Color(0xFFFF3333)` (rouge)
- Police monospace pour `fileHash` en mode expert (non applicable ici sauf tooltip optionnel)

### TTL 30 jours
```kotlin
private val TTL_MS = 30L * 24 * 60 * 60 * 1000
val expiryTs = System.currentTimeMillis() - TTL_MS
```

### Localisation (vouvoiement)
Tous les textes utilisateurs utilisent le vouvoiement ("Votre corbeille", "Déplacé vers la corbeille", "Restaurer", "Supprimer définitivement", "Vider la corbeille").

### CatalogRepositoryImpl — localisation
Le fichier se trouve probablement dans `app/src/main/kotlin/com/mobicloud/data/repository/CatalogRepositoryImpl.kt`. Lire ce fichier avant toute modification pour comprendre la structure actuelle.

---

## Dev Agent Record

### Debug Log
- `compileDebugKotlin` : BUILD SUCCESSFUL (186 tasks, 0 erreur Kotlin)
- `compileDebugUnitTestKotlin` : échec sur erreurs pré-existantes (DashboardViewModelTest, JoinIntegrationTest, MemberLivenessNfrTest, MonitorMemberLivenessUseCaseTest, ProcessJoinRequestUseCaseTest) — identique à Story 13.4, non introduit par cette story
- Room migration : MIGRATION_17_18 ajoutée dans IdentityModule + CatalogDatabase companion
- SwipeToDismissBox : utilisation de `rememberSwipeToDismissBoxState` avec `confirmValueChange` (deprecated dans Material3 récent mais fonctionnel — warning uniquement, identique au reste du codebase)

### Completion Notes
Story 13.2 implémentée intégralement :
- **T1** : Migration Room v17→v18 — colonnes `is_in_trash` + `deleted_at` sur `catalog_entry`, enregistrée dans `IdentityModule`
- **T2** : 7 nouvelles requêtes DAO — `getAllActiveEntriesFlow`, `getDeletedEntriesFlow`, `softDeleteEntry`, `restoreEntry`, `permanentlyDeleteEntry`, `getExpiredEntries`, `purgeExpiredEntries`, `emptyTrash`
- **T3** : Champs `isInTrash`/`deletedAt` dans `CatalogEntry` (sans ProtoNumber), mappers bidirectionnels, 7 méthodes dans `CatalogRepository` + `CatalogRepositoryImpl`
- **T4** : `ExplorerViewModel` — `getActiveEntriesFlow()` (AC-10), `moveToTrash()`, `undoMoveToTrash()`, `undoEvent: SharedFlow<String>` ; `ExplorerScreen` — `TopAppBar` amber + `SwipeToDismissBox` fond rouge + snackbar "Annuler"
- **T5** : Purge TTL 30j dans `init {}` de `ExplorerViewModel`
- **T6** : `TrashViewModel` avec `deletedEntries`, `restoreEntry`, `permanentlyDelete`, `emptyTrash`
- **T7** : `TrashScreen` — état vide, liste `LazyColumn`, `TrashEntryRow` (nom + date + jours restants + boutons amber/rouge), dialogues de confirmation, snackbar restauration
- **T8** : `TrashRoute` dans `NavHost`, `onNavigateToTrash` câblé dans `ExplorerScreen`
- **T9** : `TrashViewModelTest` + `ExplorerViewModelTrashTest` écrits ; compilation bloquée par erreurs pré-existantes 13.1

---

## File List
- `app/src/main/kotlin/com/mobicloud/data/local/entity/CatalogEntryEntity.kt` (modifié — +2 colonnes)
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt` (modifié — version 17→18 + MIGRATION_17_18)
- `app/src/main/kotlin/com/mobicloud/di/IdentityModule.kt` (modifié — +MIGRATION_17_18)
- `app/src/main/kotlin/com/mobicloud/data/local/dao/CatalogDao.kt` (modifié — +7 requêtes corbeille)
- `app/src/main/kotlin/com/mobicloud/domain/models/CatalogEntry.kt` (modifié — +isInTrash, +deletedAt)
- `app/src/main/kotlin/com/mobicloud/domain/repository/CatalogRepository.kt` (modifié — +7 méthodes)
- `app/src/main/kotlin/com/mobicloud/data/repository_impl/CatalogRepositoryImpl.kt` (modifié — mappers + 7 implémentations)
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt` (modifié — getActive, moveToTrash, undoMoveToTrash, undoEvent, init{purge})
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt` (modifié — TopAppBar, SwipeToDismissBox, snackbar annuler, onNavigateToTrash)
- `app/src/main/kotlin/com/mobicloud/presentation/trash/TrashViewModel.kt` (créé)
- `app/src/main/kotlin/com/mobicloud/presentation/trash/TrashScreen.kt` (créé)
- `app/src/main/kotlin/com/mobicloud/navigation/NavHost.kt` (modifié — +TrashRoute)
- `app/src/test/kotlin/com/mobicloud/presentation/trash/TrashViewModelTest.kt` (créé)
- `app/src/test/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModelTrashTest.kt` (créé)

## Change Log
| Date | Version | Description |
|---|---|---|
| 2026-05-14 | 1.0 | Création de la story |
| 2026-05-14 | 1.1 | Implémentation complète — migration Room v17→v18, DAO corbeille, domain, ExplorerScreen swipe-to-delete, TrashViewModel, TrashScreen, navigation, tests |

---

### Review Findings

> Code review adversarial — 3 couches (Blind Hunter · Edge Case Hunter · Acceptance Auditor) — 2026-05-14

**`decision_needed` (1)**

- [x] [Review][Decision] **F4 — AC-8 PARTIEL : purge TTL non déclenchée à l'ouverture de `TrashScreen`** — Résolu (B) : `viewModelScope.launch { catalogRepository.purgeExpired() }` ajouté dans `TrashViewModel.init {}`. [`TrashViewModel.kt`]

**`patch` (3)**

- [x] [Review][Patch] **F1 — `moveToTrash()` émet `undoEvent` sans vérifier le `Result`** [`ExplorerViewModel.kt`] — ✅ Corrigé : `catalogRepository.moveToTrash(fileHash).onSuccess { _undoEvent.emit(fileHash) }`.
- [x] [Review][Patch] **F2 — `confirmDeleteHash!!` force-unwrap dans AlertDialog** [`TrashScreen.kt`] — ✅ Corrigé : `confirmDeleteHash?.let { hashToDelete -> ... viewModel.permanentlyDelete(hashToDelete) }`.
- [x] [Review][Patch] **F8 — `restoreEntry()` / `permanentlyDelete()` ignorent `Result<Unit>` dans `TrashViewModel`** [`TrashViewModel.kt`] — ✅ Corrigé : `errorEvent: SharedFlow<String>` exposé, `onFailure { _errorEvent.emit(...) }` sur les deux méthodes.

**`defer` (2)**

- [x] [Review][Defer] **F3 — Snackbar "Fichier restauré" optimiste avant confirmation DB** [`TrashScreen.kt`] — deferred, UX optimiste acceptable pour MVP.
- [x] [Review][Defer] **F6 — `undoEvent` extraBufferCapacity=1 — pré-existant** [`ExplorerViewModel.kt`] — deferred, déjà loggué W-13.3-P8 dans deferred-work.md.
