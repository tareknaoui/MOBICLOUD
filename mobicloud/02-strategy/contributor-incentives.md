# Récompense des Contributeurs — MobiCloud

**Phase :** 4 — Stratégie
**Projet :** mobicloud
**Date :** 2026-06-22
**Confiance :** Moyenne (mécanique structurelle solide ; calibrage des récompenses à valider)

> **Pourquoi ce document existe :** les contributeurs (les téléphones qui prêtent leur stockage) **sont** l'infrastructure de MobiCloud. Sans eux, il n'y a pas de stockage, donc pas de produit. Les récompenser n'est pas un bonus — c'est une condition d'existence. Ce document recense **tous** les leviers de récompense et propose un système en couches.

---

## 1. Le principe directeur

Un réseau de stockage a besoin de **plusieurs comportements** distincts, pas d'un seul :

| Comportement nécessaire | Pourquoi vital |
|---|---|
| Fournir de l'espace disque | Sans espace, rien à stocker |
| Rester en ligne (uptime) | Données récupérables à tout moment |
| Durer dans le temps (anti-churn) | Stabilité du cluster |
| Tenir le rôle super-peer | Coordination & disponibilité du cluster |
| Recruter d'autres contributeurs | Croissance du pool de stockage |

**Une récompense n'a de valeur que si elle déclenche le bon comportement.** Payer « pour contribuer » en général est inefficace ; récompenser *chaque* comportement utile est efficace.

---

## 2. Le socle structurel : le ratio give:take (la porte)

Avant toute récompense, un mécanisme **garantit** que l'offre de stockage égale la demande. Il s'appuie sur la logique **tracker BitTorrent-style** déjà présente dans l'architecture.

On mesure pour chaque membre :

```
        stockage qu'il fournit aux autres
  R  =  ──────────────────────────────────
        stockage qu'il consomme pour lui
```

- **Règle (la porte) :** pour garder son backup actif, un membre doit maintenir **R ≥ 1** — il donne au moins autant qu'il prend.
- **Conséquence :** l'offre égale **structurellement** la demande. Aucun passager clandestin. Le réseau ne peut pas tomber en pénurie de stockage.
- **Vérification légère (actionnable) :** comptabilité d'octets assignés + ping de vivacité périodique (le nœud détient-il toujours le bloc ?). Le cluster connaît déjà le placement des blocs.
- **Vérification forte (perspective) :** Proof of Retrievability cryptographique anti-triche — *travail futur, hors scope MVP.*

> Les récompenses ci-dessous ne servent qu'à attirer le **surplus** (R > 1), qui donne la marge de redondance et de durabilité.

---

## 3. Catalogue complet des leviers de récompense

### A — En nature (stockage) · coût MobiCloud ≈ 0

| # | Levier | Ce que reçoit le contributeur |
|---|---|---|
| 1 | Réciprocité de base | Son backup réparti chiffré |
| 2 | Quota asymétrique | Donne 50 Go → débloque 50 Go de backup (au lieu de 5) |
| 3 | Intérêt d'ancienneté | Quota bonus qui s'accumule avec la durée de contribution |
| 4 | Durabilité renforcée | Ses données protégées par plus de redondance (m plus élevé) |
| 5 | Restauration prioritaire | Reconstruction plus rapide quand il récupère ses données |

### B — Statut & features · coût ≈ 0

| # | Levier | Ce que reçoit le contributeur |
|---|---|---|
| 6 | Premium gratuit | Features payantes offertes aux gros contributeurs |
| 7 | Score de fiabilité / badge | « Nœud fiable », « Super-peer certifié » |
| 8 | Gamification | Niveaux, séries (streaks), classement uptime |
| 9 | Droits sociaux du cluster | Admin du cluster, nommer le cluster |

### C — Quasi-cash · payé par un tiers, pas par MobiCloud

| # | Levier | Ce que reçoit le contributeur | Qui paie |
|---|---|---|---|
| 10 | **Crédit de facture opérateur** | Data/minutes sur sa facture mobile | Opérateur |
| 11 | Bonus super-peer | Data offerte quand il tient le rôle super-peer | Opérateur |
| 12 | Réduction de son propre abonnement | Contribue → Premium moins cher ou gratuit | Marge produit |
| 13 | Perks partenaires | Réductions e-commerce / accessoires télécom | Partenaires |

### D — Monétaire · rouvre le scope technique (perspective)

| # | Levier | Ce que reçoit le contributeur | Coût |
|---|---|---|---|
| 14 | Micropaiements cash | De l'argent réel (modèle Storj/Filecoin) | Élevé + Proof of Storage requis |
| 15 | Pool de revenue-share | % du revenu réparti selon contribution | Élevé + ledger requis |
| 16 | Parrainage rémunéré | Bonus cash pour amener d'autres contributeurs | Moyen |

