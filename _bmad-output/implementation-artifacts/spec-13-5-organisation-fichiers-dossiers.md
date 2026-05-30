---
title: '13-5 Organisation des fichiers en dossiers'
type: 'feature'
created: '2026-05-30'
status: 'in-progress'
baseline_commit: 'c62e0e6244e456f519bfa4ab9c0cd947f6371a61'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** L'explorateur MobiCloud liste tous les fichiers à plat — aucune organisation possible. Les utilisateurs ne peuvent pas regrouper leurs fichiers par projet, thème ou usage.

**Approach:** Ajouter une colonne `folder_path TEXT DEFAULT NULL` sur `catalog_entry` (locale, non distribuée dans le DHT). Un dossier est implicite : il existe dès qu'un fichier y est assigné et disparaît quand il est vide. UI : dossiers affichés en ligne horizontale au-dessus des fichiers racine ; long-press sur un fichier → bottom sheet "Déplacer vers…" avec liste des dossiers existants + "Nouveau dossier".

## Boundaries & Constraints

**Always:**
- `folder_path` est un champ **local uniquement** (comme `is_in_trash`) — jamais sérialisé en Protobuf, jamais propagé par Gossip/DHT.
- Un seul niveau de profondeur (pas de sous-dossiers).
- Suppression d'un dossier = déplacer tous ses fichiers vers la racine (folder_path → NULL) ; aucun fichier ne peut être perdu.
- Le filtre catégorie (All/Images/etc.) reste fonctionnel à l'intérieur d'un dossier ouvert.
- Créer un dossier = saisir un nom + assigner au moins le fichier courant (pas de dossier vide persisté).

**Ask First:**
- Si renommer un dossier provoque un conflit de nom avec un dossier existant, HALT et demander confirmation de fusion.

