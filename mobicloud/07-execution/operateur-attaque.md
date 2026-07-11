# Playbook d'Attaque Opérateur — MobiCloud

**Phase :** Exécution (post-soutenance) · **Mode :** Attaque
**Projet :** mobicloud · **Date :** 2026-06-22
**Focus :** **Opérateur téléphonique uniquement, pour l'instant** (Mobilis en priorité — filiale Algerie Telecom).

> Stratégie & économie déjà rédigées : `02-strategy/operator-model.md` (flux d'argent, revenue-share, crédit facture) et `02-strategy/go-to-market.md` Phase D (chemin d'entrée AT→Mobilis, template de pitch). **Ce doc = le plan d'action d'exécution** : comment passer du prototype à un pilote signé.

---

## 1. Réalité : ce qu'un opérateur exige pour dire oui (et où tu en es)

| Ce que l'opérateur exige | Où tu en es | Écart |
|---|---|---|
| Preuve que ça marche techniquement | Prototype fonctionnel (clusters réels, failover) | ✅ Faible — c'est ton atout |
| Données hébergées sur sol algérien | Relais encore sur Render (US) | ❌ **Bloquant** — Move 0 |
| Preuve de demande / traction | Zéro utilisateur | ❌ Gros écart |
| Sécurité & conformité | Chiffrement AES-256 ; ANPDP non enregistré | ⚠️ Partiel |
| Garanties SLA à l'échelle | Jamais démontré à l'échelle | ⚠️ À cadrer dans le pilote |
| Business case (ARPU / rétention) | Modèle construit (`operator-model.md`) | ✅ Prêt |

**Lecture :** tes deux écarts critiques sont le **relais (sol algérien)** et la **traction**. Les deux ont une parade pas chère (§3).

---

## 2. Pourquoi le jeu est long (et comment ne pas brûler le cycle)

Un deal opérateur prend typiquement **6 à 18 mois**, même bien mené. Ne pitche pas un revenue-share dès le 1er RDV — tu fais peur. Tu déroules une **séquence par paliers**, chacun engageant un peu plus l'opérateur :

```
  RDV exploratoire  →  PoC technique  →  Pilote 1 wilaya  →  Bundle commercial
   (innovation)        (démo réseau)     (3-6 mois, KPI)     (revenue-share/licence)
```

Chaque palier réduit le risque pour l'opérateur. Ton objectif immédiat n'est pas « signer » — c'est **décrocher le 1er RDV et proposer un PoC à risque quasi nul pour eux**.

---

## 3. Combler les 2 écarts critiques (pas cher, sans relancer un front B2C)

**Écart relais (Move 0) :** migrer le relais sur un hébergeur algérien **agréé**. C'est un prérequis de l'opérateur autant que du B2G — il ne routera jamais les données de ses abonnés via un serveur US. → priorité absolue.

**Écart traction → « cohorte de démo » comme munition :** tu n'as pas besoin d'un vrai lancement B2C, juste de **20-50 clusters qui tournent** (amis, étudiants, ta promo) pour entrer en RDV avec **quelque chose de vivant** plutôt qu'une slide. Ce n'est pas un front B2C — c'est de la **munition commerciale opérateur** : « voici 40 clusters actifs, voici la rétention, voici comment ça scale chez vous ». Une démo vivante de-risque massivement le pilote aux yeux de l'opérateur.

> Règle : ne va **jamais** à un RDV opérateur les mains vides. Un prototype + une petite cohorte vivante + un business case = le minimum qui te fait prendre au sérieux.

---

## 4. Chemin d'entrée (qui contacter, dans quel ordre)

1. **Cible n°1 : Mobilis** (filiale Algerie Telecom → mandat cybersécurité/innovation, alignement souveraineté).
2. **Porte d'entrée :** département **Innovation / Partenariats** (pas le commercial grand public, pas le support).
3. **Voies d'accès, par ordre de force :**
   - Intro chaude via l'écosystème **ANPT / incubateur** (Algeria Venture, Sylabs, Cyberparc Sidi Abdallah) — une intro vaut 100 emails froids.
   - Lien **Algerie Telecom** (maison-mère) : événement, forum Algérie Numérique, contact direction innovation.
   - LinkedIn ciblé : responsables Innovation/Digital/Partenariats Mobilis.
