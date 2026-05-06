# Test Automation Summary — Epic 9 (Stockage Inter-Cluster)

**Date** : 2026-05-06
**Périmètre** : Stories 9.1 → 9.4 (Relais HA + inter-cluster)
**Framework** : Jest 29 (Node.js 20+, `testEnvironment: 'node'`)

## Stratégie

L'epic 9 ajoute du protocole serveur (`REGISTER_PEER` enrichi en clusterId/freeBytes,
nouvelle paire `REQUEST_BLOCK`/`REQUEST_BLOCK_FORWARDED`) et de la logique Android
(use-cases inter-cluster, wrapper `BlockDownloaderWithRelay`).

- **Couche serveur — testée E2E ici** : un vrai serveur HTTP+WebSocket est
  démarré sur un port éphémère, connecté par des clients `ws` réels qui
  effectuent la handshake AUTH EC P-256 puis échangent des frames binaires.
  C'est le niveau de test où les bugs de framing, de routage inter-session et de
  gestion des erreurs apparaissent.
- **Couche Android — déjà testée JVM** : tests unitaires MockK existants
  (`SignalingRepositoryImplTest`, `RelayWebSocketClientTest`, `RelayRepositoryImplTest`,
  `BlockDownloaderWithRelayTest`, `RequestInterClusterHostingUseCaseTest`,
  `DistributeEncryptedBlocksUseCaseTest`, `RespondToBlockRequestUseCaseTest`,
  `NodeSettingsRepositoryImplTest`). Pas re-testée ici — cf. limites.
- **E2E mobile complet** : non implémenté (cf. section "Limites").

## Generated Tests

### E2E Tests (nouveau)

- [x] `relay-server/server.e2e.test.js` — 13 tests, ~1 s
  - **9.1** — REGISTER_PEER avec `clusterId` UUID v4 → annuaire (2 tests).
  - **9.2** — `clusterId` + `freeBytes` exposés dans GET_PEERS, defaults legacy,
    coercion negative → 0 + warn (3 tests).
  - **9.3** — UPLOAD inter-cluster zero-knowledge (FORWARD intact + ACK), buffer
    + flush à la (re)connexion du destinataire (2 tests).
  - **9.4** — `REQUEST_BLOCK` happy-path forward, dest absent → ERROR (no-buffer),
    self-loop AC#10, payload longueur invalide, blockId malformé (5 tests).
  - `/health` — endpoint expose `registeredSuperPeers` (1 test).

### Tests existants (rappel — non régressés)

- [x] `relay-server/server.test.js` — 49 tests (`buildFrame`/`parseFrame`,
  `verifyAuth`, `handleRegisterPeer`, `handleJoin`, `handleGetPeers`,
  `handleUpload`, `handleRequestBlock`).

## Coverage E2E

- **Stories Epic 9** : 4/4 couvertes côté serveur — ACs critiques (rétrocompat
  legacy, coercion clusterId/freeBytes, no-loop, no-buffer pull) explicitement
  testés via WebSocket réel.
- **Cross-coupures** : la combinaison [auth EC P-256 + framing binaire +
  signaling enrichi + UPLOAD/FORWARD + REQUEST_BLOCK pull] ne pouvait pas être
  exercée par les tests handler unitaires existants — c'est le gain principal
  des E2E ajoutés.

## Comment exécuter

```bash
cd relay-server
npm test                      # toute la suite (server.test.js + server.e2e.test.js)
npx jest server.e2e.test.js   # uniquement E2E Epic 9
```

Résultat actuel : **62/62 verts** (49 unit + 13 E2E), ~1.1 s total.

## Limites — pas dans cette livraison

- **E2E mobile bout-en-bout (Android ↔ Relay ↔ Android)** : nécessiterait deux
  émulateurs/devices + instrumented tests + déploiement du serveur. Reporté à
  une story de hardening QA dédiée. Les tests JVM Android existants couvrent la
  logique de chaque use-case en isolation.
- **Tests de charge / TTL** : `TTL_MS = 60_000` n'est pas exercé (laisserait les
  tests trop lents). Couvert structurellement par les tests handler existants.
- **NAT-traversal réel** : ces E2E tournent en `localhost` ; la validation
  4G↔WiFi via HA Relay reste IRL (cf. mémoire `project_intercluster_test_result.md`).

## Next Steps

- Intégrer `server.e2e.test.js` dans la CI (déjà couvert par `npm test` du
  `relay-server/package.json`).
- Optionnel : ajouter un test "TTL court" (override `TTL_MS` via env) pour
  exercer l'expiration d'annuaire — utile si une régression "le clusterId
  survit à l'expiration TTL" apparaît un jour.
