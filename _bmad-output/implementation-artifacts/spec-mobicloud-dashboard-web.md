---
title: 'MobiCloud Admin Dashboard Web'
type: 'feature'
created: '2026-05-17'
status: 'in-review'
baseline_commit: '37235e5a751d1d8fc1db4f3658505699223ac9e2'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Il n'existe pas de dashboard web permettant à un admin de surveiller en temps réel l'état du système MobiCloud distribué (topologie des nœuds, KPIs stockage, réseau, sécurité).

**Approach:** Créer une app web React/TypeScript séparée (`mobicloud-dashboard/`) connectée au relay server existant via WebSocket et polling REST. Ajouter les endpoints `/metrics/topology` et `/metrics/events` au relay server. Afficher la topologie en graphe 3D interactif (react-force-graph-3d) + panneaux KPI avec Recharts.

## Boundaries & Constraints

**Always:**
- Données 100% temps réel depuis le relay server existant (`relay-server/server.js`)
- Le graphe 3D montre les nœuds réels connectés au relay (pas mock)
- CORS activé sur les nouveaux endpoints du relay server
- App web déployable indépendamment (Vite + npm)
- Stack : React 18 + TypeScript + Vite + react-force-graph-3d + Recharts + Tailwind CSS

**Ask First:**
- Si l'URL du relay server en production est différente de `localhost:10000`
- Si on ajoute de l'authentification au dashboard web

**Never:**
- Modifier le protocole WebSocket binaire existant (MSG types, framing)
- Ajouter de la persistance côté relay (tout reste en RAM)
- Toucher à l'app Android

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Nœuds connectés | relay `signalingRegistry` contient N entrées | Graphe 3D affiche N nœuds colorés selon rôle | — |
| Aucun nœud | registry vide | Graphe vide + badge "0 nœuds actifs" | Message "Aucun nœud connecté" |
| Relay injoignable | fetch `/metrics/topology` échoue | Badge rouge "Relay offline" | Retry toutes les 5s |
| Super-Peer élu | `isSuperPair: true` pour un nœud | Nœud affiché en jaune, plus grand | — |
| Nœud déconnecté | nœud disparaît du registry (TTL 60s) | Nœud retiré du graphe au prochain poll | — |

</frozen-after-approval>

## Code Map

- `relay-server/server.js:527-546` -- Handler HTTP existant à étendre avec `/metrics/topology` et `/metrics/events`
- `relay-server/server.js:1-35` -- Constantes et structures `signalingRegistry`, `sessions`, `relayBuffer`
- `mobicloud-dashboard/` -- Nouveau répertoire app web (à créer)
- `mobicloud-dashboard/src/components/GraphView3D/` -- Composant graphe 3D (react-force-graph-3d)
- `mobicloud-dashboard/src/components/KpiCards/` -- Cartes KPI (sessions, nœuds, blocs)
- `mobicloud-dashboard/src/components/NetworkPanel/` -- Graphiques Recharts (activité relay)
- `mobicloud-dashboard/src/hooks/useTopology.ts` -- Polling `/metrics/topology` toutes les 3s
- `mobicloud-dashboard/src/hooks/useHealth.ts` -- Polling `/health` toutes les 5s
- `mobicloud-dashboard/src/services/api.ts` -- Fetch helpers vers relay server

## Tasks & Acceptance

**Execution:**
- [ ] `relay-server/server.js` -- Ajouter `GET /metrics/topology` qui retourne `signalingRegistry` sérialisé + liens actifs (sessions) + `GET /metrics/events` qui retourne compteurs auth failures, election broadcasts, forwards depuis démarrage du process -- exposer les données déjà en mémoire
- [ ] `relay-server/server.js` -- Ajouter header CORS `Access-Control-Allow-Origin: *` sur tous les endpoints HTTP
- [ ] `mobicloud-dashboard/package.json` -- Initialiser projet Vite + React + TypeScript avec deps : `react-force-graph-3d`, `recharts`, `three`, `tailwindcss`
- [ ] `mobicloud-dashboard/src/services/api.ts` -- Fonctions `fetchTopology()`, `fetchHealth()`, `fetchEvents()` avec base URL configurable via `VITE_RELAY_URL`
- [ ] `mobicloud-dashboard/src/hooks/useTopology.ts` -- Hook avec polling 3s, retourne `{ nodes, links, error }`
- [ ] `mobicloud-dashboard/src/hooks/useHealth.ts` -- Hook avec polling 5s, retourne `HealthSnapshot`
- [ ] `mobicloud-dashboard/src/components/GraphView3D/index.tsx` -- Graphe 3D : nœuds colorés (jaune=SuperPeer, bleu=Member, rouge=Relay), taille proportionnelle à `freeBytes`, tooltip au clic avec nodeId + reliabilityScore + freeBytes + clusterId
- [ ] `mobicloud-dashboard/src/components/KpiCards/index.tsx` -- 4 cartes : Sessions actives, Nœuds enregistrés, Super-Peers, Blocs en attente relay
- [ ] `mobicloud-dashboard/src/components/NetworkPanel/index.tsx` -- LineChart Recharts historique sessions/blocs sur 60 points (1 par poll)
- [ ] `mobicloud-dashboard/src/App.tsx` -- Layout principal : header "MobiCloud Admin", graphe 3D à gauche (60% width), KpiCards + NetworkPanel à droite
- [ ] `mobicloud-dashboard/.env.example` -- `VITE_RELAY_URL=http://localhost:10000`

**Acceptance Criteria:**
- Given le relay server tourne sur port 10000, when le dashboard s'ouvre, then les nœuds connectés apparaissent dans le graphe 3D en moins de 5 secondes
- Given un nœud est Super-Peer (`isSuperPair: true`), when il apparaît dans le graphe, then son nœud est jaune et plus grand que les membres
- Given le relay est injoignable, when le dashboard tente de fetcher, then un badge rouge "Relay offline" s'affiche sans crash
- Given des nœuds sont connectés, when on clique sur un nœud dans le graphe 3D, then un tooltip affiche nodeId, reliabilityScore, freeBytes, clusterId
- Given le dashboard tourne, when les sessions changent, then les KPI cards se mettent à jour toutes les 5 secondes

## Design Notes

**Topologie du graphe :**
- Nœud central fixe = Relay Server (rouge, grand)
- Chaque nœud authentifié = arête vers le relay (connexion WS active)
- Pas d'arêtes P2P directes (relay ne connaît pas les liens LAN directs)

**Endpoint `/metrics/topology` response shape :**
```json
{
  "nodes": [
    { "id": "nodeId", "isSuperPair": true, "clusterId": "...", "reliabilityScore": 0.87, "freeBytes": 4000000000, "ip": "192.168.1.5", "port": 9000 }
  ],
  "activeSessions": 3,
  "relayNode": { "id": "relay", "label": "Relay Server" }
}
```

**Endpoint `/metrics/events` response shape :**
```json
{ "authFailures": 0, "electionBroadcasts": 12, "forwardedBlocks": 5, "uptimeMs": 38400000 }
```

## Verification

**Commands:**
- `cd mobicloud-dashboard && npm run build` -- expected: build sans erreur TypeScript
- `cd mobicloud-dashboard && npm run dev` -- expected: dashboard accessible sur localhost:5173
- `curl http://localhost:10000/metrics/topology` -- expected: JSON avec `nodes` array et `activeSessions`
- `curl http://localhost:10000/metrics/events` -- expected: JSON avec compteurs events
