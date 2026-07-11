# Économie Unitaire — MobiCloud

**Phase :** 7 — Financier
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Très faible — toutes les valeurs sont des estimations sans données clients réelles.

---

## Segment B2G — Institutions Algériennes

### CAC (Coût d'Acquisition Client)

Le CAC B2G est principalement du temps fondateur, pas de la dépense monétaire directe.

| Composante | Estimation | Base |
|---|---|---|
| Temps fondateur par deal (prospection + démo + pilot + négociation) | 3–6 mois × 50% du temps | Hypothèse cycle de vente 6 mois |
| Déplacements (1–3 visites sur site) | 10 000–30 000 DZD | Estimation transport Algérie |
| Dossier légal et conformité (one-time, amorti sur N clients) | 350 000 DZD ÷ N clients | N = 10 clients → 35 000 DZD/client |
| **CAC total Year 1 (1 client)** | **~395 000 DZD** | Dominé par la conformité one-time |
| **CAC marginal (clients suivants)** | **~30 000–50 000 DZD** | Sans le coût légal one-time |

*Note : si le partenariat AYRADE fonctionne (accès à 10 000 clients via leur réseau), le CAC marginal tombe à quasi-zéro pour les institutions AYRADE existantes.*

---

### LTV (Lifetime Value)

| Hypothèse | Valeur |
|---|---|
| ACV (prix moyen contrat) | 800 000 DZD/an |
| Durée de rétention estimée | 3 ans (coûts de migration élevés pour l'institution) |
| Churn annuel B2G | 10% [Hypothèse — faible car switching cost élevé] |
| LTV = ACV × (1/churn annuel) × marge brute | 800K × (1/0,10) × 85% ≈ **6 800 000 DZD** |

*La marge brute B2G est élevée : le coût marginal d'un client supplémentaire est quasi nul (relay déjà déployé).*

---

### Ratio LTV / CAC

| Scénario | LTV | CAC | Ratio |
|---|---|---|---|
| Fondateur seul, sans AYRADE | 6 800 000 DZD | 395 000 DZD | **17:1** |
| Via partenariat AYRADE (CAC ~0) | 6 800 000 DZD | ~50 000 DZD | **136:1** |

**Lecture :** Un ratio LTV/CAC > 3:1 est considéré sain en SaaS. Le B2G de MobiCloud est structurellement favorable dès le second client — le coût fixe one-time (conformité) est amorti rapidement.

---

### Break-even B2G (par contrat)

| Coût fixe Year 1 à couvrir | Montant DZD |
|---|---|
| Relay hébergement annuel (palier Pilot) | 150 000 |
| Conformité légale (one-time) | 350 000 |
| Outillage IA Claude Max (12 mois × 25 300) | 304 000 |
| Total à couvrir | **804 000** |

| Prix contrat | Contrats nécessaires pour couvrir les coûts |
|---|---|
| 500 000 DZD/an (petit) | 2 contrats |
| 800 000 DZD/an (moyen) | 1 contrat (couvre quasi exactement — 800K ≈ 804K) |
| 1 500 000 DZD/an (grand) | 1 contrat (marge de +696K) |

**Conclusion :** 1 seul contrat B2G moyen (800K DZD) couvre la quasi-totalité des coûts Year 1. Le modèle est viable à très petite échelle — même avec le coût Claude Max recalculé au taux réel (1 USD ≈ 253 DZD).

---

## Segment B2C — Abonnés Directs

### CAC B2C

| Canal | CAC estimé | Base |
|---|---|---|
| TikTok organique | ~0 DZD | Pas de budget pub |
| WhatsApp/Telegram groupes uni | ~0 DZD | Bouche-à-oreille |
| Référencement par cluster existant | ~0 DZD | Viral structurel (inviter des amis = créer le cluster) |
| **CAC B2C estimé** | **< 500 DZD** | Hypothèse canal organique uniquement |

*Le mécanisme d'invitation au cluster (chaque utilisateur doit inviter 2+ amis pour que le produit fonctionne) crée un effet viral structurel — l'acquisition est organique par nature.*

---

### LTV B2C

| Hypothèse | Valeur | Confiance |
|---|---|---|
| ARPU mensuel | 300 DZD/mois | Faible |
| Churn mensuel | 15% | Très faible — aucune donnée |
| Durée de vie client (1/churn) | 6,7 mois | Calculée |
| LTV = ARPU × (1/churn mensuel) | 300 × 6,7 ≈ **2 000 DZD** | Très faible |

*Un churn de 15%/mois est élevé mais cohérent avec un nouveau produit sans habitude d'utilisation établie. Si le churn tombe à 5%/mois (produit ancré dans la routine), la LTV passe à ~6 000 DZD.*

---

### Ratio LTV / CAC B2C

| Scénario churn | LTV | CAC | Ratio |
|---|---|---|---|
| Churn 15%/mois (lancement) | 2 000 DZD | 500 DZD | **4:1** |
| Churn 5%/mois (produit établi) | 6 000 DZD | 500 DZD | **12:1** |

**Lecture :** Le B2C est viable même avec un churn élevé — à condition que le CAC reste proche de zéro. Si on introduit un budget pub, le modèle peut rapidement devenir non-rentable.

---

## Segment B2B2C — Bundle Opérateur

*L'économie unitaire du bundle opérateur est fondamentalement différente : MobiCloud ne contrôle pas le CAC (c'est l'opérateur qui acquiert) ni le churn (c'est l'opérateur qui rétient).*

### Principe clé : partage à 2 parties, pas 3

Le contributeur de stockage **est** l'abonné (mutualisation : prêter du stockage = recevoir du backup). Il est rétribué **en nature**, jamais en cash. Le revenu se partage donc entre **2 parties seulement** : Opérateur ↔ MobiCloud. Cela préserve la marge et évite de rouvrir le système d'incitation retiré du scope technique. (Détail du modèle : `02-strategy/business-model.md`, Stream 2.)

### Décomposition par abonné actif (bundle à 300 DZD/mois)

| Ligne | Part | Montant DZD | Note |
|---|---|---|---|
| Prix bundle facturé par l'opérateur | 100% | 300 | Mode A revenue-share |
| − Part opérateur | 60% | 180 | Distribution, facturation, marque, réseau, nœuds-ancre |
| = Part MobiCloud | 40% | 120 | |
| − Coût relay (routage, bande passante) | | 5–10 | Quasi nul : le relay ne stocke pas |
| **= Net MobiCloud par abonné actif** | | **~110** | Marge ~92% sur la part reçue |
| Rétribution contributeur | en nature | 0 cash | Son propre backup |
| Crédit de facture (déséquilibre/super-peer) | | 0 pour MobiCloud | Financé par la part opérateur |

### Effet de volume (le levier d'échelle)

| Abonnés actifs | Net MobiCloud/mois | Net MobiCloud/an | Stade |
|---|---|---|---|
| 10 000 (pilot 1 wilaya) | ~1,1M DZD | ~13M DZD | Year 2–3 |
| 100 000 (croissance) | ~11M DZD | ~132M DZD | Year 3–4 |
| 500 000 (maturité) | ~55M DZD | ~660M DZD | Year 5+ |

*Mobilis ~20M abonnés → 1% d'activation = 200 000. Un seul deal opérateur à maturité dépasse tous les contrats B2G cumulés — d'où le statut de levier d'échelle. Mais conditionné à : référence B2G + SLA démontré + relay scalé. Ne pas inclure en scénario conservateur.*

### Mode B — Licence/ARPU (alternative)

Si l'opérateur bundle « gratuit » dans un forfait premium (anti-churn) plutôt que de facturer au détail : MobiCloud reçoit un **fee fixe ~50 DZD/abonné actif/mois**, prévisible, sans risque de volume payant. Souvent préféré par l'opérateur. À 100K abonnés → ~5M DZD/mois garantis.

---

## Comparatif des Segments

| Critère | B2G | B2C | B2B2C Opérateur |
|---|---|---|---|
| CAC | Élevé (time-based) | Quasi-nul | Quasi-nul (porté par opérateur) |
| LTV | Très élevé (6,8M DZD) | Faible (2–6K DZD) | Dépend du volume |
| Ratio LTV/CAC | 17:1 → 136:1 | 4:1 → 12:1 | N/A (pas de CAC propre) |
| Cycle de vente | 6–12 mois | Immédiat | 12–24 mois |
| Risque principal | Cycle long + prérequis relay | Churn élevé si cluster instable | Dépendance opérateur unique |
| Priorité | **1 — Beachhead** | **2 — Parallèle B2G** | **3 — Scale uniquement** |

---

## Métriques de Santé à Surveiller

| Métrique | Cible Year 1 | Signal d'alerte |
|---|---|---|
| Cycle de vente B2G moyen | < 6 mois | > 9 mois = revoir le pitch ou le pricing |
| Churn annuel B2G | < 10% | > 20% = problème de product-market fit institutionnel |
| Rétention B2C J+30 | > 40% | < 25% = cluster trop instable pour le grand public |
| Coût relay / revenu total | < 30% | > 60% = le modèle ne scale pas |
| CAC B2G moyen (time-based) | < 4 mois de temps fondateur | > 8 mois = cycle de vente non viable solo |

---

## Drapeaux

**Drapeaux Rouges :**
- Le churn B2C de 15%/mois est une hypothèse critique non validée. Si le churn réel est de 40%/mois (produit qui ne colle pas), la LTV B2C tombe à 750 DZD — proche du CAC. Le modèle B2C devient marginal.

**Drapeaux Jaunes :**
- La LTV B2G de 6,8M DZD suppose une durée de vie client de 3 ans. Sans renouvellement prouvé (aucun contrat signé), cette valeur est une hypothèse. Ne pas l'utiliser comme argument commercial sans une clause de renouvellement dans le contrat.
- Le revenu B2B2C opérateur est conditionnel à un pilot signé. Ne pas l'inclure dans des projections conservatrices.

## Sources
- `02-strategy/business-model.md` — structure de revenus, prix estimés
- `05-financial/projections.md` — scénarios revenus/coûts
- `01-discovery/target-audience.md` — comportement willingness-to-pay
