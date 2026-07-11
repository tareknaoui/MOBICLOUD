# Dossier Fonds Algerie Telecom — MobiCloud

**Phase :** Exécution (post-soutenance) · **Mode :** Attaque
**Projet :** mobicloud · **Date :** 2026-06-22
**Objectif :** Décrocher un financement (grant ou prêt préférentiel) du fonds cybersécurité/IA d'Algerie Telecom.

> 🅿️ **DORMANT (2026-06-22)** — Le B2G est mis de côté « en ce moment » (décision fondateur). Ce dossier reste un asset réutilisable si le B2G est réactivé, mais il **n'est PAS sur le front actif**. Front actif : **B2C + opérateur** → voir `b2c-attaque.md`. *(Note : le fonds AT pourrait être re-sollicité sous un angle B2C/grand public, mais l'argumentaire ci-dessous est orienté secteur public et devra être réécrit.)*

> ⚠️ **À VÉRIFIER en priorité (réel, non inventé ici) :** nom officiel exact du fonds, organisme gestionnaire (Algerie Telecom ? ANPT ? Fonds national startup via le label « Startup » du MNESRA ?), portail de candidature, formulaire, pièces obligatoires, dates limites, montants plafonds. **Comment vérifier :** (1) site officiel Algerie Telecom + ANPT, (2) le label/financement « Startup Algérie » (algeriastartup.dz / ministère de l'Économie de la connaissance), (3) appeler directement l'ANPT (Cyberparc Sidi Abdallah), (4) demander à un incubateur (Algeria Venture, Sylabs) qui connaît les guichets actifs. Le présent dossier prépare le **fond** ; les **modalités** se confirment auprès de ces sources.

---

## 1. Ce qu'on demande

| Élément | Valeur |
|---|---|
| Montant visé | **2 000 000 – 5 000 000 DZD** (grant ou prêt à taux préférentiel) |
| Usage | Déblocage Year 1 + Year 2 (relais algérien, conformité légale, outillage) |
| Contrepartie acceptable | Grant idéal ; prêt préférentiel ok ; dilution faible (10–15%) en dernier recours |
| Ce que ça change | Permet d'opérer **sans revenu pendant 18–24 mois** jusqu'au 1er contrat B2G |

---

## 2. Pitch exécutif (5 lignes — à mettre en tête de dossier)

> MobiCloud est une solution algérienne de stockage mobile distribué qui garde les données des institutions publiques **sur le sol algérien**, conforme à la Loi 11-25 et à la Décision ARPCE 48. Le prototype fonctionne déjà sur de vrais appareils (clusters multi-téléphones, basculement automatique). Là où Google Drive et OneDrive sont devenus **illégaux** pour le secteur public algérien, MobiCloud offre la seule alternative mobile-native souveraine, facturée en dinars. Le fonds AT finance le passage du prototype au premier déploiement institutionnel.

---

## 3. Argumentaire d'éligibilité — mapping critères → preuves

| Critère probable du fonds | Preuve MobiCloud | Statut |
|---|---|---|
| Startup/produit **algérien** | Fondateur en Algérie, produit conçu localement | ✅ Acquis |
| Domaine **cybersécurité / souveraineté numérique / IA** | Chiffrement AES-256, souveraineté des données, conformité Law 11-25 | ✅ Acquis |
| **Maturité technique** (pas qu'une idée) | Prototype fonctionnel : clusters multi-appareils, erasure coding RS(k,m), failover Bully | ✅ Acquis — différenciateur fort |
| **Impact / marché** adressable | 600–700 institutions publiques sous obligation de conformité | ✅ Données recherche |
| **Souveraineté / réduction dépendance étrangère** | Remplace clouds US (Google/Microsoft) par hébergement algérien | ✅ Cœur du pitch |
| Label « Startup » (si requis pour le guichet) | À obtenir si nécessaire | ⚠️ À VÉRIFIER / faire |

**Angle gagnant :** ne pas se vendre comme « une app de backup » mais comme **infrastructure de souveraineté numérique pour le secteur public** — c'est exactement le mandat politique qui justifie l'existence d'un fonds cybersécurité.

---

## 4. Use of funds — ce que le financement débloque (détaillé)

| Poste | Montant DZD | Pourquoi c'est finançable |
|---|---|---|
| Migration + hébergement relais sol algérien (12 mois, palier Pilot) | 250 000 | Prérequis #1 de toute vente B2G — l'argument souveraineté |
| Conformité légale ARPCE + ANPDP (avocat + dossiers) | 450 000 | Obligatoire avant toute vente institutionnelle |
| Outillage de développement (12 mois) | 304 000 | Productivité fondateur solo |
| Frais de vie fondateur (12 mois, permet le plein temps) | 540 000 | Convertit le projet en activité à temps plein |
| Déplacements commerciaux (DSI, ARPCE, AT) | 100 000 | Cycle de vente B2G de terrain |
| Marge / imprévus | 350 000 | — |
| **Total (cible haute du financement)** | **~2 000 000** | Couvre intégralement le Year 1 |

*Si 5M DZD obtenus : couvre aussi le Year 2 (recrutement 1er commercial + scale relais).*

---

## 5. Chiffres clés à présenter (cohérents avec les projections)

- **Taux de référence :** 1 EUR = 276 DZD ; 1 USD ≈ 253 DZD.
- **Coût pour atteindre le 1er revenu :** ~1,1M DZD réaliste (~4 000 EUR) — **seuil d'entrée volontairement bas**.
- **Break-even (scénario base) :** ~Mois 13–15, dès le 1er contrat B2G (800K DZD couvre ~les coûts Year 1).
- **Potentiel Year 3 (base) :** ~10,9M DZD de revenu, +8,4M DZD de résultat.
- **Avantage structurel :** stockage à coût marginal ≈ 0 (fourni par les appareils), le relais ne stocke rien → coût d'infra qui **suit** le revenu, ne le précède pas.

*Message au fonds : « petit ticket, fort effet de levier — 2M DZD transforment un prototype primé en infrastructure souveraine déployée. »*

---

## 6. Pièces du dossier (checklist à préparer)

- [ ] Executive summary 1 page (le pitch §2 étoffé)
- [ ] Présentation produit + preuve de fonctionnement (captures/vidéo du prototype, clusters réels)
- [ ] Argumentaire souveraineté + cadre légal (Law 11-25, Décision 48) — réutiliser `01-discovery/`
- [ ] Modèle économique 1 page — réutiliser `02-strategy/business-model.md`
- [ ] Projections financières — réutiliser `05-financial/projections.md`
- [ ] Use of funds (§4)
- [ ] CV fondateur + statut (étudiant/diplômé PFE — atout : projet déjà mature)
- [ ] Pièces administratives (registre de commerce / statut auto-entrepreneur / label startup) — ⚠️ À VÉRIFIER selon guichet
- [ ] Lettre d'intérêt d'une institution (même informelle) si obtenable — **booste énormément** la crédibilité

---

## 7. Ce que le fonds apporte au-delà du cash (à exploiter)

- **Légitimité DSI :** « soutenu par Algerie Telecom » ouvre les portes institutionnelles.
- **Porte d'entrée Mobilis** (filiale AT) → prépare le Move 4 (bundle opérateur).
- **Crédibilité AYRADE / partenaires** dans les négociations futures.

---

## 8. Risques & réalité

| Risque | Mitigation |
|---|---|
| Processus d'attribution opaque, délais imprévisibles | Ne pas planifier le Year 1 en comptant **uniquement** dessus ; avancer bootstrapping en parallèle |
| Guichet exact / éligibilité incertains | §0 À VÉRIFIER — confirmer avant d'investir du temps de montage |
| Exigence d'un statut juridique formel | Anticiper : créer le statut (auto-entrepreneur / EURL) si requis |

---

## 9. Plan d'action (next steps concrets)

1. **Cette semaine :** vérifier le guichet réel (§0) — 3 appels : ANPT, un incubateur, hotline label Startup.
2. **Semaine +1 :** assembler le dossier à partir des pièces déjà rédigées (`01` à `05`) — 80% existe déjà.
3. **Semaine +1 :** capturer une démo vidéo du prototype (preuve qui vaut 10 pages).
4. **Parallèle :** lancer la migration relais — le dossier est bien plus fort « produit déjà sur sol algérien ».
5. **Dès guichet confirmé :** déposer.

> **Lien de dépendance :** le dossier gagne énormément si le relais est **déjà** migré sur sol algérien au moment du dépôt. Idéalement : Move 0 (relais) puis dépôt. Si délai, déposer quand même avec un plan de migration daté.
