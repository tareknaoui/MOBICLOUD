# Intake Brief

**Phase:** 1 — Intake Interview
**Project:** mobicloud
**Date:** 2026-06-21
**Confidence:** High (direct founder answers)

---

## The Problem

People in regions with unreliable internet or expensive cloud storage lose their files when their phone breaks or gets stolen. This is not a niche edge case — it is the default experience for hundreds of millions of Android users in emerging markets where:
- Cloud storage subscriptions cost real money relative to local salaries
- Reliable connectivity is inconsistent even where 4G coverage exists
- No systematic backup habit exists; most people accept file loss as normal

**How people currently cope:**
- WhatsApp "Keep in Chat" as accidental, unintentional backup
- Manual USB transfers to a laptop or family member's phone
- Cheap Chinese USB drives that fail after 6 months
- Doing nothing — accepting file loss as inevitable

The problem is real and under-served. The gap between "phones are everywhere" and "reliable backup exists" has not been closed by any consumer product.

## The Solution

**MobiCloud** — an Android app that lets a group of smartphones collectively *store* (not share) each other's files in a distributed, encrypted way.

**How it works (technical):**
1. Files are encrypted on-device using Android Keystore
2. Reed-Solomon erasure coding splits each file into k+m fragments distributed across cluster members — parameters k and m are configurable. In the tested 3-phone demo: RS(2,1) — 3 fragments, any 2 sufficient to reconstruct, tolerates 1 node failure. In larger clusters (e.g. 50 phones): k and m scale accordingly, tolerating m simultaneous node failures. Fault tolerance grows with cluster size.
3. Fragments are distributed across group members' phones
4. A WebSocket relay server (~$10/month) handles NAT traversal for inter-network communication (4G↔4G, 4G↔WiFi) — internet is required for transfers between devices on different networks
5. A super-peer topology manages cluster coordination: one peer elected via Bully algorithm handles cluster orchestration; failover is automatic
6. No file content is ever stored on the relay — the relay only routes encrypted traffic in transit. Data at rest lives exclusively on member devices.

**Important distinction — "distributed" ≠ "offline":**
MobiCloud requires an internet connection (4G or WiFi) for inter-device file transfers, because the relay is needed when devices are on different networks (which is the common case). It is NOT an offline/mesh solution. What makes it different from cloud storage is that data is *stored* on users' own phones — not on any central server — and the relay only handles routing, never persistence.

**What is working today (prototype):**
- 3-phone clusters with file upload and distributed storage
- Automatic self-healing when one phone goes offline
- Super-peer failover via Bully election
- Tested on real devices (not just emulator)

**What is not yet implemented:**
- Re-replication after permanent node loss (identified as next version)
- Incentive mechanism for storage contribution (removed from scope)
- Cluster sizes above 3 (intentional demo constraint — architecture supports larger by changing MAX_CLUSTER_SIZE constant)

## The Customers

### Primary: Consumer (B2C)
**Profile:** University students and young professionals in emerging markets — specifically Algeria and North Africa — who:
- Share living spaces (dorms) or work in the same building
- Own Android phones (dominant platform in the market)
- Cannot afford or reliably access Google Drive / iCloud due to bandwidth costs or connectivity gaps
- Already trust each other enough to share Wi-Fi passwords (social trust model)

**Pain:** They lose files when phones break or get stolen. They have no affordable, reliable backup option.

### Secondary: Institutional (B2G)
**Profile:** Algerian public sector — universities, hospitals, ministries — that are:
- Actively seeking sovereign, locally-hosted alternatives to foreign cloud services
- Subject to Algeria's data sovereignty legislation (Law No. 11-25, Presidential Decree 25-321)
- Blocked from using Google Drive / Microsoft 365 for sensitive documents due to compliance requirements

**Pain:** Foreign cloud providers cannot legally or structurally offer data that never leaves Algerian jurisdiction. MobiCloud can.

## The Team

**Solo founder:** Computer science final year student (Algeria). Full technical ownership — Android development, distributed systems design, and simulation all done by one person.

**Strengths:** Full-stack technical depth, intimate knowledge of the system architecture, real prototype exists.
**Gaps:** No business or design co-founder, no sales or institutional relationships, no marketing background.

## Why Now

1. **4G penetration in Africa crossed 50% in 2024** — phones are connected but cloud remains expensive relative to local income. The infrastructure gap is closing; the affordability gap is not. [Data, GSMA 2024]
2. **Algeria's digital sovereignty legislation is active** — Law No. 11-25 (July 2025), Presidential Decree 25-321 (Dec 2025), Presidential Decree 26-07 (Jan 2026). The regulatory tailwind is real and recent.
3. **Android Keystore and erasure coding libraries are mature** — the technical primitives to build this without specialized hardware now exist on consumer devices.
4. **No consumer-grade P2P Android storage app exists without crypto/blockchain friction** — the gap in the market is genuine.

