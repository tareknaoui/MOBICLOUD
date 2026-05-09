# MobiCloud — Rapport de Présentation Jury
**Système de Stockage Distribué Pair-à-Pair sur Mobile**

> **Auteur :** Anis Naoui  
> **Date :** Mai 2026  
> **Encadrant :** —  
> **Établissement :** —

---

## Résumé Exécutif

MobiCloud est une application Android de **stockage distribué pair-à-pair** (P2P) permettant à des appareils mobiles de stocker et récupérer des fichiers de manière **décentralisée**, **chiffrée** et **tolérante aux pannes**, sans dépendre d'un serveur central ni d'un cloud tiers. L'application implémente une architecture super-peer/cluster, un protocole de gossip épidémique, un codage d'effacement Reed-Solomon, un chiffrement de bout en bout AES-256-GCM, et une infrastructure de relais haute disponibilité WebSocket.

---

## 1. Vue d'Ensemble Architecturale

### 1.1 Modèle de Distribution

```
┌───────────────────────────────────────────────────────────┐
│                     CLUSTER LOCAL                          │
│                                                           │
│   [Pair A] ─── TCP direct ──── [Pair B]                  │
│       │                             │                     │
│       └──── [Super-Peer] ──────────┘                     │
│                  │                                        │
│           UDP mDNS LAN                                    │
└──────────────────┼────────────────────────────────────────┘
                   │  WebSocket (4G/NAT traversal)
         ┌─────────▼─────────┐
         │  Relais HA (x2)   │  ← 2 instances Node.js déployées
         │  Instance 1       │     sur Render (production)
         │  Instance 2       │
         └─────────┬─────────┘
                   │
┌──────────────────▼────────────────────────────────────────┐
│                   CLUSTER DISTANT                          │
│                                                           │
│   [Pair X] ─── TCP direct ──── [Pair Y]                  │
│       └──── [Super-Peer] ───────┘                        │
└───────────────────────────────────────────────────────────┘
```

**Matrice de connectivité réelle testée :**
| Scénario | Mode | Résultat |
|---|---|---|
| 4G ↔ 4G | TCP direct | ✅ Fonctionne |
| 4G ↔ WiFi | Relais WebSocket | ✅ Fonctionne |
| WiFi ↔ WiFi (différents NAT) | Relais WebSocket | ✅ Fonctionne |
| WiFi ↔ WiFi (même LAN) | TCP direct + mDNS | ✅ Fonctionne |

---

## 2. Fonctionnalités Implémentées

### 2.1 Identité Cryptographique des Nœuds

Chaque appareil génère une **identité permanente** basée sur une paire de clés EC P-256 stockée dans l'Android Keystore matériel (TEE/StrongBox).

```
nodeId = SHA-256(publicKey)[0..15] → hex string 32 caractères
```

**Propriétés :**
- Clé générée une seule fois, persistante aux réinstallations
- Stockée dans le Keystore matériel (protégée par le TEE si disponible)
- Pas de serveur d'identité central : l'identité est autosuffisante
- Permet la vérification de signature cryptographique sur toutes les opérations critiques

---

### 2.2 Stockage Distribué avec Codage d'Effacement

**Le fichier n'est jamais stocké entier chez un seul pair.** Il est découpé en fragments via le codage d'effacement Reed-Solomon (k données + n parité).

#### Pipeline de Stockage (Upload)

```
Fichier original
       │
       ▼
[Génération clé maître aléatoire 256 bits]
       │
       ▼
[Codage d'effacement Reed-Solomon (k=3, n=2)]
 → 5 fragments : 3 données + 2 parité
       │
       ▼
[Chiffrement AES-256-GCM par fragment]
 HKDF-SHA256(clé maître, index fragment) → clé dérivée
       │
       ▼
[ECIES : wrapping de la clé maître avec EC P-256]
       │
       ▼
[Distribution sur K+N pairs distincts via TCP / Relais]
       │
       ▼
[Mise à jour DHT : blockId → {ownerNodeId, replicaNodeIds[]}]
       │
       ▼
[Dissémination DHT via Gossip Bloom Filter]
```

#### Pipeline de Récupération (Download)

```
Demande de fichier (blockId)
       │
       ▼
[Lookup DHT → liste des nœuds hébergeant les fragments]
       │
       ▼
[Téléchargement parallèle de K fragments quelconques sur K+N]
 (TCP direct préféré, relais WebSocket en fallback)
       │
       ▼
[Déchiffrement AES-256-GCM de chaque fragment]
       │
       ▼
[Décodage Reed-Solomon : reconstruction depuis n'importe quels K fragments]
       │
       ▼
[Vérification intégrité + suppression du rembourrage]
       │
       ▼
Fichier original reconstruit
```

