# Proposition de Changement de Sprint (Correct Course) - MobiCloud

**Date :** 2026-04-13
**Objectif :** Réconciliation de l'Architecture Hybride (Inter-réseaux) avec les exigences académiques P2P pures (CRDT, Bully, Karma).

## 1. Résumé du Problème (Issue Summary)
**Déclencheur :** Lors de la transition vers une architecture capable de fonctionner sur des réseaux différents (4G, Wifi distincts), le PRD V3.0 et les Epics ont indûment supprimé les modules algorithmiques complexes indispensables à un PFE de niveau Master (DHT, Synchronisation Gossip/CRDT, Algorithme d'élection Bully, Système de Karma).
**Le vrai défi :** Il a été présumé (à tort) que pour fonctionner hors réseau local, le système *devait* centraliser son catalogue sur le Super-Pair et abandonner le P2P pur. L'objectif est donc de concevoir une véritable logique de "Fédération" où le serveur fixe ne sert **qu'à la signalisation et à la rencontre**, tandis que la gestion et les données restent purement distribuées (Gossip, CRDT).

## 2. Analyse d'Impact
- **Impact sur le PRD :** Le document actuel stipule l'abandon du Gossip et des validations (Karma). Il doit être actualisé pour restaurer ces modules tout en gardant l'autorisation d'un Tracker DDNS/Serveur pour franchir les barrières réseau (NAT).
- **Impact sur les Epics :** Le backlog passe de 4 Epics (simplifiés Hybride centralisé) à **7 Epics Master-Level**.
- **Impact Architectural :** Le serveur "DDNS" devient uniquement un Annuaire de Routage (Signaling Server). Les Super-Pairs s'en servent pour connecter les "îlots" ou régions ensemble. À l'intérieur du réseau global, le catalogue redevient une DHT synchronisée par algorithmes épidémiques (Gossip).

## 3. Approche Recommandée : Fédération de Clusters (Hybridation Forte)
**Option Choisie : Ajustement et Réécriture Structurée.**
Plutôt que d'abandonner l'inter-réseau, nous allons l'intégrer aux algorithmes P2P :
- **Découverte Hybride :** Si un nœud est en 4G, il utilise le serveur fixe pour trouver un Super-Pair. S'il est en Wifi local, il utilise le Multicast.
- **Réintégration des Modules Master :** Une fois le nœud connecté au réseau global, il participe à l'anneau DHT, synchronise son catalogue via CRDT (Conflict-free Replicated Data Type) et cumule un score de Karma. En cas de changement de réseau, une "Migration Proactive" est initiée entre les Super-Pairs.

## 4. Propositions de Modifications Détaillées (Edit Proposals)

> [!WARNING]
> Ces modifications écraseront l'approche simpliste de centralisation du PRD 3.0 pour remettre la complexité au cœur du projet.

### Mise à jour du PRD (`prd.md`)
**MODIFICATION (FR-01)**
- *OLD:* "Le système s'appuie sur une adresse fixe... Le DDNS gère la signalisation."
- *NEW:* Le Serveur Fixe agit uniquement comme STUN/Tracker pour fédérer les appareils de sous-réseaux différents.

**MODIFICATION (FR-04)**
- *OLD:* "Le système délaisse les DHT/Gossip... Catalogue centralisé sur le Super-Pair."
- *NEW:* **Restauration de la DHT**. L'index global des blocs est partagé. Le Super-Pair connecte les régions (clusters), mais la synchronisation du catalogue utilise un protocole Gossip (CRDT).

**MODIFICATION (FR-Extra)**
- *NEW:* Ajout explicite des exigences "Migration Géographique Inter-Réseaux via les Super-Pairs" et "Système Anti-Clandestin (Karma)".

### Nouvelle Structure des Epics (`epics.md`)
1. **Epic 1: Fondation Sécurisée & UI/UX** (Zéro-Trust, Identité asymétrique, Thème sombre).
2. **Epic 2: Découverte Hybride & Dashboard Tactique** (Multicast UDP pour le local + Signaling Server fixe pour l'extérieur/4G).
3. **Epic 3: Algorithme Bully & Fédérations** (Élection du Super-Pair, enregistrement sur l'annuaire fixe pour lier les clusters).
4. **Epic 4: DHT (CRDT) & Gossip Inter-Clusters** (Synchronisation épidémique de l'arborescence du catalogue).
5. **Epic 5: Erasure Coding C++ P2P** (Fragmentation vectorielle, Hébergement, Chiffrement).
6. **Epic 6: Résilience Extrême & Migration Géographique** (Déclenchement du transfert pro-actif lorsqu'un nœud migre d'une région A à B).
7. **Epic 7: Karma Équitable & Téléchargement Distribué** (Téléchargement multi-sources concurrent, intégration finale des Jauges).

## 5. Plan de Transfert (Handoff)
- **Classification :** Majeur (Replanification fondamentale requise).
- **Destinataires :** Route(s) d'implémentation vers les agents BMad Product Manager (`bmad-agent-pm`) et Solution Architect (`bmad-agent-architect`).
- **Livrables à venir :** Réécriture finalisée du `prd.md` et `epics.md` conformes à ce plan.
