---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation', 'step-05-add-relay-epic', 'step-06-zero-firebase-pivot', 'step-07-readiness-fix']
inputDocuments: ['prd.md', 'architecture.md', 'architecture-connectivity-and-clustering.md', 'ux-design-specification.md', 'technical-serveur-relais-research.md', 'sprint-change-proposal-2026-04-28.md']
---

# MobiCloud - Epic Breakdown

## Overview

Ce document fournit la décomposition complète des épics et stories pour MobiCloud V5.0 (Architecture Fédération de Clusters Hybride avec Serveurs Relais HA — Zero-Firebase). Il décompose les exigences du PRD, de l'Architecture, et de la Spécification UX en stories implémentables et actionnables.

## ⚠️ Implementation Sequencing (Ordre de Construction Obligatoire)

Bien que les Epics soient numérotés 1 → 8 par cohérence fonctionnelle, **l'ordre d'implémentation séquentiel n'est pas linéaire**. L'Epic 8 (Couche Transport — Serveurs Relais HA) est une **fondation transport** dont dépendent Epic 2, Epic 3 et Epic 7.

**Ordre d'exécution réel pour le dev agent :**

1. **Epic 1** (Stories 1.1 → 1.6) — Fondation projet, identité, UI shell, foreground service.
2. **Epic 8 — Foundation slice** (Stories 8.1 + 8.2) — Serveur Node.js HA + `RelayWebSocketClient.kt`. **À implémenter AVANT Story 2.1.**
3. **Epic 2** (Stories 2.0 → 2.3) — Découverte Multicast UDP locale + signaling HA + Dashboard. Story 2.1 **consomme** `RelayWebSocketClient` de Story 8.2.
4. **Epic 3** (Stories 3.1 → 3.4) — Élection Bully + REGISTER_PEER. Story 3.2 **réutilise** `SignalingRepository` de Story 2.1.
5. **Epic 4** (Stories 4.1 → 4.4) — DHT + Gossip CRDT + Explorer.
6. **Epic 5** (Stories 5.1 → 5.5) — Erasure Coding C++ + Chiffrement + Distribution.
7. **Epic 6** (Stories 6.1 → 6.4) — Récupération concurrente K+2 + streaming.
8. **Epic 7** (Stories 7.1 → 7.3) — Migration proactive + auto-réparation.
9. **Epic 8 — Fallback slice** (Story 8.3) — Câblage du fallback Try-Direct-Then-Relay dans `BlockSenderWithRelay`. Dépend des Stories 5.3 et 7.2.

**Règle d'or pour le dev agent :** ne jamais implémenter Story 2.1 sans avoir terminé Stories 8.1 + 8.2.

## Requirements Inventory

### Functional Requirements

- FR-01.1: **Multicast UDP** pour la découverte locale au sein d'un même sous-réseau (Wi-Fi campus / conférence). Chemin prioritaire avant tout recours à la couche Relais HA. (P0)
- FR-01.2: Cluster de **Serveurs Relais HA** (min 2 instances Node.js/WebSocket) agissant strictement comme **annuaire de signalisation** (REGISTER_PEER / GET_PEERS) pour fédérer des réseaux séparés (NAT) et lier les Super-Pairs. Zero-Firebase. (P0)
- FR-01.3: Tous les transferts (catalogue ou fichiers) se font en P2P direct de nœud à nœud (Zero-Trust, Zero-Knowledge), avec fallback transparent FR-08 si le direct échoue. (P0)
- FR-02.1: Calcul du Score de Fiabilité (Batterie, Uptime, IP locale) par chaque appareil local. (P0)
- FR-02.2: Élection locale d'un Super-Pair strictement via l'Algorithme Bully. Le gagnant s'enregistre auprès des Serveurs Relais HA (REGISTER_PEER) pour rejoindre la fédération. (P0)
- FR-03.1: Erasure Coding vectoriel (C++ NDK) divisant le fichier en K+N blocs sans réplication redondante. (P0)
- FR-03.2: Chiffrement asymétrique des fragments (Zero-Trust) — l'hébergeur ne peut pas lire le bloc qu'il stocke. (P0)
- FR-04.1: Index global distribué dans un anneau DHT entre tous les pairs qualifiés du cluster (remplacement SQLite centralisé). (P0)
- FR-04.2: Synchronisation de la DHT par protocole Gossip épidémique avec CRDT (convergence garantie sans autorité centrale). (P0)
- FR-05.1: **Téléchargement concurrent K+2** — K+2 requêtes TCP parallèles, les 2 plus lentes annulées dès K blocs valides reçus. (P0)
- FR-05.2: **Pipeline streaming actif** — déchiffrement et réassemblage Erasure démarrés dès les premiers K blocs disponibles, sans attendre la fin du téléchargement. (P0)
- FR-06.1: Migration proactive des blocs d'un nœud quittant un cluster (basculement réseau) vers le cluster local avant déconnexion. (P1)
- FR-08.1: Serveurs Relais HA WebSocket Zero-Knowledge agissant comme **fallback de transport** (UPLOAD/FORWARD store-and-forward 60s RAM) pour les blocs chiffrés inter-réseaux quand le P2P direct échoue (NAT symétrique). (P0)
- FR-08.2: **Fallback transparent Try-Direct-Then-Relay** — TCP direct (P1), Relais HA (P2), failover séquentiel inter-instances HA (P3) ; UseCase appelant agnostique du canal. (P0)

