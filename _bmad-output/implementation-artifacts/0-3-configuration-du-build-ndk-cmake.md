# Story 0.3: Configuration du Build NDK & CMake

**Status:** done
**Epic:** 0 - Fondation Technique P2P 🛠️ (Enabler Epic)

---

## 1. Story Foundation & Requirements

**User Story:**
As a Développeur Android,
I want d'initialiser et de lier la toolchain C++ (CMake) pour les architectures `armeabi-v7a` et `arm64-v8a`,
So that je puisse compiler et interfacer ultérieurement la librairie Erasure Coding via JNI sans bloquer les Sprints futurs.

**Acceptance Criteria:**
- **Given** le projet Android fraîchement généré,
- **When** j'ajoute un fichier C++ "Hello World" et configure le `CMakeLists.txt`,
- **Then** le Gradle Build compile la librairie `.so` native pour ARMv7 et ARM64.
- **And** un test unitaire Kotlin parvient à lire la string native via JNI.

**Business Value & Context:**
Permettre l'intégration native de la logique d'Erasure Coding avec accélération matérielle (SIMD ARM NEON). Le calcul des blocs de parité en Kotlin pur (JVM) causerait un effort CPU massif et viderait la batterie en quelques minutes (ainsi qu'un étranglement thermique / Throttling). Ce bridge C++ est donc une fondation impérative de performance (NFR2).

---

## 2. Developer Context & Guardrails

### 🏗 Architecture Compliance (CRITICAL)
- **DirectByteBuffer Batching :** Bien que non testé directement dans le Hello World initial, l'infrastructure C++ doit être amorcée avec l'esprit orienté haute performance mémoires. C'est critique pour l'architecture. (Le transfert d'octets isolés ruinerait les perfs, l'utilisation de Zéro-copie via `DirectByteBuffer` est exigée plus tard).
- **NDK et CMake :** Le fichier `CMakeLists.txt` doit exister et la compilation doit cibler les architectures ABI mobiles standard (`armeabi-v7a` et `arm64-v8a`). L'exclusion de x86 pour les builds physiques est fréquente, mais incluez x86/x86_64 pour l'émulateur (tests).

### ⚙️ Technical Requirements & Guidelines
> [!WARNING]
> N'oubliez pas d'ajouter les blocs `externalNativeBuild` dans le script `app/build.gradle.kts` ou via un module Gradle dédié si vous décidez d'isoler la librairie C++. Placez le `CMakeLists.txt` proprement dans `app/src/main/cpp`.
> Protégez l'accès au LoadLibrary avec un bloc `init { System.loadLibrary("mobimath_lib") }` par exemple.

### 📂 File Structure Requirements
Vous devez suivre cette arborescence (suggestion) :
```text
app/src/main/cpp/
 ├── CMakeLists.txt
 └── native-lib.cpp
app/src/main/java/com/mobicloud/
 └── core/
     └── ndk/
         └── NdkBridge.kt (Interface qui chargera la lib JNI)
app/src/test/java/com/mobicloud/
 └── core/ndk/
     └── NdkBridgeTest.kt (Ou test d'androidTest si nécessaire pour le load library local JVM)
```
*Note sur NdkBridgeTest : Sur une JVM pure standard de test unitaire, charger la librairie système C++ (Linux/Mac/Windows) nécessitera un .so/.dylib/dll local pour la machine hôte. Si cela est trop lourd à configurer avec Gradle local, un test d'instrumentation (androidTest) fonctionnera en direct sur téléphone/émulateur. Optez pour ce qui valide l'intégration.*

### 🧪 Testing Requirements
- Valider le build : Gradle compile sans erreur et produit les `.so` encapsulés dans l'AAR / l'APK.
- L'appel JNI (`stringFromJNI()`) fonctionne et renvoie une string au Kotlin (preuve d'interfaçage réussi).

---

## 3. Previous Story Intelligence
*(Tiré de l'epic 0)*
- Maintain pure isolation `domain` vs `data` via les `core` modules pour l'infrastructure bas niveau.
- Prenez garde aux versions NDK : Utilisez si possible la version standard/stable par défaut listée dans le Version Catalog ou celle testée.
- Story 0.4 compilait à 100%, l'objectif est de ne pas casser la build loop lors de l'intégration native !

---

## 4. Tasks/Subtasks

- [x] Task 1 (AC: Gradle Build): Ajouter le support NDK et les flags CMake dans `app/build.gradle.kts` ciblant au moins armeabi-v7a et arm64-v8a (x86_64 pour l'émulateur).
- [x] Task 2 (AC: CMake Config): Créer le fichier `CMakeLists.txt` explicitement configuré et lier proprement `native-lib.cpp`.
- [x] Task 3 (AC: Hello World JNI): Écrire la fonction C++ minimaliste qui renvoie une string et son binding Kotlin `NdkBridge.kt` avec `external fun`.
- [x] Task 4 (AC: Unit/Integration Test): Écrire le test automatisé appelant la méthode native et prouvant l'intégrité de l'interfaçage JNI.

---

## 5. Dev Agent Record

### Agent Model Used
Gemini 3.1 Pro (High)

### Completion Notes List
- BMad workflow applied.
- Extraction PRD / Epics / Architecture completed.
- Guardrails enforced for CMake file and DirectByteBuffer hints.
- ✅ Implemented NDK toolchain in `app/build.gradle.kts` for target architectures.
- ✅ Created `CMakeLists.txt` and C++ `native-lib.cpp` correctly integrated via standard rules.
- ✅ Configured JNI NdkBridge Kotlin object linking native string output.
- ✅ Set up `AndroidJUnit4` instrumentation test validating the external function successfully.
- ✅ Fixed reflection compilation issues by modifying `gradle.properties` (JVM Args for Gson access issue).
 
### File List
- `c:/Users/naoui/Desktop/Projets/PFE/app/build.gradle.kts`
- `c:/Users/naoui/Desktop/Projets/PFE/app/src/main/cpp/CMakeLists.txt`
- `c:/Users/naoui/Desktop/Projets/PFE/app/src/main/cpp/native-lib.cpp`
- `c:/Users/naoui/Desktop/Projets/PFE/app/src/main/java/com/mobicloud/core/ndk/NdkBridge.kt`
- `c:/Users/naoui/Desktop/Projets/PFE/app/src/androidTest/java/com/mobicloud/core/ndk/NdkBridgeTest.kt`
- `c:/Users/naoui/Desktop/Projets/PFE/gradle.properties`
- `c:/Users/naoui/Desktop/Projets/PFE/app/src/main/kotlin/com/mobicloud/MobicloudApplication.kt`
- `c:/Users/naoui/Desktop/Projets/PFE/_bmad-output/implementation-artifacts/0-3-configuration-du-build-ndk-cmake.md`
- `c:/Users/naoui/Desktop/Projets/PFE/app/google-services.json`

### Code Review Patches (2026-03-29)
- ✅ [E2] `ndkVersion = "26.1.10909125"` ajouté dans `app/build.gradle.kts` (builds reproductibles)
- ✅ [B1] `try/catch UnsatisfiedLinkError` + `Timber.e` dans `NdkBridge.kt`
- ✅ [E1] ABI `"x86"` ajouté aux `abiFilters` pour prise en charge des anciens émulateurs
- ✅ [B3] `applicationId` corrigé de `"com.mobicloud.compose"` à `"com.mobicloud"` + `google-services.json` aligné
