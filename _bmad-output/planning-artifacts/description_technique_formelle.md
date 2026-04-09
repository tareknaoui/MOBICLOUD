# Description Technique Formelle du Système MobiCloud
## Datalake Éphémère Mobile Distribué

> **Projet :** Mémoire de Master — Big Data & Systèmes Distribués  
> **Version :** 2.1 (inclut les correctifs de scalabilité)  
> **Date :** Mars 2026  
> **Niveau :** Conception algorithmique — abstraction technologique

---

## 1. Présentation Générale

### 1.1 Contexte et Problématique

MobiCloud est un système de stockage distribué conçu pour des environnements mobiles ad hoc, dans lesquels aucune infrastructure réseau fixe ni aucun serveur central n'est disponible. Le système répond à trois objectifs fondamentaux, désignés par la triple-contrainte **A+B+C** :

- **(A) Persistance :** garantir qu'un fichier déposé sur le réseau reste accessible malgré la déconnexion du nœud émetteur.
- **(B) Récupération :** permettre à tout nœud autorisé de retrouver et reconstruire un fichier, qu'il soit lui-même l'auteur ou un tiers.
- **(C) Requêtage :** offrir la capacité d'interroger l'ensemble des fichiers disponibles sur le réseau local sans latence réseau.

La contrainte centrale du système est la **volatilité des nœuds** (également désignée sous le terme *churn*) : dans un environnement mobile de type campus, conférence ou zone de crise, entre 30 % et 70 % des nœuds peuvent rejoindre ou quitter le réseau en l'espace d'une heure, pour des raisons de batterie faible, de mobilité physique ou de déconnexion volontaire.

### 1.2 Proposition Architecturale

MobiCloud repose sur **quatre piliers techniques** complémentaires :

| Pilier | Rôle dans le système |
|--------|----------------------|
| **Intelligence Artificielle Embarquée** | Évaluation continue de la fiabilité de chaque nœud et déclenchement des migrations préventives |
| **Erasure Coding Adaptatif (EC)** | Redondance mathématique ajustée dynamiquement à l'état du réseau |
| **Catalogue Distribué par Gossip et DHT** | Mémoire collective des métadonnées, cohérente, sans serveur central |
| **Gouvernance Économique (Karma)** | Système de réciprocité anti-exploitation garantissant la contribution de chaque participant |

Le système est décomposé en **dix modules fonctionnels** dont les interactions sont décrites dans les sections suivantes.

---

## 2. Architecture Réseau

### 2.1 Canal Unique de Communication

MobiCloud utilise un canal de communication P2P unifié pour l'ensemble de ses échanges réseau :

- **Rôle de Découverte et Contrôle :** Découverte de pairs, signaux de présence (*heartbeat*), métadonnées du catalogue (*gossip*) et annonces d'urgence.
- **Rôle de Transfert :** Connexion directe point à point pour le transfert de fragments de données, la réparation et la migration d'urgence.

**Règle fondamentale :** le routage multi-sauts via un nœud relais est **strictement interdit pour les fragments de données** : tout transfert de fragment doit s'effectuer en connexion directe entre l'émetteur et le récepteur. Si aucune connexion directe n'est possible, le nœud cible est exclu de la liste des candidats pour ce fragment.

*Justification :* faire transiter un fragment de données lourd via un relais consomme la batterie de ce relais sans lui apporter de contrepartie en termes de Karma. Cette asymétrie crée un vecteur d'exploitation qui serait incompatible avec le modèle de réciprocité du système.

### 2.2 Identité Cryptographique et Coût d'Entrée

Chaque nœud est identifié de manière unique par une **paire de clés asymétriques**. L'identifiant réseau du nœud (`ID_DHT`) est le hachage de sa clé publique. Toutes les communications, tous les fragments et toutes les entrées du catalogue sont signés par la clé privée du nœud émetteur ou propriétaire.