### NonFunctional Requirements

- NFR-01 (Convergence CRDT): La synchronisation Gossip au sein d'un cluster doit garantir une convergence ≤ 3 secondes lors de l'ajout d'un nouveau bloc.
- NFR-02 (Latence Migration): Déclenchement et orchestration de la migration des blocs en moins de 5 secondes avant coupure réseau imminente.
- NFR-03 (Batterie/CPU): L'overhead du système CRDT/Gossip en arrière-plan ne doit pas excéder 5% d'utilisation CPU sur 30 minutes de tourner-à-vide. Le NDK C++ pour Erasure Coding doit compenser la complexité de calcul.
- NFR-04 (Résilience Churn): Circuit-Breaker Anti-Avalanche actif si > 30% des pairs deviennent INACTIVE en < 5 min ; reprise auto si churn < 10%.
- NFR-05 (Sécurité Zero-Knowledge bout-en-bout): AES-256 GCM avec clés éphémères dérivées par bloc (HKDF) ; clé maître protégée par ECIES. Aucun nœud ni Relais ne peut déchiffrer.
- NFR-06 (Mandat Super-Pair Limité): Abdication automatique après 30 min ; cooldown 5 min hors élection.
- NFR-07 (Anti-Sybil — Identité Hardware-Backed): EC P-256 stockée dans Android Keystore TEE ; clé privée non exportable (`isInsideSecureHardware`).

### Additional Requirements

- Starter Template : `atick-faisal/Jetpack-Android-Starter` (Clean Architecture : Compose + Hilt + Room + Coroutines/Flow). Obligatoire pour Story 1.1.
- Foreground Service Android obligatoire pour la couche réseau P2P.
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
- UX-DR7: ModalBottomSheet utilitaristes pour les actions contextuelles sur fichiers/blocs (stocker, supprimer, détails).
- UX-DR8: Permissions réseau silencieuses et englobantes au lancement (Wi-Fi, Réseau) sans friction utilisateur.
- UX-DR9: **Slider Quota Stockage** — composant Settings permettant de définir l'espace alloué au réseau (0.5 GB → 80% espace libre) avec affichage de l'espace actuellement utilisé.
- UX-DR10: **Cloud Relay Badge** — indicateur visuel discret dans le Dashboard signalant l'état du fallback Relais HA (P2P direct ✓ / Relais actif / Hors-ligne).

### FR Coverage Map

