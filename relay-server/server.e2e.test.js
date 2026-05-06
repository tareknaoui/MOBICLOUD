'use strict';

// Tests E2E Epic 9 — Stockage Inter-Cluster (Stories 9.1 → 9.4)
// Pilote le vrai serveur HTTP+WebSocket avec des clients `ws` authentifiés EC P-256,
// validant les flots end-to-end : signaling enrichi (clusterId/freeBytes), UPLOAD/FORWARD
// inter-cluster (zero-knowledge) et REQUEST_BLOCK pull-mode (Story 9.4).

const crypto = require('crypto');
const WebSocket = require('ws');

let mod;
let port;

beforeAll(async () => {
  mod = require('./server');
  await new Promise((resolve, reject) => {
    mod.httpServer.once('error', reject);
    mod.httpServer.listen(0, '127.0.0.1', () => {
      port = mod.httpServer.address().port;
      resolve();
    });
  });
});

afterAll(async () => {
  for (const [, s] of mod.sessions.entries()) {
    try { s.ws.close(1001, 'teardown'); } catch (_) { /* déjà fermée */ }
  }
  mod.signalingRegistry.forEach(e => clearTimeout(e.ttlTimer));
  mod.relayBuffer.forEach(arr => arr.forEach(e => clearTimeout(e.ttlTimer)));
  mod.sessions.clear();
  mod.signalingRegistry.clear();
  mod.relayBuffer.clear();
  await new Promise((resolve) => mod.httpServer.close(resolve));
});

afterEach(() => {
  for (const [, s] of mod.sessions.entries()) {
    try { s.ws.close(1001, 'between tests'); } catch (_) { /* noop */ }
  }
  mod.signalingRegistry.forEach(e => clearTimeout(e.ttlTimer));
  mod.relayBuffer.forEach(arr => arr.forEach(e => clearTimeout(e.ttlTimer)));
  mod.sessions.clear();
  mod.signalingRegistry.clear();
  mod.relayBuffer.clear();
});

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeNodeId() {
  return crypto.randomBytes(8).toString('hex'); // 16 chars hex
}

function makeBlockId() {
  return crypto.randomBytes(32).toString('hex'); // 64 chars hex
}

// Connecte un client WSS, exécute la handshake AUTH EC P-256, expose une queue de
// frames post-auth. Retourne { ws, nodeId, nextMessage(timeoutMs) }.
async function connectAuth(opts = {}) {
  const nodeId = opts.nodeId || makeNodeId();
  const keyPair = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });

  const ws = new WebSocket(`ws://127.0.0.1:${port}`);
  await new Promise((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
  });

  // Inbox post-AUTH_OK : remplie une fois la handshake terminée.
  const queue = [];
  const waiters = [];
  let authOk = false;

  ws.on('message', (data) => {
    const frame = mod.parseFrame(data);
    if (!frame) return;
    if (!authOk) {
      // La toute première trame post-handshake doit être AUTH_OK ; tout le reste
      // est piégé par le promise de auth ci-dessous.
      return;
    }
    const waiter = waiters.shift();
    if (waiter) waiter(frame);
    else queue.push(frame);
  });

  // Construit le payload AUTH (Story 8.2) : nodeId, pubKeySpki DER base64, ts ms,
  // signature ECDSA P-256 sur "MobiCloud-HA-AUTH:<nodeId>:<ts>".
  const ts = Date.now();
  const signedData = Buffer.from(`MobiCloud-HA-AUTH:${nodeId}:${ts}`, 'utf8');
  const signature = crypto.sign('SHA256', signedData, keyPair.privateKey).toString('base64');
  const pubKeySpkiDer = keyPair.publicKey
    .export({ type: 'spki', format: 'der' })
    .toString('base64');
  const authPayload = Buffer.from(JSON.stringify({
    nodeId, pubKeySpkiDer, timestamp: ts, signature
  }), 'utf8');

  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout AUTH_OK')), 3000);
    const onAuthMsg = (data) => {
      const frame = mod.parseFrame(data);
      if (!frame) return;
      if (frame.type === mod.MSG.AUTH_OK) {
        clearTimeout(timer);
        ws.off('message', onAuthMsg);
        authOk = true;
        resolve();
      } else if (frame.type === mod.MSG.ERROR) {
        clearTimeout(timer);
        ws.off('message', onAuthMsg);
        reject(new Error(`AUTH erreur: ${frame.payload.toString('utf8')}`));
      }
    };
    ws.on('message', onAuthMsg);
    ws.send(mod.buildFrame(mod.MSG.AUTH, authPayload));
  });

  function nextMessage(timeoutMs = 2000) {
    if (queue.length > 0) return Promise.resolve(queue.shift());
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const idx = waiters.indexOf(resolver);
        if (idx >= 0) waiters.splice(idx, 1);
        reject(new Error(`timeout après ${timeoutMs}ms`));
      }, timeoutMs);
      const resolver = (frame) => { clearTimeout(timer); resolve(frame); };
      waiters.push(resolver);
    });
  }

  return { ws, nodeId, nextMessage };
}

