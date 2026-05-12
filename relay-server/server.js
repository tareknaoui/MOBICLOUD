'use strict';
const http = require('http');
const crypto = require('crypto');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = parseInt(process.env.PORT || '10000', 10);

// Types de messages (doit correspondre exactement au protocole Android Story 8.2)
const MSG = {
  AUTH: 0x01, AUTH_OK: 0x02,
  REGISTER_PEER: 0x03, GET_PEERS: 0x04, PEERS: 0x05,
  UPLOAD: 0x06, FORWARD: 0x07, ACK: 0x08,
  PING: 0x09, PONG: 0x0A,
  // JOIN (0x0B) : ajoute le nœud à l'annuaire de présence sans le déclarer Super-Pair.
  // Story Bully — sépare la "présence dans le réseau" de l'élection formelle.
  // Un nœud envoie JOIN au démarrage ; seul le gagnant Bully envoie ensuite REGISTER_PEER.
  JOIN: 0x0B,
  // Story 9.4 — récupération inter-cluster (mode pull) : requester demande
  // un blockId à un Super-Pair distant, le serveur pivote 80 bytes vers le destinataire.
  // La réponse passe par le canal UPLOAD/FORWARD existant — pas de nouveau message retour.
  REQUEST_BLOCK: 0x0C,
  REQUEST_BLOCK_FORWARDED: 0x0D,
  // SIGNAL (0x0E) : forwarding P2P éphémère — utilisé pour Gossip DHT entre pairs.
  // Pas de buffering : si le destinataire est absent le signal est droppé.
  SIGNAL: 0x0E,
  SIGNAL_RECEIVED: 0x0F,
  ERROR: 0xFF
};

// Story 12.1 — MIRROR: app/.../ClusterConstants.kt#MAX_CLUSTER_SIZE
const MAX_CLUSTER_SIZE_SERVER = 50;

const TTL_MS = 60_000;               // TTL registre signaling + buffer relay
const AUTH_WINDOW_MS = 30_000;       // fenêtre anti-replay auth
const AUTH_TIMEOUT_MS = 10_000;      // délai max pour envoyer AUTH après connexion
const MAX_BLOCK_SIZE = 1_100_000;    // 1.1 MB — marge sur fragments MobiCloud ~1 MB
const MAX_RELAY_BUFFER_ENTRIES = 500; // cap total des blocs en attente en RAM
const MAX_SIGNALING_PEERS = 100;      // cap total des Super-Pairs enregistrés
const HEARTBEAT_INTERVAL_MS = 30_000; // ping ws protocol-level — détecte les sockets zombies (réseau coupé sans close)

// Regex IP : IPv4 ou IPv6 compacte — pas de hostname
const IP_RE = /^[\d.:a-fA-F]{2,45}$/;

// ─── Framing ────────────────────────────────────────────────────────────────

function buildFrame(type, payloadBuf = Buffer.alloc(0)) {
  const buf = Buffer.allocUnsafe(5 + payloadBuf.length);
  buf.writeUInt8(type, 0);
  buf.writeUInt32LE(payloadBuf.length, 1);
  payloadBuf.copy(buf, 5);
  return buf;
}

function parseFrame(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 5) return null;
  const type = buf.readUInt8(0);
  const length = buf.readUInt32LE(1);
  if (buf.length !== 5 + length) return null;
  if (length > MAX_BLOCK_SIZE + 128) return null; // +128 pour header JSON UPLOAD
  return { type, payload: buf.slice(5) };
}

function sendError(ws, message) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(buildFrame(MSG.ERROR, Buffer.from(message, 'utf8')));
  }
}

// Envoyer sans risque de crash si la socket est en train de fermer
function safeSend(ws, frame) {
  try {
    if (ws.readyState === WebSocket.OPEN) ws.send(frame);
  } catch { /* socket déjà fermée */ }
}

// ─── Authentification EC P-256 ───────────────────────────────────────────────

