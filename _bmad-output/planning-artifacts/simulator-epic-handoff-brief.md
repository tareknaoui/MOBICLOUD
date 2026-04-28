---
status: draft
author: Winston (Architect)
date: 2026-04-27
revision: 2 (recadrage transport hors scope thèse)
target-agent: bmad-create-epics-and-stories (PM/SM)
inputDocuments:
  - architecture-connectivity-and-clustering.md
  - epics.md
  - prd.md
---

# Brief de handoff — Epic « Simulateur de Stockage Distribué Mobile »

## 1. Contexte et raison d'être

### 1.1. Cadrage de scope

MobiCloud est un **système de stockage distribué pour environnement mobile**. La contribution thèse porte sur la couche stockage : placement, élection du super-peer, réplication, récupération, erasure coding.

**La couche transport est explicitement hors scope thèse.** MobiCloud résout la traversée NAT par un cluster de **Serveurs Relais HA WebSocket Zero-Knowledge** (signaling + fallback Store-and-Forward) — approche pragmatique, sans dépendance Firebase, STUN, TURN, ICE ou DDNS ; aucune contribution scientifique à ce niveau n'est revendiquée.

### 1.2. Pourquoi un simulateur

La démonstration terrain (5 téléphones, même réseau local) prouve que le système de stockage **fonctionne**. Elle ne peut pas prouver qu'il **résiste** :
- à un cluster de 50–200 pairs (impossible logistiquement),
- à des conditions de transport dégradées (inter-NAT, perte de paquets, latence variable),
- à du churn sévère (pairs qui partent et reviennent),
- à des pannes répétées du super-peer.

Le simulateur **rejoue le code applicatif réel** sur un faux réseau dont la **probabilité de livraison entre pairs** est contrôlée par un profil de transport. Il ne simule **pas** le NAT ni les protocoles de transport ; il modélise le transport comme une **entrée stochastique calibrée sur les mesures terrain**.

### 1.3. Précédents big data défendables

`MiniDFSCluster` (Hadoop), `Trogdor` (Kafka), `Jepsen` (Cassandra/etcd). Tous les systèmes distribués sérieux ont leur simulateur intégré qui modélise le transport abstraitement et stresse la logique applicative.

## 2. Objectif de l'epic

Permettre de :

1. **Stress-tester le système de stockage** (placement, élection, réplication, récupération) sous des conditions de transport variées et reproductibles.
2. **Rejouer les scénarios S0–S7** du document architecture en quelques secondes par scénario.
3. **Produire des résultats publiables** pour la section évaluation de la thèse : robustesse du placement, vitesse de l'élection, taux de blocs perdus, ratio de centralisation, sous trois profils de transport calibrés.
4. **Détecter les régressions** automatiquement à chaque modification de la politique de placement ou de l'algorithme d'élection.

## 3. Exigences à ajouter au PRD

### Exigences fonctionnelles nouvelles

- **FR-SIM-1** : Le système doit exposer un module `:simulator` (JVM pur) capable d'instancier N pairs en mémoire, exécuter les use cases réels de placement/élection/transfert, et collecter des métriques par run. (P0)
- **FR-SIM-2** : Chaque pair simulé doit posséder un `ReachabilityProfile` (type réseau, score de joignabilité ∈ [0,1]) consommé par la politique de placement. (P0)
- **FR-SIM-3** : La politique de placement doit consommer le `reachability_score` et appliquer la règle **« somme des scores des détenteurs ≥ S »** (S = 1.5 par défaut), en remplacement de la règle binaire « ≥ 1 pair 4G ». Cette règle est de la **politique de stockage**, pas du transport. (P0)
- **FR-SIM-4** : Le simulateur doit fournir au moins **3 profils de transport** (`IDEAL`, `INTER_NETWORK_REALISTIC`, `ADVERSARIAL`) calibrés sur la matrice empirique mesurée terrain. (P0)
- **FR-SIM-5** : Le simulateur doit fournir un DSL Kotlin permettant d'exprimer un scénario (cluster + profil transport + actions + assertions) en moins de 30 lignes, avec exécution déterministe via seed. (P1)
- **FR-SIM-6** : Le simulateur doit produire un rapport JSON par run contenant au minimum : `direct_transfer_success_rate`, `super_peer_relay_count`, `bytes_through_super_peer_ratio`, `election_duration_ms`, `blocks_lost`, `replication_factor_actual_min`. (P1)

