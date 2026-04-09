---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-03-executive-summary', 'step-04-requirements']
inputDocuments: ['concept_mobicloud_V2.md', 'conception_modules_detailles.md']
workflowType: 'prd'
documentCounts:
  briefs: 1
  research: 0
  brainstorming: 0
  projectDocs: 2
classification:
  projectType: 'mobile_app + scientific_simulation'
  domain: 'scientific'
  complexity: 'high'
  projectContext: 'brownfield'
---

# Product Requirements Document — MobiCloud

**Auteur :** Yasmine  
**Date :** 2026-03-17  
**Version :** 2.0  
**Statut :** Conception Validée — Prêt pour Spécification Technique  

---

## 1. Résumé Exécutif (Executive Summary)

### 1.1 Le Problème

Dans les environnements de type campus, conférence ou zone de crise, des dizaines voire des centaines de smartphones coexistent physiquement sans exploiter leur capacité de stockage collective. Chaque appareil possède entre 32 Go et 256 Go de stockage, dont une partie est inutilisée. Parallèlement, ces environnements manquent souvent d'infrastructure cloud fiable (connectivité limitée, coûts prohibitifs, latence élevée vers les datacenters distants).

Les solutions existantes de stockage distribué (HDFS, Ceph, IPFS) sont conçues pour des serveurs fixes, alimentés en continu, avec des connexions réseau stables. Elles échouent dans un contexte mobile où :
- Les nœuds apparaissent et disparaissent en permanence (**churn élevé**).
- La batterie est une ressource critique et non renouvelable à court terme.
- La bande passante fluctue (connexions P2P variables, pas de 4G/5G garanti).
- Aucune entité centrale n'est disponible pour coordonner.

### 1.2 La Solution : MobiCloud

**MobiCloud** est un **Datalake Éphémère Mobile Distribué** qui transforme un groupe de smartphones en un cloud de stockage autonome, intelligent et sécurisé. Le système repose sur trois piliers :

1. **Intelligence Embarquée (IA)** : Chaque téléphone évalue en continu sa propre fiabilité (batterie, signal, mouvement) et anticipe son départ pour migrer les données à temps.
2. **Redondance Adaptative** : Le niveau de protection des données s'ajuste dynamiquement à la santé du réseau (peu de copies en réseau stable, beaucoup en réseau instable).
3. **Sécurité de bout en bout** : Les données sont chiffrées individuellement par bloc, signées cryptographiquement et vérifiées à chaque transfert. Aucun nœud hébergeur ne peut lire ni altérer ce qu'il stocke.

### 1.3 Proposition de Valeur Unique

| Caractéristique | Solutions Classiques (HDFS/Ceph) | MobiCloud |
|----------------|----------------------------------|-----------|
| Infrastructure requise | Serveurs fixes, alimentation continue | Smartphones standards |
| Tolérance au churn | Faible (nœuds stables attendus) | Haute (conçu pour 30-70% de turnover) |
| Consommation énergétique | Non critique (secteur) | Optimisée par le calcul et stratégies de sommeil (les canaux réseau ne sont pas spécifiquement optimisés) |
| Redondance | Fixe (3 copies) | Adaptative (IA-driven) |
| Coordination | Serveur central (NameNode, MDS) | Super-Pair élu démocratiquement |
| Sécurité des données hébergées | Variable | Chiffrement par bloc, Zero-Trust |

---

## 2. Vision du Produit

### 2.1 Vision Long Terme
> *"Tout groupe de smartphones à portée les uns des autres peut former instantanément un cloud de stockage sécurisé, sans infrastructure, sans serveur, sans Internet."*

### 2.2 Objectifs Stratégiques

| ID | Objectif | Métrique de Succès |
|----|----------|-------------------|
| OBJ-1 | Maintenir la disponibilité des données malgré un churn élevé | ≥ 95% de taux de récupération avec 50% de churn |
| OBJ-2 | Minimiser la consommation énergétique | ≤ 5% de batterie/heure en mode passif |
| OBJ-3 | Garantir la confidentialité absolue des données | 0% de données lisibles par un nœud hébergeur |
| OBJ-4 | Permettre la recherche instantanée | Latence de recherche < 100ms (catalogue local) |
| OBJ-5 | Auto-régulation sans intervention humaine | 0 intervention manuelle pour la réparation/migration |