**Never:**
- Pas de sous-dossiers imbriqués.
- Ne pas modifier la structure Protobuf de `CatalogEntry` (pas de `@ProtoNumber` sur `folderPath`).
- Ne pas distribuer `folder_path` via Gossip ou DHT — c'est une métadonnée de vue locale.
- Ne pas remplacer le swipe-to-delete existant.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Move fichier → dossier existant | fileHash valide, folderName existant | `folder_path` mis à jour en DB ; fichier disparaît de la vue racine et apparaît dans le dossier | Erreur DB → snackbar "Déplacement échoué" |
| Move fichier → nouveau dossier | fileHash valide, nouveau nom non vide | Dossier créé implicitement ; fichier déplacé | Nom vide → bouton "Valider" désactivé |
| Move fichier → racine | fileHash valide, folderPath = null | `folder_path = NULL` ; fichier réapparaît à la racine | Erreur DB → snackbar |
| Supprimer un dossier | folderPath non vide | Tous ses fichiers → NULL ; dossier disparaît de la liste | Erreur DB → snackbar "Suppression échouée" |
| Renommer un dossier | oldPath, newPath non vide, pas de conflit | UPDATE masse ; dossier renommé dans la liste | Conflit de nom → HALT demander confirmation de fusion |
| Ouvrir dossier | tap sur FolderItem | currentFolder = name ; liste filtrée sur ce dossier | — |
| Retour racine | bouton retour ou breadcrumb | currentFolder = null ; vue racine restaurée | — |
| Dossier devient vide | dernier fichier déplacé ailleurs | Le dossier disparaît de la liste (plus de rows avec ce folder_path) | — |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/com/mobicloud/data/local/entity/CatalogEntryEntity.kt` — entité Room ; ajouter `folderPath: String? = null`
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt` — migration 19→20 ; bump `CURRENT_VERSION = 20`
- `app/src/main/kotlin/com/mobicloud/data/local/dao/CatalogDao.kt` — ajouter 4 requêtes folder
- `app/src/main/kotlin/com/mobicloud/domain/models/CatalogEntry.kt` — ajouter `folderPath: String? = null` (local uniquement)
- `app/src/main/kotlin/com/mobicloud/domain/repository/CatalogRepository.kt` — ajouter 4 méthodes folder
- `app/src/main/kotlin/com/mobicloud/data/repository_impl/CatalogRepositoryImpl.kt` — implémenter + mapper
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerViewModel.kt` — état `currentFolder`, `folders`, 4 actions
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt` — row de dossiers, breadcrumb, long-press
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/CatalogEntryCard.kt` — ajouter `onLongClick: (() -> Unit)? = null`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/FolderItem.kt` — **NOUVEAU** composant carte dossier
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/components/MoveToFolderSheet.kt` — **NOUVEAU** bottom sheet déplacement

## Tasks & Acceptance

**Execution:**

- [ ] `data/local/entity/CatalogEntryEntity.kt` -- Ajouter `@ColumnInfo(name = "folder_path", defaultValue = "NULL") val folderPath: String? = null` -- champ local non distribué
- [ ] `data/local/CatalogDatabase.kt` -- Ajouter `MIGRATION_19_20` (`ALTER TABLE catalog_entry ADD COLUMN folder_path TEXT DEFAULT NULL`) et bumper `CURRENT_VERSION = 20` -- schéma DB cohérent
- [ ] `data/local/dao/CatalogDao.kt` -- Ajouter : `updateFolderPath(hash, folder)`, `getActiveFolderNamesFlow(): Flow<List<String>>` (DISTINCT folder_path WHERE is_in_trash=0 AND folder_path NOT NULL), `renameFolder(old, new)`, `moveFilesToRoot(folderPath)` -- opérations CRUD folder
- [ ] `domain/models/CatalogEntry.kt` -- Ajouter `val folderPath: String? = null` sans `@ProtoNumber` -- champ view-local
- [ ] `domain/repository/CatalogRepository.kt` -- Ajouter : `moveFileToFolder(fileHash, folderPath)`, `getActiveFolderNamesFlow()`, `renameFolder(old, new)`, `deleteFolder(folderPath)` -- contrat Clean Architecture
- [ ] `data/repository_impl/CatalogRepositoryImpl.kt` -- Implémenter les 4 méthodes ; mettre à jour `CatalogEntry.toEntity()` pour inclure `folderPath` -- mapper complet
- [ ] `presentation/explorer/ExplorerViewModel.kt` -- Ajouter `currentFolder: MutableStateFlow<String?>(null)`, `folders: StateFlow<List<String>>`, modifier `catalogEntries` pour filtrer sur `currentFolder` ; ajouter `navigateIntoFolder(name)`, `navigateToRoot()`, `moveFileToFolder(fileHash, folder?)`, `renameFolder(old, new)`, `deleteFolder(name)` -- logique de navigation et d'édition
- [ ] `presentation/explorer/components/FolderItem.kt` -- Créer composant `FolderItem(name, fileCount, onClick, onLongClick)` avec icône dossier jaune/orange, nom, nombre de fichiers, badge count ; long-press → callback pour menu contextuel (renommer/supprimer) -- composant réutilisable
- [ ] `presentation/explorer/components/MoveToFolderSheet.kt` -- Créer `MoveToFolderSheet(folders, onMoveToFolder, onMoveToRoot, onDismiss)` avec liste des dossiers existants + entrée "Nouveau dossier" (TextField + bouton Valider) -- BottomSheet Material3
- [ ] `presentation/explorer/components/CatalogEntryCard.kt` -- Ajouter `onLongClick: (() -> Unit)? = null` ; wrapper le contenu avec `combinedClickable` si `onLongClick != null` -- déclencheur long-press
- [ ] `presentation/explorer/ExplorerScreen.kt` -- (1) Afficher `LazyRow` de `FolderItem` au-dessus des fichiers quand `currentFolder == null` et `folders.isNotEmpty()` ; (2) Afficher breadcrumb `"/ $currentFolder"` + bouton retour quand `currentFolder != null` ; (3) Passer `onLongClick = { selectedEntry = entry; showMoveSheet = true }` à chaque `CatalogEntryCard` ; (4) Afficher `MoveToFolderSheet` quand `showMoveSheet` ; (5) Long-press sur `FolderItem` → `AlertDialog` avec actions Renommer/Supprimer -- navigation complète + intégration

**Acceptance Criteria:**

- Given un fichier à la racine, when long-press + sélection d'un dossier existant dans le sheet, then le fichier disparaît de la liste racine et apparaît dans ce dossier.
- Given un fichier, when long-press + "Nouveau dossier" avec nom non vide + Valider, then le dossier apparaît dans la LazyRow et le fichier y est assigné.
- Given un dossier ouvert, when tap sur bouton retour ou breadcrumb, then retour à la vue racine avec tous les dossiers et fichiers racine.
- Given un dossier, when long-press sur son FolderItem + "Supprimer", then tous ses fichiers retournent à la racine et le dossier disparaît.
- Given un dossier, when long-press + "Renommer" + nouveau nom non conflit + Valider, then le dossier est renommé dans la LazyRow.
- Given tous les fichiers d'un dossier déplacés ailleurs, then le dossier disparaît automatiquement de la LazyRow (plus de rows avec ce folder_path).
- Given une DB ancienne (version 19), when migration 19→20 exécutée, then `folder_path = NULL` pour toutes les entrées existantes — aucun fichier perdu.
- Given un fichier en dossier, when synchronisation Gossip reçue d'un autre nœud, then `folder_path` local n'est pas écrasé (Gossip ne touche pas ce champ).

## Design Notes

**Dossiers implicites :** Pas de table `folders` dédiée. Un dossier existe ssi `COUNT(*) WHERE folder_path = name AND is_in_trash = 0 > 0`. Avantage : zero orphan cleanup. Inconvénient : impossible d'avoir un dossier vide → acceptable pour la soutenance.

**Mapper CatalogRepositoryImpl :** `folderPath` est exclu de la sérialisation Protobuf. Lors d'un `insertEntry` (Gossip entrant), l'entité existante en DB garde son `folder_path` courant — le mapper doit lire l'entité existante avant REPLACE, ou utiliser `INSERT OR IGNORE` + `UPDATE` séparé. **Approche retenue :** dans `insertEntry`, après upsert Protobuf, NE PAS écraser `folder_path` — utiliser `updateCatalogEntryOnly` qui ne touche que les champs de l'entité passée ; `folderPath` de l'entité entrante = null → le mapper doit copier le `folderPath` existant depuis la DB avant le REPLACE.

**Alternative simple :** Ajouter une requête `UPDATE catalog_entry SET ... WHERE file_hash = :hash` qui ne touche que les colonnes Protobuf, laissant `folder_path` intact. Voir `updateCatalogEntryOnly` déjà présent dans `CatalogDao`.

## Verification

**Commands:**
- `./gradlew :app:compileDebugKotlin` -- expected: BUILD SUCCESSFUL, zéro erreur de compilation
- `./gradlew :app:testDebugUnitTest` -- expected: tous les tests existants passent

**Manual checks (if no CLI):**
- Vérifier dans Android Studio que `CatalogDatabase.CURRENT_VERSION = 20` et que `MIGRATION_19_20` est enregistrée dans `databaseBuilder(...).addMigrations(...)`
- Vérifier sur émulateur : long-press fichier → sheet apparaît → créer dossier "Test" → fichier déplacé → naviguer dans dossier → retour racine → long-press FolderItem → Supprimer → fichier retourne à la racine

## Spec Change Log
