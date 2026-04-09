---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
inputDocuments: ['prd.md', 'description_technique_formelle.md']
workflowType: 'architecture'
project_name: 'PFE'
user_name: 'Naoui'
date: '2026-03-25T15:40:01+01:00'
lastStep: 8
status: 'complete'
completedAt: '2026-03-25T16:30:00+01:00'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
Le système s'articule autour de 10 modules autonomes mais fortement couplés. L'architecture est purement P2P (pas de serveur). Les fonctionnalités s'appuient sur un canal de communication P2P unifié, un chiffrement par bloc strict, et un calcul d'Erasure Coding dont la redondance (M) s'ajuste dynamiquement via un score de fiabilité IA. La découverte et la synchronisation des fiches dépendent d'un anneau DHT couplé à un protocole Gossip épidémique.

**Non-Functional Requirements:**
- **Résilience extrême :** Survie à un churn de 30% à 70% et auto-cicatrisation.
- **Energie et Batterie :** Consommations limitées imposant l'interdiction stricte du routage data multi-sauts.
- **Performances temporelles :** Reconstruction rapide (via téléchargements compétitifs K+2) et streaming direct des blocs déchiffrés.
- **Sécurité et Équité :** Contremesures contre les attaques Sybil (Hashcash), collusion (PoR déterministe) et "Trous Noirs".

**Scale & Complexity:**
- L'échelle et la nature décentralisée rendent la complexité systémique extrêmement élevée. Le projet est exposé à d'innombrables *edge cases* liés aux partitions réseau.

- Primary domain: Mobile Distributed Systems (Android P2P) & Applied Cryptography
- Complexity level: High / Scientific Research Grade
- Estimated architectural components: ~10 Core Modules + Couches Réseau & IA

### Technical Constraints & Dependencies

- **Contraintes Android API :** Les limites des API P2P sur la formation de groupes et les temps de négociation (3-5 secondes par topologie) sont le goulot d'étranglement majeur du système.
- **Contraintes Matérielles :** Limites de batterie, RAM et throttling thermique sur processeurs ARM, dictant des pipelines de déchiffrement fenêtrés.

### Cross-Cutting Concerns Identified & Pragmatic Resolutions

Suite à l'analyse architecturale (Winston), les décisions critiques suivantes sont actées pour assainir l'implémentation par rapport au design théorique :

