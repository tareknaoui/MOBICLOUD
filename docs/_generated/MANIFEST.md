# Manifest — Fichiers Générés Scan Exhaustif
**Date**: 13 mai 2026  
**Location**: `/c/Users/DABC/Documents/GitHub/MOBICLOUD/docs/_generated/`  
**Mode**: EXHAUSTIVE  
**Total**: 6 documents, ~2,740 lignes, ~89 KB

---

## Fichiers Générés

### 1. 📋 **00-INDEX.md** (280 lignes, 12 KB)

**Purpose**: Navigation maître et index complet

**Contenu**:
- Table des documents générés
- Docs existantes (avant scan)
- Alignement code-docs (28 écarts résumés)
- Instructions utilisation (dev, docs, audit, QA)
- Fichiers clés code source
- Paramètres & constantes résumé
- Prochaines étapes recommandées

**Audience**: Tous (point d'entrée)

---

### 2. 🏗️ **01-ARCHITECTURE-IMPLEMENTATION.md** (526 lignes, 21 KB)

**Purpose**: Vue d'ensemble complète architecture & 11 modules

**Contenu**:
- Vue d'ensemble projet (monorepo multi-part)
- Structure répertoires detaillée
- Stack technologique (Kotlin, Jetpack Compose, Node.js, Room, etc.)
- **11 Modules m01-m11** :
  - m01 Auth & Discovery
  - m03-04 Gossip & Heartbeat
  - m05 DHT Catalog
  - m06-07 Auto-repair & Migration
  - m08 Hosting
  - m08-09 Erasure Coding
  - m10 Élection Bully
  - **m11 Cluster & JOIN** (NOUVEAU)
- Protocole relai (opcodes, framing, signalisation)
- Paramètres réels (30s heartbeat, 90s timeout, etc.)
- Architecture réseau (3 canaux)
- Propriétés sécurité
- Résumé alignement vs. doc théorique

**Audience**: Architectes, développeurs, PM

---

### 3. 🔧 **02-RELAY-PROTOCOL-OPCODES.md** (494 lignes, 13 KB)

**Purpose**: Référence complète protocole relai Node.js

**Contenu**:
- Framing binaire (structure exacte)
- **18 Opcodes détaillés** (0x01-0xFF) :
  - AUTH/AUTH_OK — EC P-256, anti-replay ±30s
  - REGISTER_PEER/JOIN — Annuaire signalisation
  - GET_PEERS/PEERS — Discovery + clés publiques
  - UPLOAD/FORWARD — Relay store-and-forward
  - REQUEST_BLOCK/REQUEST_BLOCK_FORWARDED — Pull inter-cluster
  - SIGNAL/SIGNAL_RECEIVED — Gossip DHT unicast
  - ELECTION_BROADCAST/ELECTION_RECEIVED — Bully broadcast
  - PING/PONG — Heartbeat protocol-level
  - ERROR — Messages erreur
- Validation stricte (anti-injection, anti-replay, anti-loop)
- State DB serveur (sessions, signalingRegistry, relayBuffer — RAM only)
- Limits & constants (MAX_BLOCK_SIZE, TTL, etc.)
- Health check HTTP `/health`

**Audience**: Backend engineers, relay operators, protocol designers

---

### 4. 📦 **03-MODULE-11-CLUSTER-JOIN.md** (500 lignes, 15 KB)

**Purpose**: Module 11 cluster explicite (NOUVEAU, pas en théorique)

**Contenu**:
- ⚠️ Statut : **Module 11 AJOUTÉ APRÈS doc théorique** (~40 pages code)
- Architecture cluster (≤50 nœuds, <5 km GPS)
- State Machine (Undiscovered → Candidate → SuperPair / Member)
- Protocol JOIN détaillé :
  - Découverte super-peer
  - JOIN_REQUEST (signature EC P-256, anti-replay ±30s)
  - JoinAccept / JoinRedirect (alternatives moins chargés)
- Member Registry (Room DB, snapshot MEMBER_UPDATE)
- Heartbeat intra-cluster (30s, timeout 90s)
- Anti-cascade (20s backoff avant BullySolo)
- Story 12.1 : GPS retiré du payload (protocol v2)
- Fichiers implémentation détaillés (12+ usecases)

**Audience**: Développeurs m11, cluster engineers, QA

---

### 5. 💻 **04-IMPLEMENTATION-DETAILS.md** (656 lignes, 17 KB)

**Purpose**: Code exacts extraits ligne par ligne (source de vérité)

**Contenu**:
- **Extraits source vérifiables** :
  - Format nodeId (16 hex ASCII)
  - EC P-256 signature (payload exact)
  - Fenêtres anti-replay (code + exemple)
  - Gossip fan-out (FAN_OUT=2)
  - Heartbeat interval (30s fixe)
  - Timeout Super-Pair (90s)
  - Erasure k/n (k=4, n=2)
  - Bully comparaison (code exact)
  - Monitoring window (20s)
  - JOIN_REQUEST signature (5 champs)
  - Check-and-add atomique (transaction garantie)
  - Frame format (type + length LE + payload)
  - Déduplication relay buffer (TTL 60s)
  - Annuaire entry (structure DB)
  - Anti-amplification Gossip (hardening)
  - Anti-loop (src≠dest validation)
  - Health endpoint JSON
  - Member Registry Room schema
  - Atomic insert (transaction)
  - Performance heartbeat relai

**Audience**: Code reviewers, security auditors, maintainers

---

### 6. 📊 **SCAN_SUMMARY.md** (284 lignes, 11 KB)

**Purpose**: Résumé exécution workflow et découvertes principales

**Contenu**:
- Étapes workflow (1-12) — statut ✅ pour chaque
- Fichiers générés (résumé + pages + contenu)
- Implémentation découverte :
  - ✅ Confirmés (8 modules + protocole + sécurité)
  - ❌ Non-implémentés (7 features)
  - ⚠️ Simplifiés (4 vs. doc)
- Paramètres exacts extraits (timings, cluster, erasure)
- 18 opcodes table résumé
- Module 11 discovery
- Alignment 1:1 confirmation
- Recommandations immédiates (dev, docs, audit)
- Maintenance future
- Conclusion

**Audience**: Exécutifs, PM, tech leads

---

## Structure Recommandée pour Lecture

### 🔰 Démarrage rapide
1. **SCAN_SUMMARY.md** (5 min) — Quoi a été scanné, résultats clés
2. **00-INDEX.md** (10 min) — Vue d'ensemble complète

### 🔧 Développement
1. **01-ARCHITECTURE-IMPLEMENTATION.md** — Comprendre 11 modules
2. **02-RELAY-PROTOCOL-OPCODES.md** — Implémenter relai
3. **04-IMPLEMENTATION-DETAILS.md** — Code exacts

### 📦 Module Spécifique
- m01-m10: Voir section dédiée dans **01-ARCHITECTURE-IMPLEMENTATION.md**
- m11: **03-MODULE-11-CLUSTER-JOIN.md** complet

### 🔐 Sécurité
1. **02-RELAY-PROTOCOL-OPCODES.md** — Validation protocole
2. **04-IMPLEMENTATION-DETAILS.md** — Hardening code
3. `RAPPORT_HARDENING.md` (root) — Anti-patterns complets

### 🧪 QA/Testing
1. **SCAN_SUMMARY.md** — Paramètres clés à tester
2. **04-IMPLEMENTATION-DETAILS.md** — Détails constants
3. **01-ARCHITECTURE-IMPLEMENTATION.md** — Scenario end-to-end

---

## Cross-References Fichiers Code Source

### Modules (m01-m11)
```
app/src/main/kotlin/com/mobicloud/domain/usecase/
├── m01_auth_discovery/           → Doc: 01, section "Module 01"
├── m01_discovery/
├── m03_m04_gossip_heartbeat/     → Doc: 01, section "Module 03-04"
├── m05_dht_catalog/              → Doc: 01, section "Module 05"
├── m06_m07_repair_migration/     → Doc: 01, section "Module 06-07"
├── m08_hosting/                  → Doc: 01, section "Module 08"
├── m08_m09_erasure_coding/       → Doc: 01, section "Module 08-09"
├── m10_election/                 → Doc: 01, section "Module 10"
└── m11_join/                     → Doc: 03 (complet) + 01 section
```

### Protocole Relai
```
relay-server/server.js           → Doc: 02 (complet) + 04 (extraits)
app/src/main/kotlin/.../websocket/
├── RelayMsg.kt                  → Doc: 02, section "Framing"
├── RelayWebSocketClient.kt      → Doc: 02, section "AUTH"
└── RelayAuthSigner.kt           → Doc: 04, section "EC P-256"
```

### Constants
```
ClusterConstants.kt              → Doc: 04, section "Constantes Cluster"
ErasureParameters.kt             → Doc: 04, section "Paramètres k/n"
RelayMsg.kt                      → Doc: 02, section "Opcodes"
```

---

## Alignement Documenation

### Avant Scan (28 Écarts Identifiés)
- Voir: `RAPPORT_ALIGNEMENT_CODE_DOCS.md` (root)

### Après Génération (0 Gaps pour implémenté)
- ✅ 01-ARCHITECTURE-IMPLEMENTATION.md — Modules m01-m11 exacts
- ✅ 02-RELAY-PROTOCOL-OPCODES.md — Protocole exakt
- ✅ 03-MODULE-11-CLUSTER-JOIN.md — Module 11 complètement documenté
- ✅ 04-IMPLEMENTATION-DETAILS.md — Code source vérifiable
- ✅ Non-implémentés marqués "❌" explicitement

---

## Maintenance & Mise à Jour

**À chaque commit modifiant**:
- `domain/usecase/m01-m11/` (module logic)
- `data/p2p/websocket/` ou `relay/` (protocole)
- `ClusterConstants.kt` ou `ErasureParameters.kt` (params)

**→ Re-run scan exhaustif** pour synchroniser

**Recommandé**: 
- CI/CD hook générant docs à chaque release
- Générer dans branch `docs/updated` → PR review

**Commande Re-run**:
```bash
# (À implémenter en CI)
./bmad-document-project \
  --mode=exhaustive \
  --language=français \
  --output=/docs/_generated \
  --scan-path=app/src/main/kotlin \
  --scan-path=relay-server
```

---

## Validité & Snapshot

**Code snapshot à**: 13 mai 2026 (commit e1bc774 + derniers scans)

**Couverture**:
- ✅ Tous fichiers .kt en `app/src/main/kotlin/com/mobicloud/`
- ✅ Fichier `relay-server/server.js` complet
- ✅ Fichiers constants & models
- ✅ Fichiers protocole (WebSocket, TCP, etc.)

**Non couvert** (out of scope):
- Tests (app/src/test, app/src/androidTest) — implémentation, pas architecture
- Build config (Gradle, convention plugins) — référencé seulement
- UI (Jetpack Compose screens) — architecture m01-m11 primary focus
- External dependencies — versions referenced, pas analysées

---

## Fichier de Référence (Cet Index)

**Location**: `/docs/_generated/MANIFEST.md`

**Mise à jour**: À chaque génération docs (automatiquement)

**Accès**: 
- Consultation → Lire ce fichier en premier (après SCAN_SUMMARY.md)
- Recherche → Utiliser Section Cross-References
- Maintenance → Rester dans `/docs/_generated/` pour future regeneration

---

## Support & Questions

**Pour question sur**:
- ✅ Architecture modules m01-m11 → Lire **01-ARCHITECTURE-IMPLEMENTATION.md**
- ✅ Protocole opcodes relai → Lire **02-RELAY-PROTOCOL-OPCODES.md**
- ✅ Cluster JOIN protocol → Lire **03-MODULE-11-CLUSTER-JOIN.md**
- ✅ Code exakt (signature, constante, algo) → Lire **04-IMPLEMENTATION-DETAILS.md**
- ✅ Ce qui a été scanné, résultats → Lire **SCAN_SUMMARY.md**
- ✅ Navigation, utilisation → Lire **00-INDEX.md**

**Écart between docs et code → Enquêter**:
1. Vérifier commit date documentation
2. Vérifier code source (chemins fournis dans 04)
3. Run re-scan exhaustif si doute
4. Créer issue GitHub avec détails

---

**Généré par**: bmad-document-project workflow  
**Mode**: EXHAUSTIVE  
**Date**: 13 mai 2026  
**Validité**: Snapshot production code à date  
**Prochaine update**: À chaque changement code m01-m11 ou relai protocole  
**Total couverture**: 2 740 lignes documentation, ~89 KB, 6 documents
