---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments: ['prd.md', 'architecture.md', 'ux-design-specification.md']
---

# MobiCloud - Epic Breakdown

## Overview

Ce document fournit la décomposition complète des épics et stories pour MobiCloud V4.0 (Architecture Fédération de Clusters Hybride). Il décompose les exigences du PRD V4.0, de l'Architecture, et de la Spécification UX en stories implémentables et actionnables.

## Requirements Inventory

### Functional Requirements

- FR-01.1: Multicast UDP pour la découverte locale au sein d'un même sous-réseau, sans serveur central. (P0)
- FR-01.2: Serveur Tracker agissant **uniquement** comme STUN/signaling pour fédérer des réseaux séparés (NAT) et lier les Super-Pairs. (P0)
- FR-01.3: Tous les transferts (catalogue ou fichiers) se font en P2P direct de nœud à nœud (Zero-Trust, Zero-Knowledge). (P0)
- FR-02.1: Calcul du Score de Fiabilité (Batterie, Uptime, IP locale) par chaque appareil local. (P0)
- FR-02.2: Élection locale d'un Super-Pair strictement via l'Algorithme Bully. Le gagnant relaie sa table de routage sur le Tracker STUN pour rejoindre la fédération. (P0)
- FR-03.1: Erasure Coding vectoriel (C++ NDK) divisant le fichier en K+N blocs sans réplication redondante. (P0)
- FR-03.2: Chiffrement asymétrique des fragments (Zero-Trust) — l'hébergeur ne peut pas lire le bloc qu'il stocke. (P0)
- FR-04.1: Index global distribué dans un anneau DHT entre tous les pairs qualifiés du cluster (remplacement SQLite centralisé). (P0)
- FR-04.2: Synchronisation de la DHT par protocole Gossip épidémique avec CRDT (convergence garantie sans autorité centrale). (P0)
- FR-06.1: Migration proactive des blocs d'un nœud quittant un cluster (basculement réseau) vers le cluster local avant déconnexion. (P1)
- FR-07.1: Système de Karma : gain lors du stockage/service de blocs, dépense lors du téléchargement ; bridage des freeriders (Time-Decay). (P1)

### NonFunctional Requirements

- NFR-01 (Convergence CRDT): La synchronisation Gossip au sein d'un cluster doit garantir une convergence ≤ 3 secondes lors de l'ajout d'un nouveau bloc.
- NFR-02 (Latence Migration): Déclenchement et orchestration de la migration des blocs en moins de 5 secondes avant coupure réseau imminente.
- NFR-03 (Batterie/CPU): L'overhead du système CRDT/Gossip en arrière-plan ne doit pas excéder 5% d'utilisation CPU. Le NDK C++ pour Erasure Coding doit compenser la complexité de calcul.

### Additional Requirements