1. **Réseau P2P (Topologie Flexible vs Fixe) :** Face à l'overhead de négociation de groupe fixe empêchant les transferts parallèles massifs, l'implémentation privilégiera un réseau P2P permettant une topologie "sans groupe figé". En cas de repli sur un mode hiérarchique classique, le "Super-Pair" devra impérativement assumer le rôle de coordinateur central pour maintenir une étoile stable et absorber le coût de négociation, malgré la concentration de charge qui en découle.
2. **Synchronisation (DHT + Gossip) :** Le protocole Gossip épidémique sera strictement circonscrit aux *Replica Sets* (nœuds gérant la même partition DHT) pour les métadonnées. Pour le reste du réseau, un Gossip "ultra-léger" sera conservé uniquement pour le *Heartbeat* (Typologie vivant/mort), évitant ainsi la redondance de la transmission globale des *deltas*.
3. **Anti-Sybil (Hashcash) et Churn :** Pour épargner le processeur ARM et la batterie lors de multiples reconnexions (Churn), le calcul massif (1s max CPU) ne sera exigé qu'à la création initiale de l'identité. Cette identité (`KeyPair`) sera persistée localement et sera réutilisée comme métadonnée cryptographique lors des re-connexions de session pour prouver la légitimité d'un nœud sans recalcul.
4. **Scission et Élection (Buffer d'Urgence) :** Pour pallier le "vide de pouvoir" lors d'une ré-élection du Super-Pair (Bully avec Hystérésis), chaque "Nœud Médecin" potentiel implémentera une **file d'attente (buffer) locale in-memory** pour conserver les requêtes d'auto-réparation. Celles-ci seront émises dès que le nouveau coordinateur annoncera son mandat, évitant ainsi la perte de fragments durant la transition.

---

## Realistic Scope Adjustments (PFE 2026)

> [!IMPORTANT]
> Suite à la révision du plan de charge pour la deadline du **03 Juin 2026**, l'architecture reste inchangée dans sa vision théorique mais l'implémentation effective subit les ajustements pragmatiques suivants pour garantir une soutenance stable :

### 1. Simplification Matérielle & IA
- **Erasure Coding (SIMD NEON) :** L'implémentation utilisera une version C++ standard via JNI. L'optimisation vectorielle NEON est reportée (traitée comme perspective dans le rapport).
- **Modèle de Fiabilité (TFLite) :** Le moteur d'inférence IA est remplacé par un `StaticMockTrustScore` injecté via Hilt. L'architecture `ITrustScoreProvider` est conservée pour permettre une intégration future transparente.

### 2. Réduction des Protocoles de Sécurité & Consensus
- **Proof of Retrievability (PoR) :** Ce mécanisme de challenge à 3 nœuds est retiré du scope d'implémentation.
- **Validation du Karma :** La certification collégiale par l'anneau DHT est simplifiée en une validation direct-peer signée (Anti-Replay conservé).
- **Split-Brain :** La résolution automatique des conflits de fusion de réseaux est documentée dans le rapport mais non implémentée (trop complexe à reproduire en démo).

### 3. Validation au Churn
- La preuve de résilience s'appuiera sur des scénarios manuels documentés et des logs structurés plutôt que sur un simulateur de réseau virtuel automatisé dédié.

---

---

## Starter Template Evaluation

### Primary Technology Domain

Mobile Application (Android Native / Kotlin) based on project requirements analysis

### Starter Options Considered

- **atick-faisal/Jetpack-Android-Starter**: Template robuste et prêt pour la production, basé sur l'architecture "Now In Android" de Google (Compose, Hilt, Coroutines/Flow, Room).
- **im-o/jetpack-compose-clean-architecture**: Template fortement modularisé axé sur le design pattern Use Case.

### Selected Starter: atick-faisal/Jetpack-Android-Starter

**Rationale for Selection:**
Ce boilerplate fournit une fondation moderne de grade Production qui gère nativement Kotlin Coroutines et Flow. Ces outils linguistiques sont vitaux pour l'orchestration asynchrone complexe du P2P MobiCloud (callbacks réseaux, buffers d'attente, Heartbeats, Gossip). La séparation Clean Architecture garantit que l'UI est déconnectée de la logique décentralisée.

**Initialization Command:**

```bash
git clone https://github.com/atick-faisal/Jetpack-Android-Starter.git .
```

**Architectural Decisions Provided by Starter:**

**Language & Runtime:**
Kotlin avec intégration native de Coroutines et Flow.

**Styling Solution:**
Jetpack Compose avec Material Design 3 (UI déclarative).

**Build Tooling:**
Gradle avec Convention Plugins et Version Catalogs.

**Testing Framework:**
JUnit, MockK - Configurations prêtes à l'emploi essentielles pour la couverture de code de l'algorithme d'Erasure Coding et la DHT.

**Code Organization:**
Clean Architecture modulaire (Presentation, Domain, Data). Parfait pour l'isolation des 10 modules MobiCloud au sein de la couche Domain.

**Development Experience:**
Injection de dépendances automatisée via Dagger Hilt.

**Note:** L'initialisation du projet en utilisant cette configuration doit être la première User Story d'implémentation.

---

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Data Serialization format for P2P messages (Protobuf)
- Persistent Node Identity Anti-Sybil (Android Keystore)
- P2P Communication Protocol (Raw Sockets)

**Important Decisions (Shape Architecture):**
- Local Database (Room SQLite)
- Encryption Cipher (AES-256 GCM)

**Deferred Decisions (Post-MVP):**
- UI/UX features, as the core is fundamentally a headless P2P engine.

### Data Architecture

