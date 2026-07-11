# Modèle Économique — MobiCloud

**Phase :** 4 — Stratégie
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Faible à Moyenne (aucun chiffre validé par des clients réels — toutes les projections sont des hypothèses)

---

## Modèle de Revenus

MobiCloud est une **entreprise d'infrastructure**, pas une application. La distinction est stratégique : le relay (l'infrastructure centralisée contrôlée par MobiCloud) est le point de monétisation naturel, pas l'application Android (qui peut être open-source).

### Stream 1 — RaaS B2G (Relay-as-a-Service pour Institutions)

**Ce qu'on vend :** Un contrat annuel incluant :
- Le relay WebSocket hébergé sur infrastructure algérienne (conforme ARPCE + Law 11-25)
- L'application Android déployée pour les membres de l'institution
- Le support technique et la documentation de conformité ANPDP/ARPCE
- SLA d'uptime (à définir — 99% suggéré)

**Prix indicatif :** 500 000 – 2 000 000 DZD/an selon la taille de l'institution et le nombre d'utilisateurs [Estimation — aucun benchmark Algeria B2G SaaS disponible]

| Taille institution | Utilisateurs estimés | Prix suggéré |
|---|---|---|
| Petite (faculté, clinique) | 50–200 | 500K DZD/an |
| Moyenne (université, hôpital régional) | 200–1 000 | 1M DZD/an |
| Grande (ministère, CHU) | 1 000+ | 2M DZD/an + |

**Conditions :** Contrat gré à gré sous le seuil d'appel d'offres (~3M DZD en Algérie) pour les premières ventes. Au-delà, appel d'offres BOMOP.

**Renouvellement :** Le coût de migration pour l'institution est élevé (re-certification, re-déploiement, ré-audit) → churn faible une fois déployé. [Opinion]

---

### Stream 2 — Bundle Opérateur Téléphonique (B2B2C) ★ Levier d'échelle

**Ce qu'on vend :** Un accord de distribution avec Mobilis/Djezzy/Ooredoo pour inclure MobiCloud dans leurs forfaits grand public.

**Modèle :** Revenue share par abonné actif.
- L'opérateur intègre MobiCloud comme feature dans ses bundles premium.
- MobiCloud reçoit X DZD/mois par abonné actif utilisant le service.
- Revenue share typique en Afrique : opérateur garde 50–70%, MobiCloud reçoit 30–50%. [Estimation, benchmarks services bundlés Afrique]

**Contrainte technique :** Le bundle "stockage" nécessite que l'abonné ait des contacts dans le même cluster. L'opérateur peut résoudre cela via des packs famille/groupe.

**Prérequis :** Premier contrat B2G signé + relay algérien opérationnel + SLA démontré avant toute négociation opérateur. Un opérateur ne signera jamais avec un produit sans référence client et sans infrastructure locale.

---

#### Comment MobiCloud gagne de l'argent quand l'opérateur ET les contributeurs prennent leur part

**Le piège : compter 3 bouches alors qu'il n'y en a que 2.**

Le réflexe est de raisonner comme un marché ouvert type Storj/Filecoin : l'opérateur prend sa part, MobiCloud prend la sienne, et « les gens qui contribuent leur stockage » réclament la leur — trois mains tendues sur le même revenu. **Ce n'est pas l'architecture de MobiCloud.**

Dans le modèle MobiCloud, **le contributeur EST l'abonné**. Karim prête 10 Go de son téléphone → Karim reçoit 10 Go de sauvegarde répartie dans son cluster. Yasmine fait de même. Personne ne paie personne en cash : ils **échangent un service (mutualisation / troc)**. La rétribution du contributeur, c'est **son propre backup**.

> **Conséquence :** il n'y a pas de troisième part à découper. Le partage monétaire est à **2 parties** : Opérateur ↔ MobiCloud. Le contributeur est payé **en nature**.

**Pourquoi c'est l'avantage économique structurel (et pas un détail) :**

