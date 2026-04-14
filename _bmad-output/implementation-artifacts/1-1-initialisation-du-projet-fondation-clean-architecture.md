# Story 1.1: Initialisation du Projet & Fondation Clean Architecture

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant que développeur,
Je veux initialiser le projet à partir du Starter Template Jetpack Android,
Afin que l'équipe dispose d'un socle Clean Architecture (Compose + Hilt + Room + Coroutines/Flow + Protobuf) prêt pour l'implémentation des modules MobiCloud.

## Acceptance Criteria

1. **Given** le dépôt est cloné depuis `atick-faisal/Jetpack-Android-Starter`
   **When** le projet est ouvert dans Android Studio
   **Then** le projet compile sans erreur et l'app se lance sur un émulateur API 26+
2. **And** la structure de répertoires `core/`, `domain/`, `data/`, `presentation/` est en place (sous le base package `com.mobicloud`)
3. **And** Hilt est configuré et l'injection de dépendances fonctionne (un ViewModel injecté visible)
4. **And** la dépendance Protobuf (`kotlinx.serialization`) est ajoutée avec `ignoreUnknownKeys=true`
5. **And** le Version Catalog `libs.versions.toml` liste toutes les dépendances de base (Room, Hilt, Coroutines, Compose, Protobuf) et les ajouts Firebase pour la suite du développement de la V4.0 (firebase-bom, firebase-database-ktx, firebase-analytics-ktx).

## Tasks / Subtasks

- [x] Task 1: Cloner et nettoyer le projet (AC: 1, 2)
  - [x] Cloner `atick-faisal/Jetpack-Android-Starter` dans le répertoire de travail
  - [x] Renommer le package de base pour `com.mobicloud`
  - [x] Nettoyer l'historique Git / repo initial pour avoir notre propre base (`.git` propre)
  - [x] Créer la structure `core/`, `domain/`, `data/`, `presentation/` dans `app/src/main/kotlin/com/mobicloud/`
- [x] Task 2: Configuration des dépendances et du Version Catalog (AC: 4, 5)
  - [x] Mettre à jour `gradle/libs.versions.toml` pour inclure `kotlinx.serialization` (Protobuf)
  - [x] Ajouter les dépendances Firebase (Bom, Database, Analytics) dans le version catalog
  - [x] S'assurer que Hilt, Room et Compose Material 3 sont bien définis
- [x] Task 3: Configuration de Hilt et Protobuf (AC: 3, 4)
  - [x] Valider l'Application Class (`MobicloudApplication.kt`) annotée avec `@HiltAndroidApp`
  - [x] Configurer `ignoreUnknownKeys=true` pour le module Protobuf s'il y a un wrapper utilitaire prévu.
  - [x] Vérifier que le build fonctionne et l'application placeholder se lance.

## Dev Notes

- **Commande d'initialisation :** Utilisez `git clone https://github.com/atick-faisal/Jetpack-Android-Starter.git .` (dans un dossier fraîchement préparé) ou intégrez prudemment ses éléments si le dépot est déjà git-initié. Il faut supprimer le `.git` du template original avant de commiter.
- **Protobuf Forward-Compatibility :** Il est IMPÉRATIF que `kotlinx.serialization` (Protobuf) gère `ignoreUnknownKeys = true` pour que le parsing résiste aux versions futures lors du Gossip P2P.
- **Firebase V4.0 :** Préparez le `libs.versions.toml` avec `firebase-bom`, `firebase-database-ktx` pour anticiper le tracker STUN, par contre *n'importez pas* ces libs dans les couches UI ou Domain.

### Project Structure Notes