function verifyAuth(payload) {
  let msg;
  try { msg = JSON.parse(payload.toString('utf8')); } catch { return { ok: false, reason: 'AUTH JSON invalide' }; }

  const { nodeId, pubKeySpkiDer, timestamp, signature } = msg;
  if (!nodeId || !pubKeySpkiDer || !timestamp || !signature) {
    return { ok: false, reason: 'AUTH champs manquants' };
  }

  // nodeId : exactement 16 chars hex ASCII (pas de multi-octet UTF-8)
  if (typeof nodeId !== 'string' || !/^[0-9a-fA-F]{16}$/.test(nodeId)) {
    return { ok: false, reason: 'nodeId invalide (attendu 16 chars hex ASCII)' };
  }

  // Fenêtre anti-replay 30s — timestamp doit être un nombre fini
  const ts = Number(timestamp);
  if (!Number.isFinite(ts) || ts <= 0) {
    return { ok: false, reason: 'AUTH timestamp invalide (doit être Unix ms fini)' };
  }
  const skew = Math.abs(Date.now() - ts);
  if (skew > AUTH_WINDOW_MS) return { ok: false, reason: `AUTH timestamp hors fenêtre (écart ${skew}ms)` };

  // Payload signé — doit correspondre exactement à ce que signe l'Android Keystore (Story 8.2)
  const signedData = Buffer.from(`MobiCloud-HA-AUTH:${nodeId}:${timestamp}`, 'utf8');

  let publicKey;
  try {
    publicKey = crypto.createPublicKey({
      key: Buffer.from(pubKeySpkiDer, 'base64'),
      format: 'der',
      type: 'spki'
    });
  } catch (e) {
    return { ok: false, reason: `Clé publique invalide : ${e.message}` };
  }

  // Vérifier que la clé est bien EC P-256 (prime256v1)
  if (publicKey.asymmetricKeyType !== 'ec') {
    return { ok: false, reason: 'Clé doit être de type EC' };
  }
  const { namedCurve } = publicKey.asymmetricKeyDetails;
  if (namedCurve !== 'prime256v1') {
    return { ok: false, reason: `Courbe EC invalide : attendu prime256v1, reçu ${namedCurve}` };
  }

  try {
    const sigBuf = Buffer.from(signature, 'base64');
    const valid = crypto.verify('SHA256', signedData, publicKey, sigBuf);
    if (!valid) return { ok: false, reason: 'Signature EC P-256 invalide' };
  } catch (e) {
    return { ok: false, reason: `Erreur vérification signature : ${e.message}` };
  }

  return { ok: true, nodeId, publicKey };
}

// ─── Données en RAM (aucune persistance disque) ─────────────────────────────

// Map<nodeId, { ws, publicKey }>
const sessions = new Map();

// Map<nodeId, { ip, port, reliabilityScore, electedAt, lastSeen, ttlTimer, isSuperPair }>
// isSuperPair=false → simple présence (JOIN), isSuperPair=true → Super-Pair élu (REGISTER_PEER).
const signalingRegistry = new Map();

// Map<blockId, [{ fromNodeId, destNodeId, data: Buffer, ttlTimer }]>
const relayBuffer = new Map();

// ─── Signaling (REGISTER_PEER / GET_PEERS) ──────────────────────────────────

