# Go-to-Market — MobiCloud

**Phase :** 4 — Stratégie
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Faible (aucune vente réalisée ; séquence GTM basée sur données de recherche et logique de marché, non validée)

---

## Stratégie de Lancement

**Principe directeur : Ne pas lancer publiquement avant que le relay soit sur infrastructure algérienne.**

Un lancement public avec un relay Render (US) est contre-productif : tout DSI qui posera la question de l'hébergement ("où est votre serveur relay ?") recevra une réponse qui invalide immédiatement la proposition de valeur. Chaque conversation institutionnelle avant la migration est du temps perdu.

**Séquence de lancement en 3 étapes :**

```
Étape 1 : Migration relay (prérequis technique)
    ↓
Étape 2 : Pilot silencieux B2G (1 institution)
    ↓
Étape 3 : Expansion via référence + partenariat AYRADE
```

---

## Plan des 100 Premiers Clients

*Pour MobiCloud, "100 clients" en Year 1-2 signifie : 1-3 institutions B2G + 50-100 utilisateurs B2C beta.*

### Phase A — Avant la vente (Mois 1-3) : Prérequis

| Action | Objectif | Ressources |
|---|---|---|
| Migrer le relay sur hébergeur algérien | Conformité ARPCE Décision 48 — prérequis absolu | Devis auprès d'Algerie Telecom / OVH Algeria / CERIST commercial |
| Déposer un dossier auprès de l'ARPCE | Enregistrement comme opérateur cloud algérien | Avocat spécialisé droit numérique Algérie |
| Contacter AYRADE pour exploratory call | "Nous avons construit une couche mobile-native P2P qui peut compléter votre infrastructure pour vos 10 000 clients" — pitch partenariat, pas concurrence | LinkedIn + email contact presse AYRADE |
| Préparer le dossier conformité | Document prouvant la conformité Law 11-25 + ARPCE à remettre au RSSI lors du pitch | 5-10 pages légales + schéma architecture |

### Phase B — Premier client (Mois 3-6) : Beachhead

| Action | Objectif | Canal |
|---|---|---|
| Identifier 5 DSI via LinkedIn ("DSI Algérie université" "responsable systèmes d'information hôpital algérien") | Pipeline de 5 contacts qualifiés | LinkedIn direct — zéro coût |
| Demander 20 minutes de conversation (pas un rendez-vous commercial) | "Je mène une recherche sur comment les institutions gèrent la conformité Law 11-25" — écouter, pas vendre | Email + LinkedIn message |
| Proposer un pilot gratuit 60 jours à l'institution la plus intéressée | Démontrer la valeur sans risque financier pour l'institution | Contrat pilot simplifié |
| Déployer sur 20-50 utilisateurs réels pendant 60 jours | Valider la rétention et la stabilité cluster en conditions réelles | Sur site ou à distance |
| Convertir en contrat payant gré à gré si pilot concluant | Premier revenu ; première référence | Contrat annuel DZD |

### Phase C — Expansion via référence (Mois 6-18) : Scaling B2G

| Action | Objectif |
|---|---|
| Demander à l'institution pilote une lettre de référence ou une présentation à un collègue DSI | Chaque institution B2G en connaît 5–10 autres ; le réseau DSI Algérie est petit |
| Cibler les institutions dans la même wilaya que le premier pilot | Proximité géographique = crédibilité locale accrue |
| Contacter le Forum Algérie Numérique / DISTREE Africa | Présence événementielle avec référence client |
| Formaliser le partenariat AYRADE si exploratory call concluant | Accès potentiel à 10 000 clients AYRADE avec recommandation interne |

### Phase D — B2B2C Opérateur (Mois 12-24) : Distribution via Mobilis/Djezzy/Ooredoo

**Prérequis avant d'approcher un opérateur :**
Un opérateur algérien ne signe jamais avec un produit sans trois preuves minimales. Ces prérequis doivent être réunis avant d'initier tout contact commercial.

| Prérequis | Indicateur prouvable |
|---|---|
| Relay sur sol algérien | Certificat d'hébergement algérien + test fonctionnel |
| Au moins 1 contrat B2G signé et actif | Lettre de référence ou nom de l'institution (selon confidentialité) |
| SLA démontré sur 60 jours | Rapport uptime cluster sur la période pilot |
| Base utilisateurs beta active | Nombre de clusters actifs, rétention J+30 |

---

**Contrainte du cluster — Solution "Pack Groupe"**

Le principal obstacle au bundle opérateur : MobiCloud nécessite au minimum 3 appareils dans le même cluster pour fonctionner. Un abonné solo ne peut pas utiliser le service seul.

Cette contrainte devient un argument commercial si on la recadre correctement :

| Recadrage | Argument pour l'opérateur |
|---|---|
| "Pack Famille" (3+ abonnés famille) | L'activation du service MobiCloud incite à migrer les membres de la famille vers le même opérateur — rétention + recrutement de nouveaux abonnés |
| "Pack Groupe Étudiant" (résidence universitaire, groupe de TP) | Segment 18-28 ans avec fort potentiel de fidélisation long terme — l'opérateur capte un segment avant la concurrence |
| "Pack Pro PME" (équipe de 3-10 employés) | Extension naturelle du B2B2C vers les PME — valeur plus élevée par cluster |

