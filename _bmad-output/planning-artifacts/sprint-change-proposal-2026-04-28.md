# Sprint Change Proposal — 2026-04-28

**Auteur :** Yasmine (PO/Dev) — Workflow `bmad-correct-course`
**Mode :** Incrémental
**Statut :** ✅ Implémenté (édits déjà appliqués)

> **Note V5.0 (mise à jour finale) :** Le scope réel de ce sprint change combine **DEUX changements significatifs** :
> 1. Suppression complète du système Karma/Weight (cf. §1A)
> 2. **Pivot architectural V4.0 → V5.0 : Suppression totale de Firebase + consolidation Signaling/Transport sur Serveurs Relais HA** (cf. §1B)

---

## 1A. Issue Summary — Karma

**Déclencheur :** Décision de l'auteure de **retirer complètement le système Karma / Weight / Anti-Clandestin** du scope d'implémentation MobiCloud V5.0, en cours de sprint, alors que les artefacts (PRD, architecture, epics, UX, description technique) référençaient encore ce module comme central.

**Contexte de découverte :** Identifié lors du cadrage du workflow `correct-course`. La revue d'inventaire a révélé un désalignement profond — Karma était inscrit dans 5 documents vivants (PRD §1.2/§2.1/§2.3/UJ-04/FR-07, architecture Module 9 + arborescence + coverage map, epics FR-07.1/Story 5.5, UX 8 sections, description technique Module 9 entier).

**Pourquoi maintenant :** Deadline 3 juin 2026 imminente. Décision assumée que la contribution thèse tient sur **deux piliers** : mobile-native + topologie super-peer/cluster. Karma + PoR sortent en perspective rapport.

---

## 1B. Issue Summary — Pivot V4 → V5 Zero-Firebase

**Déclencheur :** Décision de l'auteure d'aligner les artefacts vivants sur l'architecture V5.0 cible : **suppression complète de Firebase** au profit d'un cluster de **Serveurs Relais HA WebSocket Node.js** (min 2 instances) qui assument simultanément :
- (1) **Signaling** : annuaire RAM des Super-Pairs avec messages `REGISTER_PEER` + `GET_PEERS`
- (2) **Transport (fallback NAT)** : Store-and-Forward 60s pour blocs binaires chiffrés (`UPLOAD` + `FORWARD`)

**Pourquoi maintenant :**
- L'architecture cible était déjà V5.0 (visible dans le contexte Story 8.1 standalone) mais epics.md/architecture.md sur disque restaient en V4.0 Firebase → désalignement majeur
- Décentralisation = principe directeur thèse ("le moins centralisé possible") — Firebase = service tiers Google = SPOF + risque de défense thèse faible
- Permet au narratif thèse de tenir face au jury : "mobile-native + super-peer/cluster sans dépendance commerciale"

**Impact V4→V5 :**
- FR-01.2 reformulé : "Serveurs Relais HA" au lieu de "Tracker STUN Firebase"
- FR-08.1 ajouté en P0 : "Relais HA Zero-Knowledge fallback"
- Stories 2.1, 2.2, 2.3, 3.2 d'epics.md réécrites pour pointer vers HA WebSocket
- Epic 8 entier ajouté à epics.md (Stories 8.1, 8.2, 8.3 en V5.0)
- architecture.md : revisionReason mise à V5.0, Cross-Cutting Concerns réécrits, §3 Tracker Firebase → §3 Serveurs Relais HA, arborescence projet mise à jour (relay-server/, RelayWebSocketClient, BlockSenderWithRelay), boundary section devient "Server Boundary Zero-Firebase", FR Coverage Map V5.0, libs.versions.toml passe de firebase-* → okhttp

---

## 2. Impact Analysis

