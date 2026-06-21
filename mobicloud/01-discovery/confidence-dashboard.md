# Confidence Dashboard

**Phase:** 3 — Research Synthesis
**Project:** mobicloud
**Date:** 2026-06-21
**Purpose:** Meta-layer — tells the founder where the research rests on solid ground vs. thin ice. Read this before making decisions based on the other 4 discovery documents.

---

## Overview

The research drew on 6 agents across 3 waves (Wave 4 skipped — single-country market). Source quality is strongest for regulatory findings (Tier 1 government documents), moderate for competitor intelligence (Tier 2 press/Crunchbase), and weakest for consumer demand validation (Tier 3 proxies from South Africa/Nigeria — not Algeria). The B2G thesis rests on solid legal ground; the B2C thesis rests on inference.

---

## Claim-Level Confidence Table

| Claim | Source Tier | Corroborating Sources | Confidence | Data Age |
|---|---|---|---|---|
| ARPCE Decision 48 requires cloud operators to host on Algerian territory | 1 | 2 (ARPCE + CMS legal guide) | **High** | 2017 (operative) |
| Law 11-25 creates criminal liability for unauthorized cross-border data transfer | 1 | 3 (DPA, APA News, CookieYes) | **High** | July 2025 |
| Decree 26-07 mandates cybersecurity units in all public institutions | 1 | 2 (TechAfrica News, government gazette) | **High** | January 2026 |
| No hyperscaler has Algerian-territory infrastructure as of June 2026 | 2 | 2 (Wave 1 research + Wave 2 GTM research) | **High** | June 2026 |
| AYRADE has 10,000+ institutional clients and IPO'd June 2026 | 2 | 2 (investor materials + press) | **High** | June 2026 |
| AYRADE revenue grew 117% YoY | 2 | 1 (investor materials) | **Medium** | 2025 data |
| Hivenet has documented reliability failures (silent upload, "file not found") | 3 | Multiple (Trustpilot + Play Store reviews) | **High** (for the claim; Tier 3 source) | 2025-2026 |
| Hivenet raised €12M Series A | 2 | 1 (Crunchbase) | **Medium** | 2024-2025 |
| Africa public cloud market $15.55B (2025), 23.3% CAGR | 2 | 2 (Statista + Grand View Research) | **Medium** | 2025 |
| Algeria IT services total ~$1.9B | 2 | 1 (IDC proxy) | **Medium** | 2025 |
| Algeria cloud storage market $80M–$150M | Derived | 0 (calculated estimate) | **Low** | Derived |
| B2G SAM: $1.2M–$3.0M ARR at ceiling penetration | Derived | 0 (founder calculation from Wave 1) | **Low** | Derived |
| SOM Year 3: $200K–$400K | Derived | 0 (comparable company trajectory) | **Low** | Projected |
| Algerian startups raised $4.1B in 2025 | 2 | 1 (African tech press) | **Medium** | 2025 |
| $11M Algerie Telecom fund targets cybersecurity/AI | 2 | 1 (press release) | **Medium** | 2025 |
| B2G sales cycle: 12–24 months | 2 | 2 (US Commercial Service + Algeria procurement research) | **Medium** | 2024-2025 |
| TikTok has 21.1M Algerian users | 2 | 1 (DataReportal 2025) | **Medium** | 2025 |
| Consumer CAC near zero via TikTok/WhatsApp organic | 3 | 1 (inferred from channel density) | **Low** | 2025 |
| B2C WTP: 200–500 DZD/month | Derived | 0 (Spotify/Coursera anchors as proxy) | **Low** | 2025 |
| No consumer verbatim Algerian user quotes obtained | N/A | N/A | **Verified gap** | June 2026 |
| IEEE paper confirms MobiCloud's research gap | 1 | 1 (MDPI / IEEE Xplore) | **High** | 2024-2025 |
| DZD billing eliminates most foreign competitors (no EUR/USD card access) | 2 | 2 (payment behavior data + platform research) | **Medium** | 2025 |

---

## Highest Confidence Findings

These claims are backed by Tier 1 sources with multiple corroborating signals. Build strategy on these.

1. **The legal moat is real and recent.** ARPCE Decision 48 + Law 11-25 + Decree 26-07 collectively prohibit Algerian public institutions from using non-compliant foreign cloud storage. These are enforcement instruments, not policy statements. Criminal liability provisions (1-5 years prison) make the risk real for institution directors. [Multiple Tier 1 sources, 2017-2026]

2. **No hyperscaler will compete in Algeria for 18–36 months.** No Algerian territory infrastructure announced by Google, Microsoft, or AWS as of June 2026. The window is real. [Tier 2, cross-referenced across multiple sources]

