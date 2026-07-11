# Analyse de Marché

**Phase :** 3 — Recherche de Marché (Synthèse)
**Projet :** mobicloud
**Date :** 2026-06-21
**Confiance :** Moyenne (données proxy ; les chiffres spécifiques à l'Algérie sont des estimations dérivées de jeux de données régionaux plus larges)

---

## Résumé Exécutif

L'Algérie se situe à un point d'inflexion rare : la pénétration 4G a dépassé 50 %, la législation sur la souveraineté des données a mûri brutalement en 6 mois (juillet 2025–janvier 2026), aucun hyperscaler n'opère sur le territoire algérien, et le fournisseur de cloud souverain incumbent (AYRADE) n'offre pas de produit mobile-natif. MobiCloud cible un gap étroit mais légalement imposé : un stockage distribué qui est mobile-natif, tourne sur des appareils que les utilisateurs possèdent déjà, et garde les données physiquement sur sol algérien. Le marché n'est pas grand selon les standards mondiaux (plafond réaliste de 1,2M–3,0M $ ARR pour le B2G, 120K–720K $ pour le B2C), mais la combinaison d'urgence légale, d'exclusion structurelle des concurrents étrangers, et d'absence de solution actuelle crée une rare opportunité à fenêtre courte. La fenêtre est estimée à 18–36 mois avant que les hyperscalers ne construisent une infrastructure sur territoire algérien ou qu'AYRADE n'élargisse le périmètre de son produit.

---

## Taille du Marché

### TAM — Total Addressable Market

| Marché | Taille (2025) | TCAC | Source | Tier |
|---|---|---|---|---|
| Cloud public Afrique | 15,55 Md$ | 23,3 % → 44,3 Md$ d'ici 2030 | Statista | Tier 2 |
| Cloud souverain MEA | 6,77 Md$ | 23 % → 42,8 Md$ d'ici 2033 | Grand View Research | Tier 2 |
| Total services IT Algérie | ~1,9 Md$ | N/A | Proxy IDC | Tier 2 |
| Marché data center Algérie | 218 M$ | N/A | Mordor Intelligence | Tier 2 |
| Stockage cloud Algérie seul | **80M–150M $** [Estimation] | N/A | Dérivé : 8-10 % des services IT | Confiance faible |

**[Lacune de Données]** Aucun dimensionnement de marché du stockage cloud spécifique à l'Algérie n'existe dans les rapports publiés. 80M–150M $ est dérivé en appliquant le ratio cloud-vers-services-IT africain au total de 1,9 Md$ de services IT de l'Algérie. À traiter comme un ordre de grandeur uniquement.

**Confiance : Moyenne** (les chiffres TAM régionaux sont Tier 2 ; le spécifique Algérie est Faible)

### SAM — Serviceable Addressable Market

**Segment B2G :**
- L'Algérie compte ~114 universités, ~395 hôpitaux majeurs, 48 directions de wilaya, 1 541 communes → estimé à **600–700 cibles institutionnelles** avec autorité budgétaire et exigences de conformité souveraineté [Estimation, Wave 1]
- À une ACV de 5 000–25 000 $/an par institution : maximum théorique **3M–17,5M $ ARR**
- Plafond réaliste (en tenant compte de la faible pénétration, des cycles longs, de la capacité de vente solo) : **1,2M–3,0M $ ARR** [Estimation]

**Segment B2C :**
- Algérie : 36,2M d'internautes (76,9 % de pénétration) ; âge médian 28,6 ; smartphone appareil principal
- Cible : étudiants et jeunes professionnels avec téléphones Android, sans abonnement cloud → estimé à 2M–5M d'utilisateurs adressables [Estimation, confiance faible]
- À 2 % de conversion payante à 300 DZD/mois (~2 $/mois) : **120K–720K $/an** [Estimation]

**Confiance : Faible** — les deux chiffres SAM reposent sur des taux de conversion estimés sans validation terrain

### SOM — Serviceable Obtainable Market (Réaliste)

| Période | B2G | B2C | Total |
|---|---|---|---|
| Année 1 | 0–75K $ (0–3 contrats pilots) | 0–12K $ (early adopters) | 0–87K $ |
| Année 2 | 50K–200K $ (1–8 institutions payantes) | 12K–60K $ | 62K–260K $ |
| Année 3 | 200K–400K $ (scaling via référence) | 60K–120K $ | 260K–520K $ |

**[Hypothèse]** Ces chiffres supposent que le relay migre vers un hébergement algérien avant le début des ventes de l'Année 1, et qu'au moins un pilot institutionnel est sécurisé via une relation directe (gré à gré), pas un appel d'offres BOMOP.

---

## Trajectoire de Croissance

**Moteurs clés :**
1. **La pression réglementaire augmente, elle n'est pas stable.** Quatre lois promulguées de juillet 2025 à janvier 2026. La posture d'application de l'ANPDP devrait se renforcer à mesure que l'autorité mûrit. Chaque action de mise en application contre une institution non conforme est un déclencheur de vente pour MobiCloud. [Données, sources réglementaires]
2. **La croissance de revenus de 117 % en glissement annuel d'AYRADE** confirme que les institutions paient déjà pour du cloud souverain. La catégorie est validée ; le gap mobile-natif en son sein ne l'est pas. [Données, documents investisseurs AYRADE]
3. **La pénétration 4G dépassant 50 % en Afrique (2024)** signifie que la connectivité requise pour le relay de MobiCloud existe à grande échelle. [Données, GSMA 2024]

**Vents contraires clés :**
1. **Pas de relations institutionnelles.** Fondateur solo, pas de contacts DSI, pas de client de référence. Les 12–18 premiers mois sont de la construction de relations, pas du revenu.
2. **Culture des marchés publics au moins-disant.** Les nouveaux entrants ne peuvent pas concurrencer sur l'historique. Les contrats gré à gré (sous le seuil d'appel d'offres) sont la seule entrée réaliste sans relation gouvernementale existante.
3. **Infrastructure de paiement.** La facturation DZD est requise (avantage structurel) mais accepter le DZD crée un risque de change et ajoute de la complexité opérationnelle pour un fondateur solo. [Opinion]

---

## Maturité du Marché

**Cloud souverain en Algérie : Émergent → Croissance Précoce**
La catégorie existe (AYRADE, CERIST) mais est loin de la saturation. La conscience institutionnelle des exigences de conformité a été nouvellement éveillée par la rafale législative 2025-2026. Les acheteurs sont en mode éducation précoce — ils ne comparent pas les fournisseurs, ils cherchent à savoir s'ils sont en risque légal.

**Stockage P2P mobile-natif à l'échelle mondiale : Pré-produit (phase de recherche)**
Aucun produit commercial grand public n'a réussi dans cette catégorie. Hivenet est la tentative la plus proche ; elle a des échecs de fiabilité documentés. La catégorie n'a pas été validée à l'échelle. [Opinion, soutenu par la recherche sur la voix du client]

---

## Benchmarks d'Économie Unitaire

| Métrique | Estimation B2G | Estimation B2C | Source |
|---|---|---|---|
| ACV | 5 000–25 000 $/an | 24–72 $/an (300–500 DZD/mois) | Estimation — pas de benchmark spécifique Algérie |
| CAC (B2G) | Très élevé — relationnel, cycle 12–24 mois | N/A | [Hypothèse] |
| CAC (B2C) | N/A | Quasi-nul — TikTok/WhatsApp organique | [Estimation, Wave 3] |
| Churn (B2G) | Faible une fois déployé (coûts de switching : risque migration, ré-audit conformité) | N/A | [Opinion] |
| Churn (B2C) | Inconnu — pas de données terrain | Inconnu | LACUNE DE DONNÉES |
| LTV (B2G) | Élevé si contrat pluriannuel | Faible | [Estimation] |

**[Lacune de Données]** Aucun benchmark de valeur de contrat SaaS B2G spécifique à l'Algérie n'existe dans les données publiées. Les estimations d'ACV sont dérivées de fourchettes de contrats PME-vers-entreprise typiques dans des marchés africains comparables.

---

## Synthèse Réglementaire

| Instrument | Date | Obligation Clé | Impact sur MobiCloud |
|---|---|---|---|
| ARPCE Décision 48 | 2017 (opérative) | Les opérateurs cloud doivent héberger l'infrastructure sur territoire algérien | Relay sur Render (US) = non conforme. Doit migrer avant toute vente B2G. |
| Loi 18-07 (amendée par 25-11) | Juillet 2025 | Transfert transfrontalier non autorisé de données : 1–5 ans de prison + amende 500K–1M DZD. DPO + DPIA requis pour les institutions. | Les institutions sous pression légale pour trouver un stockage conforme MAINTENANT. Le pitch conformité est concret, pas théorique. |
| Décret 25-320 | 30 déc. 2025 | Cadre national de gouvernance des données pour toutes les administrations publiques | Définit les obligations de conformité que MobiCloud peut aider les institutions à satisfaire |
| Décret 25-321 | 30 déc. 2025 | Stratégie Nationale de Cybersécurité 2025–2029 | L'infrastructure de cybersécurité priorisée dans les dépenses gouvernementales |
| Décret 26-07 | 7 jan. 2026 | Chaque institution publique doit créer une unité de cybersécurité ; tous les contrats de fournisseurs TIC doivent inclure des clauses de cybersécurité | Unité de cybersécurité = champion interne + gardien des marchés pour MobiCloud |

**Estimation du coût de conformité pour MobiCloud :**
- Conformité minimale viable : enregistrement ARPCE + relay sur serveur algérien (~200–1 000 $/mois selon le fournisseur) + revue par conseil juridique (~2 000–5 000 $ unique)
- Conformité complète à l'échelle : enregistrement ANPDP, documentation de conformité DPA, potentielle revue de certification ANPT

**Niveau de risque réglementaire : FAIBLE pour MobiCloud** (la réglementation *favorise* MobiCloud). Le risque devient **ÉLEVÉ** si le relay reste sur infrastructure US.

---

## Analyse Géographique

**Beachhead : Algérie**
- Le vent réglementaire favorable le plus fort d'Afrique du Nord
- Aucun hyperscaler avec infrastructure locale
- AYRADE comme partenaire/référence potentiel
- Financement startup algérien de 4,1 Md$ (2025) — l'écosystème d'investissement existe

**Chemin d'expansion (Année 3+) :**
- Maroc : roadmap gouvernementale « Cloud First 2025-2030 » → opportunité B2G similaire
- Tunisie : marché plus petit, pression réglementaire moins aiguë
- Afrique subsaharienne : cadres réglementaires différents, calendrier plus long

**Ne pas s'étendre avant que l'Algérie ne soit validée.** Le jeu multi-pays nécessite des relations, une conformité locale et une infrastructure relay dans chaque pays — des coûts qui ne peuvent pas être soutenus avant le revenu.

---

## Évaluation du Timing

**Pourquoi maintenant (vents favorables) :**
1. Fenêtre d'application de la législation : les institutions sont légalement exposées mais la plupart n'ont pas encore agi. Le premier arrivant capture la conversion.
2. Pas de concurrence hyperscaler pendant 3–5 ans (meilleure estimation). Cette fenêtre se fermera.
3. L'écosystème de financement startup algérien est réceptif (fonds cybersécurité Algerie Telecom de 11M$ ; MobiCloud est éligible).
4. Pénétration 4G suffisante ; l'infrastructure relay fonctionne aux niveaux de connectivité actuels.

**Pourquoi le timing est aussi serré (vents contraires) :**
1. AYRADE pourrait élargir le périmètre de son produit pour inclure un accès mobile-natif, éliminant le gap de MobiCloud.
2. Le gouvernement algérien pourrait nationaliser/imposer l'usage de CERIST, réduisant l'opportunité tierce.
3. Chaque mois où le relay reste sur infrastructure US est un mois où MobiCloud ne peut pas vendre légalement aux institutions.

**Verdict :** La fenêtre est réelle et courte. L'action la plus urgente n'est pas le développement produit — c'est la migration du relay vers un hébergement algérien.

---

## Connexions Stratégiques

- Le gap concurrentiel (pas de P2P mobile-natif à l'intersection B2G algérienne) — voir `competitor-landscape.md` — valide directement le calcul du SAM ci-dessus.
- L'urgence réglementaire (unités de cybersécurité du Décret 26-07) crée l'acheteur interne décrit dans `target-audience.md` (persona RSSI/DSI).
- Le fonds Algerie Telecom de 11M$ mentionné dans `industry-trends.md` est une voie de financement directe pour couvrir les coûts de migration du relay.

---

## Drapeaux

**Drapeaux Rouges :**
- Les chiffres SAM sont des estimations construites sur des données proxy ; aucun dimensionnement de marché spécifique à l'Algérie n'existe. Traiter tous les nombres comme directionnellement utiles, pas de qualité investissement.
- La viabilité du marché B2C n'est pas confirmée — pas de citations verbatim d'utilisateurs algériens, pas de données terrain sur la rétention.

**Drapeaux Jaunes :**
- Les contrats gré à gré sous le seuil d'appel d'offres sont l'entrée B2G réaliste, mais cette voie nécessite de connaître quelqu'un à l'intérieur de l'institution. Fondateur solo sans contacts institutionnels = forte friction sur la première vente.
- L'avantage de facturation DZD est réel mais signifie aussi une exposition au change et une complexité opérationnelle.

## Lacunes de Données

- Pas de taille de marché du stockage cloud spécifique à l'Algérie publiée (estimation proxy utilisée)
- Pas de benchmarks confirmés de valeur de contrat SaaS B2G Algérie
- Données de capacité et de liste d'attente CERIST indisponibles
- Valeurs d'attribution d'appels d'offres BOMOP pour les contrats IT non accessibles publiquement (abonnement BOMOP requis)
- Pas de données d'installation/rétention d'app grand public pour l'Algérie spécifiquement

## Sources
- Statista marché cloud Afrique (2025) — Tier 2
- Grand View Research cloud souverain MEA (2025) — Tier 2
- GSMA Mobile Connectivity Index Afrique (2024) — Tier 1
- Décrets gouvernementaux algériens (Loi 11-25, Décret 25-321, Décret 26-07) — Tier 1 (journal officiel)
- ARPCE Décision 48 (2017) — Tier 1
- Recherche brute Wave 1 : `01-discovery/raw/market-size.md`, `01-discovery/raw/trends-regulatory.md`