- Starter Template : `atick-faisal/Jetpack-Android-Starter` (Clean Architecture : Compose + Hilt + Room + Coroutines/Flow). Obligatoire pour Story 1.1.
- Foreground Service Android obligatoire pour la couche réseau P2P (MulticastLock Wi-Fi).
- Protobuf (kotlinx.serialization) avec `ignoreUnknownKeys=true` pour la compatibilité forward des messages Gossip CRDT.
- Android Keystore System (hardware-backed) pour l'identité nœud anti-Sybil persistée.
- AES-256 GCM avec clés éphémères dérivées (Clé_Fichier + Index_Bloc) pour le chiffrement des fragments.
- JNI/NDK avec `DirectByteBuffer` (batching obligatoire, pas de transfert octet isolé) pour Erasure Coding.
- Circuit-Breaker Avalanche : gel des réparations si churn instantané > 30% en < 5 minutes.
- Abdication automatique du Super-Pair après 30 minutes de mandat (exclusion de l'élection pendant 5 minutes).
- Filtres de Bloom pour Gossip intra-partition (pas de catalogues bruts échangés, delta uniquement).
- Timeout ACK adaptatif réseau (s'allonge automatiquement en cas de forte densité/interférences).
- Pattern `Result<T>` / `sealed class Resource<T>` obligatoire pour tous les retours de couche Data/UseCase (zéro exception silencieuse).

### UX Design Requirements

- UX-DR1: Composant `ReliabilityGauge` — indicateur visuel animé du Score de Fiabilité du nœud local (batterie + uptime + IP).
- UX-DR2: Composant `KpiDiagnosticCard` — carte de diagnostic haute performance affichant les métriques clés du nœud.
- UX-DR3: Composant `RadarLogConsole` — console de logs réseau en temps réel, style "radar/terminal", pour visualiser les événements P2P.
- UX-DR4: Composant `ErasureProgressIndicator` — indicateur de progression multi-blocs de l'opération d'Erasure Coding (encodage et décodage).
- UX-DR5: Dark Mode OLED pur absolu — thème système Material Design 3, fond #000000 strict.
- UX-DR6: Bottom Navigation à 3 onglets simples : Dashboard (état nœud) / Explorer (DHT fichiers) / Paramètres.
- UX-DR7: ModalBottomSheet utilitaristes pour les actions contextuelles sur fichiers/blocs (partager, supprimer, détails).
- UX-DR8: Permissions réseau silencieuses et englobantes au lancement (Wi-Fi, Multicast, Réseau) sans friction utilisateur.

### FR Coverage Map

| Exigence | Épic |
|---|---|
| FR-01.1 (UDP Multicast local) | Epic 2 |
| FR-01.2 (Tracker STUN/Signaling) | Epic 2 |
| FR-01.3 (P2P Zero-Trust bout-en-bout) | Epic 1 + Epic 5 |
| FR-02.1 (Score Fiabilité) | Epic 2 |
| FR-02.2 (Algorithme Bully + Inscription Tracker) | Epic 3 |
| FR-03.1 (Erasure Coding C++ K+N blocs) | Epic 5 |
| FR-03.2 (Chiffrement asymétrique fragments) | Epic 5 |
| FR-04.1 (Anneau DHT distribué) | Epic 4 + Epic 6 |
| FR-04.2 (Gossip épidémique CRDT) | Epic 4 |
| FR-06.1 (Migration proactive inter-réseaux) | Epic 7 |
| FR-07.1 (Karma Anti-Clandestin + Time-Decay) | Epic 8 |
| NFR-01 (Convergence CRDT ≤ 3s) | Epic 4 |
| NFR-02 (Latence migration < 5s) | Epic 7 |
| NFR-03 (Overhead CPU ≤ 5%) | Epic 5 + Global |
| UX-DR1 (ReliabilityGauge) | Epic 2 |
| UX-DR2 (KpiDiagnosticCard) | Epic 2 |
| UX-DR3 (RadarLogConsole) | Epic 2 |
| UX-DR4 (ErasureProgressIndicator) | Epic 5 |
| UX-DR5 (Dark OLED) | Epic 1 |
| UX-DR6 (Bottom Nav 3 onglets) | Epic 1 |
| UX-DR7 (ModalBottomSheet) | Epic 8 |
| UX-DR8 (Permissions silencieuses) | Epic 1 |

## Epic List

### Epic 1: Fondation & Identité de Confiance du Nœud
**Objectif:** L'utilisateur installe l'app, qui génère automatiquement une identité cryptographique infalsifiable (Android Keystore), configure l'UI (Dark OLED, navigation 3 onglets) et demande les permissions réseau. Le nœud est prêt à rejoindre le réseau.
**FRs covered:** FR-01.3, UX-DR5, UX-DR6, UX-DR8, Architecture: Starter Template, Keystore Anti-Sybil, Foreground Service.

### Epic 2: Découverte Hybride & Dashboard Tactique
**Objectif:** L'utilisateur peut voir les nœuds pairs détectés — en local via UDP Multicast (Wifi), et à travers le NAT via le Tracker STUN/Firebase (4G). Le Dashboard affiche les pairs, le score de fiabilité et les événements réseau en temps réel.
**FRs covered:** FR-01.1, FR-01.2, FR-02.1, UX-DR1, UX-DR2, UX-DR3.

### Epic 3: Gouvernance Décentralisée — Élection Bully & Super-Pair
**Objectif:** L'écosystème de nœuds s'auto-organise : l'Algorithme Bully élit un Super-Pair à partir des scores de fiabilité, celui-ci enregistre sa présence sur le Tracker pour lier son cluster à la fédération, et abdique automatiquement après 30 minutes.
**FRs covered:** FR-02.2, Architecture: Abdication automatique, Buffer d'urgence électoral, Circuit-Breaker churn.

### Epic 4: Catalogue DHT & Synchronisation CRDT/Gossip
**Objectif:** L'utilisateur peut voir dans l'Explorer la liste des fichiers disponibles dans le cluster, synchronisée de façon décentralisée via l'anneau DHT et les échanges Gossip épidémiques avec Filtres de Bloom garantissant une convergence ≤ 3s.
**FRs covered:** FR-04.1, FR-04.2, NFR-01, Architecture: Filtres de Bloom, Protobuf CRDT ignoreUnknownKeys.

### Epic 5: Partage de Fichiers Zero-Trust — Erasure Coding & Chiffrement
**Objectif:** L'utilisateur peut partager un fichier qui est découpé en blocs K+N chiffrés (C++ NDK via JNI DirectByteBuffer) et distribués aux nœuds du cluster. L'ErasureProgressIndicator visualise l'opération. L'hébergeur ne peut jamais lire le bloc.
**FRs covered:** FR-03.1, FR-03.2, UX-DR4, NFR-03, Architecture: JNI DirectByteBuffer batching, AES-256 GCM.

### Epic 6: Récupération Concurrentielle & Streaming Actif
**Objectif:** L'utilisateur peut récupérer un fichier stocké dans le cluster : les K blocs sont téléchargés en parallèle depuis plusieurs nœuds, le déchiffrement/réassemblage commence dès les premiers blocs disponibles (pipeline streaming actif K+2).
**FRs covered:** FR-04.1 (recherche DHT), Architecture: Pipeline streaming actif, Timeout ACK adaptatif.

### Epic 7: Résilience Extrême — Migration Proactive & Circuit-Breaker
**Objectif:** Lorsqu'un nœud quitte le réseau (basculement réseau), le Super-Pair orchestre la migration proactive de ses blocs vers d'autres nœuds en moins de 5 secondes. Le Circuit-Breaker gèle les réparations si le churn dépasse 30% pour protéger les survivants.
**FRs covered:** FR-06.1, NFR-02, Architecture: Circuit-Breaker Avalanche, Buffer d'urgence électoral.

### Epic 8: Équité & Karma Anti-Clandestin
**Objectif:** L'utilisateur voit son score Karma dynamique (gain en servant des blocs, dépense en téléchargeant, décroissance temporelle). Les freeriders sont bridés. Les actions Karma sont accessibles via ModalBottomSheet dans l'Explorer.
**FRs covered:** FR-07.1, UX-DR7, Architecture: Validation Karma peer-to-peer signée, Time-Decay.

---

## Epic 1: Fondation & Identité de Confiance du Nœud

**Objectif :** L'utilisateur installe l'app, qui génère automatiquement une identité cryptographique infalsifiable (Android Keystore), configure l'UI (Dark OLED, navigation 3 onglets) et demande les permissions réseau. Le nœud est prêt à rejoindre le réseau.

### Story 1.1: Initialisation du Projet & Fondation Clean Architecture

En tant que développeur,
Je veux initialiser le projet à partir du Starter Template Jetpack Android,
Afin que l'équipe dispose d'un socle Clean Architecture (Compose + Hilt + Room + Coroutines/Flow + Protobuf) prêt pour l'implémentation des modules MobiCloud.

**Acceptance Criteria:**

**Given** le dépôt est cloné depuis `atick-faisal/Jetpack-Android-Starter`
**When** le projet est ouvert dans Android Studio
**Then** le projet compile sans erreur et l'app se lance sur un émulateur API 26+
**And** la structure de répertoires `core/`, `domain/`, `data/`, `presentation/` est en place
**And** Hilt est configuré et l'injection de dépendances fonctionne (un ViewModel injecté visible)
**And** la dépendance Protobuf (kotlinx.serialization) est ajoutée avec `ignoreUnknownKeys=true`
**And** le Version Catalog `libs.versions.toml` liste toutes les dépendances (Room, Hilt, Coroutines, Compose, Protobuf)

### Story 1.2: Thème OLED Dark & Navigation 3 Onglets

En tant qu'utilisateur,
Je veux que l'application s'affiche en mode sombre OLED pur avec une navigation claire à 3 onglets,
Afin d'avoir une interface énergétiquement efficace et intuitive dès le premier lancement.

**Acceptance Criteria:**

**Given** l'application est lancée sur un appareil Android
**When** l'écran principal s'affiche
**Then** le fond de l'app est `#000000` strict (OLED pur) avec le thème Material Design 3 sombre activé
**And** une Bottom Navigation Bar affiche 3 onglets : "Dashboard" (icône radar), "Explorer" (icône dossier DHT), "Paramètres" (icône engrenage)
**And** chaque onglet navigue vers un écran placeholder fonctionnel (sans crash)
**And** l'onglet actif est visuellement mis en évidence (couleur accent distincte)
**And** le thème Dark OLED est appliqué de façon persistante (pas de flash blanc au démarrage)

### Story 1.3: Génération & Persistance de l'Identité Cryptographique (Keystore)

En tant que nœud MobiCloud,
Je veux générer une paire de clés asymétriques persistée dans l'Android Keystore au premier démarrage,
Afin de disposer d'une identité de confiance infalsifiable et anti-Sybil utilisable pour signer tous les messages P2P.

**Acceptance Criteria:**

**Given** l'application est lancée pour la première fois
**When** le module d'identité initialise le nœud
**Then** une paire de clés asymétriques (EC P-256) est générée et stockée dans l'Android Keystore System (hardware-backed)
**And** la clé publique est extraite et persistée localement (Room DB) comme `NodeIdentity.publicKeyBytes`
**And** un `nodeId` unique est dérivé de la clé publique (hash SHA-256 tronqué à 8 bytes)
**And** au prochain démarrage, la clé existante est réutilisée (pas de régénération)
**And** la clé privée ne peut jamais être exportée hors du TEE/KeyStore (vérifiable par `isInsideSecureHardware`)
**And** le tout est accessible via l'interface `domain/repository/IdentityRepository.kt` (Clean Architecture)

### Story 1.4: Foreground Service Réseau & Permissions au Lancement

En tant qu'utilisateur,
Je veux accorder les permissions réseau nécessaires en un seul flux au démarrage,
Afin que le service P2P de MobiCloud fonctionne en arrière-plan de façon continue sans être tué par l'OS.

**Acceptance Criteria:**

**Given** l'app est lancée pour la première fois
**When** l'écran de démarrage s'affiche
**Then** les permissions `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `INTERNET`, `ACCESS_NETWORK_STATE` sont demandées en un seul flux
**And** si l'utilisateur accorde les permissions, un `Foreground Service` est démarré avec une notification persistante discrète ("MobiCloud P2P actif")
**And** le service acquiert un `MulticastLock` Wi-Fi pour empêcher l'OS de filtrer les paquets UDP
**And** si le service est tué par l'OS, il redémarre automatiquement (`START_STICKY`)
**And** l'état du service est exposé via un `StateFlow<ServiceStatus>` observable depuis le Dashboard

---

## Epic 2: Découverte Hybride & Dashboard Tactique

**Objectif :** L'utilisateur peut voir les nœuds pairs détectés — en local via UDP Multicast (Wifi), et à travers le NAT via le Tracker STUN/Firebase (4G). Le Dashboard affiche les pairs découverts, le score de fiabilité local et les événements réseau en temps réel.

### Story 2.1: Découverte Locale via UDP Multicast (Heartbeat)

En tant que nœud MobiCloud sur Wifi,
Je veux envoyer et recevoir des heartbeats UDP Multicast,
Afin de découvrir automatiquement les pairs présents sur le même sous-réseau sans aucun serveur central.

**Acceptance Criteria:**

**Given** deux appareils sont connectés au même réseau Wifi et le Foreground Service est actif
**When** l'app démarre sur chaque appareil
**Then** chaque nœud envoie périodiquement un message `HEARTBEAT` Protobuf en UDP Multicast (groupe `239.255.255.250:7777`)
**And** chaque nœud reçoit les heartbeats des pairs et les enregistre dans une `PeerRegistry` locale (Room DB)
**And** un pair non entendu depuis > 15 secondes est marqué `INACTIVE` dans la registry
**And** les données du pair incluent `nodeId`, `publicKeyBytes`, `ipAddress`, `port`, `reliabilityScore`
**And** le tout passe par `domain/repository/PeerRepository.kt` (interface Clean Architecture)
**And** la `PeerRegistry` expose un `Flow<List<PeerNode>>` réactif

### Story 2.2: Signalisation Inter-Réseaux via Tracker Firebase

En tant que nœud MobiCloud sur réseau 4G,
Je veux m'enregistrer auprès du Tracker Firebase et découvrir les Super-Pairs d'autres clusters,
Afin de rejoindre la fédération MobiCloud même sans réseau local commun.

**Acceptance Criteria:**

**Given** le nœud est sur un réseau 4G (pas de Multicast UDP disponible)
**When** le service démarre et détecte l'absence de pairs locaux après 10 secondes
**Then** le nœud s'enregistre sur Firebase Realtime Database avec ses métadonnées (`nodeId`, `publicKey`, `ip`, `port`, `timestamp`)
**And** le nœud lit la liste des Super-Pairs inscrits sur Firebase et les ajoute à sa `PeerRegistry` locale
**And** les entrées Firebase âgées de plus de 60 secondes sont ignorées (TTL)
**And** la logique Firebase est encapsulée dans `data/repository/SignalingRepositoryImpl.kt` (interface `domain/repository/SignalingRepository.kt`)
**And** si Firebase est inaccessible, le nœud reste en mode Multicast local seul (`Result.Failure` remontée proprement)

### Story 2.3: Calcul du Score de Fiabilité Local

En tant que nœud MobiCloud,
Je veux mesurer et publier mon Score de Fiabilité (batterie, uptime, IP),
Afin que les autres nœuds puissent évaluer si je suis un candidat valide pour le rôle de Super-Pair.

**Acceptance Criteria:**

**Given** le Foreground Service est actif
**When** le score est recalculé toutes les 30 secondes
**Then** le score composite est calculé : `BatteryLevel (40%) + Uptime (40%) + NetworkStability (20%)` normalisé entre 0.0 et 1.0
**And** le score est persisté dans `NodeIdentity.reliabilityScore` (Room DB)
**And** le score est inclus dans les messages Heartbeat UDP et les enregistrements Firebase
**And** l'interface `domain/usecase/CalculateReliabilityScoreUseCase.kt` encapsule la logique
**And** un mock `StaticMockTrustScore` est injectable via Hilt pour les tests unitaires

### Story 2.4: Dashboard Tactique — Composants UX de Diagnostic

En tant qu'utilisateur,
Je veux voir un tableau de bord affichant mon état de nœud, les pairs découverts et les événements réseau en temps réel,
Afin d'avoir une visibilité complète sur la santé de mon cluster local.

**Acceptance Criteria:**

**Given** l'utilisateur est sur l'onglet "Dashboard"
**When** l'écran s'affiche
**Then** le composant `ReliabilityGauge` affiche le score de fiabilité local sous forme d'une jauge animée circulaire (0–100%)
**And** les composants `KpiDiagnosticCard` affichent : Niveau de batterie, Uptime (hh:mm), Réseau actif (Wifi/4G), Nombre de pairs actifs
**And** le composant `RadarLogConsole` affiche un flux scrollable des 50 derniers événements réseau P2P avec horodatage
**And** les données sont mises à jour en temps réel via `StateFlow` (pas de pull manuel)
**And** si aucun pair n'est découvert, un message "Aucun pair détecté — scan en cours..." s'affiche

---

## Epic 3: Gouvernance Décentralisée — Élection Bully & Super-Pair

**Objectif :** L'écosystème de nœuds s'auto-organise : l'Algorithme Bully élit un Super-Pair à partir des scores de fiabilité, celui-ci s'enregistre sur le Tracker Firebase pour lier son cluster à la fédération, et abdique automatiquement après 30 minutes.

### Story 3.1: Déclenchement & Protocole d'Élection Bully

En tant que nœud MobiCloud,
Je veux participer à une élection Bully lorsqu'aucun Super-Pair n'est joignable,
Afin que le cluster désigne automatiquement son meilleur coordinateur sans intervention humaine.

**Acceptance Criteria:**

**Given** aucun Super-Pair actif n'est détecté dans la `PeerRegistry` depuis > 5 secondes
**When** le nœud déclenche le protocole d'élection
**Then** il envoie un message `ELECTION` Protobuf signé (avec son `nodeId` et `reliabilityScore`) à tous les pairs connus
**And** tout pair recevant un `ELECTION` avec un score inférieur au sien répond `ALIVE` et lance sa propre candidature
**And** tout pair recevant un `ELECTION` avec un score supérieur reste silencieux
**And** si aucune réponse `ALIVE` n'est reçue après 3 secondes, le nœud se déclare vainqueur et envoie `COORDINATOR` à tous les pairs
**And** tous les pairs mettent à jour leur `PeerRegistry` avec le nouveau Super-Pair désigné
**And** la logique est encapsulée dans `domain/usecase/m10_election/RunBullyElectionUseCase.kt`

### Story 3.2: Enregistrement du Super-Pair sur le Tracker Firebase

En tant que Super-Pair élu,
Je veux publier ma présence sur Firebase,
Afin que les nœuds d'autres clusters (4G) puissent me trouver et rejoindre la fédération.

**Acceptance Criteria:**

**Given** un nœud remporte l'élection Bully et devient Super-Pair
**When** le message `COORDINATOR` est envoyé
**Then** le Super-Pair s'enregistre sur Firebase Realtime Database sous `super-peers/{nodeId}` avec `{ip, port, reliabilityScore, electedAt}`
**And** cet enregistrement est rafraîchi toutes les 30 secondes (keepalive) pour maintenir le TTL
**And** si le Super-Pair abdique ou perd sa connexion, son entrée Firebase est supprimée (`onDisconnect().removeValue()`)
**And** l'enregistrement réutilise `SignalingRepository` défini à l'Epic 2
**And** l'état Super-Pair est exposé via `StateFlow<NodeRole>` (PEER / SUPER_PEER) dans le Dashboard

### Story 3.3: Abdication Automatique & Buffer d'Urgence Électoral

En tant que Super-Pair,
Je veux abdiquer automatiquement après 30 minutes et protéger les requêtes en transit lors de la transition,
Afin d'éviter l'épuisement de ma batterie et de garantir la continuité du cluster.

**Acceptance Criteria:**

**Given** un nœud est Super-Pair depuis exactement 30 minutes
**When** le timer d'abdication expire
**Then** le Super-Pair envoie un message `ABDICATION` signé à tous les pairs, déclenchant une nouvelle élection
**And** le nœud abdiquant s'exclut automatiquement de la prochaine élection pendant 5 minutes (`cooldownUntil` en mémoire)
**And** pendant la transition, les requêtes d'auto-réparation reçues sont enfilées dans un `LocalRepairBuffer` in-memory (max 50 entrées)
**And** lorsque le nouveau Super-Pair se déclare, les entrées du buffer sont retransmises dans l'ordre FIFO
**And** si le buffer dépasse 50 entrées, les plus anciennes sont droppées avec un log WARNING dans le `RadarLogConsole`

### Story 3.4: Circuit-Breaker Anti-Avalanche (Churn > 30%)

En tant que Super-Pair,
Je veux détecter un effondrement rapide du cluster et geler temporairement les transferts de réparation,
Afin d'éviter d'épuiser les nœuds survivants en cascade.

**Acceptance Criteria:**

**Given** le Super-Pair surveille la `PeerRegistry` en continu
**When** plus de 30% des nœuds du cluster passent à `INACTIVE` en moins de 5 minutes
**Then** le Super-Pair active le mode `CIRCUIT_BREAKER` et émet un log WARNING dans le `RadarLogConsole`
**And** toutes les directives de transfert de blocs de réparation sont suspendues pendant 2 minutes
**And** après 2 minutes le Circuit-Breaker réévalue le taux de churn : si < 10%, il se désactive et reprend normalement
**And** l'état est visible dans le Dashboard (badge "Réseau instable" rouge)
**And** la logique est dans `domain/usecase/m06_m07_repair_migration/CircuitBreakerUseCase.kt`

---

## Epic 4: Catalogue DHT & Synchronisation CRDT/Gossip

**Objectif :** L'utilisateur peut voir dans l'Explorer la liste des fichiers disponibles dans le cluster, synchronisée de façon décentralisée via l'anneau DHT et les échanges Gossip épidémiques avec Filtres de Bloom, garantissant une convergence ≤ 3s.

### Story 4.1: Modélisation & Persistance de la Partition DHT Locale

En tant que nœud MobiCloud,
Je veux maintenir localement ma partition de l'anneau DHT dans Room DB,
Afin de répondre aux requêtes de localisation de blocs sans aucune infrastructure centralisée.

**Acceptance Criteria:**

**Given** le nœud a rejoint le cluster et connaît ses pairs voisins
**When** un bloc est stocké sur ce nœud
**Then** une entrée `DhtEntry(blockId, nodeId, ipAddress, port, timestamp)` est insérée dans Room DB (table `dht_entries`)
**And** la partition assignée est déterminée par hachage consistant : `hash(blockId) mod N` où N = nombre de nœuds qualifiés
**And** le nœud peut répondre à une requête `LOOKUP(blockId)` avec l'`ipAddress:port` du nœud détenteur
**And** l'anneau DHT est accessible via `domain/repository/DhtRepository.kt` (interface pure Kotlin)
**And** `data/local/DhtDao.kt` implémente les requêtes Room nécessaires (`findByBlockId`, `insertEntry`, `deleteByNodeId`)

### Story 4.2: Protocole Gossip Épidémique avec Filtres de Bloom

En tant que nœud MobiCloud,
Je veux synchroniser ma partition DHT avec mes voisins via des échanges Gossip légers (Filtres de Bloom),
Afin que tous les nœuds convergent vers une vue cohérente du catalogue sans échanger de catalogues bruts.

**Acceptance Criteria:**

**Given** deux nœuds voisins sont actifs dans le cluster
**When** le cycle Gossip s'exécute (toutes les 2 secondes)
**Then** chaque nœud envoie un `BloomFilterGossip` Protobuf contenant son Filtre de Bloom (représentation probabiliste de sa partition DHT)
**And** le nœud récepteur calcule les éléments potentiellement manquants (`diff`) en comparant les Filtres de Bloom reçus
**And** si un delta est détecté, une requête `DELTA_SYNC` est émise pour ne récupérer que les entrées manquantes
**And** la convergence est atteinte en ≤ 3 secondes après une mise à jour de bloc (NFR-01)
**And** le Gossip est circulaire : chaque nœud sélectionne aléatoirement 2 voisins par cycle (fan-out = 2)
**And** la logique est dans `domain/usecase/m03_m04_gossip_heartbeat/GossipSyncUseCase.kt`

### Story 4.3: CRDT — Résolution de Conflits de Catalogue

En tant que nœud MobiCloud,
Je veux que les conflits d'état de la DHT soient résolus automatiquement par des règles CRDT,
Afin de garantir la convergence éventuelle sans coordination centrale ni perte de données.

**Acceptance Criteria:**

**Given** deux nœuds ont des versions différentes d'une même entrée DHT (même `blockId`, `timestamps` différents)
**When** une synchronisation Gossip-Delta se produit
**Then** la règle CRDT LWW (Last-Write-Wins sur `timestamp`) s'applique : l'entrée la plus récente écrase l'ancienne
**And** si les timestamps sont identiques, le `nodeId` lexicographiquement supérieur est prioritaire (déterminisme garanti)
**And** aucune entrée n'est supprimée sans un `TombstoneEntry` CRDT explicite (évite les résurrections)
**And** les `TombstoneEntry` expirées (âge > 24h) sont purgées au démarrage du service
**And** la logique CRDT est dans `domain/usecase/m05_dht_catalog/ResolveDhtConflictUseCase.kt`

### Story 4.4: Explorer DHT — Affichage du Catalogue de Fichiers

En tant qu'utilisateur,
Je veux voir dans l'onglet Explorer la liste des fichiers disponibles dans mon cluster DHT,
Afin de savoir quels fichiers sont accessibles et par qui ils sont hébergés.

**Acceptance Criteria:**

**Given** l'utilisateur navigue vers l'onglet "Explorer"
**When** l'écran s'affiche
**Then** la liste des `CatalogEntry` (nom, taille, blocs K+N, date d'ajout) est affichée depuis Room DB
**And** chaque entrée indique son état de disponibilité : "Complet" / "Partiel" / "Dégradé"
**And** un pull-to-refresh déclenche une synchronisation Gossip manuelle immédiate
**And** la liste est observable via `Flow<List<CatalogEntry>>` (mise à jour auto quand la DHT locale change)
**And** un état vide "Catalogue vide — aucun fichier partagé dans le cluster" s'affiche si la DHT est vide

---

## Epic 5: Partage de Fichiers Zero-Trust — Erasure Coding & Chiffrement

**Objectif :** L'utilisateur peut partager un fichier qui est découpé en blocs K+N chiffrés (C++ NDK via JNI DirectByteBuffer) et distribués aux nœuds du cluster. L'`ErasureProgressIndicator` visualise l'opération. L'hébergeur ne peut jamais lire le bloc.

### Story 5.1: Moteur Erasure Coding C++ (NDK/JNI)

En tant que développeur,
Je veux implémenter le moteur d'Erasure Coding en C++ natif via JNI,
Afin de découper un fichier en K+N blocs avec une consommation CPU/batterie minimale grâce au batching via `DirectByteBuffer`.

**Acceptance Criteria:**

**Given** un fichier binaire de taille quelconque est passé au moteur
**When** `EncodeErasureFragmentsUseCase.encode(file, K, N)` est appelé
**Then** le fichier est découpé en `K` blocs de données et `N` blocs de parité en Corps de Galois GF(256)
**And** le transfert entre JVM et NDK utilise exclusivement `DirectByteBuffer` (zéro copie, pas d'octets isolés)
**And** le code C++ est compilé via NDK et exposé via JNI dans `core/erasure/ErasureCodingJni.kt`
**And** le décodage `DecodeErasureFragmentsUseCase.decode(blocs, K)` reconstruit le fichier à partir de n'importe quels K blocs parmi K+N
**And** les paramètres sont configurables (défaut : K=4, N=2)
**And** un test unitaire JVM valide : encode puis decode reproduit le fichier original bit-à-bit

### Story 5.2: Chiffrement AES-256 GCM des Fragments (Zero-Trust)

En tant que nœud hébergeur,
Je veux que chaque bloc Erasure soit chiffré avec une clé éphémère unique avant distribution,
Afin de ne jamais pouvoir lire le contenu du bloc que je stocke (Zero-Trust).

**Acceptance Criteria:**

**Given** K+N blocs Erasure sont générés pour un fichier
**When** chaque bloc est préparé pour la distribution
**Then** une clé AES-256 éphémère dérivée est générée pour chaque bloc : `HKDF(FileMasterKey, BlockIndex)` → clé AES-256 GCM
**And** chaque bloc est chiffré individuellement avec sa clé éphémère + un IV aléatoire 96 bits
**And** la `FileMasterKey` est chiffrée avec la clé publique EC du destinataire (ECIES) et transmise séparément
**And** les clés éphémères par bloc ne sont jamais stockées en clair sur disque (RAM uniquement pendant l'opération)
**And** `core/security/FragmentCipherUseCase.kt` encapsule toute la logique cryptographique
**And** un test unitaire vérifie : déchiffrement avec bonne clé = bloc original ; clé incorrecte = `Result.Failure`

### Story 5.3: Distribution des Blocs aux Nœuds du Cluster

En tant qu'utilisateur,
Je veux partager un fichier depuis l'Explorer,
Afin que ses blocs chiffrés soient distribués automatiquement aux nœuds disponibles du cluster via sockets TCP directs.

**Acceptance Criteria:**

**Given** l'utilisateur sélectionne un fichier et appuie sur "Partager" dans l'Explorer
**When** la distribution est déclenchée
**Then** le fichier est encodé en K+N blocs chiffrés (Stories 5.1 + 5.2)
**And** le Super-Pair assigne un nœud destination par bloc (round-robin sur nœuds `ACTIVE` de la `PeerRegistry`)
**And** chaque bloc est transmis via socket TCP direct (pas de routage multi-sauts)
**And** le nœud destinataire confirme la réception avec un `ACK` signé contenant le hash SHA-256 du bloc
**And** si un nœud est indisponible (timeout ACK adaptatif), un nœud de remplacement est sélectionné automatiquement
**And** après distribution complète, une `CatalogEntry` est ajoutée à la DHT locale et diffusée via Gossip
**And** en cas d'échec partiel (< K confirmations), l'opération est annulée et l'utilisateur notifié

### Story 5.4: ErasureProgressIndicator — Feedback UX en Temps Réel

En tant qu'utilisateur,
Je veux voir la progression du découpage et de la distribution de mes blocs Erasure en temps réel,
Afin de comprendre l'état de mon opération de partage sans attendre la fin.

**Acceptance Criteria:**

**Given** l'utilisateur a déclenché un partage de fichier
**When** l'opération d'Erasure Coding et de distribution est en cours
**Then** le composant `ErasureProgressIndicator` affiche une barre multi-étapes : "Encodage..." → "Chiffrement..." → "Distribution (X/K+N blocs)"
**And** chaque bloc confirmé par ACK incrémente le compteur de blocs distribués
**And** les blocs de données (K) et de parité (N) sont visuellement distincts dans l'indicateur
**And** en cas d'erreur sur un bloc, celui-ci est affiché en rouge avec le message d'erreur
**And** à la fin de la distribution réussie, un toast "Fichier partagé avec succès — X nœuds" s'affiche

---

## Epic 6: Récupération Concurrentielle & Streaming Actif

**Objectif :** L'utilisateur peut récupérer un fichier stocké dans le cluster : les K blocs sont téléchargés en parallèle depuis plusieurs nœuds, le déchiffrement/réassemblage commence dès les premiers blocs disponibles (pipeline streaming K+2).

### Story 6.1: Localisation des Blocs via Requête DHT

En tant qu'utilisateur,
Je veux rechercher un fichier par son nom dans l'Explorer et localiser automatiquement tous ses blocs dans la DHT,
Afin de savoir depuis quels nœuds je peux les récupérer.

**Acceptance Criteria:**

**Given** l'utilisateur est sur l'onglet Explorer et le fichier apparaît dans le catalogue
**When** l'utilisateur appuie sur un fichier et sélectionne "Télécharger"
**Then** une requête `LOOKUP(fileId)` est envoyée sur l'anneau DHT pour localiser les K+N blocs
**And** pour chaque `blockId`, la `PeerRegistry` retourne l'`ipAddress:port` du nœud qui le détient
**And** si un bloc est hébergé par plusieurs nœuds, le nœud avec le meilleur `reliabilityScore` est priorisé
**And** si un nœud local ne détient pas l'entrée DHT, la requête est relayée au nœud suivant de l'anneau
**And** le résultat est une `Map<BlockId, PeerNode>` remontée via `Result<Map<BlockId, PeerNode>>`

### Story 6.2: Téléchargement Concurrent K+2 (Multi-Nœuds)

En tant qu'utilisateur,
Je veux que mes blocs soient téléchargés simultanément depuis plusieurs nœuds,
Afin d'obtenir mon fichier le plus rapidement possible même si certains nœuds sont lents.

**Acceptance Criteria:**

**Given** la localisation des blocs (Story 6.1) est complète
**When** le téléchargement démarre
**Then** K+2 requêtes TCP parallèles sont ouvertes simultanément (K blocs requis + 2 de secours compétitifs)
**And** le premier K blocs à arriver "complète" le set — les 2 plus lents sont annulés (compétitif)
**And** les requêtes utilisent un timeout ACK adaptatif qui s'allonge en cas d'interférences Wi-Fi élevées
**And** chaque bloc reçu est immédiatement vérifié via son hash SHA-256 (intégrité)
**And** si un nœud retourne une erreur, un nœud de secours est sollicité depuis la `PeerRegistry`
**And** la progression est exposée via `Flow<DownloadProgressState>` (blocs reçus / K total)

### Story 6.3: Pipeline de Déchiffrement & Réassemblage Streaming

En tant qu'utilisateur,
Je veux que le déchiffrement et le réassemblage de mes blocs commencent dès les premiers blocs disponibles,
Sans attendre la fin du téléchargement complet.

**Acceptance Criteria:**

**Given** les premiers blocs Erasure de données sont reçus
**When** au moins K blocs valides sont disponibles (données ou parité)
**Then** `DecodeErasureFragmentsUseCase.decode()` est appelé immédiatement pour reconstruire les données manquantes
**And** le déchiffrement AES-256 GCM de chaque bloc est effectué sur `Dispatchers.Default` (hors thread UI)
**And** les données déchiffrées sont écrites en streaming dans un fichier temporaire dès qu'elles sont disponibles
**And** le fichier est déplacé vers son emplacement final uniquement lorsque tous les K blocs sont validés
**And** si une corruption est détectée (hash invalide), la story remonte `Result.Failure(CorruptBlockException)`

### Story 6.4: UI de Téléchargement & Notifications de Progression

En tant qu'utilisateur,
Je veux voir la progression de mon téléchargement distribué en temps réel dans l'Explorer,
Afin de savoir combien de blocs ont été récupérés et depuis combien de nœuds.

**Acceptance Criteria:**

**Given** un téléchargement distribué est en cours
**When** l'utilisateur consulte l'entrée du fichier dans l'Explorer
**Then** une barre de progression indique le nombre de blocs reçus (ex: "4/6 blocs")
**And** chaque nœud contributeur est affiché avec son `nodeId` tronqué et sa latence (ex: "a3f2... 42ms")
**And** si un nœud est lent (> 5s sans réponse), il est marqué "⏳ Attente" et un nœud de secours apparaît
**And** à la fin du téléchargement, un ModalBottomSheet s'ouvre : "Fichier récupéré en Xms — depuis Y nœuds" avec action "Ouvrir"
**And** si le téléchargement échoue (< K blocs valides), une erreur "Fichier irrécupérable — trop peu de nœuds actifs" est affichée

---

## Epic 7: Résilience Extrême — Migration Proactive & Circuit-Breaker

**Objectif :** Lorsqu'un nœud quitte le réseau (basculement Wifi→4G), le Super-Pair orchestre la migration proactive de ses blocs vers d'autres nœuds en < 5 secondes. L'auto-réparation maintient le niveau de résilience après chaque départ définitif.

### Story 7.1: Détection du Départ Imminent d'un Nœud

En tant que nœud MobiCloud,
Je veux signaler mon départ imminent au cluster lorsque je détecte un basculement réseau Wifi → 4G,
Afin que le Super-Pair puisse orchestrer la migration de mes blocs avant ma déconnexion.

**Acceptance Criteria:**

**Given** le nœud est actif et héberge des blocs dans le cluster
**When** le `ConnectivityManager` Android détecte un basculement de réseau (Wifi → 4G ou perte de signal)
**Then** le nœud envoie immédiatement un message `DEPARTURE_NOTICE` Protobuf signé au Super-Pair
**And** le `DEPARTURE_NOTICE` contient la liste des `blockId` hébergés par ce nœud
**And** le nœud continue à servir les requêtes TCP pendant 5 secondes supplémentaires (fenêtre de migration)
**And** si le Super-Pair ne confirme pas le début de migration dans les 5 secondes, le nœud se déconnecte proprement
**And** la logique de détection est dans `core/network/NetworkChangeObserver.kt`

### Story 7.2: Orchestration de la Migration Proactive par le Super-Pair

En tant que Super-Pair,
Je veux orchestrer le transfert des blocs d'un nœud partant vers d'autres nœuds disponibles en < 5 secondes,
Afin de maintenir le niveau de résilience du cluster avant la déconnexion.

**Acceptance Criteria:**

**Given** le Super-Pair reçoit un `DEPARTURE_NOTICE` d'un nœud partant
**When** l'orchestration de migration démarre
**Then** pour chaque `blockId` du nœud partant, le Super-Pair identifie un nœud de destination disponible (`ACTIVE`, hors nœud partant)
**And** le nœud partant reçoit un `MIGRATE_BLOCK(blockId, destinationIp:port)` et transfère le bloc chiffré sans le déchiffrer (transfert aveugle opaque)
**And** le nœud de destination confirme la réception avec un `ACK` signé + hash SHA-256 du bloc
**And** la DHT est mise à jour immédiatement (Gossip déclenché) pour refléter le nouveau propriétaire
**And** toute l'opération doit être complétée en < 5 secondes (NFR-02)
**And** la logique est dans `domain/usecase/m06_m07_repair_migration/OrchestrateBlockMigrationUseCase.kt`

### Story 7.3: Auto-Réparation — Détection de Blocs Sous-Répliqués

En tant que Super-Pair,
Je veux détecter lorsque le niveau de réplication d'un fichier descend sous le seuil K,
Afin de déclencher automatiquement une auto-réparation pour restaurer la résilience du cluster.

**Acceptance Criteria:**

**Given** le Super-Pair surveille la `PeerRegistry` et la DHT locale
**When** un nœud est marqué `INACTIVE` de façon définitive (> 30s sans heartbeat)
**Then** le Super-Pair identifie tous les `blockId` qui n'ont plus que < K copies dans des nœuds `ACTIVE`
**And** pour chaque bloc sous-répliqué, un `REPLICATE_BLOCK(blockId, destinationIp:port)` est envoyé au nœud donneur
**And** si le Circuit-Breaker (Story 3.4) est actif, les directives de réplication sont mises en queue dans le `LocalRepairBuffer`
**And** après réplication réussie, la DHT est mise à jour et diffusée via Gossip
**And** la logique est dans `domain/usecase/m06_m07_repair_migration/TriggerAutoRepairUseCase.kt`

---

## Epic 8: Équité & Karma Anti-Clandestin

**Objectif :** L'utilisateur voit son score Karma dynamique (gain en servant des blocs, dépense en téléchargeant, décroissance temporelle). Les freeriders sont bridés. Les actions Karma sont accessibles via ModalBottomSheet dans l'Explorer.

### Story 8.1: Moteur de Karma — Calcul, Persistance & Time-Decay

En tant que nœud MobiCloud,
Je veux maintenir un score de Karma qui évolue selon mes contributions au réseau,
Afin que les nœuds égoïstes soient pénalisés et que ma participation continue soit récompensée.

**Acceptance Criteria:**

**Given** le nœud est actif dans le cluster
**When** une transaction réseau se produit
**Then** le score Karma augmente de +1 point lorsque le nœud sert un bloc (envoi TCP réussi avec ACK)
**And** le score Karma diminue de -2 points lorsque le nœud télécharge un bloc (réception TCP)
**And** un mécanisme de Time-Decay applique -5% du score toutes les heures d'inactivité (minimum 0)
**And** le score Karma est persisté dans `NodeIdentity.karmaScore` (Room DB, mis à jour atomiquement)
**And** chaque transaction Karma est signée par la clé privée du nœud (Anti-Replay : timestamp + nonce)
**And** la logique est dans `domain/usecase/m09/UpdateKarmaScoreUseCase.kt`

### Story 8.2: Validation Karma Pair-à-Pair & Bridage des Freeriders

En tant que Super-Pair,
Je veux valider les scores Karma des nœuds du cluster et brider ceux dont le score est négatif,
Afin de garantir l'équité des contributions au réseau et d'empêcher les comportements parasites.

**Acceptance Criteria:**

**Given** un nœud demande un téléchargement de bloc
**When** le Super-Pair traite la requête
**Then** le Super-Pair vérifie le score Karma du demandeur dans la `PeerRegistry` locale
**And** si le score Karma est ≤ 0, la bande passante allouée est réduite de 50% (bridage progressif)
**And** si le score Karma est < -10, la requête est rejetée avec un message `KARMA_INSUFFICIENT`
**And** la validation est directe peer-to-peer signée (sans certification collégiale DHT complexe)
**And** le nœud bridé peut retrouver ses droits en servant des blocs (score remonte via Story 8.1)
**And** les décisions de bridage sont loguées dans le `RadarLogConsole` (visibilité réseau)

### Story 8.3: Affichage Karma & ModalBottomSheet dans l'Explorer

En tant qu'utilisateur,
Je veux voir mon score Karma dans l'interface et accéder aux actions contextuelles sur mes fichiers,
Afin de comprendre ma réputation dans le réseau et gérer mes fichiers partagés efficacement.

**Acceptance Criteria:**

**Given** l'utilisateur est sur l'onglet Explorer ou Dashboard
**When** il consulte son profil de nœud
**Then** son score Karma actuel est affiché dans la `KpiDiagnosticCard` (badge coloré : vert > 10, jaune 0-10, rouge < 0)
**And** le Time-Decay restant est affiché (ex: "Prochain -5% dans 23min")
**And** un appui long sur un fichier dans l'Explorer ouvre un `ModalBottomSheet` avec les actions : "Détails des blocs", "Supprimer ma copie", "Forcer synchronisation Gossip"
**And** les détails d'un bloc incluent : `blockId` tronqué, nœud hébergeur, taille, état Karma requis pour téléchargement
**And** si le Karma de l'utilisateur est insuffisant, l'action "Télécharger" dans le ModalBottomSheet est grisée avec un tooltip "Karma insuffisant"