### Exigences non fonctionnelles nouvelles

- **NFR-SIM-1** : Un scénario simulant 1000 pairs sur 24 heures de temps virtuel doit s'exécuter en ≤ 30 secondes wall-clock sur la machine de dev de l'équipe.
- **NFR-SIM-2** : Le simulateur doit être déterministe : deux runs avec la même seed et le même profil de transport produisent strictement le même rapport JSON.
- **NFR-SIM-3** : Le code applicatif simulé doit être **identique** au code de production. Les seules implémentations divergentes sont les adapters de transport `BlockSender`/`BlockDownloader`/`IElectionNetworkClient`. **Aucune logique métier n'est dupliquée.**
- **NFR-SIM-4** : Les profils de transport doivent être documentés avec leur méthodologie de calibration (lien explicite vers la matrice empirique du document architecture, section 2).

## 4. Modifications du modèle de données

À ajouter dans [Peer.kt](../../app/src/main/kotlin/com/mobicloud/domain/models/Peer.kt) :

```kotlin
data class ReachabilityProfile(
    val networkType: NetworkType,                  // existant : WIFI, CELLULAR, UNKNOWN
    val reachabilityScore: Float,                  // [0,1] moyenne glissante des sondes récentes
    val lastProbeMs: Long
) {
    companion object {
        val UNKNOWN = ReachabilityProfile(NetworkType.UNKNOWN, 0.5f, 0L)
    }
}

data class Peer(
    val identity: NodeIdentity,
    val lastSeenTimestampMs: Long,
    val source: DiscoverySource = DiscoverySource.REMOTE_HA_RELAY,
    val ipAddress: String? = null,
    val port: Int? = null,
    val isActive: Boolean = true,
    val isSuperPair: Boolean = false,
    val reachability: ReachabilityProfile = ReachabilityProfile.UNKNOWN
)
```

**Note** : aucun `NatType` dans le modèle. Le `reachability_score` est calculé en production à partir des sondes TCP du super-peer (heartbeat + ping vers Serveurs Relais HA) ; il est injecté en simulation par le profil de transport choisi.

## 5. Stories proposées (structure prête pour l'agent)

### Story SIM.1 : Modèle ReachabilityProfile et migration des appelants

En tant que **développeur**,
Je veux **étendre le modèle `Peer` avec un `ReachabilityProfile` (type réseau + score de joignabilité)**,
Afin que **la politique de placement et le simulateur puissent raisonner sur la joignabilité mesurée plutôt que sur le seul type de connexion**.

**Acceptance Criteria :**

**Given** le modèle [Peer.kt](../../app/src/main/kotlin/com/mobicloud/domain/models/Peer.kt) existant avec `NetworkType`
**When** la classe `ReachabilityProfile` est ajoutée et `Peer` modifié pour l'inclure
**Then** la compilation du projet réussit après mise à jour de tous les call sites
**And** la valeur par défaut `ReachabilityProfile.UNKNOWN` (score = 0.5, network = UNKNOWN) est utilisée pour les pairs nouvellement découverts sans sondage
**And** la couche persistance Room ([PeerNodeEntity.kt](../../app/src/main/kotlin/com/mobicloud/data/local/entity/PeerNodeEntity.kt)) sérialise/désérialise les nouveaux champs (avec migration de schéma)
**And** les tests existants de [PeerRepositoryImplTest](../../app/src/test/kotlin/com/mobicloud/data/repository/PeerRepositoryImplTest.kt) passent toujours
**And** un nouveau test unitaire valide la (dé)sérialisation Room de `ReachabilityProfile`

**Dépendances :** aucune (story de fondation)

---

### Story SIM.2 : Création du module Gradle `:simulator`

En tant que **développeur**,
Je veux **un module Gradle `:simulator` JVM pur, séparé du module Android**,
Afin que **les scénarios de simulation tournent rapidement en JUnit sans dépendance à l'émulateur Android**.

**Acceptance Criteria :**