### Epic Impact
- ❌ Aucun Epic Karma dédié n'existait dans `epics.md` (seul FR-07.1 vivait, mappé sur un Epic 8 absent du fichier réel)
- Epic 5 Story 5.5 a une ripple : "gagner du Weight" + "UpdateWeightScoreUseCase (Epic 8)" → nettoyé
- Epic 1 Story 1.5 a une ripple : "pénalisera votre Weight" → reformulé

### Story Impact
- Aucune story Karma à supprimer (l'Epic 9 du contexte initial n'était pas dans le fichier réel)
- 2 stories ripple corrigées (1.5 et 5.5)

### Artifact Conflicts (résolus)
| Document | Sections impactées | Statut |
|---|---|---|
| `prd.md` | Changelog, §1.2, §2.1 vision, UJ-04, §2.3 scope, FR-07 entier | ✅ Édité |
| `architecture.md` | §Cross-Cutting Concerns L24, §2 Réductions, Module 9, Models, m09_karma/, ModalBottomSheet, FR coverage, Key Strengths | ✅ Édité |
| `epics.md` | FR-07.1, FR Coverage Map (FR-07 + UX-DR7 remap), Story 1.5, Story 5.5 | ✅ Édité |
| `ux-design-specification.md` | §Design Opportunities, Critical Success Moments, Experience Principles, Emotional Goals, Micro-Emotions, Design Implications, Visual Patterns, Onglet 2 | ✅ Édité |
| `description_technique_formelle.md` | Pillar table, intro "dix modules", §2.1 justification, Module 9 entier (remplacé par note V4.0), §4.1 flux dépôt | ✅ Édité |

### Snapshots historiques **non touchés** (par design)
- `implementation-readiness-report-2026-03-26.md` — rapport daté
- `implementation-readiness-report-2026-04-11.md` — rapport daté
- `prd-validation-report.md` — rapport de validation daté
- `sprint-change-proposal-2026-04-13.md` — change proposal historique
- `ux-design-directions.html` — faux positifs `font-weight` CSS

### Technical Impact
- ❌ Aucun code à supprimer (le module n'était pas implémenté)
- ❌ Aucune table Room, aucun UseCase, aucune classe Karma à toucher
- ✅ Aucune migration de données nécessaire

---

## 3. Recommended Approach

**Direct Adjustment** — pas de rollback, pas de replan majeur.

**Rationale :**
- Le module Karma n'était pas codé (juste spec'é)
- La suppression touche uniquement la documentation
- Le scope restant (Epics 1-7) reste cohérent et implémentable

**Effort :** ~30 min de revue documentaire (déjà appliqué)
**Risque :** Faible — les artefacts historiques (snapshots) conservent la traçabilité de la décision pour la soutenance
**Timeline :** Aucun impact sur la deadline 3 juin

---

## 4. Detailed Change Proposals

### prd.md (6 éditions)
- L19 changelog : "DHT, CRDT, Bully, Karma" → "DHT, CRDT, Bully" (Karma retiré)
- L41 §1.2 : "(Algorithme Bully & Karma)" + équité Karma → "(Algorithme Bully)" + migration géo seulement
- L49 §2.1 vision : "(Karma)" → "multi-sources"
- UJ-04 : "Téléchargement Distribué Concurrent et Karma" → "Téléchargement Distribué Concurrent" (consommation/gain Karma supprimés)
- L75 §2.3 scope : "Karma Anti-clandestins" retiré de la liste algos Master
- FR-07 entier supprimé

### architecture.md (8 éditions)
- L24 §Functional Requirements : retrait mention "système Karma (équité Anti-Clandestins)"
- L67 §2 : "Validation du Karma" → ajout ligne explicite **"Système Karma/Weight retiré V4.0"** avec justification thèse
- Module 9 (Anti-Clandestin/Karma) entier supprimé
- Models L292 : `KarmaTransaction` retiré
- Arborescence usecase L307 : `m09_karma/` supprimé
- Explorer L327 : "ModalBottomSheet Karma" → "ModalBottomSheet"
- FR Coverage Map L392 : ligne FR-07.1 supprimée
- Key Strengths L439 : Karma retiré de la liste algos JVM-testables

