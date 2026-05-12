---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation', 'step-05-add-relay-epic', 'step-06-zero-firebase-pivot', 'step-07-readiness-fix', 'step-08-approche-join-gps']
inputDocuments: ['prd.md', 'architecture.md', 'architecture-connectivity-and-clustering.md', 'ux-design-specification.md', 'technical-serveur-relais-research.md', 'sprint-change-proposal-2026-04-28.md', 'cluster-delimitation-gps-multicast.md', 'comparaison-approches-cluster.md', 'exemple-concret-approche-join.md', 'plan-tests-soutenance.md']
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
- FR-11.1: **Infrastructure GPS optionnelle** — `LocationProvider` Android (FusedLocationProvider) avec gestion permission `ACCESS_FINE_LOCATION` ; fournit une `GpsCoordinate` (latitude/longitude) ou `null` si permission refusée/GPS indisponible. Calcul Haversine de distance entre 2 coordonnées GPS. (P0)
- FR-11.2: **Protocole JOIN Explicite** — Tout pair candidat à rejoindre un cluster envoie un `JOIN_REQUEST` signé EC P-256 (avec `nodeId`, `gpsLocation`, `freeBytes`, `reliabilityScore`) au Super-Pair élu. Le Super-Pair retourne `JOIN_ACCEPT` (avec `memberSnapshot` complet) ou `JOIN_REDIRECT` (avec `reason` et `alternativeSuperPeers`). Le relai HA fait du forwarding pass-through, sans logique d'admission. (P0)
- FR-11.3: **Filtre GPS d'admission (optionnel et gracieux)** — Le Super-Pair refuse un `JOIN_REQUEST` si `Haversine(self.gps, candidate.gps) > MAX_RADIUS` (défaut 5 km). Si l'une des deux positions est `null`, le filtre GPS est **ignoré** (dégradation gracieuse), seul `MAX_CLUSTER_SIZE` s'applique. (P0)
- FR-11.4: **Plafond de taille de cluster** — Le Super-Pair refuse un `JOIN_REQUEST` si `clusterSize >= MAX_CLUSTER_SIZE` (défaut 50). Réponse `JOIN_REDIRECT` avec la liste des Super-Pairs alternatifs connus pour permettre une seconde tentative. (P0)
- FR-11.5: **Retry orphelin & auto-élection** — Un candidat qui reçoit `JOIN_REDIRECT` essaie séquentiellement les Super-Pairs alternatifs. Si tous refusent ou aucun n'est joignable, il déclenche un **Bully solo** et devient lui-même Super-Pair d'un nouveau cluster. (P0)
- FR-11.6: **Heartbeat de membre** — Chaque membre régulier envoie un `HEARTBEAT` signé toutes les 30 secondes au Super-Pair (avec `freeBytes` mis à jour). Le Super-Pair maintient un registre `MemberRegistry` en Room DB (`members` : `nodeId`, `gps`, `freeBytes`, `lastSeen`, `role`). (P0)
- FR-11.7: **Détection de mort & MEMBER_UPDATE** — Si le Super-Pair ne reçoit aucun `HEARTBEAT` d'un membre pendant 90 s, il diffuse `MEMBER_UPDATE { event: LEFT }` aux autres membres. Réciproquement, si un membre ne reçoit aucun signal du SP pendant 90 s, il déclenche une élection Bully (Story 3.1). (P0)
- FR-11.8: **Snapshot de membres pour continuité post-Bully** — Chaque `JOIN_ACCEPT` inclut le `memberSnapshot` complet à l'instant de l'admission. Le snapshot est mis à jour côté membre via les `MEMBER_UPDATE` reçus, persisté en table Room `member_snapshot` côté chaque membre régulier, et réutilisé lors d'une élection Bully post-mort SP. (P0)
- FR-11.9: **Annonce GPS du Super-Pair** — Le Super-Pair publie sa position GPS dans 3 canaux : (a) `HelloPayload` multicast UDP avec flag `superPair=true` (Story 2.0 étendue), (b) `REGISTER_PEER` envoyé au relai HA (Story 3.2 + `relay-server/server.js` étendus), (c) `ElectionPayload` du message `COORDINATOR` (Story 3.1 étendue). Tous les champs GPS sont optionnels — si `null`, les nœuds reçus tolèrent l'absence. (P0)
- FR-11.10: **Départ volontaire `LEAVE`** — Un membre quittant gracieusement (fermeture app, désinscription UI) envoie un `LEAVE` signé au SP. Le SP supprime immédiatement l'entrée de `MemberDao` et diffuse `MEMBER_UPDATE { event: LEFT }`. Permet une transition propre sans attendre le timeout 90 s. (P1)
- FR-11.11: **Sélection candidat par proximité GPS** — `SendJoinRequestUseCase` trie les `SuperPeerHint` candidats par distance GPS croissante (Haversine) avant tentative séquentielle. Si GPS local ou cible indisponible, fallback sur `reliabilityScore` descendant. (P1)

### NonFunctional Requirements

