# Story 2.1: Modèle DHT Partitionné "Zero-Knowledge" (CRDT) & Jetpack Room FR-05.1

**Story ID:** 2.1
**Story Key:** 2-1-modele-dht-partitionne-crdt-jetpack-room-fr-05-1
**Epic:** 2 (Catalogue Distribué P2P)
**Status:** in-progress

## Story

**As a** Nœud du réseau,
**I want** de posséder un registre local persistant structuré comme une Table de Hachage Distribuée (DHT) strictement orientée "machine" (Zero-Knowledge),
**so that** je NE stocke QUE ma partition locale en agissant comme un "disque dur aveugle" (fichiers personnels + fiches de routage anonymes dont je suis responsable DHT), préservant ainsi la confidentialité totale et évitant la saturation globale.

## Acceptance Criteria

1. **Given** une nouvelle fiche de catalogue générée pour le stockage,
2. **When** elle est modélisée (`CatalogEntry`) et sauvegardée en base,
3. **Then** elle ne contient AUCUNE métadonnée en clair (ni nom de fichier, ni taille lisible, ni extension). Elle se limite strictement aux métadonnées de routage (`FileHash`, `OwnerPubKeyHash`, `VersionClock` pour le CRDT, et la liste des `FragmentLocations`).
4. **Given** l'application recevant un delta de catalogue (CRDT),
5. **When** une fiche "Fichier" dont le hachage ne correspond pas à ma partition locale est reçue via Gossip,
6. **Then** la BDD Room rejette l'insertion silencieusement pour préserver mon espace de stockage.
7. **And** les conflits locaux de fiches légitimes (deux nœuds ayant des versions différentes du même `FileHash`) sont résolus par fusion CRDT commutative (Last-Writer-Wins basé sur l'horloge logique `VersionClock`).
8. **And** les fiches sont stockées localement via Jetpack Room dans des tables en `snake_case`.
9. **And** toutes les opérations Room sont sécurisées via `Dispatchers.IO` et encapsulées dans des `Result<T>`.
10. **And** le système intègre la logique pour vérifier si un ID P2P tombe dans la plage assignée `[ID_DHT, ID_Successeur[` sur l'anneau DHT.

## Tasks / Subtasks

- [x] **Task 1: Définir les modèles du domaine pour le Catalogue Zero-Knowledge**
  - [x] Créer le modèle `CatalogEntry` et `FragmentLocation` dans `domain/models/`. S'assurer de l'absence de champs textuels humains.
  - [x] Les classes Kotlinx Serialization Protobuf doivent inclure `ignoreUnknownKeys = true` pour tolérer un futur décalage de version de la structure Gossip.
- [x] **Task 2: Définir les entités et DAO Room dans la couche Data**
  - [x] Modéliser les entités Room (`@Entity`) avec la convention `snake_case` (ex: `catalog_entry`, `fragment_location`).
  - [x] Implémenter les méthodes de recherche avec requêtes "zéro-latence" observables en Kotlin Flow (< 100ms local).
  - [x] Gérer le stockage des types complexes via des `TypeConverter`s.
- [x] **Task 3: Développer le cas d'usage de fusion CRDT**
  - [x] Dans `domain/usecase/m05_dht_catalog/`, créer `MergeCatalogEntriesUseCase`.
  - [x] Concevoir la fusion LWW (Last-Writer-Wins) avec l'horloge logique de versionnage qui gère commutativement la synchronisation asynchrone des fiches et fragments.
- [x] **Task 4: Implémenter le calcul de la plage sur l'anneau DHT**
  - [x] Ajouter un service qui détermine le partitionnement P2P logique et identifie la couverture d'un ID.
- [x] **Task 5: Tests unitaires de la persistance locale et fusion CRDT**
  - [x] Créer `CatalogDaoTest` pour tester les opérations basiques de Room (Insertion, Récupération, Flow).
  - [x] Écrire des tests pour `MergeCatalogEntriesUseCaseTest` validant l'horloge logique et la structure CRDT commutative.
  - [x] Rédiger les tests de calcul de partitionnement dans l'anneau (`CalculateDhtRangeUseCaseTest`).

## Developer Context & Guardrails

### 🏗 Architecture Compliance (CRITICAL)
- **Zero-Knowledge Strict :** Interdiction absolue de stocker des métadonnées lisibles par l'humain. Le nœud est un disque dur aveugle.
- **Séparation Clean Architecture:** Les classes du catalogue P2P doivent résider dans `domain/models` indépendamment d'Android.
- **Jetpack Room Isolée:** Restreindre la BDD à la couche `data/local`. La couche de présentation (UI) n'y accède qu'indirectement en observant des Flows du Domain.
- **ZÉRO Exception Silencieuse:** Toute DAO Room doit être invoquée dans `Dispatchers.IO` au travers de blocs try/catch, retournant des résultats explicites asynchrones (`Result<T>`).

### ⚙️ Technical Guidelines
> [!IMPORTANT]
> - Utiliser strictement la convention de nommage Base de Données: Annotations Room en `tableName = "catalog_entry"`. Le schéma SQL sous-jacent ne doit avoir aucun CamelCase.
> - Utilisation de Protobuf v1.10.x. N'oubliez pas les annotations de configuration `@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)` s'il y a lieu.

### 📂 File Structure Requirements
Localisation probable pour les créations/éditions :
- `app/src/main/java/com/mobicloud/domain/models/CatalogEntry.kt`
- `app/src/main/java/com/mobicloud/domain/usecase/m05_dht_catalog/MergeCatalogEntriesUseCase.kt`
- `app/src/main/java/com/mobicloud/data/local/dao/CatalogDao.kt`
- `app/src/main/java/com/mobicloud/data/local/entity/CatalogEntryEntity.kt`
- `app/src/main/java/com/mobicloud/data/repository_impl/CatalogRepositoryImpl.kt`

### 🧪 Testing Requirements
- Valider la commutativité: `A ∪ B == B ∪ A`. MockK + Room in-memory.

## Reference Intelligence
- `description_technique_formelle.md#Module 5` pour plus de contexte sur les propriétés Gossip et l'horloge logique CRDT.
- `architecture.md#Module 5` détaille les filtres de Bloom qui complèteront plus tard cette Story dans le pipeline Gossip.

## File List
- `app/src/main/kotlin/com/mobicloud/domain/models/CatalogEntry.kt`
- `app/src/main/kotlin/com/mobicloud/domain/models/FragmentLocation.kt`
- `app/src/test/kotlin/com/mobicloud/domain/models/CatalogEntryTest.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/CatalogEntryEntity.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/FragmentLocationEntity.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/entity/CatalogEntryWithFragments.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/dao/CatalogDao.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/Converters.kt`
- `app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt`
- `app/src/androidTest/kotlin/com/mobicloud/data/local/dao/CatalogDaoTest.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/MergeCatalogEntriesUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/MergeCatalogEntriesUseCaseTest.kt`
- `app/src/main/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/CalculateDhtRangeUseCase.kt`
- `app/src/test/kotlin/com/mobicloud/domain/usecase/m05_dht_catalog/CalculateDhtRangeUseCaseTest.kt`

## Dev Agent Record

### Debug Log
- 

### Completion Notes
- Task 1: Création des modèles `CatalogEntry` et `FragmentLocation` selon le paradigme Zero-Knowledge avec Protobuf. Un test unitaire de sérialisation `testSerializationIgnoresUnknownKeys` démontre la tolérance de Protobuf à de futures évolutions du protocole Gossip (simulant un comportement similaire à `ignoreUnknownKeys = true`).
- Task 2: Modélisation des entités `CatalogEntryEntity` et `FragmentLocationEntity` avec contraintes `snake_case` et @Relation Room. Implémentation de `CatalogDao` avec Flow observables et TypeConverters pour le stockage `List<String>`. Tests rédigés dans `CatalogDaoTest`.
- Task 3: Création de `MergeCatalogEntriesUseCase` implémentant CRDT LWW. Validé avec `MergeCatalogEntriesUseCaseTest` pour garantir l'idempotence et la commutativité (A ∪ B == B ∪ A).
- Task 4: Implémentation du calcul de couverture d'anneau `[nodeId, successorId)` gérant le débordement d'anneau (wrap-around) dans `CalculateDhtRangeUseCase`.
- Task 5: Ensemble des tests unitaires (couche Data/Room, logique CRDT de l'UseCase, calcul DHT) complétés en vert selon les critères stricts d'acceptation. Story de Développement TDD complètement achevée.

## Change Log
- 

