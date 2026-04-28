'use strict';
const crypto = require('crypto');

// Isolate module state between test suites
let mod;

beforeEach(() => {
  jest.resetModules();
  mod = require('./server');
  // Vider les Maps partagées
  mod.sessions.clear();
  mod.signalingRegistry.clear();
  mod.relayBuffer.clear();
});

afterEach(() => {
  // Nettoyer les timers TTL résiduels
  mod.signalingRegistry.forEach(e => clearTimeout(e.ttlTimer));
  mod.relayBuffer.forEach(arr => arr.forEach(e => clearTimeout(e.ttlTimer)));
  mod.sessions.clear();
  mod.signalingRegistry.clear();
  mod.relayBuffer.clear();
});

// ─── Tests buildFrame / parseFrame ──────────────────────────────────────────

describe('buildFrame / parseFrame', () => {
  test('round-trip AUTH_OK sans payload', () => {
    const { buildFrame, parseFrame, MSG } = mod;
    const frame = buildFrame(MSG.AUTH_OK);
    const parsed = parseFrame(frame);
    expect(parsed).not.toBeNull();
    expect(parsed.type).toBe(MSG.AUTH_OK);
    expect(parsed.payload.length).toBe(0);
  });

  test('round-trip avec payload binaire', () => {
    const { buildFrame, parseFrame, MSG } = mod;
    const payload = Buffer.from([0xDE, 0xAD, 0xBE, 0xEF]);
    const frame = buildFrame(MSG.PING, payload);
    const parsed = parseFrame(frame);
    expect(parsed.type).toBe(MSG.PING);
    expect(parsed.payload).toEqual(payload);
  });

  test('retourne null si buffer trop court', () => {
    const { parseFrame } = mod;
    expect(parseFrame(Buffer.alloc(4))).toBeNull();
  });

  test('retourne null si longueur incohérente', () => {
    const { parseFrame } = mod;
    const buf = Buffer.alloc(10);
    buf.writeUInt8(0x01, 0);
    buf.writeUInt32LE(99, 1); // length 99 mais buffer n'a que 5 bytes de payload
    expect(parseFrame(buf)).toBeNull();
  });

  test('retourne null si payload dépasse MAX_BLOCK_SIZE', () => {
    const { buildFrame, parseFrame, MSG, MAX_BLOCK_SIZE } = mod;
    const oversized = Buffer.alloc(MAX_BLOCK_SIZE + 200);
    const frame = buildFrame(MSG.UPLOAD, oversized);
    expect(parseFrame(frame)).toBeNull();
  });
});

// ─── Tests verifyAuth ────────────────────────────────────────────────────────

describe('verifyAuth', () => {
  function makeAuthPayload(nodeId, timestamp, keyPair) {
    const signedData = Buffer.from(`MobiCloud-HA-AUTH:${nodeId}:${timestamp}`, 'utf8');
    const signature = crypto.sign('SHA256', signedData, keyPair.privateKey);
    const pubKeySpkiDer = keyPair.publicKey
      .export({ type: 'spki', format: 'der' })
      .toString('base64');
    return Buffer.from(JSON.stringify({
      nodeId,
      pubKeySpkiDer,
      timestamp,
      signature: signature.toString('base64')
    }), 'utf8');
  }

  let keyPair;
  beforeAll(() => {
    keyPair = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
  });

  test('accepte une AUTH valide', () => {
    const { verifyAuth } = mod;
    const nodeId = 'a1b2c3d4e5f60708';
    const timestamp = Date.now();
    const payload = makeAuthPayload(nodeId, timestamp, keyPair);
    const result = verifyAuth(payload);
    expect(result.ok).toBe(true);
    expect(result.nodeId).toBe(nodeId);
  });

  test('rejette si JSON malformé', () => {
    const { verifyAuth } = mod;
    const result = verifyAuth(Buffer.from('not json'));
    expect(result.ok).toBe(false);
    expect(result.reason).toMatch(/JSON/i);
  });

  test('rejette si champs manquants', () => {
    const { verifyAuth } = mod;
    const result = verifyAuth(Buffer.from(JSON.stringify({ nodeId: 'abc' })));
    expect(result.ok).toBe(false);
    expect(result.reason).toMatch(/manquants/);
  });

  test('rejette si nodeId pas 16 chars', () => {
    const { verifyAuth } = mod;
    const payload = Buffer.from(JSON.stringify({
      nodeId: 'short',
      pubKeySpkiDer: 'x',
      timestamp: Date.now(),
      signature: 'x'
    }));
    const result = verifyAuth(payload);
    expect(result.ok).toBe(false);
    expect(result.reason).toMatch(/nodeId/);
  });

  test('rejette si timestamp hors fenêtre 30s', () => {
    const { verifyAuth } = mod;
    const nodeId = 'a1b2c3d4e5f60708';
    const timestamp = Date.now() - 60_000; // 60s dans le passé
    const payload = makeAuthPayload(nodeId, timestamp, keyPair);
    const result = verifyAuth(payload);
    expect(result.ok).toBe(false);
    expect(result.reason).toMatch(/fenêtre/);
  });

  test('rejette si clé publique invalide', () => {
    const { verifyAuth } = mod;
    const payload = Buffer.from(JSON.stringify({
      nodeId: 'a1b2c3d4e5f60708',
      pubKeySpkiDer: 'dGVzdA==', // "test" en base64 — pas une vraie clé
      timestamp: Date.now(),
      signature: 'dGVzdA=='
    }));
    const result = verifyAuth(payload);
    expect(result.ok).toBe(false);
    expect(result.reason).toMatch(/Clé publique invalide/);
  });

  test('rejette si signature invalide', () => {
    const { verifyAuth } = mod;
    const nodeId = 'a1b2c3d4e5f60708';
    const timestamp = Date.now();
    const pubKeySpkiDer = keyPair.publicKey
      .export({ type: 'spki', format: 'der' })
      .toString('base64');
    const payload = Buffer.from(JSON.stringify({
      nodeId,
      pubKeySpkiDer,
      timestamp,
      signature: Buffer.from('invalide').toString('base64')
    }));
    const result = verifyAuth(payload);
    expect(result.ok).toBe(false);
  });
});

