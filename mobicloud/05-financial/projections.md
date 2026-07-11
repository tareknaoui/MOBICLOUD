# Projections Financières — MobiCloud

**Phase :** 7 — Financier
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Très faible — aucun chiffre validé par des clients réels. Toutes les projections sont des hypothèses de travail fondées sur la recherche de marché et la logique de modèle économique.

---

## Hypothèses de base

*Toute projection repose sur des hypothèses explicites. En changer une change le résultat.*

| Hypothèse | Valeur retenue | Justification |
|---|---|---|
| Taux de change | 276 DZD / 1 EUR ; 1 USD ≈ 253 DZD | Référence juin 2026 — marché officiel Algérie |
| Prix contrat B2G moyen (base) | 800 000 DZD/an | Milieu de la fourchette 500K–2M DZD |
| Cycle de vente B2G | 6 mois (base) ; 12 mois (conservateur) | Sans partenariat AYRADE |
| Abonnement B2C moyen | 300 DZD/mois | Milieu du range 200–500 DZD |
| Churn mensuel B2C | 15% (base) | Hypothèse — aucune donnée algérienne disponible |
| Coût relay algérien | **Par palier d'usage, pas fixe** : Pilot ~10–15K/mois · Croissance ~25–60K/mois · Scale ~80–300K/mois | Le relay ne stocke rien → coût par trafic, pas par utilisateur. Voir modèle dans `02-strategy/business-model.md` |
| Outillage IA (Claude Max 5×) | ~25 300 DZD/mois (~304K/an) | $100/mois × 253 DZD/USD ; coût de développement, pas d'infrastructure produit |
| Fondateur : salaire Year 1 | 0 DZD | Bootstrapping pur ; Year 2 : SMIG algérien (~45 000 DZD/mois) |
| Relay AT fund accordé | Non inclus dans le scénario de base | Variable externe non contrôlable |

---

## Scénario Conservateur

*Hypothèse : migration relay prend du retard (Mois 4), premier contrat B2G signé à Mois 10, pas de bundle opérateur avant Year 3.*

### Revenus (en DZD)

| Ligne | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| RaaS B2G — nombre de contrats | 0 | 2 | 5 |
| RaaS B2G — revenu | 0 | 1 000 000 | 2 500 000 |
| Abonnement B2C — utilisateurs payants fin d'année | 0 | 100 | 400 |
| Abonnement B2C — revenu annuel | 0 | 162 000 | 648 000 |
| Bundle opérateur | 0 | 0 | 0 |
| **Total Revenus** | **0** | **1 162 000** | **3 148 000** |

*Note : Year 1 = 0 revenu. Le pilot peut être gratuit (valeur = référence, pas cash).*

### Coûts (en DZD)

| Ligne | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| Relay hébergement (palier : Pilot → Croissance) | 150 000 | 300 000 | 480 000 |
| Conformité légale ARPCE/ANPDP (one-time) | 350 000 | 0 | 0 |
| Outillage IA (Claude Max 5×) | 304 000 | 304 000 | 304 000 |
| Salaire fondateur | 0 | 540 000 | 540 000 |
| Développement (fondateur seul) | — | — | — |
| **Total Coûts** | **804 000** | **1 144 000** | **1 324 000** |

### Résultat

| | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| Résultat | **-804 000** | **+18 000** | **+1 824 000** |
| Cash cumulé (si 2M DZD levés au départ) | +1 196 000 | +1 214 000 | +3 038 000 |

**Break-even :** Year 2 (~Mois 22–24). *Claude Max au taux réel (25 300 DZD/mois, calculé sur 1 USD ≈ 253 DZD) creuse légèrement le Year 1 mais le modèle reste viable.*

---

## Scénario Base

*Hypothèse : relay migré à Mois 2, premier contrat B2G signé à Mois 6, partenariat AYRADE accélère le cycle de vente en Year 2, pilot Mobilis initié en Year 3.*

### Revenus (en DZD)

| Ligne | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| RaaS B2G — nombre de contrats | 1 | 4 | 10 |
| RaaS B2G — revenu | 800 000 | 3 200 000 | 8 000 000 |
| Abonnement B2C — utilisateurs payants fin d'année | 0 | 300 | 1 500 |
| Abonnement B2C — revenu annuel | 0 | 486 000 | 2 430 000 |
| Bundle opérateur (pilot Mobilis 1 wilaya) | 0 | 0 | 500 000 |
| **Total Revenus** | **800 000** | **3 686 000** | **10 930 000** |

### Coûts (en DZD)

| Ligne | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| Relay hébergement (palier : Pilot → Croissance → Scale bas) | 150 000 | 420 000 | 960 000 |
| Conformité légale ARPCE/ANPDP (one-time) | 350 000 | 0 | 0 |
| Outillage IA (Claude Max 5×) | 304 000 | 304 000 | 304 000 |
| Salaire fondateur | 0 | 540 000 | 540 000 |
| Recrutement (1 profil commercial Year 2) | 0 | 600 000 | 720 000 |
| **Total Coûts** | **804 000** | **1 864 000** | **2 524 000** |

### Résultat

| | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| Résultat | **-4 000** | **+1 822 000** | **+8 406 000** |
| Cash cumulé (si 2M DZD levés au départ) | +1 996 000 | +3 818 000 | +12 224 000 |

**Break-even :** début Year 2 (~Mois 13–15). *Le premier contrat (800K) et les coûts Year 1 (804K) sont quasi à l'équilibre — le taux de change corrigé décale le break-even de 2 mois vs. l'estimation précédente.*

---

## Scénario Optimiste

*Hypothèse : relay migré Mois 1, fonds AT accordé, partenariat AYRADE signé en Year 1, Mobilis pilot signé en Year 2.*

### Revenus (en DZD)

| Ligne | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| RaaS B2G — nombre de contrats | 3 | 10 | 25 |
| RaaS B2G — revenu | 2 400 000 | 8 000 000 | 20 000 000 |
| Abonnement B2C | 0 | 1 080 000 | 5 400 000 |
| Bundle opérateur | 0 | 1 500 000 | 8 000 000 |
| Fonds Algerie Telecom (grant) | 2 000 000 | 0 | 0 |
| **Total Revenus** | **4 400 000** | **10 580 000** | **33 400 000** |

### Coûts (en DZD)

| Ligne | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| Relay hébergement + scale (palier : Croissance → Scale) | 360 000 | 1 200 000 | 3 000 000 |
| Conformité légale | 350 000 | 100 000 | 100 000 |
| Outillage IA (Claude Max) | 304 000 | 304 000 | 304 000 |
| Équipe (fondateur + 1 commercial + 1 dev) | 0 | 1 260 000 | 1 800 000 |
| Marketing (events Algérie Numérique) | 0 | 300 000 | 600 000 |
| **Total Coûts** | **1 014 000** | **3 164 000** | **5 804 000** |

### Résultat

| | Year 1 | Year 2 | Year 3 |
|---|---|---|---|
| Résultat | **+3 386 000** | **+7 416 000** | **+27 596 000** |

**Break-even :** Year 1 (Mois 6 avec premier contrat + fonds AT). *Au palier Scale (Year 3), le relay atteint 3M DZD/an — mais le revenu correspondant dépasse 33M DZD : le coût d'infrastructure suit le revenu.*

---

## Comparatif des Scénarios (Year 3)

| Indicateur | Conservateur | Base | Optimiste |
|---|---|---|---|
| Revenu total Year 3 | 3,1M DZD | 10,9M DZD | 33,4M DZD |
| Résultat Year 3 | +1,8M DZD | +8,4M DZD | +27,6M DZD |
| Contrats B2G actifs | 5 | 10 | 25 |
| Break-even | ~Mois 22–24 | ~Mois 13–15 | Mois 6 |
| Équivalent EUR Year 3 revenu | ~11K EUR | ~40K EUR | ~121K EUR |

**Le scénario de référence pour le pitch est le scénario Base.** Le conservateur est utilisé pour rassurer (le pire cas reste positif en Year 3). L'optimiste est utilisé pour montrer le potentiel au fonds AT.

---

## Sensibilité — Variables qui changent le plus le résultat

| Variable | Impact si elle varie de ±50% |
|---|---|
| Prix contrat B2G (800K DZD) | ±2M DZD sur le Year 3 base |
| Nombre de contrats B2G Year 2 (4) | ±1,6M DZD sur Year 2 base |
| Coût relay (par palier) | Non linéaire — croît par seuils d'usage, pas par utilisateur. Impact quasi-nul en Year 1 (palier Pilot ~10–15K/mois) ; modéré seulement au palier Scale (Year 3+) où le revenu le couvre |
| Churn B2C (15%) | Impact modéré en Year 2–3, fort en Year 4+ |
| Cycle de vente B2G (6 mois) | Si 12 mois : décale tout le scénario base de 6 mois |

**La variable la plus critique** n'est pas le prix — c'est le cycle de vente B2G. Passer de 6 à 3 mois (via AYRADE) est l'action à plus fort impact financier.

---

## Drapeaux

**Drapeaux Rouges :**
- Le scénario conservateur en Year 1 (0 revenu) nécessite un capital de départ de 1,5–2M DZD pour survivre jusqu'au premier contrat. Ce capital n'est pas encore sécurisé.
- Tous les chiffres B2C sont des hypothèses sans aucune base empirique algérienne. Ne pas les présenter comme des projections — les présenter comme des estimations de potentiel.

**Drapeaux Jaunes :**
- Le revenu "Bundle opérateur" dans le scénario optimiste (1,5M DZD Year 2) suppose un pilot Mobilis signé et actif en Year 2 — ce qui implique que les prérequis (SLA démontré, référence B2G, dossier technique) sont tous réunis avant Mois 12. Très ambitieux pour un fondateur solo.
- Les projections sont en DZD courant (pas ajustées pour l'inflation). L'inflation algérienne est d'environ 5–8% par an — les coûts réels Year 3 seront plus élevés.

## Sources
- `02-strategy/business-model.md` — prix, revenue share, structure de coûts
- `01-discovery/market-analysis.md` — SAM/SOM, benchmarks marché
- `05-financial/unit-economics.md` — LTV/CAC par segment
