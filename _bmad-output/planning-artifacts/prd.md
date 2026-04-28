---
stepsCompleted: ['step-e-01-discovery', 'step-e-02-review', 'step-e-03-edit', 'step-e-04-readiness-fix']
inputDocuments: ['concept_mobicloud_V2.md', 'conception_modules_detailles.md', 'sprint-change-proposal-2026-04-13.md', 'sprint-change-proposal-2026-04-28.md']
workflowType: 'prd'
workflow: 'edit'
documentCounts:
  briefs: 1
  research: 0
  brainstorming: 0
  projectDocs: 2
classification:
  projectType: 'mobile_app + distributed_systems'
  domain: 'PFE Big Data / P2P Fédération Hybride'
  complexity: 'medium-high'
  projectContext: 'brownfield'
lastEdited: '2026-04-28'
editHistory:
  - date: '2026-04-13'
    changes: 'Refonte V5.0 — Fédération de Clusters Hybride avec Serveurs Relais HA WebSocket (Zero-Firebase, Zero-STUN, Zero-DDNS). Réintégration de la DHT, CRDT, Bully et Migration Géographique. Karma retiré.'
  - date: '2026-04-28'
    changes: 'Fix readiness — FR-01.2 scindé en signaling pur, ajout FR-05 (téléchargement concurrent K+2), ajout FR-08 (fallback Store-and-Forward), ajout NFR-04..07 (résilience churn, sécurité AES-GCM, mandat super-pair, anti-Sybil), métadonnées V5 alignées.'
---

# Product Requirements Document — MobiCloud

**Auteur :** Yasmine (Révisé et validé pour la V5)  
**Date :** 2026-04-28  
**Version :** 5.0 (Architecture Fédération de Clusters Hybride — Zero-Firebase)  
**Statut :** Conception Validée — Prêt pour Développement  

---

## 1. Résumé Exécutif (Executive Summary)

### 1.1 Le Problème
Dans des environnements éclatés où les utilisateurs mobiles accèdent à différents réseaux (4G, réseaux Wifi universitaires isolés), il est laborieux de partager des fichiers lourds de manière purement décentralisée. HDFS classique est inadapté au mobile. Les approches centralisées simplistes détruisent la nature P2P du système, tandis que le P2P pur est bloqué par les NAT et les changements de réseaux (churn). 

### 1.2 La Solution : MobiCloud "Fédération de Clusters"
**MobiCloud** est un **Datalake Mobile** reposant sur une architecture de "Fédération de Clusters" alliant une signalisation centralisée (Tracker) pour traverser les NAT, et des modules algorithmiques distribués natifs de niveau Master pour l'orchestration interne :

1. **Topologie Fédérée (Serveurs Relais HA WebSocket)** : Un cluster de Serveurs Relais HA Node.js (min 2 instances Zero-Knowledge) agit comme annuaire de signalisation (REGISTER_PEER / GET_PEERS) pour lier des "îlots" ou régions (clusters 4G vs Wifi), permettant ainsi la découverte inter-réseaux. Aucune dépendance à Firebase, STUN, DDNS ou tout autre service tiers.
2. **Synchronisation Décentralisée (Gossip & CRDT)** : À l'intérieur du réseau (et entre les Super-Pairs), le catalogue des fichiers redevient partagé via une DHT (Table de Hachage Distribuée) et synchronisé de manière épidémique (Gossip).
3. **Orchestration Avancée (Algorithme Bully)** : L'élection des Super-Pairs est dictée par l'algorithme de Bully, et le système supporte la migration géographique inter-réseaux.
4. **Erasure Coding P2P** : Optimisation de la résilience et économie de la batterie grâce au découpage vectoriel des fichiers via du code C++ natif.

---

## 2. Vision du Produit

### 2.1 Vision Long Terme
> *"Fédérer la puissance de stockage des smartphones de différents sous-réseaux au sein d'un maillage P2P pur, en s'appuyant ponctuellement sur un tracker pour la rencontre inter-clusters, garantissant un système sans point de défaillance central (CRDT) et un téléchargement concurrent multi-sources."*

### 2.2 Parcours Utilisateurs Clés (User Journeys)

1. **UJ-01 : Découverte Hybride et Fédération**
   - *Déclencheur :* L'application démarre.
   - *Flux :* En local (Wifi), elle recherche des pairs via Multicast UDP. Si elle est isolée (4G), elle interroge le serveur Tracker fixe pour rencontrer le Super-Pair de sa région.
   
2. **UJ-02 : Élection de Super-Pair (Bully)**
   - *Déclencheur :* Aucun Super-Pair n'est joignable sur la boucle DHT.
   - *Flux :* Les nœuds locaux déclenchent un message d'élection `ELECTION` (Algorithme Bully). Le nœud avec le plus haut Score de Fiabilité déclare victoire et s'enregistre auprès du Tracker.
   