**Given** la structure de modules existante dans [settings.gradle.kts](../../settings.gradle.kts)
**When** le module `:simulator` est créé
**Then** `include(":simulator")` apparaît dans `settings.gradle.kts`
**And** le `build.gradle.kts` du module utilise le plugin `kotlin("jvm")` (pas `com.android.library`)
**And** le module dépend des use cases domain (à extraire dans `:core:domain` si nécessaire) sans dépendre du framework Android
**And** `./gradlew :simulator:test` compile et exécute un test à vide sans erreur
**And** un README.md du module documente : objectif, structure, comment ajouter un scénario, **rappel explicite que le transport est modélisé abstraitement**

**Dépendances :** SIM.1

---

### Story SIM.3 : `SimulatedNetwork` basé sur matrice de probabilité de livraison

En tant que **développeur**,
Je veux **des implémentations en mémoire de `BlockSender`, `BlockDownloader` et `IElectionNetworkClient`, routées par une classe centrale `SimulatedNetwork` qui consulte une matrice de probabilité de livraison**,
Afin que **les use cases applicatifs s'exécutent dans le simulateur sans modification, et que les conditions de transport (livraison, latence, perte) soient reproductibles et calibrables**.

**Acceptance Criteria :**

**Given** les interfaces [BlockSender.kt](../../app/src/main/kotlin/com/mobicloud/domain/repository/BlockSender.kt), [BlockDownloader.kt](../../app/src/main/kotlin/com/mobicloud/domain/repository/BlockDownloader.kt), [IElectionNetworkClient.kt](../../app/src/main/kotlin/com/mobicloud/domain/repository/IElectionNetworkClient.kt)
**When** les classes `SimulatedBlockSender`, `SimulatedBlockDownloader`, `SimulatedElectionClient` et `SimulatedNetwork` sont implémentées dans `:simulator`
**Then** `SimulatedNetwork` détient :
  - une `Map<PeerId, ReachabilityProfile>` mutable
  - une **matrice de probabilité** `Map<Pair<PeerId, PeerId>, Float>` retournant la probabilité de livraison A→B
  - une fonction `send(from, to, msg): RouteOutcome` qui retourne `Timeout` si le tirage aléatoire dépasse la probabilité
  - un `Random(seed)` injectable pour le déterminisme
  - une horloge virtuelle `VirtualClock` permettant `advanceTime(duration)`
**And** un pair `offline` retourne toujours `Timeout` quelle que soit la probabilité
**And** la latence de livraison est paramétrable par le profil de transport
**And** un test unitaire vérifie : matrice constante p=1.0 → toujours succès ; matrice constante p=0.0 → toujours timeout ; même seed → même séquence de résultats
**And** **aucune logique « 4G/WiFi » ni « NAT » n'est codée en dur** dans `SimulatedNetwork` — tout passe par la matrice

**Dépendances :** SIM.1, SIM.2

---

### Story SIM.4 : Profils de transport calibrés sur la matrice empirique

En tant que **chercheur**,
Je veux **disposer de 3 profils de transport prêts à l'emploi (`IDEAL`, `INTER_NETWORK_REALISTIC`, `ADVERSARIAL`)**,
Afin de **lancer des scénarios de simulation sans avoir à recalculer une matrice de probabilité à chaque test, et de pouvoir défendre devant le jury que les conditions simulées reflètent des mesures terrain**.

**Acceptance Criteria :**

**Given** la classe `SimulatedNetwork` de SIM.3
**When** la classe `TransportProfile` est implémentée avec 3 instances :
  - `IDEAL` : toutes paires p = 1.0, latence 50 ms
  - `INTER_NETWORK_REALISTIC` : calibré sur la matrice §2 du doc architecture — `(CELLULAR↔CELLULAR)` p ≈ 0.92, `(CELLULAR↔WIFI)` p ≈ 0.88, `(WIFI↔WIFI)` p ≈ 0.10 (échec inter-NAT typique), latence 200–400 ms
  - `ADVERSARIAL` : toutes paires p = 0.3, latence 800 ms ± 500 ms
**Then** chaque profil expose une méthode `buildMatrix(peers: List<Peer>): Map<Pair<PeerId, PeerId>, Float>` qui génère la matrice de livraison
**And** le profil `INTER_NETWORK_REALISTIC` lit le `networkType` de chaque pair pour appliquer les bonnes probabilités
**And** un fichier `TRANSPORT_PROFILES.md` dans `:simulator` documente la méthodologie de calibration de chaque profil avec **lien explicite vers la matrice empirique du document architecture**
**And** un test unitaire vérifie que `INTER_NETWORK_REALISTIC` produit p < 0.2 entre 2 pairs WIFI et p > 0.8 entre 2 pairs CELLULAR