**Ne pas présenter la contrainte comme une limite technique — la présenter comme le mécanisme qui crée de la valeur pour l'opérateur** : chaque activation MobiCloud est une raison pour 3 personnes de rester (ou de venir) sur le même réseau.

---

**Chemin d'entrée Mobilis (prioritaire)**

Mobilis est une filiale d'Algerie Telecom — la même entité qui gère le fonds cybersécurité/IA de 11M$. Cette double porte est l'avantage structurel de MobiCloud sur les autres opérateurs.

```
Algerie Telecom
├── Fonds cybersécurité/IA (11M$) ← dossier de financement (Phase A, Mois 2-4)
│   └── si financement accordé → légitimité interne AT
└── Mobilis (filiale) ← négociation bundle (Phase D, Mois 12+)
    └── le financement AT devient un argument de crédibilité dans le pitch Mobilis
```

**Séquence recommandée :**
1. Déposer dossier fonds AT (Mois 2-4) — indépendamment du bundle
2. Si financement accordé → citer l'appui AT dans le pitch Mobilis
3. Si financement refusé → approcher Mobilis via le département Innovation/Partenariats (pas Commercial) avec la référence B2G et le dossier technique

**Chemin d'entrée Djezzy / Ooredoo (secondaire, Mois 18+)**

Ces deux opérateurs n'ont pas de lien capitalistique avec MobiCloud. L'approche est purement commerciale : montrer qu'un concurrent (Mobilis) discute déjà avec MobiCloud — ou a déjà signé — crée une pression concurrentielle naturelle.

---

**Actions Phase D :**

| Action | Objectif | Timing |
|---|---|---|
| Déposer dossier fonds Algerie Telecom (cybersécurité/IA) | Financement + légitimité interne AT → porte Mobilis | Mois 2-4 (en parallèle des prérequis) |
| Lancer beta B2C via TikTok/WhatsApp universités | Construire base utilisateurs actifs à montrer à l'opérateur | Mois 6-12 |
| Préparer dossier technique intégration opérateur (API relay, SLA, capacity) | Montrer la maturité avant le premier rendez-vous | Mois 10-11 |
| Premier contact Mobilis — département Innovation/Partenariats | Pitch : "feature différenciante pour vos bundles premium" | Mois 12 |
| Présenter métriques beta + référence B2G + dossier technique | Convaincre de lancer un pilot bundle sur 1 wilaya | Mois 13-15 |
| Pilot bundle Mobilis (1 wilaya, 3-6 mois) | Valider l'activation, le taux d'utilisation, le revenue share réel | Mois 15-18 |
| Extension ou signature contrat national | Revenu récurrent à l'échelle | Mois 18-24 |

---

## Canaux de Croissance — Classés par Impact / Coût