function handleRegisterPeer(nodeId, payload) {
  let entry;
  try { entry = JSON.parse(payload.toString('utf8')); } catch { return false; }

  const { ip, port, reliabilityScore, electedAt, clusterId, freeBytes, currentMemberCount } = entry;
  if (!ip || typeof port !== 'number' || port < 1 || port > 65535) return false;

  // Valider le format IP (pas de hostname, pas de loopback non contrôlé)
  if (typeof ip !== 'string' || !IP_RE.test(ip) || ip.length > 45) return false;

  // clusterId optionnel : UUID v4 strict ou "" pour nœuds legacy.
  // Story 9.1 review (option b) : valider format UUID v4 ; coerce en "" + warn si invalide.
  const UUID_V4_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  let clusterIdStr = '';
  if (clusterId === undefined || clusterId === null || clusterId === '') {
    clusterIdStr = '';
  } else if (typeof clusterId === 'string' && UUID_V4_RE.test(clusterId)) {
    clusterIdStr = clusterId;
  } else {
    console.warn(`[SIGNALING] clusterId invalide rejeté (coerce en "") — nodeId=${nodeId.slice(0, 8)} type=${typeof clusterId}`);
    clusterIdStr = '';
  }

  // Story 9.2 — freeBytes optionnel : Number entier fini, 0 ≤ x ≤ MAX_SAFE_INTEGER ;
  // sinon coerce en 0 + warn. Absent/null ⇒ legacy/JOIN amont, on stocke 0 sans warn.
  // Story 9.2 review (P2/P3) : exiger typeof === 'number' (refuse "1234", true/false) ET
  // borner par MAX_SAFE_INTEGER (refuse 1e18 qui passerait isFinite mais perdrait la précision).
  let freeBytesNum = 0;
  if (freeBytes !== undefined && freeBytes !== null) {
    if (typeof freeBytes === 'number'
      && Number.isFinite(freeBytes)
      && freeBytes >= 0
      && freeBytes <= Number.MAX_SAFE_INTEGER) {
      freeBytesNum = Math.floor(freeBytes);
    } else {
      console.warn(`[SIGNALING] freeBytes invalide rejeté (coerce en 0) — nodeId=${nodeId.slice(0, 8)} type=${typeof freeBytes}`);
    }
  }

  // Story 12.1 — currentMemberCount : Number entier fini 0 ≤ x ≤ MAX_CLUSTER_SIZE_SERVER ;
  // coerce en 0 + warn si invalide — même pattern que freeBytes.
  let currentMemberCountNum = 0;
  if (currentMemberCount !== undefined && currentMemberCount !== null) {
    if (typeof currentMemberCount === 'number'
      && Number.isFinite(currentMemberCount)
      && currentMemberCount >= 0
      && currentMemberCount <= MAX_CLUSTER_SIZE_SERVER) {
      currentMemberCountNum = Math.floor(currentMemberCount);
    } else {
      console.warn(`[SIGNALING] currentMemberCount invalide rejeté (coerce en 0) — nodeId=${nodeId.slice(0, 8)} val=${currentMemberCount}`);
    }
  }

  // Cap : refuser si annuaire plein et ce nœud n'est pas déjà enregistré
  if (!signalingRegistry.has(nodeId) && signalingRegistry.size >= MAX_SIGNALING_PEERS) return false;

  // Annuler l'ancien timer TTL si re-registration
  const existing = signalingRegistry.get(nodeId);
  if (existing?.ttlTimer) clearTimeout(existing.ttlTimer);

  const ttlTimer = setTimeout(() => {
    signalingRegistry.delete(nodeId);
    console.log(`[SIGNALING] TTL expiré — nodeId=${nodeId.slice(0, 8)} supprimé`);
  }, TTL_MS);

  signalingRegistry.set(nodeId, {
    ip, port,
    reliabilityScore: reliabilityScore ?? 0.5,
    electedAt: electedAt ?? Date.now(),
    clusterId: clusterIdStr,
    freeBytes: freeBytesNum,
    currentMemberCount: currentMemberCountNum,
    lastSeen: Date.now(),
    ttlTimer,
    isSuperPair: true   // REGISTER_PEER = revendication formelle de statut Super-Pair (post-Bully)
  });

  console.log(`[SIGNALING] REGISTER super-peer nodeId=${nodeId.slice(0, 8)} ip=${ip}:${port} clusterId=${clusterIdStr.slice(0, 8) || '(legacy)'} freeBytes=${freeBytesNum} memberCount=${currentMemberCountNum}/${MAX_CLUSTER_SIZE_SERVER}`);
  return true;
}