Afin de contrer les **attaques Sybil** (création massive de fausses identités pour obtenir des crédits d'entrée multipliés), la création d'une identité est soumise à une **Preuve de Travail légère** (schéma Hashcash) : le nœud doit calculer un *nonce* tel que le hachage `Hash(Clé_Publique + nonce)` satisfasse une condition de préfixe cible. La difficulté est calibrée pour nécessiter environ une seconde de calcul sur un appareil mobile moyen. Ceci rend la création de dizaines de milliers d'identités fictives économiquement non rentable (environ 2 heures 45 minutes pour 10 000 identités).

La vérification de la preuve par un pair est, en revanche, instantanée (un seul calcul de hachage).

### 2.3 Gestion de l'Énergie : Le Réveil Asynchrone

Pour qu'un réseau mobile subsiste sans épuiser la batterie, les téléphones ne maintiennent pas une connexion active permanente avec chaque pair. Ils alternent entre des phases de persistance élevée et de sommeil logique, tout en laissant toujours une **interface réseau d'écoute ouverte** (protégée du système de gestion d'énergie par une directive de premier plan de l'OS).

Ce mécanisme de réveil s'articule autour de deux vecteurs asynchrones :
1. **Réveil par Interruption Directe (Téléchargement) :** Un nœud actif désirant un fragment auprès d'un nœud passif initie directement une demande de liaison physique. La connexion sur l'interface d'écoute du nœud passif déclenche une interruption qui réveille instantanément la machine logique, sans nécessiter d'échange de signaux de réveil préalables conditionnés.
2. **Réveil par Alarme de Découverte (Diffusion Localisée) :** Si un nœud est devenu inatteignable (changement d'adresse locale), le nœud en détresse émet un datagramme réseau de diffusion marqué « `URGENT` ». La réception de ce paquet par un nœud passif force sa machine à état à rebasculer en cycle **Actif** : la fréquence de son Heartbeat s'emballe temporairement afin de restabiliser la topologie locale.

---

## 3. Description des Dix Modules

---

### Module 1 — Entrée dans le Réseau et Découverte

**Objectif :** permettre à un nouveau nœud de rejoindre le réseau de manière autonome, sécurisée et sans serveur central, en établissant des liens de confiance cryptographiques avec ses pairs.

**Mécanisme :**

Le nœud rejoignant émet un signal d'annonce sur le réseau P2P local. Il inclut son identifiant public et la version de son catalogue. Si aucune réponse n'est reçue dans un délai T, le nœud applique un **backoff exponentiel** (doublement de T à chaque tentative, jusqu'à un maximum `T_MAX`) afin d'éviter la saturation du canal lors d'arrivées simultanées massives.

À la réception d'une réponse d'un pair voisin, les deux nœuds procèdent à une **authentification mutuelle explicite** : chaque nœud signe un message d'annonce avec sa clé privée et vérifie la signature de l'autre avec sa clé publique reçue. Aucun mot de passe ni tiers de confiance n'est requis. Tout nœud dont la signature est invalide est immédiatement rejeté et inscrit sur une liste noire locale.

La découverte au-delà de la portée directe est assurée par un mécanisme **multi-sauts restreint aux messages légers** : un nœud intermédiaire transmet le signal d'annonce sans en lire le contenu (chiffrement de bout en bout préservé). Ce multi-saut est autorisé uniquement pour les messages de contrôle (gossip, heartbeat, annonces).

À l'issue de l'authentification, le nouveau nœud entre en **période probatoire** : son Score de Fiabilité initial est fixé à une valeur plancher. Durant cette période, il peut demander des données mais n'héberge que des fragments non critiques. À l'issue de la période probatoire, si le nœud est resté stable, il est promu au statut ACTIF et peut héberger des fragments critiques.

**Mécanisme de révocation :** si un nœud actif se comporte de manière malveillante (retour de fragments corrompus, refus de répondre), les voisins le signalent et, après N signalements convergents, son identifiant est ajouté à une liste noire propagée par le protocole Gossip à l'ensemble du réseau.

---

### Module 2 — Évaluation de la Fiabilité (IA Embarquée)

**Objectif :** calculer en continu un **Score de Fiabilité (SF) ∈ [0, 1]** pour chaque nœud, afin d'alimenter les décisions de placement, de migration et d'élection des autres modules.

**Mécanisme :**

Le calcul s'effectue en plusieurs étapes successives :

**Étape 1 — Collecte des signaux :**  
- B = niveau de batterie normalisé [0, 1]  
- dB = vitesse de décharge (différentiel temporel de B)  
- M = intensité du mouvement physique (accéléromètre lissé)  
- U = durée de présence continue (uptime) normalisée  
- L = latence réseau moyenne sur les N dernières requêtes  
- P = taux de paquets perdus

**Étape 2 — Adaptation contextuelle des poids :**  
Si le téléphone est branché sur secteur, le poids de la batterie est ramené à zéro (le risque d'extinction par batterie est nul). En contrepartie, le poids du mouvement physique est augmenté (risque de débranchement).

**Étape 3 — La Guillotine (veto vital) :**  
Avant tout calcul pondéré, le système vérifie des **seuils de mort imminente**. Si la batterie est inférieure à un seuil critique (ex. 5 %) ou si le taux de perte de paquets dépasse un seuil critique (ex. 50 %), le SF est immédiatement forcé à zéro, indépendamment des autres métriques. Cette règle empêche un long historique de stabilité de masquer une extinction imminente.

**Étape 4 — Calcul du score composite :**  
En l'absence de veto vital :

```
Score_Matériel = (w_B × B) − (w_dB × dB) − (w_M × M) + (w_U × U)
Score_Réseau   = 1.0 − (w_L × L) − (w_P × P)
Score_Brut     = MIN(Score_Matériel, Score_Réseau)
```

Le score composite est la valeur minimale des deux composantes, car le "maillon le plus faible" est déterminant : un nœud avec une excellente batterie mais un réseau défaillant ne peut pas héberger des données de manière fiable.

**Étape 5 — Lissage temporel (anti-oscillation) :**  
Le score brut est lissé par une moyenne exponentielle pondérée afin d'absorber les micro-fluctuations transitoires :

```
SF_Local = (α × SF_Précédent) + ((1 − α) × Score_Brut)
avec α = 0.7 (inertie de 70%)
```

**Étape 6 — Résolution du statut :**  
Le SF lissé est converti en un statut discret :  
- `SF > seuil_stable` → **STABLE** : le nœud peut héberger de nouveaux fragments  
- `seuil_critique < SF ≤ seuil_stable` → **INSTABLE** : le nœud conserve ses hébergements existants mais n'en accepte pas  
- `SF ≤ seuil_critique` → **CRITIQUE** : déclenche le Module 7 (migration d'urgence)

**Mécanisme anti-clandestin :** le SF calculé est diffusé aux voisins. Chaque voisin confronte le SF déclaré au "Score Perçu" qu'il mesure indépendamment (vitesse de téléchargement observée, latence réelle). Si l'écart dépasse un seuil de tolérance, le SF déclaré est ignoré au profit du Score Perçu pour les décisions de routage.

---

### Module 3 — Stockage : Chiffrement et Erasure Coding Adaptatif

**Objectif :** transformer un fichier utilisateur en un ensemble de fragments chiffrés, redondants et individuellement vérifiables, prêts à être distribués sur le réseau.

**Mécanisme en trois passes séquentielles :**

**Passe 1 — Découpage :**  
Le fichier est découpé en K blocs de taille fixe (ex. 1 Mo par bloc). Le découpage précède le chiffrement afin de maintenir une empreinte mémoire constante, indépendante de la taille totale du fichier. Cette contrainte est essentielle sur mobile, où charger et chiffrer un fichier de 2 Go en mémoire provoquerait un crash.

**Passe 2 — Chiffrement individuel par bloc :**  
- Un **Sel aléatoire** unique est généré pour ce fichier et signé par le propriétaire (toute altération du Sel dans le catalogue est détectable par vérification de signature).  
- Une **clé maître de fichier** est dérivée de la clé personnelle de l'utilisateur combinée au Sel.  
- Pour chaque bloc d'indice `i`, une **clé individuelle** est dérivée : `Clé_i = Dériver(Clé_Fichier, i)`. Chaque bloc est chiffré avec sa propre clé et un vecteur d'initialisation unique. La clé est effacée de la mémoire immédiatement après usage.  
- Cette isolation garantit que la corruption d'un bloc ne compromet pas les autres.

**Passe 3 — Erasure Coding Adaptatif :**  
Le système calcule la **médiane** et l'**écart-type** des Scores de Fiabilité du voisinage (et non la moyenne, car une moyenne peut masquer des nœuds mourants dans un groupe hétérogène). Selon ces indicateurs, le paramètre M (nombre de blocs de protection) est déterminé :

| Condition réseau | Schéma EC | Signification |
|-----------------|-----------|---------------|
| SF_médiane > seuil ET écart-type faible | K + 2 | Réseau stable et homogène |
| SF_médiane > seuil ET écart-type élevé | K + 4 | Réseau globalement sain mais hétérogène |
| SF_médiane ≤ seuil | K + 6 à K + 8 | Réseau instable : mode survie |

Les M blocs de protection sont des combinaisons linéaires des K blocs de données dans un corps de Galois fini. La propriété fondamentale du code est : **n'importe quels K fragments parmi les K+M générés suffisent à reconstruire le fichier original**. Le système peut ainsi perdre jusqu'à M nœuds sans perte de données.

Chaque fragment est ensuite **scellé** : son en-tête (ID fichier, index, paramètres K et M, identifiant propriétaire) et son contenu sont signés conjointement par la clé privée du propriétaire. Tout fragment altéré par un hébergeur malveillant sera détecté à la reconstruction.

Un **checkpoint à mi-distribution** est déclenché lorsque 50 % des fragments ont été confirmés : si la santé du réseau s'est significativement dégradée depuis le début de la distribution, des fragments de protection supplémentaires sont générés et ajoutés à la file d'expédition sans interrompre le transfert en cours.

---

### Module 4 — Distribution et Placement Intelligent

**Objectif :** affecter chaque fragment au nœud optimal du réseau en respectant des contraintes de diversité, d'équilibre et d'intégrité.

**Mécanisme :**

Le placement des K+M fragments suit cinq règles strictes et ordonnées :

**Règle 1 — Anti-corrélation stricte :**  
Deux fragments du même fichier ne peuvent en aucun cas être hébergés par le même nœud. La défaillance d'un nœud ne peut causer la perte que d'un seul fragment par fichier.

**Règle 2 — Seuil de score minimum :**  
Seuls les nœuds dont le SF est supérieur au seuil `seuil_acceptable` et dont la période probatoire est terminée sont éligibles.

**Règle 3 — Diversité spatiale :**  
Les nœuds sont regroupés en **Groupes de Proximité** : deux nœuds partageant plus de 70 % de leurs voisins directs sont considérés co-localisés. Le système tente de placer chaque fragment dans un groupe de proximité distinct, afin de survivre aux pannes réseau locales (ex. coupure d'un sous-réseau entier).

**Règle 4 — Répartition de charge :**  
Le **Score de Candidature** d'un nœud intègre son taux d'occupation actuel :  
`Score_Candidature = SF × (1 − Taux_Occupation)`  
Un nœud très fiable mais déjà saturé voit ainsi son attractivité diminuer, évitant la concentration des responsabilités sur les meilleurs nœuds.

**Règle 5 — Parallélisme borné :**  
Les transferts sont effectués en parallèle avec un nombre maximal de canaux simultanés (ex. 3) afin de réaliser un compromis entre vitesse de distribution et consommation énergétique.

**Protocole de transfert :**  
Pour chaque fragment, une connexion P2P est établie vers le nœud cible. Après réception, le nœud cible retourne un accusé de réception incluant le hachage du fragment reçu. Ce hachage est comparé au sceau attendu. En cas de discordance ou de timeout, jusqu'à trois tentatives sont effectuées sur le même candidat avant de passer au candidat suivant. Les fragments non distribués sont placés dans une file de *retry*.

Le fichier est déclaré **PUBLIÉ** lorsque au moins K fragments ont été confirmés avec succès. Il est déclaré **VULNÉRABLE** si ce seuil n'est pas atteint, déclenchant une redistribution planifiée.

---

### Module 5 — Catalogue Distribué par Gossip et DHT

**Objectif :** maintenir une vision cohérente et à jour des fichiers disponibles et de la localisation de leurs fragments, sans serveur central, en gérant naturellement les mises à jour concurrentes et les partitions réseau.

**Structure d'une fiche catalogue :**  
Chaque fichier est décrit par une fiche contenant : son identifiant unique, ses métadonnées lisibles (nom, taille, type, tags), l'identifiant de son propriétaire, une durée de vie (TTL), le Sel signé nécessaire au déchiffrement, une horloge logique de versionnage, et la liste de ses morceaux avec les localisations de chaque fragment et leurs horloges de mise à jour. La fiche est intégralement signée par le propriétaire.

**Partitionnement DHT (correctif scalabilité v2.1) :**  
À l'échelle de 100 000 nœuds, un catalogue global répliqué sur chaque appareil représenterait plusieurs gigaoctets de métadonnées par nœud, rendant la synchronisation Gossip intenable. Le catalogue est donc **partitionné** selon un anneau DHT : chaque nœud est responsable des fiches dont le hachage de l'identifiant tombe dans sa plage assignée `[ID_DHT, ID_Successeur[`. Un nœud ne stocke localement que les fiches de sa partition, de ses propres fichiers et de ses fichiers épinglés. La recherche d'un fichier hors-partition se fait en **maximum deux sauts réseau**, garantissant une complexité O(log N).

**Protocole de synchronisation (Gossip épidémique) :**  
À intervalles adaptatifs (courts en cas de changements récents, longs en mode silencieux), chaque nœud sélectionne aléatoirement trois voisins et leur transmet ses changements récents (*delta*). Ce mécanisme épidémique garantit que 99 % des nœuds sont informés d'une nouvelle entrée en environ log(N) cycles, soit moins de 30 secondes pour un réseau de 100 nœuds.

**Propriétés de fusion (CRDT) :**  
La fusion de deux mises à jour concurrentes est régie par l'horloge logique de chaque entrée : la version avec l'horloge la plus récente est retenue. Cette fusion est **commutative** (A ∪ B = B ∪ A), **associative** ((A ∪ B) ∪ C = A ∪ (B ∪ C)) et **idempotente** (A ∪ A = A). L'ordre de réception des messages n'affecte pas le résultat final de la synchronisation.

**Sécurité du catalogue :**  
Toute entrée reçue dont la signature est invalide ou dont le TTL est expiré est rejetée silencieusement. Un nœud dont les messages sont systématiquement invalides est signalé et peut être inscrit sur liste noire.

**Garbage Collection :** périodiquement, toutes les entrées expirées et toutes les fiches dont aucun hébergeur n'est joignable sont supprimées localement. Cette suppression se propage naturellement lors des cycles de Gossip suivants.

---

### Module 6 — Surveillance, Heartbeat et Auto-Réparation

**Objectif :** détecter les disparitions de nœuds et restaurer automatiquement le niveau de redondance des fichiers affectés.

**Mécanisme de détection :**  
Chaque nœud émet périodiquement un signal *heartbeat* basse consommation incluant son SF actuel et son taux d'occupation. Un nœud qui n'émet plus de heartbeat pendant plus de `1.5 × T_heartbeat` est marqué SUSPECT. Au-delà de `3 × T_heartbeat` de silence, il est déclaré DISPARU. Cette période de grâce évite de déclencher de coûteuses réparations pour des micro-coupures temporaires (tunnel, déplacement bref).

**Inventaire des dommages :**  
À la détection d'un nœud disparu, le système consulte le catalogue pour identifier tous les fragments hébergés par ce nœud. Pour chacun, il comptabilise les fragments survivants du même fichier toujours hébergés sur des nœuds actifs. Si ce compte atteint ou descend sous `K + marge_sécurité`, une alerte est émise.

**Orchestration par le Super-Pair :**  
Les alertes sont remontées au Super-Pair (Module 10), qui les dédoublonne et les ordonne par urgence croissante. Cette centralisation de la décision évite que dix nœuds voisins déclenchent simultanément dix réparations redondantes du même fichier (tempête de diffusion).

**Cicatrisation mathématique (Healing) :**  
Le Super-Pair désigne un **Nœud Médecin**, de préférence un nœud stable qui héberge déjà un fragment du fichier affecté (économisant ainsi un téléchargement). Le Médecin :
1. Collecte exactement K fragments survivants du fichier depuis le réseau.
2. Résout le système d'équations du code d'effacement (inversion en corps de Galois) pour obtenir les K blocs de données originaux.
3. Recalcule uniquement les fragments manquants (pas l'intégralité du fichier).
4. Distribue ces nouveaux fragments vers des nœuds sains, en respectant les règles d'anti-corrélation du Module 4.
5. Met à jour le catalogue via Gossip pour refléter les nouvelles localisations.

---

### Module 7 — Migration Proactive (Évacuation d'Urgence)

**Objectif :** empêcher la perte de fragments lorsqu'un nœud détecte sa propre défaillance imminente, en organisant un transfert ordonné et priorisé de ses responsabilités.

**Déclencheur :** le Module 2 bascule le statut du nœud à CRITIQUE.

**Mécanisme :**

**Gel immédiat :** le nœud cesse d'accepter de nouveaux fragments et diffuse un signal d'urgence à ses voisins pour leur signifier de ne plus lui envoyer de données.

**Triage médical :** pour chaque fragment hébergé, le nœud calcule la **Marge de Survie** du fichier parent, définie comme le nombre de fragments survivants estimés après son départ moins le seuil K requis. Les fragments sont triés par priorité décroissante : Marge = 0 représente une urgence absolue (le fichier mourra avec ce nœud), Marge > 0 représente une priorité proportionnelle à 1 / Marge.

**Recherche de sanctuaires :** le nœud identifie ses voisins actifs au statut STABLE et disposant de capacité de stockage. Si aucun sanctuaire n'est disponible (effet "Fin de Conférence" : départs massifs simultanés), la migration est interrompue. Tenter de saturer un réseau déjà appauvri aggraverait la situation.

**Évacuation parallèle :** les fragments sont transférés en parallèle au débit maximal disponible, en respectant la contrainte d'anti-corrélation (le sanctuaire reçu ne doit pas héberger un autre fragment du même fichier). Les clés de chiffrement ne sont jamais transmises. Seuls les blocs chiffrés opaque transitent.

**Testament Numérique :** avant extinction, le nœud diffuse via Gossip, avec priorité maximale, la liste complète de toutes ses migrations réussies `(ID_Fragment → Nouveau_Hébergeur)`. Ce message unique et urgent met à jour instantanément le catalogue de l'ensemble du réseau, sans attendre les cycles Gossip habituels.

---

### Module 8 — Récupération d'un Fichier

**Objectif :** permettre à un utilisateur de retrouver, télécharger, décoder et déchiffrer un fichier de manière sécurisée, en tolérant les défaillances de certains hébergeurs et en préservant la mémoire vive de l'appareil.

**Mécanisme :**

**Recherche locale :** le nœud consulte son catalogue local synchronisé. Si la fiche est dans sa partition DHT locale, la recherche est quasi instantanée (< 100 ms). Sinon, un lookup DHT en maximum deux sauts réseau est effectué (< 800 ms). La recherche ne nécessite aucune diffusion réseau large.

**Vérification préliminaire du Sel :** avant tout téléchargement, la signature du Sel stocké en métadonnées est vérifiée. Si elle est invalide, le catalogue a été falsifié et l'opération est interrompue avec une alerte.

**Téléchargement compétitif synchronisé :** plutôt que de solliciter séquentiellement les hébergeurs, le nœud cible K + 2 fragments en priorité chez ceux à faible latence. Les transferts se font par **fenêtres glissantes (ex: 3 requêtes simultanées maximum)** pour éviter d'asphyxier la batterie et le matériel réseau (ex: puce Wi-Fi/BLE locale). Dès que K fragments valides sont reçus et leur sceau vérifié, le nœud coupe le circuit et annule les requêtes en attente. Cette stratégie absorbe les aléas du réseau mobile sans surcharge matérielle.

**Pipeline de décodage en flux continu :** la reconstruction ne charge pas l'ensemble des fragments en mémoire vive simultanément. Le décodage EC et le déchiffrement sont effectués **par fenêtres successives** : pour chaque bloc reconstitué, la clé de bloc correspondante est dérivée, le bloc est déchiffré, délivré au flux de sortie utilisateur, puis les données intermédiaires et la clé de bloc sont effacées de la mémoire. Ce pipeline permet à l'utilisateur de commencer à consommer le contenu (lecture vidéo, affichage) avant la fin du téléchargement complet.

---

### Module 9 — Auto-Régulation et Système Anti-Clandestin (Karma)

**Objectif :** prévenir l'exploitation du réseau par des nœuds "passagers clandestins" qui publieraient massivement sans contribuer au stockage collectif, ainsi que les comportements de "Trou Noir" (accepter des fragments puis les effacer).

**Mécanisme central — Le Karma :**  
La monnaie d'échange du réseau est la réciprocité. Pour publier des données, un nœud doit héberger des données pour les autres. Le Ratio de Réciprocité est défini comme :

```
Karma = Volume_Hébergé_Utile / Volume_Publié_Actif
```

Le Karma n'est **pas auto-déclaré** par le nœud lui-même. Étant donné le partitionnement du catalogue (Module 5), le ratio de réciprocité nécessite une preuve. L'émetteur demande à ses "Gardiens DHT" (les nœuds responsables de la région de son identifiant local) de certifier son bilan : "Signez mon relevé de Karma". L'émetteur présente ce **"Jeton de Karma" (Karma Token)** lors d'un transfert. Le récepteur se contente d'en vérifier la signature cryptographique (principe Zero-Trust déterministe, robuste même au changement de Super-Pair).

La publication est autorisée si et seulement si `Karma ≥ Effort_Redondance`, où `Effort_Redondance = (K + M) / K`. Ce ratio tient compte du fait qu'un fichier déposé sollicite le réseau pour K + M fragments, pas seulement K.

**Correctifs Anti-Sybil et Anti-Collusion (v2.1) :**

*Coût d'Entrée (Anti-Sybil) :* la création d'une identité exige une Preuve de Travail (PoW) calibrée à ~1 seconde. Ceci rend la création de milliers d'identités fictives économiquement prohibitive.

*Crédit de départ restreint :* les nœuds en période d'intégration ne peuvent publier qu'avec des schémas EC à faible parité (M ≤ 2), limitant leur impact sur les ressources réseau.

*Proof of Retrievability (PoR) (Anti-Collusion) :* périodiquement, un défi cryptographique est émis à destination d'un nœud hébergeur. Le challenger est désigné de manière **déterministe** par `Hash(ID_Fragment + Horloge_Logique_Réseau)`, ce qui empêche deux nœuds complices de se défier mutuellement. Le défi consiste à retourner le hachage d'un segment dont l'**offset est aléatoire et inconnu avant réception**, rendant impossible toute réponse correcte sans possession effective du fragment. Deux échecs consécutifs entraînent le bannissement du nœud et le déclenchement de réparations pour tous ses fragments.

**Détection des Trous Noirs :** si un nœud répertorié comme hébergeur d'un fragment répond "introuvable" lors d'une requête de lecture, le demandeur génère une **Plainte Gossip signée** (preuve cryptographique de la défaillance). Trois plaintes de sources distinctes entraînent le bannissement du nœud, la perte de son Karma, et le déclenchement d'auto-réparations pour ses fragments.

---

### Module 10 — Élection du Coordinateur (Super-Pair)

**Objectif :** désigner de manière décentralisée, rapide et incontestable un nœud coordinateur chargé de dédoublonner les décisions critiques d'orchestration (ordres de réparation), sans lui accorder de privilèges sur les données.

**Déclencheur :** l'élection est **réactive** — elle ne se déclenche que lors de la disparition du Super-Pair actuel (détectée par le Module 6), de son abdication volontaire (Module 7, statut CRITIQUE ou INSTABLE), ou de la scission du sous-réseau.

**Algorithme d'élection (Bully modifié avec Hystérésis) :**

Tout nœud dont le SF dépasse un seuil minimal de candidature peut diffuser sa candidature. À la réception d'une candidature concurrente, chaque candidat compare les SF :

*Règle d'Hystérésis (correctif v2.1) :* pour prévenir les ré-élections incessantes causées par les micro-fluctuations naturelles du SF, le Super-Pair en exercice bénéficie d'un **bonus de 15 %** dans la comparaison :

```
SF_Effectif_SP_Sortant = SF_Réel × (1 + H),  H = 0.15
```

Un challenger ne peut renverser le SP sortant que si `SF_Challenger > SF_Réel_SP × 1.15`. En pratique, cela signifie qu'un nœud qui allume son écran (baisse temporaire de SF de 3 %) ne déclenche pas une ré-élection. Seul un concurrent significativement et durablement supérieur prend le pouvoir.

*Bris d'égalité* : en cas d'égalité des scores effectifs, la clé publique la plus grande (comparaison lexicographique) détermine le vainqueur de manière déterministe, sans négociation.

Le candidat ayant résisté à toutes les révocations à l'expiration du chronomètre d'élection se déclare nouveau Super-Pair.

**Mandat limité (correctif v2.1) :** un Super-Pair ne peut exercer ses fonctions plus de 30 minutes consécutives. À l'issue de ce mandat, il abdique et déclenche une réélection. Ceci prévient le monopole opérationnel d'un nœud très stable (ex. branché sur secteur en permanence).

**Séparation des pouvoirs :** le Super-Pair ne dispose d'aucun privilège cryptographique sur les données hébergées par les autres nœuds. Son rôle se limite à l'orchestration des ordres de réparation et au dédoublonnage des alertes. Il peut être **destitué par vote majoritaire** de ses voisins directs en cas de comportement anormal (spam d'ordres ou inactivité prolongée).

---

## 4. Interactions entre Modules — Flux Principaux

### 4.1 Dépôt d'un Fichier

```
Utilisateur → Application

[M9] Vérification du Karma → AUTORISÉ
       ↓
[M3] Découpage → Chiffrement par bloc → Calcul EC adaptatif (via M2) → K+M Fragments scellés
       ↓
[M4] Sélection des candidats (SF, anti-corrélation, groupes de proximité)
       ↓
[M4] Transferts parallèles (max 3 canaux) + Accusés de réception + Checkpoint mi-parcours
       ↓
[M5] Publication dans le Catalogue (Gossip) → FICHIER PUBLIÉ
```

### 4.2 Lecture d'un Fichier

```
Utilisateur → Application

[M8] Recherche locale dans catalogue (< 100 ms) ou Lookup DHT (< 800 ms)
       ↓
[M8] Vérification signature Sel → Dérivation Clé_Fichier
       ↓
[M8] Téléchargement compétitif K+2 en parallèle → Vérification sceau par fragment
       ↓
[M8] Coupe-circuit économique dès K fragments validés
       ↓
[M8] Pipeline : Décodage EC + Déchiffrement par fenêtre → Flux Utilisateur
```

### 4.3 Défaillance d'un Nœud (Sans Préavis)

```
[M6] Absence heartbeat > 3×T → Nœud DISPARU
       ↓
[M6] Inventaire des fragments hébergés + Calcul de la vulnérabilité des fichiers
       ↓
[M10] Super-Pair dédoublonne les alertes et ordonne par urgence
       ↓
[M6] Désignation d'un Nœud Médecin → Collecte K fragments survivants
       ↓
[M3] Inversion EC → Recalcul des fragments perdus uniquement
       ↓
[M4] Redistribution des nouveaux fragments sur nœuds sains
       ↓
[M5] Mise à jour du Catalogue par Gossip → FICHIER RÉPARÉ
```

### 4.4 Défaillance Prévisible d'un Nœud (Migration Proactive)

```
[M2] SF bascule à CRITIQUE → Déclenche Module 7
       ↓
[M7] Gel des réceptions + Annonce "Je pars" au voisinage
       ↓
[M7] Triage médical : tri des fragments hébergés par Marge de Survie
       ↓
[M7] Recherche de sanctuaires STABLES disponibles
       ↓
       SI sanctuaires disponibles :
         [M7] Évacuation parallèle par ordre de priorité
       SI aucun sanctuaire :
         [M7] Interruption (éviter l'effet avalanche)
       ↓
[M7] Testament Numérique → Gossip prioritaire → Catalogue mis à jour
```

---

## 5. Propriétés de Sécurité

| Propriété | Mécanisme | Niveau de garantie |
|-----------|-----------|-------------------|
| **Confidentialité** | Chiffrement AES-256 par bloc, clés dérivées uniques effacées après usage | Un hébergeur ne peut pas lire les fragments qu'il stocke |
| **Intégrité des fragments** | Signature cryptographique propriétaire sur chaque fragment | Toute altération est détectée à la reconstruction |
| **Intégrité du catalogue** | Signature propriétaire sur chaque entrée | Injection de fausses fiches impossible |
| **Authenticité des nœuds** | Authentification mutuelle par clés asymétriques à chaque connexion | Aucune usurpation d'identité possible |
| **Anti-Sybil** | Preuve de Travail à la création d'identité (~1s/identité) | Création massive d'identités fictives économiquement prohibitive |
| **Anti-Collusion** | PoR avec challenger déterministe et offset aléatoire | Fausse réciprocité entre nœuds complices détectable |
| **Anti-Trou Noir** | Plaintes Gossip signées + Bannissement après 3 plaintes | Suppression clandestine de fragments sanction immédiate |

---

## 6. Métriques Cibles et Paramètres de Configuration

| Paramètre | Valeur cible | Justification |
|-----------|-------------|---------------|
| Taux de récupération (churn 30 %) | ≥ 99 % | Conditions normales |
| Taux de récupération (churn 50 %) | ≥ 95 % | Conditions dégradées |
| Taux de récupération (churn 70 %) | ≥ 80 % | Conditions extrêmes |
| Consommation mode passif | ≤ 5 % batterie/heure | Heartbeat + Gossip silencieux |
| Consommation mode actif | ≤ 15 % batterie/heure | Transfert de fichier |
| Latence recherche locale | < 100 ms | Catalogue local |
| Latence recherche DHT | < 800 ms | Maximum 2 sauts |
| Convergence Gossip (100 nœuds) | < 30 secondes | 99 % du réseau informé |
| Reconstruction fichier 10 Mo | < 5 secondes | Avec 10 nœuds à portée |
| Réponse défi PoR | < 200 ms | Lecture offset local |
| Hystérésis élection | H = 0.15 | Bonus 15 % au SP sortant |
| Durée de mandat Super-Pair | 30 minutes maximum | Anti-monopole |
| Scalabilité | 10 à 500 nœuds/sous-réseau | Objectif opérationnel |

---

## 7. Synthèse

MobiCloud est un système de stockage distribué mobile qui repose sur une architecture en dix modules couvrant l'intégralité du cycle de vie d'une donnée, de son dépôt à sa récupération, en passant par sa maintenance en présence de défaillances fréquentes.

La contribution technique principale du système réside dans l'**articulation cohérente de quatre mécanismes** habituellement traités séparément dans la littérature : un code d'effacement dont les paramètres sont pilotés en temps réel par un score de fiabilité calculé par intelligence artificielle embarquée, un catalogue distribué partitionné dont la cohérence est assurée par des propriétés CRDT via un protocole épidémique, et un système de gouvernance économique qui garantit la contribution effective de chaque participant sans autorité centrale.

Les correctifs de scalabilité (v2.1) adressent quatre vulnérabilités identifiées lors du passage à grande échelle : la saturation par le catalogue global, l'exploitation par identités Sybil, l'instabilité du Super-Pair par oscillation du score, et l'inéquité énergétique du routage multi-sauts non rémunéré.

---

*Document établi à partir de la conception algorithmique détaillée des 10 modules MobiCloud — Mars 2026*