| Rang | Canal | Impact Estimé | Coût | Timing |
|---|---|---|---|---|
| 1 | **Partenariat AYRADE** | Très élevé (10 000 institutions accessibles) | Négociation (temps) | Mois 1 (exploratory) |
| 2 | **LinkedIn DSI outreach** | Élevé (pipeline qualifié direct) | Nul | Mois 2–3 |
| 3 | **Fonds Algerie Telecom 11M$ (cybersécurité/IA)** | Élevé (financement + légitimité) | Dossier (temps) | Mois 2–4 |
| 4 | **Forum Algérie Numérique / DISTREE Africa** | Moyen (notoriété B2G) | Faible (accréditation étudiant possible) | Mois 6+ |
| 5 | **Mobilis bundle** | Très élevé si signé (millions d'abonnés) | Long (négociation 6–18 mois) | Mois 12+ |
| 6 | **TikTok organique** | Moyen (B2C) | Nul | En parallèle |
| 7 | **Bouche-à-oreille / WhatsApp groupes universitaires** | Faible à moyen (B2C) | Nul | En parallèle |
| 8 | **BOMOP appel d'offres** | Élevé si gagné | Long (12–24 mois) | Année 2+ |

**Ne pas faire :** campagnes payantes Google/Meta, SEO content en anglais, Product Hunt — aucun de ces canaux n'atteint les DSI algériens ni les étudiants algériens de manière efficace et rapide.

---

## Jalons (Timeline)

| Jalon | Timing | Indicateur de succès |
|---|---|---|
| Relay migré sur infrastructure algérienne | Mois 1–2 | Certificat hébergement algérien + test fonctionnel |
| Enregistrement ARPCE initié | Mois 2 | Dossier déposé |
| Exploratory call AYRADE | Mois 2 | Réunion tenue, retour documenté |
| 5 DSI contactés (pas pitch — conversation) | Mois 3 | 5 réponses obtenues |
| 1 pilot déployé (20–50 utilisateurs, 60 jours) | Mois 4–5 | Pilot signé et lancé |
| Métriques pilot validées (uptime >99%, rétention J+30 >40%) | Mois 6 | Kill criterion : si <40% J+30, revoir architecture avant scale |
| Premier contrat institutionnel payant signé | Mois 6–9 | Contrat signé en DZD |
| Dossier fonds Algerie Telecom déposé | Mois 3–4 | Dossier soumis |
| 3 institutions pilotes simultanées | Mois 12 | 3 contrats actifs |
| Négociation Mobilis initiée | Mois 12 | Premier contact établi |
| 50–100 utilisateurs B2C beta actifs | Mois 6–12 | Taux de rétention J+30 mesuré |

---

## Pitch d'Approche DSI (Template)

**Objet LinkedIn/email :**
> *"Conformité Law 11-25 pour le stockage mobile de votre institution — 20 minutes ?"*

**Corps du message :**
> *"Bonjour [Prénom], je développe MobiCloud, une solution de stockage mobile-natif hébergée en Algérie qui aide les institutions publiques à migrer hors de Google Drive sans provisionner de serveur. Je contacte des DSI d'universités/hôpitaux algériens pour comprendre comment vous gérez la conformité Law 11-25. Ce n'est pas un rendez-vous commercial — je cherche à comprendre le problème réel avant de valider ma solution. Avez-vous 20 minutes cette semaine ?"*

**Ne pas mentionner** : l'académique, le PFE, "startup", prix. Mentionner : la loi, le problème concret, la solution en une phrase.

---

## Pitch d'Approche Opérateur (Template Mobilis)

**À qui s'adresser :** Directeur Innovation ou Responsable Partenariats — pas la direction commerciale (qui gère des fournisseurs, pas des partenariats technologiques).

**Objet email / LinkedIn :**
> *"Stockage souverain mobile en bundle — opportunité de différenciation pour Mobilis"*

**Corps du message :**
> *"Bonjour [Prénom], je développe MobiCloud, une solution de stockage distribué mobile-native hébergée en Algérie — les fichiers des abonnés sont stockés sur leurs téléphones, chiffrés, et survivent à la perte d'un appareil.*
>
> *J'ai une question concrète pour vous : est-ce qu'un bundle "stockage souverain mobile" — activable en groupe de 3 abonnés Mobilis, sans serveur propre à Mobilis, sans coût d'infrastructure — vous semble être un argument de différenciation pertinent face à Djezzy et Ooredoo ?*
>
> *Je ne cherche pas encore à signer quoi que ce soit — je valide si le problème est réel du côté opérateur avant de construire l'intégration. 30 minutes cette semaine ?"*

**Ce qu'on apporte au premier rendez-vous :**
1. Référence client institutionnel B2G (preuve que le produit est en production)
2. Métriques de rétention beta B2C (preuve que les abonnés utilisent le service)
3. Schéma d'architecture relay (preuve que Mobilis n'a rien à héberger)
4. Proposition de pilot limité (1 wilaya, 6 mois, revenue share à définir ensemble)

**Ce qu'on ne dit pas au premier rendez-vous :**
- Le prix (trop tôt — laisser l'opérateur ancrer sa valeur perçue)
- La contrainte des 3 téléphones minimum (la présenter comme "Pack Groupe" uniquement si la question se pose)
- "On est une startup" (dire : "on est l'équipe technique derrière MobiCloud, déployé chez [institution]")

**Argument principal pour l'opérateur (ARPU + rétention) :**
> *"Chaque cluster MobiCloud actif représente 3 abonnés qui ont une raison fonctionnelle d'être sur le même réseau. Ce n'est pas juste du stockage — c'est un mécanisme de rétention groupée."*

---

## Drapeaux

**Drapeaux Rouges :**
- La totalité de ce GTM est hypothétique. Aucune conversation DSI n'a encore eu lieu. La première conversation réelle peut invalider plusieurs hypothèses (urgence de conformité, budget disponible, décision gré à gré possible).
- Le partenariat AYRADE est l'élément le plus stratégique du GTM — et le moins contrôlable. AYRADE peut refuser, ignorer, ou demander des conditions défavorables.

**Drapeaux Jaunes :**
- Le cycle de vente B2G de 12–24 mois signifie que même si la première conversation DSI a lieu en Mois 3, le premier revenu peut n'arriver qu'en Mois 15. Le fondateur a besoin de financement (fonds Algerie Telecom) ou d'une activité génératrice de revenus en parallèle pour tenir.
- Le template de pitch DSI est une hypothèse sur ce qui résonne — il doit être testé et itéré après les 5 premières conversations.

## Sources
- `01-discovery/target-audience.md` — profil DSI, canaux d'acquisition
- `01-discovery/competitor-landscape.md` — AYRADE comme partenaire stratégique
- `01-discovery/market-analysis.md` — cycle gré à gré, BOMOP
- `01-discovery/industry-trends.md` — fonds Algerie Telecom, Forum Algérie Numérique