**Point remarquable :** avec k=3, n=2, l'application peut **perdre jusqu'à 2 pairs** sur 5 et toujours reconstruire le fichier sans perte de données.

---

### 2.3 Chiffrement de Bout en Bout (Zero-Knowledge)

| Algorithme | Usage |
|---|---|
| AES-256-GCM | Chiffrement des fragments |
| HKDF-SHA256 (RFC 5869) | Dérivation de clé par fragment |
| EC P-256 (secp256r1) | Paire de clés d'identité |
| ECIES (ECDH + HKDF + AES) | Wrapping de la clé maître |
| SHA-256 with ECDSA | Signatures de messages réseau |
| Android Keystore (TEE) | Protection matérielle des clés privées |

**Propriétés Zero-Knowledge :** le relais n'a jamais accès aux données en clair. Les blocs transitent chiffrés de manière opaque. Même un attaquant qui compromet le relais ne peut pas déchiffrer les données.

---

### 2.4 DHT (Table de Hachage Distribuée) avec Gossip Épidémique

#### Consistent Hashing Ring
- SHA-256 mappe chaque `blockId` → partition → nœud responsable
- Distribution équilibrée des responsabilités de catalogue

#### Protocole Gossip (Bloom Filter Delta-Sync)
- Chaque nœud maintient un **Bloom Filter 1024 bits, 3 fonctions de hachage** de ses entrées DHT connues
- Toutes les **2 secondes**, chaque nœud envoie son Bloom Filter à un sous-ensemble aléatoire de pairs
- Les récepteurs identifient les entrées manquantes et répondent avec le delta
- Dissémination épidémique : une information se propage à tout le cluster en O(log N) cycles

#### CRDT Tombstones
- Les suppressions sont propagées via des **entrées tombstone** horodatées
- Résolution de conflits sans coordination centralisée
- Expiration automatique des tombstones après 1 heure

---

### 2.5 Topologie Super-Peer et Élection Bully

#### Rôles

| Rôle | Responsabilités |
|---|---|
| **Regular Peer** | Héberge des blocs, télécharge, participe au gossip |
| **Super-Peer** | + Orchestration des réparations, relayage inter-cluster, annonce au tracker, élection |

#### Élection Super-Peer (Algorithme Bully)
1. Déclenchement : absence de Super-Peer détectée, ou abdication du Super-Peer actuel
2. Chaque nœud calcule son **score de fiabilité** [0.0, 1.0]
3. L'élection sélectionne le nœud avec le score le plus élevé (tiebreaker : ordre lexicographique du nodeId)
4. Le gagnant s'enregistre auprès du relais avec son `clusterId` UUID v4
5. Mandat de **30 minutes** puis abdication automatique (rotation)
6. Cooldown de 5 minutes après victoire

**Propriété clé :** n'importe quel pair peut devenir Super-Peer → pas de point de défaillance unique au niveau du rôle.

---

### 2.6 Score de Fiabilité

Calculé toutes les **30 secondes** par chaque nœud pour lui-même et ses pairs observés.

**Facteurs :**
- Durée de disponibilité (uptime)
- Réactivité réseau (temps de réponse)
- Transferts de blocs réussis
- Stabilité réseau (WiFi vs 4G)

**Utilisation :**
- Sélection des pairs optimaux pour la distribution de blocs
- Critère primaire pour l'élection Super-Peer
- Diffusé dans le registre du relais (champ `reliabilityScore`)

---

### 2.7 Réparation Automatique et Migration

**Problème résolu :** si un pair quitte le réseau, les fragments qu'il hébergeait deviennent inaccessibles. Le nombre de répliques descend en dessous du seuil K+N.

**Solution MobiCloud :**

1. **Détection proactive** : le Super-Peer scanne toutes les **10 secondes** les blocs sous-répliqués
2. **Planification de migration** : le Super-Peer calcule un plan de migration (quel fragment vers quel nœud)
3. **Signature cryptographique du plan** : anti-rejeu (fenêtre ±30 secondes), signature EC P-256
4. **Exécution distribuée** : les pairs exécutent les directives après vérification de signature
5. **Avis de départ** : un nœud qui se déconnecte proprement diffuse un `DepartureNoticeMessage` pour déclencher une réparation immédiate

