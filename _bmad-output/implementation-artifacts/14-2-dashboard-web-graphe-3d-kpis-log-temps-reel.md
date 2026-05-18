# Story 14.2 — Application Web Dashboard : Graphe 3D + KPIs + Log Temps Réel

## Status: done

## Story

**As a** administrateur système,
**I want** un dashboard web accessible sur navigateur affichant en temps réel la topologie 3D des nœuds MobiCloud, les KPIs système et un log d'événements en direct,
**So that** je peux surveiller l'état global du cluster, détecter les anomalies (instabilité, churn, auth échouées) et comprendre exactement ce qui se passe dans le réseau P2P.

---

## Epic

**Epic 14 — Dashboard Admin Web Temps Réel**

---

## Stack technique

| Couche | Technologie |
|---|---|
| Framework | React 18 + TypeScript |
| Build | Vite 6 |
| Graphe 3D | react-force-graph-3d (Three.js) |
| Graphiques KPI | Recharts |
| Style | Tailwind CSS v4 (@tailwindcss/vite) |
| Données | Polling REST vers relay server |

---

## Architecture

```
mobicloud-dashboard/
├── src/
│   ├── services/api.ts          ← fetch helpers (fetchTopology, fetchHealth, fetchEvents)
│   ├── hooks/
│   │   ├── useTopology.ts       ← poll /metrics/topology toutes les 3s
│   │   ├── useHealth.ts         ← poll /health + /metrics/events toutes les 5s + historique circulaire 60pts
│   │   ├── useLogs.ts           ← poll /metrics/logs?since=<ts> toutes les 1.5s (différentiel)
│   │   └── useClusters.ts       ← poll /metrics/clusters toutes les 3s
│   ├── components/
│   │   ├── GraphView3D/         ← graphe 3D interactif (Three.js via react-force-graph-3d)
│   │   ├── NetworkPanel/        ← LineChart Recharts sessions + blocs relay
│   │   ├── ClusterPanel/        ← vue par cluster (Super-Peer, membres, churn)
│   │   └── RealtimeLog/         ← terminal log scrollable auto-scroll
│   └── App.tsx                  ← layout pleine page, 7 colonnes bande basse
├── .env / .env.example          ← VITE_RELAY_URL=http://localhost:10000
└── vite.config.ts
```

---

## Layout

```
┌─────────────────────────────────────────────────────┐
│ Header : statut relay · sessions · nœuds · churn%   │
├─────────────────────────────────────────────────────┤
│                                                     │
│              GRAPHE 3D INTERACTIF                   │
│         (pleine largeur, ~75% hauteur)              │
│                                                     │
├──────┬──────┬──────┬──────┬───────┬───────┬────────┤
│Clust.│Réseau│Sécu. │Stab. │Chart  │Clust. │Log     │
│KPIs  │KPIs  │KPIs  │KPIs  │activ. │panel  │temps   │
│      │      │      │      │réseau │       │réel    │
└──────┴──────┴──────┴──────┴───────┴───────┴────────┘
                          220px fixe
```

---

## Graphe 3D — règles de rendu

| Élément | Rendu |
|---|---|
| Relay Server | Sphère rouge, taille 8, fixe au centre |
| Super-Peer | Sphère jaune (`#facc15`), taille 6 |
| Member connecté | Sphère bleue (`#60a5fa`), taille 3–5 selon freeBytes |
| Member offline | Sphère grise (`#374151`), taille 3 |
| Lien | Arête grise vers le relay (connexion WS active) |
| Tooltip | nodeId · isSuperPair · clusterId · reliabilityScore · freeBytes · ip:port · isConnected |

Le composant utilise un `ResizeObserver` pour passer `width` et `height` dynamiques à `ForceGraph3D` et occuper exactement son conteneur.

---

## Groupes KPI (bande basse)

**Cluster** : Sessions WS actives · Nœuds enregistrés · Super-Peers élus · Membres

**Réseau** : Blocs en relay buffer ⚠(>50) · Blocs forwardés · Élections Bully · Signaux Gossip envoyés

**Sécurité** : Auth réussies · Auth échouées ⚠(>0) · Taux succès auth · Uptime relay

**Stabilité** : Taux de churn 5min ⚠(≥30%) · Départs totaux · Connexions totales · Signaux droppés ⚠(>10)

---

## Log temps réel — couleurs

| Niveau | Couleur |
|---|---|
| INFO | Bleu `#60a5fa` |
| WARN | Orange `#f59e0b` |
| ERROR | Rouge `#ef4444` |

