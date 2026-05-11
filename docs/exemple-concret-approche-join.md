# Exemple concret — Approche 2 : JOIN explicite + GPS optionnel

> **Objectif :** illustrer le fonctionnement complet de l'approche retenue pour la délimitation de cluster dans MobiCloud, à travers un scénario réaliste avec 5 téléphones répartis sur deux villes.

---

## Cadre du scénario

### Constantes de l'approche

| Paramètre | Valeur | Rôle |
|-----------|--------|------|
| `MAX_CLUSTER_SIZE` | 10 | Plafond de membres par cluster (charge Super-Pair) |
| `MAX_RADIUS` | 5 km | Rayon géographique max (filtre actif si GPS disponible) |
| `HEARTBEAT_INTERVAL` | 30 s | Période de signal de vie membre → Super-Pair |
| `SP_TIMEOUT` | 90 s | Délai avant déclenchement Bully de remplacement |

### Acteurs

| Téléphone | Position | Réseau | Distance à Alice |
|-----------|----------|--------|------------------|
| **Alice** | Bab Ezzouar (Alger) | Wi-Fi maison | 0 km (référence) |
| **Bob** | Bab Ezzouar | 4G | 3.2 km |
| **Carol** | Bab Ezzouar (café) | Même Wi-Fi qu'Alice | 800 m |
| **Dave** | Oran | 4G | 398 km |
| **Eve** | Oran (proche Dave) | 4G | 398 km (mais 800 m de Dave) |

---

## T = 0 — Alice lance MobiCloud (cold start)

Alice est la première utilisatrice MobiCloud au monde. Aucun pair n'existe encore.

```
Alice :
  1. Multicast UDP sur Wi-Fi    → personne ne répond
  2. GET tracker.mobicloud.io   → liste vide, aucun Super-Pair
  3. Aucun pair connu           → Bully en mode "solo"
  4. Bully timeout (3 s)        → personne ne répond avec un score supérieur
  5. Auto-élection              → Alice devient Super-Pair
  6. Génère clusterId           → "CL-7F3A"
  7. POST tracker /announce :
        {
          nodeId: "alice-pk-hash",
          clusterId: "CL-7F3A",
          superPair: true,
          ip: relay.../alice (via NAT traversal),
          freeBytes: 8 GB,
          gps: { lat: 36.7167, lon: 3.2333 }
        }
```

**État du monde MobiCloud :**

```
Cluster CL-7F3A (Alger) : { Alice (SP) }
```

---

## T = 1 — Bob lance MobiCloud en 4G

Bob est à 3 km d'Alice, en 4G. Pas de découverte multicast possible (réseaux différents).

```
Bob :
  1. Multicast UDP               → impossible (pas de Wi-Fi)
  2. GET tracker /super-peers    → reçoit [Alice]
  3. Établit connexion via HA Relay (4G ↔ relai ↔ Wi-Fi d'Alice ✓)
  4. Envoie JOIN_REQUEST à Alice :
        {
          type: "JOIN_REQUEST",
          from: "bob-pk-hash",
          to: "alice-pk-hash",
          clusterId: "CL-7F3A",
          gps: { lat: 36.7400, lon: 3.2500 },
          freeBytes: 12 GB,
          reliabilityScore: 0.7,
          signature: sig(bob-priv-key, payload)
        }
```

```
Alice (Super-Pair) — ProcessJoinRequestUseCase :
  1. Vérifie signature          → ✓
  2. Haversine(Alice, Bob)      → 3.2 km
  3. 3.2 km < MAX_RADIUS (5)    → ✓
  4. clusterSize (1) < MAX (10) → ✓
  5. Envoie JOIN_ACCEPT :
        {
          type: "JOIN_ACCEPT",
          from: "alice-pk-hash",
          to: "bob-pk-hash",
          clusterId: "CL-7F3A",
          memberSnapshot: [
            { nodeId: "alice-pk-hash", gps: {...}, role: "SP" }
          ],
          signature: sig(alice-priv-key, payload)
        }
  6. Met à jour son registre local (Room DB) :
        members = [Alice, Bob]
```

```
Bob :
  1. Reçoit JOIN_ACCEPT          → vérifie signature ✓
  2. Stocke memberSnapshot localement
  3. clusterId = "CL-7F3A"
  4. Démarre MemberHeartbeatUseCase → ping Alice toutes les 30 s
```

**État :**