// Pause courte pour laisser le serveur traiter les sends asynchrones avant qu'on
// inspecte le state interne (signalingRegistry).
const tick = (ms = 50) => new Promise(r => setTimeout(r, ms));

// ─── Story 9.1 — clusterId publié via REGISTER_PEER ─────────────────────────

describe('Epic 9 — Story 9.1 — clusterId via REGISTER_PEER', () => {
  test('Super-Pair authentifié REGISTER_PEER → clusterId persisté dans l\'annuaire', async () => {
    const sp = await connectAuth();
    const { MSG, buildFrame, signalingRegistry } = mod;
    const clusterId = '11111111-2222-4333-8aaa-555555555555';

    sp.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '203.0.113.10', port: 9000, reliabilityScore: 0.9,
      electedAt: Date.now(), clusterId, freeBytes: 1024 * 1024
    }), 'utf8')));
    await tick();

    const entry = signalingRegistry.get(sp.nodeId);
    expect(entry).toBeDefined();
    expect(entry.isSuperPair).toBe(true);
    expect(entry.clusterId).toBe(clusterId);
    expect(entry.freeBytes).toBe(1024 * 1024);
  });

  test('REGISTER_PEER avec clusterId non-UUID est coercé en "" (rétrocompat warn)', async () => {
    const sp = await connectAuth();
    const { MSG, buildFrame, signalingRegistry } = mod;

    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    sp.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '203.0.113.10', port: 9000,
      clusterId: 'not-a-uuid', freeBytes: 100
    }), 'utf8')));
    await tick();

    const entry = signalingRegistry.get(sp.nodeId);
    expect(entry).toBeDefined();
    expect(entry.clusterId).toBe('');
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });
});

// ─── Story 9.2 — clusterId & freeBytes exposés dans GET_PEERS ───────────────

describe('Epic 9 — Story 9.2 — GET_PEERS expose clusterId & freeBytes', () => {
  test('Deux Super-Pairs dans des clusters distincts → GET_PEERS fournit les 2 champs', async () => {
    const a = await connectAuth();
    const b = await connectAuth();
    const { MSG, buildFrame } = mod;

    const clusterA = '11111111-2222-4333-8aaa-aaaaaaaaaaaa';
    const clusterB = '99999999-2222-4333-8bbb-bbbbbbbbbbbb';
    const freeBytesA = 5_000_000;
    const freeBytesB = 8_000_000;

    a.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '203.0.113.1', port: 8000, reliabilityScore: 0.8,
      clusterId: clusterA, freeBytes: freeBytesA
    }), 'utf8')));
    b.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '198.51.100.2', port: 8000, reliabilityScore: 0.7,
      clusterId: clusterB, freeBytes: freeBytesB
    }), 'utf8')));
    await tick();

    a.ws.send(buildFrame(MSG.GET_PEERS));
    const frame = await a.nextMessage();
    expect(frame.type).toBe(MSG.PEERS);
    const peers = JSON.parse(frame.payload.toString('utf8'));

    const peerA = peers.find(p => p.nodeId === a.nodeId);
    const peerB = peers.find(p => p.nodeId === b.nodeId);
    expect(peerA).toBeDefined();
    expect(peerB).toBeDefined();
    expect(peerA.clusterId).toBe(clusterA);
    expect(peerA.freeBytes).toBe(freeBytesA);
    expect(peerB.clusterId).toBe(clusterB);
    expect(peerB.freeBytes).toBe(freeBytesB);
    expect(peerA.isSuperPair).toBe(true);
    expect(peerB.isSuperPair).toBe(true);
    // AC#3 9.3 : les clusterId DOIVENT différer pour qu'un fallback inter-cluster
    // côté Android puisse considérer B comme distant.
    expect(peerA.clusterId).not.toBe(peerB.clusterId);
  });

  test('REGISTER_PEER legacy (sans clusterId/freeBytes) → GET_PEERS retourne defaults "" / 0', async () => {
    const legacy = await connectAuth();
    const { MSG, buildFrame } = mod;

    legacy.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '203.0.113.5', port: 9000, reliabilityScore: 0.5
    }), 'utf8')));
    await tick();

    legacy.ws.send(buildFrame(MSG.GET_PEERS));
    const frame = await legacy.nextMessage();
    const peers = JSON.parse(frame.payload.toString('utf8'));
    const me = peers.find(p => p.nodeId === legacy.nodeId);
    expect(me).toBeDefined();
    expect(me.clusterId).toBe('');
    expect(me.freeBytes).toBe(0);
  });

  test('REGISTER_PEER avec freeBytes négatif est coercé en 0 (warn) et toujours exposé', async () => {
    const sp = await connectAuth();
    const { MSG, buildFrame } = mod;
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});

    sp.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '203.0.113.7', port: 9000,
      clusterId: '12345678-1234-4abc-8def-123456789abc',
      freeBytes: -42
    }), 'utf8')));
    await tick();

    sp.ws.send(buildFrame(MSG.GET_PEERS));
    const frame = await sp.nextMessage();
    const me = JSON.parse(frame.payload.toString('utf8')).find(p => p.nodeId === sp.nodeId);
    expect(me.freeBytes).toBe(0);
    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });
});