// ─── Tests handleRegisterPeer ────────────────────────────────────────────────

describe('handleRegisterPeer', () => {
  test('enregistre un Super-Pair valide', () => {
    const { handleRegisterPeer, signalingRegistry } = mod;
    const payload = Buffer.from(JSON.stringify({
      ip: '192.168.1.10', port: 48999,
      reliabilityScore: 0.87, electedAt: Date.now()
    }));
    const result = handleRegisterPeer('a1b2c3d4e5f60708', payload);
    expect(result).toBe(true);
    expect(signalingRegistry.has('a1b2c3d4e5f60708')).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.ip).toBe('192.168.1.10');
    expect(entry.port).toBe(48999);
    clearTimeout(entry.ttlTimer);
  });

  test('échoue si JSON invalide', () => {
    const { handleRegisterPeer } = mod;
    expect(handleRegisterPeer('a1b2c3d4e5f60708', Buffer.from('notjson'))).toBe(false);
  });

  test('échoue si port hors plage', () => {
    const { handleRegisterPeer } = mod;
    const payload = Buffer.from(JSON.stringify({ ip: '10.0.0.1', port: 99999 }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(false);
  });

  test('re-registration annule l\'ancien TTL', () => {
    const { handleRegisterPeer, signalingRegistry } = mod;
    const payload = Buffer.from(JSON.stringify({ ip: '10.0.0.1', port: 5000 }));
    handleRegisterPeer('a1b2c3d4e5f60708', payload);
    const first = signalingRegistry.get('a1b2c3d4e5f60708');
    const firstTimer = first.ttlTimer;
    handleRegisterPeer('a1b2c3d4e5f60708', payload);
    const second = signalingRegistry.get('a1b2c3d4e5f60708');
    // Le timer doit avoir changé
    expect(second.ttlTimer).not.toBe(firstTimer);
    clearTimeout(second.ttlTimer);
  });
});

// ─── Tests handleGetPeers ────────────────────────────────────────────────────

describe('handleGetPeers', () => {
  test('envoie PEERS avec liste des Super-Pairs', () => {
    const { handleGetPeers, handleRegisterPeer, signalingRegistry, buildFrame, MSG } = mod;
    const payload = Buffer.from(JSON.stringify({ ip: '10.0.0.5', port: 48999 }));
    handleRegisterPeer('a1b2c3d4e5f60708', payload);
    const sent = [];
    const fakeWs = { send: (buf) => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    expect(sent.length).toBe(1);
    const frame = mod.parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.PEERS);
    const peers = JSON.parse(frame.payload.toString('utf8'));
    expect(peers.length).toBe(1);
    expect(peers[0].nodeId).toBe('a1b2c3d4e5f60708');
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
  });

  test('envoie liste vide si annuaire vide', () => {
    const { handleGetPeers, parseFrame, MSG } = mod;
    const sent = [];
    const fakeWs = { send: (buf) => sent.push(buf) };
    handleGetPeers(fakeWs);
    const frame = parseFrame(sent[0]);
    const peers = JSON.parse(frame.payload.toString('utf8'));
    expect(peers).toEqual([]);
  });
});

// ─── Tests handleUpload ───────────────────────────────────────────────────────

describe('handleUpload', () => {
  function makeUploadPayload(destNodeId, blockId, data) {
    const buf = Buffer.allocUnsafe(16 + 64 + data.length);
    Buffer.from(destNodeId.padEnd(16, '\0'), 'utf8').copy(buf, 0);
    Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(buf, 16);
    data.copy(buf, 80);
    return buf;
  }

  test('rejette payload trop court', () => {
    const { handleUpload, parseFrame, MSG } = mod;
    const sent = [];
    const fakeWs = { send: buf => sent.push(buf), readyState: 1 };
    handleUpload('sender0000000001', Buffer.alloc(10), fakeWs);
    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.ERROR);
  });

  test('forward immédiat si destinataire connecté', () => {
    const { handleUpload, sessions, buildFrame, parseFrame, MSG } = mod;
    const destNodeId = 'dest000000000001';
    const blockId = 'a'.repeat(64);
    const cipherData = Buffer.from([0x42, 0x43, 0x44]); // ciphertext opaque

    const destSent = [];
    const destWs = { send: buf => destSent.push(buf), readyState: 1 };
    sessions.set(destNodeId, { ws: destWs, publicKey: null });

    const senderSent = [];
    const senderWs = { send: buf => senderSent.push(buf), readyState: 1 };

    const payload = makeUploadPayload(destNodeId, blockId, cipherData);
    handleUpload('sender0000000001', payload, senderWs);

    // ACK envoyé au sender
    expect(senderSent.length).toBe(1);
    const ack = parseFrame(senderSent[0]);
    expect(ack.type).toBe(MSG.ACK);

    // FORWARD envoyé au destinataire
    expect(destSent.length).toBe(1);
    const fwd = parseFrame(destSent[0]);
    expect(fwd.type).toBe(MSG.FORWARD);
    // Vérifier que data est transmis byte-à-byte (Zero-Knowledge)
    const dataInForward = fwd.payload.slice(80);
    expect(dataInForward).toEqual(cipherData);
  });

  test('buffer en RAM si destinataire absent', () => {
    const { handleUpload, relayBuffer, parseFrame, MSG } = mod;
    const destNodeId = 'dest000000000002';
    const blockId = 'b'.repeat(64);
    const cipherData = Buffer.from([0xFF, 0x00]);

    const senderSent = [];
    const senderWs = { send: buf => senderSent.push(buf), readyState: 1 };

    const payload = makeUploadPayload(destNodeId, blockId, cipherData);
    handleUpload('sender0000000001', payload, senderWs);

    // ACK envoyé
    const ack = parseFrame(senderSent[0]);
    expect(ack.type).toBe(MSG.ACK);

    // Bloc bufférisé
    const trimmedBlockId = blockId.trim();
    expect(relayBuffer.has(trimmedBlockId)).toBe(true);
    const entries = relayBuffer.get(trimmedBlockId);
    expect(entries.length).toBe(1);
    expect(entries[0].data).toEqual(cipherData);
    clearTimeout(entries[0].ttlTimer);
  });
});

