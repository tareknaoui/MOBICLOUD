# Économie Détaillée du Modèle Opérateur — MobiCloud

**Phase :** Exécution (post-soutenance) · **Mode :** Attaque
**Projet :** mobicloud · **Date :** 2026-06-22
**Taux :** 1 EUR = 276 DZD · 1 USD ≈ 253 DZD
**Objet :** Comment gagner de l'argent avec l'opérateur, en tenant compte de TOUS les revenus, TOUS les coûts, et de la rémunération des contributeurs. Toutes les possibilités.

> ⚠️ Tous les chiffres sont des **[Estimation]** — aucun n'est validé par un contrat réel. Ce sont des hypothèses de travail pour décider.

---

## 0. Clarification de vocabulaire (sinon tout se mélange)

| Terme | Qui c'est | Ce qu'il fait |
|---|---|---|
| **Abonné / client** | L'utilisateur Mobilis qui active MobiCloud | Paie (via sa facture opérateur) ET prête du stockage |
| **Contributeur** | **Le MÊME abonné** | Prête l'espace de son téléphone au cluster |
| **Opérateur** | Mobilis | Distribue, facture, prend sa part |
| **MobiCloud** | Toi / la plateforme | Fournit l'app + le relais + le support |

**Point fondamental : l'abonné ET le contributeur sont la même personne.** Il n'y a pas un « client » qui paie et un « contributeur » séparé à rémunérer. C'est ce qui change toute l'économie.

---

## 1. La démonstration : pourquoi payer les contributeurs en CASH est impossible

### 1.1 Un téléphone vaut des centimes en stockage

| Référence | Valeur |
|---|---|
| Prix de gros du stockage cloud | ~0,005–0,015 USD / Go / mois |
| Ce que Storj paie ses fournisseurs de stockage | ~1,5 USD / **To** / mois = 0,0015 USD / Go / mois |
| Un contributeur prête (téléphone) | ~20–50 Go |
| **Valeur marché de sa contribution** | 50 Go × 0,0015 USD = **0,075 USD/mois ≈ 19 DZD/mois** |

→ Au **prix juste du marché**, payer un contributeur de téléphone rapporte **~20 DZD/mois**. Insignifiant. Ça ne motive personne.

→ Pour que ce soit motivant (disons 200 DZD/mois), il faudrait payer **10× le prix du cloud** — économiquement suicidaire, et c'est *plus* que ce que l'abonné te rapporte.

**Conclusion : le cash est à la fois trop cher pour toi ET trop faible pour le contributeur. Double perte.**

### 1.2 Le cash se DILUE avec l'échelle ; le non-cash, NON

C'est le point décisif :

- **Récompense cash :** tu as un pool d'argent à partager entre *tous* les contributeurs. Or *tout le monde* contribue. Plus il y a de membres, plus la part de chacun **diminue**. Dans un cluster de 5, un pool de 12 DZD/abonné = ~2,4 DZD par personne. Ridicule.
- **Récompense en nature (backup) :** chacun reçoit **son** backup, **quel que soit** le nombre de contributeurs. Ça ne se dilue **jamais**. Plus il y a de monde, mieux c'est (plus de redondance).

> La récompense en nature passe à l'échelle. La récompense cash s'effondre à l'échelle. **C'est structurel, pas une opinion.**

---

## 2. Tous les modèles de revenus avec l'opérateur

| # | Modèle | Mécanique | MobiCloud reçoit | Pour / Contre |
|---|---|---|---|---|
| **1** | **Revenue-share** | % du prix du bundle par abonné actif | 30–50% du prix | + gros upside · − dépend du volume payant |
| **2** | **Licence par utilisateur actif** | Fee fixe/abonné/mois, opérateur bundle « gratuit » | ~50 DZD/abonné | + prévisible, anti-churn · − upside plafonné |
| **3** | **Licence plateforme forfaitaire** | Montant fixe annuel quel que soit le nb d'users | X M DZD/an | + ultra-prévisible · − ne capte pas la croissance |
| **4** | **White-label** | Opérateur rebrande (« Mobilis Cloud ») | Licence + per-user | + gros contrat · − tu perds la marque |
| **5** | **Setup + récurrent (hybride)** | Frais d'intégration one-time + per-user | Setup + récurrent | + cash immédiat · − négociation plus lourde |
| **6** | **Freemium via opérateur** | Tier gratuit bundlé ; opérateur prend une part **seulement** sur le premium | % du premium | + adoption massive · − monétisation lente |
| **7** | **Revenue-share sur ARPU uplift** | % de la hausse d'ARPU mesurée vs témoin | % de l'uplift | + aligné sur la valeur réelle · − difficile à mesurer |

