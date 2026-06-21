# Direct Competitors: MobiCloud

> Research conducted: June 2026. Labels: [Data] = verified fact from source, [Estimate] = derived figure, [Assumption] = reasoned inference, [Opinion] = editorial judgment.

---

## Hivenet

- **Website:** https://www.hivenet.com
- **Founded:** 2022 [Data]
- **Headquarters:** Switzerland (Swiss incorporated) [Data]
- **Funding:** €12M Series A (March 2024, led by SC Ventures); total ~$20.5M over 2 rounds [Data]

### Product

- **Core offering:** Distributed cloud storage (consumer/prosumer) + distributed GPU compute, built on a network of contributed hard-drive space and computing resources from participants worldwide
- **Key features (top 5):**
  1. End-to-end encryption with cryptographic sharding — data fragmented across EU nodes
  2. Android + iOS + Windows + macOS apps [Data]
  3. Contributors earn credits by sharing unused hard drive space (up to 55.56% bill reduction) [Data]
  4. Unlimited Send transfers (large file sharing) [Data]
  5. GPU compute marketplace (RTX 4090/5090) for AI/ML workloads [Data]
- **Tech approach:** Distributed node network; data encrypted and sharded before leaving device; no central server holds complete data; EU-based nodes only for storage [Data]

### Pricing

- **Model:** Freemium + tiered subscription [Data]
- **Tiers (storage):**
  - Free: 25 GB (some sources say 10 GB — likely updated tier) [Data/Estimate]
  - Paid: ~€0.01/GB; 5 TB plan = ~€3.30/TB/month [Data]
  - 200 GB, 1 TB, 2 TB, 5 TB tiers available [Data]
- **GPU compute:** From $0.10/hour; CPU from €0.035/h; per-second billing [Data]
- **Free plan:** Yes, 10–25 GB [Data]

### Market Position

- **Target customer:** Privacy-conscious consumers, developers, small teams; pivoting upmarket toward AI compute buyers [Data/Opinion]
- **Positioning tagline:** "The Sustainable Cloud" — green, decentralized, cheaper than AWS/Azure [Data]
- **Key differentiator:** Users can monetize spare hard drive space to offset subscription cost; sustainability angle (77% lower carbon emissions claimed) [Data]

### Traction Signals

- **Reviews (G2):** 5.0/5 from 2 verified reviews (storage); Compute product rated 4+ stars with 6 Trustpilot reviews [Data]
- **Social/Awards:** Received Bpifrance Deep Tech label (June 2024) [Data]
- **Notable customers:** No major institutional customers named publicly; primarily consumer/developer segment [Assumption]
- **App:** Android app exists on Play Store ("Secure Cloud Storage – Hivenet") with user reviews citing slow upload speeds and missing features [Data]

### Strengths

- Consumer-ready Android app already shipping [Data]
- Sustainability narrative resonates in EU regulatory climate [Opinion]
- Series A funded — runway to compete [Data]
- Pricing undercuts Dropbox/Google Drive significantly on $/GB [Data]
- EU-sovereign nodes — GDPR compliant [Data]

### Weaknesses (from reviews/complaints)

- Android app review complaints: slow/unreliable uploads, missing folder-upload feature, no streaming, weak file management (delete/download issues) [Data]
- Very small review base (2 G2 reviews) — limited trust signal for enterprise buyers [Data]
- No Africa presence, no Algerian territory nodes [Assumption]
- Compute offering potentially expensive vs. specialized GPU clouds (A100 for $0.75/h elsewhere) [Data]
- Storage product separate from compute — not an integrated mobile-native experience [Opinion]
- No offline-first / local-network P2P capability — requires internet for all transfers [Assumption]

### Threat Level to MobiCloud: **Medium**

- **Why:** Hivenet is the closest product analogy (consumer Android, distributed storage, sustainability) but targets a European privacy-tech consumer niche. It has no Algerian territory presence, no B2G sales motion, and does not solve the offline/local-network use case. It could threaten MobiCloud in a future consumer market, but today is irrelevant to the B2G Algeria SAM. If Hivenet ever builds Algerian nodes, threat level becomes High for non-regulated segment.