| | Cloud centralisé (Google Drive) | MobiCloud |
|---|---|---|
| Qui paie le stockage ? | L'éditeur (datacenters) | Les téléphones des abonnés |
| Coût marginal du stockage | Élevé (€/Go/mois) | **≈ 0** |
| Coût relay MobiCloud | — | Routage seul, quasi nul |

Le stockage coûte **zéro** à MobiCloud, car les abonnés le fournissent. Payer les contributeurs en cash détruirait précisément cet avantage — et rouvrirait le système d'incitation/Karma **volontairement retiré du scope technique**. La mutualisation en nature garde ce scope fermé. *Formulation défendable : « la contribution n'est pas un marché monétisé, c'est une mutualisation entre membres d'un cluster. »*

**Le flux d'argent concret** (mode revenue-share, bundle à 300 DZD/mois) :

| Partie | Part | Montant | Justification de la part |
|---|---|---|---|
| **Opérateur** | 60 % | 180 DZD | Distribution, facturation, marque, réseau, support N1, nœuds-ancre |
| **MobiCloud** | 40 % | 120 DZD | Techno, relay, conformité ARPCE, support N2 |
| **Contributeur** | en nature | 0 DZD cash | Payé par **son propre backup** |
| − Coût relay MobiCloud | | ~5–10 DZD | Bande passante de routage |
| **= Net MobiCloud** | | **~110 DZD/abonné actif/mois** | |

**Le déséquilibre de contribution — le vrai problème — se règle sans cash.** La mutualisation pure suppose que chacun donne autant qu'il prend. Or certains ont un grand téléphone toujours allumé (bons nœuds), d'autres un petit souvent éteint. Solution : **le crédit de facture opérateur**. Celui qui contribue plus que sa part reçoit un crédit data/minutes sur sa facture mobile.
- Le crédit **coûte presque rien à l'opérateur** (coût réel d'un crédit data ≪ valeur faciale).
- Il **sort de la part opérateur**, pas de la marge MobiCloud.
- Il transforme « prêter mon stockage » en « baisser ma facture » → incitation forte, partenariat opérateur approfondi.

**Cas du super-peer :** le membre dont le téléphone joue le rôle de super-peer porte une charge supérieure (batterie, bande passante). C'est le **seul** cas justifiant une récompense ciblée — un crédit « super-peer fiable ». Incitation **bornée**, qui ne rouvre pas toute l'économie de tokens.

**Cold-start (dispo au démarrage) :** tant qu'il n'y a pas assez de pairs en ligne, l'opérateur fournit quelques **nœuds-ancre** toujours allumés (edge nodes de son réseau ou petite allocation cloud) comme plancher de disponibilité. Le coût de ce plancher fait partie de ce qui justifie sa part de 60 %.

**Deux modes de monétisation à arbitrer avec l'opérateur :**

| | Mode A — Revenue-share | Mode B — Licence / ARPU |
|---|---|---|
| L'abonné paie | 300 DZD explicite | Inclus « gratuit » dans forfait premium |
| MobiCloud reçoit | 40 % du prix | Fee fixe/abonné actif (~50 DZD) |
| Intérêt opérateur | Nouveau revenu | Rétention + différenciation (anti-churn) |
| Risque MobiCloud | Dépend du volume payant | Revenu prévisible |

L'opérateur **préfère souvent le Mode B** : il ne veut pas facturer au détail, il veut une feature qui retient l'abonné et justifie un forfait plus cher. À mettre en avant dans le pitch Mobilis.

**Synthèse défendable :** *MobiCloud ne paie pas les contributeurs en argent — il les paie en service (leur backup). L'argent se partage à deux : opérateur et plateforme. Les déséquilibres de contribution se règlent par crédit de facture, qui coûte presque rien à l'opérateur et n'entame pas la marge MobiCloud.*

---

### Stream 3 — Abonnement B2C Direct (Freemium)

**Modèle :**

| Tier | Prix | Inclus | Limites |
|---|---|---|---|
| Gratuit | 0 DZD | App Android + accès relay + cluster jusqu'à 3 membres | 5 Go de quota total par cluster |
| Standard | 200–300 DZD/mois | Clusters jusqu'à 6 membres | 20 Go par cluster |
| Premium | 400–500 DZD/mois | Clusters jusqu'à 10 membres + priorité relay | Quota illimité (limité par espace disque des membres) |

