# Story 1.3: Génération & Persistance de l'Identité Cryptographique (Keystore)

Status: done

## Dev Agent Record

### Agent Model Used
Gemini 3.1 Pro (High) / Claude Sonnet 4.6 (Thinking)

### Debug Log References
- `publicId` → `nodeId` renommage global effectué proprement via PowerShell sur tous les fichiers `.kt`.
- `BasicElectionUseCaseTest` : suppression des références à `generateHashcashUseCase` et `genesisHashcash` (arc Hashcash retiré en V4).
- `NodeIdentity` : ajout de `reliabilityScore: Float = 1.0f` conforme à la Story.
- `IdentityModule` : utilisation de deux `@Module` séparés (un `object` pour `@Provides`, un `abstract class` pour `@Binds`) — contrainte Hilt.

### Completion Notes List
- ✅ AC1 : Paire de clés EC P-256 générée et stockée dans l'Android Keystore System (hardware-backed via `KeyGenParameterSpec`).
- ✅ AC2 : Clé publique extraite et persistée dans Room DB via `NodeIdentityEntity` table `node_identity`.
- ✅ AC3 : `nodeId` dérivé via SHA-256 de la clé publique, 8 premiers bytes → string hex de 16 caract.
- ✅ AC4 : Au redémarrage, l'identité est lue depuis la BDD Room sans régénération.
- ✅ AC5 : La clé privée reste dans le TEE/KeyStore (non exportable). Vérification via `KeyInfo.isInsideSecureHardware`.
- ✅ AC6 : Accessible via `IdentityRepository.getIdentity()` (Clean Architecture respectée ; aucun import Android dans le Domain).
- ✅ Zéro exception silencieuse : toutes les opérations I/O sont enveloppées dans `runCatching`.
- ✅ Initialisation déclenchée depuis `MainActivityViewModel.init {}` (silencieuse, en arrière-plan).

### File List
- app/src/main/kotlin/com/mobicloud/domain/models/NodeIdentity.kt (modifié — `publicId` → `nodeId`, ajout `reliabilityScore`)
- app/src/main/kotlin/com/mobicloud/domain/repository/IdentityRepository.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/core/security/KeystoreManager.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/data/local/entity/NodeIdentityEntity.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/data/local/dao/IdentityDao.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/data/local/CatalogDatabase.kt (modifié — ajout `NodeIdentityEntity` + `identityDao()`)
- app/src/main/kotlin/com/mobicloud/data/repository_impl/IdentityRepositoryImpl.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/di/IdentityModule.kt (nouveau)
- app/src/main/kotlin/com/mobicloud/MainActivityViewModel.kt (modifié — injection `IdentityRepository` + init)
- app/src/test/kotlin/com/mobicloud/data/repository_impl/IdentityRepositoryImplTest.kt (nouveau)
- app/src/test/kotlin/com/mobicloud/core/security/KeystoreManagerTest.kt (nouveau)
- app/src/test/kotlin/com/mobicloud/domain/usecase/m10_election/BasicElectionUseCaseTest.kt (modifié — suppression refs hashcash)

### Change Log
- 2026-04-14: Implémentation complète Story 1.3 — Génération & Persistance de l'Identité Cryptographique (Keystore).
  Création de `IdentityRepository`, `KeystoreManager`, `NodeIdentityEntity`, `IdentityDao`, `IdentityRepositoryImpl`, `IdentityModule`.
  Refactoring `NodeIdentity` (`publicId` → `nodeId`, ajout `reliabilityScore`).
  Déclenchement de l'init identité dans `MainActivityViewModel`.
  Tests unitaires : 7 tests `IdentityRepositoryImplTest` + 4 tests `KeystoreManagerTest`.


## Story

As a nœud MobiCloud,
I want générer une paire de clés asymétriques persistée dans l'Android Keystore au premier démarrage,
so that je dispose d'une identité de confiance infalsifiable et anti-Sybil utilisable pour signer tous les messages P2P.

## Acceptance Criteria

1. **Given** l'application est lancée pour la première fois
   **When** le module d'identité initialise le nœud
   **Then** une paire de clés asymétriques (EC P-256) est générée et stockée dans l'Android Keystore System (hardware-backed).