### 2.3 Utilisateurs Cibles

| Persona | Contexte | Besoin Principal |
|---------|----------|------------------|
| Étudiants en amphi | Campus universitaire, 50-200 smartphones | Partager cours, notes, fichiers entre pairs |
| Participants de conférence | Événement temporaire, 100-500 appareils | Distribuer présentations, photos, vidéos éphémères |
| Équipes de terrain | Zone de crise / zone blanche, 10-50 appareils | Stocker des données critiques sans infrastructure |
| Chercheurs | Laboratoire mobile, collecte de données IoT | Agréger des données capteurs distribuées |

### 2.4 Parcours Utilisateurs Clés (User Journeys)

1. **UJ-01 : Onboarding Silencieux**
   - *Déclencheur :* L'utilisateur ouvre l'application dans un amphi.
   - *Flux :* L'application lance le Foreground Service, exécute le défi Hashcash (~1s), génère les clés locales, puis détecte et s'appaire aux pairs via P2P en arrière-plan sans bloquer l'interface. L'utilisateur voit immédiatement un explorateur de fichiers (vide au début) avec un indicateur discret "Recherche de pairs sur le réseau local...".

2. **UJ-02 : Publication Asynchrone de Masse**
   - *Déclencheur :* L'utilisateur sélectionne un fichier vidéo de 300Mo et clique sur "Publier dans le Datalake".
   - *Flux :* Le fichier est instantanément ajouté à une file d'attente asynchrone (l'interface libère l'utilisateur). En arrière-plan, si le Karma est suffisant, le système découpe, chiffre, calcule les fragments EC, ouvre des canaux P2P éphémères vers les pairs qualifiés, et transfère via 3 canaux parallèles.

3. **UJ-03 : L'Échappée Critique (Migration Urgence)**
   - *Déclencheur :* La batterie de l'utilisateur passe sous 5%, ou son score WiFi Direct/P2P montre un éloignement brutal.
   - *Flux :* L'IA embarquée déclenche le statut CRITIQUE. Le P2P gèle les réceptions entrantes, cible urgemment les nœuds très stables à portée, pousse les fragments vitaux, et diffuse son "Testament Numérique" (Gossip) juste avant l'extinction de l'écran ou déconnexion totale.

### 2.5 Exigences Spécifiques au Domaine (Domain Requirements)

- **DR-01 (OS Continuité Réseau) :** L'application DOIT s'exécuter via un Foreground Service (avec notification persistante) afin d'empêcher l'OS Android de purger les sockets P2P lors de la mise en veille stricte (Doze Mode).
- **DR-02 (Souveraineté & RGPD) :** Bien que l'architecture soit *Zero-Trust* (fragments indéchiffrables pour l'hébergeur), l'application DOIT offrir un bouton explicite permettant à l'utilisateur de "Vider l'espace alloué", lui garantissant un contrôle absolu sur le matériel de son appareil, même au détriment de la communauté.

### 2.6 Périmètre du Produit (Product Scope & Phasing)

Pour garantir une implémentation logicielle maîtrisée, le projet applicatif est divisé en deux phases majeures d'implémentation :
- **Phase 1 (Core MVP - Faisabilité P2P) :** Déploiement du réseau maillé (Single-Hop), de la DHT distribuée (Gossip), et du découpage de fragments chiffrés via Erasure Coding (JNI/C++). La gouvernance et le score de survie du nœud reposent sur des heuristiques mathématiques locales strictes, sans composante d'IA temporelle lourde.
- **Phase 2 (Vision MobiCloud Intelligent) :** Déploiement du modèle asynchrone TensorFlow Lite (IA On-Device) pour l'inférence prédictive des pannes, et activation réseau du système de pénalité Anti-Clandestin complet (Proof of Retrievability continu, Jetons de Karma signés virtuellement par la DHT).

