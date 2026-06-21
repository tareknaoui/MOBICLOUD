# Brainstorm — MobiCloud Idea Variations

**Phase:** 2 — Brainstorm
**Project:** mobicloud
**Date:** 2026-06-21
**Confidence:** High (founder-validated direction)

---

## Variations Explored

Seven variations were presented across a spectrum from simplest to most ambitious:

| Variation | Core Direction | Verdict |
|---|---|---|
| 1 — Trusted Group Consumer | Original: closed group backup app | Keep as long-term consumer play |
| 2 — B2G First | Skip consumer, sell to institutions | **Selected beachhead** |
| 3 — Relay-as-a-Service | Monetize relay infrastructure, open-source client | **Core asset identified** |
| 4 — Student Document Vault | Vertical narrow for university segment | Useful for early pilots, not standalone |
| 5 — 1:1 Backup Buddy | Strip to minimum 2-person backup | Set aside — prototype already exceeds this |
| 6 — Offline Mesh | Remove relay, use WiFi Direct/Bluetooth | Rejected — WiFi Direct on Android is unreliable by design |
| 7 — African Sovereign Cloud | 10x vision: sovereign cloud continent | North star for narrative, not roadmap |

## Founder's Direction (verbatim synthesis)

**"Tu n'es pas une app company, tu es une infrastructure company."**

The relay is already built, deployed, and operational. It is the only centralized component controlled by MobiCloud that no competitor can replicate without permission. The Android app can be open-source — the relay running on Algerian servers, under Algerian jurisdiction, is the asset.

The institutional B2G path (Variation 2) is the right first customer because:
- No consumer marketing or distribution required
- One pilot contract proves the model
- Data sovereignty is not a selling point — it is a legal obligation for institutions, and the pressure is increasing
- The urgency is on the buyer's side, not the seller's

## Selected Direction — Refined Idea

**Sovereign Relay Infrastructure + Android App, sold to Algerian institutions (B2G first)**

**What MobiCloud sells:**
A relay server hosted in Algeria on Algerian infrastructure (not Render/US) + the Android distributed storage client + support contract + optional subscription tiers for consumers. Patient data / student records / government documents never leave Algerian territory. Foreign cloud providers cannot legally replicate this proposition.

**Technical clarification (corrected from initial brainstorm):**
MobiCloud is NOT an offline/mesh solution. Internet (4G or WiFi) is required for inter-device transfers because the relay handles NAT traversal between devices on different networks. The key distinction is: *data is stored on users' own phones, not on any server* — but transfer requires connectivity. Framing it as "local" or "offline" is inaccurate and would mislead institutional buyers.

**First target customer:** One university or one ministry. A single signed pilot contract (even unpaid) validates the model and finances consumer development next.

**Consumer market:** Secondary. B2G revenue funds the consumer product — attempting the reverse (build consumer base to impress institutions) adds 3 years to the timeline.

**What is set aside:**
- Offline Mesh (WiFi Direct unreliable on Android, removed from project for good reasons)
- Backup Buddy (prototype already exceeds this; starting over costs 6 months for no gain)
- 10x African vision (pitch narrative only, not roadmap)

## Key Tensions Resolved

| Tension | Resolution |
|---|---|
| App company vs. infrastructure company | Infrastructure company. Relay is the moat. |
| Consumer vs. B2G first | B2G first. Consumer funded by first institutional contract. |
| Relay as cost center vs. monetization point | Relay-as-a-Service is the natural revenue model. |
| Algerian jurisdiction (required for B2G) | Relay must move off Render (US) to Algerian hosting immediately. |

## Variation 8 — Bundle Opérateur Téléphonique (B2B2C) *(ajoutée post-gate)*

**L'idée :** Intégrer MobiCloud dans les offres des opérateurs téléphoniques algériens (Djezzy, Ooredoo, Mobilis) sous forme de bundle. Exemple : un client paie 1 500 DZD/mois et obtient 4 000 DZD de crédit d'appel + 50 Go de données + stockage MobiCloud distribué.

**Ce qui est fort :**
- Distribution instantanée à des dizaines de millions d'abonnés sans budget marketing.
- Facturation en DZD déjà gérée par l'opérateur — zéro friction de paiement.
- Mobilis est filiale d'Algerie Telecom, qui gère le fonds de 11M$ cybersécurité/IA — alignement direct.
- Le modèle bundle est prouvé en Afrique (Spotify, YouTube, BeIN Sports déjà bundlés avec des forfaits opérateur).

**Contrainte technique fondamentale :**
Le stockage MobiCloud est distribué sur les téléphones des membres du groupe — un abonné seul sans cluster actif n'a pas de stockage utilisable. "50 Go de MobiCloud" ≠ "50 Go sur un serveur". Nécessite au minimum 3 téléphones en cluster actif.

Solutions possibles :
- **Pack famille/groupe** : vendre le bundle à 3+ abonnés simultanément (pack famille Djezzy). Le groupe forme automatiquement un cluster.
- **Framing feature, pas quota** : l'opérateur inclut "MobiCloud Premium — sauvegarde distribuée avec tes contacts" sans mentionner un quota de Go.

**Ce qui est risqué :**
- C'est du **B2B2C**, pas du B2C direct : négociation opérateur = cycles longs (6–18 mois), SLA, intégration technique, revenue share (l'opérateur prend 30–50 % typiquement).
- L'opérateur peut exiger l'exclusivité réseau.
- Le relay doit être sur infrastructure algérienne avant toute intégration opérateur (même prérequis que B2G).

**Verdict :** Canal B2C à très fort potentiel à moyen terme. À initier après le premier contrat institutionnel B2G (qui valide la fiabilité et finance la capacité à honorer un SLA opérateur). Premier contact logique : **Mobilis** via le lien Algerie Telecom / fonds cybersécurité.

---

## Updated Brief Notes

Le brief d'intake (`00-intake/brief.md`) décrit trois voies de monétisation. Après brainstorm et gate, la direction consolidée est :
- **Primaire :** RaaS (Relay-as-a-Service) + contrat de support pour institutions (B2G)
- **Secondaire :** Bundle opérateur téléphonique via Mobilis/Djezzy/Ooredoo (B2B2C) — après premier contrat B2G
- **Tertiaire :** Abonnement freemium consommateur direct

---

## Flags

**Red Flags:**
- None introduced by this direction that were not already in the intake.

**Yellow Flags:**
- The relay must move off US infrastructure before any institutional B2G conversation — this is a prerequisite, not a future task.
- "Infrastructure company" positioning requires a different sales motion (longer cycles, relationships, procurement) than the current solo technical founder skill set.

## Sources
- Founder direction (June 2026) — direct