2. **And** la clé publique est extraite et persistée localement (Room DB) comme `NodeIdentity.publicKeyBytes`.
3. **And** un `nodeId` unique est dérivé de la clé publique (hash SHA-256 tronqué à 8 bytes).
4. **And** au prochain démarrage, la clé existante est réutilisée (pas de régénération).
5. **And** la clé privée ne peut jamais être exportée hors du TEE/KeyStore (vérifiable par `isInsideSecureHardware`).
6. **And** le tout est accessible via l'interface `domain/repository/IdentityRepository.kt` (Clean Architecture).

## Tasks / Subtasks

- [x] Task 1: Modélisation de l'Identité (Domain Layer)
  - [x] Définir la data class `domain/models/NodeIdentity` (avec `nodeId` en String Hex, `publicKeyBytes` en ByteArray, et `reliabilityScore`).
  - [x] Définir l'interface `domain/repository/IdentityRepository.kt` exposant l'identité (ex: `suspend fun getIdentity(): Result<NodeIdentity>`).
- [x] Task 2: Interaction avec Android KeyStore (Core Security Layer)
  - [x] Créer `core/security/KeystoreManager.kt` pour gérer la génération et récupération de clés.
  - [x] Implémenter la génération de clé EC (sur courbe `secp256r1`) via `KeyGenParameterSpec` avec `PURPOSE_SIGN` | `PURPOSE_VERIFY`.
  - [x] S'assurer que le stockage TEE/Hardware-backed est exigé (`isInsideSecureHardware`).
  - [x] Extraire et formater la clé publique brute; dériver le `nodeId` (SHA-256 tronqué à 8 bytes puis formaté en Hexadécimal).
- [x] Task 3: Persistance de la Clé Publique (Data Layer - Room DB)
  - [x] Créer l'entité Room `data/local/entities/NodeIdentityEntity.kt` (nom de table `node_identity` en snake_case).
  - [x] Créer le `data/local/IdentityDao.kt` avec les requêtes d'insertion/sélection.
  - [x] Ajouter cette entité et DAO à la configuration Room globale existante de l'application (ou l'initialiser si non existante).
- [x] Task 4: Intégration et Implémentation du Repository (Data Repository)
  - [x] Implémenter `data/repository/IdentityRepositoryImpl.kt` liant le Keystore et l'IdentityDao.
  - [x] Mettre en place la logique de création : Si l'identité existe en DB, la retourner ; Sinon, générer via le Keystore, insérer en DB, puis retourner.
  - [x] Gérer rigoureusement les exceptions sans plantage silencieux (encapsuler les I/O DB et Keystore dans un `Result.success` / `Result.failure`).
- [x] Task 5: Di et Cycle de Vie (Hilt)
  - [x] Enregistrer les dépendances (`IdentityRepository`, `IdentityDao`, `KeystoreManager`) via `di/IdentityModule.kt`. 
  - [x] Déclencher l'initialisation de l'identité via un Use Case ou ViewModel appelé lors du lancement de l'application (par ex: dans le point d'entrée Jetpack Compose).

### Review Findings

- [x] [Review][Decision] Migration de schéma Room — `CatalogDatabase` reste en version 1 alors que `NodeIdentityEntity` est ajoutée. `fallbackToDestructiveMigration()` efface les données utilisateur existantes. Décision : `fallbackToDestructiveMigration()` conservé — acceptable en phase de développement actif. [CatalogDatabase.kt / IdentityModule.kt]
- [x] [Review][Patch] `IdentityModule.provideCatalogDatabase()` — DISMISSED (faux positif : aucun autre module ne fournit `CatalogDatabase`, pas de conflit Hilt) [IdentityModule.kt:35]
- [x] [Review][Patch] `generateIdentity()` — variable `keyStore` morte supprimée ✅ [KeystoreManager.kt:51]
- [x] [Review][Patch] `getExistingIdentity()` — `keyInfo` dead code supprimé ✅ [KeystoreManager.kt:34-36]
- [x] [Review][Patch] `MainActivityViewModel.init` — `Result<NodeIdentity>` désormais géré via `.onFailure { Log.e }` ✅ [MainActivityViewModel.kt:44-48]
- [x] [Review][Patch] `@Inject constructor()` sur `KeystoreManager` redondant supprimé ✅ [KeystoreManager.kt:15]
- [x] [Review][Patch] `isInsideSecureHardware` — `Log.w(TAG, "[AC5-WARNING]...")` ajouté si non hardware-backed ✅ [KeystoreManager.kt:74-78]
- [x] [Review][Defer] Race condition sur `getIdentity()` : double appel simultané au premier démarrage peut produire deux identités, `REPLACE` garde la dernière — deferred, à mitiger avec un Mutex dans une story future [IdentityRepositoryImpl.kt:19]
- [x] [Review][Defer] `NodeIdentity @Serializable` avec `ByteArray` — sérialisation JSON kotlinx non-standard (array of ints vs base64) — deferred, à adresser lors de la story de sérialisation réseau P2P [NodeIdentity.kt]
- [x] [Review][Defer] `KeystoreManager` méthodes non-`suspend` sur I/O Keystore — fragilité pour évolutions futures — deferred, refactoring suspend à envisager en story future [KeystoreManager.kt]