---

## Cubbit

- **Website:** https://www.cubbit.io
- **Founded:** 2016 [Data — based on seed rounds and 40-year SI partnership timelines]
- **Headquarters:** Bologna, Italy [Data]
- **Funding:** $19.7M total over 8 rounds (last round July 2024: LocalGlobe, ETF Partners, Verve Ventures, CDP Venture Capital, Primo Ventures, 2100 Ventures, Datalogic) [Data]

### Product

- **Core offering:** DS3 (Distributed S3) — software-defined geo-distributed object storage enabling enterprises and managed service providers (MSPs) to deploy sovereign, S3-compatible cloud storage on their own or partner infrastructure
- **Key features (top 5):**
  1. S3-compatible API — drop-in replacement for AWS S3 workloads [Data]
  2. Geo-distribution with erasure coding — data split and replicated across multiple locations [Data]
  3. DS3 Composer — white-label, customizable cloud storage platform for MSPs and enterprises [Data]
  4. Zero egress fees, no API call charges, no deletion fees [Data]
  5. Ransomware-resilient by design (no single failure point) [Data]
- **Tech approach:** Software layer that orchestrates distributed storage nodes across existing hardware (on-prem, edge, cloud); does not require new hardware investment; S3-protocol compatibility [Data]

### Pricing

- **Model:** Enterprise software license (DS3 Composer); DS3 Cloud subscription for SMB/MSP [Data]
- **Tiers:**
  - DS3 Cloud: flat rate per TB/month — exact figure not publicly listed; contact sales required [Data]
  - DS3 Composer: licensed per raw TB of total installed storage — custom quote only [Data]
- **Free plan:** No public free tier [Data]

### Market Position

- **Target customer:** European enterprises, MSPs, system integrators, government/defense institutions [Data]
- **Positioning tagline:** "Outsmart cloud storage. Sovereign & geo-resilient by design." [Data]
- **Key differentiator:** The only geo-distributed S3-compatible storage software that lets enterprises stay 100% sovereign on their own hardware while achieving cloud-like resilience [Opinion]

### Traction Signals

- **Reviews (Gartner Peer Insights):** Listed on Gartner Peer Insights for Public Cloud Storage 2025 [Data]
- **Customers:** 400+ companies and MSPs across Europe; Leonardo (€14B+ defense company); Rai Way (Italian state broadcaster); Eurosystem SpA (580% revenue increase from storage after adopting Cubbit) [Data]
- **Partnerships:** Commvault (cyber resilience), Worldstream (Netherlands, first Dutch partner), Scaleway marketplace listing [Data]
- **Market signal:** Gartner projects EU sovereign cloud IaaS to grow 3.3x from $6.9B (2025) to $23.1B (2027) — Cubbit is positioned directly in this wave [Data]

### Strengths

- Proven enterprise deployments including defense-grade customers [Data]
- No consumer app complexity — pure B2B/B2G SaaS model with clear ROI [Opinion]
- S3 compatibility means zero migration friction for existing cloud workloads [Data]
- Strong European regulatory alignment (GDPR, NIS2, data residency) [Data]
- 580% revenue uplift case study is a powerful sales asset [Data]

### Weaknesses (from reviews/complaints)

- Pricing opacity — no published rates; high friction for SME/government procurement [Assumption]
- DS3 Composer "available to a selected cluster of partners and customers" — gated access limits adoption [Data]
- Zero Africa presence; zero Algerian-territory nodes or partnerships announced [Data]
- Requires enterprise infrastructure investment — not mobile-native, no Android app [Data]
- European-centric brand; lacks local-market knowledge for North Africa regulatory landscape [Opinion]
- No offline/local-network storage capability [Assumption]

### Threat Level to MobiCloud: **Low (current) → Medium (3-year horizon)**

