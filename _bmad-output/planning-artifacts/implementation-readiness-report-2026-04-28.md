---
stepsCompleted: ['step-01-document-discovery', 'step-02-prd-analysis', 'step-03-epic-coverage-validation', 'step-04-ux-alignment', 'step-05-epic-quality-review', 'step-06-final-assessment']
filesIncluded:
  prd: 'prd.md'
  architecture: ['architecture.md', 'architecture-connectivity-and-clustering.md']
  epics: 'epics.md'
  ux: 'ux-design-specification.md'
  context: 'sprint-change-proposal-2026-04-28.md'
  derived_view: 'description_technique_formelle.md'
---

# Implementation Readiness Assessment Report

**Date:** 2026-04-28
**Project:** PFE (MobiCloud V5.0 — Pivot Zero-Firebase)

## Step 1 — Document Inventory

**Sources autoritaires sélectionnées :**
- **PRD** : `prd.md`
- **Architecture** : `architecture.md` + `architecture-connectivity-and-clustering.md` (annexe topologie/clustering)
- **Epics** : `epics.md` (+ `simulator-epic-handoff-brief.md` en contexte)
- **UX** : `ux-design-specification.md`
- **Contexte pivot** : `sprint-change-proposal-2026-04-28.md`
- **Vue dérivée (non autoritaire)** : `description_technique_formelle.md`

**Aucun duplicate critique** (whole vs sharded). Tous les documents principaux datés du 28 avril 2026, post-pivot V5 Zero-Firebase.

## Step 2 — PRD Analysis

### Functional Requirements Extracted

- **FR-01** : Découverte Hybride et Signalisation UDP/TCP
  - **FR-01.1 (P0)** : Multicast UDP pour découverte intra-sous-réseau, sans serveur.
  - **FR-01.2 (P0)** : Cluster Serveurs Relais HA WebSocket (≥2 instances Node.js, Zero-Knowledge) — annuaire signaling (REGISTER_PEER / GET_PEERS) + fallback Store-and-Forward 60s. Zéro Firebase/STUN/DDNS.
  - **FR-01.3 (P0)** : Tous les transferts (catalogue + fichiers) en direct P2P de bout en bout (Zero-Trust).
- **FR-02** : Élection Bully et Scoring de Fiabilité
  - **FR-02.1 (P0)** : Mesure de stabilité (Batterie, Uptime, IP locale) ; élection locale via Algorithme Bully strict.
  - **FR-02.2 (P0)** : Gagnant = Super-Pair, s'enregistre auprès des Serveurs Relais HA (REGISTER_PEER signé EC P-256).
- **FR-03** : Erasure Coding P2P & Chiffrement C++
  - **FR-03.1 (P0)** : Erasure Coding vectoriel C++ NDK, découpage K+N sans réplication redondante.
  - **FR-03.2 (P0)** : Chiffrement asymétrique de tous les fragments (Zero-Trust / Zero-Knowledge).
- **FR-04** : Restauration de la DHT partagée (CRDT / Gossip)
  - **FR-04.1 (P0)** : Remplacement SQLite par index global DHT entre pairs qualifiés du cluster.
  - **FR-04.2 (P0)** : Synchronisation arbre de Merkle / CRDT via algorithme épidémique (Gossip).
- **FR-06** : Migration Géographique Inter-Réseaux
  - **FR-06.1 (P1)** : Transfert proactif des blocs hébergés vers le cluster local lors de la détection de sortie imminente.

**Total FRs : 5 groupes (10 sous-exigences).**

### Non-Functional Requirements Extracted

- **NFR-01 (Convergence CRDT)** : Gossip intra-cluster ⇒ convergence éventuelle DHT ≤ 3 s lors d'ajout d'un bloc.
- **NFR-02 (Latence de Migration)** : Déclenchement + orchestration de récupération avant interruption réseau < 5 s.
- **NFR-03 (Batterie/CPU)** : Overhead CRDT/Gossip arrière-plan ≤ 5% CPU. NDK C++ compense complexité Erasure.

**Total NFRs : 3.**

### Additional Requirements (Contraintes & Hypothèses)