// JOIN : ajoute le nœud à l'annuaire en tant que simple participant (isSuperPair=false).
// Si le nœud est DÉJÀ enregistré comme Super-Pair, le statut est préservé (re-JOIN ne dégrade pas).
// ip/port sont optionnels — peuvent être omis pour les nœuds non joignables directement.
function handleJoin(nodeId, payload) {
  let entry;
  try { entry = JSON.parse(payload.toString('utf8')); } catch { return false; }

  const { ip, port, reliabilityScore } = entry;

  // ip/port optionnels — si présents, doivent être valides
  if (ip !== undefined && ip !== null) {
    if (typeof ip !== 'string' || !IP_RE.test(ip) || ip.length > 45) return false;
  }
  if (port !== undefined && port !== null) {
    if (typeof port !== 'number' || port < 0 || port > 65535) return false;
  }

  if (!signalingRegistry.has(nodeId) && signalingRegistry.size >= MAX_SIGNALING_PEERS) return false;

  const existing = signalingRegistry.get(nodeId);
  if (existing?.ttlTimer) clearTimeout(existing.ttlTimer);

  const ttlTimer = setTimeout(() => {
    signalingRegistry.delete(nodeId);
    console.log(`[PRESENCE] TTL expiré — nodeId=${nodeId.slice(0, 8)} supprimé`);
  }, TTL_MS);

  // Préserver le statut Super-Pair si déjà élu — re-JOIN ne déclasse pas.
  const wasSuperPair = existing?.isSuperPair ?? false;

  // Story 9.2 : préserver clusterId/freeBytes/currentMemberCount existants — un Super-Pair
  // envoyant un JOIN heartbeat ne doit PAS être rétrogradé jusqu'au prochain REGISTER_PEER.
  signalingRegistry.set(nodeId, {
    ip: ip ?? '0.0.0.0',
    port: port ?? 0,
    reliabilityScore: reliabilityScore ?? 0.5,
    electedAt: existing?.electedAt ?? null,
    clusterId: existing?.clusterId ?? '',
    freeBytes: existing?.freeBytes ?? 0,
    currentMemberCount: existing?.currentMemberCount ?? 0,
    lastSeen: Date.now(),
    ttlTimer,
    isSuperPair: wasSuperPair
  });

  console.log(`[PRESENCE] JOIN nodeId=${nodeId.slice(0, 8)} ip=${ip ?? '?'}:${port ?? '?'} isSuperPair=${wasSuperPair}`);
  return true;
}

function handleGetPeers(ws) {
  const peers = [];
  for (const [nodeId, entry] of signalingRegistry.entries()) {
    // Story 10.1 — exporter la clé publique EC P-256 (SPKI-DER Base64) lue depuis la session
    // active. La clé est déjà vérifiée à l'AUTH (signature EC P-256 validée) — le serveur joue
    // le rôle de PKI TOFU. Si la session est fermée ou si l'export échoue (clé non-exportable),
    // pubKeySpkiDerB64 vaut "" pour cette entrée (le client retombera sur ByteArray(0)).
    let pubKeySpkiDerB64 = '';
    try {
      const session = sessions.get(nodeId);
      if (session?.publicKey) {
        pubKeySpkiDerB64 = session.publicKey
          .export({ format: 'der', type: 'spki' })
          .toString('base64');
      }
    } catch (e) {
      console.warn(`[GET_PEERS] export clé publique échoué pour nodeId=${nodeId.slice(0, 8)} : ${e.message}`);
    }

    peers.push({
      nodeId,
      ip: entry.ip,
      port: entry.port,
      reliabilityScore: entry.reliabilityScore,
      lastSeen: entry.lastSeen,
      isSuperPair: entry.isSuperPair === true,
      clusterId: entry.clusterId ?? '',
      freeBytes: entry.freeBytes ?? 0,
      // Story 10.1 — clé publique EC P-256 SPKI-DER encodée Base64.
      pubKeySpkiDerB64,
      // Story 12.1 — charge cluster (membres actifs) pour load balancing admission.
      currentMemberCount: entry.currentMemberCount ?? 0
    });
  }
  safeSend(ws, buildFrame(MSG.PEERS, Buffer.from(JSON.stringify(peers), 'utf8')));
}

// ─── Relay Store-and-Forward (UPLOAD / FORWARD) ──────────────────────────────