---

## 3. Architecture Fonctionnelle — Cartographie des Modules

Le système est décomposé en **10 modules interdépendants** dont la conception algorithmique détaillée est documentée dans `conception_modules_detailles.md`.

```
┌─────────────────────────────────────────────────────────────────┐
│                     CYCLE DE VIE DE LA DONNÉE                   │
│                                                                 │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐    │
│  │ Module 1 │──▶│ Module 2 │──▶│ Module 3 │──▶│ Module 4 │    │
│  │ Entrée   │   │ Score IA │   │ Chiffre+ │   │ Distribue│    │
│  │ Réseau   │   │ Fiabilité│   │ Découpe+ │   │ Anti-    │    │
│  │          │   │          │   │ EC Adapt.│   │ Corrélé  │    │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘    │
│       │                                            │           │
│       │         ┌──────────┐                       │           │
│       └────────▶│ Module 5 │◀──────────────────────┘           │
│                 │ Catalogue│                                    │
│                 │ Gossip   │                                    │
│                 └──────────┘                                    │
│                      │                                          │
│       ┌──────────────┼──────────────┐                          │
│       ▼              ▼              ▼                          │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐                   │
│  │ Module 6 │   │ Module 7 │   │ Module 8 │                   │
│  │ Heartbeat│   │ Migration│   │ Lecture + │                   │
│  │ + Repair │   │ Urgence  │   │ Déchiffre│                   │
│  └──────────┘   └──────────┘   └──────────┘                   │
│                                                                 │
│              ┌──────────┐   ┌──────────┐                       │
│              │ Module 9 │   │ Module 10│                       │
│              │ Anti-    │   │ Élection │                       │
│              │ Clandestin│   │ Super-   │                       │
│              │ (Karma)  │   │ Pair     │                       │
│              └──────────┘   └──────────┘                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Exigences Fonctionnelles Détaillées

### FR-01 : Entrée dans le Réseau et Découverte (Module 1)

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-01.1 | Le système DOIT permettre à un nouveau nœud de découvrir les pairs à portée via le réseau local. | P0 |
| FR-01.1b | Le système DOIT implémenter une détection de "Client Isolation" réseau. En cas de blocage des sockets par le routeur de la zone (ex: WiFi Campus), le système DOIT basculer sur un protocole matériel P2P de contournement natif au niveau de la couche réseau (ex: Wi-Fi Direct, Bluetooth ou Hotspot). | P0 |
| FR-01.2 | Le système DOIT exiger une authentification mutuelle par échange de clés publiques avant toute communication de données. | P0 |
| FR-01.3 | Le système DOIT imposer une période probatoire aux nouveaux nœuds pendant laquelle ils ne peuvent pas héberger de fragments critiques. | P1 |
| FR-01.4 | La découverte multi-saut (nœud A découvre C via B) DOIT être autorisée **uniquement pour les messages légers** : Gossip, Heartbeat, signaux de présence. | P1 |
| FR-01.4b | Le routage multi-saut de **fragments de données** (> 1 Ko) est **STRICTEMENT INTERDIT**. Tout transfert de fragment DOIT s'effectuer via connexion directe (Canal P2P unifié). Si aucune connexion directe n'est possible, le nœud cible est exclu des candidats pour ce fragment. | P0 |
| FR-01.5 | Le système DOIT implémenter un mécanisme de backoff exponentiel pour l'annonce initiale, plafonné à 10 secondes maximum. | P2 |
| FR-01.6 | Le système DOIT permettre la révocation (blacklisting) d'un nœud malveillant via diffusion Gossip. | P0 |
| FR-01.7 | Le système DOIT utiliser un Heartbeat adaptatif (Near Real-Time) pour économiser la batterie, couplé à un mécanisme de Réveil Asynchrone (via interruption directe ou alarme de Gossip) pour réactiver instantanément les nœuds passifs. | P1 |

> **Justification FR-01.4b :** Le routage de fragments via un nœud relais consomme la batterie du relais sans lui apporter de Karma (il héberge, il ne stocke pas). Cette asymétrie crée un vecteur d'exploitation et une désinitation des nœuds intermédiaires.

---

### FR-02 : Évaluation de Fiabilité par IA Embarquée (Module 2)

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-02.1 | Chaque nœud DOIT calculer en continu un Score de Fiabilité (SF) basé sur la batterie, la connectivité réseau, la mobilité physique et l'uptime. | P0 |
| FR-02.2 | Le système DOIT appliquer une règle "Guillotine" : si un paramètre vital (ex: batterie < 5%) atteint un seuil critique, le SF passe immédiatement à CRITIQUE indépendamment des autres métriques. | P0 |
| FR-02.3 | Les poids des capteurs DOIVENT être contextuels (ex: le poids de la batterie diminue si l'appareil est en charge). | P1 |
| FR-02.4 | Le SF DOIT être lissé temporellement (anti-yoyo) pour éviter les réactions à des fluctuations transitoires. | P1 |
| FR-02.5 | Le système DOIT implémenter un mécanisme "Anti-Free-Rider" : le SF auto-déclaré est cross-vérifié par les voisins (Score Perçu vs Score Annoncé). | P0 |

---

### FR-03 : Stockage, Chiffrement Étanche et Redondance Adaptative (Module 3)

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-03.1 | Le système DOIT découper le fichier en blocs AVANT de chiffrer. Le moteur JNI/C++ DOIT utiliser un système de recyclage natif (Buffer Pooling) pour ses allocations (empreinte mémoire constante garantissant l'absence de Native OOM). | P0 |
| FR-03.2 | Chaque bloc DOIT être chiffré avec une clé dérivée unique (Clé_Fichier + index du bloc). | P0 |
| FR-03.3 | Le Sel utilisé pour la dérivation de clé DOIT être signé par le propriétaire et stocké dans le catalogue. | P0 |
| FR-03.4 | Le nombre de blocs de protection (M) DOIT être calculé dynamiquement en fonction de la médiane ET de l'écart-type des SF du voisinage. Mais le système DOIT imposer un plancher de survie minimal stricte (`M_minimum ≥ K/2`) pour tolérer une Sudden Death brutale des OS hôtes non prévisible de 33% avant la première réparation. | P0 |
| FR-03.5 | Chaque fragment DOIT être scellé (entête + contenu signés ensemble par le propriétaire) pour empêcher toute altération par les hébergeurs. | P0 |
| FR-03.6 | Les clés de chiffrement DOIVENT être effacées de la mémoire vive immédiatement après usage. | P1 |
| FR-03.7 | Le système DOIT supporter un mécanisme de checkpoint à mi-distribution pour réajuster M en vol. | P1 |

---

### FR-04 : Distribution et Placement Intelligent (Module 4)

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-04.1 | Deux fragments d'un même fichier NE DOIVENT JAMAIS être hébergés par le même nœud (anti-corrélation stricte). | P0 |
| FR-04.2 | Seuls les nœuds ayant un SF supérieur au seuil acceptable et hors période probatoire DOIVENT recevoir des fragments. | P0 |
| FR-04.3 | Le système DOIT répartir les fragments dans des Groupes de Proximité distincts pour survivre aux pannes réseau locales. | P1 |
| FR-04.4 | Le score de candidature d'un nœud DOIT intégrer son taux d'occupation (SF × (1 - Occupation)) pour éviter la saturation des meilleurs nœuds. | P1 |
| FR-04.5 | Les transferts DOIVENT être parallélisés avec un nombre maximal de canaux simultanés (ex: 3) pour préserver la batterie. | P1 |
| FR-04.6 | Chaque transfert DOIT être confirmé par un accusé de réception avec vérification du sceau d'intégrité. | P0 |
| FR-04.7 | Le système DOIT implémenter une file de retry intelligente avec N tentatives et candidats alternatifs. | P1 |

---

### FR-05 : Synchronisation du Catalogue Distribué — Partitionné (Module 5)

> **⚠️ Correctif Scalabilité v2.1** : Le catalogue global unique est remplacé par un **Catalogue Partitionné** pour supporter le passage à 100 000+ utilisateurs. Un catalogue global représenterait plusieurs gigaoctets par nœud à cette échelle.

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-05.1 | Chaque nœud NE DOIT PAS stocker le catalogue global complet. Il DOIT uniquement maintenir les métadonnées des fichiers : (a) qu'il a créés, (b) qu'il a épinglés, (c) dont il est le responsable DHT selon le hash de l'ID fichier. | P0 |
| FR-05.2 | La fusion des mises à jour concurrentes DOIT être commutative, associative et idempotente (propriétés CRDT). | P0 |
| FR-05.3 | L'intervalle de Gossip DOIT être adaptatif : court en cas de changements récents, long en mode silencieux. | P1 |
| FR-05.4 | Toute entrée du catalogue DOIT être signée par le propriétaire du fichier. Les entrées non signées ou mal signées DOIVENT être rejetées. | P0 |
| FR-05.5 | Le catalogue DOIT implémenter un TTL (durée de vie) et un Garbage Collector pour purger les entrées expirées. | P1 |
| FR-05.6 | Un nouveau nœud DOIT recevoir uniquement la **partition DHT locale** qui lui est assignée lors de son intégration, pas le catalogue complet. | P1 |
| FR-05.7 | La recherche d'un fichier (Module 8) DOIT utiliser un lookup DHT en O(log N) — maximum 2 sauts réseau — si le fichier n'est pas dans la partition locale. | P0 |

> **Note de conception :** Ce correctif implique que la propriété "Zéro-Latence" du Module 8 (FR-08.1) est remplacée par une latence en O(log N) avec 1-2 sauts. Ce trade-off est nécessaire et doit être assumé dans les métriques de performance.

---

### FR-06 : Surveillance, Heartbeat et Auto-Réparation (Module 6)

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-06.1 | Chaque nœud DOIT émettre un signal Heartbeat périodique basse consommation incluant son SF et son taux d'occupation. | P0 |
| FR-06.2 | Le système DOIT accorder une Période de Grâce (ex: 3 × T_Heartbeat) avant de déclarer un nœud disparu, afin d'éviter les faux positifs. | P0 |
| FR-06.3 | Le système DOIT inventorier les fragments hébergés par un nœud disparu et évaluer la vulnérabilité de chaque fichier concerné. | P0 |
| FR-06.4 | Les ordres d'Auto-Réparation DOIVENT être dédoublonnés par le Super-Pair pour éviter les réparations redondantes. | P0 |
| FR-06.5 | La réparation DOIT être ciblée : ne régénérer que les fragments perdus (pas tout le fichier) via l'inversion des équations EC. | P1 |
| FR-06.6 | La réparation DOIT être préventive : se déclencher avant d'atteindre le seuil critique K (avec une marge de sécurité). | P1 |

---

### FR-07 : Migration Proactive — Évacuation d'Urgence (Module 7)

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-07.1 | Dès que le SF bascule à CRITIQUE, le nœud DOIT geler immédiatement toute réception et diffuser un signal d'urgence. | P0 |
| FR-07.2 | L'évacuation DOIT suivre un tri par "Marge de Survie" : les fragments des fichiers les plus vulnérables sont évacués en priorité absolue. | P0 |
| FR-07.3 | Si aucun nœud stable n'est disponible (Effet "Fin de Conférence"), le système DOIT interrompre la migration pour ne pas aggraver la situation. | P0 |
| FR-07.4 | Avant extinction, le nœud DOIT diffuser un "Testament Numérique" via Gossip prioritaire avec la liste des migrations réussies. | P0 |
| FR-07.5 | Le système DOIT implémenter une mécanique de "Triage d'Urgence" (Load Shedding). En cas d'effondrement massif de la capacité de stockage globale (Storage Pressure), les nœuds récepteurs DOIVENT refuser les gros fragments et privilégier uniquement le sauvetage des métadonnées du catalogue et des très petits fichiers vitaux. | P0 |

---

### FR-08 : Récupération de Fichier — Lecture et Déchiffrement (Module 8)

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-08.1 | La recherche d'un fichier DOIT s'effectuer localement sur le catalogue synchronisé (Zéro-Latence réseau). | P0 |
| FR-08.2 | La signature du Sel DOIT être vérifiée avant tout téléchargement pour détecter les falsifications de catalogue. | P0 |
| FR-08.3 | Le téléchargement DOIT être compétitif par **fenêtre glissante** : solliciter K+2 nœuds, mais en maintenant un maximum de 3 requêtes de sockets simultanées (ex: TCP, respect contrainte batterie/réseau). Le nœud annule les connexions restantes dès que K fragments valides sont reçus. | P1 |
| FR-08.4 | Chaque fragment reçu DOIT être vérifié par son sceau d'intégrité avant d'être accepté. | P0 |
| FR-08.5 | Le décodage EC et le déchiffrement DOIVENT se faire en pipeline (streaming) pour préserver la RAM. | P1 |

---

### FR-09 : Auto-Régulation et Quotas — Système Anti-Clandestin (Module 9)

> **⚠️ Correctif Scalabilité v2.1** : Deux vecteurs d'attaque critiques ajoutés — Attaque Sybil (identités gratuites) et Fausse Réciprocité (collusion de nœuds complices).

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-09.1 | Le Crédit de Départ accordé aux nouveaux nœuds DOIT être limité aux schémas EC à faible parité (M ≤ 2). Les nouveaux nœuds NE PEUVENT PAS publier avec les schémas de haute redondance (M ≥ 4) qui coûtent cher au réseau. | P0 |
| FR-09.1b | La création d'une identité DOIT comporter un coût d'entrée non-falsifiable : une Preuve de Travail légère (Hashcash, difficulté calibrée pour ~1 seconde sur mobile) afin de limiter la création massive d'identités Sybil. | P0 |
| FR-09.2 | Le Ratio de Réciprocité (Karma) NE DOIT PAS être calculé à la volée par le récepteur (à cause du partitionnement). L'émetteur DOIT fournir un "Jeton de Karma" (Karma Token) signé cryptographiquement par les nœuds responsables de sa partition DHT (ses "Gardiens"), que le récepteur se contente de vérifier. | P0 |
| FR-09.3 | Le ratio minimum exigé DOIT intégrer la Taxe de Redondance : héberger (K+M)/K octets pour publier 1 octet. | P1 |
| FR-09.4 | Le système DOIT implémenter une **Preuve de Stockage (Proof of Retrievability)** : des défis cryptographiques aléatoires (hash d'un offset imprévisible du fragment) émis par un challenger tierce désigné par le réseau — permettant de vérifier qu'un nœud possède réellement les données qu'il prétend héberger. | P0 |
| FR-09.4b | Le challenger d'un défi PoR DOIT être sélectionné de manière déterministe via le hash de l'ID fragment, empêchant les nœuds complices de se challenger mutuellement. | P0 |
| FR-09.5 | Le système DOIT détecter les "Trous Noirs" via un mécanisme de Plainte Gossip cryptographiquement prouvable. | P0 |
| FR-09.6 | Un nœud cumulant plus de 3 plaintes de sources distinctes OU échouant à 2 défis PoR consécutifs DOIT être banni et perdre la protection de ses propres fichiers. | P0 |

---

### FR-10 : Élection du Coordinateur — Super-Pair (Module 10)

> **⚠️ Correctif Scalabilité v2.1** : Facteur d'Hystérésis introduit pour empêcher les ré-élections incessantes dues aux fluctuations naturelles du Score de Fiabilité.

| ID | Exigence | Priorité |
|----|----------|----------|
| FR-10.1 | L'élection DOIT être réactive (déclenchée uniquement par la disparition ou l'abdication du chef actuel). | P1 |
| FR-10.2 | Le nœud avec le meilleur SF DOIT gagner l'élection. En cas d'égalité, la clé publique la plus grande sert de bris d'égalité déterministe. | P0 |
| FR-10.2b | Un challenger NE PEUT renverser le Super-Pair en exercice que si : `SF(Challenger) > SF(Super-Pair) × (1 + H)` avec H = 0.15 (Facteur d'Hystérésis). Cela empêche les ré-élections dues aux micro-fluctuations naturelles du SF ("effet yoyo"). | P0 |
| FR-10.2c | Le mandat d'un Super-Pair DOIT être limité à une durée maximale (ex : 30 minutes). À l'expiration, une ré-élection est déclenchée même si le Super-Pair est stable. | P1 |
| FR-10.3 | Le Super-Pair NE DOIT avoir AUCUN privilège cryptographique sur les données (séparation des pouvoirs). | P0 |
| FR-10.4 | Le système DOIT permettre la destitution du Super-Pair par vote majoritaire des voisins directs en cas de comportement anormal (spam d'ordres ou inactivité prolongée). | P1 |
| FR-10.5 | Le système DOIT implémenter une mécanique de "Consensus de Fusion" en cas de Split-Brain : si deux Super-Pairs détectent mutuellement leur existence en fusionnant, celui ayant le plus haut Score de Fiabilité annexe l'autre gracieusement, rétrogradant le perdant au rang de pair standard. | P0 |

---

## 5. Exigences Non-Fonctionnelles

### NFR-01 : Performance

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-01.1 | Latence de recherche — fichier dans la partition locale | < 100 ms |
| NFR-01.1b | Latence de recherche — fichier hors partition locale (lookup DHT, max 2 sauts) | < 800 ms |
| NFR-01.2 | Temps de convergence Gossip (99% du réseau informé) | < 30 secondes pour 100 nœuds |
| NFR-01.3 | Temps de reconstruction d'un fichier de 10 Mo | < 5 secondes avec 10 nœuds à portée |
| NFR-01.4 | Temps de réponse à un défi Proof of Retrievability | < 200 ms |

### NFR-02 : Fiabilité

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-02.1 | Taux de récupération avec 30% de churn | ≥ 99% |
| NFR-02.2 | Taux de récupération avec 50% de churn | ≥ 95% |
| NFR-02.3 | Taux de récupération avec 70% de churn | ≥ 80% |

### NFR-03 : Énergie

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-03.1 | Consommation en mode passif (Heartbeat + Gossip silencieux) | ≤ 5% batterie/heure |
| NFR-03.2 | Consommation en mode actif (transfert de fichier) | ≤ 15% batterie/heure |

### NFR-04 : Sécurité

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-04.1 | Confidentialité des données hébergées | 0% lisible par l'hébergeur |
| NFR-04.2 | Détection de falsification de fragment | 100% (signature cryptographique) |
| NFR-04.3 | Résistance à l'injection de fausses entrées catalogue | 100% (rejet si signature invalide) |

### NFR-05 : Scalabilité

| ID | Exigence | Cible |
|----|----------|-------|
| NFR-05.1 | Support de charge maximale | Support certifié jusqu'à 500 nœuds concurrents par sous-réseau |
| NFR-05.2 | Taille maximale de fichier supportée | 2 Go (traitement en streaming) |

---

## 6. Stratégie de Communication Réseau

**Principe de Communication Exclusif :** L'ensemble des communications (découverte, heartbeat, potin, et transferts lourds) s'effectue sur un canal de communication P2P unifié. Le routage de données s'effectue exclusivement en point à point direct.

---

## 7. Stratégie de Validation et Tests

### 7.1 Preuve de Concept (PoC)
- **Environnement :** 3 à 5 smartphones physiques (Android).
- **Objectif :** Valider le protocole de communication P2P unifié, le chiffrement par bloc, et la migration proactive.

### 7.2 Simulation à Grande Échelle
- **Outils :** PeerSim, NS-3 ou simulateur Python sur mesure.
- **Objectif :** Modéliser le comportement sur 100 à 5000 nœuds virtuels.
- **Scénarios de Stress :**

| Scénario | Description | Métrique |
|----------|------------|---------|
| Churn progressif | 10% → 70% de départs sur 30 minutes | Taux de récupération |
| Flash Crowd | Arrivée massive de 200 nœuds en 60 secondes | Temps de convergence Gossip |
| Fin de Conférence | 80% de départs simultanés en 5 minutes | Fragments sauvés par migration |
| Attaque Sybil | 20% de nœuds malveillants injectés | Fausses entrées catalogue rejetées |
| Trou Noir | 5% de nœuds acceptent puis effacent | Plaintes déclenchées, bannissements |

---

## 8. Matrice de Traçabilité (Modules ↔ Objectifs)

| Module | OBJ-1 (Dispo) | OBJ-2 (Énergie) | OBJ-3 (Confid.) | OBJ-4 (Recherche) | OBJ-5 (Auto) |
|--------|:-:|:-:|:-:|:-:|:-:|
| M1 — Entrée Réseau | ✓ | ✓ | ✓ | | |
| M2 — Score IA | ✓ | ✓ | | | ✓ |
| M3 — Chiffrement + EC | ✓ | | ✓ | | ✓ |
| M4 — Distribution | ✓ | ✓ | | | ✓ |
| M5 — Catalogue Gossip | ✓ | ✓ | ✓ | ✓ | ✓ |
| M6 — Heartbeat + Repair | ✓ | ✓ | | | ✓ |
| M7 — Migration Urgence | ✓ | | | | ✓ |
| M8 — Lecture/Déchiffrement | ✓ | ✓ | ✓ | ✓ | |
| M9 — Anti-Clandestin | ✓ | | | | ✓ |
| M10 — Élection Super-Pair | ✓ | ✓ | ✓ | | ✓ |

---

## 9. Glossaire

| Terme | Définition |
|-------|-----------|
| **SF (Score de Fiabilité)** | Métrique composite [0,1] calculée par l'IA embarquée, reflétant la stabilité d'un nœud. |
| **EC (Erasure Coding)** | Technique mathématique de redondance. Un fichier de K blocs génère M blocs de protection. N'importe quels K blocs parmi K+M suffisent à reconstruire l'original. |
| **Churn** | Taux de rotation des nœuds (entrées/sorties) dans le réseau. |
| **Gossip** | Protocole épidémique de propagation d'information : chaque nœud transmet à quelques voisins aléatoires, qui retransmettent, etc. |
| **CRDT** | Structure de données à fusion sans conflit (Commutative, Associative, Idempotente). |
| **Super-Pair** | Nœud élu démocratiquement pour coordonner les actions critiques (déduplication des réparations). |
| **Karma** | Ratio de réciprocité d'un nœud : Volume_Hébergé / Volume_Publié. Mesure de contribution au réseau. |
| **Testament Numérique** | Dernier message Gossip envoyé par un nœud mourant pour informer le réseau de ses migrations d'urgence réussies. |
| **Guillotine** | Règle de sécurité : un paramètre vital critique force le SF à zéro, quel que soit le score des autres capteurs. |
| **Trou Noir** | Nœud malveillant qui accepte des fragments dans le catalogue mais les efface localement. |
| **Anti-Corrélation** | Règle de placement : deux fragments du même fichier ne partagent jamais le même hébergeur ni le même groupe de proximité. |

---

## 10. Documents de Référence

| Document | Description |
|----------|-----------|
| `concept_mobicloud_V2.md` | Vision initiale et architecture de haut niveau |
| `conception_modules_detailles.md` | Conception algorithmique détaillée des 10 modules (pseudo-code, logique, décisions) |
| `logique_algorithmique_abstraite.md` | Logique algorithmique abstraite du pipeline de stockage |

---

> **Statut du PRD : COMPLET — Version 2.0**  
> Ce document constitue la spécification fonctionnelle exhaustive de MobiCloud.  
> Prochaine étape recommandée : Spécification Technique Détaillée (choix d'algorithmes, de protocoles et de frameworks).
