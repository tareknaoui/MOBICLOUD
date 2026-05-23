# BEAST MODE — 4h Technical Mastery Plan

> **Goal:** by the end, you can explain every algorithm, every class, and every design decision from memory — no notes.

---

## H+0:00 → H+0:45 — Architecture Map (45 min)

**Draw this from memory on paper, then verify against the code.**

```
LAYER STRUCTURE
─────────────────────────────────────
presentation/   → Compose UI (Dashboard, Network, Explorer)
domain/         → UseCases + Models + Repository interfaces (pure Kotlin, zero Android)
data/           → Repository impls, DAOs, JNI bridges
core/           → ErasureCodec (JNI), CryptoPrimitives, HkdfSha256
di/             → Hilt modules wiring everything
```

**Module pipeline — know this sequence cold:**

```
M01    → Hashcash PoW + Node Identity (anti-Sybil, entry gate)
M03-04 → Gossip + Heartbeat (BloomFilter delta-sync, liveness)
M05    → DHT Catalog (ConsistentHashRing, Insert/Lookup/Conflict)
M06-07 → Repair + Migration (DepartureNoticeHandler, LocalRepairBuffer)
M08-09 → Erasure Coding (Reed-Solomon GF(256), encode/decode JNI)
M10    → Election (ReliabilityScore → lex tiebreak → SuperPeer)
```

