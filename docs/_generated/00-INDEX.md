# Index Documentation MobiCloud — Scan Exhaustif
**Généré**: 13 mai 2026  
**Mode**: Exhaustive scan (tous fichiers .kt et .js lus)  
**Langue**: Français  
**Validité**: Snapshot production code à date

---

## Documentation Générée

Cette section contient les documents générés par scan exhaustif du code source. Ces documents reflètent **EXACTEMENT** ce qu'est implémenté, pas ce qui est aspirationnel.

### 📋 [01-ARCHITECTURE-IMPLEMENTATION.md](01-ARCHITECTURE-IMPLEMENTATION.md)

**Vue d'ensemble complète du système**

- Structure multi-parties (app Android + relay Node.js)
- Stack technologique réel (Kotlin, Jetpack Compose, Room, Retrofit, Node.js WebSocket)
- **11 Modules implémentés** (m01-m11) avec détails ligne par ligne :
  - m01: Auth & Discovery (EC P-256, anti-replay ±30s)
  - m03-04: Gossip & Heartbeat (FAN_OUT=2, 30s fixe)
  - m05: DHT Catalog (Consistent hash ring, CRDT LWW)
  - m06-07: Auto-Repair & Migration (orchestration Super-Pair)
  - m08: Stockage & Hosting (hébergement fragments)
  - m08-09: Erasure Coding (k=4, n=2 fixe, pas adaptatif)
  - m10: Élection Bully (comparaison SF simple)
  - **m11: Cluster & JOIN** (nouveau, pas documenté théorique)
- Protocole relai WebSocket (opcodes, framing, signalisation)
- Paramètres réels (HEARTBEAT_INTERVAL_MS=30s, SP_TIMEOUT_MS=90s, MAX_CLUSTER_SIZE=50)
- Architecture réseau (3 canaux : relai WS + TCP P2P + UDP multicast futur)
- Propriétés sécurité implémentées vs. promises

### 🔧 [02-RELAY-PROTOCOL-OPCODES.md](02-RELAY-PROTOCOL-OPCODES.md)

**Référence complète protocole relai Node.js**

- Framing binaire (1 byte type + 4 bytes length LE + payload)
- **18 opcodes** détaillés :
  - `0x01-0x02` AUTH/AUTH_OK (EC P-256 signature, fenêtre ±30s)
  - `0x03/0x0B` REGISTER_PEER/JOIN (annuaire, statut Super-Pair)
  - `0x04-0x05` GET_PEERS/PEERS (discovery + clés publiques PKI TOFU)
  - `0x06-0x07` UPLOAD/FORWARD (relay store-and-forward blocs)
  - `0x0C-0x0D` REQUEST_BLOCK/REQUEST_BLOCK_FORWARDED (pull inter-cluster)
  - `0x0E-0x0F` SIGNAL/SIGNAL_RECEIVED (Gossip DHT unicast)
  - `0x10-0x11` ELECTION_BROADCAST/ELECTION_RECEIVED (Bully broadcast)
  - `0x09-0x0A` PING/PONG (heartbeat protocol-level 30s)
  - `0xFF` ERROR (messages erreur)
- Validation stricte par serveur (anti-injection, anti-replay, anti-loop)
- State DB serveur (sessions, signalingRegistry, relayBuffer — RAM only)
- Limits & constants (MAX_BLOCK_SIZE=1.1MB, TTL=60s, MAX_RELAY_BUFFER_ENTRIES=500)
- Health check HTTP `/health`

### 📦 [03-MODULE-11-CLUSTER-JOIN.md](03-MODULE-11-CLUSTER-JOIN.md)

**Module cluster explicite (NOUVEAU, pas documenté théorique)**

- ⚠️ **Module 11 ajouté APRÈS documentation théorique** (~40 pages code Kotlin)
- Architecture cluster (max 50 nœuds, <5 km GPS proximity)
- State machine FSM (Undiscovered → Candidate → SuperPair / Member)
- Protocol JOIN (candidat → Super-Pair) :
  - Découverte super-peer via GET_PEERS
  - JOIN_REQUEST signé (EC P-256, anti-replay ±30s)
  - JoinAccept / JoinRedirect (alts moins chargés)
- Member Registry (Room DB persistence, snapshot MEMBER_UPDATE)
- Heartbeat intra-cluster (30s fixe, timeout 90s = 3 manqués)
- Anti-cascade (ISOLATION_BACKOFF_MS = 20s attente avant BullySolo)
- Story 12.1 : GPS retiré du payload (protocol v2)

---

## Documentation Existante (Avant scan)

Ces documents existaient avant le scan exhaustif. **Partiellement alignés au code réel.**

### 📘 Existants dans `/docs/`

- `explication-simple-cluster.md` — ✅ Aligné au code (explique JOIN/cluster bien)
- `exemple-concret-approche-join.md` — Explique approche par cas d'usage
- `comparaison-approches-cluster.md` — Compare différentes stratégies architecte
- `cluster-delimitation-gps-multicast.md` — Explique délimitation géographique
- `plan-tests-soutenance.md` — Plan tests UAT