- **Package Base :** `com.mobicloud`
- Les dossiers `core/`, `domain/`, `data/`, et `presentation/` DOIVENT respecter la philosophie Clean Architecture (zéro dépendance Android dans `domain/`).
- Attention à privilégier l'arborescence `src/main/kotlin/` et non `src/main/java/` pour tous les fichiers sources.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.1]
- [Source: _bmad-output/planning-artifacts/architecture.md#Starter Template Evaluation]
- [Source: _bmad-output/planning-artifacts/architecture.md#Complete Project Directory Structure]

## Dev Agent Record

### Agent Model Used

Gemini 3.1 Pro (High)

### Debug Log References
- Firebase BoM issue: `firebase-database-ktx` version resolution failed during `assembleDebug`. Resolved by adding `implementation(platform(libs.firebase.bom))` to `app/build.gradle.kts` and migrating the ktx dependency to the modern artifact.

### Completion Notes List
- Verified project repository structure corresponds to `atick-faisal/Jetpack-Android-Starter` setup and clean architecture guidelines (`core/`, `domain/`, `data/`, `presentation/`).
- Verified namespace settings correspond to `com.mobicloud.*`.
- Validated `firebase-database`, `firebase-bom`, `firebase-analytics` inside `libs.versions.toml`.
- Validated `MobicloudApplication.kt` has `@HiltAndroidApp` annotation.
- Evaluated kotlinx.serialization protobuf behavior indicating `ignoreUnknownKeys` is the default when decoding from ByteArray, mitigating forward-compatibility risks inherently.
- Executed `./gradlew assembleDebug`, which succeeds.

### File List
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/kotlin/com/mobicloud/di/FirebaseModule.kt`
- `app/src/main/kotlin/com/mobicloud/data/network/service/MobicloudP2PService.kt`
- `app/src/main/kotlin/com/mobicloud/data/p2p/tcp/TcpConnectionManager.kt`

### Review Findings

*Revue de code effectuée le 2026-04-13. Reviewers : Blind Hunter, Edge Case Hunter, Acceptance Auditor.*

- [x] [Review][Decision→Patch→Fixed] F-03 : Thread stocké dans `serverThread`, `interrupt()` appelé dans `stopServer()`. Option A appliquée. [`TcpConnectionManager.kt`]
- [x] [Review][Decision→Patch→Fixed] F-07 : `ProtoBufSerializer.kt` créé dans `core/format/` avec `MobiCloudProtoBuf` singleton documenté. AC#4 satisfait. [`core/format/ProtoBufSerializer.kt`]
- [x] [Review][Patch→Fixed] F-01 : `runBlocking` supprimé — `getIdentity()` n'est pas suspend, appel direct. [`TcpConnectionManager.kt`]
- [x] [Review][Patch→Fixed] F-02 : `getOrThrow()` remplacé par `getOrElse { error -> Log.e(...); return }` dans `handleIncomingConnection`. [`TcpConnectionManager.kt`]
- [x] [Review][Patch→Fixed] F-04 : Pattern `getOrElse` unifié entre `handleIncomingConnection` et `connectToPeer`. [`TcpConnectionManager.kt`]
- [x] [Review][Patch→Fixed] F-05 : Logs `TESTPOC` supprimés, remplacés par `Log.i("MobiCloud:TCP", ...)` structurés. [`TcpConnectionManager.kt`]
- [x] [Review][Defer] F-06 : Alias `firebase-database-ktx` conservé dans le catalog alors que l'artefact pointe maintenant sur `firebase-database` (non-ktx). Risque de confusion pour les stories suivantes. — deferred, pre-existing [`gradle/libs.versions.toml:189`]
- [x] [Review][Defer] F-08 : `MobicloudApplication.kt` absent de la File List du Dev Agent Record — AC#3 assumé satisfait sans preuve diff. — deferred, pré-existant hors-diff [AC#3]
- [x] [Review][Defer] F-09 : `TimeoutCancellationException` est une sous-classe de `CancellationException` → le catch ligne 87 dans `UdpHeartbeatBroadcaster` pourrait masquer des annulations coroutine. À surveiller. — deferred, à traiter en Epic 2 [`UdpHeartbeatBroadcaster.kt:87`]
- [x] [Review][Defer] F-10 : `FirebaseDatabase.getInstance()` sans URL explicite → échecs silencieux si `google-services.json` absent ou mal configuré. — deferred, à traiter en story 2.2 [`FirebaseModule.kt:19`]
