# Code Review - Acceptance Auditor Prompt

**Mode:** Acceptance Auditor (Spec + Diff + Context docs)

**Story:** 4.1 - Modélisation & Persistance de la Partition DHT Locale

**Spec Location:** `_bmad-output/implementation-artifacts/4-1-modelisation-persistance-de-la-partition-dht-locale.md`

---

## Key Spec Requirements (Acceptance Criteria)

From Story 4.1 AC:

1. **Given** le nœud a rejoint le cluster et connaît ses pairs voisins
2. **When** un bloc est stocké sur ce nœud
3. **Then** une entrée `DhtEntry(blockId, nodeId, ipAddress, port, timestamp)` est insérée dans Room DB
4. **And** la partition assignée est déterminée par hachage consistant : `hash(blockId) mod N`
5. **And** le nœud peut répondre à une requête `LOOKUP(blockId)` avec l'`ipAddress:port` du nœud détenteur
6. **And** l'anneau DHT est accessible via `domain/repository/DhtRepository.kt`
7. **And** `data/local/DhtDao.kt` implémente les requêtes Room

---

## Spec Constraints (From Context/Architecture)

From epics.md Story 1.5 (NEW in diff):
- **Story 1.5: Configuration du Quota de Stockage Alloué au Réseau**
- Value persisted in `NodeSettings.allocatedStorageBytes` (Room DB)
- Default: `min(2 GB, 20% de l'espace libre)`

From epics.md Story 2.1 (MODIFIED in diff):
- **Was:** "Découverte Locale via UDP Multicast (Heartbeat)"
- **Now:** "Signalisation Inter-Réseaux via Tracker Firebase"
- AC: Firebase registerNode only (no UDP fallback)

From epics.md Story 5.5 (NEW in diff):
- **Reception & Hébergement de Blocs Distants**
- AC: "if `usedStorage >= allocatedStorage`, la requête est rejetée avec `STORAGE_FULL`"
- Weight gained: +1 per block served
- Weight penalty: -5 per block deleted due to quota reduction

From epics.md Epic 8 (MODIFIED):
- Renamed from "Karma" to "Weight"
- Story 8.4 (NEW): Quota de Stockage & Weight Potentiel

From epics.md Epic 9 (NEW):
- AI prediction for node departure and future reliability score

---

## Key Changes in Diff

1. **Removal of UDP Multicast Discovery** (Stories 2.1 refactored)
   - Deleted: UdpHeartbeatBroadcaster.kt, UdpHeartbeatReceiver.kt, HeartbeatMessage.kt, HeartbeatPayload.kt
   - Removed: CHANGE_WIFI_MULTICAST_STATE permission
   - Changed: Default DiscoverySource from LOCAL_UDP → REMOTE_FIREBASE

2. **Firebase Announce Logic Simplified**
   - Removed: 10-second delay + conditional check for active local peers
   - Removed: Fallback messages mentioning "mode local seul"

3. **Removed Stability Monitoring Loops**
   - Loop 4: Stability monitor (setStable based on active peers)
   - Loop 5: Network state listener
   - Removed: reliabilityScoreFlow management

4. **Epics Documentation Updated**
   - Epic 2: Removed FR-01.1 (UDP Multicast) from Functional Requirements
   - Epic 2: Updated FRs covered (no more UDP)
   - Story naming: 2.1 changed from UDP to Firebase
   - New Epic 9: AI Prediction (Stories 9.1-9.4)
   - New Story 5.5: Block Reception & Hosting
   - New Story 1.5: Storage Quota Configuration
   - Epic 8 renamed: Karma → Weight

---

## Audit Checklist

**Does the diff satisfy Story 4.1 Acceptance Criteria?**
- [ ] AC#1: Peer discovery still works (Firebase alternative to UDP)
- [ ] AC#3: DhtEntry model supports ipAddress:port (from Peer source)
- [ ] AC#4: Consistent hash ring (SHA-256 determinism maintained)
- [ ] AC#5: LOOKUP mechanism (depends on peer registration via Firebase)
- [ ] AC#6: DhtRepository interface accessible
- [ ] AC#7: DhtDao implements CRUD

**Does the diff align with spec constraints?**
- [ ] Story 1.5 (Storage Quota): New story added to epics, but implementation files NOT in diff (verify if intentional)
- [ ] Story 5.5 (Block Reception): New story added to epics, UseCase check in spec: `domain/usecase/m08_hosting/ReceiveAndHostBlockUseCase.kt` (NOT in diff — verify if already implemented)
- [ ] Story 2.1 Refactoring: Firebase-only discovery viable? No UDP fallback?
- [ ] Epic 8 Rename: "Karma" → "Weight" terminology consistent? (verify UpdateWeightScoreUseCase, check for leftover "Karma" references)
- [ ] Epic 9 New: AI prediction infrastructure (TFLite models, feature collection) — this epic scope beyond Story 4.1?

**Spec Violations or Deviations?**
- [ ] Multicast Lock: spec mentions "MulticastLock Wi-Fi pour empêcher l'OS de filtrer les paquets UDP" — now removed, WiFi multicast behavior undefined
- [ ] Firebase as sole discovery: spec says "(Wifi) et à travers le NAT via le Tracker STUN/Firebase" — now ONLY Firebase, hybrid no longer true
- [ ] Error handling: spec says "si Firebase est inaccessible, une `Result.Failure` est remontée" — diff shows failure, but no mention of retry strategy or timeout

**Missing Implementation in Diff?**
- [ ] DhtModule.kt: Should exist per spec, NOT in diff (verify if separate commit)
- [ ] ConsistentHashRing tests: Should exist per spec, NOT in diff
- [ ] Story 1.5 implementation: New story in epics, no implementation files in diff
- [ ] Story 5.5 implementation: New story in epics, no implementation files in diff
- [ ] Updated permission requests: Story 1.4 AC changes (remove CHANGE_WIFI_MULTICAST_STATE) — partially done in AndroidManifest.xml, but AC also says "les permissions ... sont demandées en un seul flux" — which component handles request flow?

**Epic/Story Numbering Consistency?**
- [ ] Story 2.1 renamed but still labeled 2.1 (correct, not 2.2)
- [ ] Story 2.2 now has AC from old Story 2.2 (Signaling) — renumbering done correctly?
- [ ] Story 2.3 references "Dashboard" but was Story 2.4 — renumbering applied throughout?
- [ ] New Stories 1.5, 5.5, 8.4, 9.1-9.4: Are these blocking Story 4.1 completion or parallel work?

---

## Task

Review the diff against the spec. List findings as Markdown:
- **Finding title** (one line)
- **Spec reference:** Which AC/constraint violated
- **Evidence from diff:** Quote or line range
- **Risk/Impact:** What could break

Focus on:
1. Violations of acceptance criteria
2. Deviations from spec intent
3. Missing implementation of specified behavior
4. Contradictions between spec constraints and actual code
5. Missing error handling or fallback logic

Report ONLY significant findings; ignore style/naming unless spec-prescribed.