| Exigence | Épic | Story(ies) |
|---|---|---|
| FR-01.1 (Multicast UDP locale) | Epic 2 | 2.0 |
| FR-01.2 (Serveurs Relais HA — Signaling) | Epic 2 + Epic 8 | 2.1 / 8.1 / 8.2 |
| FR-01.3 (P2P Zero-Trust bout-en-bout) | Epic 1 + Epic 5 | 5.3 / 5.5 |
| FR-02.1 (Score Fiabilité) | Epic 2 | 2.2 |
| FR-02.2 (Algorithme Bully + Inscription Relais HA) | Epic 3 | 3.1 / 3.2 |
| FR-03.1 (Erasure Coding C++ K+N blocs) | Epic 5 | 5.1 |
| FR-03.2 (Chiffrement asymétrique fragments) | Epic 5 | 5.2 |
| FR-04.1 (Anneau DHT distribué) | Epic 4 + Epic 6 | 4.1 / 6.1 |
| FR-04.2 (Gossip épidémique CRDT) | Epic 4 | 4.2 / 4.3 |
| FR-05.1 (Téléchargement concurrent K+2) | Epic 6 | 6.2 |
| FR-05.2 (Pipeline streaming actif) | Epic 6 | 6.3 |
| FR-06.1 (Migration proactive inter-réseaux) | Epic 7 | 7.1 / 7.2 |
| FR-08.1 (Relais HA Fallback Zero-Knowledge) | Epic 8 | 8.1 / 8.3 |
| FR-08.2 (Fallback transparent Try-Direct-Then-Relay) | Epic 8 | 8.3 |
| NFR-01 (Convergence CRDT ≤ 3s) | Epic 4 | 4.2 |
| NFR-02 (Latence migration < 5s) | Epic 7 | 7.2 |
| NFR-03 (Overhead CPU ≤ 5%) | Epic 5 + Global | 5.1 + 1.4 |
| NFR-04 (Résilience Churn 30%/10%) | Epic 3 | 3.4 |
| NFR-05 (Sécurité AES-256 GCM Zero-Knowledge) | Epic 5 + Epic 8 | 5.2 / 8.1 |
| NFR-06 (Mandat Super-Pair ≤ 30 min) | Epic 3 | 3.3 |
| NFR-07 (Anti-Sybil Keystore EC P-256) | Epic 1 | 1.3 |
| UX-DR1 (ReliabilityGauge) | Epic 2 | 2.3 |
| UX-DR2 (KpiDiagnosticCard) | Epic 2 | 2.3 |
| UX-DR3 (RadarLogConsole) | Epic 2 | 2.3 |
| UX-DR4 (ErasureProgressIndicator) | Epic 5 | 5.4 |
| UX-DR5 (Dark OLED) | Epic 1 | 1.2 |
| UX-DR6 (Bottom Nav 3 onglets) | Epic 1 | 1.2 |
| UX-DR7 (ModalBottomSheet) | Epic 6 | 6.4 |
| UX-DR8 (Permissions silencieuses) | Epic 1 | 1.4 |
| UX-DR9 (Slider Quota Stockage) | Epic 1 | 1.6 |
| UX-DR10 (Cloud Relay Badge) | Epic 8 | 8.3 |

## Epic List

### Epic 1: Fondation & Identité de Confiance du Nœud
**Objectif:** L'utilisateur installe l'app, qui génère automatiquement une identité cryptographique infalsifiable (Android Keystore), configure l'UI (Dark OLED, navigation 3 onglets) et demande les permissions réseau. Le nœud est prêt à rejoindre le réseau.
**FRs covered:** FR-01.3, UX-DR5, UX-DR6, UX-DR8, Architecture: Starter Template, Keystore Anti-Sybil, Foreground Service.

### Epic 2: Découverte Inter-Réseaux & Dashboard Tactique
**Objectif:** L'utilisateur peut voir les nœuds pairs détectés en LAN via Multicast UDP **et** à travers le NAT via les **Serveurs Relais HA** (REGISTER_PEER/GET_PEERS, Zero-Firebase). Le Dashboard affiche les pairs, le score de fiabilité et les événements réseau en temps réel.
**FRs covered:** FR-01.1, FR-01.2, FR-02.1, UX-DR1, UX-DR2, UX-DR3.

### Epic 3: Gouvernance Décentralisée — Élection Bully & Super-Pair
**Objectif:** L'écosystème de nœuds s'auto-organise : l'Algorithme Bully élit un Super-Pair à partir des scores de fiabilité, celui-ci enregistre sa présence auprès des **Serveurs Relais HA** pour lier son cluster à la fédération, et abdique automatiquement après 30 minutes.
**FRs covered:** FR-02.2, Architecture: Abdication automatique, Buffer d'urgence électoral, Circuit-Breaker churn.