**Recommandation :** viser le **#2 (licence par user)** ou le **#5 (setup + récurrent)** pour le premier deal — prévisibles, faciles à dire oui pour l'opérateur. Garder le **#1 (revenue-share)** pour quand la valeur est prouvée (plus d'upside mais plus risqué).

---

## 3. Structure de coûts complète (toutes les dépenses)

| Poste | Nature | Pilot | Croissance | Scale |
|---|---|---|---|---|
| **Stockage des données** | — | **0** | **0** | **0** (fourni par les téléphones) |
| Relais (hébergement + bande passante) | Variable par palier | 150–300K/an | 0,7–1M/an | 3,6M/an |
| Relais HA (instances + Redis anti split-brain) | Step | inclus | +0,5M/an | inclus scale |
| Outillage IA (Claude Max) | Fixe | 304K/an | 304K/an | 304K/an |
| Intégration opérateur (technique, billing) | One-time | 0,5–2M | — | — |
| Support N2 (N1 = opérateur) | Variable | faible | 2M/an | 12M/an |
| Équipe | Variable | fondateur (0) | 3–4 pers. ~6M/an | 10–15 pers. ~30M/an |
| Conformité ANPDP | One-time + maj | 0,4M | maj | maj |
| **Récompense contributeurs (cash)** | **À éviter** | voir §1 | voir §1 | voir §1 |

**Le poste qui change tout : le stockage coûte 0.** Contrairement à un cloud (qui paie chaque Go), MobiCloud ne stocke rien côté serveur. Le relais ne fait que **router**. C'est pourquoi les marges sont énormes (§5).

---

## 4. Waterfall par abonné actif (4 hypothèses de paiement contributeur)

Bundle à **300 DZD/mois**, revenue-share 60/40 :

| Ligne | A. En nature | B. Cash depuis ta part | C. Crédit via opérateur | D. Cash « motivant » |
|---|---|---|---|---|
| Prix bundle | 300 | 300 | 300 | 300 |
| − Part opérateur (60%) | 180 | 180 | 150* | 180 |
| = Part MobiCloud | 120 | 120 | 150* | 120 |
| − Coûts MobiCloud (relais, support) | 10 | 10 | 10 | 10 |
| − Cash aux contributeurs | 0 | 30 | 0 (payé par op.) | 200 |
| **= NET MobiCloud** | **110** | **80** | **140** | **−90 ❌** |
| Récompense ressentie par contributeur | Son backup | ~quelques DZD (dilué) | Crédit facture visible | 200 DZD |

\* En option C, l'opérateur réduit sa part de 30 DZD pour financer un crédit de facture (qui lui coûte ~10 DZD réel) ; ça **augmente** ta part nette car l'opérateur absorbe la récompense.

**Lecture :**
- **A (en nature)** : net 110, contributeur payé en backup. Le modèle par défaut. ✅
- **B (cash depuis ta part)** : net 80, et le contributeur touche des miettes diluées. Tu perds 27% de marge pour rien. ❌
- **C (crédit opérateur)** : le **meilleur** — le contributeur sent une vraie récompense, et c'est l'opérateur qui paie. ✅✅
- **D (cash motivant)** : tu perds de l'argent sur chaque abonné. Faillite garantie. ❌❌

---

## 5. P&L à 3 échelles (steady-state, Mode revenue-share, net ~110 DZD/abonné/mois)