**Dépendances :** SIM.3

---

### Story SIM.5 : Politique de placement « somme des scores ≥ S »

En tant que **système**,
Je veux **que la sélection des hébergeurs de blocs maximise la somme des scores de joignabilité plutôt que d'appliquer une règle binaire 4G/WiFi**,
Afin que **le placement reste robuste face à des pairs partiellement joignables et soit défendable comme évolution mesurée de la règle de placement initiale**.

**Acceptance Criteria :**

**Given** un cluster de N pairs avec leurs `ReachabilityProfile`
**When** un nouveau bloc doit être placé avec facteur de réplication R
**Then** l'algorithme sélectionne R pairs maximisant `Σ reachability_score` parmi les pairs disponibles
**And** la sélection garantit `Σ score ≥ S` (S configurable, défaut 1.5)
**And** si aucun ensemble de R pairs n'atteint S, le système retente avec R+1 (sur-réplication adaptative) jusqu'à `R_max = 5`
**And** si même R_max ne suffit pas, le placement est marqué `DEGRADED` et un événement est émis vers `NetworkEventRepository`
**And** un test simulé construit un cluster `[score=0.9, 0.6, 0.3, 0.4, 0.4, 0.2]` et vérifie la sélection attendue
**And** **cette story modifie le code applicatif de production**, pas seulement le simulateur — à valider avec l'encadrant avant merge

**Dépendances :** SIM.1, SIM.3

---

### Story SIM.6 : DSL de scénario et harness JUnit

En tant que **développeur ou chercheur**,
Je veux **exprimer un scénario de cluster (membres + profil transport + actions + assertions) en quelques lignes Kotlin**,
Afin que **les scénarios S0–S7 du document architecture soient rejouables en tests JUnit lisibles**.

**Acceptance Criteria :**

**Given** le simulateur SIM.3, les profils SIM.4 et la politique SIM.5
**When** le DSL `scenario { cluster { peer(...) } transport(...) ... }` est implémenté
**Then** un test peut écrire :
```kotlin
@Test fun karim_part_a_l_etranger_S5() = scenario(seed = 42) {
    cluster("Famille Benali") {
        peer("Karim",   network = CELLULAR, battery = 80)
        peer("Sara",    network = WIFI,     battery = 60)
        peer("Amine",   network = CELLULAR, battery = 50)
        peer("Yasmine", network = WIFI,     battery = 90)
        peer("Téta",    network = WIFI,     battery = 70)
    }
    transport(profile = INTER_NETWORK_REALISTIC)
    
    `Sara`.upload("photos.zip", sizeMb = 50)
    `Karim`.goOffline()
    advanceTime(seconds = 120)
    
    assertSuperPeerIs("Amine")
    assertNoBlockLost()
}
```
**And** les scénarios S0, S2, S3, S4, S5, S6, S7 du document architecture sont implémentés comme tests JUnit
**And** chaque scénario s'exécute en moins de 2 secondes wall-clock
**And** chaque scénario est déterministe (rerun avec même seed → même résultat)

**Dépendances :** SIM.3, SIM.4, SIM.5

---

### Story SIM.7 : Collecte et export des métriques

En tant que **chercheur**,
Je veux **que chaque run de scénario produise un rapport JSON avec les métriques clés**,
Afin que **les résultats puissent alimenter des graphiques pour la section évaluation de la thèse**.

**Acceptance Criteria :**

