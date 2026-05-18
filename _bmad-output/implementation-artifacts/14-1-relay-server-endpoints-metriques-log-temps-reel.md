# Story 14.1 — Relay Server : Endpoints Métriques + Log Circulaire Temps Réel

## Status: done

## Story

**As a** administrateur système,
**I want** des endpoints REST exposant les métriques internes du relay server en temps réel,
**So that** un dashboard web externe peut surveiller la topologie, les événements et la santé du système MobiCloud sans accès direct au process Node.js.

---

## Epic

**Epic 14 — Dashboard Admin Web Temps Réel**

---

## Contexte technique

Le relay server (`relay-server/server.js`) maintient en RAM trois structures principales :
- `sessions` : Map des connexions WebSocket authentifiées
- `signalingRegistry` : Map des nœuds enregistrés (Super-Pairs et membres)
- `relayBuffer` : Map des blocs en attente de livraison store-and-forward

Ces données n'étaient pas exposées à des clients externes. Cette story ajoute une couche d'observabilité REST sans modifier le protocole WebSocket binaire existant.

---

## Changements implémentés

### Nouveaux endpoints HTTP

| Endpoint | Description |
|---|---|
| `GET /metrics/topology` | Topologie complète : nœuds (ip, port, clusterId, reliabilityScore, freeBytes, isSuperPair, isConnected) + liens actifs relay |
| `GET /metrics/clusters` | Vue agrégée par clusterId : Super-Peer élu + electedAt, membres, storage libre, fiabilité moyenne, churnRate |
| `GET /metrics/events` | Compteurs enrichis : authFailures/Successes, electionBroadcasts, forwardedBlocks, droppedSignals, joinEvents, departures, churnRate, uptimeMs |
| `GET /metrics/logs?since=<ts>` | Log circulaire 200 entrées, polling différentiel par timestamp |

Tous les endpoints retournent `Content-Type: application/json` avec headers CORS `Access-Control-Allow-Origin: *`.

### Log circulaire (`realtimeLogs[]`)

Buffer circulaire de 200 entrées. Chaque entrée :
```json
{ "ts": 1700000000000, "level": "INFO|WARN|ERROR", "category": "AUTH|ELECTION|JOIN|DEPART|RELAY|GOSSIP|SERVER", "message": "..." }
```

Catégories instrumentées :
- `AUTH` — succès et échecs d'authentification EC P-256
- `ELECTION` — Super-Peer élu via Bully (REGISTER_PEER)
- `JOIN` — nœud rejoint la présence (JOIN message)
- `DEPART` — nœud déconnecté (WebSocket close ou TTL expiré 60s)
- `RELAY` — bloc forwardé directement ou bufferisé
- `GOSSIP` — signal P2P envoyé ou droppé (destinataire absent)
- `SERVER` — démarrage du process

### Suivi churn (fenêtre glissante 5 min)

```js
const churnEvents = []; // timestamps de départs
function recordChurn(nodeId) { /* push + purge > 5min */ }
function getChurnRate() { /* recent.length / (registry.size + recent.length) * 100 */ }
```

Appelé sur : TTL expiré (`signalingRegistry` TTL timer) + déconnexion WebSocket propre.

### Compteurs `eventCounters` enrichis

Ajouts : `forwardedBlocksFailed`, `droppedSignals`, `joinEvents`, `departures` (en plus des existants).

---

## Acceptance Criteria

- **Given** le relay tourne, **when** `GET /metrics/topology`, **then** JSON avec `nodes[]`, `links[]`, `activeSessions`, `relayNode`
- **Given** le relay tourne, **when** `GET /metrics/clusters`, **then** JSON avec `clusters[]` agrégés par clusterId et `churnRate`
- **Given** un nœud se connecte, **when** `GET /metrics/logs`, **then** entrée `JOIN` apparaît dans les logs en < 2s
- **Given** un nœud se déconnecte, **when** `GET /metrics/logs`, **then** entrée `DEPART` avec reason apparaît
- **Given** `GET /metrics/logs?since=<ts>`, **then** seules les entrées postérieures à `ts` sont retournées
- **Given** requête OPTIONS (preflight CORS), **then** réponse 204 avec headers CORS corrects
- **Given** auth EC P-256 échoue, **then** `authFailures` incrémenté et log `ERROR/AUTH` enregistré
- **Given** churn > 30% sur 5 min, **then** `getChurnRate()` retourne valeur ≥ 30

---

## Fichiers modifiés

- `relay-server/server.js` — ajout endpoints, log circulaire, churn tracker, compteurs enrichis
