---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: ['prd.md', 'description_technique_formelle.md', 'architecture.md']
scopeRevision: "PFE Realiste - Deadline 03 Juin 2026"
scopeDate: "2026-04-08"
team: "Binôme | Niveau Intermédiaire | 4h/jour chacun"
---

# MobiCloud - Epic Breakdown (Scope PFE Réaliste — 03 Juin 2026)

## ⚠️ Note de Révision de Scope

Ce document est la version **révisée et réaliste** du backlog MobiCloud, calibrée pour une soutenance PFE le **3 juin 2026** par un binôme travaillant ~4h/jour chacun (~160-170h productives restantes).

**Principe directeur :** Un jury PFE évalue la maîtrise de l'architecture et la qualité de la démonstration — pas l'exhaustivité des features. Mieux vaut 5 épics solides et démontrables qu'un projet à 8 épics qui crashe en soutenance.

### Légende
- ✅ **INCLUS** — Implémenté complètement
- ✂️ **SIMPLIFIÉ** — Implémenté avec contraintes allégées (justification fournie)
- ❌ **RETIRÉ** — Hors scope PFE (justification fournie, documenté pour le rapport)

---

## Planning Sprint (8 semaines — 08 Avril → 03 Juin 2026)

| Semaines | Sprint | Objectif | Livrable Demo |
|---|---|---|---|
| S1-S2 | Sprint 1 | Epic 0 (finalisé) + Epic 1 (finalisé) | App qui démarre, identité crypto, discovery UDP |
| S3-S4 | Sprint 2 | Epic 2 complet | Catalogue DHT synchronisé entre 2 téléphones |
| S5 | Sprint 3 | Epic 3 (3.1 + 3.2 simplifié) | Fichier chiffré + fragmenté visible |
| S6 | Sprint 4 | Epic 4 (4.1 + 4.2 + 4.3) | Download streaming P2P fonctionnel |
| S6 fin | Sprint 4 | Story 6.3 Circuit-Breaker | Résilience visible en démo |
| S7 | Sprint 5 | Epic 5 (5.1 + 5.2) + Story 6.1 | Super-Pair dynamique + auto-réparation mock |
| **S8** | **Buffer** | **Intégration, bugfix, démo stable, rapport** | **Démo finale sans crash** |

> **Règle absolue :** La semaine S8 est INTOUCHABLE pour du dev. Elle est entièrement dédiée au rapport et à la répétition de soutenance.

---

## Requirements Inventory (inchangé — tous couverts en architecture même si non implémentés)

### Functional Requirements
FR1: Le système doit permettre à un nœud de s'authentifier localement et sans serveur via un défi Hashcash asymétrique.
FR2: Le système doit attribuer dynamiquement un score de fiabilité IA à chaque nœud pour ajuster son poids de stockage.
FR3: Le système doit maintenir une liste des pairs (routage) en temps quasi-réel adaptatif via un protocole de Heartbeat ultra-léger pour préserver la batterie.
FR4: Le système doit synchroniser les deltas du catalogue distribué en utilisant un protocole Gossip partitionné basé sur des Filtres de Bloom.
FR5: Le système doit persister un catalogue P2P (DHT) basé sur des CRDTs pour la résolution des fiches fichiers.
FR6: Le système doit orchestrer la détection de fragments perdus et déclencher l'auto-réparation ciblée.
FR7: Le système doit permettre l'évacuation proactive de fragments chiffrés invisibles juste avant que l'hébergeur ne quitte le réseau.
FR8: Le système doit découper et chiffrer tout fichier entrant via Erasure Coding en blocs de parité selon les ressources courantes.
FR9: Le système doit télécharger et déchiffrer en streaming adaptatif les K fragments originaux d'un fichier hébergé sur le maillage.
FR10: Le système doit inciter à la coopération par un score de "Karma" anti-clandestin qui s'évapore dans le temps.
FR11: Le système doit élire un Super-Pair de coordination locale, avec hystérésis de continuité et abdication automatique fixée à 30 minutes.

### NonFunctional Requirements (inchangés — architecture documentée même si partiellement implémentée)
NFR1 à NFR5 : conservés dans l'architecture et le rapport.

---

## Epic List Révisée

---

### ✅ Epic 0: Fondation Technique P2P 🛠️ (Enabler Epic)
Statut scope : **INCLUS COMPLET**
Estimation : ~1 semaine binôme

#### ✅ Story 0.1: Initialisation du Starter & Clean Architecture
*(inchangée)*

#### ✅ Story 0.2: Configuration Protobuf & Sérialisation
*(inchangée)*

#### ✅ Story 0.3: Configuration du Build NDK & CMake
*(inchangée)*

---

### ✅ Epic 1: Identité & Découverte Réseau (Rejoindre l'Essaim)
Statut scope : **INCLUS COMPLET**
Estimation : ~1 semaine binôme (vous y êtes déjà à la Story 1.5)

#### ✅ Story 1.1: Génération de la Node Identity (AndroidKeyStore)
*(inchangée)*

