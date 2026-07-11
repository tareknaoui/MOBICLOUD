# Modèle Opérateur (B2B2C) — MobiCloud

**Phase :** 4 — Stratégie · **Slide de présentation mentor**
**Projet :** mobicloud · **Date :** 2026-06-22

> Document de synthèse. Le détail économique complet est dans `business-model.md` (Stream 2) et `05-financial/unit-economics.md`.

---

## La question à laquelle ce modèle répond

> *« Comment MobiCloud gagne de l'argent si l'opérateur prend sa part ET les gens qui contribuent leur stockage doivent aussi avoir la leur ? »*

**Réponse en une phrase :** il n'y a pas trois parts à découper, mais **deux**. Le contributeur est l'abonné lui-même — il est payé **en nature** (son propre backup), pas en cash.

---

## Le flux d'argent

```
              300 DZD/mois  (facture abonné)
                     │
                     ▼
        ┌─────────────────────────┐
        │       OPÉRATEUR         │   garde 180 DZD  (60 %)
        │     (Mobilis ...)       │   → distribution, facturation,
        │                         │     marque, réseau, nœuds-ancre
        └────────────┬────────────┘
                     │  reverse 120 DZD  (40 %)
                     ▼
        ┌─────────────────────────┐
        │        MOBICLOUD        │   − relay (routage) ~10 DZD
        │       (plateforme)      │   ───────────────────────────
        │                         │   = NET  ~110 DZD / abonné
        └────────────┬────────────┘
                     │  fournit le service (app + relay + dispo)
                     ▼
        ┌─────────────────────────┐
        │   ABONNÉ = CONTRIBUTEUR  │   payé EN NATURE :
        │  (prête son stockage)   │   son backup réparti
        │                         │   →  0 DZD cash
        └─────────────────────────┘

   Déséquilibre de contribution / super-peer ?
   → CRÉDIT DE FACTURE, financé par la part opérateur (pas par MobiCloud).
```

---

## Qui prend quoi, et pourquoi

| Partie | Part | Montant | Justification |
|---|---|---|---|
| **Opérateur** | 60 % | 180 DZD | Distribution, facturation, marque, réseau, nœuds-ancre |
| **MobiCloud** | 40 % | 120 DZD | Techno, relay, conformité ARPCE, support |
| **− Coût relay** | | ~10 DZD | Le relay ne stocke rien → routage quasi gratuit |
| **= Net MobiCloud** | | **~110 DZD** | Marge ~92 % sur la part reçue |
| **Contributeur** | en nature | 0 DZD cash | Payé par **son propre backup** |

---

## Pourquoi payer en nature (et pas en cash) est un choix, pas une limite

| | Cloud centralisé (Google Drive) | MobiCloud |
|---|---|---|
| Qui paie le stockage ? | L'éditeur (datacenters) | Les téléphones des abonnés |
| Coût marginal du stockage | Élevé (€/Go/mois) | **≈ 0** |

Payer les contributeurs en cash **détruirait** cet avantage de coût — et rouvrirait le système d'incitation volontairement retiré du scope technique. La mutualisation en nature est ce qui rend MobiCloud structurellement moins cher que Google.

---

## Le levier d'échelle

| Abonnés actifs | Net MobiCloud / mois | Net / an |
|---|---|---|
| 10 000 (pilot 1 wilaya) | ~1,1M DZD | ~13M DZD |
| 100 000 (croissance) | ~11M DZD | ~132M DZD |
| 500 000 (maturité) | ~55M DZD | ~660M DZD |

*Mobilis ~20M abonnés → 1 % d'activation = 200 000. Un seul deal opérateur à maturité dépasse tous les contrats B2G cumulés.*

**Conditionné à :** référence B2G signée + SLA démontré + relay scalé. C'est un levier de Year 3+, pas de Year 1.

---

## Deux modes à arbitrer avec l'opérateur

| | Mode A — Revenue-share | Mode B — Licence / ARPU |
|---|---|---|
| L'abonné paie | 300 DZD explicite | Inclus « gratuit » dans forfait premium |
| MobiCloud reçoit | 40 % du prix | Fee fixe ~50 DZD / abonné actif |
| Intérêt opérateur | Nouveau revenu | Rétention + différenciation (anti-churn) |
| Risque MobiCloud | Dépend du volume payant | Revenu prévisible |

L'opérateur préfère souvent le **Mode B** : pas de facturation au détail, juste une feature qui retient l'abonné.

---

## La phrase à retenir (soutenance)

> **MobiCloud ne paie pas les contributeurs en argent — il les paie en service (leur backup). L'argent se partage à deux : opérateur et plateforme. Les déséquilibres se règlent par crédit de facture, qui coûte presque rien à l'opérateur et n'entame pas la marge.**