## Dev Notes

> [!CAUTION] **CRITICAL ARCHITECTURE MISTAKES TO PREVENT:**
> - **Anti-Sybil (Hashcash retiré):** N'implémentez PAS Hashcash. L'architecture V4 stipule que l'anti-Sybil repose exclusivement sur l'impossibilité d'exporter la clé privée depuis l'Android Keystore (TEE).
> - **Error Handling:** ZERO exception silencieuse brute. Toutes vos méthodes du repository et use cases doivent retourner `Result<T>` ou une Sealed Class. Appelez explicitement `runCatching { ... }` lors des accès Keystore/Room.
> - **Thread Blocking (Performance):** L'accès au Keystore et à la BDD Room fait de l'I/O. Utilisez explicitement `Dispatchers.IO` ou configurez Room avec les Coroutines suspend funs.

### Developer Context & Constraints

#### 1. Security APIs & Android Keystore
- Utilisez le provider `"AndroidKeyStore"` de `java.security.KeyStore`.
- Pour la génération EC : `KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")`.
- `KeyGenParameterSpec`: Utilisez `setDigests(KeyProperties.DIGEST_SHA256)`. Vérifiez le hardware via l'API KeyInfo `info.isInsideSecureHardware`. Si le device ne supporte pas StrongBox, le TEE normal (hardware-backed par défaut) est acceptable.
- **NodeID Generation:** Hacher la clé publique exportée `publicKey.encoded` avec un `MessageDigest.getInstance("SHA-256")`. Prendre les 8 premiers octets et les formater en une string hexadécimale (soit 16 caractères de long).

#### 2. Architecture & File Structure Requirements
- **Domain Layer:** Aucun import Android (donc Pas de `android.security.*` dans le domain !). `NodeIdentity` n'est qu'un POJO Kotlin simple.
- **Data Layer:** `IdentityRepositoryImpl.kt` doit hériter de l'interface Domain et injecter ses dépendances via Dagger/Hilt.
- **Database (Room):** Utilisez les annotations Room avec du `snake_case` (ex: `@ColumnInfo(name = "public_key_bytes")`). Room gérera le `ByteArray` sans TypeConverter si c'est natif, mais vérifiez les configurations.

#### 3. Previous Work Intelligence
- Story 1.2 a finalisé la navigation Compose (Dashboard, Explorer, Settings). Pour déclencher l'initialisation de l'identité, vous pouvez appeler cette initialisation silencieusement dans le ViewModel du `DashboardScreen` (ou `MainViewModel`) en arrière-plan sans bloquer l'UI.
- Le thème PFE est très "Utilitariste" et "Dark OLED" ; vous n'avez pas d'UI visible profonde dans cette story à modifier, mais veillez à ce qu'une erreur (`Result.Failure`) puisse éventuellement être traduite par un StateFlow afin que le Dashboard affiche un message (optionnel ici, mais prépare le terrain).

### References

- [Source: architecture.md#Authentication & Security] (Retrait Hashcash validé, Keystore EC P-256 comme identité).
- [Source: architecture.md#Error Handling Patterns] (Zéro exception non gérée, `Result<T>`).
- [Source: epics.md#Story 1.3] (Acceptance Criteria de référence).

## Dev Agent Record

### Agent Model Used
{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