#### ✅ Story 1.2: Preuve de Travail Hashcash Anti-Sybil
*(inchangée)*

#### ✅ Story 1.3: Couche Réseau (Foreground Service, Multicast & P2P Hardware Fallback)
*(inchangée)*

#### ✅ Story 1.4: Protocole Heartbeat Adaptatif et Registre des Pairs
*(inchangée)*

#### ✅ Story 1.5: Backoff Progressif d'Annonce (Anti-Saturation)
*(inchangée — en cours)*

---

### ✅ Epic 2: Catalogue Distribué P2P (Explorer le Contenu)
Statut scope : **INCLUS COMPLET**
Estimation : ~2 semaines binôme
Justification : C'est le cœur intellectuel du projet. DHT + CRDTs + Bloom Filters = le meilleur ratio "impressionnant en soutenance / faisable" de tout le backlog.

#### ✅ Story 2.1: Modèle DHT Partitionné (CRDT) & Jetpack Room
*(inchangée)*

#### ✅ Story 2.2: Élection Basique & Genesis Hashcash
*(inchangée)*

#### ✅ Story 2.3: Filtres de Bloom Paramétrés et Gossip Epidémique
*(inchangée)*

#### ✅ Story 2.4: Transmission Delta (Désynchronisation)
*(inchangée)*

#### ✅ Story 2.5: Garbage Collector et Time-To-Live (Purge du Catalogue)
*(inchangée)*

---

### ✂️ Epic 3: Stockage Sécurisé & Découpage (Partager un Fichier)
Statut scope : **SIMPLIFIÉ**
Estimation : ~1 semaine binôme

#### ✅ Story 3.1: Chunking de Fichiers & Chiffrement AES-256
*(inchangée — priorité absolue)*

#### ✂️ Story 3.2: Erasure Coding avec Bridge C++ NDK
**Simplification PFE :** L'Erasure Coding C++ via JNI est conservé car c'est un argument technique fort en soutenance. En revanche, l'optimisation **SIMD ARM NEON est retirée** du scope d'implémentation.

**Justification :** Le SIMD NEON est une optimisation de performance (NFR2), non une feature fonctionnelle. L'architecture JNI + DirectByteBuffer reste en place et démontre la maîtrise NDK. La différence de performance sera documentée dans le rapport comme "amélioration future mesurée".

**Acceptance Criteria (révisés) :**
**Given** une structure de blocs chiffrés et un paramètre de redondance mock statique (M=50%),
**When** l'agent Kotlin appelle la méthode JNI,
**Then** les données sont transmises en `DirectByteBuffer` au C++,
**And** le calcul EC retourne les K + M blocs finalisés via une implémentation Reed-Solomon C++ standard (sans intrinsics NEON dans ce sprint).
**And** un commentaire d'architecture documente l'emplacement prévu des intrinsics `vld1q_u8` / `veorq_u8` pour la vectorisation future.

---

### ✅ Epic 4: Récupération Haute Vitesse (Consommer le Contenu)
Statut scope : **INCLUS COMPLET**
Estimation : ~1 semaine binôme

#### ✅ Story 4.1: Résolution des Sources P2P (DHT)
*(inchangée)*

#### ✅ Story 4.2: Téléchargement Compétitif par Fenêtres Glissantes
*(inchangée)*

#### ✅ Story 4.3: Pipeline de Streaming et Déchiffrement Actif
*(inchangée)*

#### ✅ Story 4.4: Enforcement Strict du Routage Direct (Single-Hop)
*(inchangée)*

---

### ✂️ Epic 5: Coordination Autonome (Le Super-Pair Dynamique)
Statut scope : **SIMPLIFIÉ — Stories 5.1 et 5.2 uniquement**
Estimation : ~3 jours binôme

#### ✅ Story 5.1: Ré-élection avec Formule Mathématique Bully (Hystérésis)
*(inchangée)*

#### ✅ Story 5.2: Abdication Forcée et File d'attente I/O
*(inchangée)*