- NFR-01 (Convergence CRDT): La synchronisation Gossip au sein d'un cluster doit garantir une convergence ≤ 3 secondes lors de l'ajout d'un nouveau bloc.
- NFR-02 (Latence Migration): Déclenchement et orchestration de la migration des blocs en moins de 5 secondes avant coupure réseau imminente.
- NFR-03 (Batterie/CPU): L'overhead du système CRDT/Gossip en arrière-plan ne doit pas excéder 5% d'utilisation CPU sur 30 minutes de tourner-à-vide. Le NDK C++ pour Erasure Coding doit compenser la complexité de calcul.
- NFR-04 (Résilience Churn): Circuit-Breaker Anti-Avalanche actif si > 30% des pairs deviennent INACTIVE en < 5 min ; reprise auto si churn < 10%.
- NFR-05 (Sécurité Zero-Knowledge bout-en-bout): AES-256 GCM avec clés éphémères dérivées par bloc (HKDF) ; clé maître protégée par ECIES. Aucun nœud ni Relais ne peut déchiffrer.
- NFR-06 (Mandat Super-Pair Limité): Abdication automatique après 30 min ; cooldown 5 min hors élection.
- NFR-07 (Anti-Sybil — Identité Hardware-Backed): EC P-256 stockée dans Android Keystore TEE ; clé privée non exportable (`isInsideSecureHardware`).
- NFR-08 (Latence admission cluster): Le délai entre l'envoi d'un `JOIN_REQUEST` et la réception de `JOIN_ACCEPT`/`JOIN_REDIRECT` doit être ≤ 2 secondes en Wi-Fi LAN et ≤ 5 secondes via relai HA (4G), **par tentative individuelle** (timeout par candidat = `JOIN_REQUEST_TIMEOUT_MS = 5_000` ms). En cas d'épuisement de 3 candidats puis transition vers `Isolated`, la latence totale jusqu'à `BullySoloElectionUseCase` est plafonnée à `3 × 5 s + 20 s backoff = 35 s` worst-case — admissible car événement rare (réseau saturé OU zone géographique sans aucun SP).
- NFR-09 (Overhead heartbeat): La consommation CPU agrégée du `MemberHeartbeatUseCase` (envoi côté membre) + `ProcessHeartbeatUseCase` (réception côté SP avec 50 membres max) doit rester ≤ 1% en moyenne sur 30 minutes de service.
- NFR-10 (Dégradation gracieuse GPS): Le protocole d'admission doit rester fonctionnel si `LocationProvider` retourne `null` (permission refusée, GPS indoor non lockable). Aucun blocage utilisateur, le filtre GPS est simplement ignoré au profit du seul plafond `MAX_CLUSTER_SIZE`.
- NFR-11 (Plafond cluster défendable): `MAX_CLUSTER_SIZE = 50` et `MAX_RADIUS = 5 km` sont des constantes configurables documentées dans le rapport avec leur justification empirique (zone urbaine dense, charge SP soutenable).

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
| FR-11.1 (Infrastructure GPS optionnelle) | Epic 11 | 11.1 |
| FR-11.2 (Protocole JOIN explicite) | Epic 11 | 11.2 |
| FR-11.3 (Filtre GPS d'admission) | Epic 11 | 11.2 |
| FR-11.4 (Plafond MAX_CLUSTER_SIZE) | Epic 11 | 11.2 |
| FR-11.5 (Retry orphelin & auto-élection) | Epic 11 | 11.2 |
| FR-11.6 (Heartbeat de membre) | Epic 11 | 11.3 |
| FR-11.7 (Détection mort & MEMBER_UPDATE) | Epic 11 | 11.3 |
| FR-11.8 (Snapshot membres post-Bully) | Epic 11 | 11.3 |
| NFR-08 (Latence admission ≤ 2s/5s) | Epic 11 | 11.2 |
| NFR-09 (Overhead heartbeat ≤ 1%) | Epic 11 | 11.3 |
| NFR-10 (Dégradation gracieuse GPS) | Epic 11 | 11.1 / 11.2 |
| NFR-11 (Constantes défendables) | Epic 11 | 11.2 |
| FR-11.9 (Annonce GPS SP — Hello/Register/Coordinator) | Epic 11 + Epics 2/3 | 11.1 (étend Stories 2.0, 3.1, 3.2) |
| FR-11.10 (Départ volontaire LEAVE) | Epic 11 | 11.3 |
| FR-11.11 (Sélection candidat par proximité GPS) | Epic 11 | 11.2 |

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

### Epic 11: Délimitation Spatiale des Clusters — JOIN Explicite & GPS Optionnel
**Objectif (user-outcome):** L'utilisateur rejoint un cluster MobiCloud dont la frontière est décidée **par le Super-Pair élu** (décentralisé) selon deux critères locaux : proximité GPS (≤ 5 km si disponible) et capacité (≤ 50 membres). Si refusé, il tente d'autres Super-Pairs connus ; en dernier recours, il devient lui-même Super-Pair d'un nouveau cluster. Le protocole JOIN, les heartbeats et le registre des membres garantissent la cohésion du cluster et sa résilience à la mort du Super-Pair (snapshot transmis à l'admission).
**FRs covered:** FR-11.1, FR-11.2, FR-11.3, FR-11.4, FR-11.5, FR-11.6, FR-11.7, FR-11.8, NFR-08, NFR-09, NFR-10, NFR-11.
**Sequencing:** Stories 11.1 → 11.2 → 11.3 séquentiel strict. Story 11.2 dépend de 11.1 (besoin du GPS et de Haversine). Story 11.3 dépend de 11.2 (besoin du registre initial transmis par `JOIN_ACCEPT`).
**Dépendances externes :** Epic 3 (Bully + COORDINATOR), Epic 2 (Signaling HA + Multicast), Epic 8 (RelayWebSocketClient pour le forwarding pass-through).

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

## Epic 11: Délimitation Spatiale des Clusters — JOIN Explicite & GPS Optionnel

**Objectif :** L'utilisateur rejoint un cluster MobiCloud dont la frontière est décidée **par le Super-Pair élu** (décentralisé) selon deux critères locaux : proximité GPS (≤ 5 km si disponible) et capacité (≤ 50 membres). Si refusé, il tente d'autres Super-Pairs connus ; en dernier recours, il devient lui-même Super-Pair d'un nouveau cluster.

**Justification architecturale :** L'approche par JOIN explicite est l'alternative retenue à l'approche XOR-prefix par split/merge piloté par le relai (voir `docs/comparaison-approches-cluster.md`). Elle préserve trois principes thèse :
1. **Décentralisation maximale** — le relai reste un transport stateless, jamais une autorité d'admission.
2. **Super-Pair sacré et promotable** — le SP élu décide localement de l'admission ; tout membre peut le remplacer par Bully.
3. **Dégradation gracieuse** — le filtre GPS est ignoré si la permission est refusée ou le fix indisponible (NFR-10).

**Prérequis d'implémentation :**
- Epic 3 terminé (Bully + COORDINATOR signalent un `clusterId`).
- Epic 2 terminé (`SignalingRepository` pour découvrir les Super-Pairs via Multicast + tracker HA).
- Epic 8 terminé (`RelayWebSocketClient` pour le forwarding pass-through des messages JOIN inter-réseaux).

**Référence design :** `docs/cluster-delimitation-gps-multicast.md`, `docs/exemple-concret-approche-join.md`, `docs/plan-tests-soutenance.md`.