- **Why:** Cubbit is a pure enterprise B2B/B2G player with zero consumer or mobile play, and no Africa footprint. It does not compete on the same product axis (mobile P2P) and cannot fulfill Algeria's territorial data requirements today. However, if a Cubbit-style actor were to set up Algerian nodes or partner with AYRADE, they could threaten MobiCloud's institutional sales pitch. Monitoring needed.

---

## AYRADE (Algeria)

- **Website:** https://www.ayrade.com
- **Founded:** 2009 [Data]
- **Headquarters:** Algiers, Algeria [Data]
- **Funding/Capital:** IPO on Algiers Stock Exchange (June 2026), raising ~1B dinars ($7.4M USD at ~135 DZD/USD) by opening 20% of capital at 800 DZD/share [Data]

### Product

- **Core offering:** Traditional centralized cloud hosting and data center services for Algerian institutions — colocation, IaaS, cybersecurity, and data sovereignty compliance [Data]
- **Key features (top 5):**
  1. Two operational data centers on Algerian territory [Data]
  2. Sovereign cloud compliance with Algerian data residency law [Data]
  3. Cybersecurity solutions (regulatory compliance automation, AI integration) [Data]
  4. Research & innovation arm for AI/regulatory tech [Data]
  5. 10,000+ clients including ~3,700 active cloud customers across banking, energy, healthcare, public administration [Data]
- **Tech approach:** Traditional centralized data center / IaaS model; NOT distributed, NOT P2P, NOT mobile-native [Data]

### Pricing

- **Model:** Enterprise/B2G contracts — no public pricing [Data]
- **Tiers:** Custom quotes for colocation, cloud VM, cybersecurity bundles [Assumption]
- **Free plan:** No [Assumption]

### Market Position

- **Target customer:** Algerian public institutions (ministries, hospitals, banks, energy companies), large enterprises [Data]
- **Positioning tagline:** First Algerian sovereign cloud operator [Data]
- **Key differentiator:** Only dedicated Algerian cloud operator with physical infrastructure on Algerian territory and a 16-year track record serving local institutions [Data]

### Traction Signals

- **Revenue:** 192M DZD (2024) → 416M DZD (2025), +117% YoY; projected 1.66B DZD in 2026 [Data]
- **Clients:** 10,000+ total, ~3,700 active cloud customers [Data]
- **IPO:** First sovereign cloud operator to list on Algiers Stock Exchange — June 2026 [Data]
- **Infrastructure:** 2 data centers + planned expansion with 294 new servers funded by IPO [Data]

### Strengths

- Sole established Algerian-territory cloud provider with institutional track record [Data]
- 10,000+ client relationships in exactly MobiCloud's target vertical [Data]
- Regulatory moat identical to MobiCloud's (data residency law compliance) [Data]
- IPO capital influx will fund infrastructure expansion [Data]
- Deep institutional trust from 16 years in market [Data]

### Weaknesses (from reviews/complaints)

- Centralized architecture — single point of failure, no distributed resilience [Opinion]
- Traditional IaaS model — does not leverage mobile devices, P2P, or edge computing [Opinion]
- 10,000 clients but primarily VMs/colocation — no mobile-first storage UX [Assumption]
- No Android mobile app for end-users [Assumption]
- Revenue still small (~€3M equivalent 2025) relative to institutional market potential [Estimate]
- Cloud infrastructure model means OPEX for institutions (servers, network, SLAs) vs. MobiCloud's device-reuse model [Opinion]

### Threat Level to MobiCloud: **Medium**

- **Why:** AYRADE is the incumbent Algerian sovereign cloud player. It does NOT offer P2P or mobile-native storage — it is a classical data center operator. However, it controls the key institutional relationships MobiCloud needs to win. AYRADE is more likely a potential channel partner or validation reference than a direct competitor on the product axis, but institutions could choose "AYRADE VMs" over "MobiCloud P2P" for storage compliance. Threat is on the sales/relationship layer, not product.

---

## UniCloud Africa

- **Website:** https://unicloudafrica.africa
- **Founded:** ~2024–2025 (launched November 2025) [Data]
- **Headquarters:** Nigeria (pan-African) [Data]
- **Funding:** Partnership with OADC (Open Access Data Centres); funding details not disclosed [Data]

