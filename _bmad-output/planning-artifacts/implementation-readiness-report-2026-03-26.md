---
stepsCompleted:
  - step-01-document-discovery
includedFiles:
  prd: ["prd.md"]
  architecture: ["architecture.md", "description_technique_formelle.md"]
  epics: ["epics.md"]
  ux: []
---
# Implementation Readiness Assessment Report

**Date:** 2026-03-26
**Project:** PFE

## Document Discovery Files Found

**PRD Documents:**
- `prd.md` (~26 KB)

**Architecture Documents:**
- `architecture.md` (~22 KB)
- `description_technique_formelle.md` (~34 KB)

**Epics & Stories Documents:**
- `epics.md` (~23 KB)

**UX Design Documents:**
- (None found)

## PRD Analysis

### Functional Requirements

FR-01.1: Le système DOIT permettre à un nouveau nœud de découvrir les pairs à portée via un canal basse consommation (type BLE).
FR-01.2: Le système DOIT exiger une authentification mutuelle par échange de clés publiques avant toute communication de données.
FR-01.3: Le système DOIT imposer une période probatoire aux nouveaux nœuds pendant laquelle ils ne peuvent pas héberger de fragments critiques.
FR-01.4: La découverte multi-saut (nœud A découvre C via B) DOIT être autorisée **uniquement pour les messages légers** : Gossip, Heartbeat, signaux de présence BLE.
FR-01.4b: Le routage multi-saut de **fragments de données** (> 1 Ko) est **STRICTEMENT INTERDIT**. Tout transfert de fragment DOIT s'effectuer via connexion directe (Wi-Fi Direct). Si aucune connexion directe n'est possible, le nœud cible est exclu des candidats pour ce fragment.
FR-01.5: Le système DOIT implémenter un mécanisme de backoff progressif pour éviter la saturation lors de l'annonce initiale.
FR-01.6: Le système DOIT permettre la révocation (blacklisting) d'un nœud malveillant via diffusion Gossip.
FR-02.1: Chaque nœud DOIT calculer en continu un Score de Fiabilité (SF) basé sur la batterie, la connectivité réseau, la mobilité physique et l'uptime.
FR-02.2: Le système DOIT appliquer une règle "Guillotine" : si un paramètre vital (ex: batterie < 5%) atteint un seuil critique, le SF passe immédiatement à CRITIQUE indépendamment des autres métriques.
FR-02.3: Les poids des capteurs DOIVENT être contextuels (ex: le poids de la batterie diminue si l'appareil est en charge).
FR-02.4: Le SF DOIT être lissé temporellement (anti-yoyo) pour éviter les réactions à des fluctuations transitoires.
FR-02.5: Le système DOIT implémenter un mécanisme "Anti-Free-Rider" : le SF auto-déclaré est cross-vérifié par les voisins (Score Perçu vs Score Annoncé).
FR-03.1: Le système DOIT découper le fichier en blocs AVANT de chiffrer (empreinte mémoire constante).
FR-03.2: Chaque bloc DOIT être chiffré avec une clé dérivée unique (Clé_Fichier + index du bloc).
FR-03.3: Le Sel utilisé pour la dérivation de clé DOIT être signé par le propriétaire et stocké dans le catalogue.
FR-03.4: Le nombre de blocs de protection (M) DOIT être calculé dynamiquement en fonction de la médiane ET de l'écart-type des SF du voisinage (pas la moyenne).
FR-03.5: Chaque fragment DOIT être scellé (entête + contenu signés ensemble par le propriétaire) pour empêcher toute altération par les hébergeurs.
FR-03.6: Les clés de chiffrement DOIVENT être effacées de la mémoire vive immédiatement après usage.
FR-03.7: Le système DOIT supporter un mécanisme de checkpoint à mi-distribution pour réajuster M en vol.
FR-04.1: Deux fragments d'un même fichier NE DOIVENT JAMAIS être hébergés par le même nœud (anti-corrélation stricte).
FR-04.2: Seuls les nœuds ayant un SF supérieur au seuil acceptable et hors période probatoire DOIVENT recevoir des fragments.
FR-04.3: Le système DOIT répartir les fragments dans des Groupes de Proximité distincts pour survivre aux pannes réseau locales.
FR-04.4: Le score de candidature d'un nœud DOIT intégrer son taux d'occupation (SF × (1 - Occupation)) pour éviter la saturation des meilleurs nœuds.
FR-04.5: Les transferts DOIVENT être parallélisés avec un nombre maximal de canaux simultanés (ex: 3) pour préserver la batterie.
FR-04.6: Chaque transfert DOIT être confirmé par un accusé de réception avec vérification du sceau d'intégrité.
FR-04.7: Le système DOIT implémenter une file de retry intelligente avec N tentatives et candidats alternatifs.
FR-05.1: Chaque nœud NE DOIT PAS stocker le catalogue global complet. Il DOIT uniquement maintenir les métadonnées des fichiers : (a) qu'il a créés, (b) qu'il a épinglés, (c) dont il est le responsable DHT selon le hash de l'ID fichier.
FR-05.2: La fusion des mises à jour concurrentes DOIT être commutative, associative et idempotente (propriétés CRDT).
FR-05.3: L'intervalle de Gossip DOIT être adaptatif : court en cas de changements récents, long en mode silencieux.
FR-05.4: Toute entrée du catalogue DOIT être signée par le propriétaire du fichier. Les entrées non signées ou mal signées DOIVENT être rejetées.
FR-05.5: Le catalogue DOIT implémenter un TTL (durée de vie) et un Garbage Collector pour purger les entrées expirées.
FR-05.6: Un nouveau nœud DOIT recevoir uniquement la **partition DHT locale** qui lui est assignée lors de son intégration, pas le catalogue complet.
FR-05.7: La recherche d'un fichier (Module 8) DOIT utiliser un lookup DHT en O(log N) — maximum 2 sauts réseau — si le fichier n'est pas dans la partition locale.
FR-06.1: Chaque nœud DOIT émettre un signal Heartbeat périodique basse consommation incluant son SF et son taux d'occupation.
FR-06.2: Le système DOIT accorder une Période de Grâce (ex: 3 × T_Heartbeat) avant de déclarer un nœud disparu, afin d'éviter les faux positifs.
FR-06.3: Le système DOIT inventorier les fragments hébergés par un nœud disparu et évaluer la vulnérabilité de chaque fichier concerné.
FR-06.4: Les ordres d'Auto-Réparation DOIVENT être dédoublonnés par le Super-Pair pour éviter les réparations redondantes.
FR-06.5: La réparation DOIT être ciblée : ne régénérer que les fragments perdus (pas tout le fichier) via l'inversion des équations EC.
FR-06.6: La réparation DOIT être préventive : se déclencher avant d'atteindre le seuil critique K (avec une marge de sécurité).
FR-07.1: Dès que le SF bascule à CRITIQUE, le nœud DOIT geler immédiatement toute réception et diffuser un signal d'urgence.
FR-07.2: L'évacuation DOIT suivre un tri par "Marge de Survie" : les fragments des fichiers les plus vulnérables sont évacués en priorité absolue.
FR-07.3: Si aucun nœud stable n'est disponible (Effet "Fin de Conférence"), le système DOIT interrompre la migration pour ne pas aggraver la situation.
FR-07.4: Avant extinction, le nœud DOIT diffuser un "Testament Numérique" via Gossip prioritaire avec la liste des migrations réussies.
FR-08.1: La recherche d'un fichier DOIT s'effectuer localement sur le catalogue synchronisé (Zéro-Latence réseau).
FR-08.2: La signature du Sel DOIT être vérifiée avant tout téléchargement pour détecter les falsifications de catalogue.
FR-08.3: Le téléchargement DOIT être compétitif : solliciter K+2 nœuds en parallèle et annuler les plus lents dès que K fragments valides sont reçus.
FR-08.4: Chaque fragment reçu DOIT être vérifié par son sceau d'intégrité avant d'être accepté.
FR-08.5: Le décodage EC et le déchiffrement DOIVENT se faire en pipeline (streaming) pour préserver la RAM.
FR-09.1: Le Crédit de Départ accordé aux nouveaux nœuds DOIT être limité aux schémas EC à faible parité (M ≤ 2). Les nouveaux nœuds NE PEUVENT PAS publier avec les schémas de haute redondance (M ≥ 4) qui coûtent cher au réseau.
FR-09.1b: La création d'une identité DOIT comporter un coût d'entrée non-falsifiable : une Preuve de Travail légère (Hashcash, difficulté calibrée pour ~1 seconde sur mobile) afin de limiter la création massive d'identités Sybil.
FR-09.2: Le Ratio de Réciprocité (Karma) DOIT être calculé par le nœud receveur (pas auto-déclaré) en analysant la partition DHT du catalogue.
FR-09.3: Le ratio minimum exigé DOIT intégrer la Taxe de Redondance : héberger (K+M)/K octets pour publier 1 octet.
FR-09.4: Le système DOIT implémenter une **Preuve de Stockage (Proof of Retrievability)** : des défis cryptographiques aléatoires (hash d'un offset imprévisible du fragment) émis par un challenger tierce désigné par le réseau — permettant de vérifier qu'un nœud possède réellement les données qu'il prétend héberger.
FR-09.4b: Le challenger d'un défi PoR DOIT être sélectionné de manière déterministe via le hash de l'ID fragment, empêchant les nœuds complices de se challenger mutuellement.
FR-09.5: Le système DOIT détecter les "Trous Noirs" via un mécanisme de Plainte Gossip cryptographiquement prouvable.
FR-09.6: Un nœud cumulant plus de 3 plaintes de sources distinctes OU échouant à 2 défis PoR consécutifs DOIT être banni et perdre la protection de ses propres fichiers.
FR-10.1: L'élection DOIT être réactive (déclenchée uniquement par la disparition ou l'abdication du chef actuel).
FR-10.2: Le nœud avec le meilleur SF DOIT gagner l'élection. En cas d'égalité, la clé publique la plus grande sert de bris d'égalité déterministe.
FR-10.2b: Un challenger NE PEUT renverser le Super-Pair en exercice que si : `SF(Challenger) > SF(Super-Pair) × (1 + H)` avec H = 0.15 (Facteur d'Hystérésis). Cela empêche les ré-élections dues aux micro-fluctuations naturelles du SF ("effet yoyo").
FR-10.2c: Le mandat d'un Super-Pair DOIT être limité à une durée maximale (ex : 30 minutes). À l'expiration, une ré-élection est déclenchée même si le Super-Pair est stable.
FR-10.3: Le Super-Pair NE DOIT avoir AUCUN privilège cryptographique sur les données (séparation des pouvoirs).
FR-10.4: Le système DOIT permettre la destitution du Super-Pair par vote majoritaire des voisins directs en cas de comportement anormal (spam d'ordres ou inactivité prolongée).

Total FRs: 62

### Non-Functional Requirements

NFR-01.1: Latence de recherche — fichier dans la partition locale
NFR-01.1b: Latence de recherche — fichier hors partition locale (lookup DHT, max 2 sauts)
NFR-01.2: Temps de convergence Gossip (99% du réseau informé)
NFR-01.3: Temps de reconstruction d'un fichier de 10 Mo
NFR-01.4: Temps de réponse à un défi Proof of Retrievability
NFR-02.1: Taux de récupération avec 30% de churn
NFR-02.2: Taux de récupération avec 50% de churn
NFR-02.3: Taux de récupération avec 70% de churn
NFR-03.1: Consommation en mode passif (Heartbeat + Gossip silencieux)
NFR-03.2: Consommation en mode actif (transfert de fichier)
NFR-04.1: Confidentialité des données hébergées
NFR-04.2: Détection de falsification de fragment
NFR-04.3: Résistance à l'injection de fausses entrées catalogue
NFR-05.1: Nombre de nœuds supportés par sous-réseau
NFR-05.2: Taille maximale de fichier supportée

Total NFRs: 15

### Additional Requirements

- Stratégie de Communication Réseau (Canal Basse Consommation vs Haut Débit)
- Stratégie de Validation et Tests (Preuve de Concept, Simulation à Grande Échelle)
- Contraintes de sécurité et d'authentification (Zero-Trust, Proof of Retrievability)

### PRD Completeness Assessment

Le PRD est extrêmement détaillé et bien structuré. Il couvre un grand nombre de cas limites (Attaque Sybil, Fausse Réciprocité, Effet 'Fin de Conférence', etc.). Les exigences fonctionnelles et non-fonctionnelles sont clairement définies et priorisées. La traçabilité vers les modules est bien établie.

## Epic Coverage Validation

### Epic FR Coverage Extracted

FR1: Covered in Epic 1 - Identité & Découverte (Authentification Hashcash sans serveur)
FR2: Covered in Epic 7 - Confiance & Équité (Score de fiabilité IA)
FR3: Covered in Epic 1 - Identité & Découverte (Heartbeat UDP et présence)
FR4: Covered in Epic 2 - Catalogue Distribué (Gossip épidémique et Filtres de Bloom)
FR5: Covered in Epic 2 - Catalogue Distribué (Persistance locale DHT CRDT)
FR6: Covered in Epic 6 - Résilience & Cicatrisation (Détection et auto-réparation)
FR7: Covered in Epic 6 - Résilience & Cicatrisation (Évacuation proactive avant déconnexion)
FR8: Covered in Epic 3 - Stockage Sécurisé & Découpage (Erasure Coding K+M et Chiffrement)
FR9: Covered in Epic 4 - Récupération Haute Vitesse (Streaming P2P compétitif)
FR10: Covered in Epic 7 - Confiance & Équité (Système de Karma temporel)
FR11: Covered in Epic 5 - Coordination Autonome (Élection du Super-Pair)
Total FRs in epics: 11

### FR Coverage Analysis

| FR Number | PRD Requirement | Epic Coverage | Status |
| --------- | --------------- | -------------- | --------- |
| FR-01.1 | Le système DOIT permettre à un nouveau nœud de déc... | **NOT FOUND** | ❌ MISSING |
| FR-01.2 | Le système DOIT exiger une authentification mutuel... | **NOT FOUND** | ❌ MISSING |
| FR-01.3 | Le système DOIT imposer une période probatoire aux... | **NOT FOUND** | ❌ MISSING |
| FR-01.4 | La découverte multi-saut (nœud A découvre C via B)... | **NOT FOUND** | ❌ MISSING |
| FR-01.4b | Le routage multi-saut de **fragments de données** ... | **NOT FOUND** | ❌ MISSING |
| FR-01.5 | Le système DOIT implémenter un mécanisme de backof... | **NOT FOUND** | ❌ MISSING |
| FR-01.6 | Le système DOIT permettre la révocation (blacklist... | **NOT FOUND** | ❌ MISSING |
| FR-02.1 | Chaque nœud DOIT calculer en continu un Score de F... | **NOT FOUND** | ❌ MISSING |
| FR-02.2 | Le système DOIT appliquer une règle "Guillotine" :... | **NOT FOUND** | ❌ MISSING |
| FR-02.3 | Les poids des capteurs DOIVENT être contextuels (e... | **NOT FOUND** | ❌ MISSING |
| FR-02.4 | Le SF DOIT être lissé temporellement (anti-yoyo) p... | **NOT FOUND** | ❌ MISSING |
| FR-02.5 | Le système DOIT implémenter un mécanisme "Anti-Fre... | **NOT FOUND** | ❌ MISSING |
| FR-03.1 | Le système DOIT découper le fichier en blocs AVANT... | **NOT FOUND** | ❌ MISSING |
| FR-03.2 | Chaque bloc DOIT être chiffré avec une clé dérivée... | **NOT FOUND** | ❌ MISSING |
| FR-03.3 | Le Sel utilisé pour la dérivation de clé DOIT être... | **NOT FOUND** | ❌ MISSING |
| FR-03.4 | Le nombre de blocs de protection (M) DOIT être cal... | **NOT FOUND** | ❌ MISSING |
| FR-03.5 | Chaque fragment DOIT être scellé (entête + contenu... | **NOT FOUND** | ❌ MISSING |
| FR-03.6 | Les clés de chiffrement DOIVENT être effacées de l... | **NOT FOUND** | ❌ MISSING |
| FR-03.7 | Le système DOIT supporter un mécanisme de checkpoi... | **NOT FOUND** | ❌ MISSING |
| FR-04.1 | Deux fragments d'un même fichier NE DOIVENT JAMAIS... | **NOT FOUND** | ❌ MISSING |
| FR-04.2 | Seuls les nœuds ayant un SF supérieur au seuil acc... | **NOT FOUND** | ❌ MISSING |
| FR-04.3 | Le système DOIT répartir les fragments dans des Gr... | **NOT FOUND** | ❌ MISSING |
| FR-04.4 | Le score de candidature d'un nœud DOIT intégrer so... | **NOT FOUND** | ❌ MISSING |
| FR-04.5 | Les transferts DOIVENT être parallélisés avec un n... | **NOT FOUND** | ❌ MISSING |
| FR-04.6 | Chaque transfert DOIT être confirmé par un accusé ... | **NOT FOUND** | ❌ MISSING |
| FR-04.7 | Le système DOIT implémenter une file de retry inte... | **NOT FOUND** | ❌ MISSING |
| FR-05.1 | Chaque nœud NE DOIT PAS stocker le catalogue globa... | **NOT FOUND** | ❌ MISSING |
| FR-05.2 | La fusion des mises à jour concurrentes DOIT être ... | **NOT FOUND** | ❌ MISSING |
| FR-05.3 | L'intervalle de Gossip DOIT être adaptatif : court... | **NOT FOUND** | ❌ MISSING |
| FR-05.4 | Toute entrée du catalogue DOIT être signée par le ... | **NOT FOUND** | ❌ MISSING |
| FR-05.5 | Le catalogue DOIT implémenter un TTL (durée de vie... | **NOT FOUND** | ❌ MISSING |
| FR-05.6 | Un nouveau nœud DOIT recevoir uniquement la **part... | **NOT FOUND** | ❌ MISSING |
| FR-05.7 | La recherche d'un fichier (Module 8) DOIT utiliser... | **NOT FOUND** | ❌ MISSING |
| FR-06.1 | Chaque nœud DOIT émettre un signal Heartbeat pério... | **NOT FOUND** | ❌ MISSING |
| FR-06.2 | Le système DOIT accorder une Période de Grâce (ex:... | **NOT FOUND** | ❌ MISSING |
| FR-06.3 | Le système DOIT inventorier les fragments hébergés... | **NOT FOUND** | ❌ MISSING |
| FR-06.4 | Les ordres d'Auto-Réparation DOIVENT être dédoublo... | **NOT FOUND** | ❌ MISSING |
| FR-06.5 | La réparation DOIT être ciblée : ne régénérer que ... | **NOT FOUND** | ❌ MISSING |
| FR-06.6 | La réparation DOIT être préventive : se déclencher... | **NOT FOUND** | ❌ MISSING |
| FR-07.1 | Dès que le SF bascule à CRITIQUE, le nœud DOIT gel... | **NOT FOUND** | ❌ MISSING |
| FR-07.2 | L'évacuation DOIT suivre un tri par "Marge de Surv... | **NOT FOUND** | ❌ MISSING |
| FR-07.3 | Si aucun nœud stable n'est disponible (Effet "Fin ... | **NOT FOUND** | ❌ MISSING |
| FR-07.4 | Avant extinction, le nœud DOIT diffuser un "Testam... | **NOT FOUND** | ❌ MISSING |
| FR-08.1 | La recherche d'un fichier DOIT s'effectuer localem... | **NOT FOUND** | ❌ MISSING |
| FR-08.2 | La signature du Sel DOIT être vérifiée avant tout ... | **NOT FOUND** | ❌ MISSING |
| FR-08.3 | Le téléchargement DOIT être compétitif : sollicite... | **NOT FOUND** | ❌ MISSING |
| FR-08.4 | Chaque fragment reçu DOIT être vérifié par son sce... | **NOT FOUND** | ❌ MISSING |
| FR-08.5 | Le décodage EC et le déchiffrement DOIVENT se fair... | **NOT FOUND** | ❌ MISSING |
| FR-09.1 | Le Crédit de Départ accordé aux nouveaux nœuds DOI... | **NOT FOUND** | ❌ MISSING |
| FR-09.1b | La création d'une identité DOIT comporter un coût ... | **NOT FOUND** | ❌ MISSING |
| FR-09.2 | Le Ratio de Réciprocité (Karma) DOIT être calculé ... | **NOT FOUND** | ❌ MISSING |
| FR-09.3 | Le ratio minimum exigé DOIT intégrer la Taxe de Re... | **NOT FOUND** | ❌ MISSING |
| FR-09.4 | Le système DOIT implémenter une **Preuve de Stocka... | **NOT FOUND** | ❌ MISSING |
| FR-09.4b | Le challenger d'un défi PoR DOIT être sélectionné ... | **NOT FOUND** | ❌ MISSING |
| FR-09.5 | Le système DOIT détecter les "Trous Noirs" via un ... | **NOT FOUND** | ❌ MISSING |
| FR-09.6 | Un nœud cumulant plus de 3 plaintes de sources dis... | **NOT FOUND** | ❌ MISSING |
| FR-10.1 | L'élection DOIT être réactive (déclenchée uniqueme... | **NOT FOUND** | ❌ MISSING |
| FR-10.2 | Le nœud avec le meilleur SF DOIT gagner l'élection... | **NOT FOUND** | ❌ MISSING |
| FR-10.2b | Un challenger NE PEUT renverser le Super-Pair en e... | **NOT FOUND** | ❌ MISSING |
| FR-10.2c | Le mandat d'un Super-Pair DOIT être limité à une d... | **NOT FOUND** | ❌ MISSING |
| FR-10.3 | Le Super-Pair NE DOIT avoir AUCUN privilège crypto... | **NOT FOUND** | ❌ MISSING |
| FR-10.4 | Le système DOIT permettre la destitution du Super-... | **NOT FOUND** | ❌ MISSING |
| FR1 | Le système doit permettre à un nœud de s'authentif... (From Epics Doc) | Epic 1 - Identité & Découverte (Authentification Hashcash sans serveur) | ⚠️ NOT IN PRD |
| FR2 | Le système doit attribuer dynamiquement un score d... (From Epics Doc) | Epic 7 - Confiance & Équité (Score de fiabilité IA) | ⚠️ NOT IN PRD |
| FR3 | Le système doit maintenir une liste des pairs (rou... (From Epics Doc) | Epic 1 - Identité & Découverte (Heartbeat UDP et présence) | ⚠️ NOT IN PRD |
| FR4 | Le système doit synchroniser les deltas du catalog... (From Epics Doc) | Epic 2 - Catalogue Distribué (Gossip épidémique et Filtres de Bloom) | ⚠️ NOT IN PRD |
| FR5 | Le système doit persister un catalogue P2P (DHT) b... (From Epics Doc) | Epic 2 - Catalogue Distribué (Persistance locale DHT CRDT) | ⚠️ NOT IN PRD |
| FR6 | Le système doit orchestrer la détection de fragmen... (From Epics Doc) | Epic 6 - Résilience & Cicatrisation (Détection et auto-réparation) | ⚠️ NOT IN PRD |
| FR7 | Le système doit permettre l'évacuation proactive d... (From Epics Doc) | Epic 6 - Résilience & Cicatrisation (Évacuation proactive avant déconnexion) | ⚠️ NOT IN PRD |
| FR8 | Le système doit découper et chiffrer tout fichier ... (From Epics Doc) | Epic 3 - Stockage Sécurisé & Découpage (Erasure Coding K+M et Chiffrement) | ⚠️ NOT IN PRD |
| FR9 | Le système doit télécharger et déchiffrer en strea... (From Epics Doc) | Epic 4 - Récupération Haute Vitesse (Streaming P2P compétitif) | ⚠️ NOT IN PRD |
| FR10 | Le système doit inciter à la coopération par un sc... (From Epics Doc) | Epic 7 - Confiance & Équité (Système de Karma temporel) | ⚠️ NOT IN PRD |
| FR11 | Le système doit élire un Super-Pair de coordinatio... (From Epics Doc) | Epic 5 - Coordination Autonome (Élection du Super-Pair) | ⚠️ NOT IN PRD |

### Missing Requirements

### Critical Missing FRs

⚠️ **CRITICAL TRACEABILITY MISMATCH:** Le document `epics.md` utilise une liste simplifiée de `FR1` à `FR11` qui ne correspond pas aux identifiants détaillés (`FR-01.1` à `FR-10.4`) définis dans le `prd.md`. Par conséquent, **AUCUNE** des 62 exigences fonctionnelles détaillées du PRD n'est explicitement tracée dans la matrice de couverture des épopées.

Exemples de FRs critiques manquant de traçabilité stricte :
FR-01.1: Le système DOIT permettre à un nouveau nœud de découvrir les pairs à portée via un canal basse consommation (type BLE).
- Impact: Perte de granularité, risque d'omission lors du développement.
- Recommendation: Mettre à jour `epics.md` pour mapper explicitement chaque exigence détaillée (ex: FR-01.1) vers une épopée/user story spécifique.

FR-01.2: Le système DOIT exiger une authentification mutuelle par échange de clés publiques avant toute communication de données.
- Impact: Perte de granularité, risque d'omission lors du développement.
- Recommendation: Mettre à jour `epics.md` pour mapper explicitement chaque exigence détaillée (ex: FR-01.2) vers une épopée/user story spécifique.

FR-01.3: Le système DOIT imposer une période probatoire aux nouveaux nœuds pendant laquelle ils ne peuvent pas héberger de fragments critiques.
- Impact: Perte de granularité, risque d'omission lors du développement.
- Recommendation: Mettre à jour `epics.md` pour mapper explicitement chaque exigence détaillée (ex: FR-01.3) vers une épopée/user story spécifique.

FR-01.4: La découverte multi-saut (nœud A découvre C via B) DOIT être autorisée **uniquement pour les messages légers** : Gossip, Heartbeat, signaux de présence BLE.
- Impact: Perte de granularité, risque d'omission lors du développement.
- Recommendation: Mettre à jour `epics.md` pour mapper explicitement chaque exigence détaillée (ex: FR-01.4) vers une épopée/user story spécifique.

FR-01.4b: Le routage multi-saut de **fragments de données** (> 1 Ko) est **STRICTEMENT INTERDIT**. Tout transfert de fragment DOIT s'effectuer via connexion directe (Wi-Fi Direct). Si aucune connexion directe n'est possible, le nœud cible est exclu des candidats pour ce fragment.
- Impact: Perte de granularité, risque d'omission lors du développement.
- Recommendation: Mettre à jour `epics.md` pour mapper explicitement chaque exigence détaillée (ex: FR-01.4b) vers une épopée/user story spécifique.

### High Priority Missing FRs

Toutes les autres exigences de `FR-01.1` à `FR-10.4` (57 autres exigences). Le mapping actuel est trop haut niveau pour garantir que chaque règle métier (ex: le backoff progressif FR-01.5, l'anti-corrélation stricte FR-04.1) est implémentée.

### Coverage Statistics

- Total PRD FRs: 62
- FRs covered in epics: 0 (Exact ID matches)
- Coverage percentage: 0%

## UX Alignment Assessment

### UX Document Status

Not Found (Confirmé lors de la phase de Découverte des documents)

### Alignment Issues

Aucun document UX détaillé n'a été fourni. Le PRD indique : "L'interface principale se résumera à un explorateur de fichiers DHT et un cadran de diagnostiques du noeud."

### Warnings

⚠️ Bien que l'interface soit décrite comme basique, l'absence de spécifications UX/UI formelles peut engendrer des incertitudes lors du développement Frontend (Jetpack Compose). Des données complexes (Scores de fiabilité, état du routage P2P multicasts, état de téléchargement des fragments épars) devront être rendues intelligibles pour l'utilisateur. Les développeurs UI devront concevoir ces vues sans maquettes directrices, ce qui présente un léger risque de dérives de périmètre UI.