### epics.md (6 éditions)
- FR Inventory : FR-07.1 supprimée
- FR Coverage Map : ligne FR-07.1/Epic 8 supprimée + UX-DR7 remappé Epic 8 → Epic 6 (Story 6.4 utilise déjà ModalBottomSheet)
- Story 1.5 AC : "pénalisera votre Weight" → "supprimera des blocs hébergés du réseau"
- Story 5.5 narrative : "et gagner du Weight en retour" supprimé
- Story 5.5 AC : 2 lignes Weight (`UpdateWeightScoreUseCase`, "pas de Weight gagné") supprimées

### ux-design-specification.md (8 éditions)
- §Design Opportunities : "Gamification Karma" supprimé, "Karma" retiré du tableau de bord
- Critical Success Moments : "Hausse du Karma" supprimé
- Experience Principles : "équité" Karma retiré (juste Zero-Trust)
- Primary Emotional Goals : "valorisé par Karma" → "membre utile"
- Micro-Emotions : "boucle gameplay Karma" → "rendre visible la contribution"
- Design Implications : "Design de Fierté (Karma)" → "Design de Fierté" (fiabilité nœud)
- Visual Patterns : "Karma, Batterie" → "Score Fiabilité, Batterie"
- Onglet 2 : Bloc "Karma & Réciprocité" entier supprimé

### description_technique_formelle.md (5 éditions)
- §1 Pillar table : ligne "Gouvernance Économique (Karma)" supprimée
- §1 intro : "dix modules" → "neuf modules"
- §2.1 justification : "contrepartie en termes de Karma" → "contrepartie" (justif énergétique pure)
- Module 9 entier (Auto-Régulation/Karma) → remplacé par note de traçabilité V4.0 + ex-Module 10 Élection devient Module 9
- §4.1 Flux dépôt : ligne "[M9] Vérification du Karma → AUTORISÉ" supprimée

---

## 5. Implementation Handoff

**Scope classification : Minor (documentaire uniquement)**

**Recipients :**
- ✅ **Developer (Amelia / dev-story)** — Aucun code à toucher. Les stories restantes (Epics 1-7) restent valides.
- ✅ **Tech Writer (Paige)** — Notes de traçabilité ajoutées dans architecture.md L67 et description_technique_formelle.md L289 pour la défense de soutenance.

**Success criteria :**
- [x] Aucune occurrence active de "Karma|Weight|FR-07.1|UpdateKarma|m09_karma" dans les artefacts vivants (vérifié via grep final)
- [x] Snapshots historiques préservés (traçabilité soutenance)
- [x] Mémoire LLM mise à jour (`project_karma_removed.md` + `project_mobicloud.md` revisé)

**Defense narrative pour la soutenance :**
> *"Le système d'incentives (Karma) et la preuve de stockage (PoR) ont été spec'és dans le rapport mais retirés du scope d'implémentation pour focaliser sur les deux contributions principales : mobile-native P2P storage et topologie super-peer/cluster avec promotabilité. Ces mécanismes sont documentés comme perspective rapport."*

---

## 6. Open Questions / Watchouts

1. **Soutenance — anticipation question jury :** Si le jury demande "pourquoi pas d'incentives ?", la narrative défense ci-dessus tient. À répéter avec Yasmine en simulation Q&A.
2. **Réseau social du projet (UX) :** Le bloc "Fierté Communautaire / réciprocité" était un levier émotionnel UX fort. Reformulé en "membre utile du réseau d'entraide" → impact UX résiduel à valider quand on retouchera l'UI.
3. **Si Yasmine veut ré-ouvrir le sujet :** la mémoire `project_karma_removed.md` flag explicitement de demander "pourquoi" avant d'agir, pour éviter un re-pivot coûteux.