// ─── Story 9.3 — UPLOAD inter-cluster (zero-knowledge data preserved) ───────

describe('Epic 9 — Story 9.3 — UPLOAD inter-cluster', () => {
  test('Sender d\'un cluster A → Dest d\'un cluster B : FORWARD intact + ACK au sender', async () => {
    const sender = await connectAuth();
    const dest = await connectAuth();
    const { MSG, buildFrame } = mod;

    // Les deux super-pairs s'annoncent dans des clusters distincts (préalable 9.3).
    sender.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '203.0.113.1', port: 9000,
      clusterId: '11111111-2222-4333-8aaa-aaaaaaaaaaaa', freeBytes: 10_000_000
    }), 'utf8')));
    dest.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '198.51.100.2', port: 9000,
      clusterId: '99999999-2222-4333-8bbb-bbbbbbbbbbbb', freeBytes: 20_000_000
    }), 'utf8')));
    await tick();

    // UPLOAD : 16 bytes destNodeId + 64 bytes blockId + ciphertext (zero-knowledge).
    const blockId = makeBlockId();
    const data = crypto.randomBytes(2048);
    const payload = Buffer.alloc(80 + data.length);
    Buffer.from(dest.nodeId.padEnd(16, '\0'), 'utf8').copy(payload, 0);
    Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(payload, 16);
    data.copy(payload, 80);
    sender.ws.send(buildFrame(MSG.UPLOAD, payload));

    // Sender doit recevoir ACK.
    const ack = await sender.nextMessage();
    expect(ack.type).toBe(MSG.ACK);
    expect(JSON.parse(ack.payload.toString('utf8')).blockId).toBe(blockId);

    // Dest doit recevoir FORWARD avec données strictement identiques.
    const fwd = await dest.nextMessage();
    expect(fwd.type).toBe(MSG.FORWARD);
    expect(fwd.payload.length).toBe(80 + data.length);
    const fromRecv = fwd.payload.slice(0, 16).toString('utf8').replace(/\0+$/, '');
    const blockIdRecv = fwd.payload.slice(16, 80).toString('utf8').replace(/\0+$/, '');
    const dataRecv = fwd.payload.slice(80);
    expect(fromRecv).toBe(sender.nodeId);
    expect(blockIdRecv).toBe(blockId);
    expect(dataRecv.equals(data)).toBe(true);
  });

  test('Dest hors-ligne : UPLOAD bufferisé puis flushé à la (re)connexion', async () => {
    const sender = await connectAuth();
    const ghostNodeId = makeNodeId();
    const { MSG, buildFrame } = mod;

    const blockId = makeBlockId();
    const data = crypto.randomBytes(512);
    const payload = Buffer.alloc(80 + data.length);
    Buffer.from(ghostNodeId.padEnd(16, '\0'), 'utf8').copy(payload, 0);
    Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(payload, 16);
    data.copy(payload, 80);
    sender.ws.send(buildFrame(MSG.UPLOAD, payload));

    // ACK reçu malgré dest absent (store-and-forward).
    const ack = await sender.nextMessage();
    expect(ack.type).toBe(MSG.ACK);

    // Dest connecte plus tard avec ce nodeId → reçoit le FORWARD bufferisé (flush).
    const dest = await connectAuth({ nodeId: ghostNodeId });
    const fwd = await dest.nextMessage();
    expect(fwd.type).toBe(MSG.FORWARD);
    const dataRecv = fwd.payload.slice(80);
    expect(dataRecv.equals(data)).toBe(true);
  });
});

// ─── Story 9.4 — REQUEST_BLOCK pull-mode (forward + erreurs) ────────────────