- **Séralisation P2P :** `kotlinx.serialization` (Protobuf v1.10.x) pour une compression binaire maximale des messages Gossip et Heartbeat.
- **Stockage Local Catalogue :** Jetpack Room (SQLite) pour la persistance locale de la partition DHT assignée au nœud, permettant des requêtes "Zéro-Latence" locales (< 100ms).

### Authentication & Security

- **Identité du Nœud & Anti-Sybil :** Génération et persistance de la paire de clés asymétriques via l'**Android Keystore System** (Hardware-backed). Empêche le clonage d'identité hors extractabilité TEE.
- **Chiffrement des Fragments :** AES-256 GCM avec clés éphémères dérivées (Clé_Fichier + Index_Bloc) isolées en RAM lors du déchiffrement fenêtré.

### API & Communication Patterns

- **Transfert Data Lourd (Fragments) :** Sockets de flux directs (ex: TCP Sockets) dynamiques via Kotlin Coroutines.
- **Transfert Léger (Gossip/Heartbeat) :** Datagrammes de diffusion (ex: UDP Multicast/Unicast) pour minimiser l'overhead de connexion sur le réseau et éviter les protocoles d'accord initiaux lourds.
- **Pattern de Réveil Asynchrone :** Maintien d'une écoute réseau permanente (protégée par l'OS). Les nœuds "passifs" basculent en état hautement "Actif" soit via l'acceptation directe d'une connexion (Interruption I/O), soit par l'arrivée d'un datagramme de diffusion flaggé `URGENT` sans nécessité de connexion persistante pollée.

### Decision Impact Analysis