| Catégorie | Couleur |
|---|---|
| AUTH | Violet `#a78bfa` |
| ELECTION | Jaune `#facc15` |
| DEPART | Rouge `#f87171` |
| JOIN | Vert `#34d399` |
| RELAY | Cyan `#38bdf8` |
| GOSSIP | Orange `#fb923c` |
| SERVER | Gris `#94a3b8` |

---

## Configuration

```bash
# .env
VITE_RELAY_URL=http://localhost:10000

# Démarrage
cd mobicloud-dashboard
npm install
npm run dev        # → http://localhost:5173
npm run build      # build production
```

---

## Acceptance Criteria

- **Given** le relay tourne sur port 10000, **when** le dashboard s'ouvre, **then** les nœuds apparaissent dans le graphe 3D en < 5 secondes
- **Given** un nœud est Super-Peer (`isSuperPair: true`), **when** il apparaît dans le graphe, **then** son nœud est jaune et plus grand que les membres
- **Given** le relay est injoignable, **when** le dashboard fetche, **then** badge rouge "Relay offline" sans crash ni exception non gérée
- **Given** un clic sur un nœud du graphe, **then** tooltip affiche nodeId, reliabilityScore, freeBytes, clusterId, ip:port
- **Given** un événement (AUTH, ELECTION, DEPART, JOIN) se produit, **then** il apparaît dans le log en < 2 secondes
- **Given** le taux de churn dépasse 30%, **then** la métrique "Stabilité" s'affiche en rouge et le header indique "⚠"
- **Given** l'utilisateur scrolle vers le haut dans le log, **then** l'auto-scroll se désactive pour permettre la lecture
- **Given** `npm run build`, **then** build TypeScript sans erreur (zéro `error TS`)
- **Given** le dashboard est redimensionné, **then** le graphe 3D s'adapte sans déformation via ResizeObserver

---

## Fichiers créés

- `mobicloud-dashboard/` — application web complète (React + TypeScript + Vite)
- `mobicloud-dashboard/src/services/api.ts`
- `mobicloud-dashboard/src/hooks/useTopology.ts`
- `mobicloud-dashboard/src/hooks/useHealth.ts`
- `mobicloud-dashboard/src/hooks/useLogs.ts`
- `mobicloud-dashboard/src/hooks/useClusters.ts`
- `mobicloud-dashboard/src/components/GraphView3D/index.tsx`
- `mobicloud-dashboard/src/components/NetworkPanel/index.tsx`
- `mobicloud-dashboard/src/components/ClusterPanel/index.tsx`
- `mobicloud-dashboard/src/components/RealtimeLog/index.tsx`
- `mobicloud-dashboard/src/App.tsx`
- `mobicloud-dashboard/.env.example`

---

## Review Findings (2026-05-17)

### Décisions requises (human input needed)

- [x] [Review][Decision] D1 — Graphe 2D utilisé à la place du graphe 3D — **Décision : garder GraphView2D** (déviation intentionnelle, meilleure lisibilité cluster). Mettre à jour la spec pour documenter ce choix. — deferred, intentional
- [x] [Review][Decision] D2 — Layout redesigné (68/32 + panneau droit) vs spec 7 colonnes — **Décision : garder le layout actuel** (KPIs toujours visibles). — deferred, intentional
- [x] [Review][Patch] P16 — D3 résolu → Tooltip : passer de hover à clic (AC4) [`mobicloud-dashboard/src/components/GraphView2D/index.tsx:onNodeHover→onNodeClick`]
- [x] [Review][Patch] P17 — D4 résolu → Couleurs membres : bleu `#60a5fa` pour connectés, gris `#374151` pour offline (opacity 0.35 → couleur fixe) [`mobicloud-dashboard/src/components/GraphView2D/index.tsx:drawNode`]
- [x] [Review][Decision] D5 — Topologie des liens hiérarchique vs liens directs relay — **Décision : garder la hiérarchie** (meilleure lisibilité cluster). — deferred, intentional

### Patches (bugs non ambigus à corriger)