3. **AYRADE is both the primary threat and the primary partnership opportunity.** They are real, funded, growing, and hold the institutional client relationships MobiCloud needs. Their product gap (no mobile-native offering) is the entry point. [Tier 2, well-documented]

4. **Hivenet's documented failures are the exact problem MobiCloud's erasure coding solves.** Silent upload failures and "file not found" errors are the result of not having cryptographically verifiable fragment integrity. RS(2,1) + Android Keystore directly addresses this. [Tier 3 source for reviews, but patterns are consistent across multiple independent reviewers]

5. **MobiCloud's technical approach has academic validation.** The IEEE paper confirms the mobile-native + intermittent-connectivity gap that MobiCloud fills is a genuine research contribution, not an incremental feature. [Tier 1, 2024-2025]

---

## Lowest Confidence Findings

These claims are estimates, proxies, or single-source. Do not make irreversible decisions based on them without further validation.

1. **All B2C consumer demand data is from South Africa and Nigeria, not Algeria.** File loss frequency, WTP levels, and adoption behaviors are extrapolated from comparable markets. Algeria may be meaningfully different.

2. **SAM and SOM figures are calculated estimates, not market research.** The $1.2M–$3.0M B2G SAM ceiling is derived from institutional counts × estimated ACV. No published Algeria B2G SaaS benchmark exists to validate the ACV range.

3. **Consumer WTP at 200-500 DZD/month is a proxy estimate.** Spotify and Coursera pricing are used as anchors. Users may not perceive file backup as having similar value to entertainment or education subscriptions. This needs a direct willingness-to-pay experiment.

4. **B2G sales cycle of 12–24 months** is modeled from general Algeria procurement research. The actual cycle for a gré à gré pilot under tender threshold may be shorter (3–6 months) if an internal champion is secured. Or longer if the institution has no compliance urgency.

5. **DZD billing as structural moat.** This is accurate today but could change if Algerian payment infrastructure modernizes or if foreign competitors launch DZD-denominated payments.

---

## Critical Unknowns

Things the research could not answer that could materially change the strategy:

| Unknown | Why It Matters | How to Find Out |
|---|---|---|
| Is AYRADE planning a mobile product? | If yes, MobiCloud's B2G gap closes. | Follow AYRADE investor communications; reach out to AYRADE for partnership exploratory call. |
| Can the relay be hosted on Algerian infrastructure for a viable cost? | Relay migration is a prerequisite for all B2G sales. | Get quotes from local Algerian hosting providers (Algerie Telecom, CERIST commercial, Djezzy). |
| What does a real Algerian DSI think of MobiCloud's pitch? | All B2G validation is inferred, not tested. | 5 DSI interviews (university, hospital, ministry — at least one of each). |
| Do real users retain the app after week 1? | The kill criterion. | Deploy to 1 dorm group (10+ users) for 30 days and measure daily active rate. |
| Can Algerian institutions pay via DZD bank transfer, not credit card? | Affects revenue collection mechanism, not just pricing. | Ask an Algerian startup that has sold to institutions how they handle payment. |
| What is AYRADE's pricing for comparable services? | Needed to anchor MobiCloud's institutional pricing. | Request a quote from AYRADE as a prospective buyer. |

---

## Recommendations — What to Verify First

**Week 1 (before anything else):**
1. Get a quote from an Algerian hosting provider for relay server costs (Algerie Telecom, OVH Algeria if available, local data center). This determines whether the relay migration is economically viable.
2. Find one contact inside an Algerian university IT department (LinkedIn: search "DSI Algérie université"). Request a 20-minute conversation, not a sales call.

**Week 2-4:**
3. Run the 30-day dorm group test (10+ users, real phones, real files). Measure retention. This tests the kill criterion directly.
4. Reach out to AYRADE for a partnership exploratory call. Frame it as "we've built a mobile-native distributed backup layer that could complement AYRADE's infrastructure for mobile field access."

**Month 2:**
5. Request 5 formal DSI interviews using the customer discovery protocol (Phase 3.7). Ask specifically: "Are you under compliance pressure from Law 11-25?" and "What would it take for you to deploy a mobile storage solution?"

---

## Flags

**Red Flags:**
- The B2C thesis has zero verbatim data from Algerian users. It is built entirely on proxy markets. Do not invest in consumer marketing before 30-day real-user test confirms retention.
- All SAM/SOM/financial estimates are low-confidence. Do not present them as forecasts; present them as hypotheses to validate.

**Yellow Flags:**
- AYRADE's roadmap is unknown. The partnership window may be shorter than 18-36 months if they are already building mobile access internally.

## Sources
- All 6 Wave 1-3 raw research files in `01-discovery/raw/`
- See individual discovery files for per-claim source citations
