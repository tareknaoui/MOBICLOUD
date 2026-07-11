# Plan d'Attaque B2C — MobiCloud

**Phase :** Exécution (post-soutenance) · **Mode :** Attaque
**Projet :** mobicloud · **Date :** 2026-06-22
**Décision :** B2G mis de côté. **B2C = fer de lance maintenant. Opérateur en parallèle** (la traction B2C remplace la référence B2G pour crédibiliser l'opérateur).

---

## 0. Pourquoi le B2C d'abord (la logique)

- **Lançable seul, tout de suite** : pas d'avocat, pas de fonds, pas de cycle DSI de 6-12 mois.
- **Pas de gatekeeper** : tu ne demandes la permission à personne.
- **La traction devient la preuve** : X milliers d'utilisateurs actifs + rétention = l'argument qui ouvre la porte de Mobilis (la traction remplace la référence institutionnelle).
- **Le système de récompense des contributeurs** (`02-strategy/contributor-incentives.md`) devient le **moteur** — c'est ici qu'il sert vraiment.

---

## 1. Ton arme structurelle : la viralité FORCÉE

MobiCloud a une propriété rare : **un utilisateur seul ne peut PAS s'en servir.** L'erasure coding a besoin d'un cluster de 3+ membres. Donc pour avoir son backup, l'utilisateur **doit** amener 2-3 contacts.

C'est à la fois ta friction (cold-start) et ta super-puissance (coefficient viral K > 1 par conception) :

```
  Karim installe → veut son backup → DOIT inviter 3 amis
        │
        ▼
  3 amis installent → chacun veut SON backup → invite 3 amis
        │
        ▼
  9 → 27 → 81 ...   (croissance exponentielle structurelle)
```

**Ne combats pas cette contrainte — weaponise-la.** Le produit qui force l'invitation est le produit qui croît tout seul.

---

## 2. Le déblocage du cold-start : acquérir des GROUPES, pas des individus

Le problème « le 1er utilisateur n'a pas de cluster » se règle en changeant l'unité d'acquisition :

> **N'acquiers pas une personne. Acquiers un groupe déjà soudé.**

Cibles = groupes qui existent déjà et partagent une confiance :
- **Colocataires de résidence universitaire** (4-6 personnes, même chambre/étage)
- **Équipes de projet PFE** (groupes de 3-5)
- **Fratries / familles** (téléphones Android multiples)
- **Groupes d'amis de promo**

Un groupe de 4 colocs qui installe ensemble = **un cluster fonctionnel instantané**. Le cold-start disparaît.

---

## 3. Canaux d'acquisition (concret, coût ≈ 0)

| Canal | Angle / action | Pourquoi ça marche |
|---|---|---|
| **TikTok organique** | Vidéos pain : « ton tél tombe dans l'eau = 3 ans de photos perdues » ; démo : « sauvegarde tes photos sur les téléphones de tes potes, sans Google, sans payer » | Audience étudiante algérienne massive ; le concept P2P est visuellement intriguant |
| **Groupes WhatsApp/Telegram de résidences/facs** | Poster directement dans les groupes ; le groupe **EST** le cluster | Zéro coût, confiance préexistante, ciblage parfait |
| **Ambassadeurs campus** | 1 étudiant/résidence qui onboarde son étage | Distribution physique, démo en personne |
| **Bouche-à-oreille forcé** | L'invitation est dans le produit (§1) | Croissance organique structurelle |

**3 idées de TikTok à tourner cette semaine :**
1. *Le drame* : téléphone cassé/volé, visage paniqué, « j'ai tout perdu »… puis reveal MobiCloud.
2. *L'explication simple* : « Google te fait payer pour stocker tes photos sur SES serveurs. Et si tu les stockais sur les téléphones de tes potes, gratuitement et chiffré ? »
3. *La démo réelle* : 3 téléphones, on en casse un (simulé), le fichier est toujours là. Preuve visuelle.

---

## 4. Plan « Premiers 100 utilisateurs » (semaine par semaine)

| Semaine | Action | Cible |
|---|---|---|
| **S1** | Onboarder 5 groupes de ton réseau direct (colocs, équipes PFE) — installer AVEC eux, en personne | ~20 users / 5 clusters |
| **S2** | 3 TikToks + activer 1 ambassadeur dans 1 résidence | ~50 users |
| **S3** | Itérer sur ce qui retient (voir métriques §6) ; relancer les clusters inactifs | ~75 users |
| **S4** | Doubler le canal qui marche le mieux | **100 users / 25 clusters** |

**Règle d'or :** instrumente TOUT dès le 1er groupe (qui invite qui, qui reste, qui décroche). Les 100 premiers servent à **apprendre**, pas à scaler.

---

## 5. Monétisation B2C (plus tard — pas tout de suite)

Freemium (déjà défini dans `business-model.md`) :

| Tier | Prix | Inclus |
|---|---|---|
| Gratuit | 0 DZD | Cluster jusqu'à 3 membres, 5 Go |
| Standard | 200-300 DZD/mois | Jusqu'à 6 membres, 20 Go |
| Premium | 400-500 DZD/mois | Jusqu'à 10 membres, priorité relais |

- **Paiement :** CCP / Baridimob / recharge opérateur — ⚠️ **À VÉRIFIER** quelles options acceptent réellement un abonnement numérique récurrent en Algérie.
- **Timing :** ne monétise pas avant d'avoir prouvé la **rétention**. Un produit qui ne retient pas ne se monétise pas.

---

## 6. Le vrai risque : le churn (à instrumenter dès J1)

Le danger #1 de tout le projet : **cluster instable → un membre part → ton backup se dégrade → tu pars aussi**. Métriques à suivre :

| Métrique | Définition | Cible | Signal d'alerte |
|---|---|---|---|
| **Activation** | Cluster de 3+ actif sous 48h | >60% | <40% = onboarding cassé |
| **K-factor (viral)** | Invitations acceptées par utilisateur | **>1** | <1 = pas de croissance organique |
| **Rétention J+30** | Utilisateur encore actif à 30 jours | >40% | <25% = cluster trop instable |
| **Stabilité cluster** | % clusters intacts à J+30 | >70% | <50% = problème produit majeur |

**Mitigation churn :** pousser les clusters à **4+ membres** (l'erasure coding + super-peer tolèrent les départs). Un cluster de 3 est fragile ; un cluster de 5 est résilient.

---

## 7. Opérateur en parallèle (front secondaire, en fond)

Tu ne *vends* pas encore à l'opérateur — tu **prépares la preuve** :

- Instrumente les métriques (§6) de façon à ce qu'elles deviennent **directement le pitch Mobilis** : « voici N clusters actifs, X% de rétention, +Y%/mois de croissance ».
- La traction B2C **remplace** la référence B2G : un opérateur signe avec un produit qui a déjà des milliers d'utilisateurs actifs.
- Garde le doc `operator-model.md` prêt — le modèle de revenue-share et le crédit de facture restent valides ; tu y reviens quand la traction est là.

**Déclencheur pour ouvrir le front opérateur :** ~quelques milliers d'utilisateurs actifs + rétention J+30 stable > 40%.

---

## 8. Récap : ce sur quoi tu attaques cette semaine

1. **Onboarder 5 groupes** de ton réseau (en personne, installer avec eux).
2. **Tourner 3 TikToks** (les angles du §3).
3. **Instrumenter** les 4 métriques clés (§6) — sans data, tu pilotes à l'aveugle.
4. **Vérifier** les options de paiement DZD récurrent (pour plus tard).

> Le B2C ne se gagne pas avec un dossier — il se gagne avec des utilisateurs qui restent. Tout le reste découle de la rétention.

## Sources
- `02-strategy/contributor-incentives.md` — moteur de récompense (central en B2C)
- `02-strategy/operator-model.md` — front opérateur (parallèle)
- `02-strategy/business-model.md` — tiers freemium, paiement DZD
- `01-discovery/target-audience.md` — persona B2C, early adopters
