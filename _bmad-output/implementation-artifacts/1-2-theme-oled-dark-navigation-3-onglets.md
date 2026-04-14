# Story 1.2: Thème OLED Dark & Navigation 3 Onglets

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant qu'utilisateur,
Je veux que l'application s'affiche en mode sombre OLED pur avec une navigation claire à 3 onglets,
Afin d'avoir une interface énergétiquement efficace et intuitive dès le premier lancement.

## Acceptance Criteria

1. **Given** l'application est lancée sur un appareil Android
   **When** l'écran principal s'affiche
   **Then** le fond de l'app est `#000000` strict (OLED pur) avec le thème Material Design 3 sombre activé
2. **And** une Bottom Navigation Bar affiche 3 onglets : "Dashboard" (icône radar), "Explorer" (icône dossier DHT), "Paramètres" (icône engrenage)
3. **And** chaque onglet navigue vers un écran placeholder fonctionnel (sans crash)
4. **And** l'onglet actif est visuellement mis en évidence (couleur accent distincte)
5. **And** le thème Dark OLED est appliqué de façon persistante (pas de flash blanc au démarrage)

## Tasks / Subtasks

- [x] Task 1: Implémentation du Thème Dark OLED Strict (AC: 1, 5)
  - [x] Retoucher/Overrides le thème par défaut Material Design 3 dans `presentation/theme/Theme.kt`
  - [x] Forcer systématiquement le `colorScheme` en mode sombre uniquement (`darkColorScheme`)
  - [x] Assigner la couleur `#000000` (Noir pur) au `background` et `surface` pour désactiver complètement les pixels et préserver la batterie (NFR-03).
  - [x] Assigner le Vert Terminal (`#00FF41`) ou une couleur `Primary` sobre pour l'accentuation, Texte Primaire (`#E0E0E0`), Texte Secondaire (`#9E9E9E`).
  - [x] S'assurer que le flash blanc de démarrage d'Android (splashscreen theme) est basculé au noir dans `themes.xml` si nécessaire.
- [x] Task 2: Création de la Navigation Principale avec 3 Onglets (AC: 2, 4)
  - [x] Implémenter une structure de base (`Scaffold` avec `BottomAppBar` / `NavigationBar`) 
  - [x] Créer la liste des onglets (Dashboard, Explorer, Paramètres) avec leurs icônes associées (Radar, Folder, Settings).
  - [x] Configurer la surbrillance de l'onglet actif dans la barre de navigation.
  - [x] Assurer que toute ombre d'élévation inutile est supprimée ou au minimum, pour respecter l'esthétique Flat "Hardware/Utilitariste".
- [x] Task 3: Création des Ecrans Placeholders et Configuration du Routage (AC: 3)
  - [x] Placer les écrans placeholders dans `presentation/dashboard/DashboardScreen.kt`, `presentation/explorer/ExplorerScreen.kt`, et `presentation/settings/SettingsScreen.kt`.
  - [x] Mettre en place le moteur de navigation de Jetpack Compose (`NavHost`) pour router vers chaque onglet via le `NavController`.
  - [x] Valider que la navigation entre les onglets ne provoque de crash ou de re-création complète (maintain state).

### Review Findings

