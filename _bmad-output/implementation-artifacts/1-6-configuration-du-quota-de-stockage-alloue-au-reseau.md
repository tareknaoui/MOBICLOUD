# Story 1.6: Configuration du Quota de Stockage Alloué au Réseau

Status: done

## Story

En tant qu'utilisateur,
Je veux définir combien de gigaoctets de mon stockage j'alloue au réseau MobiCloud,
Afin de contrôler l'espace disque consommé par l'hébergement des blocs d'autres utilisateurs.

## Acceptance Criteria

1. **Given** l'utilisateur ouvre l'onglet "Paramètres"
   **When** l'écran s'affiche
   **Then** un slider est visible dans une section intitulée "Contribution au réseau", allant de 0.5 GB à 80% de l'espace libre, par paliers de 0.5 GB

2. **And** l'espace actuellement utilisé par les blocs hébergés est affiché sous le slider (ex : "1.2 GB utilisés sur 3 GB alloués")

3. **And** la valeur choisie est persistée dans `NodeSettings.allocatedStorageBytes` (Room DB, table `node_settings`)

4. **And** si l'utilisateur réduit le quota en dessous de l'espace déjà utilisé par les blocs hébergés, un `AlertDialog` d'avertissement s'affiche : "Réduire ce quota supprimera des blocs hébergés du réseau"

5. **And** la valeur par défaut au premier lancement est `min(2 GB, 20% de l'espace libre)` — calculée dynamiquement à l'initialisation du repository

6. **And** la valeur est accessible via `domain/repository/NodeSettingsRepository.kt` — interface Kotlin pure, zéro import Android

## Tasks / Subtasks