### 📄 Au niveau racine

- `AGENT.md` — Guide projet (outdated, parle de "starter template" Firebase, ne reflète pas m01-m11)
- `RAPPORT_ALIGNEMENT_CODE_DOCS.md` — **[CRITIQUE]** 28 écarts identifiés (mai 2026)
- `RAPPORT_HARDENING.md` — Sécurité, anti-patterns mitigations
- `RAPPORT_MOBICLOUD_JURY.md` — Vue d'ensemble jury

---

## Alignement Code ↔ Documentation

### ✅ ALIGNÉ (à jour)

- **Élection Bully** (module 10) — algo basique présent, documentation ok
- **Erasure Coding Reed-Solomon** (k=4, n=2) — fixe, fonctionnel
- **Chiffrement AES-256-GCM** — implémenté, utilisé partout
- **Gossip DHT CRDT** (LWW basique) — ok
- **Heartbeat & détection panne** — 30s fixe, ok
- **Cluster & JOIN explicite** (module 11) — ✅ documenté ici (01-03)
- **Authentification EC P-256** — Keystore hardware-backed, ok
- **Module 08-09 Erasure + Distribution** — code concret (sans adaptativité)

### ⚠️ PARTIELLEMENT ALIGNÉ (simplifié)

- **Score fiabilité** — brut vs. formule multi-critères doc
- **Distribution fragments** — sans optimisation taux d'occupation
- **Téléchargement** — K fragments vs. K+2 documenté
- **Gossip** — centralisé relais vs. épidémique 3-voisins

### ❌ NON ALIGNÉ (28 écarts)

- **Erasure Coding Adaptatif** — code : k/n fixes 4/2, doc : K+2 à K+8 dynamique
- **Preuve de Travail Hashcash** — code : 0, doc : 1 sec/identité
- **Réveil Asynchrone** — code : 0, doc : 2 vecteurs détaillés
- **Module 9 Gouvernance** — code : 0, doc : Karma + PoR (retiré scope)
- **DHT Partitionné Chord** — code : 0, doc : O(log N) anneau
- **Anti-Clandestin & PoR** — code : 0, doc : détecteur complices
- **Hystérésis élection** — code : 0, doc : 15% bonus sortant
- **Mandat Super-Pair** — code : ∞ (pas abdication), doc : 30 min max

**Voir détail complet** : `RAPPORT_ALIGNEMENT_CODE_DOCS.md` (460 lignes, 28 écarts numérotés)

---

## Comment Utiliser Cette Documentation

### Pour **Développeurs** (code existant)