function flushPendingBlocks(nodeId, ws) {
  for (const [blockId, entries] of relayBuffer.entries()) {
    const pending = entries.filter(e => e.destNodeId === nodeId);
    if (pending.length === 0) continue;

    // Ne purger que les entrées effectivement délivrées : si la ws ferme
    // mid-flush, les blocs non envoyés restent en buffer avec leur TTL armé
    // pour une prochaine reconnexion.
    const delivered = [];
    for (const entry of pending) {
      if (ws.readyState !== WebSocket.OPEN) break;
      const forwardPayload = Buffer.allocUnsafe(16 + 64 + entry.data.length);
      Buffer.from(entry.fromNodeId.padEnd(16, '\0'), 'utf8').copy(forwardPayload, 0);
      Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(forwardPayload, 16);
      entry.data.copy(forwardPayload, 80);
      safeSend(ws, buildFrame(MSG.FORWARD, forwardPayload));
      clearTimeout(entry.ttlTimer);
      delivered.push(entry);
      console.log(`[RELAY] FLUSH ${blockId.slice(0, 16)} → nodeId=${nodeId.slice(0, 8)}`);
    }

    if (delivered.length === 0) continue;
    const remaining = entries.filter(e => !delivered.includes(e));
    if (remaining.length === 0) relayBuffer.delete(blockId);
    else relayBuffer.set(blockId, remaining);
  }
}

function handleUpload(fromNodeId, payload, senderWs) {
  // Payload binaire: 16 bytes destNodeId + 64 bytes blockId + data
  if (payload.length < 80) {
    sendError(senderWs, 'UPLOAD payload trop court (min 80 bytes)');
    return;
  }
  // Strip uniquement le padding NUL trailing (pas tous les NUL : un \0 interior
  // permettrait de forger une collision avec un autre nodeId/blockId via injection).
  // Aligné sur handleRequestBlock.
  const destNodeId = payload.slice(0, 16).toString('utf8').replace(/\0+$/, '').trim();
  const blockId = payload.slice(16, 80).toString('utf8').replace(/\0+$/, '').trim();
  const data = payload.slice(80); // bloc chiffré AES-256 GCM — JAMAIS transformé (AC#4 Zero-Knowledge)

  // Valider destNodeId et blockId après strip — contraintes strictes alignées sur REQUEST_BLOCK.
  if (!destNodeId || destNodeId.length !== 16) {
    sendError(senderWs, 'UPLOAD destNodeId invalide (attendu 16 chars)');
    return;
  }
  if (!/^[0-9a-fA-F]{64}$/.test(blockId)) {
    sendError(senderWs, 'UPLOAD blockId invalide (attendu 64 chars hex)');
    return;
  }
  // Anti-loop : un nœud ne peut pas s'uploader un bloc à lui-même.
  if (destNodeId.toLowerCase() === String(fromNodeId).toLowerCase()) {
    sendError(senderWs, 'UPLOAD destNodeId == fromNodeId interdit');
    return;
  }

  // Tenter forward immédiat si le destinataire est connecté
  const destSession = sessions.get(destNodeId);
  if (destSession && destSession.ws.readyState === WebSocket.OPEN) {
    const forwardPayload = Buffer.allocUnsafe(16 + 64 + data.length);
    Buffer.from(fromNodeId.padEnd(16, '\0'), 'utf8').copy(forwardPayload, 0);
    Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(forwardPayload, 16);
    data.copy(forwardPayload, 80);
    safeSend(destSession.ws, buildFrame(MSG.FORWARD, forwardPayload));
    console.log(`[RELAY] FORWARD immédiat ${blockId.slice(0, 16)} → nodeId=${destNodeId.slice(0, 8)}`);
  } else {
    // Cap buffer RAM
    if (relayBuffer.size >= MAX_RELAY_BUFFER_ENTRIES) {
      sendError(senderWs, 'Relay buffer plein — réessayer plus tard');
      return;
    }

    const existing = relayBuffer.get(blockId) || [];

    // Déduplication : si (blockId, destNodeId) existe déjà, remplacer l'entrée
    const dupIdx = existing.findIndex(e => e.destNodeId === destNodeId);
    if (dupIdx !== -1) {
      clearTimeout(existing[dupIdx].ttlTimer);
      existing.splice(dupIdx, 1);
    }

    // Capturer l'entrée par référence dans la closure TTL (évite d'évincer des entrées plus récentes)
    const newEntry = { fromNodeId, destNodeId, data, ttlTimer: null };
    const ttlTimer = setTimeout(() => {
      const arr = relayBuffer.get(blockId);
      if (arr) {
        const filtered = arr.filter(e => e !== newEntry);
        if (filtered.length === 0) relayBuffer.delete(blockId);
        else relayBuffer.set(blockId, filtered);
      }
      console.log(`[RELAY] TTL expiré — blockId=${blockId.slice(0, 16)} destNodeId=${destNodeId.slice(0, 8)} purgé`);
    }, TTL_MS);
    newEntry.ttlTimer = ttlTimer;

    existing.push(newEntry);
    relayBuffer.set(blockId, existing);
    console.log(`[RELAY] BUFFERED ${blockId.slice(0, 16)} → nodeId=${destNodeId.slice(0, 8)} (dest absent)`);
  }

  // ACK au sender
  safeSend(senderWs, buildFrame(MSG.ACK, Buffer.from(JSON.stringify({ blockId }), 'utf8')));
}