- **Zero-Firebase / Zero-STUN / Zero-DDNS** : aucune dépendance services tiers.
- **Min 2 instances Node.js** pour HA des Serveurs Relais.
- **Cryptographie EC P-256** pour signature REGISTER_PEER.
- **Topologie cluster + super-peer** : non-négociable (cf. mémoire projet).
- **Karma / Réciprocité** : retiré du scope V4/V5 (perspective rapport uniquement).

### PRD Completeness Assessment — Findings préliminaires

🚨 **Issue 1 — Numérotation FR cassée** : Le PRD saute de FR-04 à FR-06. **FR-05 absent**. Soit renuméroter, soit retrouver un FR-05 perdu (probablement le Téléchargement Distribué Concurrent UJ-04, qui n'a aucun FR explicite).

🚨 **Issue 2 — UJ sans FR** : `UJ-04 (Téléchargement Distribué Concurrent multi-sources, K blocs)` est cité dans la vision mais n'a **aucune FR dédiée**. C'est pourtant un parcours utilisateur central.

⚠️ **Issue 3 — Incohérence métadonnées** : frontmatter dit "Version 4.0" et `lastEdited: 2026-04-13`, mais le contenu décrit l'**ARCHI V5** (Serveurs Relais HA, Zero-Firebase). Le pivot V5 du 28 avril n'a pas été reflété dans le frontmatter ni le titre/version. À corriger pour cohérence.

⚠️ **Issue 4 — NFRs minces** : seulement 3 NFRs. Manquent classiquement : sécurité (chiffrement bout-en-bout, gestion des clés), disponibilité (uptime cluster relais), résilience (tolérance pannes K-N), scalabilité (nb pairs/cluster), confidentialité (Zero-Knowledge mesurable comment ?), observabilité (logs/métriques pour la défense PFE).

⚠️ **Issue 5 — Critères d'acceptation absents** : aucun FR n'a de critère d'acceptation testable (Given/When/Then ou seuils chiffrés). Risque de litige au moment de l'implémentation et de la défense.

## Step 3 — Epic Coverage Validation

### Coverage Matrix (FR + NFR + UX-DR)

| ID | Source PRD | Epic Coverage | Story(ies) | Statut |
|----|-----------|---------------|------------|--------|
| FR-01.1 (Multicast UDP) | ✅ PRD | ❌ **NON couvert** | — | 🚨 **MISSING** |
| FR-01.2 (Relais HA Signaling) | ✅ PRD | Epic 2 + Epic 8 | 2.1 / 8.1 / 8.2 | ✓ Covered |
| FR-01.3 (P2P direct Zero-Trust) | ✅ PRD | Epic 1 + Epic 5 | 5.3 / 5.5 | ✓ Covered |
| FR-02.1 (Score Fiabilité) | ✅ PRD | Epic 2 | 2.2 | ✓ Covered |
| FR-02.2 (Bully + REGISTER_PEER) | ✅ PRD | Epic 3 | 3.1 / 3.2 | ✓ Covered |
| FR-03.1 (Erasure C++ K+N) | ✅ PRD | Epic 5 | 5.1 | ✓ Covered |
| FR-03.2 (Chiffrement asym fragments) | ✅ PRD | Epic 5 | 5.2 | ✓ Covered |
| FR-04.1 (Anneau DHT) | ✅ PRD | Epic 4 + Epic 6 | 4.1 / 6.1 | ✓ Covered |
| FR-04.2 (Gossip CRDT) | ✅ PRD | Epic 4 | 4.2 / 4.3 | ✓ Covered |
| FR-05 | ❌ **Absent PRD** | — | — | 🚨 **NUMÉROTATION CASSÉE** |
| FR-06.1 (Migration géo) | ✅ PRD | Epic 7 | 7.1 / 7.2 | ✓ Covered |
| FR-08.1 (Relais HA Fallback Store-and-Forward) | ❌ **Absent PRD** | Epic 8 | 8.1 / 8.2 / 8.3 | 🚨 **DANS EPICS, PAS DANS PRD** |
| NFR-01 (Convergence ≤ 3s) | ✅ PRD | Epic 4 | 4.2 | ✓ Covered |
| NFR-02 (Migration < 5s) | ✅ PRD | Epic 7 | 7.2 | ✓ Covered |
| NFR-03 (CPU ≤ 5%) | ✅ PRD | Epic 5 + Global | 5.1 | ✓ Covered |
| UX-DR1 (ReliabilityGauge) | ✅ UX | Epic 2 | 2.3 | ✓ Covered |
| UX-DR2 (KpiDiagnosticCard) | ✅ UX | Epic 2 | 2.3 | ✓ Covered |
| UX-DR3 (RadarLogConsole) | ✅ UX | Epic 2 | 2.3 | ✓ Covered |
| UX-DR4 (ErasureProgressIndicator) | ✅ UX | Epic 5 | 5.4 | ✓ Covered |
| UX-DR5 (Dark OLED) | ✅ UX | Epic 1 | 1.2 | ✓ Covered |
| UX-DR6 (Bottom Nav) | ✅ UX | Epic 1 | 1.2 | ✓ Covered |
| UX-DR7 (ModalBottomSheet) | ✅ UX | Epic 6 | 6.4 | ✓ Covered |
| UX-DR8 (Permissions silencieuses) | ✅ UX | Epic 1 | 1.4 | ✓ Covered |

### Missing Requirements & Anomalies

🚨 **Critical 1 — FR-01.1 (Multicast UDP) totalement absent des epics**
- **Texte PRD :** "Le système local utilise le Multicast UDP pour la découverte au sein d'un même sous-réseau sans serveur." (P0)
- **Impact :** C'est le mécanisme de découverte intra-LAN sans relais. Sa disparition signifie que **chaque cluster local dépend obligatoirement du Serveur Relais HA même en LAN partagé** — incohérent avec le principe "le moins centralisé possible" inscrit comme principe directeur thèse. À défendre en jury, c'est un trou.
- **Recommandation :** soit ajouter une story dans Epic 2 (Découverte locale Multicast UDP, fallback avant signalisation HA), soit retirer FR-01.1 du PRD avec justification écrite (ex: "abandonné car incompatible avec hotspots Wi-Fi modernes qui filtrent UDP multicast").

🚨 **Critical 2 — FR-08.1 dans epics sans définition PRD**
- Le PRD documente `FR-01.2` comme cumulant "annuaire signaling + fallback Store-and-Forward 60s". Les epics scindent cela en `FR-01.2` (signaling) + `FR-08.1` (fallback transport).
- **Impact :** double source de vérité. Le PRD parle de "FR-01.2" qui inclut le fallback ; les epics introduisent un `FR-08.1` qui n'existe nulle part dans le PRD. Aucune traçabilité possible côté défense PFE.
- **Recommandation :** créer FR-08 dans le PRD (Section 4) avec le texte exact du fallback Store-and-Forward, ou bien restreindre FR-01.2 au signaling et déclarer explicitement FR-08 dans le PRD.

🚨 **Critical 3 — Numérotation FR-05 absente**
- Saut FR-04 → FR-06 dans le PRD. Pas de FR-05.
- **Recommandation :** soit renuméroter (FR-06 → FR-05), soit préciser ce qui occupait FR-05 (probablement le téléchargement concurrent K+2 — Epic 6, qui n'a aucune ancre FR dans le PRD aujourd'hui).

⚠️ **High 1 — UJ-04 (Téléchargement Concurrent K+2) couvert sans FR**
- L'Epic 6 implémente intégralement le téléchargement concurrent K+2 (4 stories), citant uniquement "FR-04.1 (recherche DHT)" + "Architecture: Pipeline streaming". Aucune FR PRD ne formalise le téléchargement multi-source.
- **Recommandation :** créer un FR-05 dédié : "Téléchargement Distribué Concurrent (K+2 multi-sources avec pipeline streaming)" — cela résout simultanément les Critical 3 et High 1.

⚠️ **High 2 — Epics excessivement riches en exigences "Architecture" non tracées**
- Beaucoup de comportements critiques (Circuit-Breaker churn 30%, Abdication 30 min, Bloom filters Gossip, Timeout ACK adaptatif, AES-256 GCM, JNI DirectByteBuffer batching) sont en **Additional Requirements** côté epics, mais **n'apparaissent comme contraintes formelles ni dans le PRD, ni comme NFR**.
- **Impact :** ce sont des décisions architecturales qui pèsent sur l'évaluation PFE — elles méritent au minimum un NFR dédié (sécurité, résilience, observabilité). Risque jury : "où est la NFR qui cadre votre seuil de 30% churn ? Comment l'avez-vous validée ?"
- **Recommandation :** promouvoir 4–5 contraintes-clés en NFRs : NFR-04 (Résilience churn ≤ 30%), NFR-05 (Sécurité — AES-256 GCM Zero-Knowledge), NFR-06 (Mandat Super-Pair ≤ 30 min), NFR-07 (Anti-Sybil — Keystore EC P-256).

⚠️ **High 3 — Story 1.5 (Quota stockage) sans ancre PRD/UX**
- Story 1.5 (slider quota 0.5–80% espace libre) implémente une fonctionnalité utilisateur visible. Aucun FR ni UX-DR ne la couvre.
- **Recommandation :** soit créer FR-09 "Configuration utilisateur du quota de stockage alloué", soit ajouter UX-DR9 "Settings — Slider Allocation".

### Coverage Statistics

- **Total FRs PRD :** 10 (FR-01.1 à FR-06.1, FR-05 manquant)
- **FRs couverts par epics :** 9/10 (FR-01.1 manque)
- **FR couverts mais absents PRD :** 1 (FR-08.1)
- **Coverage PRD → Epics : 90%** (1 trou : FR-01.1)
- **Coverage Epics → PRD : 90%** (1 fantôme : FR-08.1)
- **NFRs : 100%** (3/3)
- **UX-DRs : 100%** (8/8)
- **Stories totales : 27** (5 Epic1 + 3 Epic2 + 4 Epic3 + 4 Epic4 + 5 Epic5 + 4 Epic6 + 3 Epic7 + 3 Epic8 — note: Story 1.5 placée hors séquence 1.4)

## Step 4 — UX Alignment Assessment

### UX Document Status

**Found** : `ux-design-specification.md` (32 KB, daté du **2026-03-27**, soit **antérieur au pivot V5 du 28 avril**).

### Alignment Issues

🚨 **Critical 1 — UX-Spec figée à une époque V2/V3 (pre-Karma-removal, pre-V5)**

Termes encore présents dans `ux-design-specification.md`, **incompatibles avec PRD/Epics V5** :

| Terme UX | État réel V5 | Lignes |
|----------|--------------|--------|
| **« Score IA de Fiabilité »** (5 occurrences) | Le scoring V5 est purement algorithmique (Batterie 40% + Uptime 40% + Network 20%) — **aucune IA**. | L35, L64, L92, L96, L192 |
| **« BLE / Bluetooth Low Energy »** (3 occurrences) | Découverte V5 = Multicast UDP (LAN) + Relais HA WebSocket (inter-réseaux). **Aucun BLE.** | L48, L125, L173, L200, L354 |
| **« Wi-Fi Direct »** (3 occurrences) | Transport V5 = TCP direct + WSS Relais HA. **Aucun Wi-Fi Direct.** | L48, L116, L173, L201 |
| **« MulticastLock + Foreground Service »** | OK mais accolé à BLE/Wi-Fi Direct ⇒ contexte trompeur | L48 |
| **Permissions « Bluetooth/BLE, Localisation/Wi-Fi Direct »** | Permissions V5 réelles = `ACCESS_WIFI_STATE, INTERNET, ACCESS_NETWORK_STATE` (cf. Story 1.4) | L173 |

✅ **Composants UX-DR1..4 OK** : `ReliabilityGauge`, `KpiDiagnosticCard`, `RadarLogConsole`, `ErasureProgressIndicator` sont tous spécifiés (lignes 348–380) et alignés avec les epics.

✅ **Dark OLED + Bottom Nav 3 onglets + ModalBottomSheet** : présents et alignés (UX-DR5/6/7).

⚠️ **High 2 — Aucune mention des Serveurs Relais HA dans la UX-Spec**

L'utilisateur final voit le "Cloud Relay" (badge dans Dashboard, cf. Story 8.3 AC), mais la UX-Spec ne décrit **aucun élément visuel pour cet état** : pas d'icône, pas de copie ("Connecté via Relais", "P2P direct"), pas de fallback message. Trou potentiel pour la défense PFE qui valorise l'aspect "transparence du fallback".

⚠️ **High 3 — UX-Spec ne mentionne pas Story 1.5 (Quota Stockage)**

Le slider de quota (0.5 GB → 80% espace libre) est implémenté en Story 1.5 mais aucune spécification visuelle dans `ux-design-specification.md`. C'est pourtant un élément Settings utilisateur visible.

### UX ↔ PRD Alignment

- **NFR-03 (5% CPU)** : explicitement référencé dans la UX-Spec (sérénité batterie) ✓
- **Zero-Trust** : très bien tangibilisé (métaphores cadenas/fragmentation) ✓
- **Parcours UJ-01..UJ-05 du PRD** : la UX-Spec ne les cite pas explicitement par leur identifiant. Pas de table de traçabilité Parcours → Écran.

### UX ↔ Architecture Alignment

- L'architecture cible **Compose + Hilt + Room + Coroutines/Flow** ; UX-Spec confirme "UI réactive via StateFlow, observation passive" ✓
- L'architecture impose le **Foreground Service** ; UX-Spec confirme "Background-first headless" ✓
- **Mais** UX-Spec décrit un Foreground Service "BLE + Wi-Fi Direct + MulticastLock" — incompatible avec l'architecture V5 qui n'utilise ni BLE ni Wi-Fi Direct.

### Warnings

- 🚨 **`ux-design-specification.md` n'a pas été mis à jour lors du pivot V5** (dernière modif fonctionnelle 27 mars). Il **précède même le retrait du Karma** et la migration Zero-Firebase.
- 🚨 **Risque de défense jury** : si l'examinateur ouvre la UX-Spec et lit "Score IA / BLE / Wi-Fi Direct", il y a contradiction directe avec PRD/Architecture/Code → cohérence documentaire compromise.
- ⚠️ **`ux-design-directions.html` (11 avril, pré-pivot)** : à archiver ou supprimer pour éviter la confusion.

## Step 5 — Epic Quality Review

### 🔴 Critical Violations

**C1 — Forward dependency Epic 2 → Epic 8 (transport layer in last epic)**

L'Epic 8 (Serveurs Relais HA) introduit `RelayWebSocketClient.kt` (Story 8.2) qui est la **fondation transport** sur laquelle reposent :
- Story 2.1 (Signaling) qui ouvre déjà une WSS persistante et signe avec EC P-256
- Story 3.2 (REGISTER_PEER Super-Pair) qui réutilise `SignalingRepository`
- Story 7.2 (orchestration migration) qui dépend du fallback Relais

L'ordre actuel des epics (1→2→3→…→8) **viole la règle "Epic N ne dépend pas d'Epic N+1"** : Epic 2 a besoin du WSS client défini en Epic 8.

**Impact :** un développeur qui implémente Epic 2 sans avoir fait Epic 8 ne peut pas livrer Story 2.1.

**Recommandation :** déplacer Epic 8 en **Epic 2** (renommer "Couche Transport — Serveurs Relais HA WebSocket"), et décaler les autres. Ou alors : extraire `RelayWebSocketClient` + serveur Node.js comme **Epic 0 / Foundation Transport**.

**C2 — Doublon/conflit Story 2.1 ↔ Story 8.2 sur l'implémentation WSS**

Story 2.1 spécifie `SignalingRepositoryImpl.kt` qui ouvre une "connexion WSS persistante via OkHttp" et signe en EC P-256.
Story 8.2 spécifie `RelayWebSocketClient.kt` qui ouvre une "connexion WSS persistante via OkHttp callbackFlow" et signe en EC P-256.

C'est **la même connexion implémentée deux fois** dans deux stories différentes. Sans clarification, un dev implémentera deux clients WSS concurrents.

**Recommandation :** Story 8.2 = client WSS bas-niveau réutilisable ; Story 2.1 = `SignalingRepositoryImpl` qui **utilise** `RelayWebSocketClient` (façade). Réécrire les ACs de 2.1 pour expliciter cette dépendance vers 8.2.

**C3 — Référence morte `ExecuteMigrationPlanUseCase` (Story 8.3)**

Story 8.3 cite `ExecuteMigrationPlanUseCase` comme dépendance. Aucune story ne définit ce use case. Epic 7 Story 7.2 définit en revanche `OrchestrateBlockMigrationUseCase`. Probable renommage non répercuté.

**Recommandation :** harmoniser sur un seul nom (`OrchestrateBlockMigrationUseCase`) et corriger Story 8.3.

### 🟠 Major Issues

**M1 — Story 1.5 placée hors séquence (entre 1.3 et 1.4)**

Le document liste l'ordre 1.1 → 1.2 → 1.3 → **1.5** → 1.4. C'est purement cosmétique mais signale un ajout tardif (Quota slider) inséré sans repositionnement. Rien ne casse à l'implémentation, mais c'est un signal de désorganisation.

**Recommandation :** soit renuméroter 1.5 en 1.6 (après 1.4), soit déplacer dans le document.

**M2 — Épics 1, 4, 5, 8 ont des titres/objectifs partiellement techniques**

- Epic 1 "Fondation & Identité…" : "Fondation" est un milestone technique. Acceptable car contient l'identité utilisateur.
- Epic 4 "Catalogue DHT & Synchronisation CRDT/Gossip" : "DHT/CRDT/Gossip" est jargonnesque (mais justifié PFE Big Data — défense).
- Epic 5 "Stockage Distribué Zero-Trust — Erasure Coding & Chiffrement" : valeur user "stocker un fichier" claire ✓ malgré jargon.
- Epic 8 "Serveurs Relais HA WebSocket — Signaling & Transfert" : pure infrastructure, **aucune valeur user directe**. C'est de l'enabler, pas un epic vertical.

**Recommandation :** garder le contenu (justifié PFE) mais reformuler les objectifs pour mettre le user-outcome en premier. Ex Epic 8 : "L'utilisateur peut joindre des nœuds derrière NAT symétrique grâce à un canal de fallback transparent."

**M3 — `domain/usecase/m10_election/RunBullyElectionUseCase` — préfixe `m10_` incohérent avec autres stories**

Stories utilisent `m03_m04_gossip_heartbeat`, `m05_dht_catalog`, `m06_m07_repair_migration`, `m08_hosting`. Mais Story 3.1 référence `m10_election`. Numérotation modulaire incohérente — pas de m09 ?

**Recommandation :** documenter quelque part la table de mapping module → numéro (probablement dans `architecture.md`), ou aligner les préfixes.

**M4 — Stories sans critère mesurable pour les NFRs**

- NFR-03 (CPU ≤ 5%) : aucune story n'inclut un AC de type "When le service tourne 30 min, Then la consommation CPU mesurée via Profiler ≤ 5%". L'NFR est mappée à Epic 5 mais aucun AC de Story 5.x ne la teste.
- NFR-01 (Convergence ≤ 3s) : Story 4.2 cite "convergence atteinte en ≤ 3s" — bien ✓
- NFR-02 (Migration < 5s) : Story 7.2 cite "complétée en < 5s" — bien ✓

**Recommandation :** ajouter un AC explicite mesurable pour NFR-03 dans Story 5.1 ou Story 1.4.

### 🟡 Minor Concerns

**m1 — `architecture-connectivity-and-clustering.md` non listé dans `inputDocuments` du frontmatter epics**
Le frontmatter epics liste `architecture.md`, `prd.md`, `ux-design-specification.md`, `technical-serveur-relais-research.md`. L'annexe topologie de 25 KB n'est pas référencée → perte de traçabilité.

**m2 — `Epic 1 covered FRs` : mention "Architecture: Starter Template, Keystore Anti-Sybil, Foreground Service"**
Ces éléments sont citées comme "FRs covered" alors que ce sont des contraintes architecturales. Confusion taxinomique entre FR et contrainte architecturale.

**m3 — Aucune story de tests d'intégration / validation E2E**
27 stories, pas une seule "En tant que QA, je veux des tests E2E…". Risque pour la défense PFE qui valorise la rigueur de validation.

**m4 — Aucune story d'observabilité (logs structurés, métriques, traces)**
Le PFE Big Data demande naturellement de la métrologie (latence Gossip, taux de churn, débit Erasure). Aucune story ne planifie ces logs/dashboards.

### Best Practices Compliance Summary

| Critère | Statut |
|---------|--------|
| Epics délivrent valeur utilisateur | ⚠️ Mixte (Epic 8 = pure infra) |
| Epics indépendants (N ne dépend pas de N+1) | 🚨 **VIOLÉ** (Epic 2/3/7 dépendent d'Epic 8) |
| Stories correctement sizées | ✓ OK (1–3 jours chacune en moyenne) |
| Pas de forward dependencies stories | 🚨 **VIOLÉ** (2.1 ↔ 8.2) |
| Tables DB créées au juste-à-temps | ✓ OK |
| Critères d'acceptation Given/When/Then | ✓ Excellent (toutes stories formatées BDD) |
| Traçabilité FR → Story | ⚠️ 90% (FR-01.1 manque, FR-08.1 fantôme) |
| Starter template Story 1.1 | ✓ Présent et conforme |
| Stories de tests E2E | ❌ Absent |
| Stories d'observabilité | ❌ Absent |

---

## Summary and Recommendations

### Overall Readiness Status

🟡 **NEEDS WORK** — Implémentation conditionnellement bloquée.

Le projet **n'est pas dans un état "ready"** au sens strict, mais les fondations sont solides : les 27 stories sont bien rédigées (Given/When/Then partout), 90% des FRs sont tracés, le pivot V5 Zero-Firebase est cohérent dans PRD/Architecture/Epics. Les défauts sont **rattrapables en 1 à 2 sprints courts** (5–8 jours focalisés) avant de lancer l'implémentation.

**Verdict défense PFE :** en l'état actuel, un examinateur attentif peut pointer ≥ 3 contradictions documentaires (UX-Spec V2 vs PRD V5, FR-08.1 fantôme, Epic 8 hors séquence). Ces points doivent être réparés avant le rapport final.

### Critical Issues Requiring Immediate Action

**Bloquants pour l'implémentation (à régler avant Story 1.1) :**

1. 🚨 **C1 — Réordonner les Epics** : Epic 8 (Serveurs Relais HA) est la **fondation transport** de Epic 2/3/7. Le déplacer en position 2 (ou en Epic 0/Foundation Transport) avant que le développement séquentiel ne commence.
2. 🚨 **C2 — Clarifier la séparation Story 2.1 / Story 8.2** : Story 8.2 = client WSS bas-niveau (`RelayWebSocketClient.kt`) ; Story 2.1 = repository façade (`SignalingRepositoryImpl`) qui consomme 8.2. Réécrire les ACs de Story 2.1 pour expliciter la dépendance.
3. 🚨 **C3 — Renommer/corriger `ExecuteMigrationPlanUseCase`** dans Story 8.3 (utiliser `OrchestrateBlockMigrationUseCase` défini en Story 7.2).

**Bloquants traçabilité (à régler avant la défense) :**

4. 🚨 **Trou FR-01.1 (Multicast UDP)** : présent PRD, absent Epics. Décider : (a) ajouter une story dans Epic 2 "Découverte locale Multicast UDP", ou (b) retirer FR-01.1 du PRD avec justification écrite. Le choix (a) est cohérent avec le principe directeur "le moins centralisé possible".
5. 🚨 **FR-08.1 fantôme (Relais HA Fallback)** : créer FR-08 dans le PRD Section 4 avec le texte exact du fallback Store-and-Forward 60s, ou bien réintégrer dans FR-01.2.
6. 🚨 **FR-05 numérotation** : créer un FR-05 "Téléchargement Distribué Concurrent K+2 Multi-Sources Pipeline Streaming" qui ancre Epic 6 (résout simultanément le saut de numérotation et le trou de couverture pour UJ-04).
7. 🚨 **UX-Spec datée du 27 mars (pré-V5)** : refonte ciblée pour retirer "Score IA", "BLE", "Wi-Fi Direct", "permissions Bluetooth", et ajouter le Cloud Relay badge + slider de quota (Story 1.5). Estimation : 2 h de réécriture.

**Métadonnées (à régler avant la défense) :**

8. ⚠️ **Frontmatter PRD** : passer de "Version 4.0 / lastEdited 2026-04-13" à "Version 5.0 / lastEdited 2026-04-28" + reflet du pivot Zero-Firebase dans le titre.
9. ⚠️ **Frontmatter Epics** : ajouter `architecture-connectivity-and-clustering.md` dans `inputDocuments`.
10. ⚠️ **Renuméroter Story 1.5 → Story 1.6** (placée hors séquence).

**Pour la robustesse PFE (recommandé, pas bloquant) :**

11. 💡 **Promouvoir 4 contraintes architecturales en NFRs** : NFR-04 Résilience churn ≤ 30%, NFR-05 Sécurité AES-256 GCM Zero-Knowledge, NFR-06 Mandat Super-Pair ≤ 30 min, NFR-07 Anti-Sybil Keystore EC P-256.
12. 💡 **Ajouter un Epic 9 (Observabilité & Validation)** : 2–3 stories sur logs structurés, métriques (latence Gossip, taux churn, débit Erasure), tests E2E. Renforce considérablement la défense PFE.
13. 💡 **Ajouter AC mesurable NFR-03 (CPU ≤ 5%)** dans Story 5.1 ou 1.4.

### Recommended Next Steps

1. **Sprint Fix Documentation (5 jours)** :
   - Jour 1 : corriger PRD (FR-05, FR-08, frontmatter V5, NFR-04..07)
   - Jour 2 : réordonner Epics (Epic 8 → Epic 2-bis), corriger Story 2.1/8.2/8.3
   - Jour 3 : refonte UX-Spec (purge BLE/Wi-Fi Direct/IA, ajout Cloud Relay UI + Story 1.5)
   - Jour 4 : ajouter Epic 9 Observabilité + AC NFR-03 mesurable
   - Jour 5 : re-run `bmad-check-implementation-readiness` + `bmad-validate-prd`

2. **Décision rapide à prendre par Naoui** :
   - **Q1 :** garder ou retirer FR-01.1 Multicast UDP ? (impact thèse : décentralisation locale)
   - **Q2 :** Epic 9 Observabilité dans le scope V5 ou perspective rapport ?

3. **Une fois ces correctifs faits → lancer `bmad-create-story` Story 1.1** (Initialisation projet Starter Template).

### Final Note

Cette évaluation a identifié **3 issues critiques (C1–C3)**, **4 trous de traçabilité critiques**, **3 issues de métadonnées**, **4 issues majeures**, **4 mineures**, soit **~18 anomalies au total** réparties sur 5 catégories : Document Discovery, PRD, Coverage Epics, UX Alignment, Epic Quality.

**Bonne nouvelle :** aucune anomalie ne touche à la conception architecturale — toutes sont rattrapables sur les artefacts de planification (PRD, Epics, UX-Spec). L'architecture V5 Zero-Firebase et le découpage en 8 epics fonctionnels sont **structurellement sains**.

**Recommandation finale :** **NE PAS** lancer Story 1.1 avant d'avoir traité C1, C2, C3 et les trous FR-01.1 / FR-08.1 / FR-05. Le coût d'un sprint de fix-doc maintenant est ~5 jours ; le coût d'une refonte au milieu d'Epic 2 ou en défense PFE serait bien supérieur.

**Date du rapport :** 2026-04-28  
**Évaluateur :** Claude (Product Manager — bmad-check-implementation-readiness)