1. Lire **01-ARCHITECTURE-IMPLEMENTATION.md** (vue d'ensemble modules m01-m11)
2. Pour module spécifique, chercher section "Module XX : ..."
3. Pour protocole relai, lire **02-RELAY-PROTOCOL-OPCODES.md** (opcodes exactes)
4. Pour cluster, lire **03-MODULE-11-CLUSTER-JOIN.md** (FSM, protokol JOIN)
5. Croiser avec code source (chemins fichiers fournis)

### Pour **Documentalistes** (mise à jour docs)

1. Utiliser ces documents comme **source de vérité unique**
2. Mettre à jour `/AGENT.md` (actuellement obsolète)
3. Réécrire `/docs/description_technique_formelle.md` (ou créer version v2 simplifié)
4. Retirer sections "Hashcash PoW", "Réveil asynchrone", "Module 9 Karma"
5. Ajouter section "Cluster JOIN explicite" (module 11)

### Pour **Audits sécurité**

1. Lire **02-RELAY-PROTOCOL-OPCODES.md** — validation stricte relai
2. Lire `RAPPORT_HARDENING.md` — anti-patterns mitigations (AC#1-10)
3. Vérifier `app/src/main/kotlin/com/mobicloud/data/p2p/websocket/RelayAuthSigner.kt` (signature auth)
4. Vérifier `app/src/main/kotlin/com/mobicloud/domain/usecase/m11_join/ProcessJoinRequestUseCase.kt` (check-and-add atomique)

### Pour **Tests & QA**

1. **Heartbeat interval** : 30s fixe (ClusterConstants.kt)
2. **Timeout Super-Pair** : 90s (3 manqués)
3. **Admission timeout** : 5s end-to-end (JOIN_REQUEST_TIMEOUT_MS)
4. **Erasure k/n** : 4 données + 2 parité (tolérance 2 pertes)
5. **Max cluster** : 50 nœuds (MAX_CLUSTER_SIZE)
6. **Anti-replay** : ±30s fenêtre (AUTH_WINDOW_MS)

---

## Fichiers Clés Code Source

### Android App (`app/src/main/kotlin/com/mobicloud/`)

**Modules domaine** (m01-m11) :
```
domain/usecase/
├── m01_auth_discovery/          # Auth EC P-256, score fiabilité
├── m01_discovery/               # CalculateReliabilityScoreUseCase
├── m03_m04_gossip_heartbeat/    # GossipSyncUseCase, heartbeat
├── m05_dht_catalog/             # DHT Consistent hash, CRDT
├── m06_m07_repair_migration/    # Auto-repair, migration proactive
├── m08_hosting/                 # Hébergement fragments
├── m08_m09_erasure_coding/      # Erasure encode/decode, distribution
├── m10_election/                # Bully election
└── m11_join/                    # Cluster JOIN (NOUVEAU)

domain/models/
├── ErasureParameters.kt         # k=4, n=2
├── m11_join/ClusterConstants.kt # HEARTBEAT=30s, MAX_CLUSTER=50

data/p2p/
├── websocket/RelayMsg.kt        # Opcodes (0x01-0xFF)
├── websocket/RelayWebSocketClient.kt  # Client auth
├── tcp/BlockDownloadClient.kt   # P2P TCP fragments
└── relay/GossipRelayChannel.kt  # Gossip DHT over relai

data/network/service/
└── MobicloudP2PService.kt       # Service principal
```

### Relai Node.js

```
relay-server/
└── server.js (~740 lignes)      # Authentification, signalisation, relai
```

---

## Paramètres & Constantes (Résumé)

| Paramètre | Valeur | Lieu |
|-----------|--------|------|
| **HEARTBEAT_INTERVAL_MS** | 30 000 ms | ClusterConstants.kt, relay-server |
| **SP_TIMEOUT_MS** | 90 000 ms | ClusterConstants.kt (3 manqués) |
| **AUTH_WINDOW_MS** | 30 000 ms | relay-server (anti-replay ±30s) |
| **JOIN_REQUEST_TIMEOUT_MS** | 5 000 ms | ClusterConstants.kt |
| **ISOLATION_BACKOFF_MS** | 20 000 ms | ClusterConstants.kt (anti-cascade) |
| **LIVENESS_CHECK_INTERVAL_MS** | 15 000 ms | ClusterConstants.kt |
| **Erasure k** | 4 | ErasureParameters.kt |
| **Erasure n** | 2 | ErasureParameters.kt |
| **MAX_CLUSTER_SIZE** | 50 | ClusterConstants.kt |
| **MAX_BLOCK_SIZE** (relay) | 1 100 000 | relay-server |
| **TTL_MS** (relay buffer) | 60 000 ms | relay-server |
| **FAN_OUT** (Gossip) | 2 | GossipSyncUseCase.kt |

---

## Rapport d'Écarts (Résumé)

**28 écarts critiques identifiés** (détail : `RAPPORT_ALIGNEMENT_CODE_DOCS.md`)

**Domaines affectés**:
- Architecture réseau (doc bannit relais, code les utilise)
- Module 2 Scoring (doc : IA multi-critères, code : float brut)
- Module 3 Erasure (doc : adaptatif K+2-K+8, code : k=4, n=2 fixe)
- Module 4 Distribution (doc : optimisation taux, code : sans)
- Module 5 DHT (doc : Chord O(log N), code : local ring)
- Module 9 Gouvernance (doc : Karma+PoR, code : zéro)
- Sécurité (doc : Hashcash PoW, code : Keystore direct)

**Action**: Créer **documentation de référence UNIQUE** reflétant code réel.

---

## Prochaines Étapes Recommandées

### Immédiat (Jours)

1. ✅ **Scan exhaustif documenté** (ce document : 01-03 + index)
2. **Mettre à jour AGENT.md** : remplacer par vue module m01-m11
3. **Archiver docs anciennes** : marquer comme "v1.0 aspirationnel"

### Court terme (Semaines)

4. **Clarifier module 9** : retiré définitivement ou différé ?
5. **Décider erasure adaptatif** : implémenter K+2-K+8 ou accepter k=4,n=2 ?
6. **Documenter cas d'usage** : exemple concret upload/download/repair

### Moyen terme (Mois)

7. **Créer PRD v2** basé sur code réel (pas aspirations)
8. **Ajouter "Architecture Decision Records"** (ADR) pour chaque écart
9. **Générer API docs** (Dokka) + design diagrams (Mermaid)

---

## Validité & Maintenance

**Snapshot à**: 13 mai 2026 (commit e1bc774)

**Chaque commit modifiant**:
- Module m01-m11 implementation
- Opcode relai
- Cluster constants

**→ Requiert re-scan exhaustif** pour mettre à jour cette documentation.

**Recommandé**: Générer cette doc à chaque release (CI/CD hook post-build).

---

**Généré par**: Scan exhaustif bmad-document-project workflow  
**Mode**: EXHAUSTIVE (tous .kt + .js)  
**Language**: Français  
**Format**: Markdown avec tables, code blocks, références croisées