- [x] [Review][Patch] P1 — `logEvent()` : le spread `...meta` peut écraser `ts`, `level`, `category`, `message` si un appelant passe ces clés dans meta [`relay-server/server.js:logEvent`]
- [x] [Review][Patch] P2 — `getChurnRate()` : le dénominateur `signalingRegistry.size + recent.length` est incohérent (double-compte les nœuds qui ont rejoint et quitté dans la fenêtre) [`relay-server/server.js:getChurnRate`]
- [x] [Review][Patch] P3 — `buildClusterView()` : si plusieurs nœuds ont `isSuperPair=true` dans le même cluster, seul le dernier itéré est gardé (écrasement silencieux) [`relay-server/server.js:buildClusterView`]
- [x] [Review][Patch] P4 — `/metrics/logs?since=` : `since=NaN`, négatif ou float non validés — retour silencieux au slice(-50) au lieu d'une 400 [`relay-server/server.js:/metrics/logs handler`]
- [x] [Review][Patch] P5 — Parsing query string via `new URL('http://x' + req.url)` fragile — utiliser `new URLSearchParams(req.url.split('?')[1] ?? '')` [`relay-server/server.js:/metrics/logs handler`]
- [x] [Review][Patch] P6 — `churnEvents` sans plafond de taille : lors d'un départ massif en rafale dans la fenêtre 5min, le tableau croît sans limite [`relay-server/server.js:churnEvents`]
- [x] [Review][Patch] P7 — `BASE` dupliqué dans `useLogs.ts` au lieu d'être importé depuis `api.ts` [`mobicloud-dashboard/src/hooks/useLogs.ts:3`]
- [x] [Review][Patch] P8 — Conversion RGBA cassée (dead code) : `baseColor.replace('#','rgba(') + ',0.06)'` produit `rgba(ef4444,0.06)` invalide — supprimer la ligne morte [`mobicloud-dashboard/src/components/GraphView2D/index.tsx:~313`]
- [x] [Review][Patch] P9 — `drawLink` : garde `!src?.x` traite `x=0` comme falsy → lien d'un nœud à x=0 non dessiné [`mobicloud-dashboard/src/components/GraphView2D/index.tsx:drawLink`]
- [x] [Review][Patch] P10 — `Promise.all([fetchHealth(), fetchEvents()])` : si l'un des deux fail, toute la donnée est perdue même si l'autre a réussi [`mobicloud-dashboard/src/hooks/useHealth.ts:poll`]
- [x] [Review][Patch] P11 — `spByCluster` ignore les Super-Peers avec `clusterId === ''` → leurs membres sont rattachés au relay à tort [`mobicloud-dashboard/src/components/GraphView2D/index.tsx:buildGraphData`]
- [x] [Review][Patch] P12 — Tooltip peut dépasser le bas du conteneur (seul le haut est clampé) [`mobicloud-dashboard/src/components/GraphView2D/index.tsx:tooltip`]
- [x] [Review][Patch] P13 — `isAtBottom` initialisé à `true` → le premier batch de logs force un scroll même si l'utilisateur n'est pas en bas [`mobicloud-dashboard/src/components/RealtimeLog/index.tsx:isAtBottom`]
- [x] [Review][Patch] P14 — `window.d3` peut être `undefined` si D3 est bundlé via npm (collision force silencieusement ignorée) [`mobicloud-dashboard/src/components/GraphView2D/index.tsx:useEffect forces`]
- [x] [Review][Patch] P15 — Bande basse à 200px au lieu des 220px spécifiés [`mobicloud-dashboard/src/App.tsx:height:200`]

### Déférés (pre-existing, non actionnables maintenant)

- [x] [Review][Defer] W1 — `ForceGraphMethods` typé `any` — pre-existing [`mobicloud-dashboard/src/components/GraphView2D/index.tsx`] — deferred, pre-existing
- [x] [Review][Defer] W2 — Clé de log instable sous slice (index `i`) — impact faible, log append-only [`mobicloud-dashboard/src/components/RealtimeLog/index.tsx`] — deferred, pre-existing
- [x] [Review][Defer] W3 — Timers de polling indépendants sans coordination — décision d'architecture [`mobicloud-dashboard/src/hooks/`] — deferred, pre-existing
- [x] [Review][Defer] W4 — Palette cluster réinitialisée si tri change — pré-existant [`mobicloud-dashboard/src/components/GraphView2D/index.tsx`] — deferred, pre-existing
- [x] [Review][Defer] W5 — `recordChurn` non appelé sur LEAVE/déregistrement explicite — dépend du protocole [`relay-server/server.js`] — deferred, pre-existing
- [x] [Review][Defer] W6 — CORS wildcard + pas d'auth sur `/metrics/*` — intentionnel pour dashboard local [`relay-server/server.js`] — deferred, pre-existing