// ─── Story 9.4 — REQUEST_BLOCK (pull inter-cluster) ─────────────────────────

// Le requester envoie REQUEST_BLOCK(destNodeId, blockId) ; le serveur pivote
// 80 bytes vers le destinataire en REQUEST_BLOCK_FORWARDED(fromNodeId, blockId).
// PAS de buffering : si dest absent, on rejette (le requester time-out de toute façon).
function handleRequestBlock(fromNodeId, payload, senderWs) {
  if (payload.length !== 80) {
    sendError(senderWs, 'REQUEST_BLOCK payload invalide (attendu 80 bytes)');
    return;
  }
  // Strip uniquement le padding NUL trailing (pas tous les NUL : un \0 interior
  // permettrait de forger une collision avec un autre nodeId via injection).
  const destNodeId = payload.slice(0, 16).toString('utf8').replace(/\0+$/, '').trim();
  const blockId = payload.slice(16, 80).toString('utf8').replace(/\0+$/, '').trim();

  if (!destNodeId || destNodeId.length !== 16) {
    sendError(senderWs, 'REQUEST_BLOCK destNodeId invalide (attendu 16 chars)');
    return;
  }
  if (!/^[0-9a-fA-F]{64}$/.test(blockId)) {
    sendError(senderWs, 'REQUEST_BLOCK blockId invalide (attendu 64 chars hex)');
    return;
  }
  // AC#10 — anti-loop : un nœud ne peut pas se demander un bloc à lui-même.
  // Comparaison case-insensitive pour éviter le bypass si fromNodeId=auth-canonicalisé
  // et destNodeId arrive en case mixte.
  if (destNodeId.toLowerCase() === String(fromNodeId).toLowerCase()) {
    sendError(senderWs, 'REQUEST_BLOCK destNodeId == fromNodeId interdit');
    return;
  }

  const destSession = sessions.get(destNodeId);
  if (!destSession || destSession.ws.readyState !== WebSocket.OPEN) {
    sendError(senderWs, 'REQUEST_BLOCK destinataire injoignable');
    return;
  }

  // Forward : 16 bytes fromNodeId + 64 bytes blockId
  // Buffer.alloc (zéroïsé) : `allocUnsafe` peut fuiter de la mémoire arbitraire si
  // une copie partielle laisse des octets non-écrits.
  const forwardPayload = Buffer.alloc(80);
  Buffer.from(fromNodeId.padEnd(16, '\0'), 'utf8').copy(forwardPayload, 0);
  Buffer.from(blockId.padEnd(64, '\0'), 'utf8').copy(forwardPayload, 16);
  safeSend(destSession.ws, buildFrame(MSG.REQUEST_BLOCK_FORWARDED, forwardPayload));
  console.log(`[RELAY] REQUEST_BLOCK ${blockId.slice(0, 16)} : ${fromNodeId.slice(0, 8)} → ${destNodeId.slice(0, 8)}`);
}