### Product

- **Core offering:** Pan-African sovereign IaaS/PaaS cloud platform deployed across 6 African countries (Nigeria, Ghana, South Africa, Zambia, Senegal, Mozambique) with 100% in-country data hosting [Data]
- **Key features (top 5):**
  1. 99.999% uptime SLA with two active-active availability zones per country [Data]
  2. Zero data egress fees [Data]
  3. Local currency billing [Data]
  4. GPU-as-a-Service for AI/ML workloads [Data]
  5. ISO 27001 and ISO 22301 compliance [Data]
- **Tech approach:** Traditional hyperscaler-style infrastructure deployed locally in each country; NOT distributed P2P; centralised data center per country [Data]

### Pricing

- **Model:** Pay-per-use operational expenditure; local currency [Data]
- **Tiers:** Not publicly listed [Data]
- **Free plan:** No [Assumption]

### Market Position

- **Target customer:** African enterprises, government, healthcare, finance [Data]
- **Positioning tagline:** "The First Connected Sovereign Cloud Platform" for Africa [Data]
- **Key differentiator:** Multi-country African sovereign infrastructure with local currency billing and zero egress fees — targeting "data colonialism" narrative [Data]

### Traction Signals

- **Geographic reach:** 6 countries launched (2025); expanding to Kenya, Tanzania, Rwanda, Uganda, Cote d'Ivoire, Egypt, Morocco [Data]
- **Notable:** No Algeria in either current or announced rollout — significant gap [Data]
- **Reviews:** No public G2/Capterra reviews found [Data]

### Strengths

- Sovereign-cloud narrative aligned with African government sentiment [Data]
- Multi-country reach — could capture pan-African institutional deals [Data]
- Zero egress fees is a competitive advantage vs. AWS/Azure [Data]
- Local currency billing removes FX risk for institutional clients [Data]

### Weaknesses

- No Algerian presence or announced plans [Data]
- Very new (launched November 2025) — unproven track record [Data]
- Classical data center model — no P2P, no mobile-native, no distributed edge [Opinion]
- No consumer/end-user mobile app [Assumption]
- Funding/financial backing not transparent [Data]

### Threat Level to MobiCloud: **Low (Algeria-specific)**

- **Why:** UniCloud Africa does not operate in Algeria and has not announced plans to enter Algeria. Even if it did, it is a traditional IaaS platform, not a mobile P2P storage competitor. Relevant only as a macro signal that the African sovereign cloud space is attracting investment.

---

## Storj / Filecoin / IPFS (Crypto-native Decentralized Storage)

- **Website:** https://www.storj.io / https://www.filecoin.io
- **Founded:** Storj: 2014; Filecoin/Protocol Labs: 2014 [Data]
- **Headquarters:** Storj: Atlanta, USA; Filecoin: San Francisco, USA [Data]
- **Funding:** Both well-funded via token sales and VC; Filecoin raised $257M ICO; Storj multiple VC rounds [Data]

### Product

- **Core offering:** Decentralized cloud storage using globally distributed storage nodes incentivized by cryptocurrency tokens (STORJ token / FIL token) [Data]
- **Key features:**
  1. Cryptographic erasure coding for redundancy
  2. S3-compatible API (Storj)
  3. Token-based economic incentives for node operators
  4. Global node network (thousands of operators)
  5. End-to-end encryption
- **Tech approach:** Blockchain/crypto-incentive model; nodes are always-on servers/desktops — NOT mobile-native [Data]

### Pricing

- **Model:** Pay-per-use [Data]
- **Storj rates:** $0.004/GB/month storage; $0.007/GB egress (as of Feb 2025) [Data]
- **Filecoin:** ~$2.50/TiB/month for archival [Data]
- **Free plan:** Storj offers a free trial tier [Data]

### Market Position

- **Target customer:** Developers, enterprises needing cheap redundant S3 storage; NOT consumers, NOT mobile, NOT Africa/Algeria [Data]
- **Key differentiator:** Cheapest globally distributed storage at scale; crypto-native incentive model [Data]