#### Circuit Breaker (Anti-Tempête de Réparation)
- Surveille le **taux de churn** sur une fenêtre glissante de 5 minutes
- Si > 30% des pairs transitent vers INACTIVE → **disjoncteur ouvert** (réparations suspendues)
- Si < 10% → **disjoncteur fermé** (réparations reprises)
- Évite les cascades de réparations en situation de panne réseau massive

---

### 2.8 Infrastructure Relais Haute Disponibilité

#### Protocole binaire custom (WebSocket)
| Opcode | Nom | Description |
|---|---|---|
| 0x01 | AUTH | Authentification avec signature EC P-256 |
| 0x02 | AUTH_OK | Confirmation d'authentification |
| 0x03 | REGISTER_PEER | Enregistrement Super-Peer dans le registre |
| 0x04 | GET_PEERS | Requête de liste de Super-Peers |
| 0x05 | PEERS | Réponse avec liste de Super-Peers |
| 0x06 | UPLOAD | Dépôt de bloc (store-and-forward) |
| 0x07 | FORWARD | Livraison de bloc au destinataire |
| 0x08 | ACK | Accusé de réception de bloc |
| 0x09 | PING | Keepalive |
| 0x0A | PONG | Réponse keepalive |
| 0x0B | JOIN | Annonce de présence (pair régulier ou super-peer) |
| 0x0C | REQUEST_BLOCK | Requête de bloc inter-cluster |
| 0x0D | REQUEST_BLOCK_FORWARDED | Requête de bloc transférée |
| 0xFF | ERROR | Erreur protocolaire |

**Sécurité du relais :**
- Authentification obligatoire à la connexion (10 secondes timeout)
- Vérification de signature EC P-256 + fenêtre anti-rejeu 30s
- Validation IP (IPv4/IPv6, pas de hostname)
- TTL 60 secondes sur les blocs en attente
- Détection de zombies via ping/pong WebSocket toutes les 30s
- Arrêt gracieux SIGTERM avec timeout de sécurité 5s
- Endpoint `/health` avec statistiques temps réel

**Déploiement HA testé :**
- 2 instances Node.js déployées sur Render (infrastructure cloud)
- Test IRL 4G ↔ WiFi validé : fonctionne, limité par le débit upload 4G (pas un bug logique)

---

### 2.9 Anti-Sybil : Hashcash Proof-of-Work

Pour rejoindre le réseau, chaque nœud doit calculer un **Hashcash** :
- Difficulté : **18 bits de zéros en tête** (SHA-256)
- Temps de calcul : ~1 seconde sur ARM mobile
- Empêche la création en masse de fausses identités (attaque Sybil)
- Cache du token : pas de recalcul à chaque reconnexion

---

### 2.10 Découverte de Pairs Multi-Mode

| Mode | Protocole | Portée |
|---|---|---|
| **LAN** | mDNS / UDP broadcast | Même réseau WiFi |
| **Inter-cluster** | WebSocket Relais HA | Internet (4G/NAT) |
| **Direct** | TCP | Pairs déjà connus |

**Tracker BitTorrent-style :** seuls les Super-Peers s'annoncent au relais. Les pairs réguliers obtiennent la liste des Super-Peers via `GET_PEERS` et se connectent à leur cluster.

---

## 3. Interface Utilisateur

### 3.1 Dashboard (Tableau de Bord)

**Informations temps réel affichées :**
- Statut du service P2P (actif/arrêté)
- Score de fiabilité (visualisation gauge)
- Nombre de pairs actifs
- Type réseau (WiFi / 4G)
- Durée de disponibilité (uptime formaté)
- Rôle du nœud (Super-Peer / Pair régulier)
- Indicateur de stabilité réseau (état circuit breaker)
- Badge de connexion au relais
- Cartes KPI de diagnostics
- Console de log réseau temps réel (RadarLogConsole)

### 3.2 Explorateur de Fichiers

**Fonctionnalités :**
- Liste des fichiers du catalogue distribué
- Pull-to-refresh
- Upload de fichier (sélecteur système)
- Téléchargement avec indicateur de progression
- Progression d'assemblage (décodage Reed-Solomon)
- Bottom sheet d'ouverture du fichier reconstruit
- Feedback Snackbar

### 3.3 Écran Réseau (Topologie Cluster)

- Visualisation des membres du cluster local
- Visualisation des clusters distants (inter-cluster)
- Statut des connexions

---

## 4. Couverture de Tests

**65+ classes de tests** couvrant :