### Epic 4: Catalogue DHT & Synchronisation CRDT/Gossip
**Objectif:** L'utilisateur peut voir dans l'Explorer la liste des fichiers disponibles dans le cluster, synchronisée de façon décentralisée via l'anneau DHT et les échanges Gossip épidémiques avec Filtres de Bloom garantissant une convergence ≤ 3s.
**FRs covered:** FR-04.1, FR-04.2, NFR-01, Architecture: Filtres de Bloom, Protobuf CRDT ignoreUnknownKeys.

### Epic 5: Stockage Distribué Zero-Trust — Erasure Coding & Chiffrement
**Objectif:** L'utilisateur peut stocker un fichier qui est découpé en blocs K+N chiffrés (C++ NDK via JNI DirectByteBuffer) et distribués aux nœuds du cluster. L'ErasureProgressIndicator visualise l'opération. L'hébergeur ne peut jamais lire le bloc.
**FRs covered:** FR-03.1, FR-03.2, UX-DR4, NFR-03, Architecture: JNI DirectByteBuffer batching, AES-256 GCM.

### Epic 6: Récupération Concurrentielle & Streaming Actif
**Objectif:** L'utilisateur peut récupérer un fichier stocké dans le cluster : les K blocs sont téléchargés en parallèle depuis plusieurs nœuds, le déchiffrement/réassemblage commence dès les premiers blocs disponibles (pipeline streaming actif K+2).
**FRs covered:** FR-04.1 (recherche DHT), Architecture: Pipeline streaming actif, Timeout ACK adaptatif.

### Epic 7: Résilience Extrême — Migration Proactive & Circuit-Breaker
**Objectif:** Lorsqu'un nœud quitte le réseau (basculement réseau), le Super-Pair orchestre la migration proactive de ses blocs vers d'autres nœuds en moins de 5 secondes. Le Circuit-Breaker gèle les réparations si le churn dépasse 30% pour protéger les survivants.
**FRs covered:** FR-06.1, NFR-02, Architecture: Circuit-Breaker Avalanche, Buffer d'urgence électoral.

### Epic 8: Serveurs Relais HA WebSocket — Signaling & Transfert Inter-Réseaux
**Objectif (user-outcome):** L'utilisateur peut joindre des nœuds derrière NAT symétrique (4G ↔ Wi-Fi) **sans perception de coupure** grâce à un canal de fallback transparent — l'app choisit automatiquement le meilleur chemin (P2P direct prioritaire, Relais HA en fallback). Le cluster de **serveurs relais WebSocket Zero-Knowledge** (min 2 instances HA Render/Railway) assure à la fois **signaling** (annuaire Super-Pairs) et **transport** (Store-and-Forward 60s RAM). Zero-Firebase complet.
**FRs covered:** FR-01.2, FR-08.1, FR-08.2, NFR-05, UX-DR10, Architecture V5.0: Signalisation HA + Relay Fallback.
**Sequencing:** Stories 8.1 + 8.2 = **foundation slice** (à implémenter avant Story 2.1). Story 8.3 = **fallback slice** (à implémenter après Stories 5.3 et 7.2).

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

### Story 1.6: Configuration du Quota de Stockage Alloué au Réseau

En tant qu'utilisateur,
Je veux définir combien de gigaoctets de mon stockage j'alloue au réseau MobiCloud,
Afin de contrôler l'espace disque consommé par l'hébergement des blocs d'autres utilisateurs.

**Acceptance Criteria:**

**Given** l'utilisateur ouvre l'onglet "Paramètres"
**When** il accède à la section "Contribution au réseau"
**Then** un slider affiche l'espace allouable : de 0.5 GB à 80% de l'espace libre, par paliers de 0.5 GB
**And** l'espace actuellement utilisé par les blocs hébergés est affiché (ex: "1.2 GB utilisés sur 3 GB alloués")
**And** la valeur choisie est persistée dans `NodeSettings.allocatedStorageBytes` (Room DB)
**And** si l'utilisateur réduit le quota en dessous de l'espace déjà utilisé, un dialog d'avertissement s'affiche : "Réduire ce quota supprimera des blocs hébergés du réseau"
**And** la valeur par défaut au premier lancement est `min(2 GB, 20% de l'espace libre)`
**And** la valeur est accessible via `domain/repository/NodeSettingsRepository.kt`

### Story 1.4: Foreground Service Réseau & Permissions au Lancement