- [x] [Review][Decision] SettingsDialog depuis `feature.settings` — remplacée par un TODO placeholder non-crashant (choix A appliquée)
- [x] [Review][Patch] `isTopLevelDestinationInHierarchy` — corrigé avec `hasRoute(destination.route)` [JetpackApp.kt:401-404]
- [x] [Review][Patch] Imports morts dans `Theme.kt` — supprimés [Theme.kt]
- [x] [Review][Patch] Paramètres `darkTheme`/`disableDynamicTheming` ignorés — supprimés de la signature [Theme.kt:107]
- [x] [Review][Patch] Incohérence package `com.mobicloud.compose.navigation` — corrigé en `com.mobicloud.navigation` [NavHost.kt:17, TopLevelDestination.kt:17]
- [x] [Review][Patch] Strings hardcodées dans écrans placeholders — remplacées par `stringResource()` [Dashboard/Explorer/SettingsScreen.kt]
- [x] [Review][Defer] `navigateToItemScreen()` dead code — méthode vide gardée mais obsolete [JetpackAppState.kt] — deferred, pre-existing
- [x] [Review][Defer] `LightDefaultColorScheme` maintenue mais inaccessible — thème light inatteignable post-refacto [Theme.kt] — deferred, pre-existing
- [x] [Review][Defer] `topLevelDestinationsWithUnreadResources` branché ancienne feature — notification dots jamais actives [JetpackAppState.kt] — deferred, pre-existing
- [x] [Review][Defer] Pas de sauvegarde backstack navigation — état non persisté entre recreations d'activité [NavHost.kt] — deferred, non critique pour placeholders

## Dev Notes

- **Overriding Material Design 3** : Le design cible est un Cyber-Terminal tactique / Utilitariste. Pour cela, MD3 est utilisé mais "flat". Les élévations sont de `0dp`. 
- **Économie d'Energie** : Les animations tactiles inutiles (Ripple effect massif) peuvent être laissées par défaut pour le moment mais devront être sobres.
- **Topologie de l'arborescence** : S'assurer que le code de Navigation (les actions) reste propre à Compose Navigation (par exemple en utilisant Typesafe argument via `kotlinx.serialization` si compatible avec la version NavCompose existante, ou enum simples pour le routing statique `Route`).
- **Typographie System** : Commencer à forcer les primitives si possible. Le PRD UX suggère `Inter`/`Roboto` en primaire, et une font Monospace pour les "datas" (On peut configurer cela plus tard, ici on se concentre sur le fond OLED et onglets).

### Project Structure Notes

- Le code de l'interface graphique est strictement confiné à `com.mobicloud.presentation.*`.
- Le projet est configuré avec l'artefact `atick-faisal/Jetpack-Android-Starter`, donc il faut vérifier comment la navigation et le M3 theme étaient gérés par défaut et les remplacer/écraser de façon pérenne au lieu d'empiler des surcouches.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.2: Thème OLED Dark & Navigation 3 Onglets]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Design System Foundation]
- [Source: _bmad-output/planning-artifacts/ux-design-specification.md#Color System]
- [Source: _bmad-output/planning-artifacts/architecture.md#Implementation Flow]

## Dev Agent Record

### Agent Model Used
Gemini 3.1 Pro (High)

### Debug Log References
- No major issues. Build verified successfully.

### Completion Notes List
- Implemented `DarkDefaultColorScheme` with `TerminalGreen` and `PureBlack` in `Color.kt` and `Theme.kt`.
- Overridden `JetpackTheme` to forcefully use dark mode and `0.dp` elevation for a flat OLED aesthetic.
- Patched starting window backgrounds in both `themes.xml` and `values-night/themes.xml` to `black`.
- Created placeholder screens `DashboardScreen`, `ExplorerScreen`, `SettingsScreen`.
- Modified `TopLevelDestination.kt` to define DASHBOARD, EXPLORER, SETTINGS as bottom nav items with standard Material icons.
- Attached new screens into the `JetpackNavHost` routing logic and decoupled previous implicit dependencies.

### File List
- `core/ui/src/main/kotlin/com/mobicloud/core/ui/theme/Color.kt`
- `core/ui/src/main/kotlin/com/mobicloud/core/ui/theme/Theme.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/kotlin/com/mobicloud/ui/JetpackApp.kt`
- `app/src/main/kotlin/com/mobicloud/ui/JetpackAppState.kt`
- `app/src/main/kotlin/com/mobicloud/navigation/TopLevelDestination.kt`
- `app/src/main/kotlin/com/mobicloud/navigation/NavHost.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/kotlin/com/mobicloud/presentation/dashboard/DashboardScreen.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/explorer/ExplorerScreen.kt`
- `app/src/main/kotlin/com/mobicloud/presentation/settings/SettingsScreen.kt`