3. **UJ-03 : Synchronisation CRDT / Gossip**
   - *Déclencheur :* Un pair génère ou reçoit de nouveaux blocs Erasure.
   - *Flux :* Il met à jour son état local et "Gossip" (murmure) cette modification de la DHT aux nœuds voisins, garantissant une convergence éventuelle (CRDT) sans autorité centrale.

4. **UJ-04 : Téléchargement Distribué Concurrent**
   - *Déclencheur :* Récupération d'un fichier lourd.
   - *Flux :* L'application sollicite simultanément *K* téléphones. Le C++ natif rassemble ensuite les *K* blocs vectoriels (Erasure).

5. **UJ-05 : Migration Pro-Active Inter-Réseaux**
   - *Déclencheur :* L'utilisateur subit un basculement de réseau WiFi vers 4G pendant une opération.
   - *Flux :* Le nœud signale son départ imminent. Le Super-Pair orchestre le transfert des blocs que le nœud hébergeait vers d'autres membres du cluster local pour maintenir le niveau de résilience avant la déconnexion.

### 2.3 Périmètre du Produit (Product Scope)

Le PFE justifie sa nature "Big Data" et "Systèmes Distribués Avancés" avec la **réintégration explicite des algorithmes de niveau Master** : DHT, Synchronisation Gossip/CRDT, Algorithme Bully et UDP Multicast, le tout interconnecté par un simple cluster de Serveurs Relais HA WebSocket minimaliste (qui ne gère ni les données ni le catalogue SQLite centralisé, contrairement à la V3.0).

---

## 3. Architecture Fonctionnelle — Cartographie des Modules