### Traction Signals

- Storj has thousands of independent node operators globally [Data]
- No Africa-specific deployments or marketing found [Assumption]
- No mobile Android consumer app for end users [Data]
- No Algerian regulatory compliance positioning [Assumption]

### Strengths

- Extremely low cost (78% cheaper than AWS S3 for archival) [Data]
- Already proven at scale globally [Data]
- Developer-friendly S3-compatible API [Data]

### Weaknesses (relative to MobiCloud)

- Requires crypto wallet/tokens — massive UX friction for Algerian institutions [Opinion]
- No mobile-native Android app for consumers [Data]
- Node operators are always-on servers, not mobile devices — different economic model [Data]
- Not compliant with Algerian data residency law (nodes in non-Algerian territory) [Assumption]
- Zero local presence, zero B2G sales capability in Algeria [Assumption]
- Crypto regulatory exposure in Algeria (crypto highly restricted) [Data — Algeria banned crypto transactions]

### Threat Level to MobiCloud: **Low**

- **Why:** Crypto legal prohibition in Algeria alone eliminates Storj/Filecoin as competitors in the B2G Algeria SAM. Their architecture (always-on desktop/server nodes, developer API, token incentives) is fundamentally different from MobiCloud's mobile-native P2P model.

---

## AventureCloudz (Algeria — Djezzy + Algeria Venture + Taubyte)

- **Website:** Referenced via Djezzy/Algeria Venture press releases; platform at aventurecloudz.dz [Estimate]
- **Founded/Launched:** April 30, 2025 [Data]
- **Headquarters:** Algeria [Data]
- **Funding:** Backed by Djezzy (Veon subsidiary, major Algerian telecom) + Algeria Venture (government accelerator) [Data]

### Product

- **Core offering:** Developer-focused cloud platform for Algerian startups and enterprises — IaaS, PaaS, AI development tools; hosted exclusively on Djezzy Cloud infrastructure on Algerian soil [Data]
- **Tech approach:** Traditional cloud hosting (Djezzy data center) + Taubyte's developer platform layer; NOT P2P, NOT distributed mobile-native [Data]

### Pricing

- Not publicly disclosed [Data]
- **Free plan:** Developer sandbox likely included [Assumption]

### Market Position

- **Target customer:** Algerian software developers, startups, tech enterprises [Data]
- **Key differentiator:** Only developer cloud platform natively integrated with Algerian telecom infrastructure [Data]

### Traction Signals

- Backed by dominant Algerian telco (Djezzy has 20M+ subscribers) [Data]
- Government endorsement through Algeria Venture partnership [Data]
- No public user numbers or revenue disclosed [Data]

### Strengths