En tant qu'utilisateur,
Je veux accorder les permissions réseau nécessaires en un seul flux au démarrage,
Afin que le service P2P de MobiCloud fonctionne en arrière-plan de façon continue sans être tué par l'OS.

**Acceptance Criteria:**

**Given** l'app est lancée pour la première fois
**When** l'écran de démarrage s'affiche
**Then** les permissions `ACCESS_WIFI_STATE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE` sont demandées en un seul flux
**And** si l'utilisateur accorde les permissions, un `Foreground Service` est démarré avec une notification persistante discrète ("MobiCloud P2P actif")
**And** si le service est tué par l'OS, il redémarre automatiquement (`START_STICKY`)
**And** l'état du service est exposé via un `StateFlow<ServiceStatus>` observable depuis le Dashboard
**And** **NFR-03 mesurable** : sur 30 minutes de service tournant à vide (Gossip + heartbeat seuls, pas de transferts), la consommation CPU mesurée via Android Studio Profiler reste ≤ 5% en moyenne

---

## Epic 2: Découverte Inter-Réseaux & Dashboard Tactique

**Objectif :** L'utilisateur peut voir les nœuds pairs détectés en LAN via Multicast UDP **et** à travers le NAT via les **Serveurs Relais HA** (Zero-Firebase). Le Dashboard affiche les pairs découverts, le score de fiabilité local et les événements réseau en temps réel.

**Prérequis d'implémentation :** Stories 8.1 + 8.2 (foundation transport HA) doivent être terminées avant Story 2.1.

### Story 2.0: Découverte Locale par Multicast UDP

En tant que nœud MobiCloud,
Je veux découvrir mes pairs au sein du même sous-réseau Wi-Fi (campus, conférence) via Multicast UDP,
Afin de rester P2P pur en LAN sans dépendre des Serveurs Relais HA quand ce n'est pas nécessaire.

**Acceptance Criteria:**

**Given** le Foreground Service est actif et l'appareil est connecté à un Wi-Fi
**When** le module de découverte locale démarre
**Then** un `MulticastLock` est acquis (`WifiManager.createMulticastLock`) et conservé tant que le service tourne
**And** le nœud émet périodiquement (toutes les 5 s) un datagramme `HELLO` Protobuf signé EC P-256 sur l'adresse multicast `239.255.42.99:48999` (TTL 1 — local link)
**And** le nœud écoute en parallèle les `HELLO` reçus sur le même groupe et insère chaque pair valide dans `PeerRegistry` (avec source `LAN_MULTICAST`)
**And** la signature EC P-256 de chaque `HELLO` est vérifiée avant insertion dans `PeerRegistry`
**And** la logique est encapsulée dans `data/repository/LocalDiscoveryRepositoryImpl.kt` (interface `domain/repository/LocalDiscoveryRepository.kt`)
**And** si le réseau ne supporte pas le multicast (filtrage hotspot), un log INFO "Multicast indisponible — fallback Relais HA seul" est écrit dans `RadarLogConsole` après 30 s sans `HELLO` entrant
**And** la découverte LAN est **prioritaire** sur la découverte Relais HA : un pair présent dans les deux est marqué `LAN_MULTICAST` (chemin direct préféré)

### Story 2.1: Signalisation Inter-Réseaux via Serveurs Relais HA

En tant que nœud MobiCloud,
Je veux m'enregistrer auprès des Serveurs Relais HA WebSocket et découvrir les Super-Pairs d'autres clusters,
Afin de rejoindre la fédération MobiCloud sans dépendance à un service tiers (Zero-Firebase).

**Prérequis :** Stories 8.1 (serveur Node.js) et 8.2 (`RelayWebSocketClient.kt`) doivent être terminées.

**Acceptance Criteria:**

