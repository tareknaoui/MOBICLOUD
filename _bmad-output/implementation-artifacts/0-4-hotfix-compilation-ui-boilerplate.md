# Story 0.4: Hotfix Compilation UI Boilerplate

Status: done

## Story

As a Developer,
I want to resolve all compilation errors in the UI layers (like JetpackApp.kt and other un-resolved references),
so that the :app module compiles successfully at 100% (Build Successful) before proceeding to Story 0.3.

## Acceptance Criteria

1. `JetpackApp.kt` and all UI-related files must have their unresolved references fixed or the dead code invoking them removed.
2. The `:app` module must successfully compile without any build errors.
3. No business logic or existing configurations (like Protobuf or Clean Architecture) should be negatively impacted by this UI boilerplate cleanup.
4. All unused layout references, missing Compose imports, or dangling view references are correctly resolved or deleted.

## Tasks / Subtasks

- [x] Analyze compilation errors to list errors.
- [x] Fix unresolved references in `JetpackApp.kt`.
- [x] Clean up any other UI boilerplate code causing build failures.
- [x] Verify 100% successful build for the Android project configuration.

### Review Findings

- [x] [Review][Patch] `Timber.DebugTree()` planté sans guard `BuildConfig.DEBUG` — logs visibles en production, risque de sécurité pour un projet P2P avec identités cryptographiques [MobicloudApplication.kt:35]
- [x] [Review][Patch] `SnackbarAction.REPORT` handler vide silencieux dans `JetpackApp.kt` (2 occurrences) — régression UX/comportementale : le bouton "Report" affiché ne déclenche aucune action ni log [JetpackApp.kt:169-171, 290-292]
- [x] [Review][Defer] Incohérence package `com.mobicloud.ui` vs `com.mobicloud.compose.ui` pour `JetpackAppState` \u2014 design architectural pré-existant \u2014 deferred, pre-existing
- [x] [Review][Defer] `ExperimentalMaterial3WindowSizeClassApi` — API expérimentale, risque de breakage \u2014 deferred, pré-existant hors-scope hotfix

## Dev Notes

- **Context**: The previous story properly implemented Protobuf serialization, but the overarching compilation failed due to pre-existing UI boilerplate and `JetpackApp.kt` referencing undefined symbols or missing imports.
- **Goal**: Make the project build flawlessly so we have a clean slate for the NDK/CMake configuration (Story 0.3).
- **Rule of Thumb**: If it's pure UI boilerplate (not related to MobiCloud core mechanics) and it's breaking the build, delete it or comment it out if a quick fix isn't obvious. The primary goal is compilation success.

### Project Structure Notes

- Module `:app` is the focus.

### References

- User constraint: "corrige toutes ces erreurs de références non résolues dans JetpackApp.kt et le reste de l'UI. Supprime le code mort si nécessaire, notre but est d'avoir un module :app qui compile à 100% (Build Successful) avant de passer à la Story 0.3."

## Dev Agent Record

### Agent Model Used

Gemini 3.1 Pro (High)

### Completion Notes
- Analyzed and tracked all compilation errors.
- Fixed unresolved references in `JetpackApp.kt` by importing `JetpackAppState` and deleting `crashReporter` references.
- Fixed unresolved reference for `MainActivityViewModel` in `MainActivity.kt` and `JetpackAppState` in `NavHost.kt`.
- Removed unresolved `BuildConfig.DEBUG` call in `MobicloudApplication.kt`.
- Verified the complete `:app` module builds successfully at 100%.

## File List
- `app/src/main/kotlin/com/mobicloud/ui/JetpackApp.kt`
- `app/src/main/kotlin/com/mobicloud/MainActivity.kt`
- `app/src/main/kotlin/com/mobicloud/navigation/NavHost.kt`
- `app/src/main/kotlin/com/mobicloud/MobicloudApplication.kt`

## Change Log
- Added missing explicit imports for classes existing in different sub-packages.
- Removed invalid properties calls missing from initial template.
- Achieved 100% successful build (Date: 2026-03-28T01:30:11+01:00)