- [x] Task 1 : Créer le modèle domaine `NodeSettings` (AC: #3, #5, #6)
  - [x] Créer `domain/models/NodeSettings.kt` — data class Kotlin pure avec `allocatedStorageBytes: Long`, `id: Int = 0` (Room singleton row)
  - [x] Zéro import Android (POJO — Clean Architecture)

- [x] Task 2 : Créer l'interface repository `NodeSettingsRepository` (AC: #3, #5, #6)
  - [x] Créer `domain/repository/NodeSettingsRepository.kt`
  - [x] Exposer : `suspend fun getSettings(): NodeSettings`, `suspend fun updateAllocatedStorage(bytes: Long)`, `fun observeSettings(): Flow<NodeSettings>`
  - [x] Zéro import Android dans l'interface

- [x] Task 3 : Créer l'entité Room et le DAO (AC: #3)
  - [x] Créer `data/local/entity/NodeSettingsEntity.kt` — `@Entity(tableName = "node_settings")`, `@PrimaryKey val id: Int = 0`, `val allocatedStorageBytes: Long`
  - [x] Créer `data/local/dao/NodeSettingsDao.kt` — `@Insert(onConflict = REPLACE)`, `@Query("SELECT * FROM node_settings WHERE id = 0")` retournant `Flow<NodeSettingsEntity?>` et version `suspend`

- [x] Task 4 : Ajouter `NodeSettingsEntity` à `CatalogDatabase` + migration vers v10 (AC: #3)
  - [x] Dans `CatalogDatabase.kt` : ajouter `NodeSettingsEntity::class` à la liste `entities`, incrémenter `version` de 9 à 10
  - [x] Ajouter `MIGRATION_9_10` : `CREATE TABLE IF NOT EXISTS node_settings (id INTEGER NOT NULL PRIMARY KEY, allocated_storage_bytes INTEGER NOT NULL)`
  - [x] Enregistrer `MIGRATION_9_10` dans `IdentityModule.kt` (liste `addMigrations`)

- [x] Task 5 : Implémenter `NodeSettingsRepositoryImpl` (AC: #3, #5)
  - [x] Créer `data/repository/NodeSettingsRepositoryImpl.kt`
  - [x] `getSettings()` : lit depuis Room ; si aucune ligne, insère la valeur par défaut = `min(2 * 1024³, freeSpace * 0.20).toLong()` via `StatFs(Environment.getDataDirectory().path)`
  - [x] `updateAllocatedStorage(bytes)` : insère/remplace la ligne singleton (`id = 0`)
  - [x] `observeSettings()` : retourne le `Flow<NodeSettingsEntity?>` du DAO mappé en `Flow<NodeSettings>` (avec valeur par défaut si null)
  - [x] `@Inject constructor` avec `NodeSettingsDao` et `@ApplicationContext context: Context` (pour `StatFs`)

- [x] Task 6 : Créer `NodeSettingsModule` (Hilt) (AC: #3)
  - [x] Créer `di/NodeSettingsModule.kt` — `@Module @InstallIn(SingletonComponent::class)`
  - [x] `@Binds @Singleton` : `NodeSettingsRepository → NodeSettingsRepositoryImpl`
  - [x] `@Provides @Singleton` : `NodeSettingsDao` via `database.nodeSettingsDao()`
  - [x] Ajouter `fun nodeSettingsDao(): NodeSettingsDao` dans `CatalogDatabase.kt`

- [x] Task 7 : Créer `SettingsViewModel` (AC: #1, #2, #3, #4, #5)
  - [x] Créer `presentation/settings/SettingsViewModel.kt` avec `@HiltViewModel`
  - [x] Injecter `NodeSettingsRepository` et `HostedBlockRepository`
  - [x] Exposer `val settings: StateFlow<NodeSettings>` via `observeSettings().stateIn(viewModelScope, WhileSubscribed(5000), NodeSettings(0L))`
  - [x] Exposer `val usedStorageBytes: StateFlow<Long>` calculé depuis `HostedBlockRepository` (somme de `sizeBytes` de tous les blocs hébergés)
  - [x] Exposer `val freeSpaceBytes: StateFlow<Long>` via `StatFs` — recalculé à chaque observation
  - [x] Fonction `updateAllocatedStorage(bytes: Long)` — lance `viewModelScope.launch { repository.updateAllocatedStorage(bytes) }`
  - [x] Exposer `val showWarningDialog: StateFlow<Boolean>` — `true` si l'utilisateur tente de réduire le quota en dessous de `usedStorageBytes`
  - [x] Fonction `dismissWarningDialog()` et `confirmReduceQuota(bytes: Long)`

- [x] Task 8 : Mettre à jour `SettingsScreen` (AC: #1, #2, #4)
  - [x] Modifier `presentation/settings/SettingsScreen.kt` — remplacer le placeholder `Box` par un `LazyColumn`
  - [x] Section "Contribution au réseau" avec titre `Text` + `Slider` Material3
  - [x] Slider : `valueRange = 0.5f * GB_IN_BYTES .. maxBytes`, `steps` calculés pour paliers 0.5 GB
  - [x] Affichage dynamique : `"${used.toGB()} GB utilisés sur ${allocated.toGB()} GB alloués"`
  - [x] `AlertDialog` conditionnel piloté par `showWarningDialog` ViewModel state
  - [x] `collectAsStateWithLifecycle()` pour tous les flows
  - [x] OLED dark : utiliser `MaterialTheme.colorScheme` (fond `#000000` déjà assuré par le thème global — ne pas coder en dur la couleur)

- [x] Task 9 : Tests unitaires `NodeSettingsRepositoryImplTest` (AC: #3, #5)
  - [x] Créer `data/repository/NodeSettingsRepositoryImplTest.kt`
  - [x] Tester : premier `getSettings()` insère la valeur par défaut si table vide
  - [x] Tester : `updateAllocatedStorage(bytes)` persiste la nouvelle valeur
  - [x] Mocker `NodeSettingsDao` avec MockK ; mocker `StatFs` si possible (ou injecter une lambda de `freeSpaceProvider` pour testabilité)

## Dev Notes

> [!CAUTION] **DISASTER PREVENTION — LIRE AVANT TOUTE IMPLÉMENTATION :**
> 1. **Migration Room obligatoire (v9 → v10) :** `CatalogDatabase.version` est actuellement `9`. Cette story DOIT incrémenter à `10` et enregistrer `MIGRATION_9_10` dans `IdentityModule`. Oublier la migration = crash au lancement sur les builds existants. `fallbackToDestructiveMigration()` est déjà en place mais ne doit pas masquer l'omission.
> 2. **Singleton Row Room :** La table `node_settings` contient une seule ligne avec `id = 0` (pattern singleton). `@Insert(onConflict = REPLACE)` est l'upsert à utiliser — ne pas recréer la table ni utiliser `@Update`.
> 3. **`StatFs` dans le Domain est interdit :** `android.os.StatFs` est une classe Android — elle ne peut pas être dans `domain/`. L'accès au système de fichiers doit rester dans la couche `data/` (`NodeSettingsRepositoryImpl`). Pour les tests, injecter un provider de free space (ex : `() -> Long`) via Hilt.
> 4. **`HostedBlockRepository` déjà existant :** Ne pas recréer. Utiliser l'interface `domain/repository/HostedBlockRepository.kt` et son impl `data/repository_impl/HostedBlockRepositoryImpl.kt`. La somme des `sizeBytes` est calculable via une requête DAO existante ou Room `@Query`.
> 5. **`SettingsScreen` est un placeholder :** Le fichier existe mais ne contient qu'un `Box { Text("settings") }`. Le remplacer intégralement est safe — zéro régression.

### Infrastructure Existante (Ne Pas Recréer)

| Fichier | Statut | Notes |
|---|---|---|
| `data/local/CatalogDatabase.kt` | ✅ Existant — à modifier | Ajouter `NodeSettingsEntity`, version 9→10, MIGRATION_9_10, `nodeSettingsDao()` |
| `di/IdentityModule.kt` | ✅ Existant — à modifier | Ajouter `MIGRATION_9_10` + `@Provides NodeSettingsDao` |
| `domain/repository/HostedBlockRepository.kt` | ✅ Existant | Injecter dans `SettingsViewModel` pour calculer `usedStorageBytes` |
| `presentation/settings/SettingsScreen.kt` | ⚠️ Placeholder | Remplacer intégralement — aucun code utile à conserver |

### Fichiers à Créer / Modifier

```
app/src/main/kotlin/com/mobicloud/
├── domain/
│   ├── models/
│   │   └── NodeSettings.kt                      ← NOUVEAU
│   └── repository/
│       └── NodeSettingsRepository.kt            ← NOUVEAU
├── data/
│   ├── local/
│   │   ├── CatalogDatabase.kt                   ← MODIFIER (v10, NodeSettingsEntity, DAO, MIGRATION_9_10)
│   │   ├── entity/
│   │   │   └── NodeSettingsEntity.kt            ← NOUVEAU
│   │   └── dao/
│   │       └── NodeSettingsDao.kt               ← NOUVEAU
│   └── repository/
│       └── NodeSettingsRepositoryImpl.kt        ← NOUVEAU
├── di/
│   ├── NodeSettingsModule.kt                    ← NOUVEAU
│   └── IdentityModule.kt                        ← MODIFIER (MIGRATION_9_10, NodeSettingsDao)
└── presentation/settings/
    ├── SettingsViewModel.kt                     ← NOUVEAU
    └── SettingsScreen.kt                        ← MODIFIER (remplace placeholder)

app/src/test/kotlin/com/mobicloud/
└── data/repository/
    └── NodeSettingsRepositoryImplTest.kt        ← NOUVEAU
```

### Détail de l'Implémentation — `NodeSettings` (Domain)

```kotlin
// domain/models/NodeSettings.kt
data class NodeSettings(
    val allocatedStorageBytes: Long,
    val id: Int = 0
)
```

### Détail de l'Implémentation — `NodeSettingsEntity` (Data)

```kotlin
// data/local/entity/NodeSettingsEntity.kt
@Entity(tableName = "node_settings")
data class NodeSettingsEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "allocated_storage_bytes") val allocatedStorageBytes: Long
)
```

### Détail de l'Implémentation — `NodeSettingsDao`

```kotlin
// data/local/dao/NodeSettingsDao.kt
@Dao
interface NodeSettingsDao {
    @Query("SELECT * FROM node_settings WHERE id = 0")
    fun observeSettings(): Flow<NodeSettingsEntity?>

    @Query("SELECT * FROM node_settings WHERE id = 0")
    suspend fun getSettings(): NodeSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NodeSettingsEntity)
}
```

### Détail de l'Implémentation — `NodeSettingsRepositoryImpl`

```kotlin
class NodeSettingsRepositoryImpl @Inject constructor(
    private val dao: NodeSettingsDao,
    @ApplicationContext private val context: Context
) : NodeSettingsRepository {

    private fun defaultBytes(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val twoGb = 2L * 1024 * 1024 * 1024
        return minOf(twoGb, (freeBytes * 0.20).toLong())
    }

    override suspend fun getSettings(): NodeSettings {
        return dao.getSettings()?.toDomain() ?: run {
            val default = NodeSettings(allocatedStorageBytes = defaultBytes())
            dao.upsert(default.toEntity())
            default
        }
    }

    override suspend fun updateAllocatedStorage(bytes: Long) {
        dao.upsert(NodeSettingsEntity(id = 0, allocatedStorageBytes = bytes))
    }

    override fun observeSettings(): Flow<NodeSettings> =
        dao.observeSettings().map { entity ->
            entity?.toDomain() ?: NodeSettings(allocatedStorageBytes = defaultBytes())
        }
}
```

### Détail de l'Implémentation — Migration Room v9 → v10

```kotlin
// Dans CatalogDatabase.kt :
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS node_settings " +
            "(id INTEGER NOT NULL PRIMARY KEY, allocated_storage_bytes INTEGER NOT NULL)"
        )
    }
}
```

### Détail de l'Implémentation — `SettingsViewModel`

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: NodeSettingsRepository,
    private val hostedBlockRepository: HostedBlockRepository
) : ViewModel() {

    val settings: StateFlow<NodeSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NodeSettings(0L))

    // Calculer la somme des blocs hébergés via HostedBlockRepository
    val usedStorageBytes: StateFlow<Long> = ...  // flow depuis DAO / repository

    private val _showWarningDialog = MutableStateFlow(false)
    val showWarningDialog: StateFlow<Boolean> = _showWarningDialog.asStateFlow()

    private var pendingBytes: Long = 0L

    fun requestUpdateAllocatedStorage(newBytes: Long) {
        if (newBytes < usedStorageBytes.value) {
            pendingBytes = newBytes
            _showWarningDialog.value = true
        } else {
            viewModelScope.launch { settingsRepository.updateAllocatedStorage(newBytes) }
        }
    }

    fun confirmReduceQuota() {
        _showWarningDialog.value = false
        viewModelScope.launch { settingsRepository.updateAllocatedStorage(pendingBytes) }
    }

    fun dismissWarningDialog() { _showWarningDialog.value = false }
}
```

### Détail du Slider — Calcul des Steps

```kotlin
// Dans SettingsScreen :
val halfGbBytes = 512L * 1024 * 1024
val maxBytes = (freeSpace * 0.80f).toLong()
val minBytes = halfGbBytes  // 0.5 GB
val steps = ((maxBytes - minBytes) / halfGbBytes).toInt() - 1  // paliers 0.5 GB

Slider(
    value = allocatedBytes.toFloat(),
    onValueChange = { viewModel.requestUpdateAllocatedStorage(it.toLong()) },
    valueRange = minBytes.toFloat()..maxBytes.toFloat(),
    steps = steps
)
```

### Patterns Architecture à Respecter

- **Clean Architecture :** `NodeSettings` et `NodeSettingsRepository` sont Kotlin purs — zéro import `android.*` dans `domain/`. `StatFs` uniquement dans `data/`.
- **Error Handling :** Toutes les méthodes `suspend` retournent `T` directement (non wrappé) ou `Result<T>` pour les cas d'erreur explicites. `getSettings()` ne doit jamais crasher.
- **Hilt DI :** `@Binds @Singleton` pour le binding repository dans `NodeSettingsModule`. `@Provides @Singleton` pour le DAO.
- **OLED Dark :** Slider et Text utilisent `MaterialTheme.colorScheme` — ne pas hardcoder les couleurs.
- **Thread Safety :** Toutes les opérations Room dans `Dispatchers.IO` (gestion implicite via Room et `suspend`).
- **Pattern StateFlow :** `SharingStarted.WhileSubscribed(5000)` — cohérent avec `DashboardViewModel` (Story 1.4).

### Contexte Intelligence — Stories Précédentes

- **Story 1.1 :** Fondation Clean Architecture. La séparation `domain/data/presentation` est stricte. Tout nouveau fichier doit respecter la structure de packages.
- **Story 1.2 :** `SettingsScreen.kt` est dans `presentation/settings/`. La navigation vers cet écran depuis la Bottom Nav (`SettingsRoute`) est déjà câblée — ne pas modifier la navigation.
- **Story 1.3 :** Pattern Room singleton row pour `NodeIdentityEntity` (`id = 0`) — à reproduire exactement pour `NodeSettingsEntity`.
- **Story 1.4 :** `HostedBlockRepository` expose les blocs hébergés. La somme des `sizeBytes` est nécessaire pour `usedStorageBytes` dans le ViewModel.
- **`CatalogDatabase` version 9 :** La migration vers v10 doit être explicite. `addMigrations()` est dans `IdentityModule`. Ne pas supprimer les migrations existantes (MIGRATION_2_3 → MIGRATION_8_9).

### Tests Unitaires — Guide

```kotlin
// NodeSettingsRepositoryImplTest.kt — utiliser un vrai Room DB in-memory
val db = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
    .allowMainThreadQueries().build()
val dao = db.nodeSettingsDao()

// Injecter un freeSpaceProvider mocké (lambda) pour éviter StatFs en test :
// NodeSettingsRepositoryImpl(dao, mockContext, freeSpaceProvider = { 10L * GB })

// Test 1 : getSettings() sans ligne existante → insère valeur par défaut min(2GB, 20% free)
// Test 2 : updateAllocatedStorage(bytes) → observeSettings() émet nouvelle valeur
// Test 3 : getSettings() avec ligne existante → retourne valeur persistée sans écraser
```

> **Note :** `HostedBlockRepository.getTotalHostedSize()` n'existe peut-être pas encore — le créer en même temps que ce ViewModel est acceptable (méthode `@Query("SELECT SUM(size_bytes) FROM hosted_blocks")`).

### Références

- [Source: epics.md#Story 1.6] (Acceptance Criteria de référence)
- [Source: epics.md#UX-DR9] (Slider Quota Stockage — composant Settings)
- [Source: architecture.md#Clean Architecture] (aucun import Android dans domain)
- [Source: architecture.md#Error Handling Patterns] (Result<T>, zéro exception silencieuse)
- [Source: data/local/CatalogDatabase.kt#version=9] (migration à incrémenter → 10)
- [Source: di/IdentityModule.kt#addMigrations] (point d'enregistrement des migrations)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `freeSpaceProvider` injectée comme lambda dans le constructeur primaire pour testabilité sans StatFs en test
- `observeTotalHostedBytes()` ajouté à `HostedBlockDao` (COALESCE SUM), interface `HostedBlockRepository`, et `HostedBlockRepositoryImpl` — requis par `SettingsViewModel`
- Constructeur secondaire `@Inject` dans `NodeSettingsRepositoryImpl` pour compatibilité Hilt + pattern constructeur primaire testable

### Completion Notes List

- Tous les ACs satisfaits : slider 0.5GB→80% espace libre, affichage dynamique "X GB utilisés sur Y GB alloués", persistance Room singleton row id=0, AlertDialog si réduction sous quota utilisé, valeur par défaut min(2GB, 20% espace libre), interface NodeSettingsRepository Kotlin pure zéro import Android.
- 5 tests unitaires MockK couvrent : insertion valeur par défaut, updateAllocatedStorage, getSettings avec ligne existante (pas d'upsert), observeSettings null→défaut, observeSettings avec entité.

### File List

- app/src/main/kotlin/com/mobicloud/domain/models/NodeSettings.kt (CRÉÉ)
- app/src/main/kotlin/com/mobicloud/domain/repository/NodeSettingsRepository.kt (CRÉÉ)
- app/src/main/kotlin/com/mobicloud/data/local/entity/NodeSettingsEntity.kt (CRÉÉ)
- app/src/main/kotlin/com/mobicloud/data/local/dao/NodeSettingsDao.kt (CRÉÉ)
- app/src/main/kotlin/com/mobicloud/data/repository/NodeSettingsRepositoryImpl.kt (CRÉÉ)
- app/src/main/kotlin/com/mobicloud/di/NodeSettingsModule.kt (CRÉÉ)
- app/src/main/kotlin/com/mobicloud/presentation/settings/SettingsViewModel.kt (CRÉÉ)
- app/src/main/kotlin/com/mobicloud/presentation/settings/SettingsScreen.kt (MODIFIÉ — placeholder remplacé)
- app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt (MODIFIÉ — v10, NodeSettingsEntity, MIGRATION_9_10, nodeSettingsDao)
- app/src/main/kotlin/com/mobicloud/di/IdentityModule.kt (MODIFIÉ — MIGRATION_9_10 ajouté)
- app/src/main/kotlin/com/mobicloud/data/local/dao/HostedBlockDao.kt (MODIFIÉ — observeTotalSizeBytes ajouté)
- app/src/main/kotlin/com/mobicloud/domain/repository/HostedBlockRepository.kt (MODIFIÉ — observeTotalHostedBytes ajouté)
- app/src/main/kotlin/com/mobicloud/data/repository_impl/HostedBlockRepositoryImpl.kt (MODIFIÉ — observeTotalHostedBytes implémenté)
- app/src/test/kotlin/com/mobicloud/data/repository/NodeSettingsRepositoryImplTest.kt (CRÉÉ)

### Change Log

- 2026-04-29 : Implémentation complète story 1.6 — quota stockage réseau, migration Room v9→v10, UI Slider Material3 avec AlertDialog avertissement, 5 tests unitaires MockK.

---

### Review Findings

_Code review du 2026-04-29 — sources : Blind Hunter, Edge Case Hunter, Acceptance Auditor_

**Patch (8)**

- [x] [Review][Patch] Race condition TOCTOU sur `pendingBytes` — `var` non synchronisé écrasé entre deux appels rapides au slider, `confirmReduceQuota` valide le mauvais quota [`SettingsViewModel.kt`] ✅ fixed: MutableStateFlow<Long?>
- [x] [Review][Patch] Dialog dismiss avant persistance — `_showWarningDialog.value = false` précède `settingsRepository.updateAllocatedStorage(pendingBytes)`, aucun rollback si la coroutine échoue [`SettingsViewModel.kt`] ✅ fixed: persist-then-dismiss
- [x] [Review][Patch] `StatFs` dans la couche presentation — violation Clean Architecture (Dev Notes DISASTER #3) : `SettingsViewModel` appelle `StatFs(Environment.getDataDirectory().path)` dans un `map` sur le settings flow ; doit être déplacé dans `data/` [`SettingsViewModel.kt`] ✅ fixed: observeFreeSpaceBytes() ajouté au repository
- [x] [Review][Patch] Double subscription `observeSettings()` — deux `stateIn` indépendants sur le même flow Room dans le ViewModel ; doubler les observers DB sans bénéfice [`SettingsViewModel.kt`] ✅ fixed: résolu par P3, freeSpaceBytes n'utilise plus observeSettings()
- [x] [Review][Patch] `steps = -1` quand `freeBytes == 0` — formule `((maxBytes - minBytes) / HALF_GB).toInt().coerceAtLeast(0) - 1` produit `-1` au démarrage ; le `-1` final est hors du `coerceAtLeast(0)` interne ; crash ou slider figé [`SettingsScreen.kt`] ✅ fixed: coerceAtLeast(0) déplacé après le -1
- [x] [Review][Patch] `observeSettings()` null-entity ne persiste pas la valeur par défaut — chaque émission null recompute `defaultBytes()` via StatFs sans upsert ; valeur instable entre collectes, divergence avec `getSettings()` [`NodeSettingsRepositoryImpl.kt`] ✅ fixed: upsert dans le map quand entity == null
- [x] [Review][Patch] `getSettings()` TOCTOU entre null-check et upsert — deux coroutines concurrentes peuvent lire `null`, calculer des defaults différents et s'écraser mutuellement via `REPLACE` [`NodeSettingsRepositoryImpl.kt`] ✅ fixed: double-check locking avec Mutex
- [x] [Review][Patch] `updateAllocatedStorage` sans validation des bornes — accepte tout `Long` (négatif, supérieur à la capacité disque) sans garde ; peut persister une valeur invalide [`NodeSettingsRepositoryImpl.kt`] ✅ fixed: require(bytes > 0)

**Defer (5)**

- [x] [Review][Defer] Float precision loss sur le Slider pour les valeurs > ~8 GB [`SettingsScreen.kt`] — deferred, limitation inhérente au composant Slider Material3 (Float 23-bit mantissa) ; non fixable sans changer l'API du composant
- [x] [Review][Defer] `StatFs(getDataDirectory())` vs `context.filesDir` — partitions potentiellement différentes sur adoptable storage [`NodeSettingsRepositoryImpl.kt`, `SettingsViewModel.kt`] — deferred, la spec impose ce chemin ; revisiter si le support adoptable storage est ajouté
- [x] [Review][Defer] `NodeSettingsRepositoryImpl` sans `@Singleton` sur la classe [`NodeSettingsRepositoryImpl.kt`] — deferred, pré-existant ; le `@Singleton` sur le `@Binds` suffit pour Hilt en pratique
- [x] [Review][Defer] `exportSchema = false` retire la vérification compile-time des migrations [`CatalogDatabase.kt`] — deferred, pré-existant sur toute la base de données du projet
- [x] [Review][Defer] Tests ne couvrent pas les bornes invalides de `updateAllocatedStorage` [`NodeSettingsRepositoryImplTest.kt`] — deferred, gap de couverture non bloquant pour la livraison