### E — Réduire le coût de contribuer (= récompense indirecte) · coût 0

| # | Levier | Effet |
|---|---|---|
| 17 | WiFi-only / charge-only / espace-libre-only | Contribuer ne coûte rien de ressenti → la réciprocité suffit à attirer |

### F — Intrinsèque · coût 0

| # | Levier | Effet |
|---|---|---|
| 18 | Souveraineté / fierté civique | « Je garde les données algériennes en Algérie » |
| 19 | Aider ses proches | Le cluster, ce sont des gens qu'il connaît |

---

## 4. Mapping comportement → récompense

| Comportement | Récompense(s) qui le déclenche(nt) |
|---|---|
| Fournir de l'espace | Quota asymétrique (#2), ratio give:take, durabilité (#4) |
| Rester en ligne (uptime) | Score fiabilité (#7), crédit super-peer (#11) |
| Durer (anti-churn) | Intérêt d'ancienneté (#3), Premium gratuit (#6) |
| Tenir le rôle super-peer | Crédit facture renforcé (#11), statut certifié (#7) |
| Recruter d'autres contributeurs | Parrainage (#16) |

---

## 5. Le système recommandé — en 5 couches

De la plus sûre (coût nul, actionnable) à la plus risquée (perspective) :

| Couche | Contenu | Rôle | Coût MobiCloud | Statut |
|---|---|---|---|---|
| **1. Porte** | Ratio give:take ≥ 1 | Garantit l'offre = demande | 0 | Actionnable |
| **2. Surplus en nature** | Quota asymétrique + durabilité + restauration prioritaire (#2,4,5) | Attire le surplus de stockage | 0 | Actionnable |
| **3. Felt reward** | Crédit facture opérateur + Premium gratuit (#10,12) | Donne la sensation d'être « payé » | 0 / faible | Actionnable (après deal opérateur) |
| **4. Engagement** | Badges, gamification, souveraineté (#7,8,18) | Rétention & fierté | ~0 | Actionnable |
| **5. Monétaire** | Cash / revenue-share (#14,15) | Récompense maximale | Élevé | **Perspective — hors scope MVP** |

**Lecture :** les couches 1–4 récompensent réellement les contributeurs **sans coût pour MobiCloud et sans rouvrir le scope technique**. La couche 5 (cash) est documentée comme travail futur car elle impose un Proof of Storage et une gestion de paiements.

---

## 6. Discipline de scope (important pour la soutenance)

| Élément | Statut |
|---|---|
| Ratio give:take (comptabilité légère) | Actionnable — pas de nouveau protocole lourd |
| Récompenses en nature / statut / quasi-cash | Actionnable — UX & partenariats, pas de crypto |
| Proof of Retrievability cryptographique | **Perspective** — travail futur |
| Micropaiements cash / token | **Perspective** — change la nature du produit |

> La plupart des récompenses ne nécessitent **aucun** nouveau protocole : ce sont des règles de quota, d'UX et de partenariat. Seul le palier cash exige la machinerie (Proof of Storage, ledger) volontairement gardée en perspective.

---

## 7. Le cas B2G : le problème n'existe pas (encore)

Dans le **beachhead B2G** (priorité, MVP), l'attraction des contributeurs **ne se pose pas** : quand une institution déploie MobiCloud, la DSI **impose** l'app sur les appareils du service. Les contributeurs sont **fournis par le mandat institutionnel**, pas à séduire.

L'attraction des contributeurs est un problème **B2C / opérateur** (Year 2–3). Il n'a pas besoin d'être résolu pour le MVP ni la soutenance — mais ce document montre qu'il **a une réponse construite** quand il se posera.

---

## 8. Synthèse défendable

> **Les contributeurs sont l'infrastructure, donc ils sont récompensés à chaque comportement utile.** Le socle est un ratio give:take qui garantit l'offre sans cash. Par-dessus, un surplus en nature (quota, durabilité), une sensation de paiement (crédit facture opérateur, Premium gratuit) et de l'engagement (statut, souveraineté). Le cash reste une perspective car il imposerait un Proof of Storage hors scope. Résultat : les contributeurs sont réellement payés — en valeur, pas forcément en argent — sans détruire l'avantage de coût qui rend MobiCloud moins cher que Google.

## Sources
- `02-strategy/business-model.md` — Stream 2, partage opérateur
- `02-strategy/operator-model.md` — flux d'argent B2B2C
- `05-financial/unit-economics.md` — segment opérateur
- Architecture MobiCloud — tracker BitTorrent-style, topologie super-peer (données internes)