**Implementation Sequence:**
1. Base du projet Android (Starter Jetpack)
2. Couche Sécurité et Keystore (Pour sécuriser l'Identité dès le jour 1)
3. Couche Réseau Bas Niveau (UDP Gossip & Raw TCP + API P2P unifiée)
4. Gestion Locale (Room SQLite) et CRDTs partitionnés
5. Modélisation de l'Apprentissage IA / Score de fiabilité
6. Implémentation Erasure Coding Adaptatif complet

**Cross-Component Dependencies:**
- Protobuf est omniprésent : Les sockets réseau le consomment, le Gossip le génère, et Room DB le persiste parfois en BLOB pour les champs complexes (vecteurs, matrices EC).
- Les Coroutines Kotlin relient chaque module. Un soin critique sera apporté aux `Dispatchers.IO` vs `Dispatchers.Default` pour scinder les I/O réseau du processing lourd lié au code d'effacement.

---

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**Critical Conflict Points Identified:**
5 zones de conflit potentiel entre agents d'IA ont été identifiées (Nommage BDD, Structure Clean Architecture, Gestion d'état asynchrone, Gestion d'erreurs, et Injection de dépendances).

### Naming Patterns

**Database Naming Conventions (Room):**
- Tables et colonnes toujours en `snake_case` dans les annotations Room.
  *Example:* `@Entity(tableName = "node_metadata")`, `@ColumnInfo(name = "reliability_score")`.
- Classes Kotlin associées en `PascalCase`.
  *Example:* `data class NodeMetadata(...)`.

**Code Naming Conventions:**
- **Coroutines/Flow:** Les variables exposant un état asynchrone doivent être suffixées par `Flow` ou `State`.
  *Example:* `val connectionState: StateFlow<Boolean>`.
- **Use Cases:** Doivent refléter une action claire et commencer par un verbe. `PascalCase`.
  *Example:* `CalculateReliabilityScoreUseCase`, `DecodeErasureFragmentsUseCase`.

### Structure Patterns

**Project Organization:**
- **Séparation Stricte Clean Architecture :** Les interfaces résident dans la couche `Domain`, les implémentations dans la couche `Data`.
  *Example:* `domain/repository/PeerRepository.kt` vs `data/repository/PeerRepositoryImpl.kt`.

### Format Patterns

**Data Exchange Formats:**
- **Protobuf :** Les classes `kotlinx.serialization` doivent comporter les valeurs par défaut explicites pour tolérer la perte de versioning des noeuds P2P (CRDTs).

### Process Patterns

**Error Handling Patterns:**
- **ZÉRO Exception silencieuse :** L'architecture P2P exige une gestion rigoureuse. Utilisation OBLIGATOIRE du validateur natif `Result<T>` ou d'une `sealed class` (ex: `Resource<T>`) pour chaque retour de couche Data ou Usecase. Le `try/catch` brut est restreint aux appels I/O (Sockets réseau ex: TCP/UDP ou base de données locale).

### Enforcement Guidelines

**All AI Agents MUST:**
- Injecter TUTES les dépendances via `@Inject` constructor (Dagger Hilt). Aucune instanciation manuelle globale (`object` / `singleton`) en dur.
- Exécuter le code CPU lourd (Code d'effacement, Hashcash) exclusivement sur `Dispatchers.Default`.
- Exécuter les Sockets et DB sur `Dispatchers.IO`.

**Pattern Enforcement:**
Les revues de code (`bmad-code-review`) rejetteront toute implémentation qui mute un état global contournant la Clean Architecture ou qui jette une exception non gérée.

---

## Expert Protocol Validations & Pragmatic Enhancements

Suite à une revue croisée par des agents experts, les contraintes et optimisations suivantes ont été verrouillées pour l'implémentation des modules MobiCloud :

**Module 3 (Stockage & EC) - Accélération Matérielle :**
L'implémentation de l'Erasure Coding en Corps de Galois DOIT utiliser les instructions SIMD (ARM NEON) natives du processeur mobile (via binding JNI/NDK C++) pour éviter l'effondrement de la batterie et l'emballement thermique (CPU Throttling) lors du calcul de parité.

**Module 4 (Distribution) - Timeout Adaptatif Réseau :**
Les délais de négociation et d'Acquittement (ACK) P2P doivent être dynamiques, s'allongeant automatiquement en cas de forte densité (interférences BSSID mutuelles) pour ne pas pénaliser un hébergeur fiable mais victime du bruit ambiant.

**Module 5 (Catalogue DHT) - Filtres de Bloom :**
Lors du protocole Gossip intra-partition, les nœuds ne s'échangent pas leurs catalogues bruts mais des Filtres de Bloom (structures probabilistes ultra-légères). Cela permet d'identifier la nécessité d'une synchronisation Delta en ne transférant que quelques octets.

**Module 6 (Auto-Réparation) - Circuit-Breaker Avalanche :**
Intégration d'un "Coupe-Circuit" : Si le taux de churn instantané dépasse 30% en moins de 5 minutes, le Super-Pair gèle temporairement les directives de transfert de réparation pour empêcher l'épuisement immédiat des batteries des survivants.

**Module 7 (Migration Proactive) - Transfert Aveugle Opaque :**
L'évacuation de blocs vers un profil d'accueil s'effectue strictement sur le bloc chiffré. Aucune clé ou métadonnée en clair n'est transmise avec le "cargo".

**Module 8 (Récupération) - Pipeline de Streaming Actif :**
Le protocole de téléchargement compétitif (K+2) est couplé à un pipeline de rendu réactif : le désentrelacement et le déchiffrement débutent **dès** l'obtention des premiers fragments originaux, sans attendre la fin du téléchargement complet.

**Module 9 (Anti-Clandestin) - Dépréciation du Karma :**
Le score de Karma subit une décroissance temporelle (Time-Decay). Un nœud inactif perdra progressivement ses privilèges, forçant une participation continue au réseau.

**Module 10 (Élection) - Abdication Forcée :**
Pour prévenir l'épuisement matériel du nœud chef, le mandat de Super-Pair est strictement limité à 30 minutes automatiques. Au-delà, le nœud déclenche une passation de pouvoir et s'exclut de l'élection pour au moins 5 minutes.

**Directives d'Implémentation Spécifiques Android (Points de Vigilance) :**

1. **Compatibilité P2P :** Le module réseau (`core/network`) DOIT implémenter une logique de *Fallback*. Si un appareil ne supporte pas certains modes flexibles de communication, le réseau devra gérer des topologies hybrides où des nœuds (souvent le Super-Pair) agiront comme passerelles (Bridges).
2. **Continuité Physique & OS :** Le réseau P2P natif et l'acquisition des verrous matériels de diffusion réseau (ex: `MulticastLock` Wi-Fi) nécessitent impérativement l'hébergement de la couche `core/network` dans un **Foreground Service** (Service de premier plan avec notification persistante) pour empêcher l'OS de tuer les Sockets en arrière-plan.
3. **Goulot d'étranglement JNI (Erasure Coding) :** L'agent NE DOIT PAS transférer d'octets isolés au processus NDK C++ (SIMD ARM NEON). Le coût du saut de contexte Java/Native ruinerait les performances. L'implémentation impose un système de **Batching** via des `DirectByteBuffer` pour un accès direct à la mémoire lors des traitements matriciels de Galois.
4. **Protobuf Forward-Compatibility :** Sachant que différentes versions de la structure de données "Gossip" coexisteront, la configuration Kotlinx Serialization Protobuf DOIT impérativement inclure le paramètre de résilience (`ignoreUnknownKeys = true`).

---

## Project Structure & Boundaries

### Complete Project Directory Structure

```text
mobicloud-android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── app/
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/com/mobicloud/
│   │   ├── MobicloudApplication.kt
│   │   ├── di/                 (Hilt Dependency Injection Modules)
│   │   ├── core/               (Cross-Cutting Concerns)
│   │   │   ├── network/        (Raw Sockets, Wrappers Protocole P2P)
│   │   │   ├── security/       (Android Keystore, AES-GCM)
│   │   │   ├── database/       (Room Database configuration)
│   │   │   └── format/         (Protobuf serialization configs)
│   │   ├── domain/             (Pure Kotlin - No Android dependencies)
│   │   │   ├── models/         (NodeIdentity, Fragment, CatalogEntry)
│   │   │   ├── repository/     (Abstract Interfaces)
│   │   │   └── usecase/        (The 10 PRD Modules mapped here)
│   │   │       ├── m01_auth_discovery/
│   │   │       ├── m02_ai_scoring/
│   │   │       ├── m03_m04_gossip_heartbeat/
│   │   │       ├── m05_dht_catalog/
│   │   │       ├── m06_m07_repair_migration/
│   │   │       ├── m08_m09_erasure_coding_stream/
│   │   │       └── m10_election/
│   │   ├── data/               (Implementation Layer)
│   │   │   ├── local/          (Room DAOs, DataStore)
│   │   │   ├── p2p/            (Implémentations Canaux & Datagrammes)
│   │   │   └── repository_impl/(Binds Data to Domain)
│   │   └── presentation/       (Jetpack Compose UI)
│   │       ├── theme/
│   │       ├── dashboard/      (Node Status, AI Score, Local Battery)
│   │       └── explorer/       (DHT File Explorer)
│   └── src/test/               (Unit & Integration Tests)
│       └── java/com/mobicloud/domain/usecase/m08_m09_erasure_coding_stream/ (Math-heavy tests)
└── benchmark/                  (Microbenchmark for CPU/Battery Throttling)
```

### Architectural Boundaries

**Device Boundaries (Core vs Domain):**
La couche `core/network` cache entièrement la complexité des API Android requises (Interfaces de communication P2P, MulticastLock). Le `domain` ne verra que des interfaces pures réactives (`Flow<P2PMessage>`). Cette frontière est vitale pour la testabilité unitaire sur JVM (sans émulateur Android).

**Security Boundaries:**
Seul `core/security` a le droit d'interagir avec `AndroidKeyStore`. Le reste de l'application reçoit uniquement des byte arrays chiffrés ou des signatures à vérifier, mais la clé privée asymétrique ne quitte jamais le répertoire `security`.

### Requirements to Structure Mapping

**PRD Modules Mapping:**
Les 10 modules algorithmiques stricts définis dans `description_technique_formelle.md` sont traduits 1-pour-1 dans l'arborescence `domain/usecase/`. 
*Exemple:* La "Guillotine" (Module 7) et "Bully avec Hystérésis" (Module 10) seront de purs UseCases Kotlin testables indépendamment de l'état du réseau (via des repositories mockés).

### Integration Points

**Data Flow:**
1. L'interface de diffusion (`data/p2p`) reçoit un Gossip Protobuf.
2. Il est désérialisé et poussé dans un `Flow`.
3. Le `m04_gossip` UseCase intercepte le paquet, l'analyse via CRDT.
4. Si applicable, mise à jour dans la base locale (`data/local` -> Room).
5. Le `presentation/explorer` (Jetpack Compose) observe la base locale et met à jour l'UI instantanément via Flow sans avoir besoin de rafraîchir.

---

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
Toutes les décisions (Jetpack Compose, Clean Architecture, Raw Sockets, Room, Protobuf) sont parfaitement compatibles dans l'écosystème Kotlin moderne (2025/2026). Aucune friction technologique n'est identifiée.

**Pattern Consistency:**
Les patterns d'implémentation (Coroutines Dispatchers stricts, Injection via Hilt, retours via `Result<T>`) garantissent une protection absolue contre les fuites de mémoire et les crashs silencieux typiques des systèmes P2P.

**Structure Alignment:**
La structure du projet `mobicloud-android` (séparant `core/network` de `domain/usecase`) est le SEUL moyen viable de tester mathématiquement les algorithmes (Gossip, Erasure Coding, Bully) avec 100% de couverture sur une JVM CI/CD sans dépendre d'émulateurs Android instables.

### Requirements Coverage Validation ✅

**Epic/Feature Coverage:**
Les 10 modules du cahier des charges ont tous une place physique réservée dans l'arborescence `com.mobicloud.domain.usecase`. 

**Non-Functional Requirements Coverage:**
- **Batterie/Thermique :** Traité via ARM NEON SIMD, Android Keystore, et l'abdication forcée à 30 min.
- **Réseau/Bande Passante :** Traité via Protobuf, Filtres de Bloom, UDP Gossip, et Streaming direct.
- **Résilience (Churn) :** Traité via le Coupe-circuit Avalanche et le Buffer d'urgence électoral.

### Implementation Readiness Validation ✅

**Decision Completeness:**
Les fondations sont exhaustives. L'Agent Développeur n'aura pas d'ambiguïté sur les frameworks à utiliser ou l'emplacement des fichiers.

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed
- [x] Technical constraints identified
- [x] Cross-cutting concerns mapped

**✅ Architectural Decisions**
- [x] Critical decisions documented with versions
- [x] Technology stack fully specified
- [x] Integration patterns defined
- [x] Performance considerations addressed

**✅ Implementation Patterns**
- [x] Naming conventions established
- [x] Structure patterns defined
- [x] Communication patterns specified
- [x] Process patterns documented

**✅ Project Structure**
- [x] Complete directory structure defined
- [x] Component boundaries established
- [x] Integration points mapped
- [x] Requirements to structure mapping complete

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** HIGH - Le ratio contraintes matérielles / solutions logicielles est exceptionnellement bien équilibré grâce aux validations de niveau expert.

**Key Strengths:**
- Isolation totale de l'algorithmique mathématique P2P vis-à-vis du système Android.
- Prise en compte extrême des impondérables physiques mobiles.
- Sécurité hardware-backed native incassable.

**Implementation Handoff**

**AI Agent Guidelines:**
- Ne JAMAIS dévier de l'architecture sans repasser par le workflow de validation.
- Respecter strictement l'isolation `domain` vs `data`.
- Référez-vous constamment à `architecture.md` lors de la création de nouvelles Stories.

**First Implementation Priority:**
```bash
git clone https://github.com/atick-faisal/Jetpack-Android-Starter.git mobicloud-android
```