**Given** le nœud démarre et le Foreground Service est actif, et que `RelayWebSocketClient` (Story 8.2) est disponible
**When** le service démarre
**Then** la classe `data/repository/SignalingRepositoryImpl.kt` (interface `domain/repository/SignalingRepository.kt`) **consomme** l'instance `RelayWebSocketClient` injectée via Hilt — pas de gestion WSS bas-niveau dans cette story
**And** l'authentification EC P-256 (Keystore) est déléguée à `RelayWebSocketClient.connect()` (Story 8.2)
**And** si le nœud est Super-Pair élu, `SignalingRepository.registerAsSuperPeer()` envoie `REGISTER_PEER` avec ses métadonnées (`nodeId`, `publicKey`, `ip`, `port`, `reliabilityScore`) via le client unifié
**And** `SignalingRepository.fetchActiveSuperPeers()` envoie `GET_PEERS` et insère les pairs reçus dans `PeerRegistry` locale (source `RELAY_HA`)
**And** les entrées HA âgées de plus de 60 secondes sont ignorées (TTL)
**And** AUCUNE dépendance Firebase ; AUCUN OkHttp ou WebSocket directement importé dans cette story (tout passe par le client de Story 8.2)
**And** le failover séquentiel inter-instances HA est entièrement géré par `RelayWebSocketClient` — `SignalingRepositoryImpl` reçoit simplement un `Result.Failure` si tous les serveurs sont injoignables
**And** un échec total est logué dans le `RadarLogConsole`

### Story 2.2: Calcul du Score de Fiabilité Local

En tant que nœud MobiCloud,
Je veux mesurer et publier mon Score de Fiabilité (batterie, uptime, IP),
Afin que les autres nœuds puissent évaluer si je suis un candidat valide pour le rôle de Super-Pair.

**Acceptance Criteria:**

**Given** le Foreground Service est actif
**When** le score est recalculé toutes les 30 secondes
**Then** le score composite est calculé : `BatteryLevel (40%) + Uptime (40%) + NetworkStability (20%)` normalisé entre 0.0 et 1.0
**And** le score est persisté dans `NodeIdentity.reliabilityScore` (Room DB)
**And** le score est inclus dans les enregistrements `REGISTER_PEER` envoyés aux Serveurs Relais HA et dans les messages P2P signés
**And** l'interface `domain/usecase/CalculateReliabilityScoreUseCase.kt` encapsule la logique
**And** un mock `StaticMockTrustScore` est injectable via Hilt pour les tests unitaires

### Story 2.3: Dashboard Tactique — Composants UX de Diagnostic

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
**And** si aucun pair n'est découvert, un message "Aucun pair détecté — connexion aux Serveurs Relais HA en cours..." s'affiche

---

## Epic 3: Gouvernance Décentralisée — Élection Bully & Super-Pair

**Objectif :** L'écosystème de nœuds s'auto-organise : l'Algorithme Bully élit un Super-Pair à partir des scores de fiabilité, celui-ci s'enregistre auprès des **Serveurs Relais HA** pour lier son cluster à la fédération, et abdique automatiquement après 30 minutes.

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

### Story 3.2: Enregistrement du Super-Pair auprès des Serveurs Relais HA

En tant que Super-Pair élu,
Je veux publier ma présence auprès des Serveurs Relais HA,
Afin que les nœuds d'autres clusters (4G ou WiFi distinct) puissent me trouver et rejoindre la fédération.

**Acceptance Criteria:**

**Given** un nœud remporte l'élection Bully et devient Super-Pair
**When** le message `COORDINATOR` est envoyé
**Then** le Super-Pair envoie un message binaire `REGISTER_PEER` signé EC P-256 au Serveur Relais HA avec `{nodeId, ip, port, reliabilityScore, electedAt}`
**And** cet enregistrement est rafraîchi toutes les 30 secondes (keepalive PING) pour maintenir le TTL en RAM
**And** si le Super-Pair abdique ou la WSS se ferme, l'annuaire HA purge automatiquement l'entrée après expiration TTL (60s)
**And** l'enregistrement réutilise `SignalingRepository` (impl HA WebSocket) défini à l'Epic 2
**And** en cas d'échec d'enregistrement, le client bascule sur le serveur HA suivant (failover séquentiel)
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
**And** **NFR-01 mesurable** : `GossipSyncUseCase` instrumente `convergenceLatencyMs` (timestamp insertion locale → timestamp confirmation présence chez tous voisins) et l'expose via `Flow<GossipMetrics>` ; un test d'intégration sur 5 nœuds simulés valide que `convergenceLatencyMs ≤ 3000ms` au p95

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
**And** un état vide "Catalogue vide — aucun fichier stocké dans le cluster" s'affiche si la DHT est vide

---

