  ---
  stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
  inputDocuments: ['prd.md', 'description_technique_formelle.md', 'epics.md']
  workflowType: 'architecture'
  project_name: 'PFE'
  user_name: 'Naoui'
  date: '2026-03-25T15:40:01+01:00'
  lastStep: 8
  status: 'complete'
  completedAt: '2026-03-25T16:30:00+01:00'
  lastRevision: '2026-04-13'
  revisionReason: 'Pivot architectural V4.0 — Fédération de Clusters Hybride (Firebase STUN/Tracker remplace DHT pure inter-réseaux, Hashcash retiré)'
  ---

  # Architecture Decision Document

  _This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

  ## Project Context Analysis

  ### Requirements Overview

  **Functional Requirements (V4.0 — Fédération de Clusters Hybride) :**
  Le système repose sur une topologie de **Fédération de Clusters** où un serveur Firebase minimaliste agit uniquement comme **STUN/Tracker de signalisation** pour permettre la découverte inter-réseaux (4G ↔ Wifi). À l'intérieur d'un cluster, la découverte reste purement P2P (UDP Multicast). L'orchestration interne s'appuie sur : l'**Algorithme Bully** (élection Super-Pair), la **DHT + Gossip/CRDT** (catalogue distribué), l'**Erasure Coding C++ NDK** (résilience des données), et le système **Karma** (équité Anti-Clandestins). Le transfert de fichiers est toujours en TCP direct P2P (Zero-Trust). Le Super-Pair élu s'enregistre sur Firebase pour être découvrable depuis d'autres réseaux.

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

  1. **Réseau P2P (Topologie Fédérée) :** L'architecture V4.0 adopte un modèle hybride. La découverte **locale** reste purement P2P via UDP Multicast (groupe `239.255.255.250:7777`). La découverte **inter-réseaux** utilise Firebase Realtime Database comme **Tracker STUN minimaliste** — il ne stocke que les métadonnées des Super-Pairs (`nodeId`, `ip`, `port`, `reliabilityScore`), aucune donnée utilisateur. Tous les transferts de fichiers restent TCP direct P2P.
  2. **Synchronisation (DHT + Gossip) :** Le protocole Gossip épidémique est circumscrit aux *Replica Sets* (nœuds gérant la même partition DHT) pour les métadonnées de catalogue. Un Gossip ultra-léger (Heartbeat) persiste pour la topologie vivant/mort. Firebase n'est jamais impliqué dans la synchronisation du catalogue — c'est une responsabilité DHT/CRDT exclusive.
  3. **Anti-Sybil (Keystore sans Hashcash) :** ⚠️ **Le Hashcash est retiré du scope V4.0.** L'anti-Sybil repose exclusivement sur l'**Android Keystore System** (hardware-backed EC P-256). L'identité (`KeyPair`) est générée une seule fois, persistée dans le TEE, et réutilisée pour signer tous les messages P2P. Cette approche épargne le CPU/batterie ARM lors des reconnexions tout en garantissant l'infalsifiabilité.
  4. **Scission et Élection (Buffer d'Urgence) :** Pour pallier le "vide de pouvoir" lors d'une ré-élection du Super-Pair (Bully), chaque nœud potentiel implémente un `LocalRepairBuffer` in-memory (max 50 entrées). Les requêtes d'auto-réparation sont émises dès que le nouveau coordinateur annonce son mandat. L'abdication automatique du Super-Pair est déclenchée après 30 minutes (exclusion de l'élection pendant 5 min).

  ---

  ## Realistic Scope Adjustments (PFE 2026)

  > [!IMPORTANT]
  > Suite à la révision du plan de charge pour la deadline du **03 Juin 2026**, l'architecture reste inchangée dans sa vision théorique mais l'implémentation effective subit les ajustements pragmatiques suivants pour garantir une soutenance stable :

  ### 1. Simplification Matérielle & IA
  - **Erasure Coding (SIMD NEON) :** L'implémentation utilisera une version C++ standard via JNI avec `DirectByteBuffer` (batching obligatoire). L'optimisation vectorielle NEON est reportée (perspective de rapport).
  - **Modèle de Fiabilité (TFLite) :** Le moteur d'inférence IA est remplacé par un `StaticMockTrustScore` injecté via Hilt. L'architecture `ITrustScoreProvider` est conservée pour intégration future.

  ### 2. Réduction des Protocoles de Sécurité & Consensus
  - **Hashcash Anti-Sybil :** ⚠️ **Retiré définitivement du scope V4.0.** Remplacé par la signature Android Keystore (hardware-backed) — solution plus élégante et moins consommatrice.
  - **Proof of Retrievability (PoR) :** Retiré du scope d'implémentation.
  - **Validation du Karma :** La certification collégiale par l'anneau DHT est simplifiée en une validation direct-peer signée (Anti-Replay conservé).
  - **Split-Brain :** Documenté dans le rapport mais non implémenté (trop complexe à reproduire en démo).

  ### 3. Tracker Firebase — Signalisation Minimaliste (NOUVEAU V4.0)
  - **Rôle :** Firebase Realtime Database sert uniquement de **carnet d'adresses** pour les Super-Pairs. Il ne stocke ni catalogue DHT, ni données utilisateur, ni fragments.
  - **Données stockées :** `super-peers/{nodeId}` → `{ip, port, reliabilityScore, electedAt}` + TTL 60s.
  - **Sécurité Firebase :** Les règles de sécurité Firebase n'autorisent l'écriture que sur son propre `nodeId`. Toute lecture est publique (annuaire de rencontre).
  - **Fallback :** Si Firebase est inaccessible, le nœud reste en mode Multicast local uniquement (`Result.Failure` remontée proprement via `SignalingRepository`).

  ### 4. Validation au Churn
  - La preuve de résilience s'appuiera sur des scénarios manuels documentés et des logs structurés plutôt que sur un simulateur de réseau virtuel automatisé dédié.

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
  - Persistent Node Identity Anti-Sybil (Android Keystore — **Hashcash retiré**)
  - P2P Communication Protocol (Raw Sockets : UDP Multicast + TCP direct)
  - **[NOUVEAU]** Inter-Network Signaling (Firebase Realtime Database — Tracker STUN uniquement)

  **Important Decisions (Shape Architecture):**
  - Local Database (Room SQLite)
  - Encryption Cipher (AES-256 GCM)
  - **[NOUVEAU]** `SignalingRepository` interface (Domain) → `SignalingRepositoryImpl` (Data/Firebase)

  **Deferred Decisions (Post-MVP):**
  - SIMD ARM NEON optimisation pour l'Erasure Coding (reporté en perspective rapport)

  ### Data Architecture

  - **Séralisation P2P :** `kotlinx.serialization` (Protobuf v1.10.x) pour une compression binaire maximale des messages Gossip et Heartbeat.
  - **Stockage Local Catalogue :** Jetpack Room (SQLite) pour la persistance locale de la partition DHT assignée au nœud, permettant des requêtes "Zéro-Latence" locales (< 100ms).

  ### Authentication & Security

  - **Identité du Nœud & Anti-Sybil :** Génération et persistance de la paire de clés asymétriques EC P-256 via l'**Android Keystore System** (Hardware-backed TEE). Empêche le clonage d'identité. ⚠️ **Hashcash N'EST PAS utilisé** (retiré V4.0).
  - **Chiffrement des Fragments :** AES-256 GCM avec clés éphémères dérivées `HKDF(FileMasterKey, BlockIndex)`. La `FileMasterKey` est chiffrée via ECIES avec la clé publique du destinataire. Clés éphémères en RAM uniquement.
  - **Firebase Security Rules :** Écriture restreinte à `super-peers/{own-nodeId}`, lecture publique. Aucune donnée sensible sur Firebase.

  ### API & Communication Patterns

  - **Transfert Data Lourd (Fragments) :** TCP Sockets directs via Kotlin Coroutines (`Dispatchers.IO`). Jamais multi-sauts.
  - **Découverte Locale (Heartbeat) :** UDP Multicast groupe `239.255.255.250:7777` — périmètre intra-sous-réseau.
  - **Signalisation Inter-Réseaux (NOUVEAU V4.0) :** Firebase Realtime Database SDK (Android). Le nœud local lit les `super-peers/*` pour découvrir les Super-Pairs distants. Le Super-Pair élu écrit son entrée avec `onDisconnect().removeValue()` pour le nettoyage automatique.
  - **Pattern de Réveil Asynchrone :** Foreground Service avec `MulticastLock` Wi-Fi maintient l'écoute réseau active. Réveil via interruption I/O (socket TCP accepté) ou datagramme UDP `URGENT`.

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
  ├── gradle/libs.versions.toml             ← firebase-bom, firebase-database ajoutés
  ├── google-services.json                  ← [NOUVEAU] Config Firebase projet
  ├── app/
  │   ├── src/main/AndroidManifest.xml
  │   ├── src/main/kotlin/com/mobicloud/    ← package kotlin (non java)
  │   │   ├── MobicloudApplication.kt
  │   │   ├── di/                           (Hilt Modules)
  │   │   │   └── SignalingModule.kt        ← [NOUVEAU] Bind SignalingRepository
  │   │   ├── core/                         (Préoccupations Transverses)
  │   │   │   ├── network/                  (Raw Sockets, UDP Multicast, TCP)
  │   │   │   │   └── NetworkChangeObserver.kt  ← Détection basculement Wifi→4G
  │   │   │   ├── security/                 (Android Keystore, AES-256 GCM, ECIES)
  │   │   │   │   └── FragmentCipherUseCase.kt
  │   │   │   ├── erasure/                  (JNI Bridge C++ NDK)
  │   │   │   │   └── ErasureCodingJni.kt   ← DirectByteBuffer batching
  │   │   │   ├── database/                 (Room DB config)
  │   │   │   └── format/                   (Protobuf + ignoreUnknownKeys=true)
  │   │   ├── domain/                       (Pure Kotlin — Zero Android imports)
  │   │   │   ├── models/                   (NodeIdentity, Fragment, CatalogEntry,
  │   │   │   │                              DhtEntry, KarmaTransaction, NodeRole)
  │   │   │   ├── repository/               (Interfaces pures)
  │   │   │   │   ├── PeerRepository.kt
  │   │   │   │   ├── DhtRepository.kt
  │   │   │   │   ├── IdentityRepository.kt
  │   │   │   │   └── SignalingRepository.kt ← [NOUVEAU] Interface Tracker Firebase
  │   │   │   └── usecase/
  │   │   │       ├── m01_discovery/         (CalculateReliabilityScoreUseCase)
  │   │   │       ├── m03_m04_gossip_heartbeat/ (GossipSyncUseCase)
  │   │   │       ├── m05_dht_catalog/       (ResolveDhtConflictUseCase)
  │   │   │       ├── m06_m07_repair_migration/ (OrchestrateBlockMigrationUseCase,
  │   │   │       │                              TriggerAutoRepairUseCase,
  │   │   │       │                              CircuitBreakerUseCase)
  │   │   │       ├── m08_m09_erasure_coding/ (EncodeErasureFragmentsUseCase,
  │   │   │       │                            DecodeErasureFragmentsUseCase)
  │   │   │       ├── m09_karma/             (UpdateKarmaScoreUseCase)
  │   │   │       └── m10_election/          (RunBullyElectionUseCase)
  │   │   ├── data/                          (Couche Implémentation)
  │   │   │   ├── local/                     (Room DAOs + DataStore)
  │   │   │   │   ├── PeerDao.kt
  │   │   │   │   ├── DhtDao.kt
  │   │   │   │   └── IdentityDao.kt
  │   │   │   ├── p2p/                       (Impl Canaux UDP + TCP)
  │   │   │   │   ├── UdpHeartbeatBroadcaster.kt  ← Existant
  │   │   │   │   └── TcpBlockTransferChannel.kt
  │   │   │   └── repository/                (Implémentations)
  │   │   │       ├── PeerRepositoryImpl.kt
  │   │   │       ├── DhtRepositoryImpl.kt
  │   │   │       ├── IdentityRepositoryImpl.kt
  │   │   │       └── SignalingRepositoryImpl.kt ← [NOUVEAU] Firebase SDK impl
  │   │   └── presentation/                  (Jetpack Compose UI)
  │   │       ├── theme/                     (Dark OLED #000000, Material3)
  │   │       ├── dashboard/                 (ReliabilityGauge, KpiDiagnosticCard,
  │   │       │                              RadarLogConsole)
  │   │       └── explorer/                  (DHT File Explorer, ErasureProgress,
  │   │                                       ModalBottomSheet Karma)
  │   ├── src/test/kotlin/com/mobicloud/     (Tests unitaires JVM — sans émulateur)
  │   └── src/androidTest/                   (Tests intégration)
  └── cpp/                                   ← [NOUVEAU] Sources NDK C++
      └── erasure_coding/
          └── erasure_jni.cpp                (Galois Field GF(256) + JNI bridge)
  ```

  ### Architectural Boundaries

  **Device Boundaries (Core vs Domain) :**
  La couche `core/network` cache entièrement la complexité Android (Multicast UDP, `MulticastLock`, TCP Sockets, `NetworkChangeObserver`). Le `domain` ne voit que des interfaces pures réactives (`Flow<P2PMessage>`). Vitale pour la testabilité JVM sans émulateur.

  **Security Boundaries :**
  Seul `core/security` interagit avec l'`AndroidKeyStore`. Seul `data/repository/SignalingRepositoryImpl.kt` interagit avec le SDK Firebase. Le `domain` consomme uniquement des interfaces abstraites (`SignalingRepository`). La frontière est stricte : **aucun import Firebase dans domain/**.

  **Firebase Boundary (NOUVEAU V4.0) :**
  Firebase n'est qu'un détail d'implémentation de la couche `Data`. Les règles strictes :
  - ❌ Aucun objet `DatabaseReference` ou `FirebaseDatabase` dans `domain/` ou `core/`
  - ✅ `SignalingRepository.kt` (domain) : interface pure Kotlin avec `suspend fun` et `Flow<>`
  - ✅ `SignalingRepositoryImpl.kt` (data) : implémente le bridge Firebase ↔ domain models
  - ✅ `SignalingModule.kt` (di) : Hilt bind `SignalingRepository` → `SignalingRepositoryImpl`

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

  ## Architecture Validation Results — Révision V4.0 (2026-04-13)

  ### Coherence Validation ✅

  **Decision Compatibility:**
  Toutes les décisions (Jetpack Compose, Clean Architecture, Raw Sockets, Room, Protobuf, **Firebase SDK**) sont parfaitement compatibles dans l'écosystème Kotlin moderne. Firebase Realtime Database Android SDK est mature et s'intègre nativement avec les Coroutines Kotlin via les extensions KTX. Aucune friction technologique identifiée.

  **Pattern Consistency :**
  L'ajout de Firebase respecte strictement la Clean Architecture grâce à la frontière `SignalingRepository`. L'implémentation Firebase est invisible du `domain`. Les patterns existants (Coroutines, Hilt, `Result<T>`) sont préservés et étendus à la couche signaling.

  **Structure Alignment :**
  La séparation `core/network` (P2P local) vs `data/repository/SignalingRepositoryImpl` (Firebase) vs `domain/repository/SignalingRepository` (interface) garantit la testabilité totale des algorithmes P2P en JVM sans Firebase ni émulateur.

  ### Requirements Coverage Validation ✅

  **PRD V4.0 Coverage :**
  | Exigence PRD V4.0 | Couverture Architecturale |
  |---|---|
  | FR-01.1 (UDP Multicast local) | `core/network/` + Foreground Service |
  | FR-01.2 (Firebase STUN/Tracker) | `data/repository/SignalingRepositoryImpl.kt` |
  | FR-01.3 (TCP P2P Zero-Trust) | `data/p2p/TcpBlockTransferChannel.kt` |
  | FR-02.2 (Bully Election + Firebase enregistrement) | `m10_election/RunBullyElectionUseCase.kt` + `SignalingRepository` |
  | FR-03.1/03.2 (Erasure C++ + AES-256 GCM) | `cpp/erasure_coding/` + `core/security/` |
  | FR-04.1/04.2 (DHT + Gossip CRDT) | `m05_dht_catalog/` + `m03_m04_gossip_heartbeat/` |
  | FR-06.1 (Migration proactive) | `m06_m07_repair_migration/` |
  | FR-07.1 (Karma Anti-Clandestin) | `m09_karma/UpdateKarmaScoreUseCase.kt` |

  **Non-Functional Requirements Coverage :**
  - **NFR-01 (Convergence CRDT ≤ 3s) :** Gossip fan-out=2 cycles de 2s + Filtres de Bloom (échanges delta uniquement).
  - **NFR-02 (Migration < 5s) :** `NetworkChangeObserver` → `DEPARTURE_NOTICE` → `OrchestrateBlockMigrationUseCase`.
  - **NFR-03 (Overhead CPU ≤ 5%) :** Gossip ultra-léger (Bloom), Hashcash retiré, Erasure via NDK.

  ### Implementation Readiness Validation ✅

  **Decision Completeness :**
  L'Agent Développeur a une clarté totale sur : les frameworks à utiliser, l'emplacement de chaque fichier, les frontières d'architecture, et les règles Firebase. Aucune ambiguïté.

  ### Architecture Completeness Checklist V4.0

  **✅ Requirements Analysis**
  - [x] Contexte projet V4.0 analysé (Fédération Clusters Hybride)
  - [x] Firebase pivot documenté et intégré
  - [x] Contraintes Android (Foreground Service, MulticastLock) identifiées
  - [x] Préoccupations transverses mappées

  **✅ Architectural Decisions**
  - [x] Décisions critiques documentées (Firebase remplace DHT inter-réseau)
  - [x] Stack technologique complet avec Firebase SDK
  - [x] Retrait Hashcash documenté et justifié
  - [x] Patterns d'intégration définis

  **✅ Implementation Patterns**
  - [x] Conventions de nommage établies
  - [x] Frontières Firebase strictes définies (`domain` zero-Firebase)
  - [x] Patterns de communication spécifiés (UDP local + TCP data + Firebase signaling)
  - [x] Patterns de gestion d'erreurs documentés (`Result<T>` obligatoire)

  **✅ Project Structure**
  - [x] Arborescence complète définie (inclut `cpp/`, `SignalingRepositoryImpl`, `google-services.json`)
  - [x] Frontières composants établies
  - [x] Points d'intégration cartographiés
  - [x] Mapping PRD V4.0 → structure complet

  ### Architecture Readiness Assessment — V4.0

  **Overall Status:** ✅ READY FOR IMPLEMENTATION

  **Confidence Level:** HIGH — L'architecture V4.0 est pragmatique, démontrable et scientifiquement justifiée pour un PFE.

  **Key Strengths V4.0 :**
  - Signalisation Firebase élégante et non intrusive (Firebase = détail d'implémentation isolé).
  - Suppression du Hashcash simplifiée par Keystore hardware — plus robuste, moins énergivore.
  - Algorithmes P2P avancés (Bully, DHT, Gossip, CRDT, Karma) 100% testables en JVM pur.
  - Structure NDK C++ pour Erasure Coding proprement séparée du reste du projet.

  **Implementation Handoff**

  **AI Agent Guidelines (V4.0) :**
  - Ne JAMAIS importer Firebase dans `domain/` ou `core/`.
  - Respecter strictement l'isolation `domain` (interfaces) vs `data` (implémentations Firebase/Room/TCP).
  - Utiliser `Result<T>` / `sealed class Resource<T>` pour tout retour de couche Data ou UseCase.
  - Référez-vous constamment à `architecture.md` (V4.0) lors de la création de nouvelles Stories.
  - Le `google-services.json` est le seul fichier d'intégration Firebase au niveau projet.

  **Stack de Dépendances V4.0 (libs.versions.toml additions) :**
  ```toml
  [versions]
  firebase-bom = "33.x.x"  # Vérifier dernière version stable

  [libraries]
  firebase-database = { group = "com.google.firebase", name = "firebase-database-ktx" }
  firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics-ktx" }
  ```