| | 50 000 abonnés | 200 000 abonnés | 500 000 abonnés |
|---|---|---|---|
| **Revenu/an** | 66M DZD | 264M DZD | 660M DZD |
| − Relais (+ HA) | 1,5M | 3M | 3,6M |
| − Outillage IA | 0,3M | 0,3M | 0,3M |
| − Support N2 | 2M | 6M | 12M |
| − Équipe | 6M | 18M | 30M |
| − Divers (conformité, marketing) | 1M | 3M | 6M |
| **Total coûts** | ~10,8M | ~30,3M | ~51,9M |
| **NET/an** | **~55M DZD** | **~234M DZD** | **~608M DZD** |
| **Marge** | **83%** | **89%** | **92%** |

**La marge AUGMENTE avec l'échelle** (relais et outillage quasi-fixes, revenu linéaire). C'est l'avantage du stockage à coût 0. *Mobilis ~20M abonnés → 200K = seulement 1% d'activation.*

**Mode B (licence ~50 DZD/abonné, opérateur bundle gratuit) :** revenu plus faible (50K → 30M/an) mais **prévisible et sans risque de volume payant**. Marges similaires. Souvent préféré pour le premier deal.

---

## 6. Comment récompenser les contributeurs SANS casser le modèle (chiffré)

Les leviers, du meilleur ROI au pire, avec leur coût réel pour MobiCloud :

| Levier | Coût MobiCloud/abonné | Récompense ressentie | Verdict |
|---|---|---|---|
| Backup (réciprocité) | 0 | Forte (sa donnée survit) | ✅ Socle |
| Quota bonus (donne+ → backup+) | 0 | Moyenne-forte | ✅ |
| Statut / badge / gamification | ~0 | Moyenne | ✅ |
| **Crédit facture opérateur** | **0 (payé par op.)** | **Forte (facture baisse)** | ✅✅ Le levier cash-like |
| Réduction de son abonnement | faible | Moyenne | ⚠️ ronge le revenu |
| Cash micropaiement | élevé | **Faible** (dilué, §1) | ❌ |
| Revenue-share pool | élevé | Faible (dilué) | ❌ |

**La réponse à « il faut les payer » : oui — mais en backup, en quota, en statut, et surtout en CRÉDIT DE FACTURE OPÉRATEUR.** Le crédit de facture est ce qui ressemble le plus à « être payé » (l'abonné voit sa facture baisser), tout en coûtant **zéro** à MobiCloud (c'est l'opérateur qui l'absorbe, à coût marginal quasi nul pour lui).

---

## 7. Les leviers pour MAXIMISER le gain

1. **Choisir le bon modèle de prix** : licence/setup d'abord (prévisible), revenue-share quand la valeur est prouvée (upside).
2. **Pousser le Pack Groupe** : forfait famille/groupe = ARPU plus élevé pour l'opérateur + résout le besoin de 3+ contacts. Argument *pour* eux.
3. **Faire financer les récompenses par l'opérateur** (crédit facture) : ta marge reste intacte.
4. **Garder le relais au bon palier** : ne pas surdimensionner ; le coût suit l'usage.
5. **Rester solo le plus longtemps possible** : l'équipe est ton plus gros coût ; l'outillage IA repousse le moment de recruter.
6. **Viser le taux d'activation, pas le nombre d'abonnés bruts** : 1% → 5% d'activation = ×5 sur le revenu sans changer le deal.

---

## 8. Synthèse

- **Tu gagnes** parce que le stockage te coûte **0** (les téléphones le fournissent) et le relais ne fait que router → marges 83–92%.
- **Le partage est à 2** (opérateur ↔ MobiCloud). Le contributeur est l'abonné, payé **en nature**.
- **Payer les contributeurs en cash est mathématiquement absurde** : un téléphone vaut ~20 DZD/mois de stockage, et le cash se dilue à l'échelle alors que le backup, non.
- **La vraie « paie » du contributeur** = backup + quota + statut + **crédit de facture financé par l'opérateur** (coût 0 pour toi).
- À 200 000 abonnés actifs (1% de Mobilis), le modèle dégage **~234M DZD/an net**. Le gain est réel — à condition de prouver d'abord que le produit retient (le risque, lui, est sur la rétention, pas sur l'économie).

## Sources
- `02-strategy/operator-model.md` — flux d'argent de base
- `02-strategy/contributor-incentives.md` — système de récompense 5 couches
- `05-financial/unit-economics.md` — segment B2B2C
- `07-execution/operateur-attaque.md` — playbook d'engagement