## Epic 5: Stockage Distribué Zero-Trust — Erasure Coding & Chiffrement

**Objectif :** L'utilisateur peut stocker un fichier qui est découpé en blocs K+N chiffrés (C++ NDK via JNI DirectByteBuffer) et distribués aux nœuds du cluster. L'`ErasureProgressIndicator` visualise l'opération. L'hébergeur ne peut jamais lire le bloc.

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
Je veux stocker un fichier dans le réseau distribué depuis l'Explorer,
Afin que ses blocs chiffrés soient distribués automatiquement aux nœuds disponibles du cluster via sockets TCP directs.

**Acceptance Criteria:**

**Given** l'utilisateur sélectionne un fichier et appuie sur "Stocker" dans l'Explorer
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
Afin de comprendre l'état de mon opération de stockage sans attendre la fin.

**Acceptance Criteria:**

**Given** l'utilisateur a déclenché un stockage de fichier
**When** l'opération d'Erasure Coding et de distribution est en cours
**Then** le composant `ErasureProgressIndicator` affiche une barre multi-étapes : "Encodage..." → "Chiffrement..." → "Distribution (X/K+N blocs)"
**And** chaque bloc confirmé par ACK incrémente le compteur de blocs distribués
**And** les blocs de données (K) et de parité (N) sont visuellement distincts dans l'indicateur
**And** en cas d'erreur sur un bloc, celui-ci est affiché en rouge avec le message d'erreur
**And** à la fin de la distribution réussie, un toast "Fichier stocké avec succès sur X nœuds" s'affiche

### Story 5.5: Réception & Hébergement de Blocs Distants

En tant que nœud hébergeur,
Je veux recevoir les blocs chiffrés d'autres utilisateurs et les persister localement,
Afin de contribuer au réseau de stockage distribué.

**Acceptance Criteria:**

**Given** le Foreground Service est actif et le TCP server écoute
**When** un nœud distant envoie un bloc chiffré via socket TCP
**Then** le bloc est reçu et son intégrité est vérifiée via son hash SHA-256
**And** si le hash est valide, le bloc est persisté dans le stockage local (`/files/blocks/{blockId}`) avec ses métadonnées (`blockId`, `ownerId`, `sizeBytes`, `receivedAt`)
**And** une entrée `HostedBlockEntity` est insérée en Room DB : `blockId`, `ownerId`, `filePath`, `sizeBytes`
**And** un `ACK` signé contenant le hash SHA-256 du bloc est renvoyé au nœud émetteur
**And** si le hash est invalide, le bloc est rejeté et un `NACK` est renvoyé
**And** si l'espace disque local est insuffisant (< 100 MB libres), la requête est rejetée avec `STORAGE_FULL`
**And** la logique est dans `domain/usecase/m08_hosting/ReceiveAndHostBlockUseCase.kt`

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
**And** **NFR-02 mesurable** : `OrchestrateBlockMigrationUseCase` instrumente `migrationDurationMs` (timestamp réception DEPARTURE_NOTICE → timestamp dernier ACK) et l'expose via `Flow<MigrationMetrics>` ; un test d'intégration valide `migrationDurationMs < 5000ms` pour un nœud hébergeant ≤ 10 blocs

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

## Epic 8: Serveurs Relais HA WebSocket — Signaling & Transfert Inter-Réseaux

**Objectif :** L'utilisateur peut découvrir les Super-Pairs distants ET transférer des blocs chiffrés via un cluster de serveurs relais WebSocket Zero-Knowledge (min 2 instances HA) hébergés sur Render/Railway. Les serveurs assument deux rôles consolidés en V5.0 : (1) **Signaling** — annuaire RAM des Super-Pairs (`REGISTER_PEER`/`GET_PEERS`), (2) **Transport** — fallback Store-and-Forward 60s pour les blocs binaires (`UPLOAD`/`FORWARD`). Aucune dépendance Firebase.

### Story 8.1: Serveur Relais HA Node.js — Signaling + Transport Unifiés

En tant que système MobiCloud,
Je veux disposer d'un cluster de serveurs Node.js WebSocket fixes et sécurisés gérant à la fois le signaling et le relay,
Afin de permettre la découverte inter-clusters et le transfert de blocs entre téléphones sur des réseaux différents quand le P2P direct est bloqué par les NAT.