```
Cluster CL-7F3A (Alger) : { Alice (SP), Bob }
```

---

## T = 2 — Carol lance MobiCloud sur le même Wi-Fi qu'Alice

Carol est dans le même café qu'Alice. Découverte instantanée via multicast.

```
Carol :
  1. Multicast UDP sur Wi-Fi    → Alice répond !
  2. Voit qu'Alice est SP        (champ superPair=true dans la réponse multicast)
  3. Pas besoin du tracker
  4. Envoie JOIN_REQUEST directement à Alice (LAN, ultra-rapide) :
        {
          type: "JOIN_REQUEST",
          from: "carol-pk-hash",
          clusterId: "CL-7F3A",
          gps: { lat: 36.7180, lon: 3.2380 },
          freeBytes: 4 GB,
          ...
        }
```

```
Alice :
  1. Haversine                   → 800 m  ✓
  2. clusterSize (2) < 10        ✓
  3. JOIN_ACCEPT avec snapshot = [Alice, Bob, (Carol va s'ajouter)]
  4. Diffuse MEMBER_UPDATE à Bob :
        {
          type: "MEMBER_UPDATE",
          event: "JOINED",
          member: { nodeId: "carol-pk-hash", gps: {...} }
        }
```

**État :**

```
Cluster CL-7F3A (Alger) : { Alice (SP), Bob, Carol }
```

---

## T = 3 — Dave lance MobiCloud à Oran (400 km d'Alger)

Cas du rejet par distance.

```
Dave :
  1. GET tracker /super-peers    → reçoit [Alice]   (seul SP au monde)
  2. Envoie JOIN_REQUEST à Alice via relai :
        {
          type: "JOIN_REQUEST",
          from: "dave-pk-hash",
          clusterId: "CL-7F3A",
          gps: { lat: 35.6970, lon: -0.6310 },   // Oran
          ...
        }
```

```
Alice :
  1. Haversine(Alger, Oran)     → 398 km
  2. 398 km > MAX_RADIUS (5)    ✗
  3. Envoie JOIN_REDIRECT :
        {
          type: "JOIN_REDIRECT",
          reason: "OUT_OF_RADIUS",
          distance_km: 398,
          maxRadius_km: 5,
          alternativeSuperPeers: []   // aucun autre SP connu
        }
```

```
Dave :
  1. Reçoit JOIN_REDIRECT, aucune alternative
  2. Pas d'autres candidats → auto-élection Bully solo
  3. Devient Super-Pair
  4. Génère clusterId            → "CL-9C2E"
  5. POST tracker /announce :
        {
          nodeId: "dave-pk-hash",
          clusterId: "CL-9C2E",
          superPair: true,
          gps: { lat: 35.6970, lon: -0.6310 }
        }
```

**État :** 2 clusters distincts existent maintenant.

```
Cluster CL-7F3A (Alger) : { Alice (SP), Bob, Carol }
Cluster CL-9C2E (Oran)  : { Dave (SP) }
```

---

## T = 4 — Eve lance MobiCloud à Oran (800 m de Dave)

Cas où plusieurs Super-Pairs sont disponibles : le nœud choisit le plus proche.

```
Eve :
  1. GET tracker /super-peers    → reçoit [Alice, Dave]
  2. Stratégie : tente d'abord celui dont le GPS annoncé est le plus proche
                 (heuristique optionnelle, sinon JOIN_REQUEST en parallèle)
  3. Calcule Haversine localement :
        - Alice : 398 km
        - Dave  : 800 m  ← gagne
  4. Envoie JOIN_REQUEST à Dave
```

```
Dave (Super-Pair) :
  1. Haversine                   → 800 m  ✓
  2. clusterSize (1) < 10        ✓
  3. JOIN_ACCEPT
```

**État final :**

```
Cluster CL-7F3A (Alger) : { Alice (SP), Bob, Carol }
Cluster CL-9C2E (Oran)  : { Dave (SP), Eve }
```

Les deux clusters peuvent maintenant collaborer en **inter-cluster** via `RequestInterClusterHostingUseCase` (déjà implémenté dans le code) : Alice peut stocker des blocs chez Dave et inversement, en passant par le relai HA.

---

## T = 5 — Vie du cluster (heartbeat continu)

Chaque membre envoie un signal de vie toutes les 30 s à son Super-Pair :

