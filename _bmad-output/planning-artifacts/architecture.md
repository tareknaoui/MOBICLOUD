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
  lastRevision: '2026-04-28'
  revisionReason: 'Pivot V5.0 — Suppression totale de Firebase et consolidation de la signalisation + transport sur la couche Serveurs Relais HA WebSocket (Zero-Firebase). Karma/Weight retiré.'
  ---

  # Architecture Decision Document

  _This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

  ## Project Context Analysis

  ### Requirements Overview

  **Functional Requirements (V5.0 — Fédération de Clusters Hybride avec Serveurs Relais HA) :**
  Le système repose sur une topologie de **Fédération de Clusters** où un cluster de **Serveurs Relais HA WebSocket** (min 2 instances Node.js, Zero-Knowledge) assure conjointement deux rôles : (1) **Signalisation** — annuaire en mémoire des Super-Pairs (`REGISTER_PEER`/`GET_PEERS`) ; (2) **Transport** — fallback Store-and-Forward 60s pour les blocs binaires chiffrés quand le P2P direct échoue (NAT symétrique). À l'intérieur d'un cluster, la découverte locale reste purement P2P (UDP Multicast). L'orchestration interne s'appuie sur : l'**Algorithme Bully** (élection Super-Pair), la **DHT + Gossip/CRDT** (catalogue distribué), et l'**Erasure Coding C++ NDK** (résilience des données). Le transfert de fichiers est prioritairement TCP direct P2P (Zero-Trust) avec fallback transparent vers les Serveurs Relais HA. **Aucune dépendance à Firebase ou tout autre service tiers.**

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

  1. **Réseau P2P (Topologie Fédérée V5.0) :** L'architecture adopte un modèle hybride Zero-Firebase. La découverte **locale** reste purement P2P via UDP Multicast (groupe `239.255.255.250:7777`). La découverte **inter-réseaux** utilise un cluster de **Serveurs Relais HA WebSocket** (min 2 instances Node.js sur Render/Railway) comme annuaire dynamique — ils ne stockent que les métadonnées des Super-Pairs en RAM (`nodeId`, `ip`, `port`, `reliabilityScore`) avec TTL 60s, aucune donnée utilisateur. Le transfert de fichiers reste prioritairement TCP direct P2P avec fallback Store-and-Forward via les mêmes serveurs HA quand le NAT bloque.
  2. **Synchronisation (DHT + Gossip) :** Le protocole Gossip épidémique est circumscrit aux *Replica Sets* (nœuds gérant la même partition DHT) pour les métadonnées de catalogue. Un Gossip ultra-léger (Heartbeat) persiste pour la topologie vivant/mort. Les Serveurs Relais HA ne sont JAMAIS impliqués dans la synchronisation du catalogue — c'est une responsabilité DHT/CRDT exclusive.
  3. **Anti-Sybil (Keystore sans Hashcash) :** ⚠️ **Le Hashcash est retiré du scope V4.0.** L'anti-Sybil repose exclusivement sur l'**Android Keystore System** (hardware-backed EC P-256). L'identité (`KeyPair`) est générée une seule fois, persistée dans le TEE, et réutilisée pour signer tous les messages P2P **et** les handshakes WebSocket vers les Serveurs Relais HA.
  4. **Scission et Élection (Buffer d'Urgence) :** Pour pallier le "vide de pouvoir" lors d'une ré-élection du Super-Pair (Bully), chaque nœud potentiel implémente un `LocalRepairBuffer` in-memory (max 50 entrées). Les requêtes d'auto-réparation sont émises dès que le nouveau coordinateur annonce son mandat. L'abdication automatique du Super-Pair est déclenchée après 30 minutes (exclusion de l'élection pendant 5 min).
  5. **Signalisation & Relais HA (Consolidé V5.0) :** Pour garantir la disponibilité de la couche signaling + transport inter-réseaux, un cluster de serveurs Node.js (`ws` 8.x) est déployé en **min 2 instances indépendantes** (régions différentes — ex: Render + Railway). Protocole binaire WSS, authentification systématique via signature Android Keystore au handshake. Les serveurs sont **zero-knowledge** pour les blocs (AES-256 GCM opaque) et agissent comme **annuaire RAM dynamique** pour la signalisation. Failover client séquentiel automatique en cas d'instance HS.

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
  - **Système Karma / Weight (Anti-Clandestin) :** ⚠️ **Retiré définitivement du scope V4.0.** La contribution thèse repose désormais sur mobile-native + topologie super-peer/cluster ; le système d'incentives est documenté en perspective rapport.
  - **Split-Brain :** Documenté dans le rapport mais non implémenté (trop complexe à reproduire en démo).

  ### 3. Suppression de Firebase — Signalisation sur Serveurs Relais HA (PIVOT V5.0)
  - **Rôle :** Le cluster de Serveurs Relais HA WebSocket (min 2 instances Node.js) assume désormais le rôle de **carnet d'adresses** des Super-Pairs ET de **fallback de transport** pour les blocs binaires.
  - **Données stockées (Signaling RAM) :** Annuaire en mémoire des Super-Pairs actifs (`nodeId`, `ip`, `port`, `reliabilityScore`, `last_seen`) avec TTL 60s. Aucune persistance disque, aucune base de données.
  - **Données stockées (Relay RAM) :** Buffer Store-and-Forward des blocs chiffrés en attente de livraison (TTL 60s, RAM uniquement, purge automatique).
  - **Sécurité :** Authentification obligatoire par signature Android Keystore EC P-256 sur tous les handshakes WebSocket (`REGISTER_PEER` et `UPLOAD`). Les blocs sont AES-256 GCM opaques (Zero-Knowledge — le serveur ne peut pas déchiffrer).
  - **Hébergement :** Render.com / Railway.app via Docker (`relay-server/Dockerfile`, base `node:20-slim`).
  - **Découverte & Failover :** URLs des Serveurs Relais HA hardcodées dans la config Android (ou servies via DNS futur). Le client gère le **failover séquentiel automatique** entre instances en cas d'échec.
  - **Fallback Local :** Si tous les Serveurs Relais HA sont inaccessibles, le nœud reste en mode Multicast local uniquement (`Result.Failure` remontée proprement via `SignalingRepository`).

  ### 4. Validation au Churn
  - La preuve de résilience s'appuiera sur des scénarios manuels documentés et des logs structurés plutôt que sur un simulateur de réseau virtuel automatisé dédié.

  ### 5. Serveurs Relais WebSocket HA (V5.0 — Haute Disponibilité)
  - **Redondance :** Déploiement de minimum **deux instances indépendantes** (ex: Render + Railway, ou deux régions différentes).
  - **Protocole :** WebSocket binaire sur port 443 (WSS).
  - **Buffering :** RAM uniquement (Store-and-Forward), TTL 60s par bloc.
  - **Capacité par bloc :** ~1 MB (fragments MobiCloud).
  - **Endpoint santé :** `GET /health` retourne sessions actives + blocs en attente.
  - **Graceful shutdown :** Gestion SIGTERM avec drainage des connexions en cours.

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
  - **[V5.0]** Inter-Network Signaling + Transport HA (Cluster Serveurs Relais Node.js WebSocket — Zero-Firebase)

  **Important Decisions (Shape Architecture):**
  - Local Database (Room SQLite)
  - Encryption Cipher (AES-256 GCM)
  - **[V5.0]** `SignalingRepository` + `RelayRepository` interfaces (Domain) → impl WebSocket HA (Data) — pattern unifié
  - **[V5.0]** `BlockSenderWithRelay` wrapper qui implémente try-direct-then-relay-with-failover

  **Deferred Decisions (Post-MVP):**
  - SIMD ARM NEON optimisation pour l'Erasure Coding (reporté en perspective rapport)

  ### Data Architecture

  - **Séralisation P2P :** `kotlinx.serialization` (Protobuf v1.10.x) pour une compression binaire maximale des messages Gossip et Heartbeat.
  - **Stockage Local Catalogue :** Jetpack Room (SQLite) pour la persistance locale de la partition DHT assignée au nœud, permettant des requêtes "Zéro-Latence" locales (< 100ms).

  ### Authentication & Security

  - **Identité du Nœud & Anti-Sybil :** Génération et persistance de la paire de clés asymétriques EC P-256 via l'**Android Keystore System** (Hardware-backed TEE). Empêche le clonage d'identité. ⚠️ **Hashcash N'EST PAS utilisé** (retiré V4.0).
  - **Chiffrement des Fragments :** AES-256 GCM avec clés éphémères dérivées `HKDF(FileMasterKey, BlockIndex)`. La `FileMasterKey` est chiffrée via ECIES avec la clé publique du destinataire. Clés éphémères en RAM uniquement.
  - **Sécurité Serveurs Relais HA :** Authentification stricte par signature Android Keystore EC P-256 sur tous les handshakes WebSocket (`REGISTER_PEER` et `UPLOAD`). Le serveur est zero-knowledge — il ne peut pas déchiffrer les blocs (AES-256 GCM opaque). Aucune donnée sensible persistée (RAM uniquement, TTL 60s).

  ### API & Communication Patterns

  - **Transfert Data Lourd (Fragments) :** TCP Sockets directs via Kotlin Coroutines (`Dispatchers.IO`). Jamais multi-sauts.
  - **Découverte Locale (Heartbeat) :** UDP Multicast groupe `239.255.255.250:7777` — périmètre intra-sous-réseau.
  - **Signalisation Inter-Réseaux (V5.0) :** Communication directe avec les Serveurs Relais HA via WebSocket binaire (OkHttp). Le Super-Pair élu envoie `REGISTER_PEER` (frame binaire signée EC P-256). Les autres nœuds envoient `GET_PEERS` pour récupérer l'annuaire. Le nettoyage est automatique via TTL RAM 60s côté serveur.
  - **Transfert Relais HA (V5.0) :** Fallback automatique transparent. Si TCP direct échoue (NAT symétrique), `BlockSenderWithRelay` bascule sur `RelayRepository.uploadBlock()` qui pousse le bloc binaire chiffré au Serveur Relais HA. Le serveur le forward au destinataire dès qu'il est en ligne (Store-and-Forward 60s RAM).
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
  ├── gradle/libs.versions.toml             ← OkHttp pour WebSocket (Firebase RETIRÉ)
  ├── relay-server/                         ← [V5.0] Serveur Node.js HA (package.json, server.js, Dockerfile)
  ├── app/
  │   ├── src/main/AndroidManifest.xml
  │   ├── src/main/kotlin/com/mobicloud/    ← package kotlin (non java)
  │   │   ├── MobicloudApplication.kt
  │   │   ├── di/                           (Hilt Modules)
  │   │   │   ├── SignalingModule.kt        ← Bind SignalingRepository (HA WebSocket)
  │   │   │   ├── RelayModule.kt            ← [V5.0] Bind RelayRepository + RelayWebSocketClient
  │   │   │   └── BlockTransferModule.kt    ← Bind BlockSender → BlockSenderWithRelay
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
  │   │   │   │                              DhtEntry, NodeRole)
  │   │   │   ├── repository/               (Interfaces pures)
  │   │   │   │   ├── PeerRepository.kt
  │   │   │   │   ├── DhtRepository.kt
  │   │   │   │   ├── IdentityRepository.kt
  │   │   │   │   ├── SignalingRepository.kt ← Interface Signaling (HA WebSocket)
  │   │   │   │   ├── RelayRepository.kt    ← [V5.0] Interface Relay (HA WebSocket)
  │   │   │   │   └── BlockSender.kt         ← Interface envoi bloc (impl: TCP-then-Relay)
  │   │   │   └── usecase/
  │   │   │       ├── m01_discovery/         (CalculateReliabilityScoreUseCase)
  │   │   │       ├── m03_m04_gossip_heartbeat/ (GossipSyncUseCase)
  │   │   │       ├── m05_dht_catalog/       (ResolveDhtConflictUseCase)
  │   │   │       ├── m06_m07_repair_migration/ (OrchestrateBlockMigrationUseCase,
  │   │   │       │                              TriggerAutoRepairUseCase,
  │   │   │       │                              CircuitBreakerUseCase)
  │   │   │       ├── m08_m09_erasure_coding/ (EncodeErasureFragmentsUseCase,
  │   │   │       │                            DecodeErasureFragmentsUseCase)
  │   │   │       └── m10_election/          (RunBullyElectionUseCase)
  │   │   ├── data/                          (Couche Implémentation)
  │   │   │   ├── local/                     (Room DAOs + DataStore)
  │   │   │   │   ├── PeerDao.kt
  │   │   │   │   ├── DhtDao.kt
  │   │   │   │   └── IdentityDao.kt
  │   │   │   ├── p2p/                       (Impl Canaux UDP + TCP + WebSocket)
  │   │   │   │   ├── UdpHeartbeatBroadcaster.kt
  │   │   │   │   ├── tcp/BlockTransferClient.kt    ← TCP direct
  │   │   │   │   ├── websocket/RelayWebSocketClient.kt ← [V5.0] WSS unifié
  │   │   │   │   └── BlockSenderWithRelay.kt   ← [V5.0] try-direct-then-relay
  │   │   │   └── repository/                (Implémentations)
  │   │   │       ├── PeerRepositoryImpl.kt
  │   │   │       ├── DhtRepositoryImpl.kt
  │   │   │       ├── IdentityRepositoryImpl.kt
  │   │   │       ├── SignalingRepositoryImpl.kt ← [V5.0] HA WebSocket impl (PAS Firebase)
  │   │   │       └── RelayRepositoryImpl.kt    ← [V5.0] HA WebSocket impl
  │   │   └── presentation/                  (Jetpack Compose UI)
  │   │       ├── theme/                     (Dark OLED #000000, Material3)
  │   │       ├── dashboard/                 (ReliabilityGauge, KpiDiagnosticCard,
  │   │       │                              RadarLogConsole)
  │   │       └── explorer/                  (DHT File Explorer, ErasureProgress,
  │   │                                       ModalBottomSheet)
  │   ├── src/test/kotlin/com/mobicloud/     (Tests unitaires JVM — sans émulateur)
  │   └── src/androidTest/                   (Tests intégration)
  ├── cpp/                                   ← [NOUVEAU] Sources NDK C++
  │   └── erasure_coding/
  │       └── erasure_jni.cpp                (Galois Field GF(256) + JNI bridge)
  └── relay-server/                          ← [V5.0] Cluster Node.js HA (min 2 instances)
      ├── package.json                       (dépendance ws 8.x, Jest)
      ├── server.js                          (REGISTER_PEER, GET_PEERS, UPLOAD, FORWARD)
      ├── Dockerfile                         (node:20-slim, EXPOSE 10000)
      └── .dockerignore
  ```

  ### Architectural Boundaries

  **Device Boundaries (Core vs Domain) :**
  La couche `core/network` cache entièrement la complexité Android (Multicast UDP, `MulticastLock`, TCP Sockets, `NetworkChangeObserver`). Le `domain` ne voit que des interfaces pures réactives (`Flow<P2PMessage>`). Vitale pour la testabilité JVM sans émulateur.

  **Security Boundaries :**
  Seul `core/security` interagit avec l'`AndroidKeyStore`. Seuls `data/repository/SignalingRepositoryImpl.kt` et `data/repository/RelayRepositoryImpl.kt` interagissent avec OkHttp/WebSocket. Le `domain` consomme uniquement des interfaces abstraites. La frontière est stricte : **aucun import OkHttp/WebSocket dans domain/**. **Aucune dépendance Firebase nulle part.**

  **Server Boundary (V5.0 — Zero-Firebase) :**
  La couche Serveurs Relais HA n'est qu'un détail d'implémentation de la couche `Data`. Les règles strictes :
  - ❌ Aucun objet `OkHttpClient`, `WebSocket` ou `WebSocketListener` dans `domain/` ou `core/`
  - ❌ Aucune dépendance Firebase autorisée (PRD V5.0)
  - ✅ `SignalingRepository.kt` + `RelayRepository.kt` (domain) : interfaces pures Kotlin avec `suspend fun` et `Flow<>`
  - ✅ `SignalingRepositoryImpl.kt` + `RelayRepositoryImpl.kt` (data) : implémentent le bridge WebSocket ↔ domain models
  - ✅ `SignalingModule.kt` + `RelayModule.kt` (di) : Hilt bindings

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

  ## Architecture Validation Results — Révision V5.0 (2026-04-28)

  ### Coherence Validation ✅

  **Decision Compatibility:**
  Toutes les décisions (Jetpack Compose, Clean Architecture, Raw Sockets, Room, Protobuf, **OkHttp WebSocket**) sont parfaitement compatibles dans l'écosystème Kotlin moderne. OkHttp est déjà en dépendance transitive via Ktor — aucun ajout réseau majeur. Le serveur Node.js Relay (Docker) est isolé du projet Android. Aucune friction technologique identifiée.

  **Pattern Consistency :**
  La couche HA Signaling+Relay respecte strictement la Clean Architecture grâce aux frontières `SignalingRepository` + `RelayRepository`. L'implémentation WebSocket est invisible du `domain`. Les patterns existants (Coroutines, Hilt, `Result<T>`, `callbackFlow`) sont préservés.

  **Structure Alignment :**
  La séparation `core/network` (P2P local) vs `data/p2p/websocket/RelayWebSocketClient` (HA) vs `domain/repository/{Signaling,Relay}Repository` (interfaces) garantit la testabilité totale des algorithmes P2P en JVM via MockWebServer/MockK.

  ### Requirements Coverage Validation ✅

  **PRD V5.0 Coverage :**
  | Exigence PRD V5.0 | Couverture Architecturale |
  |---|---|
  | FR-01.1 (UDP Multicast local) | `core/network/` + Foreground Service |
  | FR-01.2 (Serveurs Relais HA — Signaling) | `data/repository/SignalingRepositoryImpl.kt` (WebSocket) + `relay-server/` |
  | FR-01.3 (TCP P2P Zero-Trust) | `data/p2p/tcp/BlockTransferClient.kt` |
  | FR-02.2 (Bully Election + Enregistrement HA) | `m10_election/RunBullyElectionUseCase.kt` + `SignalingRepository` |
  | FR-03.1/03.2 (Erasure C++ + AES-256 GCM) | `cpp/erasure_coding/` + `core/security/` |
  | FR-04.1/04.2 (DHT + Gossip CRDT) | `m05_dht_catalog/` + `m03_m04_gossip_heartbeat/` |
  | FR-06.1 (Migration proactive) | `m06_m07_repair_migration/` |
  | FR-08.1 (Relais HA Fallback Zero-Knowledge) | `data/repository/RelayRepositoryImpl.kt` + `data/p2p/BlockSenderWithRelay.kt` + `relay-server/` |

  **Non-Functional Requirements Coverage :**
  - **NFR-01 (Convergence CRDT ≤ 3s) :** Gossip fan-out=2 cycles de 2s + Filtres de Bloom (échanges delta uniquement).
  - **NFR-02 (Migration < 5s) :** `NetworkChangeObserver` → `DEPARTURE_NOTICE` → `OrchestrateBlockMigrationUseCase`.
  - **NFR-03 (Overhead CPU ≤ 5%) :** Gossip ultra-léger (Bloom), Hashcash retiré, Erasure via NDK.

  ### Implementation Readiness Validation ✅

  **Decision Completeness :**
  L'Agent Développeur a une clarté totale sur : les frameworks à utiliser, l'emplacement de chaque fichier, les frontières d'architecture, et les règles Zero-Firebase. Aucune ambiguïté.

  ### Architecture Completeness Checklist V5.0

  **✅ Requirements Analysis**
  - [x] Contexte projet V5.0 analysé (Fédération Clusters Hybride + Serveurs Relais HA)
  - [x] Pivot Zero-Firebase documenté et intégré (signaling + transport consolidés sur HA WebSocket)
  - [x] Contraintes Android (Foreground Service, MulticastLock) identifiées
  - [x] Préoccupations transverses mappées

  **✅ Architectural Decisions**
  - [x] Décisions critiques documentées (Serveurs Relais HA remplacent Firebase pour signaling ET fournissent fallback transport)
  - [x] Stack technologique complet (OkHttp WebSocket côté Android, Node.js `ws` côté serveur)
  - [x] Retrait Hashcash + Karma/Weight documenté et justifié
  - [x] Patterns d'intégration définis (failover séquentiel multi-instance HA)

  **✅ Implementation Patterns**
  - [x] Conventions de nommage établies
  - [x] Frontières Clean Architecture strictes définies (`domain` zero-OkHttp/zero-Firebase)
  - [x] Patterns de communication spécifiés (UDP local + TCP data direct + WebSocket HA fallback)
  - [x] Patterns de gestion d'erreurs documentés (`Result<T>` obligatoire)

  **✅ Project Structure**
  - [x] Arborescence complète définie (inclut `cpp/`, `relay-server/`, `RelayWebSocketClient`, `BlockSenderWithRelay`)
  - [x] Frontières composants établies
  - [x] Points d'intégration cartographiés
  - [x] Mapping PRD V5.0 → structure complet

  ### Architecture Readiness Assessment — V5.0

  **Overall Status:** ✅ READY FOR IMPLEMENTATION

  **Confidence Level:** HIGH — L'architecture V5.0 est pragmatique, démontrable, scientifiquement justifiée et 100% indépendante de services tiers.

  **Key Strengths V5.0 :**
  - Couche HA Signaling+Relay unifiée — un seul serveur Node.js gère les 2 rôles, élégant et minimaliste.
  - Zero-Firebase complet — démontre la décentralisation effective et défend le narratif thèse "le moins centralisé possible".
  - Signature Keystore unifiée pour P2P et handshake WebSocket — robuste et énergétiquement efficace.
  - Algorithmes P2P avancés (Bully, DHT, Gossip, CRDT) 100% testables en JVM pur.
  - Failover multi-instance HA + fallback Multicast local = haute résilience.
  - Structure NDK C++ pour Erasure Coding proprement séparée du reste du projet.

  **Implementation Handoff**

  **AI Agent Guidelines (V5.0) :**
  - Ne JAMAIS importer OkHttp / WebSocket / Firebase dans `domain/` ou `core/`.
  - Respecter strictement l'isolation `domain` (interfaces) vs `data` (implémentations WebSocket/Room/TCP).
  - Utiliser `Result<T>` / `sealed class Resource<T>` pour tout retour de couche Data ou UseCase.
  - Référez-vous constamment à `architecture.md` (V5.0) lors de la création de nouvelles Stories.
  - Aucun `google-services.json`, aucune dépendance `firebase-*` autorisée.

  **Stack de Dépendances V5.0 (libs.versions.toml additions) :**
  ```toml
  [versions]
  okhttp = "4.12.0"  # WebSocket client + déjà transitif via Ktor

  [libraries]
  okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
  okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
  ```

  **Dépendances RETIRÉES en V5.0 :**
  - ❌ `firebase-bom`, `firebase-database-ktx`, `firebase-analytics-ktx` (toute la stack Firebase)
  - ❌ `google-services.json` au niveau projet
  - ❌ Plugin Gradle `com.google.gms.google-services`