## Competitive Landscape (Founder's Assessment)

| Competitor | Type | Gap vs. MobiCloud |
|---|---|---|
| Google Drive / iCloud | Centralized cloud | Foreign jurisdiction, subscription cost, connectivity required |
| Filecoin / Storj | Decentralized (blockchain) | Requires crypto wallet, no consumer UX, developer-oriented |
| IPFS | Protocol, not product | No consumer app |
| Briar | P2P messaging | Messaging, not storage |
| Hivenet | Distributed storage, Android app | EU-based, no sovereignty angle, no Africa focus |
| Cubbit | B2B geo-distributed | Enterprise only, no consumer mobile |

**[Opinion]:** The consumer niche — trusted-group, phone-to-phone, no crypto, Africa-first — appears genuinely unoccupied.

## Business Model (Preliminary)

Three paths identified, none fully validated:

**Path 1 — B2G Institutional (most defensible near-term):**
Sell to Algerian universities, hospitals, or ministries as a sovereign intranet storage solution with support contract. Revenue model: per-institution annual license + support fee. Relay hosted on Algerian infrastructure is a prerequisite. Requires institutional relationships (currently absent).

**Path 2 — Subscription (consumer):**
Tiered subscription model for individuals and groups. Free tier: small group (up to 3 phones), limited storage. Paid tier: larger clusters, more storage quota, priority relay bandwidth. Revenue model: monthly or annual subscription per user or per cluster. Requires real user base before generating meaningful revenue.

**Path 3 — Relay-as-a-Service (infrastructure):**
The relay server is the only centralized component MobiCloud controls. Charge per active cluster, per GB relayed, or per month per tenant. Open-source the Android client; monetize the relay. Revenue model: usage-based. Cleanest technical moat — no one can replicate the sovereign relay running on Algerian servers.

**Founder's assessment (post-brainstorm):** B2G + RaaS are the most defensible near-term paths. Subscription consumer is the long-term volume play. B2G finances consumer development — not the reverse.

**No pricing validated with customers yet.**

## Known Gaps and Risks

| Gap | Severity | Notes |
|---|---|---|
| No incentive mechanism for contribution | High | Social trust works in closed groups; breaks for strangers |
| No real users (lab only) | High | No field validation of any assumptions |
| Relay server on US infrastructure (Render) | High | Conflicts with Algerian data localization for institutional sales |
| No institutional contacts | High | B2G path has zero sales foundation |
| Permanent node loss → data loss | Medium | RS(2,1): losing 2 of 3 nodes = file unrecoverable |
| Re-replication not implemented | Medium | Identified for next version |
| Solo founder — no business/design skills | Medium | Fundable gap but limits early traction |
| Cluster size capped at 3 for demo | Low | Architectural constraint is trivially removable |

## Success Definition (12 Months)

- 3 pilot groups of 10+ real users running daily for 30+ consecutive days without data loss
- 1 institutional conversation progressed to signed pilot agreement (even unpaid)
- Relay server handling 50+ concurrent clusters without crashing

## Kill Criteria (Founder's Own)

"If after 6 months of real-world testing, groups consistently stop using it after the first week because managing cluster membership is too fragile — phones leave, clusters break, files become inaccessible — and the reliability bar for non-technical users proves fundamentally unreachable with the current architecture."

## Competitive Defense

**Against Google launching a similar product:**
Google launching P2P storage still means Google controls the relay, Google reads the metadata, and data lives under US jurisdiction. The public sector angle rejects this by design. The sovereignty positioning is structurally impossible for any foreign provider to replicate.

---

## Flags

**Red Flags:**
- No real users. Every assumption about retention, usability, and incentive is untested.
- Relay server on US infrastructure is structurally incompatible with institutional B2G sales under current Algerian law.

**Yellow Flags:**
- Solo founder with no business skills — the technical product may be sound but go-to-market execution is high-risk.
- Incentive problem limits consumer scale to trusted groups — social cap on TAM unless reward mechanism is added.
- No institutional contacts — B2G is the "most defensible" path but has zero sales pipeline.
- Hivenet exists with an Android app — differentiation must be proactively communicated.

## Sources
- Founder interviews (June 2026) — direct
- Pre-flight findings: `00-intake/preflight.md`