**Friction de paiement :** Paiement via CCP (La Poste Algérienne), Baridimob, ou recharge opérateur — pas de carte internationale requise. [Opinion — mécanisme à valider avec les vraies options de paiement disponibles]

---

## Économie Unitaire

**[Estimation — Hypothèse niveau A (assumption-based) : aucun client réel]**

### B2G

| Métrique | Estimation | Confiance |
|---|---|---|
| ACV (Average Contract Value) | 500K–2M DZD/an (~1 800–7 200 EUR) | Faible |
| CAC | Très élevé (12–24 mois de cycle de vente + temps fondateur) | Faible |
| LTV | 3–5 ans de contrat si satisfaction → 1.5M–10M DZD par client | Très faible |
| Churn | Faible une fois déployé (coûts de migration) | Très faible |
| LTV/CAC | Favorable à long terme si cycle ramené à 6 mois via partenariat AYRADE | Hypothèse |

**Hypothèse critique :** Le cycle de vente B2G de 12–24 mois est la principale menace sur l'économie unitaire. Si ramené à 3–6 mois via partenariat AYRADE ou un DSI champion, le modèle devient viable rapidement.

### B2B2C (Opérateur)

| Métrique | Estimation | Confiance |
|---|---|---|
| Revenue/abonné actif | 60–150 DZD/mois (30–50% d'un bundle à 200–300 DZD) | Très faible |
| Volume potentiel | Mobilis : 20M+ abonnés, taux d'activation estimé 1–5% | Très faible |
| CAC | Proche de zéro (distribution par l'opérateur) | Faible |
| Risque principal | Exclusivité opérateur, SLA impossible à tenir à l'échelle avant traction | Élevé |

### B2C Direct

| Métrique | Estimation | Confiance |
|---|---|---|
| CAC | ~0 DZD (TikTok/WhatsApp organique) | Faible |
| ARPU (Average Revenue Per User) | 200–500 DZD/mois | Très faible |
| LTV | 12 mois × ARPU × (1 - churn) — churn inconnu | Très faible |
| Churn | Inconnu — dépend de la stabilité cluster en conditions réelles | DATA GAP |

---

## Scalabilité

**Le relay est le goulot d'étranglement et le levier d'échelle :**

- Un seul relay peut théoriquement gérer des milliers de clusters simultanément (il ne stocke pas les données, il route uniquement du trafic chiffré).
- Le coût marginal d'ajouter un nouveau cluster est essentiellement nul une fois le relay opérationnel.
- **Limite actuelle :** Le relay est une instance unique (Render, US). Migration vers infrastructure algérienne = prérequis avant toute scale.
- **Limite à moyen terme :** Si l'adoption croît (bundle opérateur), le relay devra être scalé horizontalement (plusieurs instances) avec un store partagé pour éviter le split-brain (incident documenté en mai 2026). [Données, historique projet]

### Modèle de Coût du Relay — Par Palier d'Usage

**Point clé : le coût du relay ne se calcule PAS par utilisateur.** Le relay ne stocke aucune donnée — il ne route que du trafic chiffré en transit. Son coût dépend donc du **trafic simultané** (bande passante + connexions concurrentes + nombre d'instances pour la haute disponibilité), pas du volume de données stockées ni linéairement du nombre d'utilisateurs.

Conséquence : le coût croît **par paliers** (par seuils), pas en continu. Entre deux seuils, ajouter un utilisateur coûte ≈ 0. C'est l'avantage économique structurel de l'architecture vs. un cloud centralisé (où le coût croît avec chaque Go stocké).

| Palier | Usage typique | Infrastructure relay | Coût estimé [Estimation] |
|---|---|---|---|
| **Pilot** | 1–3 institutions, ~20–200 utilisateurs actifs, 1–10 clusters | 1 petit VPS algérien | **8 000–15 000 DZD/mois** (~100K–180K DZD/an) |
| **Croissance B2G** | 4–15 institutions, ~500–3 000 utilisateurs, 10–80 clusters | 1 VPS moyen + 1 instance de secours (HA) | **25 000–60 000 DZD/mois** (~300K–720K DZD/an) |
| **Scale Opérateur** | Bundle opérateur, 10K–200K+ utilisateurs, 1 000+ clusters | Plusieurs instances + store partagé (Redis) anti split-brain | **80 000–300 000 DZD/mois** (~1M–3,6M DZD/an) |

**Lecture stratégique :** En Year 1 (palier Pilot), le relay coûte ~10–15K DZD/mois — pas 80K. Le palier à 80K+ ne survient qu'au stade bundle opérateur, où le revenu correspondant (millions d'abonnés) couvre largement le coût. **Le coût d'infrastructure suit le revenu, il ne le précède pas.**

**App Android :** Open-source possible → réduit les coûts de distribution, augmente la confiance des institutions (audit du code), facilite les intégrations opérateur. La valeur est dans le relay et le support, pas dans l'app.

### Coûts d'Outillage (Développement)

À distinguer des coûts du produit : l'outillage que le fondateur utilise pour *construire* MobiCloud ne scale pas avec le nombre de clients. Poste principal : abonnement IA de développement (Claude Max). Voir `05-financial/projections.md`.

| Poste | Coût | Nature |
|---|---|---|
| Claude Max (assistant IA de dev) | 5× : ~25 300 DZD/mois (~304K/an) · 20× : ~50 600 DZD/mois (~607K/an) | Outillage — remplace une partie du coût d'un dev junior (~45K DZD/mois) — base : $100/mois × 253 DZD/USD (1 EUR = 276 DZD) |

*Argument fondateur solo : ~162K DZD/an d'outillage IA permet de tenir le rôle d'une équipe technique et réduit le besoin de financement initial. Confirmer le plan réellement souscrit (5× ou 20×).*

---

## Dépendances et Partenariats Clés

| Partenaire | Rôle | Priorité | Statut |
|---|---|---|---|
| **Hébergeur algérien** (Algerie Telecom, OVH Algeria, CERIST commercial, datacenter local) | Héberger le relay sur sol algérien — prérequis absolu #1 | Critique | Non initié |
| **ARPCE** | Enregistrement comme opérateur cloud pour conformité Décision 48 | Critique | Non initié |
| **AYRADE** | Distribution B2G : accès à 10 000 institutions clientes ; crédibilité marché | Stratégique | Non initié |
| **Mobilis / Algerie Telecom** | Distribution B2B2C bundle + lien fonds cybersécurité 11M$ | Important | Non initié |
| **ANPDP** | Enregistrement comme sous-traitant de données personnelles (Law 11-25) | Important | Non initié |
| **Avocat spécialisé droit numérique Algérie** | Validation du montage légal avant première vente B2G | Nécessaire | Non initié |

---

## Drapeaux

**Drapeaux Rouges :**
- Aucun chiffre de ce document n'est validé par des clients réels. Traiter toutes les projections comme des hypothèses de travail, pas comme des prévisions.
- La dépendance à un partenariat AYRADE pour accélérer le cycle B2G est une hypothèse stratégique non testée. AYRADE pourrait refuser ou demander des conditions désavantageuses.

**Drapeaux Jaunes :**
- Le modèle B2B2C bundle opérateur nécessite un SLA enterprise que le produit ne peut pas encore tenir. Ne pas initier les négociations avant le premier contrat B2G signé et le relay scalé.
- La monétisation DZD via CCP/Baridimob est un mécanisme non validé — les options de paiement réelles pour un abonnement numérique en Algérie doivent être vérifiées.

## Sources
- `01-discovery/market-analysis.md` — benchmarks économie unitaire, SAM/SOM
- `01-discovery/competitor-landscape.md` — données AYRADE, revenue share opérateurs africains
- `01-discovery/target-audience.md` — willingness to pay, comportement paiement DZD
- Historique projet MobiCloud (incident split-brain relay, mai 2026) — Données internes