// ─── Tests flushPendingBlocks ────────────────────────────────────────────────

describe('flushPendingBlocks', () => {
  test('flush et supprime les blocs en attente', () => {
    const { flushPendingBlocks, relayBuffer, parseFrame, MSG } = mod;
    const nodeId = 'recv000000000001';
    const blockId = 'c'.repeat(64);
    const data = Buffer.from([0x11, 0x22]);

    relayBuffer.set(blockId, [{
      fromNodeId: 'from000000000001',
      destNodeId: nodeId,
      data,
      ttlTimer: setTimeout(() => {}, 60_000)
    }]);

    const sent = [];
    const ws = { send: buf => sent.push(buf), readyState: 1 };
    flushPendingBlocks(nodeId, ws);

    // FORWARD envoyé
    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.FORWARD);
    expect(frame.payload.slice(80)).toEqual(data);

    // Entrée supprimée du buffer
    expect(relayBuffer.has(blockId)).toBe(false);
  });
});

// ─── Test /health endpoint ───────────────────────────────────────────────────

describe('endpoint /health', () => {
  test('répond 200 avec status ok', (done) => {
    const http = require('http');
    const { httpServer } = mod;

    // Démarrer sur port 0 (port aléatoire assigné par l'OS)
    httpServer.listen(0, '127.0.0.1', () => {
      const { port } = httpServer.address();
      http.get(`http://127.0.0.1:${port}/health`, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
          expect(res.statusCode).toBe(200);
          const json = JSON.parse(data);
          expect(json.status).toBe('ok');
          expect(typeof json.sessions).toBe('number');
          expect(typeof json.pendingBlocks).toBe('number');
          expect(typeof json.registeredSuperPeers).toBe('number');
          httpServer.close(done);
        });
      });
    });
  });
});
