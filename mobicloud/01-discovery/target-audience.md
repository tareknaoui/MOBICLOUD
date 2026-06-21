# Target Audience

**Phase:** 3 — Market Research (Synthesis)
**Project:** mobicloud
**Date:** 2026-06-21
**Confidence:** Medium (B2G buyer path confirmed from regulatory/procurement research; B2C behavioral data is proxy from South Africa/Nigeria, not Algeria specifically)

---

## Primary Persona: B2G Buyer

**Name (fictional):** Karim — DSI (Directeur des Systèmes d'Information)
**Role:** IT Director / Head of Information Systems
**Institution:** Public university (2,000–15,000 students) or regional hospital, Algeria
**Demographics:** 35–50 years old; engineering or computer science background; has worked in public sector 10+ years; based in Algiers, Oran, or Constantine

**Goals:**
- Keep the institution's IT systems compliant with evolving Algerian law
- Reduce dependence on foreign cloud services (liability after Law 11-25)
- Provide staff and students with file storage that works on mobile devices they already own
- Avoid expensive on-premise server infrastructure projects he doesn't have budget or staff for
- Not get blamed if an audit finds non-compliant data storage

**Frustrations:**
- Google Drive and Dropbox are what everyone uses, but they are now legally risky. Removing them creates user revolt without a replacement.
- CERIST connection exists on paper but is too slow and capacity-constrained for practical mobile use.
- Nextcloud requires a server he doesn't have and a sysadmin he can't hire.
- AYRADE is compliant but expensive and centralized — requires infrastructure investment.
- Procurement for anything new takes 12–18 months unless it's under the gré à gré threshold.

**Current tools:** CERIST fiber access (if connected), Google Workspace (technically non-compliant), some USB drive culture, on-premise NAS in wealthier institutions.

**Representative quote:** *"On a les textes de loi depuis juillet 2025. Mon directeur général m'a demandé ce qu'on fait pour nos données. Je n'ai pas encore de réponse concrète."* [Assumption — no verbatim from real DSI obtained; this reflects the compliance pressure context from Wave 3 research]

**Decision-making path:**
- **DSI** (Karim) — technical evaluator, identifies the problem, shortlists vendors, runs the pilot
- **RSSI** (Responsable Sécurité des Systèmes d'Information) — security validator, checks for compliance with Decree 26-07 cybersecurity obligations
- **DG / Rector / Director** — budget authority, signs the contract

**Decision criteria (ranked):**
1. Legal compliance — does this satisfy Law 11-25 and ARPCE requirements?
2. No new server infrastructure required
3. Works on Android phones staff already own
4. DZD billing (EUR/USD billing = procurement rejected)
5. Local support (can reach someone who speaks Darija or French, responds within 24h)

**Budget:** Gré à gré contracts typically under 3M DZD ($22,000 at current rates) to avoid open tender. Initial contract likely 500K–2M DZD/year.

**Sales cycle:** 12–24 months for open tender; 3–6 months for gré à gré under threshold with an existing internal champion.

**Common objections:**
- "We already use CERIST." → Response: CERIST doesn't provide mobile backup; it's a network, not a storage product.
- "You have no reference client." → The first deal breaks this. Until then: demo + pilot at zero cost.
- "Is this ANPT-certified / ANPDP-registered?" → MobiCloud must prepare compliance documentation before B2G outreach.
- "What happens if your company disappears?" → Open protocol answer: if MobiCloud closes, the relay code is open-source, institutions can run their own.

**Where to reach Karim:**
- Industry events: Forum Algérie Numérique, Salon DISTREE Africa
- LinkedIn (Algerian IT professionals are active — search "DSI Algérie" returns real contacts)
- ANPT conferences and cybersecurity working groups
- Warm introduction via AYRADE (if partnership secured)
- University rector networks

---

## Secondary Persona: B2C User

**Name (fictional):** Yasmine — Étudiante en master, Alger
**Role:** University student / young professional
**Demographics:** 22–28 years old; Android phone (Samsung or Chinese mid-range); mobile-first internet user; monthly income 30,000–60,000 DZD (student stipend or junior salary); Algiers, Oran, Constantine
**Device:** Android 11+, 64–128GB storage, often 60–80% full

**Goals:**
- Never lose her thesis drafts, lecture notes, and photos when her phone breaks or gets stolen
- Not pay €10/month for Google One when her entire disposable income is ~15,000 DZD/month
- Have something that works when connectivity is spotty (between neighborhoods, in certain buildings)
- Trust that her files are private — not indexed by Google, not visible to a company

**Frustrations:**
- Google Drive free tier (15GB) fills up fast with photos and lecture recordings. Paid tier is unaffordable and requires an international credit card she doesn't have.
- WhatsApp "Starred Messages" and "Keep in Chat" as accidental backup — fragile, not searchable, disappears if WhatsApp account is deleted.
- USB drives break. She's lost files twice already.
- Nothing works when she's on campus WiFi behind NAT — files she shared won't sync between her laptop and phone.

**Current tools:** WhatsApp for informal file sharing, occasionally Google Drive (free tier), USB drives.

**Representative quote (composite from Wave 3 review mining):** *"I had 3 years of photos and university work on my phone. When it broke I lost everything. Google Drive is too expensive for me. I wish there was a way to back up to my friends' phones automatically."* [Tier 3, composite]

**Decision criteria:**
1. Free or very cheap (200–400 DZD/month maximum, below Spotify's 1,299 DZD)
2. Works automatically without user intervention
3. Doesn't need constant internet — at least syncs opportunistically
4. Friends/flatmates can be in the same group (social buy-in required)
5. Private (she doesn't want Google reading her files)

**WTP:** 200–400 DZD/month. Evidence: Spotify Algeria = 1,299 DZD/month; Coursera = 2,499 DZD/month. Storage must price well below these anchors given lower perceived utility of "backup" vs. "entertainment." [Estimate, Wave 3]

**Common objections:**
- "I don't want to use my phone's storage for other people's files." → Reciprocal model: her files are on their phones too.
- "What if my friend leaves the group?" → Re-replication feature (currently not implemented — honest blocker).
- "Is it safe? Can my friend see my files?" → Encryption answer: no, they store encrypted fragments they cannot decrypt.
- "It needs internet to work anyway — what's the difference from cloud?" → Key distinction: files are *stored* on phones, not on any company's server. If MobiCloud disappears, the files don't. [Correct technical framing]

**How she discovers apps:**
- TikTok (21.1M Algerian users): viral demo videos in Darija showing "your files survive on your friends' phones"
- WhatsApp and Telegram study groups: peer recommendation
- University Facebook groups: posts about useful apps for students
- Organic word-of-mouth in dorms and shared apartments

**CAC:** Near zero via organic channels. [Estimate, Wave 3]

---

## Anti-Persona (Who NOT to Target)

**Multi-national companies operating in Algeria.** They have compliance teams, existing contracts with certified cloud providers, and procurement processes that require SOC 2 / ISO 27001 certifications MobiCloud doesn't have.

**Individual developers wanting decentralized storage.** They'll use IPFS, Storj, or self-hosted Nextcloud. MobiCloud's consumer UX is not for them; and their bar for "working" is different from an end-user's.

**Users outside Algeria (at launch).** Every additional country requires new relay infrastructure, new compliance documentation, and new relationships. Stay in Algeria until one market is validated.

**Rural users with no 4G coverage.** MobiCloud requires internet for inter-device transfers. A user in a village with no 4G connectivity cannot benefit from the relay-based architecture. [Technical constraint confirmed in intake]

---

## Customer Pain Hierarchy

Ranked by frequency × intensity across all customer voice research:

| Rank | Pain | Segment | Intensity | Frequency |
|---|---|---|---|---|
| 1 | Compliance exposure: using Google Drive/foreign cloud is now legally risky | B2G | Hair-on-fire | High (regulatory pressure confirmed by 4 laws) |
| 2 | No mobile-native compliant storage exists without expensive server infrastructure | B2G | High | High (AYRADE/CERIST don't solve this) |
| 3 | Phone breaks or gets stolen → all files lost | B2C | High | High (documented in Africa data) |
| 4 | Cloud storage too expensive relative to local income | B2C | Moderate-High | High (pricing data from Africa markets) |
| 5 | Self-hosted (Nextcloud) requires server and IT skills most institutions lack | B2G | Moderate | Medium (confirmed from Nextcloud reviews) |
| 6 | Distributed storage products (Hivenet) have reliability failures | B2C | Moderate | Medium (documented in reviews) |
| 7 | Data privacy: files visible to foreign companies | B2C | Moderate | Medium (emerging, especially post-law) |

---

## Jobs-to-Be-Done

**Functional jobs:**
- Store files so they survive if one phone breaks (B2C)
- Keep institution's data in Algerian territory in compliance with Law 11-25 (B2G)
- Provide mobile file access to staff without provisioning server infrastructure (B2G)
- Back up files automatically without manual USB transfers (B2C)

**Social jobs:**
- As DSI: "I am on top of the compliance situation. My institution is protected." (B2G)
- As student: "I am the person in my group who set up something smarter than WhatsApp for our files." (B2C)

**Emotional jobs:**
- Peace of mind: knowing files will survive a phone breaking (B2C)
- Relief from compliance anxiety: not being the person responsible when the audit finds non-compliant storage (B2G)

---

## Language Map

**Words used to describe the problem:**
- B2C: "lost everything," "phone broke," "can't afford," "too expensive," "takes too much data"
- B2G: "non-conforme," "risque juridique," "données qui quittent le territoire," "mise en conformité"

**Words used for desired outcome:**
- B2C: "survive," "safe," "automatic," "private," "free or cheap"
- B2G: "souverain," "conforme," "local," "sans serveur," "maîtrise des données"

**Words used in frustration:**
- B2C: "scam," "lost," "failed," "not found," "too expensive"
- B2G: "trop technique," "pas de référence," "délai trop long," "budget insuffisant"

**[Opinion]** The B2G pitch should use "conformité" and "souveraineté" — the exact words Algerian government uses ("Face aux GAFAM, l'Algérie choisit la maîtrise"). The B2C pitch should use "tes fichiers restent sur ton téléphone" — simple, visceral, visual.

---

## Where to Reach Each Persona

| Persona | Channel | Density | Cost | Priority |
|---|---|---|---|---|
| Karim (DSI) | LinkedIn direct outreach | Medium | Free | High |
| Karim (DSI) | Forum Algérie Numérique / DISTREE Africa | High | Low (event tickets) | High |
| Karim (DSI) | AYRADE partnership (warm introduction to their 10K clients) | Very High | Partnership terms | Highest if partnership secured |
| Karim (DSI) | ANPT cybersecurity working groups | High | Free (invitation) | Medium |
| Yasmine (student) | TikTok — demo video in Darija | 21M users | Free | High |
| Yasmine (student) | WhatsApp/Telegram university groups | Very High density | Free | High |
| Yasmine (student) | Facebook university groups | High | Free | Medium |
| Yasmine (student) | Word-of-mouth in dorms | High (but slow) | Free | Medium (organic flywheel) |

---

## Demand Validation

- **Search demand:** Rising — decentralized storage growing at 14.68% CAGR globally; Algeria-specific search volume data unavailable. [Medium confidence]
- **Competitive activity:** Low in Algeria specifically → unproven market, not a proven one. [Yellow flag]
- **Customer spending:** AYRADE's 117% revenue growth proves institutions are already paying for sovereign storage. The mobile-native gap is unproven. [High confidence on category; Medium on gap]
- **WTP evidence:** Indirect (Spotify/Coursera anchors for B2C). No direct WTP survey for file storage in Algeria. [Low confidence]
- **Overall demand signal: Moderate-High for B2G; Low-Medium for B2C.** The institutional pain is real and legally mandated; consumer demand is inferred but not validated.

---

## Strategic Connections

- The DSI decision path (DSI → RSSI → DG) aligns with the regulatory requirements in `market-analysis.md` — Decree 26-07 mandates a cybersecurity unit which is the RSSI's role.
- The B2C language map ("tes fichiers restent sur ton téléphone") is the inverse of the competitive framing in `competitor-landscape.md` — where Hivenet's failures are precisely about files *not* being where users expect them.
- The AYRADE partnership opportunity in `competitor-landscape.md` directly connects to Karim's need: he already knows AYRADE and trusts them; a MobiCloud-via-AYRADE is a lower-friction sale.

---

## Flags

**Red Flags:**
- No verbatim quotes from real Algerian users were obtained. All B2C persona data is inferred from South African, Nigerian, and general African market studies. The persona is directionally likely but unvalidated.
- B2G buyer behavior is modeled from procurement regulations and general emerging-market B2G patterns. No actual DSI interview was conducted.

**Yellow Flags:**
- The B2C kill criterion (cluster fragility causing week-1 churn) is entirely untested. Yasmine's "peace of mind" job can only be delivered if the cluster is reliably stable — which has not been tested outside a lab.

## Data Gaps
- No verbatim Algerian user quotes (research found indirect analogs only)
- No confirmed institutional IT director interviews
- No validated B2C WTP data for file storage in Algeria
- CERIST capacity and waitlist data unavailable

## Sources
- Wave 3 raw research: `01-discovery/raw/customer-voice.md`, `01-discovery/raw/demand-audience.md`
- Algeria digital statistics: DataReportal 2025 (Tier 2)
- Hivenet review mining: Trustpilot / Play Store (Tier 3)
- Algerian regulatory framing: official government sources (Tier 1)