**Given** un scénario qui s'exécute via SIM.6
**When** le scénario se termine
**Then** un fichier `build/sim-reports/<scenario-name>-<seed>-<profile>.json` est produit
**And** le JSON contient au minimum :
  - `transport_profile` (string : "IDEAL" / "INTER_NETWORK_REALISTIC" / "ADVERSARIAL")
  - `direct_transfer_success_rate` (float)
  - `super_peer_relay_count` (int)
  - `bytes_through_super_peer` / `bytes_total` (ratio float, **métrique de centralisation**)
  - `election_duration_ms` (int, ou `null` si pas d'élection dans le scénario)
  - `replication_factor_actual_min` (int)
  - `blocks_lost` (int)
**And** un test snapshot vérifie le format pour le scénario S2 sous chacun des 3 profils

**Dépendances :** SIM.6

---

### Story SIM.8 : Sweep paramétrique et génération de graphiques

En tant que **chercheur**,
Je veux **balayer plusieurs configurations de cluster en un seul run et obtenir un CSV exportable**,
Afin de **produire les graphiques empiriques pour la soutenance**.

**Acceptance Criteria :**

**Given** les stories SIM.6 et SIM.7
**When** la suite `ParametricSweepTest` est lancée
**Then** elle balaye :
  - 5 valeurs de ratio CELLULAR/WIFI dans le cluster : {10 %, 30 %, 50 %, 70 %, 90 %}
  - 3 profils de transport : `IDEAL`, `INTER_NETWORK_REALISTIC`, `ADVERSARIAL`
  - 3 niveaux de churn : {0 %, 10 %, 30 % de pairs partant/revenant par heure simulée}
**And** chaque combinaison est jouée sur 5 seeds distincts (intervalle de confiance)
**And** les 225 runs (5 × 3 × 3 × 5) produisent un CSV `build/sim-reports/parametric-sweep.csv` consolidé
**And** un script Python (`tools/plot_sweep.py`) lit le CSV et génère 3 graphiques PNG :
  1. taux de blocs perdus en fonction du ratio CELLULAR, par profil de transport
  2. ratio de centralisation (`bytes_through_super_peer / bytes_total`) en fonction du profil de transport
  3. durée moyenne d'élection en fonction du churn

**Dépendances :** SIM.7

## 6. Hors-scope (à NE PAS inclure dans cet epic)

- **Toute simulation de la couche transport elle-même** (pas de modèle NAT, pas de hole punching simulé, pas de simulation des Serveurs Relais HA). Le transport est une **entrée stochastique** du simulateur.
- Simulation du protocole DHT/Gossip (utiliser le code existant de l'epic 4 tel quel via les implémentations simulées).
- Simulation du moteur d'erasure coding NDK/C++ (mocker la sortie : K+N blocs avec hashes, pas de vrai calcul).
- Modélisation fine de la batterie/CPU (intégrer plus tard si besoin).
- Émulateur Android avec `tc` (traffic control Linux) — niveau de validation ultérieur, pas dans cet epic.

## 7. Critères de succès de l'epic

L'epic est **DONE** quand :

1. Toutes les stories SIM.1 à SIM.8 sont mergées et testées.
2. Les 7 scénarios S0–S7 du document architecture sont rejouables en `./gradlew :simulator:test` sous 30 secondes total, sous chacun des 3 profils de transport.
3. Le sweep paramétrique a tourné au moins une fois et a produit les 3 graphiques PNG.
4. Le rapport de réunion encadrant peut citer **un chiffre empirique avec son contexte de transport** (ex : *« sous le profil INTER_NETWORK_REALISTIC calibré sur nos mesures terrain, le système ne perd aucun bloc tant que ≥ 30 % du cluster est en CELLULAR »*).

## 8. Notes pour l'agent PM/SM

- **Cadrage thèse à respecter** : la couche transport est hors scope MobiCloud. Le simulateur **modélise** le transport, ne le **résout** pas. Toute story qui ferait dériver le simulateur vers la résolution du NAT ou l'implémentation d'un protocole de transport doit être rejetée.
- L'ordre des stories est strict (SIM.1 → SIM.8), avec dépendances explicites.
- Estimation grossière : **6 à 8 jours-homme** pour un dev solo familier du code base.
- Pas de nouvelle dépendance externe pour SIM.1–7. Pour SIM.8, ajouter `matplotlib` + `pandas` côté Python (script utilitaire hors APK).
- Risque principal : le couplage du domain au framework Android. Si extraction d'un module `:core:domain` JVM-pur s'avère trop lourde, **fallback** = utiliser `Robolectric` dans le module `:simulator` (perte de perf, mais débloque).
- Cet epic ne touche **pas** à l'UX. Aucune story UX requise.
- **À cadrer avec l'encadrant avant de lancer SIM.5** : la formule « somme des scores ≥ S » modifie la politique de placement de production, pas seulement la simulation.
- **Calibration du profil `INTER_NETWORK_REALISTIC`** (story SIM.4) : les valeurs proposées (p ≈ 0.92, 0.88, 0.10) sont des points de départ ; à raffiner avec l'encadrant en fonction des futures mesures terrain disponibles.
