# Conformité Légale ARPCE + ANPDP — Feuille de Route

**Phase :** Exécution (post-soutenance) · **Mode :** Attaque
**Projet :** mobicloud · **Date :** 2026-06-22
**Objectif :** Rendre MobiCloud **légalement vendable** au secteur public algérien.

> 🅿️ **PARTIELLEMENT DORMANT (2026-06-22)** — Le B2G est mis de côté « en ce moment ». Le volet **ARPCE** (hébergement) reste pertinent pour le B2C. Le volet **ANPDP / Loi 11-25** (protection des données personnelles) **reste obligatoire même en B2C** dès qu'il y a des utilisateurs algériens. Front actif : **B2C + opérateur** → voir `b2c-attaque.md`.

> ⚠️ **À VÉRIFIER auprès des autorités (réel, non inventé ici) :** formulaires exacts, frais, délais de traitement, pièces précises, adresses des guichets. **Comment vérifier :** (1) site officiel **ARPCE** (arpce.dz), (2) site officiel **ANPDP** (Autorité nationale de protection des données personnelles), (3) **un avocat spécialisé en droit du numérique algérien** (poste budgété — c'est lui qui sécurise le montage), (4) le texte intégral de la **Loi 11-25** et de la **Décision ARPCE 48 (2017)**. Cette feuille de route prépare la **stratégie et la séquence** ; les **modalités exactes** se confirment à ces sources.

---

## 1. Pourquoi c'est le verrou de toute vente B2G

Une institution publique ne signera **jamais** si MobiCloud n'est pas en règle avec :
1. l'**hébergement des données sur le territoire** (ARPCE / Décision 48),
2. le **traitement des données personnelles** (Loi 11-25 / ANPDP).

Sans ces deux briques, le pitch « souveraineté » est creux et le DSI est juridiquement exposé s'il te choisit. **La conformité n'est pas de l'administratif — c'est ton argument de vente.**

---

## 2. Deux obligations distinctes (ne pas les confondre)

| | ARPCE | ANPDP |
|---|---|---|
| Cadre | Décision 48 (2017) + statut opérateur cloud/hébergement | Loi 11-25 (juillet 2025), protection des données personnelles |
| Ce que ça régit | **Où** sont hébergées les données (sol algérien) + statut pour fournir un service | **Comment** sont traitées les données personnelles |
| Rôle de MobiCloud | Fournisseur d'un service d'hébergement/relais | **Sous-traitant** de données pour le compte de l'institution (responsable de traitement) |
| Livrable | Enregistrement/déclaration + hébergement conforme | Enregistrement comme sous-traitant + garanties techniques |

---

## 3. Volet ARPCE — hébergement & statut

**Ce que ça impose (principe) :** les données traitées pour des entités algériennes doivent être hébergées sur le territoire national ; fournir un service de cloud/hébergement peut nécessiter un enregistrement/une autorisation.

**Avantage MobiCloud :** l'architecture **aide** la conformité — les données ne sont pas centralisées sur un serveur étranger, elles restent sur les appareils des membres en Algérie, et le relais (qui ne stocke rien, ne fait que router) sera hébergé sur sol algérien.

**Parcours (à confirmer ARPCE) :**
- [ ] Lire le texte de la Décision 48 + identifier le régime applicable à un relais qui **ne stocke pas** (argument : MobiCloud n'est pas un hébergeur de données au sens classique — à faire qualifier par l'avocat). ⚠️ À VÉRIFIER
- [ ] Déterminer si un statut/enregistrement opérateur est requis ou si l'hébergement chez un DC algérien déjà agréé suffit. ⚠️ À VÉRIFIER
- [ ] Choisir l'hébergeur algérien (lié au Move 0 relais) parmi les options déjà identifiées (Algerie Telecom / DC local agréé). 
- [ ] Constituer le dossier ARPCE si enregistrement requis.

---

## 4. Volet ANPDP — données personnelles (Loi 11-25)

**Ce que ça impose (principe) :** quiconque traite des données personnelles d'individus en Algérie doit respecter la Loi 11-25 ; un **sous-traitant** doit offrir des garanties et, selon les cas, être enregistré/déclaré auprès de l'ANPDP.

**Position MobiCloud :** **sous-traitant** (l'institution = responsable de traitement). MobiCloud fournit les **garanties techniques** : chiffrement AES-256 côté appareil, données fragmentées, MobiCloud ne peut **pas** lire les contenus (chiffrement bout-en-bout). C'est un **argument de conformité par conception** (privacy by design).

**Parcours (à confirmer ANPDP) :**
- [ ] Identifier le régime d'enregistrement du sous-traitant auprès de l'ANPDP. ⚠️ À VÉRIFIER
- [ ] Préparer la description du traitement (nature, finalité, garanties techniques, durée). 
- [ ] Rédiger un modèle de **convention de sous-traitance** (DPA) à signer avec chaque institution cliente — l'avocat le calibre.
- [ ] Documenter les mesures techniques (chiffrement, fragmentation, contrôle d'accès) comme preuves de garanties.

---

## 5. Le rôle de l'avocat spécialisé (poste clé)

| Quand | Pourquoi |
|---|---|
| **Avant le 1er contrat** | Qualifier juridiquement le relais (stocke-t-il « au sens » de la loi ?), valider le statut ARPCE, le régime ANPDP |
| Au montage | Rédiger/valider la convention de sous-traitance (DPA) type |
| En continu | Sécuriser chaque contrat B2G |

**Budget :** déjà prévu dans la conformité (~450K DZD incluant dossiers). C'est l'investissement qui transforme « je pense être conforme » en « je suis conforme, voici les preuves » — ce que le DSI exige.

---

## 6. Séquencement avec la migration relais (Move 0)

```
Move 0 : Migration relais → hébergeur algérien agréé
   │   (choisir un DC déjà agréé simplifie le volet ARPCE)
   ▼
Volet ARPCE : qualifier le statut + enregistrement si requis
   │
   ▼
Volet ANPDP : enregistrement sous-traitant + DPA type
   │
   ▼
PREMIÈRE VENTE B2G possible (conformité = argument + sécurité juridique du DSI)
```

**Optimisation :** choisir un hébergeur algérien **déjà agréé** pour le relais règle une grande partie du volet ARPCE d'un coup. À intégrer dans le choix de l'hébergeur au Move 0.

---

## 7. Checklist d'action (next steps concrets)

1. **Cette semaine :** récupérer les textes officiels — Loi 11-25 (intégral) + Décision ARPCE 48. Les lire avec l'angle « relais qui ne stocke pas ».
2. **Cette semaine :** lister 2–3 avocats spécialisés droit du numérique (Alger) ; premier RDV de cadrage (souvent gratuit ou faible coût).
3. **Semaine +1 :** poser à l'ARPCE et à l'ANPDP les **questions précises** (statut requis ? régime sous-traitant ?) — par écrit, ça vaut référence.
4. **Parallèle Move 0 :** présélectionner un hébergeur algérien **agréé** pour le relais.
5. **Livrables à produire ensuite :** convention de sous-traitance (DPA) type + dossier de garanties techniques.

---

## 8. Ce qu'on transforme en argument commercial

> *« MobiCloud est conforme par conception : vos données restent en Algérie, chiffrées sur les appareils, illisibles par nous, sous convention de sous-traitance conforme à la Loi 11-25. Choisir MobiCloud, c'est se mettre en règle — pas prendre un risque. »*

C'est la phrase qui retourne la conformité (perçue comme une contrainte) en **raison d'achat**.

## Sources
- `01-discovery/raw/trends-regulatory.md` — Loi 11-25, Décision 48, décrets
- `02-strategy/business-model.md` — partenariats (hébergeur, ARPCE, ANPDP, avocat)
- `05-financial/funding-needs.md` — budget conformité