// ─── SIGNAL — forwarding P2P éphémère (Gossip DHT, etc.) ────────────────────
// Payload : 16 bytes destNodeId (UTF-8, NUL-paddé) + data arbitraire.
// Pas de buffering — le signal est droppé si le destinataire est absent.
function handleSignal(fromNodeId, payload) {
  if (payload.length < 16) return;
  const destNodeId = payload.slice(0, 16).toString('utf8').replace(/\0+$/, '').trim();
  const data = payload.slice(16);
  if (!destNodeId || destNodeId.length < 4) return;
  const destSession = sessions.get(destNodeId);
  if (destSession && destSession.ws.readyState === WebSocket.OPEN) {
    const out = Buffer.allocUnsafe(16 + data.length);
    Buffer.from(fromNodeId.padEnd(16, '\0'), 'utf8').copy(out, 0);
    data.copy(out, 16);
    safeSend(destSession.ws, buildFrame(MSG.SIGNAL_RECEIVED, out));
  }
  // dest absent → signal droppé silencieusement
}

// ─── Serveur HTTP + WebSocketServer ─────────────────────────────────────────

const httpServer = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    let superPeerCount = 0;
    for (const entry of signalingRegistry.values()) {
      if (entry.isSuperPair) superPeerCount++;
    }
    const body = JSON.stringify({
      status: 'ok',
      sessions: sessions.size,
      pendingBlocks: relayBuffer.size,
      participants: signalingRegistry.size,
      registeredSuperPeers: superPeerCount
    });
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(body);
  } else {
    res.writeHead(404);
    res.end('Not found');
  }
});

// maxPayload : rejeter les frames dépassant la taille max avant buffering complet
const wss = new WebSocketServer({ server: httpServer, maxPayload: MAX_BLOCK_SIZE + 256 });

wss.on('connection', (ws) => {
  // Heartbeat protocol-level : détecte les sockets zombies (réseau coupé sans close TCP).
  // Si pas de pong dans la fenêtre du heartbeat suivant, ws.terminate() force la fermeture.
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  // État de la connexion — non authentifié jusqu'au premier message AUTH
  let authState = null; // null = non auth ; après AUTH_OK = { nodeId, publicKey }

  // Fermer les connexions non-authentifiées après AUTH_TIMEOUT_MS
  const authTimeout = setTimeout(() => {
    if (!authState) {
      ws.close(1008, 'AUTH timeout');
    }
  }, AUTH_TIMEOUT_MS);

  ws.on('message', (rawData, isBinary) => {
    if (!isBinary) {
      sendError(ws, 'Frames texte non supportées — utiliser frames binaires');
      return;
    }
    const frame = parseFrame(rawData);
    if (!frame) {
      sendError(ws, 'Frame malformée');
      return;
    }

    // Étape 1 : si pas authentifié, seul AUTH est autorisé
    if (!authState) {
      if (frame.type !== MSG.AUTH) {
        sendError(ws, 'Premier message doit être AUTH (0x01)');
        ws.close(1008, 'AUTH requis');
        return;
      }
      const result = verifyAuth(frame.payload);
      if (!result.ok) {
        sendError(ws, `AUTH échouée : ${result.reason}`);
        ws.close(1008, 'AUTH invalide');
        return;
      }

      // Fermer l'ancienne connexion si le nodeId est déjà enregistré
      const existingSession = sessions.get(result.nodeId);
      if (existingSession) {
        try { existingSession.ws.close(1008, 'Replaced by new connection'); } catch { /* déjà fermée */ }
      }

      clearTimeout(authTimeout);
      authState = { nodeId: result.nodeId, publicKey: result.publicKey };
      sessions.set(result.nodeId, { ws, publicKey: result.publicKey });
      safeSend(ws, buildFrame(MSG.AUTH_OK));
      console.log(`[AUTH] nodeId=${result.nodeId.slice(0, 8)} authentifié (${sessions.size} sessions)`);

      // Flush des blocs en attente pour ce nœud
      flushPendingBlocks(result.nodeId, ws);
      return;
    }

    // Étape 2 : messages post-auth
    const { nodeId } = authState;

    switch (frame.type) {
      case MSG.AUTH: {
        sendError(ws, 'Déjà authentifié');
        break;
      }
      case MSG.REGISTER_PEER: {
        const ok = handleRegisterPeer(nodeId, frame.payload);
        if (!ok) sendError(ws, 'REGISTER_PEER payload invalide');
        break;
      }
      case MSG.JOIN: {
        const ok = handleJoin(nodeId, frame.payload);
        if (!ok) sendError(ws, 'JOIN payload invalide');
        break;
      }
      case MSG.GET_PEERS: {
        handleGetPeers(ws);
        break;
      }
      case MSG.UPLOAD: {
        handleUpload(nodeId, frame.payload, ws);
        break;
      }
      case MSG.REQUEST_BLOCK: {
        handleRequestBlock(nodeId, frame.payload, ws);
        break;
      }
      case MSG.SIGNAL: {
        handleSignal(nodeId, frame.payload);
        break;
      }
      case MSG.PING: {
        safeSend(ws, buildFrame(MSG.PONG));
        break;
      }
      default: {
        sendError(ws, `Type de message inconnu : 0x${frame.type.toString(16)}`);
      }
    }
  });

  ws.on('close', () => {
    clearTimeout(authTimeout);
    if (authState) {
      // Race-safe : si une nouvelle connexion a déjà remplacé cette session
      // (reconnexion, NAT rebinding), ne rien supprimer — sinon on évincerait
      // l'entrée de la connexion vivante.
      const currentSession = sessions.get(authState.nodeId);
      if (currentSession && currentSession.ws === ws) {
        sessions.delete(authState.nodeId);
        const entry = signalingRegistry.get(authState.nodeId);
        if (entry) {
          clearTimeout(entry.ttlTimer);
          signalingRegistry.delete(authState.nodeId);
          console.log(`[SIGNALING] nodeId=${authState.nodeId.slice(0, 8)} déconnecté — supprimé de l'annuaire`);
        }
        console.log(`[WS] nodeId=${authState.nodeId.slice(0, 8)} déconnecté (${sessions.size} sessions restantes)`);
      } else {
        console.log(`[WS] nodeId=${authState.nodeId.slice(0, 8)} ancienne ws fermée — session active conservée`);
      }
    }
  });

  ws.on('error', (err) => {
    console.error(`[WS] Erreur socket : ${err.message}`);
  });
});