```text
┌─────────────────────────────────────────────────────────────┐
│        ARCHI V5: FÉDÉRATION HYBRIDE + RELAIS HA             │
│                                                             │
│       ┌──────────────────┐                                  │
│       │ Serveurs Relais  │ ◀── (Signaling + Fallback NAT)   │
│       │ HA WebSocket x2+ │                                  │
│       └──────────────────┘                                  │
│          ▲           ▲                                      │
│    (Fédère les Super-Pairs)                                 │
│          ▼           ▼                                      │
│  ┌──────────────┐  ┌──────────────┐   ┌────────────────┐    │
│  │ Cluster Wifi │  │ Cluster 4G   │   │  5. Moteur C++ │    │
│  │ (Super-Pair) │  │ (Super-Pair) │   │ Erasure Coding │    │
│  └──────────────┘  └──────────────┘   └────────────────┘    │
│     ▲       ▲         ▲       ▲               ▲             │
│   (Synchronisation Inter-Clusters via Gossip) │             │
│     ▼       ▼         ▼       ▼               │             │
│  ┌─────┐ ┌─────┐   ┌─────┐ ┌─────┐            │ (Chiffre /  │
│  │ Pair│ │ Pair│   │ Pair│ │ Pair│ ◀──────────┘ Défragmente)│
│  │ DHT │ │ DHT │   │ DHT │ │ DHT │ (CRDT)                   │
│  └─────┘ └─────┘   └─────┘ └─────┘                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Exigences Fonctionnelles Détaillées

### FR-01 : Découverte Hybride et Signalisation UDP/TCP
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-01.1 | Le système local utilise le **Multicast UDP** pour la découverte au sein d'un même sous-réseau sans serveur (LAN partagé : Wi-Fi campus, Wi-Fi conférence). Sert de chemin prioritaire avant tout recours à la couche Relais HA. | P0 |
| FR-01.2 | Un cluster de **Serveurs Relais HA WebSocket** (min 2 instances Node.js, Zero-Knowledge) agit comme **annuaire de signalisation** (REGISTER_PEER / GET_PEERS) pour fédérer virtuellement des Super-Pairs séparés par NAT (4G ↔ Wi-Fi ou Wi-Fi ↔ Wi-Fi distincts). Rôle strictement signaling — le transport est traité en FR-08. Aucune dépendance Firebase / STUN / DDNS. | P0 |
| FR-01.3 | Tous les transferts (Catalogue ou Fichiers) se font en direct P2P de bout en bout (Zero-Trust) de Node à Node, avec fallback transparent vers FR-08 si le P2P direct échoue. | P0 |

### FR-02 : Élection Bully et Scoring de Fiabilité
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-02.1 | Chaque appareil mesure sa stabilité (Batterie, Uptime, IP Locale). L'élection locale d'un orchestrateur s'effectue strictement via **l'Algorithme Bully**. | P0 |
| FR-02.2 | Le gagnant devient "Super-Pair" et s'enregistre auprès des Serveurs Relais HA (REGISTER_PEER signé EC P-256) pour relier son cluster à la fédération. | P0 |

### FR-03 : Erasure Coding P2P & Chiffrement C++
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-03.1 | Utilisation de l'**Erasure Coding** (vectoriel C++ NDK) pour diviser le fichier en *K+N* blocs sans réplication complète redondante. | P0 |
| FR-03.2 | Tous les fragments doivent être chiffrés avec la cryptographie asymétrique (Zero-Trust/Zero-Knowledge). L'hébergeur ne peut lire le bloc. | P0 |

### FR-04 : Restauration de la DHT partagée (CRDT / Gossip)
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-04.1 | **Remplacement SQLite :** L'index global des blocs redevient partagé dans un anneau **DHT** entre tous les pairs qualifiés du cluster. | P0 |
| FR-04.2 | La synchronisation de l'arborescence (arbre de Merkle ou CRDT) repose sur un algorithme épidémique (**Gossip**). | P0 |

### FR-05 : Téléchargement Distribué Concurrent (K+2 Multi-Sources)
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-05.1 | La récupération d'un fichier ouvre **K+2 requêtes TCP parallèles** simultanées vers K+2 nœuds détenteurs (K blocs requis + 2 de secours compétitifs). Les 2 plus lents sont annulés dès que K blocs valides sont reçus. | P0 |
| FR-05.2 | Le déchiffrement et le réassemblage Erasure démarrent en **streaming actif** dès les premiers K blocs valides disponibles, sans attendre la fin du téléchargement complet. | P0 |

### FR-06 : Migration Géographique Inter-Réseaux
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-06.1 | Si le système détecte la sortie imminente d'un nœud d'un cluster, il déclenche un transfert proactif (migration d'état de ses blocs vers le cluster local). | P1 |

### FR-08 : Fallback de Transport Inter-Réseaux (Store-and-Forward Zero-Knowledge)
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-08.1 | Lorsque le P2P direct échoue (NAT symétrique, pare-feu opérateur), les blocs **chiffrés AES-256 GCM opaques** sont transmis via les Serveurs Relais HA WebSocket en mode **Store-and-Forward 60 s en RAM uniquement** (UPLOAD/FORWARD). Les serveurs ne peuvent jamais déchiffrer le contenu (Zero-Knowledge). Purge automatique au-delà du TTL. | P0 |
| FR-08.2 | Le client implémente un **fallback transparent Try-Direct-Then-Relay** : tentative TCP directe (Priorité 1), bascule automatique sur Relais HA (Priorité 2), failover séquentiel inter-instances HA (Priorité 3). Le UseCase appelant ne connaît pas le canal utilisé. | P0 |

---

## 5. Exigences Non-Fonctionnelles (NFR)

*   **NFR-01 (Convergence CRDT) :** La transmission épidémique (Gossip) au sein d'un cluster doit garantir une convergence éventuelle de la DHT avec un délai maximum mesurable ($\le 3$ secondes) lors de l'ajout d'un nouveau bloc. **Vérifiable via instrumentation timing dans Story 4.2.**
*   **NFR-02 (Latence de Migration) :** Le temps mis par l'application pour déclencher et orchestrer la récupération d'un fichier hébergé localement avant l'interruption réseau doit être inférieur à 5 secondes. **Vérifiable via instrumentation timing dans Story 7.2.**
*   **NFR-03 (Batterie/CPU) :** L'overhead induit par le système de CRDT/Gossip en arrière-plan ne doit pas excéder 5% d'utilisation processeur sur 30 minutes de tourner-à-vide. L'algorithme NDK C++ pour Erasure Coding compense la complexité. **Vérifiable via Android Studio Profiler en Story 5.1 / 1.4.**
*   **NFR-04 (Résilience Churn) :** Le Circuit-Breaker Anti-Avalanche doit geler les transferts de réparation lorsque le taux de pairs `INACTIVE` dépasse 30% du cluster en moins de 5 minutes, et reprendre automatiquement lorsque le churn redescend sous 10%.
*   **NFR-05 (Sécurité — Zero-Knowledge bout-en-bout) :** Tous les blocs Erasure sont chiffrés AES-256 GCM avec clés éphémères dérivées par bloc (HKDF). La clé maître fichier est protégée par chiffrement asymétrique ECIES. Aucun nœud hébergeur ni Serveur Relais HA ne peut déchiffrer un bloc.
*   **NFR-06 (Mandat Super-Pair Limité) :** Tout Super-Pair élu abdique automatiquement après 30 minutes de mandat et est exclu de la prochaine élection pendant 5 minutes (cooldown). Empêche l'épuisement batterie et garantit la promotabilité du rôle.
*   **NFR-07 (Anti-Sybil — Identité Hardware-Backed) :** L'identité de chaque nœud est une paire EC P-256 stockée exclusivement dans l'Android Keystore System (TEE / Secure Hardware). La clé privée n'est jamais exportable (`isInsideSecureHardware == true`).

---
> **Statut du PRD : COMPLET — Version 5.0**  
> (Validation PFE avec "Fédération de Clusters" Zero-Firebase, Modules Algorithmiques Avancés restaurés, et exigences de sécurité/résilience formalisées.)