#### ❌ Story 5.3: Résolution de Split-Brain (Consensus de Fusion)
**Statut : RETIRÉ du scope PFE**
**Justification :** La résolution de Split-Brain implique un cas de concurrence réseau difficile à reproduire et à tester de manière fiable avec 2 téléphones physiques. Le risque de passer une semaine sur un edge-case non démontrable en soutenance est trop élevé. Cette story est **documentée dans le rapport** comme limite connue et amélioration architecturale prévue (référence : algorithme Bully étendu + vecteurs d'horloge Lamport).

---

### ✂️ Epic 6: Résilience & Cicatrisation (Ne Jamais Perdre de Données)
Statut scope : **SIMPLIFIÉ — Stories 6.1, 6.2, 6.3 uniquement**
Estimation : ~4 jours binôme

#### ✅ Story 6.1: Détection de Churn et Auto-Réparation Isolée
*(inchangée — le Mock ITrustScoreProvider suffit)*

#### ✅ Story 6.2: Évacuation Proactive (OS Intents Dynamiques)
*(inchangée)*

#### ✅ Story 6.3: Circuit-Breaker SRE d'Avalanche
*(inchangée — simple à implémenter, très impactant en soutenance)*

#### ❌ Story 6.4: Test d'Intégration Churn SLA via Réseau Virtuel Mocké
**Statut : RETIRÉ du scope PFE**
**Justification :** Construire un MockNetworkLayer complet pour 20 instances virtuelles représente ~1 semaine de travail pour un test automatisé. Ce temps est mieux investi dans la stabilité de la démo réelle. La preuve du NFR1 sera démontrée par un scénario manuel documenté en vidéo (2 téléphones, kill brutal d'un pair, reconstruction visible dans les logs + UI).

#### ❌ Story 6.5: Triage d'Urgence et Rejet Capacitaire (Load Shedding)
**Statut : RETIRÉ du scope PFE**
**Justification :** Story défensive complexe à déclencher de manière reproductible en démo. Documentée dans le rapport comme mécanisme de production prévu.

---

### ✂️ Epic 7: Confiance & Équité P2P (Récompenser les Bons Samaritains)
Statut scope : **FORTEMENT SIMPLIFIÉ — Story 7.1 simplifiée uniquement**
Estimation : ~2 jours binôme

#### ✂️ Story 7.1: Jeton de Karma Décentralisé
**Simplification PFE :** Le mécanisme de certification collégiale par l'anneau DHT est remplacé par une validation directe entre pairs (émetteur → receveur). La structure cryptographique du jeton (Timestamp + Nonce + Signature) est conservée intégralement.

**Justification :** La certification collégiale nécessite un quorum DHT fonctionnel sous charge, difficile à tester de façon déterministe. Le concept Anti-Replay (hash Timestamp|Nonce) est l'élément académiquement intéressant — il reste complet.

**Acceptance Criteria (révisés) :**
**Given** le transfert réussi d'un fragment vers un pair,
**When** la récompense Karma est émise,
**Then** le payload contient `hash(Timestamp | NonceUnique | PubKey)` signé avec la clé privée de l'émetteur.
**And** le receveur invalide les Nonces dupliqués (protection Anti-Replay locale).
**And** le score Karma est persisté localement dans Room et décroît dans le temps (TTL Karma).

#### ❌ Story 7.2: Intelligence Artificielle On-Device (TensorFlow Lite)
**Statut : RETIRÉ — remplacé par StaticMockTrustScore**
**Justification :** Entraîner, valider et embarquer un modèle TFLite pertinent nécessite des données historiques d'utilisation réelles inexistantes à ce stade. L'interface `ITrustScoreProvider` est conservée avec l'injection Hilt du `StaticMockTrustScore` (déjà prévu dans l'architecture depuis l'Epic 3). Cette abstraction est présentée en soutenance comme le point d'extension IA, ce qui est architecturalement correct et honnête.

#### ❌ Story 7.3: Preuve de Stockage (Proof of Retrievability - PoR)
**Statut : RETIRÉ du scope PFE**
**Justification :** Le PoR implique une coordination à 3 nœuds (A → B, challengé par C) avec des contraintes de timing strictes (200ms). Impossible à tester de manière fiable sur réseau Wi-Fi Direct local. Documenté dans le rapport comme mécanisme de sécurité avancé de production.

---

## Récapitulatif du Scope Final

| Epic | Stories Incluses | Stories Retirées |
|---|---|---|
| Epic 0 | 0.1, 0.2, 0.3 | — |
| Epic 1 | 1.1, 1.2, 1.3, 1.4, 1.5 | — |
| Epic 2 | 2.1, 2.2, 2.3, 2.4, 2.5 | — |
| Epic 3 | 3.1, 3.2 (sans SIMD NEON) | — |
| Epic 4 | 4.1, 4.2, 4.3, 4.4 | — |
| Epic 5 | 5.1, 5.2 | 5.3 (Split-Brain) |
| Epic 6 | 6.1, 6.2, 6.3 | 6.4 (CI Churn Test), 6.5 (Load Shedding) |
| Epic 7 | 7.1 (simplifié) | 7.2 (TFLite → Mock), 7.3 (PoR) |

**Total stories implémentées : 22 sur 27**
**Features retirées documentées dans le rapport : 5**

---

## Note pour le Rapport de Soutenance

Chaque feature retirée doit apparaître dans le rapport dans une section **"Limites connues et perspectives d'évolution"**. Ce n'est pas un aveu d'échec — c'est de la maturité d'ingénierie. Un jury respecte un étudiant qui sait borner son système et identifier ses points d'extension, bien plus qu'un projet qui prétend tout faire et ne démontre rien.

Les points suivants constituent des arguments forts pour cette section :
- SIMD NEON → benchmark de perf théorique calculable (formule FLOPS ARM)
- TFLite → dataset de features déjà défini (`[Uptime, Battery, AvgPing]`), prêt à l'entraînement
- PoR → protocole de challenge publié (Ateniese et al., 2007) citable
- Split-Brain → résolution via Lamport Clocks ou RAFT citable
