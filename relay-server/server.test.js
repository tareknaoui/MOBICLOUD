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
    expect(entry.isSuperPair).toBe(true);   // REGISTER_PEER ⇒ statut Super-Pair formel
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

  // Story 9.2 — freeBytes
  test('Story 9.2 — REGISTER_PEER avec freeBytes valide stocke la valeur', () => {
    const { handleRegisterPeer, signalingRegistry } = mod;
    const payload = Buffer.from(JSON.stringify({
      ip: '10.0.0.1', port: 5000, freeBytes: 1234567
    }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.freeBytes).toBe(1234567);
    clearTimeout(entry.ttlTimer);
  });

  test('Story 9.2 — REGISTER_PEER sans freeBytes (legacy) stocke 0 sans erreur', () => {
    const { handleRegisterPeer, signalingRegistry } = mod;
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const payload = Buffer.from(JSON.stringify({ ip: '10.0.0.1', port: 5000 }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.freeBytes).toBe(0);
    // Aucun warning : champ absent = legacy, comportement attendu
    expect(warnSpy).not.toHaveBeenCalledWith(expect.stringContaining('freeBytes'));
    clearTimeout(entry.ttlTimer);
    warnSpy.mockRestore();
  });

  // NB : NaN / Infinity ne sont pas testés ici car `JSON.stringify` les sérialise
  // en `null`, qui est traité comme "champ absent" (legacy, pas de warn). On teste
  // les seules valeurs invalides qui peuvent transiter via JSON : nombre négatif et
  // string non-numérique.
  test.each([
    ['négatif', -5],
    ['string non-numérique', 'abc'],
    // Story 9.2 review (P3) : type guard typeof === 'number' refuse strings et booléens.
    ['string numérique', '1234'],
    ['boolean true', true],
    ['boolean false', false],
    // Story 9.2 review (P2) : borne sup MAX_SAFE_INTEGER refuse les Long > 2^53 qui
    // perdraient la précision (vecteur de biais inter-cluster).
    ['au-delà de MAX_SAFE_INTEGER', Number.MAX_SAFE_INTEGER + 100]
  ])('Story 9.2 — REGISTER_PEER avec freeBytes invalide (%s) coerce en 0 + warn', (_label, badValue) => {
    const { handleRegisterPeer, signalingRegistry } = mod;
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const payload = Buffer.from(JSON.stringify({
      ip: '10.0.0.1', port: 5000, freeBytes: badValue
    }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.freeBytes).toBe(0);
    expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('freeBytes invalide'));
    clearTimeout(entry.ttlTimer);
    warnSpy.mockRestore();
  });

  test('Story 9.2 — REGISTER_PEER avec freeBytes décimal est tronqué via Math.floor', () => {
    const { handleRegisterPeer, signalingRegistry } = mod;
    const payload = Buffer.from(JSON.stringify({
      ip: '10.0.0.1', port: 5000, freeBytes: 1024.9
    }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(true);
    expect(signalingRegistry.get('a1b2c3d4e5f60708').freeBytes).toBe(1024);
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
  });

  // Story 12.1 — currentMemberCount (remplace GPS)
  test('Story 12.1 — REGISTER_PEER avec currentMemberCount valide → stocké et retourné par GET_PEERS', () => {
    const { handleRegisterPeer, handleGetPeers, signalingRegistry, parseFrame, MAX_CLUSTER_SIZE_SERVER } = mod;
    // Agnostique au cap : on utilise une valeur ≤ MAX_CLUSTER_SIZE_SERVER (ajustable selon la
    // config démo — actuellement 2). Évite que le test casse quand le cap change.
    const validCount = MAX_CLUSTER_SIZE_SERVER;
    const payload = Buffer.from(JSON.stringify({
      ip: '10.0.0.1', port: 5000, currentMemberCount: validCount
    }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.currentMemberCount).toBe(validCount);

    const sent = [];
    const fakeWs = { send: buf => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const peers = JSON.parse(parseFrame(sent[0]).payload.toString('utf8'));
    expect(peers[0].currentMemberCount).toBe(validCount);
    clearTimeout(entry.ttlTimer);
  });

  test('Story 12.1 — handleRegisterPeer_rejectsCurrentMemberCountAbove50_coercesToZero — currentMemberCount > 50 coercé en 0 + warn', () => {
    const { handleRegisterPeer, signalingRegistry } = mod;
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const payload = Buffer.from(JSON.stringify({
      ip: '10.0.0.1', port: 5000, currentMemberCount: 51
    }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.currentMemberCount).toBe(0);
    expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('currentMemberCount invalide'));
    clearTimeout(entry.ttlTimer);
    warnSpy.mockRestore();
  });

  test('Story 12.1 — REGISTER_PEER sans currentMemberCount → défaut 0', () => {
    const { handleRegisterPeer, handleGetPeers, signalingRegistry, parseFrame } = mod;
    const payload = Buffer.from(JSON.stringify({ ip: '10.0.0.1', port: 5000 }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', payload)).toBe(true);
    const sent = [];
    const fakeWs = { send: buf => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const peers = JSON.parse(parseFrame(sent[0]).payload.toString('utf8'));
    expect(peers[0].currentMemberCount).toBe(0);
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
  });

  test('Story 12.1 — handleJoin préserve currentMemberCount du REGISTER_PEER précédent', () => {
    const { handleRegisterPeer, handleJoin, handleGetPeers, signalingRegistry, parseFrame, MAX_CLUSTER_SIZE_SERVER } = mod;
    // Agnostique au cap : valeur ≤ MAX_CLUSTER_SIZE_SERVER pour ne pas être coercée en 0.
    const validCount = MAX_CLUSTER_SIZE_SERVER;
    // 1. REGISTER_PEER avec currentMemberCount
    const regPayload = Buffer.from(JSON.stringify({
      ip: '10.0.0.1', port: 5000, currentMemberCount: validCount
    }));
    expect(handleRegisterPeer('a1b2c3d4e5f60708', regPayload)).toBe(true);

    // 2. JOIN heartbeat (sans currentMemberCount dans le payload)
    const joinPayload = Buffer.from(JSON.stringify({ ip: '10.0.0.1', port: 5000 }));
    expect(handleJoin('a1b2c3d4e5f60708', joinPayload)).toBe(true);

    // 3. GET_PEERS doit toujours retourner le currentMemberCount du REGISTER_PEER
    const sent = [];
    const fakeWs = { send: buf => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const peers = JSON.parse(parseFrame(sent[0]).payload.toString('utf8'));
    expect(peers[0].currentMemberCount).toBe(validCount);
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
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

// ─── Tests handleJoin ────────────────────────────────────────────────────────

describe('handleJoin', () => {
  test('JOIN insère un participant avec isSuperPair=false', () => {
    const { handleJoin, signalingRegistry } = mod;
    const payload = Buffer.from(JSON.stringify({ ip: '10.0.0.42', port: 7777 }));
    const result = handleJoin('a1b2c3d4e5f60708', payload);
    expect(result).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.isSuperPair).toBe(false);
    expect(entry.ip).toBe('10.0.0.42');
    expect(entry.port).toBe(7777);
    clearTimeout(entry.ttlTimer);
  });

  test('JOIN accepte ip/port omis (nœud non joignable directement)', () => {
    const { handleJoin, signalingRegistry } = mod;
    const payload = Buffer.from(JSON.stringify({ reliabilityScore: 0.7 }));
    const result = handleJoin('a1b2c3d4e5f60708', payload);
    expect(result).toBe(true);
    const entry = signalingRegistry.get('a1b2c3d4e5f60708');
    expect(entry.isSuperPair).toBe(false);
    expect(entry.ip).toBe('0.0.0.0');
    expect(entry.port).toBe(0);
    clearTimeout(entry.ttlTimer);
  });

  // [MAJ 2026-06-08] Comportement inversé depuis le fix SP-fantôme (2026-06-01) : JOIN
  // réinitialise TOUJOURS isSuperPair=false. Préserver le statut SP au JOIN faisait apparaître
  // un nœud redémarré comme SP fantôme dans GET_PEERS → le vrai vainqueur Bully abdiquait à
  // tort. Un SP actif reclame le statut via REGISTER_PEER (re-envoyé dans les ms suivant le JOIN).
  test('JOIN réinitialise isSuperPair=false (statut SP reclamé via REGISTER_PEER)', () => {
    const { handleRegisterPeer, handleJoin, signalingRegistry } = mod;
    handleRegisterPeer('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({ ip: '10.0.0.5', port: 5000 })));
    expect(signalingRegistry.get('a1b2c3d4e5f60708').isSuperPair).toBe(true);
    // JOIN dégrade le statut (anti SP-fantôme)
    handleJoin('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({ ip: '10.0.0.5', port: 5000 })));
    expect(signalingRegistry.get('a1b2c3d4e5f60708').isSuperPair).toBe(false);
    // REGISTER_PEER suivant reclame le statut SP
    handleRegisterPeer('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({ ip: '10.0.0.5', port: 5000 })));
    expect(signalingRegistry.get('a1b2c3d4e5f60708').isSuperPair).toBe(true);
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
  });

  test('REGISTER_PEER après JOIN promeut au statut Super-Pair', () => {
    const { handleJoin, handleRegisterPeer, signalingRegistry } = mod;
    handleJoin('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({})));
    expect(signalingRegistry.get('a1b2c3d4e5f60708').isSuperPair).toBe(false);
    handleRegisterPeer('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({ ip: '10.0.0.5', port: 5000 })));
    expect(signalingRegistry.get('a1b2c3d4e5f60708').isSuperPair).toBe(true);
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
  });

  test('JOIN échoue si JSON invalide', () => {
    const { handleJoin } = mod;
    expect(handleJoin('a1b2c3d4e5f60708', Buffer.from('notjson'))).toBe(false);
  });

  test('JOIN échoue si port hors plage', () => {
    const { handleJoin } = mod;
    expect(handleJoin('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({ port: 99999 })))).toBe(false);
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
    const fakeWs = { send: (buf) => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const frame = parseFrame(sent[0]);
    const peers = JSON.parse(frame.payload.toString('utf8'));
    expect(peers).toEqual([]);
  });

  // Story 9.2 — clusterId + freeBytes dans la réponse PEERS
  test('Story 9.2 — PEERS expose clusterId et freeBytes pour un Super-Pair enregistré', () => {
    const { handleGetPeers, handleRegisterPeer, signalingRegistry, parseFrame, MSG } = mod;
    const validUuid = '550e8400-e29b-41d4-a716-446655440000';
    const payload = Buffer.from(JSON.stringify({
      ip: '10.0.0.5', port: 48999, clusterId: validUuid, freeBytes: 1024
    }));
    handleRegisterPeer('a1b2c3d4e5f60708', payload);

    const sent = [];
    const fakeWs = { send: (buf) => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const peers = JSON.parse(parseFrame(sent[0]).payload.toString('utf8'));
    expect(peers.length).toBe(1);
    expect(peers[0].clusterId).toBe(validUuid);
    expect(peers[0].freeBytes).toBe(1024);
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
  });

  test('Story 9.2 review (P1) — JOIN heartbeat préserve clusterId et freeBytes d\'un Super-Pair déjà enregistré', () => {
    const { handleGetPeers, handleRegisterPeer, handleJoin, signalingRegistry, parseFrame } = mod;
    const validUuid = '550e8400-e29b-41d4-a716-446655440000';
    // 1) REGISTER_PEER initial avec clusterId + freeBytes
    handleRegisterPeer('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({
      ip: '10.0.0.5', port: 48999, clusterId: validUuid, freeBytes: 1024
    })));
    // 2) Le même nœud envoie un JOIN heartbeat (sans clusterId/freeBytes dans le payload)
    handleJoin('a1b2c3d4e5f60708', Buffer.from(JSON.stringify({ ip: '10.0.0.5', port: 48999 })));
    // 3) GET_PEERS doit toujours retourner clusterId + freeBytes initiaux (préservés au JOIN)
    const sent = [];
    const fakeWs = { send: (buf) => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const peers = JSON.parse(parseFrame(sent[0]).payload.toString('utf8'));
    expect(peers.length).toBe(1);
    expect(peers[0].clusterId).toBe(validUuid);
    expect(peers[0].freeBytes).toBe(1024);
    // [MAJ 2026-06-08] isSuperPair est volontairement réinitialisé à false au JOIN (fix
    // SP-fantôme du 2026-06-01) — contrairement à clusterId/freeBytes qui restent préservés.
    // Le statut SP est reclamé via le REGISTER_PEER que le SP renvoie aussitôt après le JOIN.
    expect(peers[0].isSuperPair).toBe(false);
    clearTimeout(signalingRegistry.get('a1b2c3d4e5f60708').ttlTimer);
  });

  test('Story 9.2 — PEERS expose clusterId="" et freeBytes=0 pour un nœud JOIN-only', () => {
    const { handleGetPeers, handleJoin, signalingRegistry, parseFrame } = mod;
    handleJoin('1111111111111111', Buffer.from(JSON.stringify({ ip: '10.0.0.1', port: 7777 })));
    const sent = [];
    const fakeWs = { send: (buf) => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const peers = JSON.parse(parseFrame(sent[0]).payload.toString('utf8'));
    expect(peers.length).toBe(1);
    expect(peers[0].clusterId).toBe('');
    expect(peers[0].freeBytes).toBe(0);
    clearTimeout(signalingRegistry.get('1111111111111111').ttlTimer);
  });

  test('expose le flag isSuperPair pour chaque pair (mix JOIN + REGISTER)', () => {
    const { handleGetPeers, handleJoin, handleRegisterPeer, signalingRegistry, parseFrame } = mod;
    handleJoin('1111111111111111', Buffer.from(JSON.stringify({})));
    handleRegisterPeer('2222222222222222', Buffer.from(JSON.stringify({ ip: '10.0.0.2', port: 5000 })));
    const sent = [];
    const fakeWs = { send: (buf) => sent.push(buf), readyState: 1 };
    handleGetPeers(fakeWs);
    const peers = JSON.parse(parseFrame(sent[0]).payload.toString('utf8'));
    const byId = Object.fromEntries(peers.map(p => [p.nodeId, p]));
    expect(byId['1111111111111111'].isSuperPair).toBe(false);
    expect(byId['2222222222222222'].isSuperPair).toBe(true);
    clearTimeout(signalingRegistry.get('1111111111111111').ttlTimer);
    clearTimeout(signalingRegistry.get('2222222222222222').ttlTimer);
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

// ─── Tests handleRequestBlock (Story 9.4) ──────────────────────────────────

describe('handleRequestBlock', () => {
  function makeRequestBlockPayload(destNodeId, blockId) {
    const buf = Buffer.allocUnsafe(80);
    Buffer.from(destNodeId.padEnd(16, '\0'), 'utf8').copy(buf, 0);
    Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(buf, 16);
    return buf;
  }

  test('forward immédiat si destinataire connecté', () => {
    const { handleRequestBlock, sessions, parseFrame, MSG } = mod;
    const destNodeId = 'dest000000000001';
    const fromNodeId = 'sender0000000001';
    const blockId = 'a'.repeat(64);

    const destSent = [];
    const destWs = { send: buf => destSent.push(buf), readyState: 1 };
    sessions.set(destNodeId, { ws: destWs, publicKey: null });

    const senderSent = [];
    const senderWs = { send: buf => senderSent.push(buf), readyState: 1 };

    const payload = makeRequestBlockPayload(destNodeId, blockId);
    handleRequestBlock(fromNodeId, payload, senderWs);

    // Aucun ACK au sender pour REQUEST_BLOCK (la réponse est elle-même un UPLOAD/FORWARD).
    expect(senderSent.length).toBe(0);

    // Forward envoyé au destinataire avec REQUEST_BLOCK_FORWARDED (0x0D)
    expect(destSent.length).toBe(1);
    const frame = parseFrame(destSent[0]);
    expect(frame.type).toBe(MSG.REQUEST_BLOCK_FORWARDED);
    expect(frame.payload.length).toBe(80);

    // fromNodeId aux 16 premiers bytes (paddé avec \0)
    const fwdFromNodeId = frame.payload.slice(0, 16).toString('utf8').replace(/\0/g, '');
    expect(fwdFromNodeId).toBe(fromNodeId);

    // blockId aux 64 bytes suivants
    const fwdBlockId = frame.payload.slice(16, 80).toString('utf8').replace(/\0/g, '');
    expect(fwdBlockId).toBe(blockId);
  });

  test('rejette si destinataire absent', () => {
    const { handleRequestBlock, parseFrame, MSG } = mod;
    const sent = [];
    const senderWs = { send: buf => sent.push(buf), readyState: 1 };

    const payload = makeRequestBlockPayload('absent0000000001', 'a'.repeat(64));
    handleRequestBlock('sender0000000001', payload, senderWs);

    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.ERROR);
    expect(frame.payload.toString('utf8')).toMatch(/injoignable/);
  });

  test('rejette payload trop court', () => {
    const { handleRequestBlock, parseFrame, MSG } = mod;
    const sent = [];
    const senderWs = { send: buf => sent.push(buf), readyState: 1 };

    handleRequestBlock('sender0000000001', Buffer.alloc(50), senderWs);

    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.ERROR);
    expect(frame.payload.toString('utf8')).toMatch(/80 bytes/);
  });

  test('rejette payload boundary 79 bytes (juste sous 80)', () => {
    const { handleRequestBlock, parseFrame, MSG } = mod;
    const sent = [];
    const senderWs = { send: buf => sent.push(buf), readyState: 1 };

    handleRequestBlock('sender0000000001', Buffer.alloc(79), senderWs);

    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.ERROR);
    expect(frame.payload.toString('utf8')).toMatch(/80 bytes/);
  });

  test('rejette blockId malformé (pas 64 hex chars)', () => {
    const { handleRequestBlock, parseFrame, MSG } = mod;
    const sent = [];
    const senderWs = { send: buf => sent.push(buf), readyState: 1 };

    // blockId trop court (32 chars, et pas hex strict)
    const payload = makeRequestBlockPayload('dest000000000001', 'XYZ' + 'a'.repeat(29));
    handleRequestBlock('sender0000000001', payload, senderWs);

    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.ERROR);
    expect(frame.payload.toString('utf8')).toMatch(/blockId/);
  });

  test('rejette destNodeId == fromNodeId (anti-loop AC#10)', () => {
    const { handleRequestBlock, sessions, parseFrame, MSG } = mod;
    const sent = [];
    const senderWs = { send: buf => sent.push(buf), readyState: 1 };
    const myNodeId = 'self000000000001';

    // Même si la session existe, le serveur doit refuser le self-loop.
    sessions.set(myNodeId, { ws: senderWs, publicKey: null });

    const payload = makeRequestBlockPayload(myNodeId, 'b'.repeat(64));
    handleRequestBlock(myNodeId, payload, senderWs);

    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.ERROR);
    expect(frame.payload.toString('utf8')).toMatch(/interdit/);
  });

  test('rejette destNodeId vide après strip', () => {
    const { handleRequestBlock, parseFrame, MSG } = mod;
    const sent = [];
    const senderWs = { send: buf => sent.push(buf), readyState: 1 };

    // payload de 80 bytes mais destNodeId = uniquement des \0 → vide après strip
    const payload = Buffer.alloc(80);
    Buffer.from('a'.repeat(64), 'utf8').copy(payload, 16);
    handleRequestBlock('sender0000000001', payload, senderWs);

    expect(sent.length).toBe(1);
    const frame = parseFrame(sent[0]);
    expect(frame.type).toBe(MSG.ERROR);
    expect(frame.payload.toString('utf8')).toMatch(/destNodeId/);
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

// ─── Tests isNodeConnected (grâce d'affichage — fix flicker arête SP↔membre) ───

describe('isNodeConnected (grâce CONN_GRACE_MS)', () => {
  test('session WS vive → connecté', () => {
    const { isNodeConnected, sessions } = mod;
    sessions.set('node000000000001', { ws: {}, publicKey: null });
    expect(isNodeConnected('node000000000001', { lastSeen: 0 })).toBe(true);
  });

  test('pas de session mais vu récemment (< grâce) → reste connecté (absorbe le flap WS)', () => {
    const { isNodeConnected, CONN_GRACE_MS } = mod;
    const entry = { lastSeen: Date.now() - (CONN_GRACE_MS - 5_000) };
    expect(isNodeConnected('node000000000001', entry)).toBe(true);
  });

  test('pas de session et vu il y a longtemps (> grâce) → déconnecté', () => {
    const { isNodeConnected, CONN_GRACE_MS } = mod;
    const entry = { lastSeen: Date.now() - (CONN_GRACE_MS + 5_000) };
    expect(isNodeConnected('node000000000001', entry)).toBe(false);
  });

  test('lastSeen absent/0 et pas de session → déconnecté (pas de faux positif)', () => {
    const { isNodeConnected } = mod;
    expect(isNodeConnected('node000000000001', {})).toBe(false);
    expect(isNodeConnected('node000000000001', { lastSeen: 0 })).toBe(false);
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