4. **Secondaire (si Mobilis traîne) :** Djezzy, Ooredoo — mêmes départements. Ne pas pitcher les 3 en même temps avec les mêmes mots ; Mobilis d'abord pour l'angle souveraineté/AT.

*Template d'email et corps du message déjà rédigés dans `go-to-market.md` Phase D — les réutiliser.*

---

## 5. La proposition de PILOTE (à risque quasi nul pour l'opérateur)

C'est ton arme de closing du 1er palier. Conçois-la pour qu'elle soit **facile à dire oui** :

| Paramètre | Proposition |
|---|---|
| Périmètre | **1 wilaya**, 1 segment (ex : forfaits étudiants) |
| Durée | 3-6 mois, time-boxed |
| Format produit | MobiCloud comme **feature labellisée** dans un forfait (via **Pack Groupe** : règle le besoin de 3+ contacts du même cluster) |
| Coût opérateur | Minimal — tu fournis l'app + le relais ; eux fournissent la distribution + (option) nœuds-ancre |
| KPI de succès | Activation, rétention J+30, uplift ARPU/rétention du segment vs témoin |
| Sortie | Si KPI atteints → négociation bundle (revenue-share ou licence, `operator-model.md`) |

**Le Pack Groupe est central** : il transforme la contrainte technique (cluster = 3+ contacts) en **produit naturel pour l'opérateur** (forfait famille/groupe = ARPU plus élevé). C'est un argument *pour* eux, pas une limite.

---

## 6. Le pitch en RDV : ce qu'on dit / ce qu'on ne dit pas

**Le message (ARPU + différenciation + souveraineté) :**
> « Aucun opérateur algérien n'offre de stockage souverain mobile. C'est une feature différenciante qui retient l'abonné (anti-churn) et justifie un forfait premium. Les données restent en Algérie, chiffrées. On le prouve sur un pilote 1 wilaya, à risque minimal pour vous. »

**Ce qu'on NE dit PAS :**
- Pas de demande de gros chèque au 1er RDV.
- Pas de promesse de SLA qu'on ne peut pas tenir.
- Pas « j'ai zéro utilisateur » — on dit « cohorte pilote active de N clusters » (la munition du §3).
- Pas de jargon technique (Bully, erasure coding) — on parle ARPU, rétention, souveraineté, conformité.

*Détail du template dans `go-to-market.md` Phase D.*

---

## 7. Prérequis qui tiennent toujours (même en mode opérateur)

| Prérequis | Pourquoi | Statut |
|---|---|---|
| **Relais sur sol algérien** (Move 0) | L'opérateur ne route pas via un serveur US | ❌ À faire — priorité |
| **Conformité ANPDP / Loi 11-25** | Données personnelles d'abonnés algériens | ⚠️ Obligatoire — voir `conformite-arpce-anpdp.md` (volet ANPDP) |
| **Cohorte de démo vivante** | Munition de crédibilité (§3) | ❌ À monter |
| **Capacité SLA minimale** | À cadrer/prouver dans le pilote | ⚠️ Pilote |

---

## 8. Ce sur quoi tu attaques cette semaine

1. **Lancer la migration relais** (Move 0) — prérequis dur de l'opérateur. Présélectionner un hébergeur algérien agréé.
2. **Monter une cohorte de démo** : 20-50 clusters parmi tes proches/étudiants — pas pour scaler, pour avoir une démo vivante à montrer.
3. **Chercher l'intro chaude** : lister 3 contacts écosystème (ANPT/incubateur/AT) qui peuvent t'introduire chez Mobilis Innovation.
4. **Préparer le RDV** : packager le prototype + la cohorte + le business case (`operator-model.md`) + la proposition de pilote (§5) en un deck court.

> Le deal opérateur ne se gagne pas en demandant une signature — il se gagne en proposant un pilote tellement peu risqué qu'ils n'ont pas de raison de refuser. Ta tâche : décrocher le RDV et rendre le « oui » facile.

## Sources
- `02-strategy/operator-model.md` — économie du bundle, revenue-share, crédit facture
- `02-strategy/go-to-market.md` Phase D — chemin AT→Mobilis, template email/pitch
- `02-strategy/contributor-incentives.md` — crédit facture opérateur (récompense contributeurs)
- `07-execution/conformite-arpce-anpdp.md` — volet ANPDP (obligatoire)