**📋 Divergences assumées vs `docs/cluster-delimitation-gps-multicast.md` (justifications pour le rapport) :**

| Paramètre | Doc design | Epic 11 retenu | Justification |
|---|---|---|---|
| `HEARTBEAT_INTERVAL` | 5 s | **30 s** | Compromis batterie : à 50 membres, 5 s = 10 msg/s côté SP (4× plus de réveils CPU). 30 s suffit à détecter une mort en ≤ 2 min (3 heartbeats manqués), acceptable pour P2P storage non temps-réel. |
| `SP_TIMEOUT` | > 5 s (déclenchement REJOINING) | **90 s** | Anti-flap réseau mobile : 5 s déclenche des Bully inutiles sur transition 4G→Wi-Fi. 90 s = 3 heartbeats manqués, valide la mort réelle. |
| `MEMBER_UPDATE` format | Delta (`deltaAdded`, `deltaRemoved`) | **Atomique** (1 event par message) | Simplicité d'implémentation et de debug. Le delta gagne <1 % de bande passante pour une complexité 2× supérieure. |
| `MAX_RADIUS` recommandé | 200 m / 1 km / 10 km selon contexte | **5 km configurable** | Valeur urbaine dense calibrée pour Bab Ezzouar (zone PFE) avec tolérance imprécision GPS indoor. Constante reste configurable via `ClusterConstants.kt` pour le rapport. |

> Ces divergences sont **assumées et défendables** : elles ne contredisent pas le design, elles l'ajustent à la contrainte batterie d'un téléphone Android moderne en usage continu.

**⚠️ Stories antérieures à étendre (modifications mineures, intégrées comme AC dans les stories 11.1/11.2) :**

| Story d'origine | Modification demandée | Stockée dans |
|---|---|---|
| Story 1.4 (Foreground Service & permissions) | Ajouter la demande runtime `ACCESS_FINE_LOCATION` au flux d'onboarding | AC Story 11.1 |
| Story 2.0 (Découverte Multicast) | Étendre `HelloPayload` avec `gpsLatitude?`, `gpsLongitude?`, `superPair: Boolean` | AC Story 11.1 |
| Story 2.1 (Signaling HA) | `SignalingRepository.fetchActiveSuperPeers()` retourne `List<SuperPeerHint>` avec GPS ; `RelayPeer` étendu | AC Story 11.1 + 11.2 |
| Story 3.1 (Bully Election) | Étendre `ElectionPayload` avec `gpsLatitude?`, `gpsLongitude?`, `maxRadiusMeters` (émis uniquement dans `COORDINATOR`) | AC Story 11.2 |
| Story 3.2 (REGISTER_PEER) | Étendre payload binaire avec `gpsLatitude`/`gpsLongitude` optionnels côté Android + `relay-server/server.js` (signalingRegistry + GET_PEERS) | AC Story 11.1 |
| Story 8.1 (Relay HA) | **Aucune modification** — `FORWARD` (0x07) existant transporte tous les messages d'admission (JOIN_REQUEST/ACCEPT/REDIRECT) ET de cohésion (HEARTBEAT/MEMBER_UPDATE/LEAVE) via préfixes de sous-type 1 octet | — |

### Story 11.1: Infrastructure GPS — LocationProvider, GpsCoordinate, Haversine

En tant que nœud MobiCloud,
Je veux disposer d'un service de localisation GPS optionnel et d'un calcul de distance Haversine,
Afin de pouvoir publier ma position dans les messages JOIN et permettre au Super-Pair de filtrer les candidats par proximité géographique.

**Acceptance Criteria:**