describe('Epic 9 — Story 9.4 — REQUEST_BLOCK forward inter-cluster', () => {
  test('Happy path : REQUEST_BLOCK pivote vers le destinataire en REQUEST_BLOCK_FORWARDED', async () => {
    const requester = await connectAuth();
    const responder = await connectAuth();
    const { MSG, buildFrame } = mod;

    const blockId = makeBlockId();
    const reqPayload = Buffer.alloc(80);
    Buffer.from(responder.nodeId, 'utf8').copy(reqPayload, 0);
    Buffer.from(blockId, 'utf8').copy(reqPayload, 16);
    requester.ws.send(buildFrame(MSG.REQUEST_BLOCK, reqPayload));

    const fwd = await responder.nextMessage();
    expect(fwd.type).toBe(MSG.REQUEST_BLOCK_FORWARDED);
    expect(fwd.payload.length).toBe(80);
    const fromRecv = fwd.payload.slice(0, 16).toString('utf8').replace(/\0+$/, '');
    const blockIdRecv = fwd.payload.slice(16, 80).toString('utf8').replace(/\0+$/, '');
    expect(fromRecv).toBe(requester.nodeId);
    expect(blockIdRecv).toBe(blockId);
  });

  test('Destinataire absent : ERROR (pas de buffering, contrairement à UPLOAD)', async () => {
    const requester = await connectAuth();
    const { MSG, buildFrame } = mod;

    const ghost = makeNodeId();
    const blockId = makeBlockId();
    const reqPayload = Buffer.alloc(80);
    Buffer.from(ghost, 'utf8').copy(reqPayload, 0);
    Buffer.from(blockId, 'utf8').copy(reqPayload, 16);
    requester.ws.send(buildFrame(MSG.REQUEST_BLOCK, reqPayload));

    const errFrame = await requester.nextMessage();
    expect(errFrame.type).toBe(MSG.ERROR);
    expect(errFrame.payload.toString('utf8')).toMatch(/destinataire injoignable/i);

    // Vérifier qu'aucune entrée n'est postée dans le relayBuffer (no-buffer policy).
    expect(mod.relayBuffer.size).toBe(0);
  });

  test('Self-loop (destNodeId == fromNodeId) → ERROR (AC#10)', async () => {
    const a = await connectAuth();
    const { MSG, buildFrame } = mod;

    const reqPayload = Buffer.alloc(80);
    Buffer.from(a.nodeId, 'utf8').copy(reqPayload, 0);
    Buffer.from(makeBlockId(), 'utf8').copy(reqPayload, 16);
    a.ws.send(buildFrame(MSG.REQUEST_BLOCK, reqPayload));

    const errFrame = await a.nextMessage();
    expect(errFrame.type).toBe(MSG.ERROR);
    expect(errFrame.payload.toString('utf8')).toMatch(/destNodeId == fromNodeId/i);
  });

  test('Payload longueur ≠ 80 → ERROR', async () => {
    const a = await connectAuth();
    const { MSG, buildFrame } = mod;
    a.ws.send(buildFrame(MSG.REQUEST_BLOCK, Buffer.alloc(50)));
    const errFrame = await a.nextMessage();
    expect(errFrame.type).toBe(MSG.ERROR);
    expect(errFrame.payload.toString('utf8')).toMatch(/payload invalide/i);
  });

  test('blockId malformé (non hex 64) → ERROR', async () => {
    const requester = await connectAuth();
    const responder = await connectAuth();
    const { MSG, buildFrame } = mod;

    const reqPayload = Buffer.alloc(80);
    Buffer.from(responder.nodeId, 'utf8').copy(reqPayload, 0);
    // blockId reste à zéro (NUL) → invalide
    requester.ws.send(buildFrame(MSG.REQUEST_BLOCK, reqPayload));

    const errFrame = await requester.nextMessage();
    expect(errFrame.type).toBe(MSG.ERROR);
    expect(errFrame.payload.toString('utf8')).toMatch(/blockId invalide/i);
  });
});

// ─── Health endpoint (sanity check serveur tournant) ────────────────────────

describe('Epic 9 — /health endpoint', () => {
  test('GET /health expose le compteur de Super-Pairs enregistrés', async () => {
    const sp = await connectAuth();
    const { MSG, buildFrame } = mod;
    sp.ws.send(buildFrame(MSG.REGISTER_PEER, Buffer.from(JSON.stringify({
      ip: '203.0.113.42', port: 9000,
      clusterId: '12345678-1234-4abc-8def-aaaaaaaaaaaa', freeBytes: 1234
    }), 'utf8')));
    await tick();

    const body = await new Promise((resolve, reject) => {
      const http = require('http');
      const req = http.request({
        host: '127.0.0.1', port, path: '/health', method: 'GET'
      }, (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => resolve(JSON.parse(data)));
      });
      req.on('error', reject);
      req.end();
    });

    expect(body.status).toBe('ok');
    expect(body.registeredSuperPeers).toBeGreaterThanOrEqual(1);
    expect(body.sessions).toBeGreaterThanOrEqual(1);
  });
});