**Acceptance Criteria:**

**Given** au moins **deux instances Node.js indépendantes** sont déployées sur Render/Railway via Docker (`relay-server/` à la racine)
**When** un client se connecte via WSS (port 443)
**Then** le serveur accepte le handshake et authentifie le `nodeId` après vérification de la signature EC P-256
**And** le serveur traite les messages binaires selon le protocole : `REGISTER_PEER` (Super-Pair → annuaire RAM avec TTL 60s), `GET_PEERS` (lecture annuaire), `UPLOAD` (bloc chiffré → buffer RAM), `FORWARD` (push au destinataire dès qu'il est en ligne), `PING/PONG` (keepalive)
**And** les blocs non réclamés sont purgés après 60 secondes (Store-and-Forward éphémère, RAM uniquement)
**And** le serveur ne peut JAMAIS déchiffrer les blocs (Zero-Knowledge — AES-256 GCM opaque)
**And** un endpoint `GET /health` retourne le nombre de sessions actives + blocs en attente
**And** le serveur gère SIGTERM (graceful shutdown)
**And** le code est dans `relay-server/server.js` avec dépendance `ws` 8.x

### Story 8.2: Client Android RelayWebSocketClient Unifié

En tant que nœud MobiCloud,
Je veux disposer d'un client WebSocket unifié qui gère à la fois l'enregistrement Super-Pair et le transfert de blocs,
Afin d'avoir un canal de communication unique vers la couche Serveurs Relais HA.

**Acceptance Criteria:**

**Given** la liste des URLs des Serveurs Relais HA est en config (hardcodée ou fichier)
**When** le client doit communiquer avec la couche HA (signaling ou relay)
**Then** `RelayWebSocketClient.kt` ouvre une connexion WSS persistante via OkHttp `callbackFlow`
**And** au `onOpen`, il envoie automatiquement `REGISTER` signé avec la clé EC P-256 du Keystore
**And** il expose `fun connect(relayUrl: String): Flow<RelayEvent>` avec une `sealed class RelayEvent` (Connected, BlockReceived, Ack, Error, Disconnected)
**And** il expose `fun uploadBlock(destNodeId, blockId, data)` qui envoie via frame binaire WebSocket
**And** il gère la reconnexion automatique avec backoff exponentiel (1s, 2s, 4s, 8s, max 30s)
**And** en cas d'échec sur le serveur courant, il bascule sur le suivant de la liste (failover séquentiel)
**And** AUCUN import OkHttp/WebSocket dans la couche `domain/` (Clean Architecture stricte)

### Story 8.3: Fallback Transparent Try-Direct-Then-Relay (Multi-Instance)

En tant qu'utilisateur,
Je veux que l'application choisisse automatiquement le meilleur chemin de transfert (TCP direct ou Relais HA),
Afin que l'expérience de stockage reste fluide quel que soit mon environnement réseau.

**Acceptance Criteria:**

**Given** un transfert de bloc est déclenché par `DistributeBlocksUseCase` (Story 5.3) ou `OrchestrateBlockMigrationUseCase` (Story 7.2)
**When** le `BlockSender` (= `BlockSenderWithRelay`) tente d'envoyer le bloc
**Then** il tente d'abord une connexion TCP directe via `BlockTransferClient` (Priorité 1)
**And** en cas d'échec (IOException, Timeout), il bascule automatiquement sur `RelayRepository.uploadBlock()` via la couche HA (Priorité 2)
**And** en cas d'échec du Relais HA primaire, il tente automatiquement le Relais HA suivant (failover multi-instance, Priorité 3)
**And** le succès ou l'échec final est remonté au UseCase via `Result<BlockAckMessage>` sans qu'il connaisse le canal utilisé
**And** `DistributeBlocksUseCase` et `OrchestrateBlockMigrationUseCase` ne sont PAS modifiés (substitution transparente via `BlockTransferModule` Hilt)
**And** l'état du canal de transfert actif est exposé via `StateFlow<TransferChannelState>` (DIRECT / RELAY_HA / OFFLINE) consommable par le Dashboard
**And** le composant `CloudRelayBadge` (UX-DR10) affiche cet état dans le Dashboard avec 3 icônes distinctes (✓ direct / ☁ relay / ⚠ offline)

---