- Telco distribution power (Djezzy's existing institutional/enterprise relationships) [Data]
- Government legitimacy via Algeria Venture [Data]
- Sovereign by design — Algerian territory [Data]

### Weaknesses

- Developer/startup focus — NOT targeting storage for hospitals/universities/ministries directly [Data]
- No mobile-native P2P storage product [Data]
- Very new — no track record [Data]

### Threat Level to MobiCloud: **Low**

- **Why:** Different product (developer cloud platform vs. institutional P2P storage). Could become relevant if Djezzy decided to launch a consumer/institutional storage product, but no evidence of such plans.

---

## Competitive Landscape Summary

### Market Concentration

The distributed P2P mobile storage space for consumers and institutions in Africa is effectively **vacant** [Opinion]. The global market has:
- European consumer distributed storage: Hivenet (Series A, Swiss, Android app exists)
- European enterprise sovereign storage: Cubbit ($19.7M, Italy, S3/enterprise only)
- Algerian traditional sovereign cloud: AYRADE (IPO 2026, centralized data center)
- Pan-African IaaS: UniCloud Africa (new entrant, no Algeria)
- Crypto-decentralized storage: Storj, Filecoin (developer/enterprise, no mobile, no Africa)

No single competitor occupies the intersection of: (1) mobile-native P2P, (2) Algerian territory data sovereignty, (3) institutional B2G sales motion.

### Gaps in Market (What No Competitor Does Well)

1. **Mobile-native P2P storage that leverages end-user Android devices as the storage layer** — zero competitors do this [Opinion]
2. **Algerian-territory compliant P2P/distributed storage** — AYRADE is centralized IaaS, not P2P [Data]
3. **Offline-capable / local-network storage** (WiFi LAN transfers without internet) — no competitor offers this [Assumption based on product review]
4. **Consumer-priced storage with institutional B2G compliance** — the market is bifurcated between consumer apps (Hivenet, low trust) and enterprise deals (Cubbit, no mobile) [Opinion]
5. **Relay-based encrypted transport over WebSocket for cross-network P2P on mobile** — no public competitor has productized this for the Algerian institutional context [Opinion]

### MobiCloud's Positioning Opportunity

MobiCloud occupies a **structurally unique position**:

1. **Legal moat**: Algeria's Law 11-25 (July 2025) + Decrees 25-320/321 + 26-07 + ARPCE Decision 48 mandate on-Algerian-territory data storage for public institutions. No hyperscaler has Algerian territory infrastructure. AYRADE has it but is centralized IaaS. MobiCloud's relay server can be hosted in Algeria, with data staying on Algerian devices — a legally defensible interpretation of data residency. [Data + Opinion]

2. **Zero capex for institutions**: Existing Android devices of staff become the storage nodes. No server rack procurement. For a public hospital or university with 200 Android devices and a constrained IT budget, MobiCloud's model could cost dramatically less than AYRADE colocation. [Opinion]

3. **Consumer story for later**: Hivenet proves there is an appetite for distributed mobile storage (consumer segment), but Hivenet cannot enter Algeria legally without Algerian nodes. MobiCloud starts B2G, builds distribution, then captures consumer segment from a sovereign position. [Opinion]

### Competitive Moat Assessment

| Moat Type | Strength | Notes |
|---|---|---|
| Legal/Regulatory | **Strong** | Law 11-25 is a hard barrier for non-Algerian competitors [Data] |
| Technology | **Medium** | P2P relay + super-peer topology is non-trivial but replicable [Opinion] |
| Network Effects | **Medium** | More devices = more storage = lower cost per institution [Opinion] |
| Local Market Knowledge | **Strong** | Algerian-built, Arabic/French UX, understand procurement cycles [Assumption] |
| First-Mover in Algeria | **Strong** | No comparable product exists today [Opinion] |
| Distribution | **Weak (today)** | No institutional sales relationships yet; must build vs. AYRADE's 10K clients [Data] |

---

## Data Gaps

- **Hivenet**: Total registered user count not disclosed; Android app MAU unknown; whether they have any enterprise/B2G clients [Unknown]
- **Cubbit**: Exact per-TB pricing for DS3 Cloud not public; no information on any Africa/MENA expansion plans [Unknown]
- **AYRADE**: Pricing per VM/TB not public; whether they have signed contracts with ministries or universities specifically (vs. banks/energy) is unclear [Unknown]
- **AventureCloudz**: User adoption, pricing, whether any institutional non-developer clients [Unknown]
- **UniCloud Africa**: Funding details, exact pricing, likelihood of Algeria expansion timing [Unknown]
- **Market**: No reliable data on how many Algerian public institutions have signed cloud contracts of any kind — the 600-700 institutional targets is an estimate, not a measured addressable universe [Estimate per Wave 1 brief]
- **Regulatory enforcement**: Whether ARPCE Decision 48 is actively enforced (contracts being denied to non-compliant providers) or currently aspirational — this materially affects moat strength [Unknown]

---

*Sources used: Hivenet.com, Cubbit.io, Crunchbase, Tracxn, G2, Trustpilot, Gartner Peer Insights, TechAfrica News, Ecofin Agency, DealRoom, AlgeriaTech.news, Ecofinagency.com, DevTeam.Space, Villpress, Algerianewsgate.com — June 2026.*