```
Bob   → Alice : { type: "HEARTBEAT", nodeId: "bob-pk-hash",   freeBytes: 11.8 GB }
Carol → Alice : { type: "HEARTBEAT", nodeId: "carol-pk-hash", freeBytes: 3.9 GB }
Eve   → Dave  : { type: "HEARTBEAT", nodeId: "eve-pk-hash",   freeBytes: 6.2 GB }
```

Si un Super-Pair ne reçoit pas de heartbeat d'un membre pendant 90 s, il diffuse :

```
MEMBER_UPDATE { event: "LEFT", member: "bob-pk-hash" }
```

---

## T = 6 — Cas critique : Alice (Super-Pair) tombe

Alice ferme son téléphone. Bob et Carol détectent l'absence.

```
Bob et Carol :
  1. Pas de réponse heartbeat depuis 90 s
  2. Bully redéclenché entre { Bob, Carol }
  3. Bob a un score plus élevé      → Bob gagne
  4. Bob diffuse COORDINATOR :
        { clusterId: "CL-7F3A", superPair: "bob-pk-hash" }
  5. Bob hérite du memberSnapshot qu'Alice lui avait envoyé dans le JOIN_ACCEPT
     → continuité immédiate, pas de re-JOIN nécessaire
  6. Bob POST tracker /announce → remplace Alice comme SP de CL-7F3A
```

**État :**

```
Cluster CL-7F3A (Alger) : { Bob (SP), Carol }
```

Le cluster a survécu sans pertes de données ni interruption de service.

---

## Vue d'ensemble du protocole

```
┌─────────────────────────────────────────────────────────────┐
│  Découverte                                                 │
│  ├─ Wi-Fi : UDP Multicast                                   │
│  └─ 4G    : GET tracker.mobicloud.io/super-peers            │
├─────────────────────────────────────────────────────────────┤
│  Si aucun SP joignable                                      │
│  └─ Auto-élection Bully solo → devient SP d'un nouveau cluster │
│                                                             │
│  Si SP trouvé                                               │
│  └─ JOIN_REQUEST → SP                                       │
│                    ├─ filtre signature                      │
│                    ├─ filtre GPS (si dispo)                 │
│                    ├─ filtre MAX_CLUSTER_SIZE               │
│                    ├─ ACCEPT → snapshot membres             │
│                    └─ REDIRECT → essaye un autre SP         │
├─────────────────────────────────────────────────────────────┤
│  Vie du cluster                                             │
│  ├─ HEARTBEAT toutes les 30 s                               │
│  ├─ MEMBER_UPDATE diffusé par le SP                         │
│  └─ Bully re-déclenché si SP silencieux 90 s                │
└─────────────────────────────────────────────────────────────┘
```

---

## Récapitulatif des messages du protocole

| Message | Émetteur | Destinataire | Rôle |
|---------|----------|--------------|------|
| `JOIN_REQUEST` | Pair candidat | Super-Pair | Demande d'admission, signée, GPS optionnel |
| `JOIN_ACCEPT` | Super-Pair | Pair candidat | Admission validée + snapshot membres |
| `JOIN_REDIRECT` | Super-Pair | Pair candidat | Refus (radius, full) + Super-Pairs alternatifs |
| `MEMBER_UPDATE` | Super-Pair | Tous les membres | Notification JOIN/LEFT |
| `HEARTBEAT` | Membre | Super-Pair | Signal de vie + métadonnées (freeBytes) |
| `COORDINATOR` | Super-Pair (élu) | Tous les membres | Annonce post-Bully du nouveau SP |

---

## Le point clé à retenir

Aucun acteur de ce scénario n'a contacté le relai pour décider d'une **frontière de cluster**. Le relai n'a servi qu'à :

1. **Tracker** — annoncer et découvrir les Super-Pairs (équivalent BitTorrent tracker).
2. **Transport** — faire passer les paquets entre 4G et Wi-Fi quand les pairs ne peuvent pas se joindre directement.

La **décision d'admission**, elle, est 100 % entre pairs. Le Super-Pair élu est seul juge de qui appartient à son cluster, selon ses propres critères locaux (signature, GPS, capacité).

C'est cette propriété qui rend l'approche **défendable en soutenance** sur le principe de décentralisation : le relai est un service utilitaire, pas une autorité.

---

*Document rédigé le 2026-05-11 pour MobiCloud (PFE Naoui) — illustre l'approche retenue dans `comparaison-approches-cluster.md`.*