// ─── Démarrage + SIGTERM gracieux (AC#6) ────────────────────────────────────

function startServer() {
  httpServer.listen(PORT, () => {
    console.log(`[SERVER] MobiCloud Relay HA démarré sur port ${PORT}`);
    console.log(`[SERVER] /health → http://localhost:${PORT}/health`);
  });

  // Heartbeat global — ping toutes les ws toutes les 30s, terminate celles qui n'ont pas
  // répondu au précédent ping (zombies). Le 'close' handler nettoiera sessions/registry.
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (ws.isAlive === false) {
        ws.terminate();
        continue;
      }
      ws.isAlive = false;
      try { ws.ping(); } catch { /* ws en cours de fermeture */ }
    }
  }, HEARTBEAT_INTERVAL_MS);
  heartbeat.unref(); // ne bloque pas l'exit du process

  wss.on('close', () => clearInterval(heartbeat));

  process.once('SIGTERM', () => {
    console.log('[SERVER] SIGTERM reçu — fermeture gracieuse...');
    clearInterval(heartbeat);
    for (const [, session] of sessions.entries()) {
      if (session.ws.readyState === WebSocket.OPEN) {
        session.ws.close(1001, 'Server shutting down');
      }
    }
    httpServer.close(() => {
      console.log('[SERVER] Serveur arrêté proprement.');
      process.exit(0);
    });
    // Filet de sécurité 5s — unref() pour ne pas bloquer un exit propre
    setTimeout(() => process.exit(0), 5000).unref();
  });
}

if (require.main === module) {
  startServer();
}

// ─── Exports pour les tests ──────────────────────────────────────────────────
module.exports = {
  buildFrame, parseFrame, sendError, safeSend, verifyAuth,
  handleRegisterPeer, handleJoin, handleGetPeers, handleUpload, handleRequestBlock, flushPendingBlocks,
  sessions, signalingRegistry, relayBuffer,
  MSG, TTL_MS, AUTH_WINDOW_MS, AUTH_TIMEOUT_MS, MAX_BLOCK_SIZE,
  MAX_RELAY_BUFFER_ENTRIES, MAX_SIGNALING_PEERS,
  httpServer, startServer
};