**Given** le projet Android dispose de la permission `ACCESS_FINE_LOCATION` déjà déclarée au `AndroidManifest.xml`
**When** le `LocationProvider` est instancié et démarré au boot du Foreground Service
**Then** une data class `GpsCoordinate(latitude: Double, longitude: Double, accuracyMeters: Float, timestampMs: Long)` est définie dans `domain/model/GpsCoordinate.kt` (interface pure Kotlin)
**And** un object `Haversine` expose `fun distanceMeters(a: GpsCoordinate, b: GpsCoordinate): Double` calculant la distance grand-cercle en mètres (formule Haversine standard, rayon terrestre = 6 371 000 m)
**And** un test unitaire JVM valide Haversine sur 3 cas connus : Alger↔Oran ≈ 398 km, Bab Ezzouar↔Centre Alger ≈ 13 km, point↔lui-même = 0 m (tolérance ±1 %)
**And** `domain/repository/LocationRepository.kt` (interface) expose `fun currentLocation(): StateFlow<GpsCoordinate?>` — le `?` est obligatoire (NFR-10 dégradation gracieuse)
**And** `data/repository/LocationRepositoryImpl.kt` implémente la repo via `FusedLocationProviderClient` (Google Play Services) avec `LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY`, intervalle 5 min, smallest displacement 100 m
**And** si la permission est refusée à runtime, `currentLocation()` émet `null` en permanence, **aucune exception ne remonte** et un log INFO est écrit dans `RadarLogConsole` : "GPS indisponible — admission cluster basée sur capacité seule"
**And** si le GPS n'est pas lockable (indoor, cold start) après 60 s, le repo émet `null` jusqu'au premier fix réussi
**And** la valeur émise est mise en cache RAM 5 minutes pour éviter les requêtes GPS répétées entre `JOIN_REQUEST`
**And** module Hilt `LocationModule` injecte le repo (binding `@Binds`)
**And** AUCUN import Google Play Services dans la couche `domain/` (Clean Architecture stricte)
**And** **Permission runtime** — la permission `ACCESS_FINE_LOCATION` est demandée à l'utilisateur au démarrage du `MainActivity` (extension de Story 1.4) ; si refusée, l'app continue de fonctionner et `LocationRepository.currentLocation()` émet `null` en permanence (NFR-10)
**And** **Mode dev / tests** — un binding Hilt alternatif `MockLocationRepositoryImpl` est défini dans le source set `androidTest` et `debug` ; il permet d'injecter des coordonnées arbitraires via `MockLocationRepositoryImpl.setMockLocation(GpsCoordinate)` pour les tests d'intégration (Test 4 migration de nœud, Test 5 simulation 1000) ; le binding production reste `LocationRepositoryImpl` réel
**And** **Extension de `HelloPayload` (Story 2.0)** — la data class `HelloPayload.kt` est étendue avec deux champs optionnels : `gpsLatitude: Double? = null`, `gpsLongitude: Double? = null`, et un champ booléen `superPair: Boolean = false` (signale que l'émetteur est Super-Pair actuel) ; ces champs sont sérialisés Protobuf avec compatibilité forward (`ignoreUnknownKeys=true`) ; les pairs reçus en `LOCAL_MULTICAST` propagent ces champs jusqu'à `PeerRegistry` et `RelayPeer` côté reader
**And** **Extension de `REGISTER_PEER` (Story 3.2 + relay-server)** — le payload binaire `REGISTER_PEER` est étendu côté `relay-server/server.js` pour accepter `gpsLatitude` et `gpsLongitude` optionnels ; le `signalingRegistry` les stocke ; `GET_PEERS` les retourne aux clients ; côté Android, `RelayPeer.kt` et `RelayWebSocketClient.parsePeersPayload()` (Story 8.2) gèrent ces champs optionnels — toléreront leur absence pour compatibilité avec les Super-Pairs n'ayant pas encore le GPS activé
**And** un test d'intégration valide qu'un Super-Pair annonçant un GPS via `REGISTER_PEER` est récupéré par un autre nœud via `GET_PEERS` avec le GPS intact

### Story 11.2: Protocole JOIN Explicite — Admission Décentralisée par le Super-Pair

En tant que nœud MobiCloud,
Je veux envoyer un `JOIN_REQUEST` signé au Super-Pair candidat et recevoir une décision `JOIN_ACCEPT` ou `JOIN_REDIRECT`,
Afin que la frontière de mon cluster soit décidée localement par le Super-Pair élu selon des critères de proximité GPS et de capacité, sans implication du relai HA.

**Prérequis :** Story 11.1 terminée (besoin de `GpsCoordinate` et `Haversine`).

**Acceptance Criteria:**

**Given** un nœud a découvert au moins un Super-Pair candidat via `SignalingRepository.fetchActiveSuperPeers()` (Story 2.1) ou `LocalDiscoveryRepository` (Story 2.0)
**When** le nœud souhaite rejoindre un cluster
**Then** une `data class JoinRequest(senderNodeId, candidatePublicKey, gpsLocation: GpsCoordinate?, freeBytes: Long, reliabilityScore: Float, timestampMs: Long, signature: ByteArray)` est définie dans `domain/model/m11_join/JoinRequest.kt` avec sérialisation Protobuf
**And** une `sealed class JoinResponse` est définie avec deux sous-types : `JoinAccept(clusterId, superPairNodeId, memberSnapshot: List<MemberInfo>, signature)` et `JoinRedirect(reason: JoinRedirectReason, distanceMeters: Double?, alternativeSuperPeers: List<SuperPeerHint>, signature)`
**And** **`MemberInfo` data class définie** dans `domain/model/m11_join/MemberInfo.kt` (Protobuf-sérialisable) avec champs : `nodeId: ByteArray`, `publicKey: ByteArray`, `ipAddress: String`, `port: Int`, `gpsLatitude: Double?`, `gpsLongitude: Double?`, `freeBytes: Long`, `role: MemberRole` (enum `SUPER_PAIR / MEMBER`) ; cette classe est utilisée dans `JoinAccept.memberSnapshot`, `MEMBER_UPDATE.member`, et le cache RAM `inMemoryRegistry: StateFlow<List<MemberInfo>>` ; mapping bijectif avec `MemberEntity` Room (Story 11.3) via `MemberMapper.kt`
**And** l'enum `JoinRedirectReason` contient au moins : `OUT_OF_RADIUS`, `CLUSTER_FULL`, `INVALID_SIGNATURE`, `BLACKLISTED`
**And** le `SendJoinRequestUseCase` (couche `domain/usecase/m11_join/`) :
  - récupère le GPS courant via `LocationRepository.currentLocation().value` (peut être `null`)
  - signe la requête EC P-256 via `SecurityRepository.signData()`
  - envoie via `RelayWebSocketClient` (Story 8.2) ou socket TCP direct en LAN
  - attend la réponse avec timeout 5 s (NFR-08)
  - sur `JoinAccept` : persiste `clusterId` dans `NodeSettings`, persiste `memberSnapshot` dans Room DB (table `cluster_members`, voir Story 11.3), transite vers `NodeJoinState.Member`, démarre `MemberHeartbeatUseCase`
  - sur `JoinRedirect` : itère sur `alternativeSuperPeers` (max 3 tentatives) ; si toutes échouent, **transite vers `NodeJoinState.Isolated`** (le déclenchement éventuel de `BullySoloElectionUseCase` est orchestré par `JoinStateMachine` après le délai `ISOLATION_BACKOFF_MS = 20_000` ms — voir AC plus bas, FR-11.5)
**And** le `ProcessJoinRequestUseCase` (côté Super-Pair) applique dans cet ordre strict :
  1. **Vérification signature** EC P-256 — sinon `JoinRedirect(INVALID_SIGNATURE)`
  2. **Filtre GPS optionnel** — si `self.gps != null && request.gps != null`, calcule `Haversine`. Si `distance > MAX_RADIUS (5 km)` → `JoinRedirect(OUT_OF_RADIUS, distance, alternatives)`. Si l'une des deux est `null`, le filtre est ignoré (NFR-10).
  3. **Filtre capacité** — si `memberRegistry.size >= MAX_CLUSTER_SIZE (50)` → `JoinRedirect(CLUSTER_FULL, null, alternatives)`
  4. Sinon → `JoinAccept` avec `memberSnapshot` complet (FR-11.8) et insertion du candidat dans `memberRegistry`
**And** les `alternativeSuperPeers: List<SuperPeerHint>` dans `JoinRedirect` sont peuplées depuis `SignalingRepository.fetchActiveSuperPeers()` filtré par proximité GPS (top 3 plus proches du candidat, hors self)
**And** la sérialisation Protobuf de tous les messages JOIN utilise `ignoreUnknownKeys=true` (Architecture) pour compatibilité forward
**And** **Wire format unifié — encapsulation dans `FORWARD` (0x07)** — TOUS les messages de l'approche JOIN (JOIN_REQUEST, JOIN_ACCEPT, JOIN_REDIRECT) sont encapsulés dans le message relai existant `FORWARD` (0x07) de Story 8.1, avec un préfixe de 1 octet en tête de payload identifiant le sous-type : `0x01 = HEARTBEAT`, `0x02 = MEMBER_UPDATE`, `0x03 = LEAVE`, `0x04 = JOIN_REQUEST`, `0x05 = JOIN_ACCEPT`, `0x06 = JOIN_REDIRECT` ; **AUCUNE modification de `relay-server/server.js`** (la transparence du forwarding est totale, le relai reste stateless) ; côté client, `RelayWebSocketClient.uploadBlock(destNodeId, payload)` est réutilisé avec le préfixe ajouté côté `domain/usecase/m11_join/`
**And** un test d'intégration valide les 4 scénarios canoniques (réf. `docs/exemple-concret-approche-join.md`) :
  - T=1 Bob (3 km) → `JoinAccept` → `NodeJoinState.Member`
  - T=2 Carol (800 m, multicast) → `JoinAccept` → `NodeJoinState.Member`
  - T=3 Dave (398 km) → `JoinRedirect(OUT_OF_RADIUS)` → `NodeJoinState.Isolated` → attente `ISOLATION_BACKOFF_MS = 20_000` ms → `BullySoloElectionUseCase` → `NodeJoinState.SuperPair(nouveau clusterId)` → nouveau cluster créé dans le tracker
  - GPS null (permission refusée) → `JoinAccept` si capacité OK (dégradation gracieuse) → `NodeJoinState.Member`
**And** **NFR-08 mesurable** : `SendJoinRequestUseCase` instrumente `joinLatencyMs` (envoi → réception) et l'expose via `Flow<JoinMetrics>` ; un test valide `joinLatencyMs ≤ 2000ms` en LAN et `≤ 5000ms` via relai HA (p95)
**And** AUCUN import OkHttp/WebSocket dans la couche `domain/` (Clean Architecture)
**And** **`SuperPeerHint` data class définie** dans `domain/model/m11_join/SuperPeerHint.kt` (Protobuf-sérialisable) avec champs : `nodeId: ByteArray`, `gpsLatitude: Double?`, `gpsLongitude: Double?`, `clusterId: String`, `ipAddress: String`, `port: Int`, `reliabilityScore: Float` ; cette classe est réutilisée par `JoinRedirect.alternativeSuperPeers` ET par `SignalingRepository.fetchActiveSuperPeers()` qui en retourne `List<SuperPeerHint>` (extension de Story 2.1 — le mapping `RelayPeer → SuperPeerHint` est explicite)
**And** **Tri par proximité côté candidat** — `SendJoinRequestUseCase` reçoit en entrée une `List<SuperPeerHint>` (toutes sources confondues : multicast LAN + tracker HA), et **trie cette liste par distance GPS croissante** via `Haversine` quand `self.gps != null && hint.gps != null` ; sinon ordre conservé (fallback `reliabilityScore` descendant) ; itère séquentiellement (max 3 tentatives) avec timeout 5 s par candidat
**And** **Extension de `ElectionPayload.kt` (Story 3.1)** — la data class `ElectionPayload.kt` est étendue avec trois champs optionnels : `gpsLatitude: Double? = null`, `gpsLongitude: Double? = null`, `maxRadiusMeters: Int = 5000` ; ces champs sont **uniquement** émis dans le message `COORDINATOR` (pas dans `ELECTION` ou `ALIVE`) afin que les futurs membres connaissent le centre géographique et la contrainte de rayon du SP avant d'envoyer leur `JOIN_REQUEST` ; sérialisation Protobuf forward-compatible
**And** **Constantes centralisées** — un fichier `domain/model/m11_join/ClusterConstants.kt` définit les 6 constantes : `const val MAX_RADIUS_METERS = 5_000`, `const val MAX_CLUSTER_SIZE = 50`, `const val HEARTBEAT_INTERVAL_MS = 30_000L`, `const val SP_TIMEOUT_MS = 90_000L`, `const val JOIN_REQUEST_TIMEOUT_MS = 5_000L`, `const val ISOLATION_BACKOFF_MS = 20_000L` ; toutes les références hardcodées dans `ProcessJoinRequestUseCase`, `MemberHeartbeatUseCase`, `MonitorMemberLivenessUseCase`, `SendJoinRequestUseCase` lisent ces constantes (pas de magic number) ; valeurs documentées en commentaire avec leur justification empirique (NFR-11)
**And** **Câblage du flux trigger (intégration Epic 3 → Epic 11)** — le `RunBullyElectionUseCase` (Story 3.1) doit, après émission de `COORDINATOR` (cas victoire), invoquer immédiatement `MarkSelfAsSuperPairUseCase` ; le `ProcessIncomingElectionEventUseCase` (Story 3.1) doit, après réception d'un `COORDINATOR` avec `clusterId != localClusterId` **OU** `clusterId == localClusterId && senderNodeId != self` (cas re-élection après timeout SP), invoquer immédiatement `SendJoinRequestUseCase` avec le `COORDINATOR.senderNodeId` comme premier candidat ; le cas `clusterId == localClusterId && senderNodeId == self` (nous-mêmes en sortie de victoire) n'enclenche PAS `SendJoinRequestUseCase` mais bien `MarkSelfAsSuperPairUseCase` ; ces câblages sont des modifications de Story 3.1 intégrées comme AC ici, pas une story séparée
**And** **Nouveaux use cases d'Epic 11 (à créer)** — Epic 11 introduit 3 nouveaux use cases dans `domain/usecase/m11_join/` au-delà de `SendJoinRequestUseCase` / `ProcessJoinRequestUseCase` / `MemberHeartbeatUseCase` / `ProcessHeartbeatUseCase` / `MonitorMemberLivenessUseCase` / `SendLeaveUseCase` :
  - **`MarkSelfAsSuperPairUseCase`** — appelé après victoire Bully : initialise `cluster_members` à `{self}` avec `role=SUPER_PAIR`, persiste `clusterId` généré dans `NodeSettings`, transite `NodeJoinState` vers un nouvel état `SuperPair(clusterId)` (à ajouter à la sealed class), démarre les jobs `MonitorMemberLivenessUseCase`
  - **`BullySoloElectionUseCase`** — variante de `RunBullyElectionUseCase` qui **court-circuite** la phase d'émission `ELECTION` (puisqu'aucun pair n'est joignable, par définition) et se déclare immédiatement vainqueur ; génère un nouveau `clusterId`, émet un `COORDINATOR` autoréférent dans `PeerRegistry`, puis chaîne vers `MarkSelfAsSuperPairUseCase` ; utilisé uniquement depuis l'état `Isolated` après `ISOLATION_BACKOFF_MS`
  - **`JoinStateMachine`** — orchestrateur central des transitions `NodeJoinState` ; expose `transition(event: JoinEvent): NodeJoinState` et `currentState: StateFlow<NodeJoinState>` ; déclenche `BullySoloElectionUseCase` après le timer 20 s en état `Isolated` sans nouveau candidat détecté
**And** **State machine `NodeJoinState`** définie comme `sealed class` dans `domain/model/m11_join/NodeJoinState.kt` avec **6 états** :
  - `Undiscovered` — état initial, aucun pair connu
  - `Joining(targetSuperPair: SuperPeerHint)` — `JOIN_REQUEST` émis, en attente de réponse
  - `Member(clusterId: String, superPairNodeId: ByteArray)` — admis dans un cluster, heartbeats actifs
  - `SuperPair(clusterId: String)` — auto-élu chef d'un cluster (après victoire Bully ou Bully solo)
  - `Rejoining(reason: RejoinReason)` — SP silencieux 90 s, déclenche un Bully ; `RejoinReason` enum : `SP_TIMEOUT`, `SP_ABDICATION`
  - `Isolated(rejectionCount: Int, lastRejectionTime: Long)` — tous les SP candidats ont refusé OU aucun candidat trouvé

**And** **Sealed class `JoinEvent`** (events qui déclenchent les transitions, dans `domain/model/m11_join/JoinEvent.kt`) :
  - `CoordinatorReceived(senderNodeId, clusterId, gpsLocation?, maxRadiusMeters)` — un `COORDINATOR` reçu via multicast ou relai
  - `JoinAcceptReceived(clusterId, superPairNodeId, memberSnapshot)` — `JOIN_ACCEPT` reçu
  - `JoinRedirectReceived(reason, alternatives)` — `JOIN_REDIRECT` reçu, retry possible
  - `AllCandidatesExhausted` — les 3 tentatives `JOIN_REQUEST` ont échoué
  - `IsolationBackoffElapsed` — timer 20 s écoulé en état `Isolated` sans nouveau candidat
  - `NewCandidateDetected(hint: SuperPeerHint)` — nouveau Super-Pair découvert pendant `Isolated` ou `Undiscovered`
  - `SpTimeoutDetected` — pas de signal du SP depuis 90 s (déclenché par `MonitorMemberLivenessUseCase` côté membre)
  - `BullyVictory(newClusterId)` — l'élection Bully gagnée
  - `BullyLost(winnerNodeId)` — l'élection Bully perdue par un autre

**And** **Table de transitions `JoinStateMachine`** documentée explicitement (pas implicite) :

| État courant | Event | État cible | Action déclenchée |
|---|---|---|---|
| `Undiscovered` | `CoordinatorReceived` | `Joining` | `SendJoinRequestUseCase` |
| `Undiscovered` | `NewCandidateDetected` | `Joining` | `SendJoinRequestUseCase` |
| `Joining` | `JoinAcceptReceived` | `Member` | démarrer `MemberHeartbeatUseCase` |
| `Joining` | `JoinRedirectReceived` | `Joining` (next candidate) OU `Isolated` si épuisé | retry ou transition |
| `Joining` | `AllCandidatesExhausted` | `Isolated` | démarrer timer 20 s |
| `Isolated` | `NewCandidateDetected` | `Joining` | `SendJoinRequestUseCase` |
| `Isolated` | `IsolationBackoffElapsed` | `SuperPair` | `BullySoloElectionUseCase` |
| `Member` | `SpTimeoutDetected` | `Rejoining(SP_TIMEOUT)` | `RunBullyElectionUseCase` |
| `Member` | reçoit `ABDICATION` du SP | `Rejoining(SP_ABDICATION)` | `RunBullyElectionUseCase` |
| `Rejoining` | `BullyVictory` | `SuperPair` | `MarkSelfAsSuperPairUseCase` |
| `Rejoining` | `BullyLost` (autre membre gagne) | `Member` | reprise heartbeats vers nouveau SP |
| `SuperPair` | (abdication 30 min, Story 3.3) | `Undiscovered` | nouvelle élection |

les transitions sont gérées par un `JoinStateMachine` exposé via `StateFlow<NodeJoinState>` consommable par le Dashboard (badge "État cluster" : Découverte / En cours d'adhésion / Membre / Super-Pair / Reconnexion / Isolé) ; l'état est persisté en RAM seule (re-calculé au démarrage à partir de `cluster_members` Room et `member_snapshot` Room)
**And** **Comportement de l'état `Isolated`** — quand `SendJoinRequestUseCase` épuise tous les candidats (max 3 tentatives) sans `JOIN_ACCEPT`, le nœud entre dans `Isolated(rejectionCount=3, lastRejectionTime=now)` ; il **attend `ISOLATION_BACKOFF_MS = 20_000` ms** avant de retenter ; durant ce délai, il continue à écouter en multicast et tracker pour de nouveaux Super-Pairs (s'il en détecte un nouveau, il quitte `Isolated` et envoie un `JOIN_REQUEST` ciblé) ; si après 20 s aucun nouveau candidat n'apparaît, il déclenche `BullySoloElectionUseCase` et devient lui-même Super-Pair d'un nouveau cluster ; ce délai évite la cascade d'auto-élections en cas de flap réseau transitoire

### Story 11.3: Heartbeat & Member Registry — Cohésion et Continuité du Cluster

En tant que Super-Pair,
Je veux maintenir un registre persisté des membres de mon cluster mis à jour par heartbeats et diffuser les changements d'appartenance,
Afin que la composition du cluster reste cohérente et qu'une mort du Super-Pair n'entraîne ni perte de membre ni nécessité de re-JOIN.

**Prérequis :** Story 11.2 terminée (besoin du `JoinAccept` qui amorce le registre).

**Acceptance Criteria:**

**Given** un cluster est formé suite à des `JoinAccept` (Story 11.2) et le Super-Pair maintient un `memberRegistry`
**When** le service P2P tourne
**Then** une table Room `cluster_members` est définie dans `data/local/m11_join/MemberEntity.kt` avec colonnes : `nodeId` (PK), `clusterId`, `publicKey` (ByteArray), `ipAddress` (String, dernière connue), `port` (Int), `gpsLatitude` (Double?), `gpsLongitude` (Double?), `freeBytes` (Long), `lastSeen` (epoch ms), `role` (`SUPER_PAIR` / `MEMBER`), `status` (`ACTIVE` / `EVICTED`)
**And** le `MemberDao` expose : `insertOrReplace`, `findByNodeId`, `listByClusterId(clusterId): Flow<List<MemberEntity>>` (filtré status=ACTIVE par défaut), `markEvicted(nodeId)`, `deleteByNodeId`, `purgeOlderThan(ttlMs)`
**And** la colonne `status` est utilisée pour distinguer un membre **récemment perdu mais récupérable** (EVICTED, gardé 1 h pour évent. retour) versus un membre **présent** (ACTIVE) ; les requêtes de routing intra-cluster ne consultent que les ACTIVE
**And** `ipAddress` et `port` sont indispensables au SP pour envoyer les `MEMBER_UPDATE` ciblés et pour permettre aux membres entre eux de communiquer en P2P direct (intra-cluster) ; ils sont fournis dans le `JOIN_REQUEST` initial et mis à jour par chaque `HEARTBEAT`
**And** côté membre régulier, le `MemberHeartbeatUseCase` envoie un message `HEARTBEAT(nodeId, freeBytes, timestampMs, signature)` au Super-Pair toutes les 30 secondes (configurable via `HEARTBEAT_INTERVAL_MS` dans `ClusterConstants.kt`)
**And** **Position GPS figée au JOIN (décision V5 assumée)** — le `HEARTBEAT` n'inclut **pas** `gpsLocation` ; la colonne `cluster_members.gpsLatitude/gpsLongitude` reste figée à la valeur fournie dans le `JOIN_REQUEST` initial ; le SP ne re-évalue jamais la proximité GPS d'un membre admis — la mobilité utilisateur (déplacement du téléphone après admission) est **explicitement Out-of-Scope V5** (perspective `EvaluateClusterFitUseCase` reportée) ; ce choix évite d'ajouter 16 octets × 50 membres × 2 880 heartbeats/jour (~2.3 MB/jour/cluster) de trafic GPS sans bénéfice fonctionnel V5
**And** côté Super-Pair, le `ProcessHeartbeatUseCase` valide la signature, met à jour `lastSeen` et `freeBytes` dans `MemberDao`
**And** côté Super-Pair, un job de surveillance lit `listByClusterId` toutes les 15 secondes ; pour chaque membre avec `now - lastSeen > 90s`, il :
  1. Supprime l'entrée du registre (`deleteByNodeId`)
  2. Diffuse `MEMBER_UPDATE { event: LEFT, nodeId, timestampMs, signature }` à tous les autres membres
**And** côté membre régulier, si aucun signal du SP n'est reçu pendant 90 s (heartbeat de réponse OU `MEMBER_UPDATE`), il émet `JoinEvent.SpTimeoutDetected` ; le `JoinStateMachine` transite vers `NodeJoinState.Rejoining(SP_TIMEOUT)` et déclenche `RunBullyElectionUseCase` (Epic 3 Story 3.1) ; le résultat du Bully est canalisé comme `JoinEvent.BullyVictory` (→ `MarkSelfAsSuperPairUseCase`) ou `JoinEvent.BullyLost` (→ retour `NodeJoinState.Member` avec le nouveau `superPairNodeId` issu du `COORDINATOR` reçu)
**And** **Post-Bully — câblage explicite** : si le membre **perd** l'élection Bully, il **n'émet PAS de nouveau `JOIN_REQUEST`** — il met simplement à jour son `Member.superPairNodeId` avec le winner ; le membership est préservé grâce au `inMemoryRegistry` partagé (FR-11.8) ; si le membre **gagne**, `MarkSelfAsSuperPairUseCase` repeuple `cluster_members` depuis son snapshot (voir AC plus bas)
**And** lors d'un `JOIN_ACCEPT` (Story 11.2), le SP diffuse `MEMBER_UPDATE { event: JOINED, member: MemberInfo }` aux membres existants
**And** chaque membre maintient un cache RAM `inMemoryRegistry: StateFlow<List<MemberInfo>>` mis à jour par :
  - le `memberSnapshot` reçu dans `JoinAccept`
  - les `MEMBER_UPDATE` (JOINED/LEFT) reçus ensuite
**And** lorsque le membre est élu nouveau SP via Bully (mort de l'ancien SP), il **réutilise** son `inMemoryRegistry` pour repeupler la table Room `cluster_members` — **aucun re-JOIN n'est demandé aux autres membres** (FR-11.8, continuité post-Bully)
**And** un test d'intégration valide le scénario T=6 de `exemple-concret-approche-join.md` :
  - Cluster {Alice SP, Bob, Carol} stable
  - `am force-stop` Alice
  - Après 90 s, Bob et Carol détectent l'absence
  - Bully entre {Bob, Carol} → Bob gagne
  - Bob lit son `inMemoryRegistry` (qui contient Alice+Carol) et insère Carol dans son `MemberDao` Room
  - Carol reste membre sans réémission de JOIN_REQUEST
**And** **NFR-09 mesurable** : sur un cluster de 50 membres simulés (50 corégions sur l'émulateur ou 50 acteurs in-process), la consommation CPU côté SP de l'agrégat `ProcessHeartbeatUseCase` + surveillance + diffusion `MEMBER_UPDATE` reste ≤ 1% en moyenne sur 30 minutes (Android Studio Profiler)
**And** le `MemberHeartbeatUseCase` (envoi côté membre) consomme ≤ 0.5% CPU
**And** la table `cluster_members` est purgée au démarrage du service via `purgeOlderThan(ttlMs = 24h)` pour éviter les fuites d'entrées orphelines
**And** la logique côté membre est dans `domain/usecase/m11_join/MemberHeartbeatUseCase.kt` ; côté SP dans `domain/usecase/m11_join/ProcessHeartbeatUseCase.kt` + `MonitorMemberLivenessUseCase.kt`
**And** **Message `LEAVE` volontaire** — un membre qui quitte gracieusement (utilisateur ferme l'app, désactivation manuelle du service) envoie un `LEAVE(nodeId, timestampMs, signature)` signé EC P-256 au SP avant déconnexion ; le SP traite ce message comme un timeout heartbeat **immédiat** (suppression de `MemberDao` + diffusion `MEMBER_UPDATE { event: LEFT }` sous 1 s, sans attendre les 90 s) ; le `LEAVE` est envoyé via `SendLeaveUseCase` côté membre, déclenché depuis `MobicloudP2PService.onDestroy()` et toute action UI explicite de désinscription ; si l'envoi échoue (réseau coupé brutalement), le SP retombera sur le mécanisme timeout 90 s
**And** **Snapshot persisté côté membre (continuité post-Bully)** — chaque membre régulier persiste son dernier `memberSnapshot` reçu (initial dans `JoinAccept`, incrémenté par les `MEMBER_UPDATE` JOINED/LEFT) dans une table Room dédiée `member_snapshot` (colonnes : `clusterId` PK, `superPairNodeId`, `lastUpdatedMs`, `membersJson` sérialisé en JSON/Protobuf) ; au démarrage du service après crash/redémarrage, le membre relit ce snapshot pour reprendre la conscience du cluster ; lorsqu'un membre devient SP via Bully, il **insère** chaque membre du snapshot dans la table `cluster_members` autoritaire et reprend la diffusion `MEMBER_UPDATE` sans demander de re-JOIN
**And** **Wire format inter-réseaux pour HEARTBEAT, MEMBER_UPDATE, LEAVE** — en LAN (membre et SP joignables directement), ces messages sont envoyés en UDP signé sur le port multicast/unicast du SP ; en inter-réseaux (membre 4G ↔ SP Wi-Fi distant), ils sont encapsulés dans le message relai existant `FORWARD` (0x07) de Story 8.1, en cohérence avec l'encapsulation JOIN définie en Story 11.2 — sous-types `0x01 = HEARTBEAT`, `0x02 = MEMBER_UPDATE`, `0x03 = LEAVE` (sous-types JOIN_* en `0x04..0x06` voir Story 11.2) ; envoi via `RelayWebSocketClient.uploadBlock(destNodeId, payload)` avec préfixe ajouté côté `domain/usecase/m11_join/` ; le SP désencapsule à la réception ; **aucune modification de `relay-server/server.js`** requise

---

## Out of Scope V5 (Perspectives Rapport)

Les éléments suivants sont **explicitement reportés** au-delà de V5 et documentés en chapitre "Perspectives" du mémoire PFE :

- **Migration de nœud entre clusters** (`EvaluateClusterFitUseCase`) : un membre dont le GPS sort du rayon ne change pas automatiquement de cluster. Logique à concevoir si la mobilité utilisateur s'avère un cas réel.
- **Re-réplication des blocs sur `LEFT`** : quand un membre quitte le cluster, les blocs qu'il hébergeait deviennent partiellement orphelins. La re-réplication automatique côté SP nécessite une orchestration similaire à Story 7.2 et est reportée.
- **Défense Sybil GPS spoofing** : un attaquant peut falsifier sa position GPS via Mock Location (Magisk, Android Developer Options). Une attestation device hardware-backed (Play Integrity API, RemoteAttestation TEE) serait nécessaire.
- **Super-Pair byzantin (refus arbitraire)** : un SP malveillant peut rejeter des candidats légitimes. Modèle d'attaque honest-but-curious assumé pour V5.
- **Découverte inter-cluster scalable** : à 10 000+ clusters, le tracker HA devient un goulot. Sharding géographique (geohash, S2 cells) ou DHT entre Super-Pairs (Kademlia overlay style IPFS) à étudier.

---

## Epic 12 — Décentralisation de l'Admission (Refactor)

**Objectif :** Retirer le GPS du codebase MobiCloud (Epic 11 refactor) et remplacer le critère de délimitation géographique par un critère de charge (memberCount). L'Epic 12 corrige une décision architecturale de l'Epic 11 devenue incompatible avec le déploiement V5.0 réel (Relai HA — transport centralisé indépendant de la géographie).

**Motivation :** cf. section "Évolution V5.1" dans architecture-connectivity-and-clustering.md.

### Story 12.1 — Suppression du GPS, admission cluster par charge (memberCount)

**Statut :** done

**Résumé :**
- Suppression de `Haversine.kt`, `GpsCoordinate.kt`, `LocationRepository` (interface + impl + DI module)
- Retrait des permissions Android `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- Remplacement du critère géographique par `currentMemberCount < MAX_CLUSTER_SIZE = 50`
- Sélection sticky-cluster + load balancing (`sortedBy { currentMemberCount }`) dans `SendJoinRequestUseCase`
- Migration Room v15 → v16 (retrait colonnes GPS de `cluster_members`)
- Propagation de `currentMemberCount` dans HELLO multicast, REGISTER_PEER tracker et GET_PEERS
- UI Dashboard : indicateur "N / 50 membres" dans ClusterTopologyCard

### Story 12.2 — Tracker load-based discovery (Serveur Node.js — story séparée)

**Statut :** backlog (hors scope story 12.1)

**Résumé :** Tri des super-peers par `memberCount` ASC côté tracker (Render relay-server). Les changements de server.js relatifs au retrait GPS ont été inclus dans la Story 12.1 ; la logique de tri serveur et les tests Jest complets font l'objet d'une story dédiée.