| Catégorie | Tests Notables |
|---|---|
| Algorithmes | BloomFilter, ConsistentHashRing, Gossip, Bully Election |
| Cryptographie | Keystore, FragmentCipher, ECIES round-trip, Hashcash PoW |
| Protocoles réseau | RelayFraming, BlockTransfer, TCP ACK binding, WebSocket client |
| Storage | DHT CRUD, Tombstone CRDT, Hosted blocks, Room DAOs |
| Résilience | CircuitBreaker, MigrationPlan, ReplicationPlan |
| Simulation | LargeScaleNetworkSimulationTest, NetworkScaleBenchmarkTest |
| Matériel (androidTest) | KeyStore réel, NDK JNI, Foreground Service |

**Test de simulation grande échelle :** simulation de comportement cluster multi-nœuds, validation des protocoles à l'échelle.

---

## 5. Modules et Technologies

### Application Android

| Couche | Technologies |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | Clean Architecture, MVVM, Repository Pattern |
| DI | Dagger/Hilt (16+ modules) |
| Async | Kotlin Coroutines, StateFlow, Flow |
| Base de données | Room (SQLite) — 7 DAOs, 8 entités |
| Preferences | DataStore |
| Réseau | OkHttp, Retrofit, WebSocket |
| Sérialisation | Protocol Buffers, Kotlinx.serialization |
| Crypto natif | Android Keystore TEE, JNI (Reed-Solomon) |
| Auth | Firebase Authentication, Credential Manager |

### Serveur Relais

| Technologie | Usage |
|---|---|
| Node.js | Runtime serveur |
| `ws` (WebSocket) | Protocole binaire custom |
| EC P-256 (`crypto` natif) | Vérification de signature |
| UUID v4 | Identifiants de cluster |
| Render.com | Déploiement HA (2 instances) |

---

## 6. Résultats Quantitatifs

| Métrique | Valeur |
|---|---|
| Fragments par fichier (défaut) | 5 (k=3, n=2) |
| Tolérance aux pannes | 2 pairs perdus sur 5 → fichier intact |
| Difficulté Hashcash | 18 bits (SHA-256) |
| Intervalle gossip | 2 secondes |
| Détection pair mort | 15 secondes |
| Scan de réparation | 10 secondes |
| Recalcul fiabilité | 30 secondes |
| Taille Bloom Filter | 1024 bits, 3 hash |
| Capacité registre relais | 100 Super-Peers, 500 blocs en attente |
| Seuil circuit breaker ouverture | 30% de churn sur 5 min |
| Seuil circuit breaker fermeture | 10% de churn sur 5 min |
| Mandat Super-Peer | 30 minutes, puis rotation |
| Modules DI Hilt | 16+ modules |
| Classes de tests | 65+ |
| Lignes serveur relais | 451 lignes (production) |
| Opcodes protocole binaire | 14 opcodes distincts |

---

## 7. Contributions Originales

1. **Architecture super-peer/cluster sur mobile natif Android** — topologie P2P sans serveur central, adaptée aux contraintes mobiles (batterie, connectivité intermittente, NAT)

2. **Protocole de signaling HA binaire custom** — protocole WebSocket binaire avec authentification cryptographique, anti-rejeu, store-and-forward, déployé en production sur 2 instances

3. **Pipeline de stockage décentralisé chiffré** — combinaison codage d'effacement + chiffrement par fragment + distribution multi-cluster, sans qu'aucun nœud ne possède le fichier complet

4. **Gossip Bloom Filter pour DHT mobile** — synchronisation de catalogue distribuée adaptée aux contraintes de bande passante mobile (delta uniquement, pas de flooding)

5. **Réparation automatique avec circuit breaker** — détection proactive de sous-réplication, orchestration par le Super-Peer, protection anti-tempête de réparation

6. **Anti-Sybil Hashcash sur mobile** — preuve de travail calculée sur ARM pour freiner la création d'identités malveillantes

---

## 8. Ce qui Différencie MobiCloud

| Propriété | MobiCloud | Solutions Cloud Classiques |
|---|---|---|
| Serveur central | ❌ Aucun | ✅ Obligatoire |
| Données en clair côté serveur | ❌ Jamais | ✅ Possible |
| Tolérance aux pannes sans admin | ✅ Automatique | ✅ Avec infrastructure |
| Fonctionne sans Internet | ✅ LAN (mDNS) | ❌ |
| Identité décentralisée | ✅ Cryptographique | ❌ Compte utilisateur central |
| Coût hébergement données | ≈ 0 (entre pairs) | Facturé au GB |
| Résistance à la censure | ✅ Forte | ❌ Faible |

---

*Rapport généré le 08 mai 2026*