**Files to open and scan** (don't deep-read, just confirm structure):
- `app/.../di/AppModule.kt`
- `app/.../di/NetworkModule.kt`
- `app/.../presentation/dashboard/DashboardViewModel.kt`

---

## H+0:45 → H+1:30 — Core Algorithms (45 min)

**Read each file, close it, then explain it aloud in English. No notes.**

---

### 1. Hashcash Anti-Sybil
**File:** `domain/usecase/m01_auth_discovery/GenerateHashcashProofUseCase.kt`

**What you must know cold:**
- Difficulty = **18 bits** → SHA-256 hash must start with 18 zero bits
- CPU loop: `nodeId:timestamp:nonce` → SHA-256 → check leading zeros → nonce++
- Cache check first → avoids recomputing on reconnect
- Signature appended → proves the PoW was done by this node's key
- **Why it matters:** prevents Sybil attacks — creating fake identities is computationally expensive

**Say this out loud:**
> "A node must burn CPU cycles to join the network. We require an 18-bit SHA-256 prefix of zeros. This costs ~1 second on ARM. Fake nodes can't spam joins for free."

---

### 2. Consistent Hash Ring
**File:** `domain/usecase/m05_dht_catalog/ConsistentHashRing.kt`

**What you must know cold:**
- Input: `blockId` (string) → SHA-256 → take first 4 bytes → `floorMod(hash, numNodes)`
- Output: `nodeId` responsible for hosting that block
- **Why consistent hashing:** when a node leaves, only its blocks need redistribution — not a full reshuffle

**Say this out loud:**
> "blockId → SHA-256 → 4-byte int → modulo num_nodes → responsible node. If a node leaves, only 1/N of blocks move. Classic consistent hashing."

---

### 3. BloomFilter Gossip
**File:** `domain/usecase/m03_m04_gossip_heartbeat/BloomFilter.kt`

**What you must know cold:**
- 1024-bit array, **3 hash functions**, SHA-256 based
- `add(blockId)` → sets 3 bits; `mightContain()` → checks 3 bits
- False positives possible (rare), false negatives **impossible**
- Used in **delta-sync gossip:** node sends its BloomFilter → peer identifies missing entries without sending full catalog
- Pre-allocated `MessageDigest` instances → avoids 300 `getInstance()` calls per gossip round

**Say this out loud:**
> "Instead of sending the full DHT catalog to sync, I send a 128-byte BloomFilter. The peer checks which of its entries are missing and sends only those. Bandwidth is O(delta), not O(catalog)."

---

### 4. Super-Peer Election
**File:** `domain/usecase/m10_election/BasicElectionUseCase.kt`

**What you must know cold:**
- Step 1: collect **ReliabilityScore** for all peers concurrently via `async/awaitAll`
- Step 2: keep only peers with `maxScore`
- Step 3: tiebreak → **lexicographic max of `nodeId`** (deterministic, no extra message needed)
- Winner = super-peer for the cluster

**Say this out loud:**
> "Election is local and deterministic — no extra round-trip. Everyone runs the same algorithm on the same peer list and gets the same answer. Highest reliability score wins. Ties broken by highest nodeId string."

---

### 5. Erasure Coding
**Files:** `domain/usecase/m08_m09_erasure_coding/EncodeErasureFragmentsUseCase.kt` + `core/erasure/ErasureCodec.kt`

**What you must know cold:**
- Reed-Solomon over **GF(256)** — implemented in native C++ via JNI (`ErasureCodingJni`)
- File → zero-padded → split into **K data blocks** → codec produces **N parity blocks**
- Recovery: any **K out of K+N blocks** is sufficient to reconstruct the file
- Constraint: `k + n ≤ 255` (GF(256) arithmetic limit)
- Zero-copy: `DirectByteBuffer` bridges Kotlin → C++ with no intermediate copy

**Say this out loud:**
> "A file is split into K blocks, then N parity blocks are computed via Reed-Solomon. Any K survivors — data or parity — reconstruct the original. If k=4, n=2: we tolerate 2 peers going offline without data loss."

---

## H+1:30 → H+2:15 — Storage Pipeline End-to-End (45 min)

**Trace one file upload from tap to DHT. Know every step.**

### Upload path

```
User taps "Store file"
        ↓
ExplorerViewModel.store()
        ↓
EncodeErasureFragmentsUseCase  → K data + N parity fragments
        ↓
FragmentCipherUseCase          → AES-GCM encrypt each fragment (HkdfSha256 derives key)
        ↓
ConsistentHashRing             → assigns each fragment to a target node
        ↓
BlockTransferChannel (TCP)     → sends encrypted fragment to target peer (via relay if NAT)
        ↓
InsertDhtEntryUseCase          → logs blockId → nodeId in local DHT
        ↓
Gossip (M03-04)                → propagates DHT entry to cluster peers via BloomFilter delta-sync
```

**Files to open and trace:**
- `presentation/explorer/ExplorerViewModel.kt`
- `core/security/FragmentCipherUseCase.kt`
- `domain/usecase/m05_dht_catalog/InsertDhtEntryUseCase.kt`
- `data/p2p/tcp/BlockTransferChannel.kt`

### Download path (reverse)

```
LookupBlockLocationUseCase  → find which nodes hold which fragments
        ↓
BlockDownloader             → fetch K fragments (any K out of K+N)
        ↓
DecodeErasureFragmentsUseCase → Reed-Solomon reconstruct
        ↓
FragmentCipherUseCase.decrypt → AES-GCM decrypt
        ↓
Reassemble original file
```

---

## H+2:15 → H+3:00 — Network & Topology (45 min)

**The super-peer/cluster topology is the V4 contribution — know it perfectly.**

### Cluster admission (no GPS, load-based only)

```
Peer broadcasts JOIN request
        ↓
Super-peer checks: memberCount < MAX_CLUSTER_SIZE ?
        → YES: admit, update memberCount
        → NO:  redirect to tracker for another cluster
```

> GPS and Haversine were removed (Epic 12). Admission is purely load-based.

---

### Super-peer relay (solves 4G↔WiFi NAT)

```
WiFi peer → cannot initiate TCP to 4G peer (double NAT)
        ↓
Both connect to HA WebSocket Relay (Render)
        ↓
Relay forwards frames: WiFi_peer ↔ Relay ↔ 4G_peer
```

The relay does **transport only** — no business logic, no data stored. It is replaceable.

---

### Tracker (BitTorrent-style, stateless)

```
Super-peers only  → POST /announce to tracker
Regular peers     → GET /peers from tracker → list of super-peers
        ↓
Peer connects to a super-peer → joins cluster
```

---

### Repair & Migration (M06-07)

```
Peer sends DEPARTURE_NOTICE before leaving
        ↓
DepartureNoticeHandler  → identifies orphaned blocks
        ↓
LocalRepairBuffer       → queues re-replication tasks
        ↓
ConsistentHashRing      → assigns orphaned blocks to next responsible node
        ↓
BlockTransferChannel    → migrates fragments to new host
```

**Files to open:**
- `presentation/network/components/ClusterTopologyCard.kt`
- `presentation/network/components/CommunitySummaryCard.kt`
- `presentation/network/NetworkViewModel.kt`
- `presentation/dashboard/components/CloudRelayBadge.kt`

---

## H+3:00 → H+3:45 — Jury Questions Drill (45 min)

**Answer each one aloud in English. 2 minutes max per answer.**

| # | Question | Key answer anchors |
|---|----------|--------------------|
| 1 | Why Reed-Solomon and not simple replication? | Replication = 3× storage overhead. RS(4,2) = 1.5× overhead, same fault tolerance. |
| 2 | Why GF(256) specifically? | Field arithmetic requires a prime power. 256=2^8 maps cleanly to bytes. JNI C++ implements Galois field ops. |
| 3 | How does election avoid split-brain? | Algorithm is deterministic on same input — no voting, no consensus round. Everyone computes locally. |
| 4 | Why BloomFilter for gossip, not full DHT? | Full catalog = O(entries). BloomFilter = 128 bytes fixed. Delta-sync sends only missing entries. |
| 5 | What if the super-peer crashes without notice? | Heartbeat timeout → peers detect liveness loss → re-election → new super-peer inherits relay session. |
| 6 | Your relay is centralized — isn't that a contradiction? | It's a transport relay, not a data store. Data stays on peers. Relay is replaceable. It's the minimum viable centralization for NAT traversal. |
| 7 | How is node identity generated? | Android Keystore → RSA key pair → public key hashed → nodeId. Device-bound, cannot be cloned. |
| 8 | What does difficulty 18 bits mean in practice? | Expected 2^18 = 262,144 SHA-256 iterations ≈ 1 second on ARM. Fake identity = 1s CPU cost per attempt. |
| 9 | Why tombstones in the DHT? | Without tombstones, a re-joining peer would re-insert deleted entries during gossip sync. Tombstones enforce logical deletes. |
| 10 | What is your real-world performance result? | 4G↔WiFi transfer tested IRL — works end-to-end. Throughput bounded by 4G upload speed (~10 Mbps), not a logic bottleneck. |

---

## H+3:45 → H+4:00 — Final Stress Test (15 min)

Close everything. Take a blank paper. Draw from memory:

1. **Upload pipeline:** file → erasure → encrypt → DHT → gossip
2. **Cluster topology:** peer → tracker → super-peer → cluster
3. **Election algorithm:** score → tiebreak → winner

If you can draw all 3 diagrams correctly without looking → **you are ready.**

---

## The 3 sentences you must say perfectly

> "MobiCloud is a mobile-native P2P storage system where phones form clusters around elected super-peers using a reliability score. Files are split, Reed-Solomon encoded, encrypted, and distributed across peers via a consistent hash ring. The relay server handles NAT traversal only — no data is stored centrally."

---

*Good luck.*
