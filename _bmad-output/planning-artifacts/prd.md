---
stepsCompleted: ['step-e-01-discovery', 'step-e-02-review', 'step-e-03-edit']
inputDocuments: ['concept_mobicloud_V2.md', 'conception_modules_detailles.md', 'sprint-change-proposal-2026-04-13.md']
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
lastEdited: '2026-04-13'
editHistory:
  - date: '2026-04-13'
    changes: 'Refonte vers la V4.0 (Fédération de Clusters). Réintroduction de la DHT, CRDT, Bully, Karma et Migration Géographique. Rôle du DDNS restreint à STUN/Tracker.'
---

# Product Requirements Document — MobiCloud

**Auteur :** Yasmine (Révisé et validé pour la V4)  
**Date :** 2026-04-13  
**Version :** 4.0 (Architecture Fédération de Clusters Hybride)  
**Statut :** Conception Validée — Prêt pour Développement  

---

## 1. Résumé Exécutif (Executive Summary)

### 1.1 Le Problème
Dans des environnements éclatés où les utilisateurs mobiles accèdent à différents réseaux (4G, réseaux Wifi universitaires isolés), il est laborieux de partager des fichiers lourds de manière purement décentralisée. HDFS classique est inadapté au mobile. Les approches centralisées simplistes détruisent la nature P2P du système, tandis que le P2P pur est bloqué par les NAT et les changements de réseaux (churn). 

### 1.2 La Solution : MobiCloud "Fédération de Clusters"
**MobiCloud** est un **Datalake Mobile** reposant sur une architecture de "Fédération de Clusters" alliant une signalisation centralisée (Tracker) pour traverser les NAT, et des modules algorithmiques distribués natifs de niveau Master pour l'orchestration interne :

1. **Topologie Fédérée (Tracker STUN/DDNS)** : Le serveur central fixe n'agit que comme un annuaire de rencontre (Signaling Server) pour lier des "îlots" ou régions (clusters 4G vs Wifi), permettant ainsi la découverte inter-réseaux.
2. **Synchronisation Décentralisée (Gossip & CRDT)** : À l'intérieur du réseau (et entre les Super-Pairs), le catalogue des fichiers redevient partagé via une DHT (Table de Hachage Distribuée) et synchronisé de manière épidémique (Gossip).
3. **Orchestration Avancée (Algorithme Bully & Karma)** : L'élection des Super-Pairs est dictée par l'algorithme de Bully. Le système gère des scores de Karma (Anti-Clandestins) pour garantir l'équité, et supporte la migration géographique inter-réseaux.
4. **Erasure Coding P2P** : Optimisation de la résilience et économie de la batterie grâce au découpage vectoriel des fichiers via du code C++ natif.

---

## 2. Vision du Produit

### 2.1 Vision Long Terme
> *"Fédérer la puissance de stockage des smartphones de différents sous-réseaux au sein d'un maillage P2P pur, en s'appuyant ponctuellement sur un tracker pour la rencontre inter-clusters, garantissant un système sans point de défaillance central (CRDT) et un téléchargement concurrent justifié (Karma)."*

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

4. **UJ-04 : Téléchargement Distribué Concurrent et Karma**
   - *Déclencheur :* Récupération d'un fichier lourd.
   - *Flux :* L'application sollicite simultanément *K* téléphones. Pour chaque téléchargement, le téléchargeur consomme du Karma. Les semeurs augmentent leur Karma. Le C++ natif rassemble ensuite les *K* blocs vectoriels (Erasure).

5. **UJ-05 : Migration Pro-Active Inter-Réseaux**
   - *Déclencheur :* L'utilisateur subit un basculement de réseau WiFi vers 4G pendant une opération.
   - *Flux :* Le nœud signale son départ imminent. Le Super-Pair orchestre le transfert des blocs que le nœud hébergeait vers d'autres membres du cluster local pour maintenir le niveau de résilience avant la déconnexion.

### 2.3 Périmètre du Produit (Product Scope)

Le PFE justifie sa nature "Big Data" et "Systèmes Distribués Avancés" avec la **réintégration explicite des algorithmes de niveau Master** : DHT, Synchronisation Gossip/CRDT, Algorithme Bully, Karma Anti-clandestins, et UDP Multicast, le tout interconnecté par un simple Tracker STUN minimaliste (qui ne gère ni les données ni le catalogue SQLite centralisé, contrairement à la V3.0).

---

## 3. Architecture Fonctionnelle — Cartographie des Modules

```text
┌─────────────────────────────────────────────────────────────┐
│             ARCHI V4: FÉDÉRATION HYBRIDE DE CLUSTERS        │
│                                                             │
│       ┌────────────────┐                                    │
│       │ Tracker        │ ◀─── (Signaling & NAT Traversal)   │
│       │ STUN / DDNS    │                                    │
│       └────────────────┘                                    │
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
| FR-01.1 | Le système local utilise le **Multicast UDP** pour la découverte au sein d'un même sous-réseau sans serveur. | P0 |
| FR-01.2 | Le Serveur Fixe agit **uniquement comme STUN/Tracker** pour aider à fédérer virtuellement des réseaux séparés (NAT) et lier les Super-Pairs. | P0 |
| FR-01.3 | Tous les transferts (Catalogue ou Fichiers) se font en direct P2P de bout en bout (Zero-Trust) de Node à Node. | P0 |

### FR-02 : Élection Bully et Scoring de Fiabilité
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-02.1 | Chaque appareil mesure sa stabilité (Batterie, Uptime, IP Locale). L'élection locale d'un orchestrateur s'effectue strictement via **l'Algorithme Bully**. | P0 |
| FR-02.2 | Le gagnant devient "Super-Pair" et relaie la table de routage sur le Tracker STUN pour relier son cluster à la fédération. | P0 |

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

### FR-06 : Migration Géographique Inter-Réseaux
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-06.1 | Si le système détecte la sortie imminente d'un nœud d'un cluster, il déclenche un transfert proactif (migration d'état de ses blocs vers le cluster local). | P1 |

### FR-07 : Système Anti-Clandestin de Karma
| ID | Exigence | Priorité |
|----|----------|----------|
| FR-07.1 | Un nœud gagne des points de Karma lorsqu'il stocke et sert les requêtes. Il dépense du Karma en demandant des téléchargements. Les nœuds égoïstes (freeriders) voient leur bande passante bridée. | P1 |

---

## 5. Exigences Non-Fonctionnelles (NFR)

*   **NFR-01 (Convergence CRDT) :** La transmission épidémique (Gossip) au sein d'un cluster doit garantir une convergence éventuelle de la DHT avec un délai maximum mesurable (ex: $\le 3$ secondes) lors de l'ajout d'un nouveau bloc.
*   **NFR-02 (Latence de Migration) :** Le temps mis par l'application pour déclencher et orchestrer la récupération d'un fichier hébergé localement avant l'interruption réseau doit être inférieur à 5 secondes.
*   **NFR-03 (Batterie/CPU) :** L'overhead induit par le système de CRDT/Gossip en arrière-plan ne doit pas excéder 5% d'utilisation proc. L'algorithme NDK C++ pour Erasure Coding compense la complexité.

---
> **Statut du PRD : COMPLET — Version 4.0**  
> (Validation PFE avec "Fédération de Clusters" et Modules Algorithmiques Avancés restaurés.)
